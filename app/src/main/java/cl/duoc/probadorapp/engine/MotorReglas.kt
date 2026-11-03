package cl.duoc.probadorapp.engine

import cl.duoc.probadorapp.data.repository.EstadoTienda
import cl.duoc.probadorapp.data.repository.RepositorioTienda
import cl.duoc.probadorapp.domain.Reloj
import cl.duoc.probadorapp.domain.model.Alerta
import cl.duoc.probadorapp.domain.model.Canal
import cl.duoc.probadorapp.domain.model.EstadoAlerta
import cl.duoc.probadorapp.domain.model.EstadoGuardado
import cl.duoc.probadorapp.domain.model.EstadoNumero
import cl.duoc.probadorapp.domain.model.Incidencia
import cl.duoc.probadorapp.domain.model.Llamado
import cl.duoc.probadorapp.domain.model.NumeroAtencion
import cl.duoc.probadorapp.domain.model.Ocupacion
import cl.duoc.probadorapp.domain.model.Parametros
import cl.duoc.probadorapp.domain.model.PrendaDetectada
import cl.duoc.probadorapp.domain.model.Probador
import cl.duoc.probadorapp.domain.model.TipoAlerta
import cl.duoc.probadorapp.domain.model.TipoIncidencia
import cl.duoc.probadorapp.domain.rules.Reglas
import cl.duoc.probadorapp.domain.rules.ResultadoEntrada
import cl.duoc.probadorapp.domain.rules.ResultadoSalida
import cl.duoc.probadorapp.domain.validation.Validaciones
import cl.duoc.probadorapp.signals.FuenteSenales
import cl.duoc.probadorapp.signals.Senal
import cl.duoc.probadorapp.signals.TipoSenal
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.util.UUID

/**
 * Motor de reglas del equipo del departamento (sección 4.1). Todos los cambios
 * de estado de los probadores y de los números pasan por aquí, tanto los que
 * vienen de las señales como los que vienen de las acciones del personal.
 */
class MotorReglas(
    private val repo: RepositorioTienda,
    private val reloj: Reloj,
    private val sinSenalMs: Long = 60_000L,
) {
    /** Hora de la última señal de cada dispositivo. No se guarda en la nube (RN-20). */
    private val ultimaSenal = mutableMapOf<String, Long>()
    private var inicio: Long = reloj.ahora()

    private val llamadosFlujo = MutableSharedFlow<Llamado>(extraBufferCapacity = 32)

    /** Cada llamado nuevo, para la pantalla de turnos y el anuncio por voz. */
    val llamados: SharedFlow<Llamado> = llamadosFlujo

    private fun nuevoId() = UUID.randomUUID().toString()

    // ------------------------------------------------------------------ ciclo de ejecución

    fun iniciar(scope: CoroutineScope, fuente: FuenteSenales) {
        inicio = reloj.ahora()
        scope.launch { fuente.senales.collect { procesar(it) } }
        scope.launch {
            while (isActive) {
                revisarTiempos()
                delay(1_000L)
            }
        }
    }

    // ------------------------------------------------------------------ señales

    suspend fun procesar(senal: Senal) {
        synchronized(ultimaSenal) { ultimaSenal[senal.dispositivo] = senal.hora }
        val nuevos = mutableListOf<Llamado>()
        repo.transaccion { e ->
            val p = e.probadores.values.firstOrNull { it.activo && (it.sensorCodigo == senal.dispositivo || it.lectorCodigo == senal.dispositivo) }
                ?: return@transaccion e to Unit // RN-21: dispositivo no asociado
            var s = recuperarSiCorresponde(e, p)
            val prob = s.probadores.getValue(p.id)
            s = when (senal.tipo) {
                TipoSenal.ENTRADA -> if (senal.dispositivo == prob.sensorCodigo) entrada(s, prob, senal.hora) else s
                TipoSenal.SALIDA -> if (senal.dispositivo == prob.sensorCodigo) salida(s, prob, senal.hora) else s
                TipoSenal.PRENDAS_DETECTADAS -> prendasDetectadas(s, prob, senal)
                TipoSenal.PRENDAS_RETIRADAS -> prendasRetiradas(s, prob, senal.hora)
                TipoSenal.FUNCIONAMIENTO -> s
            }
            asignarTodo(s, nuevos) to Unit
        }
        nuevos.forEach { llamadosFlujo.tryEmit(it) }
    }

    private fun entrada(e: EstadoTienda, p: Probador, hora: Long): EstadoTienda =
        when (Reglas.resultadoEntrada(p.estado)) {
            ResultadoEntrada.OCUPAR_CON_NUMERO -> {
                val num = p.numeroId?.let { e.numeros[it] }
                val oc = Ocupacion(nuevoId(), p.id, num?.id, hora, tiempoMaximoMin = e.parametros.tiempoMaximoMin)
                var numeros = e.numeros
                if (num != null) numeros = numeros + (num.id to num.copy(estado = EstadoNumero.ATENDIDO, cierre = hora))
                e.copy(
                    numeros = numeros,
                    ocupaciones = e.ocupaciones + (oc.id to oc),
                    probadores = e.probadores + (p.id to p.copy(estado = EstadoGuardado.OCUPADO, ocupacionId = oc.id, numeroId = null)),
                )
            }
            ResultadoEntrada.OCUPAR_SIN_NUMERO -> {
                val oc = Ocupacion(nuevoId(), p.id, null, hora, tiempoMaximoMin = e.parametros.tiempoMaximoMin)
                e.copy(
                    ocupaciones = e.ocupaciones + (oc.id to oc),
                    probadores = e.probadores + (p.id to p.copy(estado = EstadoGuardado.OCUPADO, ocupacionId = oc.id)),
                )
            }
            ResultadoEntrada.REVISION_DEL_PERSONAL, ResultadoEntrada.IGNORAR -> e
        }

    private fun salida(e: EstadoTienda, p: Probador, hora: Long): EstadoTienda {
        if (p.estado != EstadoGuardado.OCUPADO) return e // RN-17 y RN-21
        val oc = p.ocupacionId?.let { e.ocupaciones[it] }
        var s = e
        if (oc != null) s = s.copy(ocupaciones = s.ocupaciones + (oc.id to oc.copy(salida = hora)))
        s = cerrarAlertas(s, p.id, setOf(TipoAlerta.TIEMPO_EXCEDIDO), hora)
        val dentro = p.prendasDentro.filter { it.retirada == null }
        return when (Reglas.resultadoSalida(dentro.size, e.incidenciasAbiertas(p.id))) {
            ResultadoSalida.LIBRE ->
                s.copy(probadores = s.probadores + (p.id to p.copy(estado = EstadoGuardado.LIBRE, ocupacionId = null)))
            ResultadoSalida.EN_REVISION_POR_INCIDENCIA ->
                s.copy(probadores = s.probadores + (p.id to p.copy(estado = EstadoGuardado.EN_REVISION, ocupacionId = null)))
            ResultadoSalida.EN_REVISION_POR_PRENDAS -> {
                val detalle = dentro.joinToString("; ") { "${it.prenda.descripcion}, talla ${it.prenda.talla}" }
                val s2 = s.copy(probadores = s.probadores + (p.id to p.copy(estado = EstadoGuardado.EN_REVISION, ocupacionId = null)))
                crearAlerta(s2, p.departamentoId, p.id, TipoAlerta.PRENDA_OLVIDADA, detalle, hora)
            }
        }
    }

    private fun prendasDetectadas(e: EstadoTienda, p: Probador, senal: Senal): EstadoTienda {
        val yaDentro = p.prendasDentro.filter { it.retirada == null }.map { it.prenda.epc }.toSet()
        val nuevas = senal.prendas.filter { it.epc !in yaDentro }.map { PrendaDetectada(it, senal.hora) }
        if (nuevas.isEmpty()) return e
        var s = e.copy(probadores = e.probadores + (p.id to p.copy(prendasDentro = p.prendasDentro + nuevas)))
        val oc = p.ocupacionId?.let { s.ocupaciones[it] }
        if (oc != null) s = s.copy(ocupaciones = s.ocupaciones + (oc.id to oc.copy(prendas = oc.prendas + nuevas)))
        return s
    }

    private fun prendasRetiradas(e: EstadoTienda, p: Probador, hora: Long): EstadoTienda {
        val actualizado = p.copy(prendasDentro = emptyList())
        var s = e.copy(probadores = e.probadores + (p.id to actualizado))
        if (p.estado == EstadoGuardado.EN_REVISION && Reglas.liberarRevision(0, e.incidenciasAbiertas(p.id))) {
            s = s.copy(probadores = s.probadores + (p.id to actualizado.copy(estado = EstadoGuardado.LIBRE)))
            s = cerrarAlertas(s, p.id, setOf(TipoAlerta.PRENDA_OLVIDADA), hora)
        }
        return s
    }

    /** RN-20: si las señales volvieron, el probador recupera su estado y la alerta se cierra. */
    private fun recuperarSiCorresponde(e: EstadoTienda, p: Probador): EstadoTienda {
        if (!p.sinSenal || !conSenal(p, reloj.ahora())) return e
        val s = e.copy(probadores = e.probadores + (p.id to p.copy(sinSenal = false)))
        return cerrarAlertas(s, p.id, setOf(TipoAlerta.SIN_SENAL), reloj.ahora())
    }

    private fun conSenal(p: Probador, ahora: Long): Boolean = synchronized(ultimaSenal) {
        listOf(p.sensorCodigo, p.lectorCodigo).all { ahora - (ultimaSenal[it] ?: inicio) <= sinSenalMs }
    }

    // ------------------------------------------------------------------ temporizador

    /** Revisa cada segundo los tiempos de uso, los plazos de los llamados y las señales. */
    suspend fun revisarTiempos() {
        val ahora = reloj.ahora()
        val nuevos = mutableListOf<Llamado>()
        repo.transaccion { e ->
            var s = e
            // RN-19: tiempo excedido, una alerta por ocupación
            for (oc in s.ocupaciones.values.filter { it.salida == null && !it.alertaEmitida && Reglas.excedido(it, ahora) }) {
                val p = s.probadores[oc.probadorId] ?: continue
                s = s.copy(ocupaciones = s.ocupaciones + (oc.id to oc.copy(alertaEmitida = true)))
                s = crearAlerta(s, p.departamentoId, p.id, TipoAlerta.TIEMPO_EXCEDIDO, "Lleva ${oc.tiempoMaximoMin} min ocupado", ahora)
            }
            // RN-12: llamados vencidos
            for (n in s.numeros.values.filter { Reglas.llamadoVencido(it, ahora, s.parametros.plazoLlamadoMin) }) {
                val p = n.probadorId?.let { s.probadores[it] }
                s = s.copy(numeros = s.numeros + (n.id to n.copy(estado = EstadoNumero.PERDIDO, cierre = ahora)))
                if (p != null && p.estado == EstadoGuardado.RESERVADO && p.numeroId == n.id) {
                    s = s.copy(probadores = s.probadores + (p.id to p.copy(estado = EstadoGuardado.LIBRE, numeroId = null)))
                }
                s = registrarLlamado(s, n.departamentoId, Llamado(n.numero, p?.codigo ?: "", ahora, perdido = true))
            }
            // RN-20: probadores sin señal
            for (p in s.probadores.values.filter { it.activo }) {
                val actual = s.probadores.getValue(p.id)
                if (!actual.sinSenal && !conSenal(actual, ahora)) {
                    var cambiado = actual.copy(sinSenal = true)
                    if (actual.estado == EstadoGuardado.RESERVADO) {
                        val n = actual.numeroId?.let { s.numeros[it] }
                        if (n != null) s = s.copy(numeros = s.numeros + (n.id to n.copy(estado = EstadoNumero.EN_ESPERA, probadorId = null, llamado = null)))
                        cambiado = cambiado.copy(estado = EstadoGuardado.LIBRE, numeroId = null)
                    }
                    s = s.copy(probadores = s.probadores + (p.id to cambiado))
                    s = crearAlerta(s, p.departamentoId, p.id, TipoAlerta.SIN_SENAL, "Revise el sensor ${p.sensorCodigo} y el lector ${p.lectorCodigo}", ahora)
                }
            }
            asignarTodo(s, nuevos) to Unit
        }
        nuevos.forEach { llamadosFlujo.tryEmit(it) }
    }

    // ------------------------------------------------------------------ acciones del personal y del cliente

    /** RF-05 y RF-07: entrega el siguiente número del correlativo del departamento. */
    suspend fun entregarNumero(departamentoId: String, canal: Canal, entregadoPor: String? = null, clienteUid: String? = null): NumeroAtencion {
        val nuevos = mutableListOf<Llamado>()
        val ahora = reloj.ahora()
        val fecha = reloj.hoy()
        val id = repo.transaccion { e ->
            val clave = "$departamentoId|$fecha"
            val numero = Reglas.siguienteNumero(e.contadores[clave])
            val n = NumeroAtencion(nuevoId(), departamentoId, numero, fecha, canal, emitido = ahora,
                clienteUid = clienteUid, entregadoPor = entregadoPor)
            val s = e.copy(numeros = e.numeros + (n.id to n), contadores = e.contadores + (clave to numero))
            asignarTodo(s, nuevos) to n.id
        }
        nuevos.forEach { llamadosFlujo.tryEmit(it) }
        return repo.estado.value.numeros.getValue(id)
    }

    /** RF-20 y RN-18: el personal confirma que el probador está vacío y en condiciones. */
    suspend fun confirmarRevision(probadorId: String) {
        val ahora = reloj.ahora()
        val nuevos = mutableListOf<Llamado>()
        repo.transaccion { e ->
            val p = e.probadores[probadorId] ?: return@transaccion e to Unit
            if (p.estado != EstadoGuardado.EN_REVISION) return@transaccion e to Unit
            var s = e.copy(
                probadores = e.probadores + (p.id to p.copy(estado = EstadoGuardado.LIBRE, prendasDentro = emptyList())),
                incidencias = e.incidencias.mapValues { (_, i) -> if (i.probadorId == p.id && i.abierta) i.copy(abierta = false) else i },
            )
            s = cerrarAlertas(s, p.id, setOf(TipoAlerta.PRENDA_OLVIDADA), ahora)
            asignarTodo(s, nuevos) to Unit
        }
        nuevos.forEach { llamadosFlujo.tryEmit(it) }
    }

    /**
     * RF-23 y RN-22: registra una incidencia. Si el probador estaba Reservado,
     * el número llamado vuelve a la espera y se asigna a otro probador disponible.
     */
    suspend fun reportarIncidencia(
        probadorId: String,
        tipo: TipoIncidencia,
        descripcion: String,
        fotoUri: String?,
        usuarioUid: String,
    ): Incidencia {
        val ahora = reloj.ahora()
        val nuevos = mutableListOf<Llamado>()
        val inc = Incidencia(nuevoId(), probadorId, tipo, descripcion.trim(), fotoUri, usuarioUid, ahora)
        repo.transaccion { e ->
            val p = e.probadores[probadorId] ?: return@transaccion e to Unit
            var s = e.copy(incidencias = e.incidencias + (inc.id to inc))
            var cambiado = p.copy(estado = Reglas.estadoTrasIncidencia(p.estado))
            if (p.estado == EstadoGuardado.RESERVADO) {
                val n = p.numeroId?.let { s.numeros[it] }
                if (n != null) s = s.copy(numeros = s.numeros + (n.id to n.copy(estado = EstadoNumero.EN_ESPERA, probadorId = null, llamado = null)))
                cambiado = cambiado.copy(numeroId = null)
            }
            s = s.copy(probadores = s.probadores + (p.id to cambiado))
            asignarTodo(s, nuevos) to Unit
        }
        nuevos.forEach { llamadosFlujo.tryEmit(it) }
        return inc
    }

    /** RF-25 y RN-18: cierra una incidencia y libera el probador si ya no tiene pendientes. */
    suspend fun cerrarIncidencia(incidenciaId: String) {
        val ahora = reloj.ahora()
        val nuevos = mutableListOf<Llamado>()
        repo.transaccion { e ->
            val inc = e.incidencias[incidenciaId] ?: return@transaccion e to Unit
            if (!inc.abierta) return@transaccion e to Unit
            var s = e.copy(incidencias = e.incidencias + (inc.id to inc.copy(abierta = false, cierre = ahora)))
            val p = s.probadores[inc.probadorId]
            if (p != null && p.estado == EstadoGuardado.EN_REVISION &&
                Reglas.liberarRevision(p.prendasDentro.count { it.retirada == null }, s.incidenciasAbiertas(p.id))
            ) {
                s = s.copy(probadores = s.probadores + (p.id to p.copy(estado = EstadoGuardado.LIBRE)))
            }
            asignarTodo(s, nuevos) to Unit
        }
        nuevos.forEach { llamadosFlujo.tryEmit(it) }
    }

    /**
     * RF-37: aplica los parámetros de operación y los mínimos de personal.
     * El tiempo máximo nuevo solo afecta a las ocupaciones que empiecen después (RN-19).
     */
    suspend fun aplicarAjustes(parametros: Parametros, minimos: Map<String, Int>) {
        repo.transaccion { e ->
            e.copy(
                parametros = parametros,
                departamentos = e.departamentos.mapValues { (id, d) -> minimos[id]?.let { d.copy(minimoPersonal = it) } ?: d },
            ) to Unit
        }
    }

    /**
     * RF-36 y F-11: cambia los códigos del sensor y del lector de un probador y
     * lo activa o desactiva. Devuelve el mensaje de error, o null.
     * Un probador desactivado no recibe números; si estaba Reservado, el número vuelve a la espera.
     */
    suspend fun configurarProbador(probadorId: String, sensor: String, lector: String, activo: Boolean): String? {
        val nuevos = mutableListOf<Llamado>()
        val error = repo.transaccion { e ->
            val p = e.probadores[probadorId] ?: return@transaccion e to "El probador no existe"
            val usados = { prefijo: String ->
                e.probadores.values.filter { it.id != p.id }.associate {
                    (if (prefijo == "PS") it.sensorCodigo else it.lectorCodigo) to "${it.codigo} de ${e.departamentos[it.departamentoId]?.nombre}"
                }
            }
            Validaciones.codigoDispositivo(sensor, "PS", "Ingrese el código del sensor", usados("PS"))?.let { return@transaccion e to it }
            Validaciones.codigoDispositivo(lector, "LR", "Ingrese el código del lector", usados("LR"))?.let { return@transaccion e to it }
            var s = e
            var cambiado = p.copy(sensorCodigo = sensor.trim().uppercase(), lectorCodigo = lector.trim().uppercase(), activo = activo)
            if (!activo && p.estado == EstadoGuardado.RESERVADO) {
                val n = p.numeroId?.let { s.numeros[it] }
                if (n != null) s = s.copy(numeros = s.numeros + (n.id to n.copy(estado = EstadoNumero.EN_ESPERA, probadorId = null, llamado = null)))
                cambiado = cambiado.copy(estado = EstadoGuardado.LIBRE, numeroId = null)
            }
            s = s.copy(probadores = s.probadores + (p.id to cambiado))
            asignarTodo(s, nuevos) to null
        }
        // Los dispositivos nuevos parten con señal.
        synchronized(ultimaSenal) {
            ultimaSenal[sensor.trim().uppercase()] = reloj.ahora()
            ultimaSenal[lector.trim().uppercase()] = reloj.ahora()
        }
        nuevos.forEach { llamadosFlujo.tryEmit(it) }
        return error
    }

    /** RF-22: marcar una alerta como vista por un usuario. */
    suspend fun marcarVista(alertaId: String, uid: String) {
        repo.transaccion { e ->
            val a = e.alertas[alertaId] ?: return@transaccion e to Unit
            e.copy(alertas = e.alertas + (a.id to a.copy(vistaPor = a.vistaPor + uid))) to Unit
        }
    }

    /** RF-39: borra los datos del día. El personal y sus turnos se conservan. */
    suspend fun reiniciarDemostracion() {
        synchronized(ultimaSenal) { ultimaSenal.clear() }
        inicio = reloj.ahora()
        repo.transaccion { e ->
            e.copy(
                probadores = e.probadores.mapValues { (_, p) ->
                    p.copy(estado = EstadoGuardado.LIBRE, sinSenal = false, numeroId = null, ocupacionId = null, prendasDentro = emptyList())
                },
                numeros = emptyMap(), ocupaciones = emptyMap(), alertas = emptyMap(), incidencias = emptyMap(),
                contadores = emptyMap(), llamados = emptyMap(), objetos = emptyMap(),
            ) to Unit
        }
    }

    // ------------------------------------------------------------------ asignación y alertas

    /** RN-11: asigna probadores en todos los departamentos mientras sea posible. */
    private fun asignarTodo(e: EstadoTienda, nuevos: MutableList<Llamado>): EstadoTienda {
        var s = e
        val ahora = reloj.ahora()
        for (dep in s.departamentos.keys) {
            while (true) {
                val (n, p) = Reglas.elegirAsignacion(dep, s.numeros.values, s.probadores.values) ?: break
                s = s.copy(
                    numeros = s.numeros + (n.id to n.copy(estado = EstadoNumero.LLAMADO, probadorId = p.id, llamado = ahora)),
                    probadores = s.probadores + (p.id to p.copy(estado = EstadoGuardado.RESERVADO, numeroId = n.id)),
                )
                val llamado = Llamado(n.numero, p.codigo, ahora)
                s = registrarLlamado(s, dep, llamado)
                nuevos += llamado
            }
        }
        return s
    }

    private fun registrarLlamado(e: EstadoTienda, dep: String, l: Llamado): EstadoTienda =
        e.copy(llamados = e.llamados + (dep to (listOf(l) + (e.llamados[dep] ?: emptyList())).take(10)))

    private fun crearAlerta(e: EstadoTienda, dep: String, probadorId: String?, tipo: TipoAlerta, detalle: String, ahora: Long): EstadoTienda {
        val depto = e.departamentos[dep]
        val dest = Reglas.destinatarios(tipo, e.vendedoresEnTurno(dep).map { it.uid }, depto?.jefeUid, e.jefeDeTienda()?.uid)
        val a = Alerta(nuevoId(), dep, probadorId, tipo, detalle, ahora, dest)
        return e.copy(alertas = e.alertas + (a.id to a))
    }

    private fun cerrarAlertas(e: EstadoTienda, probadorId: String, tipos: Set<TipoAlerta>, ahora: Long): EstadoTienda =
        e.copy(alertas = e.alertas.mapValues { (_, a) ->
            if (a.probadorId == probadorId && a.tipo in tipos && a.estado == EstadoAlerta.ACTIVA) a.copy(estado = EstadoAlerta.CERRADA, cierre = ahora) else a
        })
}

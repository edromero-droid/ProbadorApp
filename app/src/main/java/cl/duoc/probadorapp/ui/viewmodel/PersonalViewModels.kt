package cl.duoc.probadorapp.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import cl.duoc.probadorapp.data.local.RepositorioHistorial
import cl.duoc.probadorapp.data.repository.EstadoTienda
import cl.duoc.probadorapp.data.repository.RepositorioTienda
import cl.duoc.probadorapp.data.session.RepositorioSesion
import cl.duoc.probadorapp.domain.Reloj
import cl.duoc.probadorapp.domain.model.Departamento
import cl.duoc.probadorapp.domain.model.EstadoSolicitud
import cl.duoc.probadorapp.domain.model.Rol
import cl.duoc.probadorapp.domain.model.SolicitudRefuerzo
import cl.duoc.probadorapp.domain.model.Usuario
import cl.duoc.probadorapp.domain.rules.Jefaturas
import cl.duoc.probadorapp.domain.validation.Validaciones
import cl.duoc.probadorapp.engine.GestionPersonal
import cl.duoc.probadorapp.ui.hora
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.mapLatest
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale

/** Mensaje breve para mostrar en un Snackbar después de una acción. */
interface ConMensaje {
    val mensaje: StateFlow<String?>
    fun mensajeMostrado()
}

open class ViewModelConMensaje : ViewModel(), ConMensaje {
    protected val mensajeFlujo = MutableStateFlow<String?>(null)
    override val mensaje: StateFlow<String?> = mensajeFlujo
    override fun mensajeMostrado() {
        mensajeFlujo.value = null
    }
}

// ---------------------------------------------------------------------- UI-13 Personal y cobertura

data class PersonaUi(
    val uid: String,
    val nombre: String,
    val rol: String,
    val enTurno: Boolean,
    val detalle: String,
    val puedeMover: Boolean,
)

data class PersonalUi(
    val departamentos: List<Departamento> = emptyList(),
    val departamentoId: String = "",
    val puedeElegirDepartamento: Boolean = false,
    val cobertura: Int = 0,
    val minimo: Int = 0,
    val jefe: String = "",
    val personas: List<PersonaUi> = emptyList(),
    val esJefeDeTienda: Boolean = false,
)

/** RF-29: personas del departamento con su turno y la cobertura frente al mínimo. */
class PersonalViewModel(repo: RepositorioTienda, sesion: RepositorioSesion) : ViewModel() {
    private val elegido = MutableStateFlow<String?>(null)

    val estado: StateFlow<PersonalUi> = combine(repo.estado, elegido, sesion.usuario) { e, d, u -> construir(e, d, u) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), PersonalUi())

    fun elegirDepartamento(id: String) {
        elegido.value = id
    }

    private fun construir(e: EstadoTienda, elegido: String?, u: Usuario?): PersonalUi {
        val dep = departamentoVisible(e, elegido, u) ?: return PersonalUi()
        val d = e.departamentos[dep]
        val personas = e.personalDe(dep).map { p ->
            val turno = e.turnoAbierto(p.uid)
            val puedeMover = p.rol == Rol.VENDEDOR && when (u?.rol) {
                Rol.JEFE_TIENDA -> true
                Rol.JEFE_DEPARTAMENTO -> u.departamentoId == dep
                else -> false
            }
            PersonaUi(
                p.uid, p.nombre, p.rol.texto, turno != null,
                if (turno != null) "En turno desde las ${hora(turno.entrada)}" else "Fuera de turno",
                puedeMover,
            )
        }
        return PersonalUi(
            departamentos = e.departamentos.values.toList(),
            departamentoId = dep,
            puedeElegirDepartamento = u?.rol == Rol.JEFE_TIENDA,
            cobertura = e.cobertura(dep),
            minimo = d?.minimoPersonal ?: 0,
            jefe = d?.jefeUid?.let { e.usuarios[it]?.nombre } ?: "A cargo del jefe de tienda",
            personas = personas,
            esJefeDeTienda = u?.rol == Rol.JEFE_TIENDA,
        )
    }
}

// ---------------------------------------------------------------------- UI-14 Mover persona (F-06)

data class MoverUi(
    val nombre: String = "",
    val actual: String = "",
    val enTurno: Boolean = false,
    val departamentos: List<Departamento> = emptyList(),
    val actualId: String? = null,
    val destino: String? = null,
    val error: String? = null,
    val advertencia: String? = null,
    val puedeEnviar: Boolean = false,
    val guardando: Boolean = false,
    val terminado: Boolean = false,
)

/** Mueve o cede a un vendedor. [uid] llega como argumento de la ruta desde UI-13. */
class MoverPersonaViewModel(
    private val uid: String,
    private val repo: RepositorioTienda,
    private val sesion: RepositorioSesion,
    private val personal: GestionPersonal,
) : ViewModelConMensaje() {
    private val destino = MutableStateFlow<String?>(null)
    private val guardando = MutableStateFlow(false)
    private val terminado = MutableStateFlow(false)

    val estado: StateFlow<MoverUi> = combine(repo.estado, destino, guardando, terminado) { e, d, g, t -> construir(e, d, g, t) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), MoverUi())

    fun elegirDestino(id: String) {
        destino.value = id
    }

    fun mover() {
        val actor = sesion.usuario.value ?: return
        val d = destino.value ?: return
        guardando.value = true
        viewModelScope.launch {
            val error = personal.moverPersona(actor, uid, d)
            guardando.value = false
            if (error == null) terminado.value = true else mensajeFlujo.value = error
        }
    }

    private fun construir(e: EstadoTienda, d: String?, g: Boolean, t: Boolean): MoverUi {
        val p = e.usuarios[uid] ?: return MoverUi()
        val actor = sesion.usuario.value
        val revision = if (actor != null && d != null) personal.revisarMovimiento(e, actor, uid, d) else null
        return MoverUi(
            nombre = p.nombre,
            actual = e.departamentos[p.departamentoId]?.nombre ?: "-",
            enTurno = e.turnoAbierto(uid) != null,
            departamentos = e.departamentos.values.toList(),
            actualId = p.departamentoId,
            destino = d,
            error = revision?.error,
            advertencia = revision?.advertencia,
            puedeEnviar = d != null && revision?.error == null && !g,
            guardando = g,
            terminado = t,
        )
    }
}

// ---------------------------------------------------------------------- UI-15 Solicitudes de refuerzo (F-07)

data class SolicitudUi(
    val id: String,
    val titulo: String,
    val detalle: String,
    val estado: EstadoSolicitud,
    val puedeResponder: Boolean,
    val puedeCancelar: Boolean,
)

data class SolicitudesUi(
    val puedeSolicitar: Boolean = false,
    val opciones: List<Departamento> = emptyList(),
    val pedido: String? = null,
    val errorPedido: String? = null,
    val puedeEnviar: Boolean = false,
    val recibidas: List<SolicitudUi> = emptyList(),
    val enviadas: List<SolicitudUi> = emptyList(),
)

class SolicitudesViewModel(
    repo: RepositorioTienda,
    private val sesion: RepositorioSesion,
    private val personal: GestionPersonal,
) : ViewModelConMensaje() {
    private val pedido = MutableStateFlow<String?>(null)

    val estado: StateFlow<SolicitudesUi> = combine(repo.estado, pedido, sesion.usuario) { e, p, u -> construir(e, p, u) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), SolicitudesUi())

    fun elegir(id: String) {
        pedido.value = id
    }

    fun enviar() {
        val actor = sesion.usuario.value ?: return
        val p = pedido.value ?: return
        viewModelScope.launch {
            val error = personal.solicitarRefuerzo(actor, p)
            mensajeFlujo.value = error ?: "Solicitud enviada"
            if (error == null) pedido.value = null
        }
    }

    fun cancelar(id: String) {
        val actor = sesion.usuario.value ?: return
        viewModelScope.launch { mensajeFlujo.value = personal.cancelarSolicitud(actor, id) ?: "Solicitud cancelada" }
    }

    private fun construir(e: EstadoTienda, p: String?, u: Usuario?): SolicitudesUi {
        if (u == null) return SolicitudesUi()
        val puedeSolicitar = u.rol == Rol.JEFE_DEPARTAMENTO
        val error = if (p != null) personal.revisarSolicitud(e, u, p) else null
        val todas = e.solicitudes.values.sortedByDescending { it.creada }
        val recibidas = todas.filter {
            when (u.rol) {
                Rol.JEFE_TIENDA -> true
                Rol.JEFE_DEPARTAMENTO -> it.departamentoPedido == u.departamentoId
                Rol.VENDEDOR -> false
            }
        }
        return SolicitudesUi(
            puedeSolicitar = puedeSolicitar,
            opciones = e.departamentos.values.filter { it.id != u.departamentoId },
            pedido = p,
            errorPedido = error,
            puedeEnviar = puedeSolicitar && p != null && error == null,
            recibidas = recibidas.map { aUi(e, it, u) },
            enviadas = todas.filter { it.solicitanteUid == u.uid }.map { aUi(e, it, u) },
        )
    }

    private fun aUi(e: EstadoTienda, s: SolicitudRefuerzo, u: Usuario): SolicitudUi {
        val origen = e.departamentos[s.departamentoOrigen]?.nombre ?: "-"
        val pedido = e.departamentos[s.departamentoPedido]?.nombre ?: "-"
        val detalle = buildString {
            append("Enviada por ${e.usuarios[s.solicitanteUid]?.nombre ?: "-"} a las ${hora(s.creada)}")
            s.vendedorUid?.let { append(" · Se cedió a ${e.usuarios[it]?.nombre ?: "-"}") }
            s.motivo?.let { append(" · Motivo: $it") }
        }
        return SolicitudUi(
            s.id, "$origen pide un vendedor a $pedido", detalle, s.estado,
            puedeResponder = personal.puedeResponder(u, s),
            puedeCancelar = s.abierta && s.solicitanteUid == u.uid,
        )
    }
}

// ---------------------------------------------------------------------- F-08 Responder solicitud

data class ResponderUi(
    val titulo: String = "",
    val existe: Boolean = false,
    val abierta: Boolean = true,
    val aceptar: Boolean? = null,
    val vendedores: List<Pair<String, String>> = emptyList(),
    val vendedor: String? = null,
    val errorVendedor: String? = null,
    val motivo: String = "",
    val errorMotivo: String? = null,
    val contador: String = "0/100",
    val puedeEnviar: Boolean = false,
    val terminado: Boolean = false,
)

/** Aceptar eligiendo al vendedor, o rechazar con un motivo de 5 a 100 caracteres (RN-28). */
class ResponderSolicitudViewModel(
    private val solicitudId: String,
    repo: RepositorioTienda,
    private val sesion: RepositorioSesion,
    private val personal: GestionPersonal,
) : ViewModelConMensaje() {
    private data class Form(val aceptar: Boolean? = null, val vendedor: String? = null, val motivo: String = "", val motivoTocado: Boolean = false, val terminado: Boolean = false)

    private val form = MutableStateFlow(Form())

    val estado: StateFlow<ResponderUi> = combine(repo.estado, form) { e, f -> construir(e, f) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), ResponderUi())

    fun elegirRespuesta(aceptar: Boolean) = form.update { it.copy(aceptar = aceptar) }
    fun elegirVendedor(uid: String) = form.update { it.copy(vendedor = uid) }
    fun alCambiarMotivo(v: String) = form.update { it.copy(motivo = v, motivoTocado = true) }

    fun enviar() {
        val actor = sesion.usuario.value ?: return
        val f = form.value
        viewModelScope.launch {
            val error = if (f.aceptar == true) {
                personal.aceptarSolicitud(actor, solicitudId, f.vendedor ?: "")
            } else {
                personal.rechazarSolicitud(actor, solicitudId, f.motivo)
            }
            if (error == null) form.update { it.copy(terminado = true) } else mensajeFlujo.value = error
        }
    }

    private fun construir(e: EstadoTienda, f: Form): ResponderUi {
        val s = e.solicitudes[solicitudId] ?: return ResponderUi()
        val pedido = s.departamentoPedido
        val vendedores = e.personalDe(pedido).filter { it.rol == Rol.VENDEDOR }.map {
            it.uid to it.nombre + if (e.turnoAbierto(it.uid) != null) " (en turno)" else " (fuera de turno)"
        }
        val errorVendedor = if (f.aceptar == true && f.vendedor != null) personal.revisarAceptacion(e, s, f.vendedor) else null
        val errorMotivo = if (f.aceptar == false) Validaciones.textoConLargo(f.motivo, 5, 100, "Indique el motivo") else null
        val valido = when (f.aceptar) {
            true -> f.vendedor != null && errorVendedor == null
            false -> errorMotivo == null
            null -> false
        }
        return ResponderUi(
            titulo = "${e.departamentos[s.departamentoOrigen]?.nombre} pide un vendedor a ${e.departamentos[pedido]?.nombre}",
            existe = true,
            abierta = s.abierta,
            aceptar = f.aceptar,
            vendedores = vendedores,
            vendedor = f.vendedor,
            errorVendedor = errorVendedor,
            motivo = f.motivo,
            errorMotivo = errorMotivo.takeIf { f.motivoTocado },
            contador = "${f.motivo.trim().length}/100",
            puedeEnviar = valido && s.abierta,
            terminado = f.terminado,
        )
    }
}

// ---------------------------------------------------------------------- UI-16 Cambio de jefatura (F-09)

/** Opción especial: el departamento queda a cargo del jefe de tienda. */
const val A_CARGO_DE_TIENDA = "__tienda__"

data class VacanteUi(
    val departamentoId: String,
    val departamento: String,
    val opciones: List<Pair<String, String>>,
    val eleccion: String?,
    val error: String?,
)

data class JefaturaUi(
    val departamentos: List<Pair<String, String>> = emptyList(),
    val jefes: List<Pair<String, String>> = emptyList(),
    val departamento: String? = null,
    val nuevoJefe: String? = null,
    val aviso: String? = null,
    val vacantes: List<VacanteUi> = emptyList(),
    val resumen: List<Triple<String, String, String>> = emptyList(),
    val puedeConfirmar: Boolean = false,
    val terminado: Boolean = false,
)

/**
 * RN-29: el jefe de tienda elige el departamento y el nuevo jefe; si eso deja otro
 * departamento vacío, pide quién queda a cargo, hasta que todos tengan jefe.
 */
class JefaturaViewModel(
    private val repo: RepositorioTienda,
    private val sesion: RepositorioSesion,
    private val personal: GestionPersonal,
) : ViewModelConMensaje() {
    private data class Form(
        val departamento: String? = null,
        val nuevo: String? = null,
        val elecciones: Map<String, String> = emptyMap(),
        val terminado: Boolean = false,
    )

    private val form = MutableStateFlow(Form())

    val estado: StateFlow<JefaturaUi> = combine(repo.estado, form) { e, f -> construir(e, f) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), JefaturaUi())

    fun elegirDepartamento(id: String) = form.update { Form(departamento = id, nuevo = it.nuevo) }
    fun elegirNuevo(uid: String) = form.update { it.copy(nuevo = uid, elecciones = emptyMap()) }
    fun elegirVacante(dep: String, opcion: String) = form.update { it.copy(elecciones = it.elecciones + (dep to opcion)) }

    /** Calcula las asignaciones aplicando la elección principal y las de cada vacante. */
    private fun calcular(e: EstadoTienda, f: Form): Triple<Map<String, String?>, Set<String>, List<String>> {
        var a: Map<String, String?> = e.departamentos.mapValues { it.value.jefeUid }
        val aCargo = mutableSetOf<String>()
        if (f.departamento != null && f.nuevo != null) a = Jefaturas.asignar(a, f.departamento, f.nuevo)
        val pendientes = mutableListOf<String>()
        repeat(e.departamentos.size + 1) {
            val vacantes = Jefaturas.vacantes(a, aCargo)
            pendientes.clear()
            for (v in vacantes) {
                when (val opcion = f.elecciones[v]) {
                    null -> pendientes += v
                    A_CARGO_DE_TIENDA -> aCargo += v
                    else -> if (a[v] == null) a = Jefaturas.asignar(a, v, opcion)
                }
            }
        }
        return Triple(a, aCargo, Jefaturas.vacantes(a, aCargo))
    }

    fun confirmar() {
        val actor = sesion.usuario.value ?: return
        val (a, _, pendientes) = calcular(repo.estado.value, form.value)
        if (pendientes.isNotEmpty()) return
        viewModelScope.launch {
            val error = personal.cambiarJefaturas(actor, a)
            if (error == null) form.update { it.copy(terminado = true) } else mensajeFlujo.value = error
        }
    }

    private fun construir(e: EstadoTienda, f: Form): JefaturaUi {
        val nombre = { uid: String? -> uid?.let { e.usuarios[it]?.nombre } ?: "A cargo del jefe de tienda" }
        val depNombre = { id: String -> e.departamentos[id]?.nombre ?: id }
        val jefes = e.jefesDeDepartamento()
        val aviso = f.departamento?.let { d ->
            val actual = e.departamentos[d]?.jefeUid
            if (actual != null && actual != f.nuevo) "${depNombre(d)} ya tiene jefe de departamento: ${nombre(actual)}." else null
        }
        val (a, aCargo, pendientes) = calcular(e, f)
        val jefesIds = jefes.map { it.uid }
        val orden = e.departamentos.keys.toList()
        val mostrar = if (f.departamento != null && f.nuevo != null) {
            (f.elecciones.keys + Jefaturas.vacantes(a, aCargo)).distinct().sortedBy { orden.indexOf(it) }
        } else {
            emptyList()
        }
        val sinAsignar = Jefaturas.sinAsignar(a, jefesIds)
        val vacantes = mostrar.map { v ->
            val candidatos = (sinAsignar + jefesIds).distinct().filter { it != f.nuevo }
            val tienda = if (Jefaturas.puedeQuedarACargoDelJefeDeTienda(a, jefesIds) || f.elecciones[v] == A_CARGO_DE_TIENDA) {
                listOf(A_CARGO_DE_TIENDA to "A cargo del jefe de tienda")
            } else {
                emptyList()
            }
            VacanteUi(
                v, depNombre(v), candidatos.map { it to nombre(it) } + tienda, f.elecciones[v],
                if (v in pendientes) "Seleccione quién queda a cargo de ${depNombre(v)}" else null,
            )
        }
        val resumen = e.departamentos.values.map { d -> Triple(d.nombre, nombre(d.jefeUid), nombre(a[d.id])) }
        val hayCambio = e.departamentos.values.any { it.jefeUid != a[it.id] }
        return JefaturaUi(
            departamentos = e.departamentos.values.map { it.id to it.nombre },
            jefes = jefes.map { it.uid to it.nombre },
            departamento = f.departamento,
            nuevoJefe = f.nuevo,
            aviso = aviso,
            vacantes = vacantes,
            resumen = resumen,
            puedeConfirmar = f.departamento != null && f.nuevo != null && pendientes.isEmpty() && hayCambio,
            terminado = f.terminado,
        )
    }
}

// ---------------------------------------------------------------------- UI-17 Historial del personal (F-10)

data class EntradaHistorialUi(val hora: String, val titulo: String, val detalle: String, val tipo: String)

data class HistorialUi(
    val fecha: String = "",
    val errorFecha: String? = null,
    val personas: List<Pair<String, String>> = emptyList(),
    val persona: String? = null,
    val entradas: List<EntradaHistorialUi> = emptyList(),
    val cargando: Boolean = true,
)

/**
 * RF-35: marcas de turno, movimientos, solicitudes y jefaturas de un día, leídos de
 * Room. [personaInicial] llega desde UI-13 con "Ver historial".
 */
@OptIn(ExperimentalCoroutinesApi::class)
class HistorialViewModel(
    personaInicial: String?,
    private val repo: RepositorioTienda,
    private val sesion: RepositorioSesion,
    private val historial: RepositorioHistorial,
    private val reloj: Reloj,
) : ViewModel() {
    private val formato = SimpleDateFormat("yyyy-MM-dd", Locale.US)
    private val fecha = MutableStateFlow(reloj.hoy())
    private val persona = MutableStateFlow(personaInicial)

    // Cada cambio en el personal vuelve a leer la base de datos.
    private val cambios = repo.estado.map { Triple(it.turnos.size + it.turnos.values.count { t -> t.salida != null }, it.movimientos.size, it.solicitudes.values.map { s -> s.estado }) }
        .distinctUntilChanged()

    val estado: StateFlow<HistorialUi> = combine(fecha, persona, cambios) { f, p, _ -> f to p }
        .mapLatest { (f, p) ->
            delay(300L) // espera a que Room guarde el último cambio
            construir(f, p)
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), HistorialUi(fecha = reloj.hoy()))

    fun elegirFecha(f: String) {
        fecha.value = f
    }

    /** El selector de fecha entrega milisegundos en UTC. */
    fun elegirFechaMs(utcMs: Long) {
        val utc = SimpleDateFormat("yyyy-MM-dd", Locale.US).apply { timeZone = java.util.TimeZone.getTimeZone("UTC") }
        fecha.value = utc.format(java.util.Date(utcMs))
    }

    fun elegirPersona(uid: String?) {
        persona.value = uid
    }

    private suspend fun construir(f: String, p: String?): HistorialUi {
        val e = repo.estado.value
        val u = sesion.usuario.value
        val error = Validaciones.fechaNoFutura(f, reloj.hoy())
        val visibles = e.usuarios.values.filter { u?.rol == Rol.JEFE_TIENDA || it.departamentoId == u?.departamentoId }
            .sortedBy { it.nombre }
        val base = HistorialUi(fecha = f, errorFecha = error, personas = visibles.map { it.uid to it.nombre }, persona = p, cargando = false)
        if (error != null) return base
        val inicio = Calendar.getInstance().apply {
            time = formato.parse(f) ?: return base
        }.timeInMillis
        val fin = inicio + 24 * 60 * 60_000L
        val r = historial.delDia(inicio, fin)
        val nombre = { uid: String? -> uid?.let { e.usuarios[it]?.nombre } ?: "Jefe de tienda" }
        val dep = { id: String? -> id?.let { e.departamentos[it]?.nombre } ?: "-" }
        val idsVisibles = visibles.map { it.uid }.toSet()
        val entradas = buildList {
            r.turnos.forEach { t ->
                add(t.entrada to EntradaHistorialUi(hora(t.entrada), "${nombre(t.usuarioUid)} marcó entrada", "", "TURNO") to setOf(t.usuarioUid))
                t.salida?.let { s ->
                    add(s to EntradaHistorialUi(hora(s), "${nombre(t.usuarioUid)} marcó salida", if (t.cierreAutomatico) "Cierre automático" else "", "TURNO") to setOf(t.usuarioUid))
                }
            }
            r.movimientos.forEach { m ->
                add(m.hora to EntradaHistorialUi(hora(m.hora), "${nombre(m.usuarioUid)} pasó de ${dep(m.origenId)} a ${dep(m.destinoId)}",
                    "${m.tipo.texto} · por ${nombre(m.autorUid)}", "MOVIMIENTO") to setOf(m.usuarioUid, m.autorUid))
            }
            r.solicitudes.forEach { s ->
                add(s.creada to EntradaHistorialUi(hora(s.creada), "${dep(s.departamentoOrigen)} pidió un vendedor a ${dep(s.departamentoPedido)}",
                    "Estado: ${s.estado.texto}" + (s.motivo?.let { " · Motivo: $it" } ?: "") + (s.vendedorUid?.let { " · Se cedió a ${nombre(it)}" } ?: ""),
                    "SOLICITUD") to setOfNotNull(s.solicitanteUid, s.respondidaPor, s.vendedorUid))
            }
            r.jefaturas.forEach { j ->
                add(j.hora to EntradaHistorialUi(hora(j.hora), "Jefatura de ${dep(j.departamentoId)}: ${nombre(j.anteriorUid)} → ${nombre(j.nuevoUid)}",
                    "Por ${nombre(j.autorUid)}", "JEFATURA") to setOfNotNull(j.anteriorUid, j.nuevoUid, j.autorUid))
            }
        }
            .filter { (_, involucrados) -> if (p != null) p in involucrados else involucrados.any { it in idsVisibles } }
            .sortedBy { it.first.first }
            .map { it.first.second }
        return base.copy(entradas = entradas)
    }
}

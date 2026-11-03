package cl.duoc.probadorapp.engine

import cl.duoc.probadorapp.data.repository.EstadoTienda
import cl.duoc.probadorapp.data.repository.RepositorioTienda
import cl.duoc.probadorapp.domain.Reloj
import cl.duoc.probadorapp.domain.model.Alerta
import cl.duoc.probadorapp.domain.model.CambioJefatura
import cl.duoc.probadorapp.domain.model.EstadoAlerta
import cl.duoc.probadorapp.domain.model.EstadoSolicitud
import cl.duoc.probadorapp.domain.model.Movimiento
import cl.duoc.probadorapp.domain.model.Rol
import cl.duoc.probadorapp.domain.model.SolicitudRefuerzo
import cl.duoc.probadorapp.domain.model.TipoAlerta
import cl.duoc.probadorapp.domain.model.TipoMovimiento
import cl.duoc.probadorapp.domain.model.Usuario
import cl.duoc.probadorapp.domain.rules.Reglas
import cl.duoc.probadorapp.domain.rules.ResultadoMovimiento
import cl.duoc.probadorapp.domain.validation.Validaciones
import java.util.UUID

/**
 * Módulo de personal (RF-29 a RF-35): movimientos, solicitudes de refuerzo,
 * cambios de jefatura y alertas de cobertura. Cada acción devuelve null si se
 * aplicó o el mensaje de error que se muestra al usuario (RN-34).
 */
class GestionPersonal(
    private val repo: RepositorioTienda,
    private val reloj: Reloj,
) {
    private fun nuevoId() = UUID.randomUUID().toString()

    private fun nombreDep(e: EstadoTienda, id: String?) = id?.let { e.departamentos[it]?.nombre } ?: "-"

    // ------------------------------------------------------------------ RF-31 Mover persona (F-06)

    /**
     * Valida un movimiento sin aplicarlo: devuelve el mensaje de error, o null.
     * Para el jefe de tienda que deja el origen bajo el mínimo, [advertencia] trae el aviso.
     */
    data class Revision(val error: String?, val advertencia: String?)

    fun revisarMovimiento(e: EstadoTienda, actor: Usuario, uid: String, destino: String?): Revision {
        val persona = e.usuarios[uid] ?: return Revision("La persona no existe", null)
        if (destino == null) return Revision("Seleccione el departamento de destino", null)
        if (persona.rol != Rol.VENDEDOR) return Revision("Solo se mueven vendedores. Los jefes cambian con el cambio de jefatura.", null)
        val origen = persona.departamentoId ?: return Revision("La persona no tiene departamento", null)
        val enTurno = e.turnoAbierto(uid) != null
        val coberturaDespues = e.cobertura(origen) - if (enTurno) 1 else 0
        val minimo = e.departamentos[origen]?.minimoPersonal ?: 0
        return when (Reglas.movimientoPermitido(actor.rol, actor.departamentoId, origen, destino, coberturaDespues, minimo)) {
            ResultadoMovimiento.PERMITIDO -> Revision(null, null)
            ResultadoMovimiento.PERMITIDO_CON_ADVERTENCIA ->
                Revision(null, "${nombreDep(e, origen)} quedará con $coberturaDespues vendedores en turno, bajo el mínimo de $minimo.")
            ResultadoMovimiento.BLOQUEADO_BAJO_MINIMO -> Revision("${nombreDep(e, origen)} quedaría bajo su mínimo de $minimo", null)
            ResultadoMovimiento.MISMO_DEPARTAMENTO -> Revision("Elija un departamento distinto del actual", null)
            ResultadoMovimiento.NO_AUTORIZADO ->
                Revision("Solo puede ceder vendedores de su departamento. Para pedir personal, envíe una solicitud de refuerzo.", null)
        }
    }

    suspend fun moverPersona(actor: Usuario, uid: String, destino: String): String? = repo.transaccion { e ->
        val r = revisarMovimiento(e, actor, uid, destino)
        if (r.error != null) return@transaccion e to r.error
        val tipo = if (actor.rol == Rol.JEFE_TIENDA) TipoMovimiento.DIRECTO else TipoMovimiento.CESION
        mover(e, uid, destino, tipo, actor.uid) to null
    }

    private fun mover(e: EstadoTienda, uid: String, destino: String, tipo: TipoMovimiento, autor: String): EstadoTienda {
        val u = e.usuarios.getValue(uid)
        val m = Movimiento(nuevoId(), uid, u.departamentoId, destino, tipo, reloj.ahora(), autor)
        return e.copy(
            usuarios = e.usuarios + (uid to u.copy(departamentoId = destino)),
            movimientos = e.movimientos + (m.id to m),
        )
    }

    // ------------------------------------------------------------------ RF-32 Solicitudes de refuerzo (F-07 y F-08)

    fun revisarSolicitud(e: EstadoTienda, actor: Usuario, pedido: String?): String? {
        if (pedido == null) return "Seleccione un departamento"
        if (actor.rol != Rol.JEFE_DEPARTAMENTO || actor.departamentoId == null) return "Solo un jefe de departamento envía solicitudes"
        if (pedido == actor.departamentoId) return "Elija otro departamento"
        val pendiente = e.solicitudes.values.any { it.abierta && it.departamentoOrigen == actor.departamentoId && it.departamentoPedido == pedido }
        return if (pendiente) "Ya hay una solicitud pendiente para ${nombreDep(e, pedido)}" else null
    }

    suspend fun solicitarRefuerzo(actor: Usuario, pedido: String): String? = repo.transaccion { e ->
        revisarSolicitud(e, actor, pedido)?.let { return@transaccion e to it }
        val s = SolicitudRefuerzo(nuevoId(), actor.uid, actor.departamentoId!!, pedido, EstadoSolicitud.PENDIENTE, reloj.ahora())
        val e2 = e.copy(solicitudes = e.solicitudes + (s.id to s))
        crearAlerta(e2, pedido, TipoAlerta.SOLICITUD_REFUERZO, "${nombreDep(e, s.departamentoOrigen)} pide un vendedor", s.id) to null
    }

    /** Quién puede responder: el jefe del departamento pedido (si no está escalada) o el jefe de tienda. */
    fun puedeResponder(actor: Usuario, s: SolicitudRefuerzo): Boolean = s.abierta && when (actor.rol) {
        Rol.JEFE_TIENDA -> true
        Rol.JEFE_DEPARTAMENTO -> s.estado == EstadoSolicitud.PENDIENTE && actor.departamentoId == s.departamentoPedido
        Rol.VENDEDOR -> false
    }

    /** F-08 al aceptar: valida al vendedor elegido. */
    fun revisarAceptacion(e: EstadoTienda, s: SolicitudRefuerzo, vendedorUid: String?): String? {
        if (vendedorUid == null) return "Seleccione a quién envía"
        val v = e.usuarios[vendedorUid] ?: return "Seleccione a quién envía"
        if (v.rol != Rol.VENDEDOR || v.departamentoId != s.departamentoPedido) return "Elija un vendedor de ${nombreDep(e, s.departamentoPedido)}"
        val minimo = e.departamentos[s.departamentoPedido]?.minimoPersonal ?: 0
        val despues = e.cobertura(s.departamentoPedido) - if (e.turnoAbierto(vendedorUid) != null) 1 else 0
        return if (despues < minimo) "${nombreDep(e, s.departamentoPedido)} quedaría bajo su mínimo de $minimo" else null
    }

    suspend fun aceptarSolicitud(actor: Usuario, id: String, vendedorUid: String): String? = repo.transaccion { e ->
        val s = e.solicitudes[id] ?: return@transaccion e to "La solicitud no existe"
        if (!s.abierta) return@transaccion e to "Esta solicitud ya fue respondida"
        if (!puedeResponder(actor, s)) return@transaccion e to "No puede responder esta solicitud"
        revisarAceptacion(e, s, vendedorUid)?.let { return@transaccion e to it }
        val respondida = s.copy(estado = EstadoSolicitud.ACEPTADA, respuesta = reloj.ahora(), respondidaPor = actor.uid, vendedorUid = vendedorUid)
        var e2 = mover(e, vendedorUid, s.departamentoOrigen, TipoMovimiento.SOLICITUD, actor.uid)
        e2 = e2.copy(solicitudes = e2.solicitudes + (id to respondida))
        cerrarAlertasDe(e2, id) to null
    }

    suspend fun rechazarSolicitud(actor: Usuario, id: String, motivo: String): String? = repo.transaccion { e ->
        val s = e.solicitudes[id] ?: return@transaccion e to "La solicitud no existe"
        if (!s.abierta) return@transaccion e to "Esta solicitud ya fue respondida"
        if (!puedeResponder(actor, s)) return@transaccion e to "No puede responder esta solicitud"
        Validaciones.textoConLargo(motivo, 5, 100, "Indique el motivo")?.let { return@transaccion e to it }
        val respondida = s.copy(estado = EstadoSolicitud.RECHAZADA, respuesta = reloj.ahora(), respondidaPor = actor.uid, motivo = motivo.trim())
        cerrarAlertasDe(e.copy(solicitudes = e.solicitudes + (id to respondida)), id) to null
    }

    /** RN-28: el jefe que la envió la cancela mientras no tenga respuesta. */
    suspend fun cancelarSolicitud(actor: Usuario, id: String): String? = repo.transaccion { e ->
        val s = e.solicitudes[id] ?: return@transaccion e to "La solicitud no existe"
        if (!s.abierta) return@transaccion e to "Esta solicitud ya fue respondida"
        if (s.solicitanteUid != actor.uid) return@transaccion e to "Solo quien la envió puede cancelarla"
        val cancelada = s.copy(estado = EstadoSolicitud.CANCELADA, respuesta = reloj.ahora(), respondidaPor = actor.uid)
        cerrarAlertasDe(e.copy(solicitudes = e.solicitudes + (id to cancelada)), id) to null
    }

    // ------------------------------------------------------------------ RF-34 Cambio de jefatura (F-09)

    /**
     * Aplica las nuevas jefaturas. [asignaciones] tiene el jefe de cada departamento,
     * o null si queda a cargo del jefe de tienda.
     */
    suspend fun cambiarJefaturas(actor: Usuario, asignaciones: Map<String, String?>): String? = repo.transaccion { e ->
        if (actor.rol != Rol.JEFE_TIENDA) return@transaccion e to "Solo el jefe de tienda cambia las jefaturas"
        val aCargo = asignaciones.filterValues { it == null }.keys
        val (sinJefe, repetidos) = Reglas.revisarJefaturas(asignaciones, aCargo)
        if (sinJefe.isNotEmpty()) return@transaccion e to "Seleccione quién queda a cargo de ${nombreDep(e, sinJefe.first())}"
        if (repetidos.isNotEmpty()) return@transaccion e to "Un jefe no puede dirigir dos departamentos"
        val ahora = reloj.ahora()
        var s = e
        for ((dep, nuevo) in asignaciones) {
            val d = s.departamentos[dep] ?: continue
            if (d.jefeUid == nuevo) continue
            val c = CambioJefatura(nuevoId(), dep, d.jefeUid, nuevo, ahora, actor.uid)
            s = s.copy(
                departamentos = s.departamentos + (dep to d.copy(jefeUid = nuevo)),
                jefaturas = s.jefaturas + (c.id to c),
            )
            if (nuevo != null) {
                val jefe = s.usuarios[nuevo]
                if (jefe != null && jefe.departamentoId != dep) {
                    val m = Movimiento(nuevoId(), nuevo, jefe.departamentoId, dep, TipoMovimiento.JEFATURA, ahora, actor.uid)
                    s = s.copy(usuarios = s.usuarios + (nuevo to jefe.copy(departamentoId = dep)), movimientos = s.movimientos + (m.id to m))
                }
            }
        }
        s to null
    }

    // ------------------------------------------------------------------ Revisión periódica

    /**
     * Cada segundo: RN-32 alerta de cobertura baja (una mientras dure) y
     * RN-28 escalamiento de solicitudes sin respuesta a los 10 minutos.
     */
    suspend fun revisar() {
        val ahora = reloj.ahora()
        repo.transaccion { e ->
            var s = e
            for (dep in s.departamentos.values) {
                val cobertura = s.cobertura(dep.id)
                val activa = s.alertas.values.firstOrNull { it.tipo == TipoAlerta.COBERTURA_BAJA && it.departamentoId == dep.id && it.estado == EstadoAlerta.ACTIVA }
                if (!Reglas.cumpleMinimo(cobertura, dep.minimoPersonal)) {
                    if (activa == null) {
                        s = crearAlerta(s, dep.id, TipoAlerta.COBERTURA_BAJA, "$cobertura vendedores en turno, mínimo ${dep.minimoPersonal}", null)
                    }
                } else if (activa != null) {
                    s = s.copy(alertas = s.alertas + (activa.id to activa.copy(estado = EstadoAlerta.CERRADA, cierre = ahora)))
                }
            }
            for (sol in s.solicitudes.values.filter { it.estado == EstadoSolicitud.PENDIENTE && Reglas.solicitudVencida(it.creada, ahora) }) {
                s = s.copy(solicitudes = s.solicitudes + (sol.id to sol.copy(estado = EstadoSolicitud.ESCALADA)))
                val tienda = s.jefeDeTienda()?.uid
                s = s.copy(alertas = s.alertas.mapValues { (_, a) ->
                    if (a.referenciaId == sol.id && a.estado == EstadoAlerta.ACTIVA) {
                        a.copy(destinatarios = Reglas.destinatarios(TipoAlerta.SOLICITUD_REFUERZO, emptyList(), null, tienda, escalada = true),
                            detalle = a.detalle + " (escalada)")
                    } else {
                        a
                    }
                })
            }
            s to Unit
        }
    }

    private fun crearAlerta(e: EstadoTienda, dep: String, tipo: TipoAlerta, detalle: String, referencia: String?): EstadoTienda {
        val dest = Reglas.destinatarios(tipo, e.vendedoresEnTurno(dep).map { it.uid }, e.departamentos[dep]?.jefeUid, e.jefeDeTienda()?.uid)
        val a = Alerta(nuevoId(), dep, null, tipo, detalle, reloj.ahora(), dest, referenciaId = referencia)
        return e.copy(alertas = e.alertas + (a.id to a))
    }

    private fun cerrarAlertasDe(e: EstadoTienda, referencia: String): EstadoTienda = e.copy(
        alertas = e.alertas.mapValues { (_, a) ->
            if (a.referenciaId == referencia && a.estado == EstadoAlerta.ACTIVA) a.copy(estado = EstadoAlerta.CERRADA, cierre = reloj.ahora()) else a
        },
    )
}

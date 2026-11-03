package cl.duoc.probadorapp.ui.viewmodel

import cl.duoc.probadorapp.data.repository.EstadoTienda
import cl.duoc.probadorapp.domain.model.EstadoAlerta
import cl.duoc.probadorapp.domain.model.Rol
import cl.duoc.probadorapp.domain.model.Usuario
import cl.duoc.probadorapp.engine.GestionPersonal

/** Cantidades que se muestran como insignia en el menú. */
data class Pendientes(val alertas: Int = 0, val solicitudes: Int = 0) {
    val total: Int get() = alertas + solicitudes
}

/**
 * Alertas activas dirigidas a la persona que todavía no marcó como vistas,
 * y solicitudes de refuerzo que puede responder (RN-28 y RN-31).
 */
fun pendientesDe(e: EstadoTienda, u: Usuario?, personal: GestionPersonal): Pendientes {
    if (u == null) return Pendientes()
    val alertas = e.alertas.values.count {
        it.estado == EstadoAlerta.ACTIVA && u.uid !in it.vistaPor &&
            (u.uid in it.destinatarios || u.rol == Rol.JEFE_TIENDA)
    }
    val solicitudes = e.solicitudes.values.count { personal.puedeResponder(u, it) }
    return Pendientes(alertas, solicitudes)
}

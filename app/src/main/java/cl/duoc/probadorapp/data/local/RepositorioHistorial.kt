package cl.duoc.probadorapp.data.local

import cl.duoc.probadorapp.domain.model.CambioJefatura
import cl.duoc.probadorapp.domain.model.Movimiento
import cl.duoc.probadorapp.domain.model.SolicitudRefuerzo
import cl.duoc.probadorapp.domain.model.Turno

/** Registros de un día para el historial del personal (RF-35, F-10). */
data class RegistrosDelDia(
    val turnos: List<Turno>,
    val movimientos: List<Movimiento>,
    val solicitudes: List<SolicitudRefuerzo>,
    val jefaturas: List<CambioJefatura>,
)

/** Lee de Room el historial de un día: [desde] y [hasta] son el inicio y el fin del día. */
class RepositorioHistorial(private val db: BaseDatosLocal) {
    suspend fun delDia(desde: Long, hasta: Long): RegistrosDelDia = RegistrosDelDia(
        turnos = db.turnoDao().entre(desde, hasta).map { it.aModelo() },
        movimientos = db.movimientoDao().entre(desde, hasta).map { it.aModelo() },
        solicitudes = db.solicitudDao().entre(desde, hasta).map { it.aModelo() },
        jefaturas = db.jefaturaDao().entre(desde, hasta).map { it.aModelo() },
    )
}

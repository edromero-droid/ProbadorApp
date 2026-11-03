package cl.duoc.probadorapp.data.local

import androidx.room.Dao
import androidx.room.Entity
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.PrimaryKey
import androidx.room.Query
import cl.duoc.probadorapp.domain.model.CambioJefatura
import cl.duoc.probadorapp.domain.model.EstadoSolicitud
import cl.duoc.probadorapp.domain.model.Movimiento
import cl.duoc.probadorapp.domain.model.SolicitudRefuerzo
import cl.duoc.probadorapp.domain.model.TipoMovimiento

// ---------------------------------------------------------------------- F-06 Mover persona

/** Tabla `movimientos`: cada cambio de departamento de una persona. */
@Entity(tableName = "movimientos")
data class MovimientoEntity(
    @PrimaryKey val id: String,
    val usuarioUid: String,
    val origenId: String?,
    val destinoId: String?,
    val tipo: String,
    val hora: Long,
    val autorUid: String,
)

/** DAO del formulario F-06 Mover persona. */
@Dao
interface MovimientoDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun guardar(movimientos: List<MovimientoEntity>)

    @Query("SELECT * FROM movimientos WHERE hora >= :desde AND hora < :hasta ORDER BY hora")
    suspend fun entre(desde: Long, hasta: Long): List<MovimientoEntity>
}

fun Movimiento.aEntidad() = MovimientoEntity(id, usuarioUid, origenId, destinoId, tipo.name, hora, autorUid)

fun MovimientoEntity.aModelo() = Movimiento(id, usuarioUid, origenId, destinoId, TipoMovimiento.valueOf(tipo), hora, autorUid)

// ---------------------------------------------------------------------- F-07 y F-08 Solicitudes de refuerzo

/** Tabla `solicitudes`: solicitudes de refuerzo con su respuesta. */
@Entity(tableName = "solicitudes")
data class SolicitudEntity(
    @PrimaryKey val id: String,
    val solicitanteUid: String,
    val departamentoOrigen: String,
    val departamentoPedido: String,
    val estado: String,
    val creada: Long,
    val respuesta: Long?,
    val respondidaPor: String?,
    val vendedorUid: String?,
    val motivo: String?,
)

/** DAO de los formularios F-07 Solicitar refuerzo y F-08 Responder solicitud. */
@Dao
interface SolicitudDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun guardar(solicitudes: List<SolicitudEntity>)

    @Query("SELECT * FROM solicitudes WHERE creada >= :desde AND creada < :hasta ORDER BY creada")
    suspend fun entre(desde: Long, hasta: Long): List<SolicitudEntity>

    /** Solicitudes sin respuesta y las de hoy. */
    @Query("SELECT * FROM solicitudes WHERE estado IN ('PENDIENTE', 'ESCALADA') OR creada >= :desde")
    suspend fun vigentes(desde: Long): List<SolicitudEntity>
}

fun SolicitudRefuerzo.aEntidad() = SolicitudEntity(
    id, solicitanteUid, departamentoOrigen, departamentoPedido, estado.name, creada, respuesta, respondidaPor, vendedorUid, motivo,
)

fun SolicitudEntity.aModelo() = SolicitudRefuerzo(
    id, solicitanteUid, departamentoOrigen, departamentoPedido, EstadoSolicitud.valueOf(estado), creada, respuesta, respondidaPor, vendedorUid, motivo,
)

// ---------------------------------------------------------------------- F-09 Cambio de jefatura

/** Tabla `jefaturas`: historial de cambios del jefe de cada departamento. */
@Entity(tableName = "jefaturas")
data class JefaturaEntity(
    @PrimaryKey val id: String,
    val departamentoId: String,
    val anteriorUid: String?,
    val nuevoUid: String?,
    val hora: Long,
    val autorUid: String,
)

/** Tabla `jefes_actuales`: jefe vigente de cada departamento (null: a cargo del jefe de tienda). */
@Entity(tableName = "jefes_actuales")
data class JefeActualEntity(
    @PrimaryKey val departamentoId: String,
    val jefeUid: String?,
)

/** DAO del formulario F-09 Cambio de jefatura. */
@Dao
interface JefaturaDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun guardar(cambios: List<JefaturaEntity>)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun guardarJefes(jefes: List<JefeActualEntity>)

    @Query("SELECT * FROM jefaturas WHERE hora >= :desde AND hora < :hasta ORDER BY hora")
    suspend fun entre(desde: Long, hasta: Long): List<JefaturaEntity>

    @Query("SELECT * FROM jefes_actuales")
    suspend fun jefes(): List<JefeActualEntity>
}

fun CambioJefatura.aEntidad() = JefaturaEntity(id, departamentoId, anteriorUid, nuevoUid, hora, autorUid)

fun JefaturaEntity.aModelo() = CambioJefatura(id, departamentoId, anteriorUid, nuevoUid, hora, autorUid)

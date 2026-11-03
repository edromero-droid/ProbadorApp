package cl.duoc.probadorapp.data.local

import androidx.room.Dao
import androidx.room.Entity
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.PrimaryKey
import androidx.room.Query
import cl.duoc.probadorapp.domain.model.Turno

/** Tabla `turnos`: marcas de entrada y salida del personal. */
@Entity(tableName = "turnos")
data class TurnoEntity(
    @PrimaryKey val id: String,
    val usuarioUid: String,
    val entrada: Long,
    val salida: Long?,
    val cierreAutomatico: Boolean,
)

/** DAO del formulario F-02 Marcar turno. */
@Dao
interface TurnoDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun guardar(turnos: List<TurnoEntity>)

    @Query("DELETE FROM turnos WHERE id IN (:ids)")
    suspend fun borrar(ids: List<String>)

    /** Turnos abiertos y los de hoy. */
    @Query("SELECT * FROM turnos WHERE salida IS NULL OR entrada >= :desde ORDER BY entrada")
    suspend fun vigentes(desde: Long): List<TurnoEntity>

    /** Turnos que empezaron en un día, para el historial (F-10). */
    @Query("SELECT * FROM turnos WHERE entrada >= :desde AND entrada < :hasta ORDER BY entrada")
    suspend fun entre(desde: Long, hasta: Long): List<TurnoEntity>

    @Query("SELECT COUNT(*) FROM turnos")
    suspend fun contar(): Int
}

fun Turno.aEntidad() = TurnoEntity(id, usuarioUid, entrada, salida, cierreAutomatico)

fun TurnoEntity.aModelo() = Turno(id, usuarioUid, entrada, salida, cierreAutomatico)

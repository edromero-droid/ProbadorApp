package cl.duoc.probadorapp.data.local

import androidx.room.Dao
import androidx.room.Entity
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.PrimaryKey
import androidx.room.Query
import cl.duoc.probadorapp.domain.model.Probador

/** Tabla `probadores`: configuración de los dispositivos de cada probador. */
@Entity(tableName = "probadores")
data class ProbadorEntity(
    @PrimaryKey val id: String,
    val departamentoId: String,
    val codigo: String,
    val sensorCodigo: String,
    val lectorCodigo: String,
    val activo: Boolean,
)

/** DAO del formulario F-11 Configurar probador. */
@Dao
interface ProbadorDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun guardar(probadores: List<ProbadorEntity>)

    @Query("SELECT * FROM probadores")
    suspend fun todos(): List<ProbadorEntity>
}

fun Probador.aConfiguracion() = ProbadorEntity(id, departamentoId, codigo, sensorCodigo, lectorCodigo, activo)

fun ProbadorEntity.aModelo() = Probador(id, departamentoId, codigo, sensorCodigo, lectorCodigo, activo)

/** El probador cambió en sus códigos o en si está activo (no en su estado de uso). */
fun cambioConfiguracion(a: Probador?, b: Probador): Boolean =
    a == null || a.sensorCodigo != b.sensorCodigo || a.lectorCodigo != b.lectorCodigo || a.activo != b.activo

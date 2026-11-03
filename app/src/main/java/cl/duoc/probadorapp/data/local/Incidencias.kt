package cl.duoc.probadorapp.data.local

import androidx.room.Dao
import androidx.room.Entity
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.PrimaryKey
import androidx.room.Query
import cl.duoc.probadorapp.domain.model.Incidencia
import cl.duoc.probadorapp.domain.model.TipoIncidencia

/** Tabla `incidencias`: datos del formulario de incidencia, con la ruta de la foto. */
@Entity(tableName = "incidencias")
data class IncidenciaEntity(
    @PrimaryKey val id: String,
    val probadorId: String,
    val tipo: String,
    val descripcion: String,
    val fotoUri: String?,
    val usuarioUid: String,
    val fecha: Long,
    val abierta: Boolean,
    val cierre: Long?,
)

/** DAO del formulario F-04 Reportar incidencia. */
@Dao
interface IncidenciaDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun guardar(incidencias: List<IncidenciaEntity>)

    @Query("DELETE FROM incidencias WHERE id IN (:ids)")
    suspend fun borrar(ids: List<String>)

    @Query("SELECT * FROM incidencias ORDER BY fecha DESC")
    suspend fun todas(): List<IncidenciaEntity>
}

fun Incidencia.aEntidad() = IncidenciaEntity(
    id, probadorId, tipo.name, descripcion, fotoUri, usuarioUid, fecha, abierta, cierre,
)

fun IncidenciaEntity.aModelo() = Incidencia(
    id = id, probadorId = probadorId, tipo = TipoIncidencia.valueOf(tipo), descripcion = descripcion,
    fotoUri = fotoUri, usuarioUid = usuarioUid, fecha = fecha, abierta = abierta, cierre = cierre,
)

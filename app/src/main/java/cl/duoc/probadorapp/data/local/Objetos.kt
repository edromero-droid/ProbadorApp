package cl.duoc.probadorapp.data.local

import androidx.room.Dao
import androidx.room.Entity
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.PrimaryKey
import androidx.room.Query
import cl.duoc.probadorapp.domain.model.EstadoObjeto
import cl.duoc.probadorapp.domain.model.ObjetoEncontrado
import cl.duoc.probadorapp.domain.model.TipoObjeto

/** Tabla `objetos`: objetos personales encontrados, con la ruta de la foto. */
@Entity(tableName = "objetos")
data class ObjetoEntity(
    @PrimaryKey val id: String,
    val probadorId: String,
    val tipo: String,
    val descripcion: String,
    val fotoUri: String?,
    val ocupacionId: String?,
    val numero: Int?,
    val registradoPor: String,
    val fecha: Long,
    val estado: String,
    val cierre: Long?,
    val cerradoPor: String?,
)

/** DAO del formulario F-05 Registrar objeto encontrado. */
@Dao
interface ObjetoDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun guardar(objetos: List<ObjetoEntity>)

    @Query("DELETE FROM objetos WHERE id IN (:ids)")
    suspend fun borrar(ids: List<String>)

    @Query("SELECT * FROM objetos ORDER BY fecha DESC")
    suspend fun todos(): List<ObjetoEntity>
}

fun ObjetoEncontrado.aEntidad() = ObjetoEntity(
    id, probadorId, tipo.name, descripcion, fotoUri, ocupacionId, numero, registradoPor, fecha, estado.name, cierre, cerradoPor,
)

fun ObjetoEntity.aModelo() = ObjetoEncontrado(
    id, probadorId, TipoObjeto.valueOf(tipo), descripcion, fotoUri, ocupacionId, numero, registradoPor, fecha,
    EstadoObjeto.valueOf(estado), cierre, cerradoPor,
)

package cl.duoc.probadorapp.data.local

import androidx.room.Dao
import androidx.room.Entity
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.PrimaryKey
import androidx.room.Query
import cl.duoc.probadorapp.domain.model.ConfiguracionEquipo
import cl.duoc.probadorapp.domain.model.UsoEquipo

/** Tabla `equipo`: una sola fila (id = 1) con la tienda y el uso de este equipo. */
@Entity(tableName = "equipo")
data class EquipoEntity(
    @PrimaryKey val id: Int = 1,
    val tiendaId: String,
    val uso: String,
    val departamentoId: String?,
)

/** DAO del formulario F-03 Configuración del equipo. */
@Dao
interface EquipoDao {
    @Query("SELECT * FROM equipo WHERE id = 1")
    suspend fun leer(): EquipoEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun guardar(equipo: EquipoEntity)
}

fun ConfiguracionEquipo.aEntidad() = EquipoEntity(tiendaId = tiendaId, uso = uso.name, departamentoId = departamentoId)

fun EquipoEntity.aModelo() = ConfiguracionEquipo(tiendaId, UsoEquipo.valueOf(uso), departamentoId)

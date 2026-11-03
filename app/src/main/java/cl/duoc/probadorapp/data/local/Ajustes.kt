package cl.duoc.probadorapp.data.local

import androidx.room.Dao
import androidx.room.Entity
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.PrimaryKey
import androidx.room.Query
import androidx.room.Transaction
import cl.duoc.probadorapp.domain.model.Parametros

/** Tabla `parametros`: una sola fila (id = 1) con los parámetros de operación. */
@Entity(tableName = "parametros")
data class ParametrosEntity(
    @PrimaryKey val id: Int = 1,
    val tiempoMaximoMin: Int,
    val plazoLlamadoMin: Int,
    val bloqueoMin: Int,
    val anuncioVoz: Boolean,
)

/** Tabla `minimos_personal`: mínimo de vendedores en turno de cada departamento. */
@Entity(tableName = "minimos_personal")
data class MinimoPersonalEntity(
    @PrimaryKey val departamentoId: String,
    val minimo: Int,
)

/** DAO del formulario F-12 Ajustes. Es una clase abstracta para tener un método con @Transaction. */
@Dao
abstract class AjustesDao {
    @Query("SELECT * FROM parametros WHERE id = 1")
    abstract suspend fun parametros(): ParametrosEntity?

    @Query("SELECT * FROM minimos_personal")
    abstract suspend fun minimos(): List<MinimoPersonalEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    abstract suspend fun guardarParametros(parametros: ParametrosEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    abstract suspend fun guardarMinimos(minimos: List<MinimoPersonalEntity>)

    /** Guarda el formulario completo en una sola transacción. */
    @Transaction
    open suspend fun guardar(parametros: ParametrosEntity, minimos: List<MinimoPersonalEntity>) {
        guardarParametros(parametros)
        guardarMinimos(minimos)
    }
}

/** Valores del formulario F-12. */
data class AjustesGuardados(val parametros: Parametros, val minimos: Map<String, Int>)

/** Lee y guarda los ajustes con [AjustesDao]. */
class RepositorioAjustes(private val dao: AjustesDao) {

    /** Ajustes guardados; si no hay, usa los valores por defecto y los mínimos actuales. */
    suspend fun leer(minimosActuales: Map<String, Int>): AjustesGuardados {
        val p = dao.parametros()
        val guardados = dao.minimos().associate { it.departamentoId to it.minimo }
        return AjustesGuardados(
            parametros = p?.let { Parametros(it.tiempoMaximoMin, it.plazoLlamadoMin, it.bloqueoMin, it.anuncioVoz) } ?: Parametros(),
            minimos = minimosActuales.mapValues { (id, actual) -> guardados[id] ?: actual },
        )
    }

    suspend fun guardar(a: AjustesGuardados) {
        dao.guardar(
            ParametrosEntity(
                tiempoMaximoMin = a.parametros.tiempoMaximoMin,
                plazoLlamadoMin = a.parametros.plazoLlamadoMin,
                bloqueoMin = a.parametros.bloqueoMin,
                anuncioVoz = a.parametros.anuncioVoz,
            ),
            a.minimos.map { (id, n) -> MinimoPersonalEntity(id, n) },
        )
    }
}

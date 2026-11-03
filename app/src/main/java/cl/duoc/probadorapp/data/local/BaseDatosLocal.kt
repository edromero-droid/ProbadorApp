package cl.duoc.probadorapp.data.local

import android.content.Context
import androidx.room.Dao
import androidx.room.Database
import androidx.room.Entity
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.PrimaryKey
import androidx.room.Query
import androidx.room.Room
import androidx.room.RoomDatabase
import kotlinx.coroutines.flow.Flow

/** Catálogo de prendas con su etiqueta RFID (tabla `prendas` de 4.3.6). */
@Entity(tableName = "prendas")
data class PrendaEntity(
    @PrimaryKey val epc: String,
    val descripcion: String,
    val talla: String,
    val color: String,
)

/** Registro de las señales recibidas en las últimas 24 horas (tabla `senales`). */
@Entity(tableName = "senales")
data class SenalEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val dispositivo: String,
    val tipo: String,
    val hora: Long,
    val detalle: String,
)

@Dao
interface PrendaDao {
    @Query("SELECT * FROM prendas ORDER BY descripcion")
    suspend fun todas(): List<PrendaEntity>

    @Query("SELECT COUNT(*) FROM prendas")
    suspend fun contar(): Int

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertar(prendas: List<PrendaEntity>)
}

/** DAO del formulario F-13 Simulador: cada señal enviada queda registrada. */
@Dao
interface SenalDao {
    @Insert
    suspend fun insertar(senal: SenalEntity)

    @Query("SELECT * FROM senales ORDER BY hora DESC, id DESC LIMIT :cantidad")
    fun ultimas(cantidad: Int): Flow<List<SenalEntity>>

    @Query("DELETE FROM senales WHERE hora < :limite")
    suspend fun borrarAnteriores(limite: Long)

    @Query("DELETE FROM senales")
    suspend fun borrarTodo()
}

/**
 * Base de datos SQLite del equipo (Room). Cada formulario guarda sus datos con su propio DAO:
 *
 * | Formulario | DAO | Tablas |
 * |---|---|---|
 * | F-01 Iniciar sesión (y desbloqueo) | UsuarioDao | usuarios |
 * | F-02 Marcar turno | TurnoDao | turnos |
 * | F-03 Configuración del equipo | EquipoDao | equipo |
 * | F-04 Reportar incidencia | IncidenciaDao | incidencias |
 * | F-05 Registrar objeto encontrado | ObjetoDao | objetos |
 * | F-06 Mover persona | MovimientoDao | movimientos |
 * | F-07 y F-08 Solicitudes de refuerzo | SolicitudDao | solicitudes |
 * | F-09 Cambio de jefatura | JefaturaDao | jefaturas, jefes_actuales |
 * | F-10 Filtro del historial | (lee TurnoDao, MovimientoDao, SolicitudDao y JefaturaDao) | |
 * | F-11 Configurar probador | ProbadorDao | probadores |
 * | F-12 Ajustes | AjustesDao | parametros, minimos_personal |
 * | F-13 Simulador | SenalDao | senales |
 *
 * Además guarda los datos de la operación: catálogo de prendas (PrendaDao),
 * números de la cola (NumeroDao) y ocupaciones (OcupacionDao).
 */
@Database(
    entities = [
        UsuarioEntity::class, TurnoEntity::class, EquipoEntity::class, IncidenciaEntity::class,
        ObjetoEntity::class, MovimientoEntity::class, SolicitudEntity::class, JefaturaEntity::class,
        JefeActualEntity::class, ProbadorEntity::class, ParametrosEntity::class, MinimoPersonalEntity::class,
        SenalEntity::class, PrendaEntity::class, NumeroEntity::class, OcupacionEntity::class,
    ],
    version = 4,
    exportSchema = true,
)
abstract class BaseDatosLocal : RoomDatabase() {
    abstract fun usuarioDao(): UsuarioDao
    abstract fun turnoDao(): TurnoDao
    abstract fun equipoDao(): EquipoDao
    abstract fun incidenciaDao(): IncidenciaDao
    abstract fun objetoDao(): ObjetoDao
    abstract fun movimientoDao(): MovimientoDao
    abstract fun solicitudDao(): SolicitudDao
    abstract fun jefaturaDao(): JefaturaDao
    abstract fun probadorDao(): ProbadorDao
    abstract fun ajustesDao(): AjustesDao
    abstract fun senalDao(): SenalDao
    abstract fun prendaDao(): PrendaDao
    abstract fun numeroDao(): NumeroDao
    abstract fun ocupacionDao(): OcupacionDao

    companion object {
        fun crear(context: Context): BaseDatosLocal =
            Room.databaseBuilder(context, BaseDatosLocal::class.java, "probadorapp.db")
                // En esta etapa de desarrollo, un cambio de versión recrea la base de datos.
                .fallbackToDestructiveMigration()
                .build()
    }
}

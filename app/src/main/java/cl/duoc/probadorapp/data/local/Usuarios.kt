package cl.duoc.probadorapp.data.local

import androidx.room.Dao
import androidx.room.Entity
import androidx.room.Index
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.PrimaryKey
import androidx.room.Query
import cl.duoc.probadorapp.data.session.AlmacenCuentas
import cl.duoc.probadorapp.data.session.Credenciales
import cl.duoc.probadorapp.data.session.Cuenta
import cl.duoc.probadorapp.domain.model.Usuario

/**
 * Tabla `usuarios`: cuentas del personal para F-01 Iniciar sesión, F-02 Marcar
 * turno y el desbloqueo. Guarda el hash de la contraseña con su sal, nunca la contraseña.
 */
@Entity(tableName = "usuarios", indices = [Index(value = ["correo"], unique = true)])
data class UsuarioEntity(
    @PrimaryKey val uid: String,
    val nombre: String,
    val correo: String,
    val rol: String,
    val departamentoId: String?,
    val sal: String,
    val hash: String,
)

/** DAO del formulario F-01 Iniciar sesión. */
@Dao
interface UsuarioDao {
    @Query("SELECT * FROM usuarios WHERE correo = :correo LIMIT 1")
    suspend fun buscarPorCorreo(correo: String): UsuarioEntity?

    @Query("SELECT COUNT(*) FROM usuarios")
    suspend fun contar(): Int

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertar(usuarios: List<UsuarioEntity>)

    /** RN-26: guarda el departamento asignado después de un movimiento o una jefatura. */
    @Query("UPDATE usuarios SET departamentoId = :departamentoId WHERE uid = :uid")
    suspend fun actualizarDepartamento(uid: String, departamentoId: String?)

    @Query("SELECT * FROM usuarios")
    suspend fun todos(): List<UsuarioEntity>
}

/** Cuentas guardadas en Room. Los correos se guardan en minúsculas. */
class AlmacenCuentasRoom(private val dao: UsuarioDao) : AlmacenCuentas {

    /** Carga las cuentas de prueba la primera vez que se abre la aplicación. */
    suspend fun sembrar(usuarios: List<Usuario>, claveInicial: String) {
        if (dao.contar() > 0) return
        dao.insertar(usuarios.map { u ->
            val c = Credenciales.cuentaDe(u, claveInicial)
            UsuarioEntity(u.uid, u.nombre, c.correo, u.rol.name, u.departamentoId, c.sal, c.hash)
        })
    }

    override suspend fun buscarPorCorreo(correo: String): Cuenta? =
        dao.buscarPorCorreo(correo.trim().lowercase())?.let { Cuenta(it.uid, it.correo, it.sal, it.hash) }
}

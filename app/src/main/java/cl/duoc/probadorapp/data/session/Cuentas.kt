package cl.duoc.probadorapp.data.session

import cl.duoc.probadorapp.domain.model.Usuario
import java.security.MessageDigest
import java.util.UUID

/** Credencial guardada de una persona: nunca se guarda la contraseña, solo su hash con sal. */
data class Cuenta(val usuarioUid: String, val correo: String, val sal: String, val hash: String)

/**
 * Dónde se buscan las cuentas al iniciar sesión (F-01), marcar turno (F-02)
 * o desbloquear. En la app es la tabla `usuarios` de Room (UsuarioDao);
 * en las pruebas, una lista en memoria.
 */
interface AlmacenCuentas {
    suspend fun buscarPorCorreo(correo: String): Cuenta?
}

object Credenciales {
    fun sal(uid: String): String = UUID.nameUUIDFromBytes(uid.toByteArray()).toString()

    fun hash(sal: String, clave: String): String =
        MessageDigest.getInstance("SHA-256").digest((sal + clave).toByteArray())
            .joinToString("") { "%02x".format(it) }

    /** Cuenta de una persona con su contraseña inicial. */
    fun cuentaDe(u: Usuario, clave: String): Cuenta {
        val sal = sal(u.uid)
        return Cuenta(u.uid, u.correo.lowercase(), sal, hash(sal, clave))
    }
}

/** Cuentas en memoria, para las pruebas unitarias. */
class AlmacenCuentasMemoria(usuarios: List<Usuario>, clave: String) : AlmacenCuentas {
    private val cuentas = usuarios.associate { it.correo.lowercase() to Credenciales.cuentaDe(it, clave) }
    override suspend fun buscarPorCorreo(correo: String): Cuenta? = cuentas[correo.trim().lowercase()]
}

package cl.duoc.probadorapp.data.session

import cl.duoc.probadorapp.data.repository.RepositorioTienda
import cl.duoc.probadorapp.domain.Reloj
import cl.duoc.probadorapp.domain.model.Turno
import cl.duoc.probadorapp.domain.model.Usuario
import cl.duoc.probadorapp.domain.rules.MINUTO_MS
import cl.duoc.probadorapp.domain.rules.Marca
import cl.duoc.probadorapp.domain.rules.Reglas
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.UUID

/** Resultado de un intento de inicio de sesión o de marca de turno. */
sealed interface ResultadoAcceso {
    data class Correcto(val usuario: Usuario, val mensaje: String = "") : ResultadoAcceso
    data class Error(val mensaje: String) : ResultadoAcceso
}

/**
 * Sesión del personal. En esta etapa las credenciales se validan en el equipo
 * contra la tabla `usuarios` (hash SHA-256 con sal); en la etapa siguiente se
 * reemplaza por Firebase Authentication sin cambiar las pantallas.
 */
class RepositorioSesion(
    private val repo: RepositorioTienda,
    private val reloj: Reloj,
    private val cuentas: AlmacenCuentas,
) {

    private val usuarioFlujo = MutableStateFlow<Usuario?>(null)
    val usuario: StateFlow<Usuario?> = usuarioFlujo.asStateFlow()

    private val bloqueadaFlujo = MutableStateFlow(false)

    /** RF-02 y RN-04: la tablet está bloqueada y pide la contraseña. */
    val bloqueada: StateFlow<Boolean> = bloqueadaFlujo.asStateFlow()

    @Volatile
    private var ultimaActividad: Long = reloj.ahora()

    /** Busca la cuenta por correo (UsuarioDao) y compara el hash de la contraseña. */
    private suspend fun validar(correo: String, clave: String): Usuario? {
        val c = cuentas.buscarPorCorreo(correo.trim().lowercase()) ?: return null
        if (Credenciales.hash(c.sal, clave) != c.hash) return null
        return repo.estado.value.usuarios[c.usuarioUid]
    }

    /** RF-01: inicia la sesión con las funciones del rol. */
    suspend fun iniciarSesion(correo: String, clave: String): ResultadoAcceso {
        val u = validar(correo, clave) ?: return ResultadoAcceso.Error("Correo o contraseña incorrectos")
        ultimaActividad = reloj.ahora()
        bloqueadaFlujo.value = false
        usuarioFlujo.value = u
        return ResultadoAcceso.Correcto(u)
    }

    fun cerrarSesion() {
        bloqueadaFlujo.value = false
        usuarioFlujo.value = null
    }

    /**
     * Recupera la sesión guardada en el equipo al abrir la aplicación.
     * La tablet queda bloqueada hasta que la persona ingrese su contraseña.
     */
    fun restaurar(uid: String) {
        val u = repo.estado.value.usuarios[uid] ?: return
        usuarioFlujo.value = u
        bloqueadaFlujo.value = true
    }

    /**
     * RN-30: si a la persona la movieron de departamento mientras tenía la sesión
     * abierta, devuelve el id del nuevo departamento hasta que lo confirme.
     */
    fun asignacionPendiente(): String? {
        val u = usuarioFlujo.value ?: return null
        val actual = repo.estado.value.usuarios[u.uid] ?: return null
        return if (actual.departamentoId != u.departamentoId) actual.departamentoId ?: "" else null
    }

    /** RN-30: al confirmar, la tablet pasa a mostrar el nuevo departamento. */
    fun confirmarAsignacion() {
        val u = usuarioFlujo.value ?: return
        repo.estado.value.usuarios[u.uid]?.let { usuarioFlujo.value = it }
    }

    /** Verifica la contraseña del jefe de tienda, por ejemplo para salir del modo equipo de departamento. */
    suspend fun esJefeDeTienda(correo: String, clave: String): Boolean =
        validar(correo, clave)?.rol == cl.duoc.probadorapp.domain.model.Rol.JEFE_TIENDA

    /** Cada toque en la pantalla reinicia el tiempo de inactividad. */
    fun registrarActividad() {
        if (!bloqueadaFlujo.value) ultimaActividad = reloj.ahora()
    }

    /** RN-04: bloquea la tablet si pasó el tiempo de inactividad configurado. */
    fun revisarInactividad(bloqueoMin: Int) {
        if (usuarioFlujo.value == null || bloqueadaFlujo.value) return
        if (reloj.ahora() - ultimaActividad >= bloqueoMin * MINUTO_MS) bloqueadaFlujo.value = true
    }

    /** RN-04: desbloquea con la contraseña de la persona que inició sesión. */
    suspend fun desbloquear(clave: String): ResultadoAcceso {
        val actual = usuarioFlujo.value ?: return ResultadoAcceso.Error("No hay una sesión iniciada")
        val u = validar(actual.correo, clave) ?: return ResultadoAcceso.Error("Contraseña incorrecta")
        ultimaActividad = reloj.ahora()
        bloqueadaFlujo.value = false
        return ResultadoAcceso.Correcto(u)
    }

    /** RF-28 y RN-32: marca la entrada o la salida del turno sin abrir la sesión. */
    suspend fun marcarTurno(correo: String, clave: String, marca: Marca): ResultadoAcceso {
        val u = validar(correo, clave) ?: return ResultadoAcceso.Error("Correo o contraseña incorrectos")
        val ahora = reloj.ahora()
        val hora = SimpleDateFormat("HH:mm", Locale.US).format(Date(ahora))
        return repo.transaccion { e ->
            val abierto = e.turnoAbierto(u.uid)
            if (!Reglas.marcaPermitida(abierto != null, marca)) {
                val msg = if (marca == Marca.ENTRADA) {
                    "Ya tiene un turno abierto desde las " + SimpleDateFormat("HH:mm", Locale.US).format(Date(abierto!!.entrada))
                } else {
                    "No tiene un turno abierto"
                }
                return@transaccion e to ResultadoAcceso.Error(msg)
            }
            if (marca == Marca.ENTRADA) {
                val t = Turno(UUID.randomUUID().toString(), u.uid, ahora)
                e.copy(turnos = e.turnos + (t.id to t)) to ResultadoAcceso.Correcto(u, "Entrada registrada a las $hora.")
            } else {
                e.copy(turnos = e.turnos + (abierto!!.id to abierto.copy(salida = ahora))) to
                    ResultadoAcceso.Correcto(u, "Salida registrada a las $hora.")
            }
        }
    }
}

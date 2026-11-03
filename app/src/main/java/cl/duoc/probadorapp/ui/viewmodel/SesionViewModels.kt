package cl.duoc.probadorapp.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import cl.duoc.probadorapp.data.session.RepositorioSesion
import cl.duoc.probadorapp.data.session.ResultadoAcceso
import cl.duoc.probadorapp.domain.rules.Marca
import cl.duoc.probadorapp.domain.validation.Validaciones
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/** Campos y errores de F-01 Iniciar sesión y de F-02 Marcar turno. */
data class CredencialesUi(
    val correo: String = "",
    val clave: String = "",
    val errorCorreo: String? = null,
    val errorClave: String? = null,
    val mensaje: String? = null,
    val mensajeEsError: Boolean = true,
    val cargando: Boolean = false,
    /** La sesión se inició y hay que ir al panel. */
    val ingreso: Boolean = false,
) {
    val puedeEnviar: Boolean
        get() = correo.isNotBlank() && clave.isNotEmpty() && errorCorreo == null && errorClave == null && !cargando
}

/** Lógica compartida de los formularios con correo y contraseña. */
abstract class CredencialesViewModel : ViewModel() {
    protected val estadoFlujo = MutableStateFlow(CredencialesUi())
    val estado: StateFlow<CredencialesUi> = estadoFlujo.asStateFlow()

    fun alCambiarCorreo(valor: String) =
        estadoFlujo.update { it.copy(correo = valor, errorCorreo = Validaciones.correo(valor), mensaje = null) }

    fun alCambiarClave(valor: String) =
        estadoFlujo.update { it.copy(clave = valor, errorClave = Validaciones.contrasena(valor), mensaje = null) }
}

/** Lógica de sesión (UI-01). */
class SesionViewModel(private val sesion: RepositorioSesion) : CredencialesViewModel() {

    /** Valida las credenciales contra la tabla usuarios (UsuarioDao). */
    fun ingresar() {
        val e = estadoFlujo.value
        if (!e.puedeEnviar) return
        estadoFlujo.update { it.copy(cargando = true) }
        viewModelScope.launch {
            when (val r = sesion.iniciarSesion(e.correo, e.clave)) {
                is ResultadoAcceso.Correcto -> estadoFlujo.value = CredencialesUi(ingreso = true)
                is ResultadoAcceso.Error -> estadoFlujo.update { it.copy(cargando = false, mensaje = r.mensaje, mensajeEsError = true) }
            }
        }
    }

    /** La pantalla ya navegó al panel. */
    fun navegado() = estadoFlujo.update { it.copy(ingreso = false) }
}

/** Lógica de marca de turno (UI-02). */
class MarcaTurnoViewModel(private val sesion: RepositorioSesion) : CredencialesViewModel() {

    fun marcar(marca: Marca) {
        val e = estadoFlujo.value
        estadoFlujo.update { it.copy(cargando = true) }
        viewModelScope.launch {
            when (val r = sesion.marcarTurno(e.correo, e.clave, marca)) {
                is ResultadoAcceso.Correcto -> estadoFlujo.value = CredencialesUi(mensaje = r.mensaje, mensajeEsError = false)
                is ResultadoAcceso.Error -> estadoFlujo.update { it.copy(cargando = false, mensaje = r.mensaje, mensajeEsError = true) }
            }
        }
    }
}

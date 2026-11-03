package cl.duoc.probadorapp.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import cl.duoc.probadorapp.data.local.AjustesGuardados
import cl.duoc.probadorapp.data.repository.RepositorioTienda
import cl.duoc.probadorapp.data.session.RepositorioSesion
import cl.duoc.probadorapp.data.session.ResultadoAcceso
import cl.duoc.probadorapp.domain.model.Departamento
import cl.duoc.probadorapp.domain.model.Parametros
import cl.duoc.probadorapp.domain.model.Rol
import cl.duoc.probadorapp.domain.validation.Validaciones
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

// ---------------------------------------------------------------------- UI-20 Ajustes (F-12)

/** Campos de F-12. Los valores numéricos se editan como texto para validarlos. */
data class AjustesForm(
    val tiempoMaximo: String = "",
    val plazoLlamado: String = "",
    val bloqueo: String = "",
    val anuncioVoz: Boolean = true,
    val minimos: Map<String, String> = emptyMap(),
    /** Campos que el usuario ya modificó; solo esos muestran su error. */
    val tocados: Set<String> = emptySet(),
)

data class AjustesUi(
    val form: AjustesForm = AjustesForm(),
    val editaParametros: Boolean = false,
    val departamentos: List<Departamento> = emptyList(),
    val errorTiempo: String? = null,
    val errorPlazo: String? = null,
    val errorBloqueo: String? = null,
    val errorMinimos: Map<String, String?> = emptyMap(),
    val puedeGuardar: Boolean = false,
    val guardando: Boolean = false,
    val guardado: Boolean = false,
)

/**
 * RF-37: el jefe de tienda edita los parámetros y los mínimos de todos los
 * departamentos; el jefe de departamento solo el mínimo del suyo (RN-03).
 */
class AjustesViewModel(
    private val repo: RepositorioTienda,
    sesion: RepositorioSesion,
    private val guardar: suspend (AjustesGuardados) -> Unit,
) : ViewModel() {

    private val usuario = sesion.usuario.value
    private val editaParametros = usuario?.rol == Rol.JEFE_TIENDA
    private val departamentos: List<Departamento> = repo.estado.value.departamentos.values
        .filter { editaParametros || it.id == usuario?.departamentoId }

    private val flujo = MutableStateFlow(inicial())
    val estado: StateFlow<AjustesUi> = flujo.asStateFlow()

    private fun inicial(): AjustesUi {
        val p = repo.estado.value.parametros
        val form = AjustesForm(
            tiempoMaximo = p.tiempoMaximoMin.toString(),
            plazoLlamado = p.plazoLlamadoMin.toString(),
            bloqueo = p.bloqueoMin.toString(),
            anuncioVoz = p.anuncioVoz,
            minimos = departamentos.associate { it.id to it.minimoPersonal.toString() },
        )
        return validar(form)
    }

    fun alCambiarTiempo(v: String) = cambiar { it.copy(tiempoMaximo = v, tocados = it.tocados + "tiempo") }
    fun alCambiarPlazo(v: String) = cambiar { it.copy(plazoLlamado = v, tocados = it.tocados + "plazo") }
    fun alCambiarBloqueo(v: String) = cambiar { it.copy(bloqueo = v, tocados = it.tocados + "bloqueo") }
    fun alCambiarVoz(v: Boolean) = cambiar { it.copy(anuncioVoz = v) }
    fun alCambiarMinimo(dep: String, v: String) = cambiar { it.copy(minimos = it.minimos + (dep to v), tocados = it.tocados + "min_$dep") }

    private fun cambiar(f: (AjustesForm) -> AjustesForm) = flujo.update { validar(f(it.form)).copy(guardando = it.guardando) }

    private fun validar(f: AjustesForm): AjustesUi {
        val tiempo = Validaciones.enteroEnRango(f.tiempoMaximo, 1, 60)?.let { if (it.startsWith("Debe")) "Debe estar entre 1 y 60 minutos" else it }
        val plazo = Validaciones.enteroEnRango(f.plazoLlamado, 1, 10)?.let { if (it.startsWith("Debe")) "Debe estar entre 1 y 10 minutos" else it }
        val bloqueo = Validaciones.enteroEnRango(f.bloqueo, 1, 15)?.let { if (it.startsWith("Debe")) "Debe estar entre 1 y 15 minutos" else it }
        val minimos = f.minimos.mapValues { (_, v) -> Validaciones.enteroEnRango(v, 0, 10) }
        val sinErrores = minimos.values.all { it == null } && (!editaParametros || listOf(tiempo, plazo, bloqueo).all { it == null })
        return AjustesUi(
            form = f,
            editaParametros = editaParametros,
            departamentos = departamentos,
            errorTiempo = tiempo.takeIf { "tiempo" in f.tocados },
            errorPlazo = plazo.takeIf { "plazo" in f.tocados },
            errorBloqueo = bloqueo.takeIf { "bloqueo" in f.tocados },
            errorMinimos = minimos.mapValues { (dep, e) -> e.takeIf { "min_$dep" in f.tocados } },
            puedeGuardar = sinErrores,
        )
    }

    fun guardarAjustes() {
        val ui = flujo.value
        if (!ui.puedeGuardar || ui.guardando) return
        val f = ui.form
        val actuales = repo.estado.value.parametros
        val parametros = if (editaParametros) {
            Parametros(f.tiempoMaximo.trim().toInt(), f.plazoLlamado.trim().toInt(), f.bloqueo.trim().toInt(), f.anuncioVoz)
        } else {
            actuales
        }
        val todosLosMinimos = repo.estado.value.departamentos.mapValues { (id, d) ->
            f.minimos[id]?.trim()?.toInt() ?: d.minimoPersonal
        }
        flujo.update { it.copy(guardando = true) }
        viewModelScope.launch {
            guardar(AjustesGuardados(parametros, todosLosMinimos))
            // Mensaje de confirmación animado durante 2,5 segundos
            flujo.update { it.copy(guardando = false, guardado = true) }
            delay(2_500L)
            flujo.update { it.copy(guardado = false) }
        }
    }
}

// ---------------------------------------------------------------------- Desbloqueo (RF-02)

data class DesbloqueoUi(
    val clave: String = "",
    val error: String? = null,
)

class DesbloqueoViewModel(private val sesion: RepositorioSesion) : ViewModel() {
    private val flujo = MutableStateFlow(DesbloqueoUi())
    val estado: StateFlow<DesbloqueoUi> = flujo.asStateFlow()

    fun alCambiarClave(v: String) = flujo.update { DesbloqueoUi(v, Validaciones.contrasena(v)) }

    fun desbloquear() {
        val clave = flujo.value.clave
        viewModelScope.launch {
            when (val r = sesion.desbloquear(clave)) {
                is ResultadoAcceso.Correcto -> flujo.value = DesbloqueoUi()
                is ResultadoAcceso.Error -> flujo.update { it.copy(error = r.mensaje) }
            }
        }
    }

    fun cerrarSesion() {
        flujo.value = DesbloqueoUi()
        sesion.cerrarSesion()
    }
}

package cl.duoc.probadorapp.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import cl.duoc.probadorapp.data.repository.EstadoTienda
import cl.duoc.probadorapp.data.repository.RepositorioTienda
import cl.duoc.probadorapp.data.session.RepositorioSesion
import cl.duoc.probadorapp.domain.Reloj
import cl.duoc.probadorapp.domain.model.Canal
import cl.duoc.probadorapp.domain.model.Departamento
import cl.duoc.probadorapp.domain.model.EstadoNumero
import cl.duoc.probadorapp.domain.model.Rol
import cl.duoc.probadorapp.domain.model.Usuario
import cl.duoc.probadorapp.ui.hora
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn

// ---------------------------------------------------------------------- UI-07 Cola del día

data class NumeroColaUi(
    val id: String,
    val numero: Int,
    val estado: EstadoNumero,
    val canal: Canal,
    val emitido: String,
    val detalle: String,
)

data class ColaUi(
    val departamentos: List<Departamento> = emptyList(),
    val departamentoId: String = "",
    val puedeElegirDepartamento: Boolean = false,
    val numeros: List<NumeroColaUi> = emptyList(),
    val resumen: List<Pair<EstadoNumero, Int>> = emptyList(),
)

/** RF-12: números del día de un departamento, con su estado, su canal y su espera estimada. */
class ColaViewModel(
    repo: RepositorioTienda,
    sesion: RepositorioSesion,
    private val reloj: Reloj,
) : ViewModel() {

    private val elegido = MutableStateFlow<String?>(null)

    val estado: StateFlow<ColaUi> = combine(repo.estado, elegido, sesion.usuario) { e, dep, u -> construir(e, dep, u) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), ColaUi())

    fun elegirDepartamento(id: String) {
        elegido.value = id
    }

    private fun construir(e: EstadoTienda, elegido: String?, u: Usuario?): ColaUi {
        val depId = departamentoVisible(e, elegido, u) ?: return ColaUi()
        val hoy = reloj.hoy()
        val numeros = e.numeros.values.filter { it.departamentoId == depId && it.fecha == hoy }.sortedBy { it.numero }
        val enEspera = numeros.filter { it.estado == EstadoNumero.EN_ESPERA }.map { it.id }
        val filas = numeros.map { n ->
            val probador = n.probadorId?.let { e.probadores[it]?.codigo }
            val detalle = when (n.estado) {
                EstadoNumero.EN_ESPERA -> {
                    val posicion = enEspera.indexOf(n.id) + 1
                    val espera = esperaEstimada(e, depId, posicion)
                    "Posición $posicion · Espera estimada: " + (espera?.let { "$it min" } ?: "Sin estimación")
                }
                EstadoNumero.LLAMADO -> "Llamado a las ${hora(n.llamado ?: n.emitido)} al probador ${probador ?: "-"}"
                EstadoNumero.ATENDIDO -> "Atendido a las ${hora(n.cierre ?: n.emitido)}" + (probador?.let { " en $it" } ?: "")
                EstadoNumero.PERDIDO -> "Perdido a las ${hora(n.cierre ?: n.emitido)}: no entró dentro del plazo"
            }
            NumeroColaUi(n.id, n.numero, n.estado, n.canal, hora(n.emitido), detalle)
        }
        return ColaUi(
            departamentos = e.departamentos.values.toList(),
            departamentoId = depId,
            puedeElegirDepartamento = u?.rol == Rol.JEFE_TIENDA,
            numeros = filas,
            resumen = EstadoNumero.entries.map { est -> est to numeros.count { it.estado == est } },
        )
    }
}

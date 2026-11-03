package cl.duoc.probadorapp.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import cl.duoc.probadorapp.data.repository.EstadoTienda
import cl.duoc.probadorapp.data.repository.RepositorioTienda
import cl.duoc.probadorapp.data.session.RepositorioSesion
import cl.duoc.probadorapp.domain.Reloj
import cl.duoc.probadorapp.domain.model.Departamento
import cl.duoc.probadorapp.domain.model.EstadoAlerta
import cl.duoc.probadorapp.domain.model.EstadoGuardado
import cl.duoc.probadorapp.domain.model.EstadoNumero
import cl.duoc.probadorapp.domain.model.EstadoVisible
import cl.duoc.probadorapp.domain.model.Llamado
import cl.duoc.probadorapp.domain.model.Rol
import cl.duoc.probadorapp.domain.model.TipoAlerta
import cl.duoc.probadorapp.domain.model.Usuario
import cl.duoc.probadorapp.domain.rules.MINUTO_MS
import cl.duoc.probadorapp.domain.rules.Reglas
import cl.duoc.probadorapp.engine.MotorReglas
import cl.duoc.probadorapp.ui.components.TarjetaUi
import cl.duoc.probadorapp.ui.hora
import cl.duoc.probadorapp.ui.mmss
import cl.duoc.probadorapp.ui.mss
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/** Emite la hora cada segundo para actualizar los tiempos en pantalla. */
fun pulso(reloj: Reloj): Flow<Long> = flow {
    while (true) {
        emit(reloj.ahora())
        delay(1_000L)
    }
}

/**
 * RN-13: espera estimada del número en la posición [posicion] de la cola de un
 * departamento, con el tiempo promedio de las ocupaciones terminadas del día.
 */
fun esperaEstimada(e: EstadoTienda, departamentoId: String, posicion: Int): Int? {
    val probadores = e.probadoresDe(departamentoId)
    val ids = probadores.map { it.id }.toSet()
    val utiles = probadores.count { it.activo && !it.sinSenal && it.estado != EstadoGuardado.EN_REVISION }
    val terminadas = e.ocupaciones.values.filter { it.salida != null && it.probadorId in ids }
    val promedio = if (terminadas.isEmpty()) null else terminadas.map { (it.salida!! - it.entrada) / 60_000.0 }.average()
    return Reglas.esperaEstimadaMin(posicion, utiles, promedio, e.parametros.tiempoMaximoMin)
}

/** Departamento que ve la persona: el elegido por el jefe de tienda o el propio (RN-03). */
fun departamentoVisible(e: EstadoTienda, elegido: String?, u: Usuario?): String? =
    (if (u?.rol == Rol.JEFE_TIENDA) elegido else null) ?: u?.departamentoId ?: e.departamentos.keys.firstOrNull()

data class AlertaUi(
    val id: String,
    val tipo: TipoAlerta,
    val titulo: String,
    val detalle: String,
    val hora: String,
    val probadorId: String?,
)

data class PanelUi(
    val usuario: Usuario? = null,
    val departamentos: List<Departamento> = emptyList(),
    val departamentoId: String = "",
    val puedeElegirDepartamento: Boolean = false,
    val tarjetas: List<TarjetaUi> = emptyList(),
    val resumen: List<Pair<EstadoVisible, Int>> = emptyList(),
    val enEspera: List<Int> = emptyList(),
    val llamando: String? = null,
    val esperaMin: Int? = null,
    val alertas: List<AlertaUi> = emptyList(),
    val ultimosLlamados: List<Llamado> = emptyList(),
)

/** Lógica del panel (UI-04): probadores del departamento, resumen, cola y alertas. */
class PanelViewModel(
    private val repo: RepositorioTienda,
    private val motor: MotorReglas,
    private val sesion: RepositorioSesion,
    reloj: Reloj,
) : ViewModel() {

    private val elegido = MutableStateFlow<String?>(null)

    val estado: StateFlow<PanelUi> = combine(repo.estado, elegido, pulso(reloj), sesion.usuario) { e, dep, ahora, u ->
        construir(e, dep, ahora, u)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), PanelUi())

    fun elegirDepartamento(id: String) {
        elegido.value = id
    }

    fun marcarVista(alertaId: String) {
        val uid = sesion.usuario.value?.uid ?: return
        viewModelScope.launch { motor.marcarVista(alertaId, uid) }
    }

    private fun construir(e: EstadoTienda, elegido: String?, ahora: Long, u: Usuario?): PanelUi {
        val deps = e.departamentos.values.toList()
        val depId = departamentoVisible(e, elegido, u) ?: return PanelUi(usuario = u)
        val probadores = e.probadoresDe(depId)
        val tarjetas = probadores.map { p ->
            val oc = p.ocupacionId?.let { e.ocupaciones[it] }
            val visible = Reglas.estadoVisible(p, oc, ahora)
            when (visible) {
                EstadoVisible.LIBRE -> TarjetaUi(p.id, p.codigo, visible, linea1 = "Listo para el siguiente número")
                EstadoVisible.RESERVADO -> {
                    val n = p.numeroId?.let { e.numeros[it] }
                    val restante = (n?.llamado ?: ahora) + e.parametros.plazoLlamadoMin * MINUTO_MS - ahora
                    TarjetaUi(p.id, p.codigo, visible, principal = n?.let { "N.º ${it.numero}" }, linea1 = "Plazo para entrar: ${mss(restante)}")
                }
                EstadoVisible.OCUPADO, EstadoVisible.EXCEDIDO -> {
                    val numero = oc?.numeroId?.let { e.numeros[it]?.numero }
                    val prendas = p.prendasDentro.count { it.retirada == null }
                    TarjetaUi(
                        p.id, p.codigo, visible,
                        principal = oc?.let { mmss(ahora - it.entrada) },
                        linea1 = (numero?.let { "N.º $it" } ?: "Sin número") + " · $prendas prendas",
                        avance = oc?.let { Reglas.avance(it.entrada, ahora, it.tiempoMaximoMin) },
                    )
                }
                EstadoVisible.EN_REVISION -> {
                    val prenda = p.prendasDentro.firstOrNull { it.retirada == null }
                    TarjetaUi(p.id, p.codigo, visible,
                        linea1 = if (prenda != null) "Prenda olvidada" else "Incidencia abierta",
                        linea2 = prenda?.let { "${it.prenda.descripcion} · Talla ${it.prenda.talla}" })
                }
                EstadoVisible.SIN_SENAL -> TarjetaUi(p.id, p.codigo, visible, linea1 = "Sin datos hace más de 60 s", linea2 = "No recibe números")
            }
        }
        val resumen = EstadoVisible.entries.map { est -> est to tarjetas.count { it.estado == est } }

        val numerosDep = e.numeros.values.filter { it.departamentoId == depId }
        val enEspera = numerosDep.filter { it.estado == EstadoNumero.EN_ESPERA }.map { it.numero }.sorted()
        val llamado = numerosDep.filter { it.estado == EstadoNumero.LLAMADO }.maxByOrNull { it.llamado ?: 0 }
        val llamando = llamado?.let { n -> "Número ${n.numero} → ${n.probadorId?.let { e.probadores[it]?.codigo } ?: ""}" }

        val espera = esperaEstimada(e, depId, enEspera.size + 1)

        val alertas = e.alertas.values
            .filter { it.estado == EstadoAlerta.ACTIVA && it.departamentoId == depId }
            .sortedByDescending { it.creada }
            .map { a ->
                val codigo = a.probadorId?.let { e.probadores[it]?.codigo }
                AlertaUi(a.id, a.tipo, a.tipo.texto + (codigo?.let { " · $it" } ?: ""), a.detalle, hora(a.creada), a.probadorId)
            }

        return PanelUi(
            usuario = u,
            departamentos = deps,
            departamentoId = depId,
            puedeElegirDepartamento = u?.rol == Rol.JEFE_TIENDA,
            tarjetas = tarjetas,
            resumen = resumen,
            enEspera = enEspera,
            llamando = llamando,
            esperaMin = espera,
            alertas = alertas,
            ultimosLlamados = e.llamados[depId].orEmpty(),
        )
    }
}

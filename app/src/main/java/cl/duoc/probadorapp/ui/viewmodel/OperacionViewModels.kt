package cl.duoc.probadorapp.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import cl.duoc.probadorapp.data.repository.EstadoTienda
import cl.duoc.probadorapp.data.repository.RepositorioTienda
import cl.duoc.probadorapp.data.session.RepositorioSesion
import cl.duoc.probadorapp.domain.Reloj
import cl.duoc.probadorapp.domain.model.Canal
import cl.duoc.probadorapp.domain.model.EstadoAlerta
import cl.duoc.probadorapp.domain.model.EstadoGuardado
import cl.duoc.probadorapp.domain.model.EstadoNumero
import cl.duoc.probadorapp.domain.model.EstadoVisible
import cl.duoc.probadorapp.domain.rules.Reglas
import cl.duoc.probadorapp.engine.MotorReglas
import cl.duoc.probadorapp.ui.hora
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

// ---------------------------------------------------------------------- UI-05 Detalle de probador

data class PrendaUi(val descripcion: String, val detalle: String)

data class DetalleUi(
    val existe: Boolean = false,
    val codigo: String = "",
    val departamento: String = "",
    val estado: EstadoVisible = EstadoVisible.LIBRE,
    val motivo: String = "",
    val datos: List<Pair<String, String>> = emptyList(),
    val prendas: List<PrendaUi> = emptyList(),
    val alertas: List<String> = emptyList(),
    val puedeConfirmar: Boolean = false,
)

class DetalleViewModel(
    private val probadorId: String,
    repo: RepositorioTienda,
    private val motor: MotorReglas,
    reloj: Reloj,
) : ViewModel() {

    val estado: StateFlow<DetalleUi> = combine(repo.estado, pulso(reloj)) { e, ahora -> construir(e, ahora) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), DetalleUi())

    fun confirmarRevision() {
        viewModelScope.launch { motor.confirmarRevision(probadorId) }
    }

    private fun construir(e: EstadoTienda, ahora: Long): DetalleUi {
        val p = e.probadores[probadorId] ?: return DetalleUi()
        val activa = p.ocupacionId?.let { e.ocupaciones[it] }
        val visible = Reglas.estadoVisible(p, activa, ahora)
        val ultima = activa ?: e.ocupaciones.values.filter { it.probadorId == p.id }.maxByOrNull { it.entrada }
        val datos = buildList {
            if (ultima != null) {
                add((if (ultima.salida == null) "Ocupación actual" else "Última ocupación") to
                    (hora(ultima.entrada) + (ultima.salida?.let { " a " + hora(it) } ?: " a ahora")))
                add("Tiempo de uso" to "${((ultima.salida ?: ahora) - ultima.entrada) / 60_000} min")
                add("Número atendido" to (ultima.numeroId?.let { e.numeros[it]?.numero?.toString() } ?: "Sin número"))
            } else {
                add("Última ocupación" to "Sin ocupaciones hoy")
            }
            add("Sensor ${p.sensorCodigo}" to if (p.sinSenal) "Sin señal" else "Con señal")
            add("Lector ${p.lectorCodigo}" to if (p.sinSenal) "Sin señal" else "Con señal")
            add("Incidencias abiertas" to e.incidenciasAbiertas(p.id).toString())
        }
        val motivo = when (visible) {
            EstadoVisible.EN_REVISION -> if (p.prendasDentro.any { it.retirada == null }) "El lector detectó una prenda al salir el cliente" else "Tiene una incidencia abierta"
            EstadoVisible.SIN_SENAL -> "El sensor o el lector no envían señales"
            EstadoVisible.EXCEDIDO -> "Superó el tiempo máximo de uso"
            EstadoVisible.RESERVADO -> "Esperando la entrada del número llamado"
            EstadoVisible.OCUPADO -> "Cliente dentro del probador"
            EstadoVisible.LIBRE -> "Disponible para el siguiente número"
        }
        return DetalleUi(
            existe = true,
            codigo = p.codigo,
            departamento = e.departamentos[p.departamentoId]?.nombre ?: "",
            estado = visible,
            motivo = motivo,
            datos = datos,
            prendas = p.prendasDentro.filter { it.retirada == null }.map {
                PrendaUi("${it.prenda.descripcion} · Talla ${it.prenda.talla}", "Detectada a las ${hora(it.detectada)} · Código RFID ...${it.prenda.epc.takeLast(4)}")
            },
            alertas = e.alertas.values.filter { it.probadorId == p.id && it.estado == EstadoAlerta.ACTIVA }
                .map { "${it.tipo.texto}, creada a las ${hora(it.creada)}" },
            puedeConfirmar = p.estado == EstadoGuardado.EN_REVISION,
        )
    }
}

// ---------------------------------------------------------------------- UI-06 Número sin celular

data class NumeroUi(
    val cargando: Boolean = true,
    val numero: Int? = null,
    val probador: String? = null,
    val antes: Int = 0,
    val esperaMin: Int? = null,
    val departamento: String = "",
)

/** Entrega el siguiente número del correlativo al abrir la pantalla (CU-03). */
class NumeroSinCelularViewModel(
    private val departamentoId: String,
    private val repo: RepositorioTienda,
    private val motor: MotorReglas,
    private val sesion: RepositorioSesion,
) : ViewModel() {

    private val flujo = MutableStateFlow(NumeroUi())
    val estado: StateFlow<NumeroUi> = flujo.asStateFlow()

    init {
        viewModelScope.launch {
            val n = motor.entregarNumero(departamentoId, Canal.SIN_CELULAR, entregadoPor = sesion.usuario.value?.uid)
            val e = repo.estado.value
            val actual = e.numeros[n.id] ?: n
            val antes = e.numeros.values.count {
                it.departamentoId == departamentoId && it.estado == EstadoNumero.EN_ESPERA && it.numero < actual.numero
            }
            val probadores = e.probadoresDe(departamentoId)
            val utiles = probadores.count { it.activo && !it.sinSenal && it.estado != EstadoGuardado.EN_REVISION }
            flujo.value = NumeroUi(
                cargando = false,
                numero = actual.numero,
                probador = if (actual.estado == EstadoNumero.LLAMADO) actual.probadorId?.let { e.probadores[it]?.codigo } else null,
                antes = antes,
                esperaMin = Reglas.esperaEstimadaMin(antes + 1, utiles, null, e.parametros.tiempoMaximoMin),
                departamento = e.departamentos[departamentoId]?.nombre ?: "",
            )
        }
    }
}

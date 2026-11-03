package cl.duoc.probadorapp.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import cl.duoc.probadorapp.data.local.SenalEntity
import cl.duoc.probadorapp.data.repository.RepositorioTienda
import cl.duoc.probadorapp.domain.model.Canal
import cl.duoc.probadorapp.domain.model.Departamento
import cl.duoc.probadorapp.domain.model.Prenda
import cl.duoc.probadorapp.domain.model.Probador
import cl.duoc.probadorapp.domain.validation.Validaciones
import cl.duoc.probadorapp.engine.ModoAutomatico
import cl.duoc.probadorapp.engine.MotorReglas
import cl.duoc.probadorapp.signals.SimuladorSenales
import cl.duoc.probadorapp.signals.TipoSenal
import cl.duoc.probadorapp.ui.horaSegundos
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/** Señales que se pueden enviar desde el simulador (F-13). */
enum class OpcionSenal(val texto: String, val usaProbador: Boolean = true) {
    ENTRADA("Entrada"),
    SALIDA("Salida"),
    PRENDAS_DETECTADAS("Prendas detectadas"),
    RETIRAR_PRENDAS("Retirar prendas"),
    PERDER_SENAL("Perder señal"),
    RECUPERAR_SENAL("Recuperar señal"),
    NUMERO_QR("Número por QR", usaProbador = false),
}

/** Campos del formulario y si el usuario ya los modificó. */
data class SimuladorForm(
    val departamentoId: String = "mujer",
    val probadorId: String? = null,
    val senal: OpcionSenal? = null,
    val cantidad: String = "",
    val cantidadTocada: Boolean = false,
)

data class SenalUi(val titulo: String, val detalle: String)

data class SimuladorUi(
    val form: SimuladorForm = SimuladorForm(),
    val departamentos: List<Departamento> = emptyList(),
    val probadores: List<Probador> = emptyList(),
    val errorProbador: String? = null,
    val errorSenal: String? = null,
    val errorCantidad: String? = null,
    val puedeEnviar: Boolean = false,
    val modoAutomatico: Boolean = false,
    val ultimas: List<SenalUi> = emptyList(),
)

/** Lógica del simulador (UI-21). */
class SimuladorViewModel(
    private val repo: RepositorioTienda,
    private val simulador: SimuladorSenales,
    private val motor: MotorReglas,
    private val modo: ModoAutomatico,
    private val catalogo: () -> List<Prenda>,
    ultimasSenales: Flow<List<SenalEntity>>,
    private val alcanceApp: CoroutineScope,
    private val guardarModo: suspend (Boolean) -> Unit,
    private val borrarSenales: suspend () -> Unit,
) : ViewModel() {

    private val form = MutableStateFlow(SimuladorForm())
    private val mensajeFlujo = MutableStateFlow<String?>(null)
    val mensaje: StateFlow<String?> = mensajeFlujo

    val estado: StateFlow<SimuladorUi> = combine(form, repo.estado, modo.activo, ultimasSenales) { f, e, auto, ultimas ->
        val probadores = e.probadoresDe(f.departamentoId).filter { it.activo }
        val errorProbador = if (f.senal?.usaProbador == true && probadores.none { it.id == f.probadorId }) "Seleccione un probador" else null
        val errorSenal = if (f.senal == null) "Seleccione una señal" else null
        val errorCantidad = if (f.senal == OpcionSenal.PRENDAS_DETECTADAS) {
            Validaciones.enteroEnRango(f.cantidad, 1, 10, "Ingrese la cantidad de prendas")
        } else {
            null
        }
        val porCodigo = e.probadores.values.flatMap { p -> listOf(p.sensorCodigo to p, p.lectorCodigo to p) }.toMap()
        SimuladorUi(
            form = f,
            departamentos = e.departamentos.values.toList(),
            probadores = probadores,
            errorProbador = errorProbador,
            errorSenal = errorSenal,
            errorCantidad = if (f.cantidadTocada) errorCantidad else null,
            puedeEnviar = errorProbador == null && errorSenal == null && errorCantidad == null,
            modoAutomatico = auto,
            ultimas = ultimas.map { s ->
                val tipo = runCatching { TipoSenal.valueOf(s.tipo).texto }.getOrDefault(s.tipo)
                val prob = porCodigo[s.dispositivo]
                SenalUi(
                    titulo = tipo + if (s.detalle.isNotEmpty()) " (${s.detalle})" else "",
                    detalle = "${horaSegundos(s.hora)} · ${s.dispositivo}" + (prob?.let { " · ${it.codigo} de ${e.departamentos[it.departamentoId]?.nombre}" } ?: ""),
                )
            },
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), SimuladorUi())

    fun elegirDepartamento(id: String) = form.update { it.copy(departamentoId = id, probadorId = null) }
    fun elegirProbador(id: String) = form.update { it.copy(probadorId = id) }
    fun elegirSenal(s: OpcionSenal) = form.update { it.copy(senal = s) }
    fun alCambiarCantidad(v: String) = form.update { it.copy(cantidad = v, cantidadTocada = true) }
    fun mensajeMostrado() { mensajeFlujo.value = null }

    fun enviar() {
        val ui = estado.value
        if (!ui.puedeEnviar) return
        val f = ui.form
        val p = ui.probadores.firstOrNull { it.id == f.probadorId }
        viewModelScope.launch {
            mensajeFlujo.value = when (f.senal) {
                OpcionSenal.ENTRADA -> { simulador.enviar(p!!.sensorCodigo, TipoSenal.ENTRADA); "Señal enviada: entrada en ${p.codigo}." }
                OpcionSenal.SALIDA -> { simulador.enviar(p!!.sensorCodigo, TipoSenal.SALIDA); "Señal enviada: salida en ${p.codigo}." }
                OpcionSenal.PRENDAS_DETECTADAS -> {
                    val prendas = catalogo().shuffled().take(f.cantidad.trim().toInt())
                    simulador.enviar(p!!.lectorCodigo, TipoSenal.PRENDAS_DETECTADAS, prendas)
                    "Señal enviada: ${prendas.size} prendas en ${p.codigo}."
                }
                OpcionSenal.RETIRAR_PRENDAS -> { simulador.enviar(p!!.lectorCodigo, TipoSenal.PRENDAS_RETIRADAS); "Señal enviada: prendas retiradas en ${p.codigo}." }
                OpcionSenal.PERDER_SENAL -> {
                    simulador.perderSenal(listOf(p!!.sensorCodigo, p.lectorCodigo))
                    "Señales de ${p.codigo} detenidas. Quedará Sin señal en 60 s."
                }
                OpcionSenal.RECUPERAR_SENAL -> {
                    simulador.recuperarSenal(listOf(p!!.sensorCodigo, p.lectorCodigo))
                    "Señales de ${p.codigo} recuperadas."
                }
                OpcionSenal.NUMERO_QR -> {
                    val n = motor.entregarNumero(f.departamentoId, Canal.QR, clienteUid = "simulado")
                    "Número ${n.numero} entregado por QR."
                }
                null -> null
            }
        }
    }

    fun cambiarModoAutomatico(activo: Boolean) {
        if (activo) modo.activar(alcanceApp) else modo.desactivar()
        viewModelScope.launch { guardarModo(activo) }
        mensajeFlujo.value = if (activo) "Modo automático activado." else "Modo automático desactivado."
    }

    fun reiniciar() {
        viewModelScope.launch {
            motor.reiniciarDemostracion()
            simulador.reiniciar()
            borrarSenales()
            mensajeFlujo.value = "Demostración reiniciada."
        }
    }
}

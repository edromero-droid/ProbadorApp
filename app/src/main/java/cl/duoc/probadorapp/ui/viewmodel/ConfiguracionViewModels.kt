package cl.duoc.probadorapp.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import cl.duoc.probadorapp.data.local.RepositorioEquipo
import cl.duoc.probadorapp.data.repository.DatosIniciales
import cl.duoc.probadorapp.data.repository.EstadoTienda
import cl.duoc.probadorapp.data.repository.RepositorioTienda
import cl.duoc.probadorapp.domain.Reloj
import cl.duoc.probadorapp.domain.model.ConfiguracionEquipo
import cl.duoc.probadorapp.domain.model.EstadoCliente
import cl.duoc.probadorapp.domain.model.EstadoNumero
import cl.duoc.probadorapp.domain.model.Llamado
import cl.duoc.probadorapp.domain.model.Tienda
import cl.duoc.probadorapp.domain.model.UsoEquipo
import cl.duoc.probadorapp.domain.rules.Reglas
import cl.duoc.probadorapp.domain.validation.Validaciones
import cl.duoc.probadorapp.engine.MotorReglas
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

// ---------------------------------------------------------------------- UI-18 Probadores y dispositivos

data class ProbadorConfigUi(val id: String, val codigo: String, val sensor: String, val lector: String, val activo: Boolean)

data class DepartamentoProbadoresUi(val nombre: String, val probadores: List<ProbadorConfigUi>)

class ProbadoresViewModel(repo: RepositorioTienda) : ViewModel() {
    val estado: StateFlow<List<DepartamentoProbadoresUi>> = repo.estado.map { e ->
        e.departamentos.values.map { d ->
            DepartamentoProbadoresUi(d.nombre, e.probadoresDe(d.id).map { ProbadorConfigUi(it.id, it.codigo, it.sensorCodigo, it.lectorCodigo, it.activo) })
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())
}

// ---------------------------------------------------------------------- UI-19 Configurar probador (F-11)

data class ConfigurarProbadorUi(
    val titulo: String = "",
    val sensor: String = "",
    val lector: String = "",
    val activo: Boolean = true,
    val errorSensor: String? = null,
    val errorLector: String? = null,
    val puedeGuardar: Boolean = false,
    val guardando: Boolean = false,
    val terminado: Boolean = false,
)

/** RF-36: códigos del sensor (PS-NN) y del lector (LR-NN), únicos en la tienda. */
class ConfigurarProbadorViewModel(
    private val probadorId: String,
    private val repo: RepositorioTienda,
    private val motor: MotorReglas,
) : ViewModelConMensaje() {
    private val flujo = MutableStateFlow(inicial())
    val estado: StateFlow<ConfigurarProbadorUi> = flujo.asStateFlow()
    private val tocados = mutableSetOf<String>()

    private fun inicial(): ConfigurarProbadorUi {
        val e = repo.estado.value
        val p = e.probadores[probadorId] ?: return ConfigurarProbadorUi()
        return validar(ConfigurarProbadorUi(
            titulo = "Probador ${p.codigo} · ${e.departamentos[p.departamentoId]?.nombre}",
            sensor = p.sensorCodigo, lector = p.lectorCodigo, activo = p.activo,
        ))
    }

    fun alCambiarSensor(v: String) {
        tocados += "sensor"
        flujo.update { validar(it.copy(sensor = v.uppercase())) }
    }

    fun alCambiarLector(v: String) {
        tocados += "lector"
        flujo.update { validar(it.copy(lector = v.uppercase())) }
    }

    fun alCambiarActivo(v: Boolean) = flujo.update { validar(it.copy(activo = v)) }

    private fun validar(u: ConfigurarProbadorUi): ConfigurarProbadorUi {
        val e = repo.estado.value
        val otros = e.probadores.values.filter { it.id != probadorId }
        val nombre = { id: String -> e.probadores[id]?.let { "${it.codigo} de ${e.departamentos[it.departamentoId]?.nombre}" } ?: "" }
        val sensor = Validaciones.codigoDispositivo(u.sensor, "PS", "Ingrese el código del sensor", otros.associate { it.sensorCodigo to nombre(it.id) })
        val lector = Validaciones.codigoDispositivo(u.lector, "LR", "Ingrese el código del lector", otros.associate { it.lectorCodigo to nombre(it.id) })
        return u.copy(
            errorSensor = sensor.takeIf { "sensor" in tocados },
            errorLector = lector.takeIf { "lector" in tocados },
            puedeGuardar = sensor == null && lector == null && !u.guardando,
        )
    }

    fun guardar() {
        val u = flujo.value
        flujo.update { it.copy(guardando = true, puedeGuardar = false) }
        viewModelScope.launch {
            val error = motor.configurarProbador(probadorId, u.sensor, u.lector, u.activo)
            if (error == null) {
                flujo.update { it.copy(guardando = false, terminado = true) }
            } else {
                flujo.update { validar(it.copy(guardando = false)) }
                mensajeFlujo.value = error
            }
        }
    }
}

// ---------------------------------------------------------------------- UI-03 Configuración del equipo (F-03)

data class EquipoUi(
    val tiendas: List<Tienda> = DatosIniciales.tiendas,
    val departamentos: List<Pair<String, String>> = emptyList(),
    val tiendaId: String? = null,
    val uso: UsoEquipo? = null,
    val departamentoId: String? = null,
    val errorTienda: String? = null,
    val errorUso: String? = null,
    val errorDepartamento: String? = null,
    val ubicacion: String? = null,
    val buscandoUbicacion: Boolean = false,
    val puedeGuardar: Boolean = false,
    val guardado: Boolean = false,
)

/** RF-03: tienda del equipo (por ubicación o de la lista) y su uso. */
class EquipoViewModel(
    private val repo: RepositorioTienda,
    private val equipo: RepositorioEquipo,
) : ViewModelConMensaje() {
    private val flujo = MutableStateFlow(validar(inicial(), intento = false))
    val estado: StateFlow<EquipoUi> = flujo.asStateFlow()

    private fun inicial(): EquipoUi {
        val c = equipo.configuracion.value
        return EquipoUi(
            departamentos = repo.estado.value.departamentos.values.map { it.id to it.nombre },
            tiendaId = c?.tiendaId,
            uso = c?.uso,
            departamentoId = c?.departamentoId,
        )
    }

    fun elegirTienda(id: String) = flujo.update { validar(it.copy(tiendaId = id)) }
    fun elegirUso(u: UsoEquipo) = flujo.update { validar(it.copy(uso = u)) }
    fun elegirDepartamento(id: String) = flujo.update { validar(it.copy(departamentoId = id)) }

    fun buscandoUbicacion() = flujo.update { it.copy(buscandoUbicacion = true, ubicacion = null) }

    /** Resultado de la ubicación: la tienda a 2 km o menos, o null (RN-06). */
    fun ubicacionObtenida(tienda: Tienda?, distanciaM: Float?, sinPermiso: Boolean) = flujo.update {
        val texto = when {
            sinPermiso -> "Sin permiso de ubicación. Elija la tienda de la lista."
            tienda == null -> "No hay una tienda a 2 km o menos. Elija la tienda de la lista."
            else -> "Tienda identificada: ${tienda.nombre}, a ${distanciaM?.toInt() ?: 0} m."
        }
        validar(it.copy(tiendaId = tienda?.id ?: it.tiendaId, ubicacion = texto, buscandoUbicacion = false))
    }

    private fun validar(u: EquipoUi, intento: Boolean = true): EquipoUi {
        val tienda = Validaciones.seleccion(u.tiendaId, "Seleccione una tienda")
        val uso = Validaciones.seleccion(u.uso, "Seleccione el uso del equipo")
        val dep = if (u.uso == UsoEquipo.EQUIPO_DEPARTAMENTO) Validaciones.seleccion(u.departamentoId, "Seleccione un departamento") else null
        return u.copy(
            errorTienda = tienda.takeIf { intento && u.tiendaId != null },
            errorUso = uso.takeIf { intento && u.uso != null },
            errorDepartamento = dep.takeIf { intento && u.uso == UsoEquipo.EQUIPO_DEPARTAMENTO && u.departamentoId == null && u.tiendaId != null },
            puedeGuardar = tienda == null && uso == null && dep == null,
        )
    }

    fun guardar() {
        val u = flujo.value
        if (!u.puedeGuardar) return
        viewModelScope.launch {
            val dep = if (u.uso == UsoEquipo.EQUIPO_DEPARTAMENTO) u.departamentoId else null
            equipo.guardar(ConfiguracionEquipo(u.tiendaId!!, u.uso!!, dep))
            flujo.update { it.copy(guardado = true) }
        }
    }
}

// ---------------------------------------------------------------------- UI-22 Pantalla de turnos

data class ProbadorTurnoUi(val codigo: String, val estado: EstadoCliente, val numero: Int?)

data class TurnosUi(
    val departamento: String = "",
    val colorArgb: Long = 0xFF1E3A5F,
    val tienda: String = "",
    val probadores: List<ProbadorTurnoUi> = emptyList(),
    val llamado: Llamado? = null,
    val ultimos: List<Llamado> = emptyList(),
    val enEspera: List<Int> = emptyList(),
    val esperaMin: Int? = null,
    val anuncioVoz: Boolean = true,
)

/** RF-11: lo que muestra el televisor del departamento con los cuatro estados de RN-14. */
class TurnosViewModel(
    private val departamentoId: String,
    repo: RepositorioTienda,
    reloj: Reloj,
) : ViewModel() {
    val estado: StateFlow<TurnosUi> = combine(repo.estado, pulso(reloj)) { e, ahora -> construir(e, ahora) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), TurnosUi())

    private fun construir(e: EstadoTienda, ahora: Long): TurnosUi {
        val d = e.departamentos[departamentoId] ?: return TurnosUi()
        val probadores = e.probadoresDe(departamentoId).filter { it.activo }.map { p ->
            val oc = p.ocupacionId?.let { e.ocupaciones[it] }
            val numero = if (p.numeroId != null) e.numeros[p.numeroId]?.numero else null
            ProbadorTurnoUi(p.codigo, Reglas.estadoCliente(Reglas.estadoVisible(p, oc, ahora)), numero)
        }
        val enEspera = e.numeros.values.filter { it.departamentoId == departamentoId && it.estado == EstadoNumero.EN_ESPERA }.map { it.numero }.sorted()
        val llamados = e.llamados[departamentoId].orEmpty()
        return TurnosUi(
            departamento = d.nombre,
            colorArgb = d.colorArgb,
            tienda = e.tiendaNombre,
            probadores = probadores,
            llamado = llamados.firstOrNull { !it.perdido },
            ultimos = llamados.take(5),
            enEspera = enEspera,
            esperaMin = esperaEstimada(e, departamentoId, enEspera.size + 1),
            anuncioVoz = e.parametros.anuncioVoz,
        )
    }
}

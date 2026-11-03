package cl.duoc.probadorapp.data.repository

import cl.duoc.probadorapp.domain.model.Alerta
import cl.duoc.probadorapp.domain.model.CambioJefatura
import cl.duoc.probadorapp.domain.model.Movimiento
import cl.duoc.probadorapp.domain.model.ObjetoEncontrado
import cl.duoc.probadorapp.domain.model.SolicitudRefuerzo
import cl.duoc.probadorapp.domain.model.Departamento
import cl.duoc.probadorapp.domain.model.Incidencia
import cl.duoc.probadorapp.domain.model.Llamado
import cl.duoc.probadorapp.domain.model.NumeroAtencion
import cl.duoc.probadorapp.domain.model.Ocupacion
import cl.duoc.probadorapp.domain.model.Parametros
import cl.duoc.probadorapp.domain.model.Probador
import cl.duoc.probadorapp.domain.model.Turno
import cl.duoc.probadorapp.domain.model.Usuario
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * Datos compartidos de una tienda, como los guardará Cloud Firestore.
 * Es un valor inmutable: cada cambio produce un nuevo estado.
 */
data class EstadoTienda(
    val tiendaNombre: String = "Sucursal Norte",
    val parametros: Parametros = Parametros(),
    val departamentos: Map<String, Departamento> = emptyMap(),
    val probadores: Map<String, Probador> = emptyMap(),
    val numeros: Map<String, NumeroAtencion> = emptyMap(),
    val ocupaciones: Map<String, Ocupacion> = emptyMap(),
    val alertas: Map<String, Alerta> = emptyMap(),
    val incidencias: Map<String, Incidencia> = emptyMap(),
    val usuarios: Map<String, Usuario> = emptyMap(),
    val turnos: Map<String, Turno> = emptyMap(),
    /** Último número entregado, por departamento y día ("departamento|fecha"). */
    val contadores: Map<String, Int> = emptyMap(),
    /** Llamados por departamento, el más reciente primero. */
    val llamados: Map<String, List<Llamado>> = emptyMap(),
    val movimientos: Map<String, Movimiento> = emptyMap(),
    val solicitudes: Map<String, SolicitudRefuerzo> = emptyMap(),
    val jefaturas: Map<String, CambioJefatura> = emptyMap(),
    val objetos: Map<String, ObjetoEncontrado> = emptyMap(),
) {
    fun probadoresDe(departamentoId: String): List<Probador> =
        probadores.values.filter { it.departamentoId == departamentoId }.sortedBy { it.codigo }

    fun turnoAbierto(uid: String): Turno? = turnos.values.firstOrNull { it.usuarioUid == uid && it.salida == null }

    fun vendedoresEnTurno(departamentoId: String): List<Usuario> = usuarios.values.filter {
        it.rol == cl.duoc.probadorapp.domain.model.Rol.VENDEDOR && it.departamentoId == departamentoId && turnoAbierto(it.uid) != null
    }

    fun jefeDeTienda(): Usuario? = usuarios.values.firstOrNull { it.rol == cl.duoc.probadorapp.domain.model.Rol.JEFE_TIENDA }

    /** RN-32: cobertura = vendedores en turno con asignación activa en el departamento. */
    fun cobertura(departamentoId: String): Int = vendedoresEnTurno(departamentoId).size

    fun personalDe(departamentoId: String): List<Usuario> =
        usuarios.values.filter { it.departamentoId == departamentoId }.sortedWith(compareBy({ it.rol.ordinal * -1 }, { it.nombre }))

    fun jefesDeDepartamento(): List<Usuario> =
        usuarios.values.filter { it.rol == cl.duoc.probadorapp.domain.model.Rol.JEFE_DEPARTAMENTO }.sortedBy { it.nombre }

    fun incidenciasAbiertas(probadorId: String): Int = incidencias.values.count { it.probadorId == probadorId && it.abierta }
}

/**
 * Acceso a los datos de la tienda. Todas las escrituras pasan por
 * [transaccion], igual que las transacciones de Firestore (RNF-11).
 */
interface RepositorioTienda {
    val estado: StateFlow<EstadoTienda>
    suspend fun <T> transaccion(bloque: (EstadoTienda) -> Pair<EstadoTienda, T>): T
}

/** Implementación en memoria para esta etapa, antes de conectar Firestore. */
class RepositorioTiendaLocal(inicial: EstadoTienda) : RepositorioTienda {
    private val flujo = MutableStateFlow(inicial)
    private val mutex = Mutex()
    override val estado: StateFlow<EstadoTienda> = flujo.asStateFlow()

    override suspend fun <T> transaccion(bloque: (EstadoTienda) -> Pair<EstadoTienda, T>): T =
        mutex.withLock {
            val (nuevo, resultado) = bloque(flujo.value)
            flujo.value = nuevo
            resultado
        }
}

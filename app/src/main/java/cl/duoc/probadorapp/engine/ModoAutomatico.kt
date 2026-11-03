package cl.duoc.probadorapp.engine

import cl.duoc.probadorapp.data.repository.RepositorioTienda
import cl.duoc.probadorapp.domain.Reloj
import cl.duoc.probadorapp.domain.model.Canal
import cl.duoc.probadorapp.domain.model.EstadoGuardado
import cl.duoc.probadorapp.domain.model.EstadoNumero
import cl.duoc.probadorapp.domain.model.Prenda
import cl.duoc.probadorapp.signals.SimuladorSenales
import cl.duoc.probadorapp.signals.TipoSenal
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlin.random.Random

/**
 * Modo automático del simulador (RF-39): genera números, entradas, salidas y
 * prendas olvidadas al azar para mostrar el funcionamiento sin intervención.
 */
class ModoAutomatico(
    private val repo: RepositorioTienda,
    private val motor: MotorReglas,
    private val simulador: SimuladorSenales,
    private val catalogo: () -> List<Prenda>,
    private val reloj: Reloj,
    private val random: Random = Random.Default,
) {
    private val activoFlujo = MutableStateFlow(false)
    val activo: StateFlow<Boolean> = activoFlujo
    private var trabajo: Job? = null

    fun activar(scope: CoroutineScope, intervaloMs: Long = 3_000L) {
        if (trabajo?.isActive == true) return
        activoFlujo.value = true
        trabajo = scope.launch {
            while (isActive) {
                paso()
                delay(intervaloMs)
            }
        }
    }

    fun desactivar() {
        trabajo?.cancel()
        trabajo = null
        activoFlujo.value = false
    }

    /** Un paso de la simulación: una acción al azar por departamento. */
    suspend fun paso() {
        val e = repo.estado.value
        val ahora = reloj.ahora()
        for (dep in e.departamentos.keys) {
            val probadores = e.probadoresDe(dep).filter { it.activo && !it.sinSenal }
            val enEspera = e.numeros.values.count { it.departamentoId == dep && it.estado == EstadoNumero.EN_ESPERA }
            if (enEspera < 4 && random.nextInt(100) < 45) {
                motor.entregarNumero(dep, if (random.nextInt(100) < 75) Canal.QR else Canal.SIN_CELULAR)
            }
            val reservado = probadores.firstOrNull { it.estado == EstadoGuardado.RESERVADO }
            if (reservado != null && random.nextInt(100) < 60) {
                simulador.enviar(reservado.sensorCodigo, TipoSenal.ENTRADA)
                val prendas = catalogo().shuffled(random).take(1 + random.nextInt(4))
                simulador.enviar(reservado.lectorCodigo, TipoSenal.PRENDAS_DETECTADAS, prendas)
                continue
            }
            val ocupado = probadores.filter { it.estado == EstadoGuardado.OCUPADO }
                .firstOrNull { p -> p.ocupacionId?.let { e.ocupaciones[it] }?.let { ahora - it.entrada > 20_000L } == true }
            if (ocupado != null && random.nextInt(100) < 50) {
                // En 8 de cada 10 salidas el cliente se lleva las prendas.
                if (random.nextInt(100) < 80) simulador.enviar(ocupado.lectorCodigo, TipoSenal.PRENDAS_RETIRADAS)
                simulador.enviar(ocupado.sensorCodigo, TipoSenal.SALIDA)
                continue
            }
            val revision = probadores.firstOrNull { it.estado == EstadoGuardado.EN_REVISION }
            if (revision != null && random.nextInt(100) < 25) {
                simulador.enviar(revision.lectorCodigo, TipoSenal.PRENDAS_RETIRADAS)
            }
        }
    }
}

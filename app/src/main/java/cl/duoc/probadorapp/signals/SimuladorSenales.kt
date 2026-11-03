package cl.duoc.probadorapp.signals

import cl.duoc.probadorapp.domain.Reloj
import cl.duoc.probadorapp.domain.model.Prenda
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

/**
 * Simulador de sensores y lectores (RF-38). Envía las señales manuales y,
 * cada 15 segundos, la señal de funcionamiento de cada dispositivo que no
 * esté "apagado". Perder la señal de un probador apaga sus dos dispositivos.
 */
class SimuladorSenales(
    private val reloj: Reloj,
    private val dispositivos: () -> List<String>,
) : FuenteSenales {

    private val flujo = MutableSharedFlow<Senal>(extraBufferCapacity = 256)
    override val senales: SharedFlow<Senal> = flujo

    private val apagados = mutableSetOf<String>()

    suspend fun enviar(dispositivo: String, tipo: TipoSenal, prendas: List<Prenda> = emptyList()) {
        flujo.emit(Senal(dispositivo, tipo, reloj.ahora(), prendas))
    }

    /** Deja de enviar la señal de funcionamiento de los dispositivos indicados. */
    fun perderSenal(codigos: List<String>) {
        synchronized(apagados) { apagados.addAll(codigos) }
    }

    /** Vuelve a enviar la señal de funcionamiento y la envía de inmediato. */
    suspend fun recuperarSenal(codigos: List<String>) {
        synchronized(apagados) { apagados.removeAll(codigos.toSet()) }
        codigos.forEach { enviar(it, TipoSenal.FUNCIONAMIENTO) }
    }

    fun estaApagado(codigo: String): Boolean = synchronized(apagados) { codigo in apagados }

    /** Envía una señal de funcionamiento por dispositivo encendido. */
    suspend fun enviarFuncionamiento() {
        dispositivos().filterNot { estaApagado(it) }.forEach { enviar(it, TipoSenal.FUNCIONAMIENTO) }
    }

    fun iniciar(scope: CoroutineScope, intervaloMs: Long = 15_000L) {
        scope.launch {
            while (isActive) {
                enviarFuncionamiento()
                delay(intervaloMs)
            }
        }
    }

    fun reiniciar() {
        synchronized(apagados) { apagados.clear() }
    }
}

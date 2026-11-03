package cl.duoc.probadorapp.signals

import cl.duoc.probadorapp.domain.model.Prenda
import kotlinx.coroutines.flow.Flow

/** Tipos de señal que envían los sensores de presencia y los lectores RFID (RN-21). */
enum class TipoSenal(val texto: String) {
    ENTRADA("Entrada"),
    SALIDA("Salida"),
    PRENDAS_DETECTADAS("Prendas detectadas"),
    PRENDAS_RETIRADAS("Retirar prendas"),
    FUNCIONAMIENTO("Funcionamiento"),
}

/** Una señal incluye el código del dispositivo, el tipo y la hora (RN-21). */
data class Senal(
    val dispositivo: String,
    val tipo: TipoSenal,
    val hora: Long,
    val prendas: List<Prenda> = emptyList(),
)

/**
 * Interfaz única por la que llegan las señales (RNF-10). En esta etapa la
 * implementa el simulador; después, un adaptador para los dispositivos físicos.
 */
interface FuenteSenales {
    val senales: Flow<Senal>
}

package cl.duoc.probadorapp.engine

import cl.duoc.probadorapp.RelojFalso
import cl.duoc.probadorapp.data.repository.DatosIniciales
import cl.duoc.probadorapp.data.repository.RepositorioTiendaLocal
import cl.duoc.probadorapp.domain.model.Canal
import cl.duoc.probadorapp.domain.model.EstadoAlerta
import cl.duoc.probadorapp.domain.model.EstadoGuardado
import cl.duoc.probadorapp.domain.model.EstadoNumero
import cl.duoc.probadorapp.domain.model.TipoAlerta
import cl.duoc.probadorapp.signals.Senal
import cl.duoc.probadorapp.signals.TipoSenal
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class MotorReglasTest {

    private val reloj = RelojFalso()
    private val repo = RepositorioTiendaLocal(DatosIniciales.estado(reloj.ahora()))
    private val motor = MotorReglas(repo, reloj)
    private val p01 get() = repo.estado.value.probadores.getValue("mujer-1")

    private suspend fun senal(dispositivo: String, tipo: TipoSenal, prendas: Int = 0) =
        motor.procesar(Senal(dispositivo, tipo, reloj.ahora(), DatosIniciales.catalogo.take(prendas)))

    @Test
    fun elPrimerNumeroSeLlamaAlProbadorP01() = runBlocking {
        val n = motor.entregarNumero("mujer", Canal.QR)
        assertEquals(1, n.numero)
        assertEquals(EstadoNumero.LLAMADO, n.estado)
        assertEquals(EstadoGuardado.RESERVADO, p01.estado)
        val dos = motor.entregarNumero("mujer", Canal.SIN_CELULAR, entregadoPor = "camila")
        assertEquals(2, dos.numero)
        assertEquals("mujer-2", dos.probadorId)
    }

    @Test
    fun entradaSalidaConPrendaOlvidadaYRetiro() = runBlocking {
        val n = motor.entregarNumero("mujer", Canal.QR)
        senal("PS-01", TipoSenal.ENTRADA)
        assertEquals(EstadoGuardado.OCUPADO, p01.estado)
        assertEquals(EstadoNumero.ATENDIDO, repo.estado.value.numeros.getValue(n.id).estado)
        senal("LR-01", TipoSenal.PRENDAS_DETECTADAS, prendas = 2)
        senal("PS-01", TipoSenal.SALIDA)
        assertEquals(EstadoGuardado.EN_REVISION, p01.estado)
        val alerta = repo.estado.value.alertas.values.single { it.tipo == TipoAlerta.PRENDA_OLVIDADA }
        assertTrue(alerta.destinatarios.containsAll(listOf("camila", "valentina")))
        senal("LR-01", TipoSenal.PRENDAS_RETIRADAS)
        assertEquals(EstadoGuardado.LIBRE, p01.estado)
        assertEquals(EstadoAlerta.CERRADA, repo.estado.value.alertas.getValue(alerta.id).estado)
    }

    @Test
    fun salidaSinPrendasDejaLibreYLlamaAlSiguiente() = runBlocking {
        repeat(9) { motor.entregarNumero("mujer", Canal.QR) }
        assertEquals(EstadoNumero.EN_ESPERA, repo.estado.value.numeros.values.single { it.numero == 9 }.estado)
        senal("PS-01", TipoSenal.ENTRADA)
        senal("PS-01", TipoSenal.SALIDA)
        val nueve = repo.estado.value.numeros.values.single { it.numero == 9 }
        assertEquals(EstadoNumero.LLAMADO, nueve.estado)
        assertEquals("mujer-1", nueve.probadorId)
    }

    @Test
    fun elLlamadoVenceALosDosMinutos() = runBlocking {
        // Se desactiva el control de señales para aislar el plazo del llamado.
        val motor = MotorReglas(repo, reloj, sinSenalMs = 24 * 60 * 60_000L)
        val n = motor.entregarNumero("hombre", Canal.QR)
        reloj.avanzar(119)
        motor.revisarTiempos()
        assertEquals(EstadoNumero.LLAMADO, repo.estado.value.numeros.getValue(n.id).estado)
        reloj.avanzar(1)
        motor.revisarTiempos()
        assertEquals(EstadoNumero.PERDIDO, repo.estado.value.numeros.getValue(n.id).estado)
        assertTrue(repo.estado.value.llamados.getValue("hombre").first().perdido)
    }

    @Test
    fun sinSenalesDuranteSesentaSegundos() = runBlocking {
        reloj.avanzar(30)
        senal("PS-02", TipoSenal.FUNCIONAMIENTO)
        senal("LR-02", TipoSenal.FUNCIONAMIENTO)
        reloj.avanzar(31)
        motor.revisarTiempos()
        assertTrue(p01.sinSenal)
        assertTrue(!repo.estado.value.probadores.getValue("mujer-2").sinSenal)
        assertTrue(repo.estado.value.alertas.values.any { it.tipo == TipoAlerta.SIN_SENAL && it.probadorId == "mujer-1" })
        senal("PS-01", TipoSenal.FUNCIONAMIENTO)
        senal("LR-01", TipoSenal.FUNCIONAMIENTO)
        assertTrue(!p01.sinSenal)
    }

    @Test
    fun tiempoExcedidoCreaUnaSolaAlerta() = runBlocking {
        senal("PS-01", TipoSenal.ENTRADA)
        repeat(4) {
            reloj.avanzar(15 * 60 / 4)
            listOf("PS-01", "LR-01").forEach { senal(it, TipoSenal.FUNCIONAMIENTO) }
            motor.revisarTiempos()
        }
        motor.revisarTiempos()
        assertEquals(1, repo.estado.value.alertas.values.count { it.tipo == TipoAlerta.TIEMPO_EXCEDIDO })
    }
}

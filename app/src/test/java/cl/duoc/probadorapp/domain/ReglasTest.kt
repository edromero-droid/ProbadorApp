package cl.duoc.probadorapp.domain

import cl.duoc.probadorapp.domain.model.Canal
import cl.duoc.probadorapp.domain.model.EstadoCliente
import cl.duoc.probadorapp.domain.model.EstadoGuardado
import cl.duoc.probadorapp.domain.model.EstadoNumero
import cl.duoc.probadorapp.domain.model.EstadoVisible
import cl.duoc.probadorapp.domain.model.NumeroAtencion
import cl.duoc.probadorapp.domain.model.Ocupacion
import cl.duoc.probadorapp.domain.model.Probador
import cl.duoc.probadorapp.domain.model.Rol
import cl.duoc.probadorapp.domain.model.TipoAlerta
import cl.duoc.probadorapp.domain.rules.Marca
import cl.duoc.probadorapp.domain.rules.Reglas
import cl.duoc.probadorapp.domain.rules.ResultadoEntrada
import cl.duoc.probadorapp.domain.rules.ResultadoMovimiento
import cl.duoc.probadorapp.domain.rules.ResultadoSalida
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ReglasTest {

    private fun probador(id: String, dep: String = "mujer", estado: EstadoGuardado = EstadoGuardado.LIBRE, sinSenal: Boolean = false) =
        Probador(id, dep, "P-$id", "PS-$id", "LR-$id", estado = estado, sinSenal = sinSenal)

    private fun numero(n: Int, dep: String = "mujer", estado: EstadoNumero = EstadoNumero.EN_ESPERA) =
        NumeroAtencion("n$n", dep, n, "2026-10-21", Canal.QR, estado, emitido = 0)

    @Test
    fun excedidoCuandoSeCumpleElTiempoMaximo() {
        val p = probador("01", estado = EstadoGuardado.OCUPADO)
        val oc = Ocupacion("o", "01", null, entrada = 0, tiempoMaximoMin = 15)
        assertEquals(EstadoVisible.OCUPADO, Reglas.estadoVisible(p, oc, 14 * 60_000L))
        assertEquals(EstadoVisible.EXCEDIDO, Reglas.estadoVisible(p, oc, 15 * 60_000L))
        assertEquals(EstadoVisible.SIN_SENAL, Reglas.estadoVisible(p.copy(sinSenal = true), oc, 0))
    }

    @Test
    fun asignaElNumeroMasBajoAlProbadorDeCodigoMasBajoDelMismoDepartamento() {
        val probadores = listOf(probador("03"), probador("02"), probador("01", estado = EstadoGuardado.OCUPADO), probador("09", dep = "hombre"))
        val numeros = listOf(numero(14), numero(13), numero(12, estado = EstadoNumero.LLAMADO), numero(1, dep = "hombre"))
        val (n, p) = Reglas.elegirAsignacion("mujer", numeros, probadores)!!
        assertEquals(13, n.numero)
        assertEquals("02", p.id)
    }

    @Test
    fun sinProbadoresDisponiblesNoHayAsignacion() {
        val probadores = listOf(probador("01", sinSenal = true), probador("02", estado = EstadoGuardado.EN_REVISION))
        assertNull(Reglas.elegirAsignacion("mujer", listOf(numero(13)), probadores))
        assertFalse(Reglas.disponible(probador("01").copy(activo = false)))
    }

    @Test
    fun llamadoVenceAlCumplirElPlazo() {
        val n = numero(12, estado = EstadoNumero.LLAMADO).copy(llamado = 0)
        assertFalse(Reglas.llamadoVencido(n, 119_000, 2))
        assertTrue(Reglas.llamadoVencido(n, 120_000, 2))
    }

    @Test
    fun entradaYSalidaSegunElEstado() {
        assertEquals(ResultadoEntrada.OCUPAR_CON_NUMERO, Reglas.resultadoEntrada(EstadoGuardado.RESERVADO))
        assertEquals(ResultadoEntrada.OCUPAR_SIN_NUMERO, Reglas.resultadoEntrada(EstadoGuardado.LIBRE))
        assertEquals(ResultadoEntrada.REVISION_DEL_PERSONAL, Reglas.resultadoEntrada(EstadoGuardado.EN_REVISION))
        assertEquals(ResultadoEntrada.IGNORAR, Reglas.resultadoEntrada(EstadoGuardado.OCUPADO))
        assertEquals(ResultadoSalida.LIBRE, Reglas.resultadoSalida(0, 0))
        assertEquals(ResultadoSalida.EN_REVISION_POR_PRENDAS, Reglas.resultadoSalida(1, 0))
        assertEquals(ResultadoSalida.EN_REVISION_POR_INCIDENCIA, Reglas.resultadoSalida(0, 1))
    }

    @Test
    fun esperaEstimada() {
        assertEquals(10, Reglas.esperaEstimadaMin(3, 6, 10.0, 15))
        assertEquals(20, Reglas.esperaEstimadaMin(7, 6, 10.0, 15))
        assertEquals(15, Reglas.esperaEstimadaMin(1, 6, null, 15))
        assertNull(Reglas.esperaEstimadaMin(1, 0, 10.0, 15))
    }

    @Test
    fun estadoParaElCliente() {
        assertEquals(EstadoCliente.LLAMANDO, Reglas.estadoCliente(EstadoVisible.RESERVADO))
        assertEquals(EstadoCliente.OCUPADO, Reglas.estadoCliente(EstadoVisible.EXCEDIDO))
        assertEquals(EstadoCliente.NO_DISPONIBLE, Reglas.estadoCliente(EstadoVisible.SIN_SENAL))
    }

    @Test
    fun marcasDeTurno() {
        assertTrue(Reglas.marcaPermitida(false, Marca.ENTRADA))
        assertFalse(Reglas.marcaPermitida(true, Marca.ENTRADA))
        assertTrue(Reglas.marcaPermitida(true, Marca.SALIDA))
        assertFalse(Reglas.marcaPermitida(false, Marca.SALIDA))
    }

    @Test
    fun movimientosSegunElRol() {
        assertEquals(ResultadoMovimiento.NO_AUTORIZADO, Reglas.movimientoPermitido(Rol.VENDEDOR, "mujer", "mujer", "hombre", 2, 2))
        assertEquals(ResultadoMovimiento.NO_AUTORIZADO, Reglas.movimientoPermitido(Rol.JEFE_DEPARTAMENTO, "hombre", "mujer", "hombre", 2, 2))
        assertEquals(ResultadoMovimiento.BLOQUEADO_BAJO_MINIMO, Reglas.movimientoPermitido(Rol.JEFE_DEPARTAMENTO, "mujer", "mujer", "hombre", 1, 2))
        assertEquals(ResultadoMovimiento.PERMITIDO, Reglas.movimientoPermitido(Rol.JEFE_DEPARTAMENTO, "mujer", "mujer", "hombre", 2, 2))
        assertEquals(ResultadoMovimiento.PERMITIDO_CON_ADVERTENCIA, Reglas.movimientoPermitido(Rol.JEFE_TIENDA, null, "mujer", "hombre", 1, 2))
        assertEquals(ResultadoMovimiento.MISMO_DEPARTAMENTO, Reglas.movimientoPermitido(Rol.JEFE_TIENDA, null, "mujer", "mujer", 3, 2))
    }

    @Test
    fun jefaturasCompletas() {
        val (sinJefe, repetidos) = Reglas.revisarJefaturas(mapOf("mujer" to "matias", "hombre" to "valentina", "nino" to null), setOf())
        assertEquals(listOf("nino"), sinJefe)
        assertTrue(repetidos.isEmpty())
        val (sin2, rep2) = Reglas.revisarJefaturas(mapOf("mujer" to "matias", "hombre" to "matias", "nino" to null), setOf("nino"))
        assertTrue(sin2.isEmpty())
        assertEquals(listOf("matias"), rep2)
    }

    @Test
    fun destinatariosDeLasAlertas() {
        assertEquals(listOf("camila", "valentina"), Reglas.destinatarios(TipoAlerta.PRENDA_OLVIDADA, listOf("camila"), "valentina", "ana"))
        assertEquals(listOf("matias", "ana"), Reglas.destinatarios(TipoAlerta.COBERTURA_BAJA, listOf("sebastian"), "matias", "ana"))
        assertEquals(listOf("ana"), Reglas.destinatarios(TipoAlerta.SOLICITUD_REFUERZO, emptyList(), "valentina", "ana", escalada = true))
    }

    @Test
    fun solicitudSeEscalaALosDiezMinutos() {
        assertFalse(Reglas.solicitudVencida(0, 599_000))
        assertTrue(Reglas.solicitudVencida(0, 600_000))
    }
}

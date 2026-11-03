package cl.duoc.probadorapp.engine

import cl.duoc.probadorapp.RelojFalso
import cl.duoc.probadorapp.data.repository.DatosIniciales
import cl.duoc.probadorapp.data.repository.RepositorioTiendaLocal
import cl.duoc.probadorapp.domain.model.Canal
import cl.duoc.probadorapp.domain.model.EstadoGuardado
import cl.duoc.probadorapp.domain.model.EstadoNumero
import cl.duoc.probadorapp.domain.model.Parametros
import cl.duoc.probadorapp.domain.model.TipoIncidencia
import cl.duoc.probadorapp.domain.rules.Reglas
import cl.duoc.probadorapp.signals.Senal
import cl.duoc.probadorapp.signals.TipoSenal
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class IncidenciasYAjustesTest {

    private val reloj = RelojFalso()
    private val repo = RepositorioTiendaLocal(DatosIniciales.estado(reloj.ahora()))
    private val motor = MotorReglas(repo, reloj, sinSenalMs = 24 * 3_600_000L)
    private fun prob(id: String) = repo.estado.value.probadores.getValue(id)

    private suspend fun reportar(id: String) =
        motor.reportarIncidencia(id, TipoIncidencia.PROBADOR_SUCIO, "Hay restos de papel en el piso", null, "camila")

    @Test
    fun estadoTrasIncidenciaSegunRn22() {
        assertEquals(EstadoGuardado.EN_REVISION, Reglas.estadoTrasIncidencia(EstadoGuardado.LIBRE))
        assertEquals(EstadoGuardado.EN_REVISION, Reglas.estadoTrasIncidencia(EstadoGuardado.RESERVADO))
        assertEquals(EstadoGuardado.OCUPADO, Reglas.estadoTrasIncidencia(EstadoGuardado.OCUPADO))
        assertEquals(EstadoGuardado.EN_REVISION, Reglas.estadoTrasIncidencia(EstadoGuardado.EN_REVISION))
    }

    @Test
    fun incidenciaEnProbadorLibreLoDejaEnRevision() = runBlocking {
        val inc = reportar("mujer-1")
        assertEquals(EstadoGuardado.EN_REVISION, prob("mujer-1").estado)
        assertTrue(repo.estado.value.incidencias.getValue(inc.id).abierta)
        // El siguiente número ya no se asigna a P-01
        val n = motor.entregarNumero("mujer", Canal.QR)
        assertEquals("mujer-2", n.probadorId)
    }

    @Test
    fun incidenciaEnProbadorReservadoDevuelveElNumeroYLoReasigna() = runBlocking {
        val n = motor.entregarNumero("mujer", Canal.QR)
        assertEquals("mujer-1", n.probadorId)
        reportar("mujer-1")
        val actual = repo.estado.value.numeros.getValue(n.id)
        assertEquals(EstadoNumero.LLAMADO, actual.estado)
        assertEquals("mujer-2", actual.probadorId)
        assertNull(prob("mujer-1").numeroId)
        assertEquals(EstadoGuardado.EN_REVISION, prob("mujer-1").estado)
    }

    @Test
    fun incidenciaEnProbadorOcupadoEsperaLaSalida() = runBlocking {
        motor.procesar(Senal("PS-01", TipoSenal.ENTRADA, reloj.ahora()))
        reportar("mujer-1")
        assertEquals(EstadoGuardado.OCUPADO, prob("mujer-1").estado)
        motor.procesar(Senal("PS-01", TipoSenal.SALIDA, reloj.ahora()))
        assertEquals(EstadoGuardado.EN_REVISION, prob("mujer-1").estado)
    }

    @Test
    fun cerrarLaIncidenciaLiberaElProbador() = runBlocking {
        val inc = reportar("mujer-1")
        motor.cerrarIncidencia(inc.id)
        val cerrada = repo.estado.value.incidencias.getValue(inc.id)
        assertFalse(cerrada.abierta)
        assertEquals(reloj.ahora(), cerrada.cierre)
        assertEquals(EstadoGuardado.LIBRE, prob("mujer-1").estado)
    }

    @Test
    fun conDosIncidenciasSigueEnRevisionHastaCerrarAmbas() = runBlocking {
        val a = reportar("mujer-1")
        val b = reportar("mujer-1")
        motor.cerrarIncidencia(a.id)
        assertEquals(EstadoGuardado.EN_REVISION, prob("mujer-1").estado)
        motor.cerrarIncidencia(b.id)
        assertEquals(EstadoGuardado.LIBRE, prob("mujer-1").estado)
    }

    @Test
    fun losAjustesSoloAfectanALasOcupacionesNuevas() = runBlocking {
        motor.procesar(Senal("PS-01", TipoSenal.ENTRADA, reloj.ahora()))
        motor.aplicarAjustes(Parametros(tiempoMaximoMin = 5, plazoLlamadoMin = 3, bloqueoMin = 2, anuncioVoz = false), mapOf("nino" to 3))
        motor.procesar(Senal("PS-02", TipoSenal.ENTRADA, reloj.ahora()))
        val e = repo.estado.value
        assertEquals(15, e.ocupaciones.getValue(e.probadores.getValue("mujer-1").ocupacionId!!).tiempoMaximoMin)
        assertEquals(5, e.ocupaciones.getValue(e.probadores.getValue("mujer-2").ocupacionId!!).tiempoMaximoMin)
        assertEquals(3, e.departamentos.getValue("nino").minimoPersonal)
        assertEquals(2, e.departamentos.getValue("mujer").minimoPersonal)
        assertEquals(3, e.parametros.plazoLlamadoMin)
    }
}

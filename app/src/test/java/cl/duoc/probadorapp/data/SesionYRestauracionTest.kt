package cl.duoc.probadorapp.data

import cl.duoc.probadorapp.RelojFalso
import cl.duoc.probadorapp.data.repository.DatosIniciales
import cl.duoc.probadorapp.data.repository.RegistrosGuardados
import cl.duoc.probadorapp.data.repository.RepositorioTiendaLocal
import cl.duoc.probadorapp.data.repository.Restauracion
import cl.duoc.probadorapp.data.session.AlmacenCuentasMemoria
import cl.duoc.probadorapp.data.session.RepositorioSesion
import cl.duoc.probadorapp.domain.model.Turno
import cl.duoc.probadorapp.domain.rules.Marca
import cl.duoc.probadorapp.data.session.ResultadoAcceso
import cl.duoc.probadorapp.domain.model.Canal
import cl.duoc.probadorapp.domain.model.EstadoGuardado
import cl.duoc.probadorapp.domain.model.EstadoNumero
import cl.duoc.probadorapp.domain.model.Incidencia
import cl.duoc.probadorapp.domain.model.NumeroAtencion
import cl.duoc.probadorapp.domain.model.Ocupacion
import cl.duoc.probadorapp.domain.model.TipoIncidencia
import cl.duoc.probadorapp.engine.MotorReglas
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class SesionYRestauracionTest {

    private val reloj = RelojFalso()
    private val repo = RepositorioTiendaLocal(DatosIniciales.estado(reloj.ahora()))
    private val sesion = RepositorioSesion(repo, reloj, AlmacenCuentasMemoria(DatosIniciales.usuarios, DatosIniciales.CONTRASENA_DEMO))
    private val correo = "camila.munoz@rinconsur.test"

    @Test
    fun laTabletSeBloqueaTrasElTiempoDeInactividad() = runBlocking {
        sesion.iniciarSesion(correo, DatosIniciales.CONTRASENA_DEMO)
        reloj.avanzar(4 * 60)
        sesion.revisarInactividad(5)
        assertFalse(sesion.bloqueada.value)
        sesion.registrarActividad()
        reloj.avanzar(4 * 60)
        sesion.revisarInactividad(5)
        assertFalse(sesion.bloqueada.value)
        reloj.avanzar(60)
        sesion.revisarInactividad(5)
        assertTrue(sesion.bloqueada.value)
    }

    @Test
    fun elInicioDeSesionValidaLaCuentaGuardada() = runBlocking {
        assertTrue(sesion.iniciarSesion("CAMILA.MUNOZ@rinconsur.test ", DatosIniciales.CONTRASENA_DEMO) is ResultadoAcceso.Correcto)
        val error = sesion.iniciarSesion(correo, "Rincon2025") as ResultadoAcceso.Error
        assertEquals("Correo o contraseña incorrectos", error.mensaje)
        val noExiste = sesion.iniciarSesion("nadie@rinconsur.test", DatosIniciales.CONTRASENA_DEMO)
        assertTrue(noExiste is ResultadoAcceso.Error)
    }

    @Test
    fun laMarcaDeTurnoQuedaEnLosTurnosQueSeGuardan() = runBlocking {
        // diego no está en turno al iniciar
        val antes = repo.estado.value.turnos
        val r = sesion.marcarTurno("diego.torres@rinconsur.test", DatosIniciales.CONTRASENA_DEMO, Marca.ENTRADA)
        assertTrue(r is ResultadoAcceso.Correcto)
        val cambios = Restauracion.cambios(antes, repo.estado.value.turnos)
        assertEquals(1, cambios.guardar.size)
        assertEquals("diego", cambios.guardar.single().usuarioUid)
    }

    @Test
    fun losTurnosGuardadosReemplazanALosIniciales() {
        val guardado = Turno("t1", "diego", reloj.ahora())
        val e = Restauracion.aplicar(repo.estado.value, RegistrosGuardados(turnos = listOf(guardado)), reloj.ahora(), reloj.hoy(), 0L)
        assertEquals(listOf("t1"), e.turnos.keys.toList())
        val sinGuardados = Restauracion.aplicar(repo.estado.value, RegistrosGuardados(), reloj.ahora(), reloj.hoy(), 0L)
        assertEquals(repo.estado.value.turnos, sinGuardados.turnos)
    }

    @Test
    fun desbloquearPideLaContrasenaDeLaMismaPersona() = runBlocking {
        sesion.iniciarSesion(correo, DatosIniciales.CONTRASENA_DEMO)
        reloj.avanzar(6 * 60)
        sesion.revisarInactividad(5)
        assertEquals("Contraseña incorrecta", (sesion.desbloquear("otra") as ResultadoAcceso.Error).mensaje)
        assertTrue(sesion.bloqueada.value)
        assertTrue(sesion.desbloquear(DatosIniciales.CONTRASENA_DEMO) is ResultadoAcceso.Correcto)
        assertFalse(sesion.bloqueada.value)
        assertEquals("camila", sesion.usuario.value?.uid)
        Unit
    }

    @Test
    fun laSesionRestauradaParteBloqueada() {
        sesion.restaurar("valentina")
        assertEquals("valentina", sesion.usuario.value?.uid)
        assertTrue(sesion.bloqueada.value)
        sesion.cerrarSesion()
        assertNull(sesion.usuario.value)
        assertFalse(sesion.bloqueada.value)
    }

    @Test
    fun cambiosDetectaRegistrosNuevosModificadosYBorrados() {
        val a = mapOf("1" to "uno", "2" to "dos")
        val dos = a.getValue("2")
        val b = mapOf("2" to dos, "3" to "tres")
        val c = Restauracion.cambios(a, b)
        assertEquals(listOf("tres"), c.guardar)
        assertEquals(listOf("1"), c.borrar)
        assertTrue(Restauracion.cambios(b, b).vacio)
    }

    @Test
    fun alReiniciarElCorrelativoContinuaYLosLlamadosVuelvenALaEspera() = runBlocking {
        val hoy = reloj.hoy()
        val inicioDia = reloj.ahora() - 3_600_000L
        val numeros = listOf(
            NumeroAtencion("a", "mujer", 1, hoy, Canal.QR, EstadoNumero.ATENDIDO, emitido = reloj.ahora()),
            NumeroAtencion("b", "mujer", 2, hoy, Canal.QR, EstadoNumero.LLAMADO, emitido = reloj.ahora(), probadorId = "mujer-1", llamado = reloj.ahora()),
            NumeroAtencion("c", "mujer", 9, "2026-10-20", Canal.QR, EstadoNumero.EN_ESPERA, emitido = reloj.ahora() - 86_400_000L),
        )
        val ocupaciones = listOf(
            Ocupacion("o1", "mujer-3", "a", entrada = reloj.ahora() - 60_000L, tiempoMaximoMin = 15),
            Ocupacion("o2", "mujer-4", null, entrada = inicioDia - 1, salida = inicioDia, tiempoMaximoMin = 15),
        )
        val incidencias = listOf(
            Incidencia("i1", "mujer-5", TipoIncidencia.PROBADOR_DANADO, "Espejo con una trizadura", null, "camila", reloj.ahora()),
        )
        reloj.avanzar(30)
        val e = Restauracion.aplicar(repo.estado.value, RegistrosGuardados(numeros, ocupaciones, incidencias), reloj.ahora(), hoy, inicioDia)
        assertEquals(2, e.numeros.size)
        assertEquals(EstadoNumero.EN_ESPERA, e.numeros.getValue("b").estado)
        assertNull(e.numeros.getValue("b").probadorId)
        assertEquals(2, e.contadores["mujer|$hoy"])
        assertEquals(reloj.ahora(), e.ocupaciones.getValue("o1").salida)
        assertFalse(e.ocupaciones.containsKey("o2"))
        assertEquals(EstadoGuardado.EN_REVISION, e.probadores.getValue("mujer-5").estado)

        // El siguiente número sigue el correlativo y el número 2 se vuelve a llamar.
        val repo2 = RepositorioTiendaLocal(e)
        val motor = MotorReglas(repo2, reloj, sinSenalMs = 24 * 3_600_000L)
        val n = motor.entregarNumero("mujer", Canal.SIN_CELULAR)
        assertEquals(3, n.numero)
        assertEquals(EstadoNumero.LLAMADO, repo2.estado.value.numeros.getValue("b").estado)
    }
}

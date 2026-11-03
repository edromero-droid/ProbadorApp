package cl.duoc.probadorapp.engine

import cl.duoc.probadorapp.RelojFalso
import cl.duoc.probadorapp.data.repository.DatosIniciales
import cl.duoc.probadorapp.data.repository.RepositorioTiendaLocal
import cl.duoc.probadorapp.domain.model.Canal
import cl.duoc.probadorapp.domain.model.EstadoAlerta
import cl.duoc.probadorapp.domain.model.EstadoGuardado
import cl.duoc.probadorapp.domain.model.EstadoNumero
import cl.duoc.probadorapp.domain.model.EstadoObjeto
import cl.duoc.probadorapp.domain.model.EstadoSolicitud
import cl.duoc.probadorapp.domain.model.TipoAlerta
import cl.duoc.probadorapp.domain.model.TipoMovimiento
import cl.duoc.probadorapp.domain.model.TipoObjeto
import cl.duoc.probadorapp.domain.rules.Jefaturas
import cl.duoc.probadorapp.signals.Senal
import cl.duoc.probadorapp.signals.TipoSenal
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class PersonalYObjetosTest {

    private val reloj = RelojFalso()
    private val repo = RepositorioTiendaLocal(DatosIniciales.estado(reloj.ahora()))
    private val personal = GestionPersonal(repo, reloj)
    private val objetos = GestionObjetos(repo, reloj)
    private val motor = MotorReglas(repo, reloj, sinSenalMs = 24 * 3_600_000L)
    private fun u(uid: String) = repo.estado.value.usuarios.getValue(uid)

    // Al iniciar: Mujer tiene 3 vendedoras en turno (mínimo 2), Hombre 1 (mínimo 2), Niño 1 (mínimo 1).

    @Test
    fun elJefeDeTiendaMueveConAdvertenciaBajoElMinimo() = runBlocking {
        val r = personal.revisarMovimiento(repo.estado.value, u("ana"), "tomas", "hombre")
        assertNull(r.error)
        assertEquals("Niño quedará con 0 vendedores en turno, bajo el mínimo de 1.", r.advertencia)
        assertNull(personal.moverPersona(u("ana"), "tomas", "hombre"))
        assertEquals("hombre", u("tomas").departamentoId)
        assertEquals(TipoMovimiento.DIRECTO, repo.estado.value.movimientos.values.single().tipo)
    }

    @Test
    fun elJefeDeDepartamentoSoloCedeSinQuedarBajoElMinimo() = runBlocking {
        // Valentina cede a una vendedora: Mujer queda con 2, igual al mínimo.
        assertNull(personal.moverPersona(u("valentina"), "camila", "nino"))
        assertEquals(TipoMovimiento.CESION, repo.estado.value.movimientos.values.single().tipo)
        // Una segunda cesión dejaría a Mujer con 1.
        assertEquals("Mujer quedaría bajo su mínimo de 2", personal.moverPersona(u("valentina"), "javiera", "nino"))
        // No puede sacar personal de otro departamento.
        assertTrue(personal.moverPersona(u("valentina"), "sebastian", "mujer")!!.startsWith("Solo puede ceder"))
        assertEquals("Elija un departamento distinto del actual", personal.moverPersona(u("ana"), "diego", "hombre"))
    }

    @Test
    fun solicitudAceptadaMueveAlVendedorYCierraLaAlerta() = runBlocking {
        assertNull(personal.solicitarRefuerzo(u("matias"), "mujer"))
        assertEquals("Ya hay una solicitud pendiente para Mujer", personal.solicitarRefuerzo(u("matias"), "mujer"))
        val s = repo.estado.value.solicitudes.values.single()
        val alerta = repo.estado.value.alertas.values.single { it.tipo == TipoAlerta.SOLICITUD_REFUERZO }
        assertEquals(setOf("valentina", "ana"), alerta.destinatarios.toSet())
        // Fernanda (jefa de Niño) no puede responder una solicitud a Mujer.
        assertEquals("No puede responder esta solicitud", personal.aceptarSolicitud(u("fernanda"), s.id, "camila"))
        assertNull(personal.aceptarSolicitud(u("valentina"), s.id, "camila"))
        assertEquals("hombre", u("camila").departamentoId)
        assertEquals(EstadoSolicitud.ACEPTADA, repo.estado.value.solicitudes.getValue(s.id).estado)
        assertEquals(EstadoAlerta.CERRADA, repo.estado.value.alertas.getValue(alerta.id).estado)
        assertEquals("Esta solicitud ya fue respondida", personal.rechazarSolicitud(u("ana"), s.id, "Sin personal"))
    }

    @Test
    fun rechazoConMotivoYEscalamientoALosDiezMinutos() = runBlocking {
        personal.solicitarRefuerzo(u("matias"), "nino")
        val s = repo.estado.value.solicitudes.values.single()
        assertEquals("Mínimo 5 caracteres", personal.rechazarSolicitud(u("fernanda"), s.id, "no"))
        reloj.avanzar(10 * 60)
        personal.revisar()
        assertEquals(EstadoSolicitud.ESCALADA, repo.estado.value.solicitudes.getValue(s.id).estado)
        val alerta = repo.estado.value.alertas.values.single { it.referenciaId == s.id }
        assertEquals(listOf("ana"), alerta.destinatarios)
        // Escalada: solo el jefe de tienda responde.
        assertEquals("No puede responder esta solicitud", personal.rechazarSolicitud(u("fernanda"), s.id, "No hay vendedores"))
        assertNull(personal.rechazarSolicitud(u("ana"), s.id, "No hay vendedores libres"))
        assertEquals("No hay vendedores libres", repo.estado.value.solicitudes.getValue(s.id).motivo)
    }

    @Test
    fun alertaDeCoberturaBajaUnaSolaVezYSeCierraAlRecuperar() = runBlocking {
        personal.revisar()
        personal.revisar()
        val hombre = repo.estado.value.alertas.values.filter { it.tipo == TipoAlerta.COBERTURA_BAJA && it.departamentoId == "hombre" }
        assertEquals(1, hombre.size)
        assertEquals(setOf("matias", "ana"), hombre.single().destinatarios.toSet())
        personal.moverPersona(u("ana"), "daniela", "hombre")
        personal.revisar()
        assertEquals(EstadoAlerta.CERRADA, repo.estado.value.alertas.getValue(hombre.single().id).estado)
    }

    @Test
    fun cambioDeJefaturaEnCadena() = runBlocking {
        val jefes = repo.estado.value.jefesDeDepartamento().map { it.uid }
        var a = repo.estado.value.departamentos.mapValues { it.value.jefeUid }
        // Matías pasa a Mujer: Hombre queda vacío y Valentina sin departamento.
        a = Jefaturas.asignar(a, "mujer", "matias")
        assertEquals(listOf("hombre"), Jefaturas.vacantes(a, emptySet()))
        assertEquals(listOf("valentina"), Jefaturas.sinAsignar(a, jefes))
        assertFalse(Jefaturas.puedeQuedarACargoDelJefeDeTienda(a, jefes))
        a = Jefaturas.asignar(a, "hombre", "valentina")
        assertTrue(Jefaturas.vacantes(a, emptySet()).isEmpty())
        assertNull(personal.cambiarJefaturas(u("ana"), a))
        val e = repo.estado.value
        assertEquals("matias", e.departamentos.getValue("mujer").jefeUid)
        assertEquals("hombre", e.usuarios.getValue("valentina").departamentoId)
        assertEquals(2, e.jefaturas.size)
        assertEquals("Solo el jefe de tienda cambia las jefaturas", personal.cambiarJefaturas(u("matias"), a))
    }

    @Test
    fun objetoSeAsociaALaUltimaOcupacionYSoloLosJefesDerivan() = runBlocking {
        val n = motor.entregarNumero("mujer", Canal.QR)
        motor.procesar(Senal("PS-01", TipoSenal.ENTRADA, reloj.ahora()))
        motor.procesar(Senal("PS-01", TipoSenal.SALIDA, reloj.ahora()))
        val o = objetos.registrar(u("camila"), "mujer-1", TipoObjeto.BILLETERA, "Billetera café de cuero", null)
        assertNotNull(o)
        assertEquals(n.numero, o!!.numero)
        assertNotNull(o.ocupacionId)
        assertEquals("Solo el jefe de departamento o el jefe de tienda deriva objetos", objetos.derivar(u("camila"), o.id))
        assertNull(objetos.entregar(u("camila"), o.id))
        assertEquals(EstadoObjeto.ENTREGADO, repo.estado.value.objetos.getValue(o.id).estado)
        assertEquals("Este objeto ya fue entregado", objetos.derivar(u("valentina"), o.id))
    }

    @Test
    fun configurarProbadorValidaCodigosYDesactivar() = runBlocking {
        assertEquals("El código ya está asignado al probador P-02 de Mujer", motor.configurarProbador("mujer-1", "PS-02", "LR-01", true))
        assertEquals("Formato LR-NN, por ejemplo LR-03", motor.configurarProbador("mujer-1", "PS-01", "LR1", true))
        val n = motor.entregarNumero("mujer", Canal.QR)
        assertEquals("mujer-1", n.probadorId)
        assertNull(motor.configurarProbador("mujer-1", "ps-31", "LR-31", false))
        val p = repo.estado.value.probadores.getValue("mujer-1")
        assertEquals("PS-31", p.sensorCodigo)
        assertFalse(p.activo)
        assertEquals(EstadoGuardado.LIBRE, p.estado)
        // El número se reasigna a otro probador activo.
        assertEquals("mujer-2", repo.estado.value.numeros.getValue(n.id).probadorId)
        assertEquals(EstadoNumero.LLAMADO, repo.estado.value.numeros.getValue(n.id).estado)
    }
}

class RestauracionPersonalTest {
    @Test
    fun seRecuperanAsignacionesJefesYProbadores() {
        val reloj = RelojFalso()
        val inicial = DatosIniciales.estado(reloj.ahora())
        val p = inicial.probadores.getValue("mujer-1").copy(sensorCodigo = "PS-40", activo = false)
        val g = cl.duoc.probadorapp.data.repository.RegistrosGuardados(
            asignaciones = mapOf("camila" to "hombre"),
            jefes = mapOf("nino" to null),
            probadores = listOf(p),
        )
        val e = cl.duoc.probadorapp.data.repository.Restauracion.aplicar(inicial, g, reloj.ahora(), reloj.hoy(), 0L)
        assertEquals("hombre", e.usuarios.getValue("camila").departamentoId)
        assertNull(e.departamentos.getValue("nino").jefeUid)
        assertEquals("matias", e.departamentos.getValue("hombre").jefeUid)
        assertEquals("PS-40", e.probadores.getValue("mujer-1").sensorCodigo)
        assertFalse(e.probadores.getValue("mujer-1").activo)
    }
}

class PendientesTest {
    @Test
    fun cuentaSolicitudesPorResponderYAlertasSinVer() = runBlocking {
        val reloj = RelojFalso()
        val repo = RepositorioTiendaLocal(DatosIniciales.estado(reloj.ahora()))
        val personal = GestionPersonal(repo, reloj)
        val motor = MotorReglas(repo, reloj, sinSenalMs = 24 * 3_600_000L)
        val u = { uid: String -> repo.estado.value.usuarios.getValue(uid) }
        personal.solicitarRefuerzo(u("matias"), "mujer")
        val p = cl.duoc.probadorapp.ui.viewmodel.pendientesDe(repo.estado.value, u("valentina"), personal)
        assertEquals(1, p.solicitudes)
        assertEquals(1, p.alertas)
        // Fernanda no puede responder una solicitud a Mujer.
        assertEquals(0, cl.duoc.probadorapp.ui.viewmodel.pendientesDe(repo.estado.value, u("fernanda"), personal).solicitudes)
        val alerta = repo.estado.value.alertas.values.single()
        motor.marcarVista(alerta.id, "valentina")
        assertEquals(0, cl.duoc.probadorapp.ui.viewmodel.pendientesDe(repo.estado.value, u("valentina"), personal).alertas)
    }
}

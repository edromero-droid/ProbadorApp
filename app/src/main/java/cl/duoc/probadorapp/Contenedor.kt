package cl.duoc.probadorapp

import android.content.Context
import cl.duoc.probadorapp.data.local.AjustesGuardados
import cl.duoc.probadorapp.data.local.AlmacenCuentasRoom
import cl.duoc.probadorapp.data.local.RepositorioAjustes
import cl.duoc.probadorapp.data.local.BaseDatosLocal
import cl.duoc.probadorapp.data.local.PersistenciaOperacion
import cl.duoc.probadorapp.data.local.PreferenciasEquipo
import cl.duoc.probadorapp.data.local.RepositorioDatosLocales
import cl.duoc.probadorapp.data.repository.DatosIniciales
import cl.duoc.probadorapp.data.repository.RepositorioTienda
import cl.duoc.probadorapp.data.repository.RepositorioTiendaLocal
import cl.duoc.probadorapp.data.repository.Restauracion
import cl.duoc.probadorapp.data.session.RepositorioSesion
import cl.duoc.probadorapp.domain.Reloj
import cl.duoc.probadorapp.domain.RelojSistema
import cl.duoc.probadorapp.engine.GestionObjetos
import cl.duoc.probadorapp.engine.GestionPersonal
import cl.duoc.probadorapp.engine.ModoAutomatico
import cl.duoc.probadorapp.nativo.AvisosAlertas
import cl.duoc.probadorapp.data.local.RepositorioEquipo
import cl.duoc.probadorapp.data.local.RepositorioHistorial
import cl.duoc.probadorapp.engine.MotorReglas
import cl.duoc.probadorapp.signals.SimuladorSenales
import cl.duoc.probadorapp.signals.TipoSenal
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.util.Calendar

/**
 * Crea las dependencias de la aplicación una sola vez (inicio de la aplicación, 4.5.2).
 * En la etapa con Firebase se cambia [repo] por la implementación con Firestore.
 */
class Contenedor(context: Context) {
    val alcance = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    val reloj: Reloj = RelojSistema
    val repo: RepositorioTienda = RepositorioTiendaLocal(DatosIniciales.estado(reloj.ahora()))
    private val db = BaseDatosLocal.crear(context)
    val datosLocales = RepositorioDatosLocales(db)
    private val persistencia = PersistenciaOperacion(db)
    private val cuentas = AlmacenCuentasRoom(db.usuarioDao())
    private val ajustes = RepositorioAjustes(db.ajustesDao())
    val preferencias = PreferenciasEquipo(context)
    val sesion = RepositorioSesion(repo, reloj, cuentas)
    val simulador = SimuladorSenales(reloj) {
        repo.estado.value.probadores.values.filter { it.activo }.flatMap { listOf(it.sensorCodigo, it.lectorCodigo) }
    }
    val motor = MotorReglas(repo, reloj)
    val modoAutomatico = ModoAutomatico(repo, motor, simulador, { datosLocales.catalogo }, reloj)
    val personal = GestionPersonal(repo, reloj)
    val objetos = GestionObjetos(repo, reloj)
    val historial = RepositorioHistorial(db)
    val equipo = RepositorioEquipo(db.equipoDao())
    val avisos = AvisosAlertas(context.applicationContext, repo, sesion)

    private val listoFlujo = MutableStateFlow(false)

    /** Indica que ya se recuperaron los datos guardados y la sesión. */
    val listo: StateFlow<Boolean> = listoFlujo.asStateFlow()

    fun iniciar() {
        alcance.launch {
            // 1. Room: cuentas (UsuarioDao), catálogo, registros del día y ajustes (AjustesDao)
            cuentas.sembrar(DatosIniciales.usuarios, DatosIniciales.CONTRASENA_DEMO)
            datosLocales.cargarCatalogo()
            val inicioDelDia = inicioDelDia(reloj.ahora())
            val guardados = persistencia.cargar(reloj.hoy(), inicioDelDia, datosLocales.catalogo, repo.estado.value.turnos.values)
            repo.transaccion { e -> Restauracion.aplicar(e, guardados, reloj.ahora(), reloj.hoy(), inicioDelDia) to Unit }
            val guardadosAjustes = ajustes.leer(repo.estado.value.departamentos.mapValues { it.value.minimoPersonal })
            motor.aplicarAjustes(guardadosAjustes.parametros, guardadosAjustes.minimos)
            equipo.cargar()

            // 2. Desde aquí, cada cambio se guarda en Room
            persistencia.sincronizar(alcance, repo)

            // 3. Sesión guardada en DataStore: se recupera bloqueada
            preferencias.leerSesion()?.let { sesion.restaurar(it) }
            alcance.launch {
                sesion.usuario.map { it?.uid }.distinctUntilChanged().drop(1).collect { preferencias.guardarSesion(it) }
            }
            listoFlujo.value = true

            // 4. Motor de reglas, simulador y bloqueo por inactividad
            motor.iniciar(alcance, simulador)
            simulador.iniciar(alcance)
            alcance.launch {
                simulador.senales.collect { if (it.tipo != TipoSenal.FUNCIONAMIENTO) datosLocales.registrar(it) }
            }
            avisos.iniciar(alcance)
            alcance.launch {
                while (isActive) {
                    sesion.revisarInactividad(repo.estado.value.parametros.bloqueoMin)
                    personal.revisar()
                    delay(1_000L)
                }
            }
            if (preferencias.modoAutomatico.first()) modoAutomatico.activar(alcance)
        }
    }

    /** RF-37: aplica los ajustes y los guarda en Room con AjustesDao. */
    suspend fun guardarAjustes(nuevos: AjustesGuardados) {
        motor.aplicarAjustes(nuevos.parametros, nuevos.minimos)
        ajustes.guardar(nuevos)
    }

    fun inicioDelDia(ms: Long): Long = Calendar.getInstance().apply {
        timeInMillis = ms
        set(Calendar.HOUR_OF_DAY, 0)
        set(Calendar.MINUTE, 0)
        set(Calendar.SECOND, 0)
        set(Calendar.MILLISECOND, 0)
    }.timeInMillis
}

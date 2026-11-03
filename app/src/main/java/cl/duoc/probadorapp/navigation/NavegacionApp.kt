@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)

package cl.duoc.probadorapp.navigation

import android.Manifest
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Logout
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.Checkroom
import androidx.compose.material.icons.filled.ConfirmationNumber
import androidx.compose.material.icons.filled.Devices
import androidx.compose.material.icons.filled.GridView
import androidx.compose.material.icons.filled.Groups
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Inventory2
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.ReportProblem
import androidx.compose.material.icons.filled.Sensors
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Badge
import androidx.compose.material3.BadgedBox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DrawerValue
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalDrawerSheet
import androidx.compose.material3.ModalNavigationDrawer
import androidx.compose.material3.NavigationDrawerItem
import androidx.compose.material3.NavigationRail
import androidx.compose.material3.NavigationRailItem
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberDrawerState
import androidx.compose.material3.windowsizeclass.WindowWidthSizeClass
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import cl.duoc.probadorapp.Contenedor
import cl.duoc.probadorapp.domain.model.Rol
import cl.duoc.probadorapp.domain.model.UsoEquipo
import cl.duoc.probadorapp.domain.model.Usuario
import cl.duoc.probadorapp.ui.components.LocalAbrirMenu
import cl.duoc.probadorapp.ui.components.LocalPendientesMenu
import cl.duoc.probadorapp.ui.viewmodel.Pendientes
import cl.duoc.probadorapp.ui.viewmodel.pendientesDe
import cl.duoc.probadorapp.ui.screen.PantallaAjustes
import cl.duoc.probadorapp.ui.screen.PantallaAlertas
import cl.duoc.probadorapp.ui.screen.PantallaBloqueo
import cl.duoc.probadorapp.ui.screen.PantallaCola
import cl.duoc.probadorapp.ui.screen.PantallaConfigurarProbador
import cl.duoc.probadorapp.ui.screen.PantallaDetalle
import cl.duoc.probadorapp.ui.screen.PantallaEquipo
import cl.duoc.probadorapp.ui.screen.PantallaHistorial
import cl.duoc.probadorapp.ui.screen.PantallaIncidencias
import cl.duoc.probadorapp.ui.screen.PantallaInicioSesion
import cl.duoc.probadorapp.ui.screen.PantallaJefatura
import cl.duoc.probadorapp.ui.screen.PantallaMarcarTurno
import cl.duoc.probadorapp.ui.screen.PantallaMoverPersona
import cl.duoc.probadorapp.ui.screen.PantallaNumeroSinCelular
import cl.duoc.probadorapp.ui.screen.PantallaObjetos
import cl.duoc.probadorapp.ui.screen.PantallaPanel
import cl.duoc.probadorapp.ui.screen.PantallaPersonal
import cl.duoc.probadorapp.ui.screen.PantallaProbadores
import cl.duoc.probadorapp.ui.screen.PantallaRegistrarObjeto
import cl.duoc.probadorapp.ui.screen.PantallaReportarIncidencia
import cl.duoc.probadorapp.ui.screen.PantallaResponderSolicitud
import cl.duoc.probadorapp.ui.screen.PantallaResumenIncidencia
import cl.duoc.probadorapp.ui.screen.PantallaSimulador
import cl.duoc.probadorapp.ui.screen.PantallaSolicitudes
import cl.duoc.probadorapp.ui.screen.PantallaTurnos
import cl.duoc.probadorapp.ui.viewmodel.AjustesViewModel
import cl.duoc.probadorapp.ui.viewmodel.AlertasViewModel
import cl.duoc.probadorapp.ui.viewmodel.ColaViewModel
import cl.duoc.probadorapp.ui.viewmodel.ConfigurarProbadorViewModel
import cl.duoc.probadorapp.ui.viewmodel.DesbloqueoViewModel
import cl.duoc.probadorapp.ui.viewmodel.DestinoAlerta
import cl.duoc.probadorapp.ui.viewmodel.DetalleViewModel
import cl.duoc.probadorapp.ui.viewmodel.EquipoViewModel
import cl.duoc.probadorapp.ui.viewmodel.HistorialViewModel
import cl.duoc.probadorapp.ui.viewmodel.IncidenciasViewModel
import cl.duoc.probadorapp.ui.viewmodel.JefaturaViewModel
import cl.duoc.probadorapp.ui.viewmodel.MarcaTurnoViewModel
import cl.duoc.probadorapp.ui.viewmodel.MoverPersonaViewModel
import cl.duoc.probadorapp.ui.viewmodel.NumeroSinCelularViewModel
import cl.duoc.probadorapp.ui.viewmodel.ObjetosViewModel
import cl.duoc.probadorapp.ui.viewmodel.PanelViewModel
import cl.duoc.probadorapp.ui.viewmodel.PersonalViewModel
import cl.duoc.probadorapp.ui.viewmodel.ProbadoresViewModel
import cl.duoc.probadorapp.ui.viewmodel.RegistrarObjetoViewModel
import cl.duoc.probadorapp.ui.viewmodel.ReportarIncidenciaViewModel
import cl.duoc.probadorapp.ui.viewmodel.ResponderSolicitudViewModel
import cl.duoc.probadorapp.ui.viewmodel.ResumenIncidenciaViewModel
import cl.duoc.probadorapp.ui.viewmodel.SesionViewModel
import cl.duoc.probadorapp.ui.viewmodel.SimuladorViewModel
import cl.duoc.probadorapp.ui.viewmodel.SolicitudesViewModel
import cl.duoc.probadorapp.ui.viewmodel.TurnosViewModel
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch

object Rutas {
    const val INICIO_SESION = "inicio_sesion"
    const val MARCAR_TURNO = "marcar_turno"
    const val PANEL = "panel"
    const val DETALLE = "detalle/{probadorId}"
    const val NUMERO = "numero/{departamentoId}"
    const val COLA = "cola"
    const val ALERTAS = "alertas"
    const val INCIDENCIAS = "incidencias"
    const val REPORTAR = "reportar_incidencia?probadorId={probadorId}"
    const val RESUMEN = "resumen_incidencia/{incidenciaId}"
    const val OBJETOS = "objetos"
    const val REGISTRAR_OBJETO = "registrar_objeto?probadorId={probadorId}"
    const val PERSONAL = "personal"
    const val MOVER = "mover/{uid}"
    const val SOLICITUDES = "solicitudes"
    const val RESPONDER = "responder/{solicitudId}"
    const val JEFATURA = "jefatura"
    const val HISTORIAL = "historial?uid={uid}"
    const val HISTORIAL_MENU = "historial"
    const val AJUSTES = "ajustes"
    const val PROBADORES = "probadores"
    const val CONFIGURAR_PROBADOR = "configurar_probador/{probadorId}"
    const val EQUIPO = "equipo"
    const val SIMULADOR = "simulador"

    fun detalle(id: String) = "detalle/$id"
    fun numero(departamentoId: String) = "numero/$departamentoId"
    fun reportar(probadorId: String? = null) = if (probadorId == null) "reportar_incidencia" else "reportar_incidencia?probadorId=$probadorId"
    fun resumen(incidenciaId: String) = "resumen_incidencia/$incidenciaId"
    fun registrarObjeto(probadorId: String? = null) = if (probadorId == null) "registrar_objeto" else "registrar_objeto?probadorId=$probadorId"
    fun mover(uid: String) = "mover/$uid"
    fun responder(id: String) = "responder/$id"
    fun historial(uid: String? = null) = if (uid == null) HISTORIAL_MENU else "historial?uid=$uid"
    fun configurarProbador(id: String) = "configurar_probador/$id"
}

/**
 * Punto de entrada de la interfaz. Si el equipo está configurado como equipo de
 * departamento (F-03), muestra la pantalla de turnos; si no, la app del personal.
 * [ancho] es la clase de tamaño de la ventana: define cómo se muestra el menú (guía 9).
 */
@Composable
fun NavegacionApp(c: Contenedor, ancho: WindowWidthSizeClass) {
    val listo by c.listo.collectAsStateWithLifecycle()
    if (!listo) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
        return
    }
    val config by c.equipo.configuracion.collectAsStateWithLifecycle()
    val equipoDepartamento = config?.takeIf { it.uso == UsoEquipo.EQUIPO_DEPARTAMENTO }?.departamentoId
    var configurando by rememberSaveable { mutableStateOf(false) }

    if (equipoDepartamento != null) {
        if (configurando) {
            val vm: EquipoViewModel = viewModel(key = "equipo-$equipoDepartamento", factory = viewModelFactory { initializer { EquipoViewModel(c.repo, c.equipo) } })
            PantallaEquipo(vm, alVolver = { configurando = false }, alGuardar = { configurando = false })
        } else {
            val vm: TurnosViewModel = viewModel(key = "turnos-$equipoDepartamento", factory = viewModelFactory {
                initializer { TurnosViewModel(equipoDepartamento, c.repo, c.reloj) }
            })
            PantallaTurnos(vm) { correo, clave ->
                val ok = c.sesion.esJefeDeTienda(correo, clave)
                if (ok) configurando = true
                ok
            }
        }
        return
    }
    AppPersonal(c, ancho)
}

/** Grafo de navegación de la tablet del personal (figuras 3.1 a 3.4). */
@Composable
private fun AppPersonal(c: Contenedor, ancho: WindowWidthSizeClass) {
    val nav = rememberNavController()
    val usuario by c.sesion.usuario.collectAsStateWithLifecycle()
    val bloqueada by c.sesion.bloqueada.collectAsStateWithLifecycle()
    val inicio = remember { if (c.sesion.usuario.value != null) Rutas.PANEL else Rutas.INICIO_SESION }

    // Si la sesión se cierra, se vuelve al inicio de sesión.
    LaunchedEffect(usuario) {
        val actual = nav.currentDestination?.route
        if (usuario == null && actual != null && actual != Rutas.INICIO_SESION && actual != Rutas.MARCAR_TURNO) {
            nav.navigate(Rutas.INICIO_SESION) { popUpTo(nav.graph.id) { inclusive = true } }
        }
    }

    val volver: () -> Unit = { nav.popBackStack() }

    Box(Modifier.fillMaxSize()) {
        NavHost(navController = nav, startDestination = inicio) {
            composable(Rutas.INICIO_SESION) {
                val vm: SesionViewModel = viewModel(factory = viewModelFactory { initializer { SesionViewModel(c.sesion) } })
                PantallaInicioSesion(
                    vm,
                    alIngresar = { nav.navigate(Rutas.PANEL) { popUpTo(Rutas.INICIO_SESION) { inclusive = true } } },
                    alMarcarTurno = { nav.navigate(Rutas.MARCAR_TURNO) },
                )
            }
            composable(Rutas.MARCAR_TURNO) {
                val vm: MarcaTurnoViewModel = viewModel(factory = viewModelFactory { initializer { MarcaTurnoViewModel(c.sesion) } })
                PantallaMarcarTurno(vm, alVolver = volver)
            }

            // ------------------------------------------------------------ Operación
            composable(Rutas.PANEL) {
                val vm: PanelViewModel = viewModel(factory = viewModelFactory { initializer { PanelViewModel(c.repo, c.motor, c.sesion, c.reloj) } })
                MarcoPersonal(usuario, Rutas.PANEL, nav, c, ancho) {
                    PantallaPanel(vm, alAbrirProbador = { nav.navigate(Rutas.detalle(it)) }, alNumeroSinCelular = { nav.navigate(Rutas.numero(it)) })
                }
            }
            // Transferencia de datos: el id del probador viaja como argumento de la ruta.
            composable(Rutas.DETALLE, arguments = listOf(navArgument("probadorId") { type = NavType.StringType })) { entrada ->
                val id = entrada.arguments?.getString("probadorId").orEmpty()
                val vm: DetalleViewModel = viewModel(key = "detalle-$id", factory = viewModelFactory {
                    initializer { DetalleViewModel(id, c.repo, c.motor, c.reloj) }
                })
                MarcoPersonal(usuario, Rutas.PANEL, nav, c, ancho) {
                    PantallaDetalle(
                        vm, alVolver = volver,
                        alReportar = { nav.navigate(Rutas.reportar(id)) },
                        alRegistrarObjeto = { nav.navigate(Rutas.registrarObjeto(id)) },
                    )
                }
            }
            composable(Rutas.NUMERO, arguments = listOf(navArgument("departamentoId") { type = NavType.StringType })) { entrada ->
                val dep = entrada.arguments?.getString("departamentoId").orEmpty()
                val vm: NumeroSinCelularViewModel = viewModel(factory = viewModelFactory {
                    initializer { NumeroSinCelularViewModel(dep, c.repo, c.motor, c.sesion) }
                })
                MarcoPersonal(usuario, Rutas.PANEL, nav, c, ancho) { PantallaNumeroSinCelular(vm, alTerminar = volver) }
            }
            composable(Rutas.COLA) {
                val vm: ColaViewModel = viewModel(factory = viewModelFactory { initializer { ColaViewModel(c.repo, c.sesion, c.reloj) } })
                MarcoPersonal(usuario, Rutas.COLA, nav, c, ancho) { PantallaCola(vm) }
            }
            composable(Rutas.ALERTAS) {
                val vm: AlertasViewModel = viewModel(factory = viewModelFactory { initializer { AlertasViewModel(c.repo, c.sesion, c.motor) } })
                MarcoPersonal(usuario, Rutas.ALERTAS, nav, c, ancho) {
                    PantallaAlertas(vm) { a ->
                        when (a.destino) {
                            DestinoAlerta.PROBADOR -> a.probadorId?.let { nav.navigate(Rutas.detalle(it)) }
                            DestinoAlerta.PERSONAL -> nav.navigate(Rutas.PERSONAL)
                            DestinoAlerta.SOLICITUDES -> nav.navigate(Rutas.SOLICITUDES)
                        }
                    }
                }
            }

            // ------------------------------------------------------------ Registros
            composable(Rutas.INCIDENCIAS) {
                val vm: IncidenciasViewModel = viewModel(factory = viewModelFactory { initializer { IncidenciasViewModel(c.repo, c.motor, c.sesion) } })
                MarcoPersonal(usuario, Rutas.INCIDENCIAS, nav, c, ancho) {
                    PantallaIncidencias(vm, alReportar = { nav.navigate(Rutas.reportar()) }, alAbrir = { nav.navigate(Rutas.resumen(it)) })
                }
            }
            // Formulario: recibe el probador opcional y, al registrar, envía el id de la incidencia al resumen.
            composable(
                Rutas.REPORTAR,
                arguments = listOf(navArgument("probadorId") { type = NavType.StringType; nullable = true; defaultValue = null }),
            ) { entrada ->
                val probador = entrada.arguments?.getString("probadorId")
                val vm: ReportarIncidenciaViewModel = viewModel(factory = viewModelFactory {
                    initializer { ReportarIncidenciaViewModel(probador, c.repo, c.motor, c.sesion) }
                })
                MarcoPersonal(usuario, Rutas.INCIDENCIAS, nav, c, ancho) {
                    PantallaReportarIncidencia(
                        vm, alVolver = volver,
                        alRegistrar = { id -> nav.navigate(Rutas.resumen(id)) { popUpTo(Rutas.REPORTAR) { inclusive = true } } },
                    )
                }
            }
            composable(Rutas.RESUMEN, arguments = listOf(navArgument("incidenciaId") { type = NavType.StringType })) { entrada ->
                val id = entrada.arguments?.getString("incidenciaId").orEmpty()
                val vm: ResumenIncidenciaViewModel = viewModel(key = "resumen-$id", factory = viewModelFactory {
                    initializer { ResumenIncidenciaViewModel(id, c.repo) }
                })
                MarcoPersonal(usuario, Rutas.INCIDENCIAS, nav, c, ancho) {
                    PantallaResumenIncidencia(
                        vm, alVolver = volver,
                        alVerIncidencias = { nav.navigate(Rutas.INCIDENCIAS) { popUpTo(Rutas.PANEL); launchSingleTop = true } },
                        alIrAlPanel = { nav.navigate(Rutas.PANEL) { popUpTo(Rutas.PANEL) { inclusive = true } } },
                    )
                }
            }
            composable(Rutas.OBJETOS) {
                val vm: ObjetosViewModel = viewModel(factory = viewModelFactory { initializer { ObjetosViewModel(c.repo, c.sesion, c.objetos) } })
                MarcoPersonal(usuario, Rutas.OBJETOS, nav, c, ancho) { PantallaObjetos(vm, alRegistrar = { nav.navigate(Rutas.registrarObjeto()) }) }
            }
            composable(
                Rutas.REGISTRAR_OBJETO,
                arguments = listOf(navArgument("probadorId") { type = NavType.StringType; nullable = true; defaultValue = null }),
            ) { entrada ->
                val probador = entrada.arguments?.getString("probadorId")
                val vm: RegistrarObjetoViewModel = viewModel(factory = viewModelFactory {
                    initializer { RegistrarObjetoViewModel(probador, c.repo, c.sesion, c.objetos) }
                })
                MarcoPersonal(usuario, Rutas.OBJETOS, nav, c, ancho) {
                    PantallaRegistrarObjeto(
                        vm, alVolver = volver,
                        alRegistrar = { nav.navigate(Rutas.OBJETOS) { popUpTo(Rutas.PANEL); launchSingleTop = true } },
                    )
                }
            }

            // ------------------------------------------------------------ Personal
            composable(Rutas.PERSONAL) {
                val vm: PersonalViewModel = viewModel(factory = viewModelFactory { initializer { PersonalViewModel(c.repo, c.sesion) } })
                MarcoPersonal(usuario, Rutas.PERSONAL, nav, c, ancho) {
                    PantallaPersonal(
                        vm,
                        alMover = { nav.navigate(Rutas.mover(it)) },
                        alVerHistorial = { nav.navigate(Rutas.historial(it)) },
                        alCambiarJefatura = { nav.navigate(Rutas.JEFATURA) },
                        alSolicitudes = { nav.navigate(Rutas.SOLICITUDES) },
                    )
                }
            }
            composable(Rutas.MOVER, arguments = listOf(navArgument("uid") { type = NavType.StringType })) { entrada ->
                val uid = entrada.arguments?.getString("uid").orEmpty()
                val vm: MoverPersonaViewModel = viewModel(key = "mover-$uid", factory = viewModelFactory {
                    initializer { MoverPersonaViewModel(uid, c.repo, c.sesion, c.personal) }
                })
                MarcoPersonal(usuario, Rutas.PERSONAL, nav, c, ancho) { PantallaMoverPersona(vm, alVolver = volver) }
            }
            composable(Rutas.SOLICITUDES) {
                val vm: SolicitudesViewModel = viewModel(factory = viewModelFactory { initializer { SolicitudesViewModel(c.repo, c.sesion, c.personal) } })
                MarcoPersonal(usuario, Rutas.SOLICITUDES, nav, c, ancho) { PantallaSolicitudes(vm, alResponder = { nav.navigate(Rutas.responder(it)) }) }
            }
            composable(Rutas.RESPONDER, arguments = listOf(navArgument("solicitudId") { type = NavType.StringType })) { entrada ->
                val id = entrada.arguments?.getString("solicitudId").orEmpty()
                val vm: ResponderSolicitudViewModel = viewModel(key = "responder-$id", factory = viewModelFactory {
                    initializer { ResponderSolicitudViewModel(id, c.repo, c.sesion, c.personal) }
                })
                MarcoPersonal(usuario, Rutas.SOLICITUDES, nav, c, ancho) { PantallaResponderSolicitud(vm, alVolver = volver) }
            }
            composable(Rutas.JEFATURA) {
                val vm: JefaturaViewModel = viewModel(factory = viewModelFactory { initializer { JefaturaViewModel(c.repo, c.sesion, c.personal) } })
                MarcoPersonal(usuario, Rutas.PERSONAL, nav, c, ancho) { PantallaJefatura(vm, alVolver = volver) }
            }
            composable(
                Rutas.HISTORIAL,
                arguments = listOf(navArgument("uid") { type = NavType.StringType; nullable = true; defaultValue = null }),
            ) { entrada ->
                val uid = entrada.arguments?.getString("uid")
                val vm: HistorialViewModel = viewModel(key = "historial-$uid", factory = viewModelFactory {
                    initializer { HistorialViewModel(uid, c.repo, c.sesion, c.historial, c.reloj) }
                })
                MarcoPersonal(usuario, Rutas.HISTORIAL_MENU, nav, c, ancho) { PantallaHistorial(vm, alVolver = if (uid != null) volver else null) }
            }

            // ------------------------------------------------------------ Configuración
            composable(Rutas.AJUSTES) {
                val vm: AjustesViewModel = viewModel(factory = viewModelFactory {
                    initializer { AjustesViewModel(c.repo, c.sesion) { c.guardarAjustes(it) } }
                })
                MarcoPersonal(usuario, Rutas.AJUSTES, nav, c, ancho) { PantallaAjustes(vm) }
            }
            composable(Rutas.PROBADORES) {
                val vm: ProbadoresViewModel = viewModel(factory = viewModelFactory { initializer { ProbadoresViewModel(c.repo) } })
                MarcoPersonal(usuario, Rutas.PROBADORES, nav, c, ancho) { PantallaProbadores(vm, alConfigurar = { nav.navigate(Rutas.configurarProbador(it)) }) }
            }
            composable(Rutas.CONFIGURAR_PROBADOR, arguments = listOf(navArgument("probadorId") { type = NavType.StringType })) { entrada ->
                val id = entrada.arguments?.getString("probadorId").orEmpty()
                val vm: ConfigurarProbadorViewModel = viewModel(key = "configurar-$id", factory = viewModelFactory {
                    initializer { ConfigurarProbadorViewModel(id, c.repo, c.motor) }
                })
                MarcoPersonal(usuario, Rutas.PROBADORES, nav, c, ancho) { PantallaConfigurarProbador(vm, alVolver = volver) }
            }
            composable(Rutas.EQUIPO) {
                val vm: EquipoViewModel = viewModel(factory = viewModelFactory { initializer { EquipoViewModel(c.repo, c.equipo) } })
                MarcoPersonal(usuario, Rutas.EQUIPO, nav, c, ancho) { PantallaEquipo(vm, alVolver = null, alGuardar = { nav.navigate(Rutas.PANEL) { popUpTo(Rutas.PANEL) { inclusive = true } } }) }
            }
            composable(Rutas.SIMULADOR) {
                val vm: SimuladorViewModel = viewModel(factory = viewModelFactory {
                    initializer {
                        SimuladorViewModel(
                            repo = c.repo,
                            simulador = c.simulador,
                            motor = c.motor,
                            modo = c.modoAutomatico,
                            catalogo = { c.datosLocales.catalogo },
                            ultimasSenales = c.datosLocales.ultimasSenales(),
                            alcanceApp = c.alcance,
                            guardarModo = { c.preferencias.guardarModoAutomatico(it) },
                            borrarSenales = { c.datosLocales.borrarSenales() },
                        )
                    }
                })
                MarcoPersonal(usuario, Rutas.SIMULADOR, nav, c, ancho) { PantallaSimulador(vm) }
            }
        }

        val actual = usuario
        if (actual != null && !bloqueada) {
            AvisoNuevoDepartamento(c)
            PermisoNotificaciones(c)
        }

        // RF-02: bloqueo por inactividad sobre la pantalla actual.
        if (actual != null && bloqueada) {
            val vm: DesbloqueoViewModel = viewModel(factory = viewModelFactory { initializer { DesbloqueoViewModel(c.sesion) } })
            PantallaBloqueo(actual, vm)
        }
    }
}

/** RN-30: "Te asignaron al departamento {nombre}"; al confirmar, la tablet muestra el nuevo departamento. */
@Composable
private fun AvisoNuevoDepartamento(c: Contenedor) {
    val flujo = remember { combine(c.repo.estado, c.sesion.usuario) { _, _ -> c.sesion.asignacionPendiente() }.distinctUntilChanged() }
    val pendiente by flujo.collectAsStateWithLifecycle(null)
    val dep = pendiente ?: return
    val nombre = c.repo.estado.value.departamentos[dep]?.nombre ?: "sin departamento"
    AlertDialog(
        onDismissRequest = {},
        icon = { Icon(Icons.Filled.Groups, contentDescription = null) },
        title = { Text("Cambio de departamento") },
        text = { Text("Te asignaron al departamento $nombre.") },
        confirmButton = { TextButton(onClick = { c.sesion.confirmarAsignacion() }) { Text("Aceptar") } },
    )
}

/**
 * Sección 2.6: al iniciar sesión por primera vez se explica para qué sirven las
 * notificaciones y luego se pide el permiso (Android 13 o superior). Si se deniega,
 * las alertas se muestran solo dentro de la aplicación.
 */
@Composable
private fun PermisoNotificaciones(c: Contenedor) {
    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) return
    val preguntadas by c.preferencias.notificacionesPreguntadas.collectAsStateWithLifecycle(true)
    val alcance = rememberCoroutineScope()
    val lanzador = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) {
        alcance.launch { c.preferencias.marcarNotificacionesPreguntadas() }
    }
    if (preguntadas || c.avisos.permitidas()) return
    AlertDialog(
        onDismissRequest = {},
        icon = { Icon(Icons.Filled.Notifications, contentDescription = null) },
        title = { Text("Notificaciones de alertas") },
        text = { Text("La aplicación avisa con una notificación y un sonido cuando un probador supera el tiempo, queda una prenda olvidada, un probador pierde la señal, falta personal o llega una solicitud de refuerzo.") },
        confirmButton = { TextButton(onClick = { lanzador.launch(Manifest.permission.POST_NOTIFICATIONS) }) { Text("Continuar") } },
        dismissButton = {
            TextButton(onClick = { alcance.launch { c.preferencias.marcarNotificacionesPreguntadas() } }) { Text("Ahora no") }
        },
    )
}

private data class OpcionMenu(val texto: String, val icono: ImageVector, val ruta: String)

/** Opciones del menú según el rol (RN-03). */
private fun opcionesDe(usuario: Usuario?): List<OpcionMenu> = buildList {
    val jefe = usuario?.rol == Rol.JEFE_DEPARTAMENTO || usuario?.rol == Rol.JEFE_TIENDA
    add(OpcionMenu("Panel", Icons.Filled.GridView, Rutas.PANEL))
    add(OpcionMenu("Cola del día", Icons.Filled.ConfirmationNumber, Rutas.COLA))
    add(OpcionMenu("Alertas", Icons.Filled.Notifications, Rutas.ALERTAS))
    add(OpcionMenu("Incidencias", Icons.Filled.ReportProblem, Rutas.INCIDENCIAS))
    add(OpcionMenu("Objetos encontrados", Icons.Filled.Inventory2, Rutas.OBJETOS))
    if (jefe) {
        add(OpcionMenu("Personal", Icons.Filled.Groups, Rutas.PERSONAL))
        add(OpcionMenu("Solicitudes", Icons.AutoMirrored.Filled.Send, Rutas.SOLICITUDES))
        add(OpcionMenu("Historial", Icons.Filled.History, Rutas.HISTORIAL_MENU))
        add(OpcionMenu("Ajustes", Icons.Filled.Settings, Rutas.AJUSTES))
    }
    if (usuario?.rol == Rol.JEFE_TIENDA) {
        add(OpcionMenu("Probadores", Icons.Filled.Sensors, Rutas.PROBADORES))
        add(OpcionMenu("Equipo", Icons.Filled.Devices, Rutas.EQUIPO))
        add(OpcionMenu("Simulador", Icons.Filled.Tune, Rutas.SIMULADOR))
    }
}

/**
 * Menú lateral adaptable (guía 9):
 * - Ventana expandida (tablet horizontal): menú fijo con textos.
 * - Ventana mediana (tablet vertical): barra lateral con íconos.
 * - Ventana compacta (celular): menú oculto que se abre con el botón de la barra superior.
 */
@Composable
private fun MarcoPersonal(
    usuario: Usuario?,
    actual: String,
    nav: NavHostController,
    c: Contenedor,
    ancho: WindowWidthSizeClass,
    contenido: @Composable () -> Unit,
) {
    val opciones = opcionesDe(usuario)
    // Insignias del menú: alertas sin ver y solicitudes por responder.
    val flujoPendientes = remember(usuario?.uid) {
        c.repo.estado.map { pendientesDe(it, c.sesion.usuario.value, c.personal) }.distinctUntilChanged()
    }
    val pendientes by flujoPendientes.collectAsStateWithLifecycle(Pendientes())
    val insignia: (String) -> Int = { ruta ->
        when (ruta) {
            Rutas.ALERTAS -> pendientes.alertas
            Rutas.SOLICITUDES -> pendientes.solicitudes
            else -> 0
        }
    }
    val ir: (String) -> Unit = { ruta ->
        if (ruta != actual) nav.navigate(ruta) { popUpTo(Rutas.PANEL); launchSingleTop = true }
    }
    when (ancho) {
        WindowWidthSizeClass.Expanded -> Row(Modifier.fillMaxSize()) {
            Surface(Modifier.width(240.dp).fillMaxHeight(), tonalElevation = 1.dp) {
                ContenidoMenu(usuario, opciones, actual, insignia, ir) { c.sesion.cerrarSesion() }
            }
            Box(Modifier.weight(1f).fillMaxHeight()) { contenido() }
        }
        WindowWidthSizeClass.Medium -> Row(Modifier.fillMaxSize()) {
            NavigationRail(header = {
                Icon(Icons.Filled.Checkroom, contentDescription = "ProbadorApp", tint = MaterialTheme.colorScheme.primary)
            }) {
                Column(Modifier.weight(1f).verticalScroll(rememberScrollState()), horizontalAlignment = Alignment.CenterHorizontally) {
                    opciones.forEach { o ->
                        NavigationRailItem(
                            selected = o.ruta == actual,
                            onClick = { ir(o.ruta) },
                            icon = {
                                val n = insignia(o.ruta)
                                BadgedBox(badge = { if (n > 0) Badge { Text(n.toString()) } }) {
                                    Icon(o.icono, contentDescription = o.texto)
                                }
                            },
                            label = { Text(o.texto, maxLines = 1) },
                        )
                    }
                }
                NavigationRailItem(
                    selected = false,
                    onClick = { c.sesion.cerrarSesion() },
                    icon = { Icon(Icons.AutoMirrored.Filled.Logout, contentDescription = null) },
                    label = { Text("Salir") },
                )
            }
            Box(Modifier.weight(1f).fillMaxHeight()) { contenido() }
        }
        else -> {
            val estadoMenu = rememberDrawerState(DrawerValue.Closed)
            val alcance = rememberCoroutineScope()
            val cerrarYIr: (String) -> Unit = { ruta ->
                alcance.launch { estadoMenu.close() }
                ir(ruta)
            }
            ModalNavigationDrawer(
                drawerState = estadoMenu,
                drawerContent = {
                    ModalDrawerSheet {
                        ContenidoMenu(usuario, opciones, actual, insignia, cerrarYIr) { c.sesion.cerrarSesion() }
                    }
                },
            ) {
                val abrir: () -> Unit = { alcance.launch { estadoMenu.open() } }
                CompositionLocalProvider(LocalAbrirMenu provides abrir, LocalPendientesMenu provides pendientes.total) { contenido() }
            }
        }
    }
}

@Composable
private fun ContenidoMenu(
    usuario: Usuario?,
    opciones: List<OpcionMenu>,
    actual: String,
    insignia: (String) -> Int,
    ir: (String) -> Unit,
    cerrarSesion: () -> Unit,
) {
    Column(Modifier.fillMaxHeight().padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Row(Modifier.padding(8.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(36.dp).background(MaterialTheme.colorScheme.primary, MaterialTheme.shapes.small), contentAlignment = Alignment.Center) {
                Icon(Icons.Filled.Checkroom, contentDescription = null, tint = MaterialTheme.colorScheme.onPrimary)
            }
            Spacer(Modifier.size(10.dp))
            Text("ProbadorApp", fontWeight = FontWeight.ExtraBold, color = MaterialTheme.colorScheme.primary)
        }
        Column(Modifier.weight(1f).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            opciones.forEach { o ->
                val n = insignia(o.ruta)
                NavigationDrawerItem(
                    label = { Text(o.texto) },
                    icon = { Icon(o.icono, contentDescription = null) },
                    badge = { if (n > 0) Badge { Text(n.toString()) } },
                    selected = o.ruta == actual,
                    onClick = { ir(o.ruta) },
                )
            }
        }
        HorizontalDivider()
        usuario?.let {
            Column(Modifier.padding(8.dp)) {
                Text(it.nombre, fontWeight = FontWeight.Bold)
                Text(it.rol.texto, style = MaterialTheme.typography.bodySmall)
            }
        }
        NavigationDrawerItem(
            label = { Text("Cerrar sesión") },
            icon = { Icon(Icons.AutoMirrored.Filled.Logout, contentDescription = null) },
            selected = false,
            onClick = cerrarSesion,
        )
    }
}

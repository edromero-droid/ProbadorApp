@file:OptIn(ExperimentalMaterial3Api::class)

package cl.duoc.probadorapp.ui.screen

import android.Manifest
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Category
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.Contactless
import androidx.compose.material.icons.filled.Devices
import androidx.compose.material.icons.filled.Email
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.MyLocation
import androidx.compose.material.icons.filled.Save
import androidx.compose.material.icons.filled.Sensors
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Store
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import cl.duoc.probadorapp.domain.model.EstadoCliente
import cl.duoc.probadorapp.domain.model.UsoEquipo
import cl.duoc.probadorapp.nativo.AnunciadorVoz
import cl.duoc.probadorapp.nativo.Ubicacion
import cl.duoc.probadorapp.ui.components.BarraSuperior
import cl.duoc.probadorapp.ui.components.CampoFormulario
import cl.duoc.probadorapp.ui.components.GrupoOpciones
import cl.duoc.probadorapp.ui.components.MostrarMensaje
import cl.duoc.probadorapp.ui.viewmodel.ConfigurarProbadorViewModel
import cl.duoc.probadorapp.ui.viewmodel.EquipoViewModel
import cl.duoc.probadorapp.ui.viewmodel.ProbadoresViewModel
import cl.duoc.probadorapp.ui.viewmodel.TurnosViewModel
import kotlinx.coroutines.launch

// ---------------------------------------------------------------------- UI-18 Probadores y dispositivos

@Composable
fun PantallaProbadores(vm: ProbadoresViewModel, alConfigurar: (String) -> Unit) {
    val deps by vm.estado.collectAsStateWithLifecycle()
    Scaffold(topBar = { BarraSuperior("Probadores y dispositivos") }) { relleno ->
        LazyColumn(Modifier.fillMaxSize().padding(relleno).padding(horizontal = 16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            deps.forEach { d ->
                item { Text(d.nombre, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, modifier = Modifier.padding(top = 8.dp)) }
                items(d.probadores, key = { it.id }) { p ->
                    Card(onClick = { alConfigurar(p.id) }, modifier = Modifier.fillMaxWidth()) {
                        Row(Modifier.padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
                            Text(p.codigo, fontWeight = FontWeight.ExtraBold, modifier = Modifier.width(64.dp))
                            Column(Modifier.weight(1f)) {
                                Text("Sensor ${p.sensor} · Lector ${p.lector}")
                                Text(if (p.activo) "Activo" else "Inactivo: no recibe números", style = MaterialTheme.typography.bodySmall,
                                    color = if (p.activo) Color(0xFF2E7D32) else MaterialTheme.colorScheme.error)
                            }
                            Icon(Icons.Filled.ChevronRight, contentDescription = "Configurar ${p.codigo}")
                        }
                    }
                }
            }
            item { Spacer(Modifier.height(16.dp)) }
        }
    }
}

// ---------------------------------------------------------------------- UI-19 Configurar probador

@Composable
fun PantallaConfigurarProbador(vm: ConfigurarProbadorViewModel, alVolver: () -> Unit) {
    val ui by vm.estado.collectAsStateWithLifecycle()
    val mensaje by vm.mensaje.collectAsStateWithLifecycle()
    val avisos = remember { SnackbarHostState() }
    var confirmarDesactivar by remember { mutableStateOf(false) }
    MostrarMensaje(mensaje, avisos, vm::mensajeMostrado)
    LaunchedEffect(ui.terminado) { if (ui.terminado) alVolver() }
    Scaffold(topBar = { BarraSuperior(ui.titulo, alVolver) }, snackbarHost = { SnackbarHost(avisos) }) { relleno ->
        Box(Modifier.fillMaxSize().padding(relleno), contentAlignment = Alignment.TopCenter) {
            Column(Modifier.widthIn(max = 640.dp).fillMaxWidth().padding(20.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
                CampoFormulario(ui.sensor, vm::alCambiarSensor, "Código del sensor", Icons.Filled.Sensors, ui.errorSensor, apoyo = "Formato PS-NN")
                CampoFormulario(ui.lector, vm::alCambiarLector, "Código del lector", Icons.Filled.Contactless, ui.errorLector, apoyo = "Formato LR-NN")
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text("Activo", fontWeight = FontWeight.SemiBold)
                        Text("Un probador inactivo no recibe números.", style = MaterialTheme.typography.bodySmall)
                    }
                    Switch(checked = ui.activo, onCheckedChange = { if (it) vm.alCambiarActivo(true) else confirmarDesactivar = true })
                }
                Button(onClick = vm::guardar, enabled = ui.puedeGuardar, modifier = Modifier.fillMaxWidth().height(52.dp)) {
                    if (ui.guardando) {
                        CircularProgressIndicator(Modifier.size(22.dp), strokeWidth = 2.dp)
                    } else {
                        Icon(Icons.Filled.Save, contentDescription = null)
                        Spacer(Modifier.size(8.dp))
                        Text("Guardar", fontWeight = FontWeight.Bold)
                    }
                }
            }
        }
    }
    if (confirmarDesactivar) {
        AlertDialog(
            onDismissRequest = { confirmarDesactivar = false },
            title = { Text("Desactivar probador") },
            text = { Text("¿Desactivar el ${ui.titulo.substringBefore(" ·").lowercase()}? No se le asignarán números.") },
            confirmButton = { TextButton(onClick = { vm.alCambiarActivo(false); confirmarDesactivar = false }) { Text("Desactivar") } },
            dismissButton = { TextButton(onClick = { confirmarDesactivar = false }) { Text("Cancelar") } },
        )
    }
}

// ---------------------------------------------------------------------- UI-03 Configuración del equipo

/**
 * Formulario F-03. "Detectar con la ubicación" pide el permiso de ubicación
 * aproximada después de explicar su uso; si se deniega, se elige de la lista (RN-06).
 */
@Composable
fun PantallaEquipo(vm: EquipoViewModel, alVolver: (() -> Unit)?, alGuardar: () -> Unit) {
    val ui by vm.estado.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val alcance = rememberCoroutineScope()
    var explicar by remember { mutableStateOf(false) }
    LaunchedEffect(ui.guardado) { if (ui.guardado) alGuardar() }

    fun detectar() {
        vm.buscandoUbicacion()
        alcance.launch {
            val ubicacion = Ubicacion.obtener(context)
            val cercana = ubicacion?.let { Ubicacion.tiendaCercana(it, ui.tiendas) }
            vm.ubicacionObtenida(cercana?.first, cercana?.second, sinPermiso = false)
        }
    }

    val permiso = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { concedido ->
        if (concedido) detectar() else vm.ubicacionObtenida(null, null, sinPermiso = true)
    }

    Scaffold(topBar = { BarraSuperior("Configuración del equipo", alVolver) }) { relleno ->
        Box(Modifier.fillMaxSize().padding(relleno), contentAlignment = Alignment.TopCenter) {
            Column(
                Modifier.widthIn(max = 640.dp).fillMaxWidth().verticalScroll(rememberScrollState()).padding(20.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp),
            ) {
                OutlinedButton(
                    onClick = { if (Ubicacion.permitida(context)) detectar() else explicar = true },
                    enabled = !ui.buscandoUbicacion,
                    modifier = Modifier.fillMaxWidth().height(48.dp),
                ) {
                    if (ui.buscandoUbicacion) CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp)
                    else Icon(Icons.Filled.MyLocation, contentDescription = null)
                    Spacer(Modifier.size(8.dp))
                    Text("Detectar la tienda con la ubicación")
                }
                ui.ubicacion?.let { Text(it, style = MaterialTheme.typography.bodyMedium) }
                Selector("Tienda", ui.tiendas.map { it.id to "${it.nombre} · ${it.empresa}" }, ui.tiendaId, vm::elegirTienda, Icons.Filled.Store, ui.errorTienda)
                if (ui.tiendaId != null && ui.tiendaId != "norte") {
                    Text("En esta versión solo Sucursal Norte tiene datos de demostración.", style = MaterialTheme.typography.bodySmall)
                }
                GrupoOpciones("Uso del equipo", Icons.Filled.Devices, UsoEquipo.entries.map { it.name to it.texto }, ui.uso?.name,
                    { vm.elegirUso(UsoEquipo.valueOf(it)) }, ui.errorUso)
                if (ui.uso == UsoEquipo.EQUIPO_DEPARTAMENTO) {
                    Selector("Departamento", ui.departamentos, ui.departamentoId, vm::elegirDepartamento, Icons.Filled.Category, ui.errorDepartamento)
                    Text("El equipo mostrará la pantalla de turnos del departamento y anunciará cada llamado por voz.",
                        style = MaterialTheme.typography.bodySmall)
                }
                Button(onClick = vm::guardar, enabled = ui.puedeGuardar, modifier = Modifier.fillMaxWidth().height(52.dp)) {
                    Icon(Icons.Filled.Save, contentDescription = null)
                    Spacer(Modifier.size(8.dp))
                    Text("Guardar configuración", fontWeight = FontWeight.Bold)
                }
            }
        }
    }
    if (explicar) {
        AlertDialog(
            onDismissRequest = { explicar = false },
            icon = { Icon(Icons.Filled.MyLocation, contentDescription = null) },
            title = { Text("Permiso de ubicación") },
            text = { Text("La ubicación aproximada se usa solo para identificar la tienda donde está este equipo. Si no la permite, elija la tienda de la lista.") },
            confirmButton = {
                TextButton(onClick = {
                    explicar = false
                    permiso.launch(Manifest.permission.ACCESS_COARSE_LOCATION)
                }) { Text("Continuar") }
            },
            dismissButton = { TextButton(onClick = { explicar = false }) { Text("Ahora no") } },
        )
    }
}

// ---------------------------------------------------------------------- UI-22 Pantalla de turnos

/** Colores de los cuatro estados que ven los clientes (RN-14). */
private fun colorCliente(e: EstadoCliente): Color = when (e) {
    EstadoCliente.LIBRE -> Color(0xFF2E7D32)
    EstadoCliente.LLAMANDO -> Color(0xFF1565C0)
    EstadoCliente.OCUPADO -> Color(0xFFF9A825)
    EstadoCliente.NO_DISPONIBLE -> Color(0xFF757575)
}

/**
 * Pantalla de turnos del equipo del departamento. Cada llamado nuevo se anuncia
 * por voz si el anuncio está activado. [alConfigurar] recibe correo y contraseña
 * del jefe de tienda y devuelve true si son correctos.
 */
@Composable
fun PantallaTurnos(vm: TurnosViewModel, alConfigurar: suspend (String, String) -> Boolean) {
    val ui by vm.estado.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val voz = remember { AnunciadorVoz(context) }
    DisposableEffect(Unit) { onDispose { voz.cerrar() } }

    // Anuncia el llamado más reciente cuando cambia.
    var ultimoAnunciado by remember { mutableStateOf<Long?>(null) }
    LaunchedEffect(ui.llamado?.hora, ui.llamado?.numero) {
        val l = ui.llamado ?: return@LaunchedEffect
        if (ultimoAnunciado != null && ultimoAnunciado != l.hora && ui.anuncioVoz) voz.anunciar(l.numero, l.probadorCodigo)
        ultimoAnunciado = l.hora
    }

    var pedirClave by remember { mutableStateOf(false) }
    val fondo = Color(0xFF0E1A2B)
    Box(Modifier.fillMaxSize().background(fondo)) {
        Column(Modifier.fillMaxSize().padding(24.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.size(18.dp).background(Color(ui.colorArgb), RoundedCornerShape(4.dp)))
                Spacer(Modifier.width(12.dp))
                Text("Probadores ${ui.departamento}", color = Color.White, fontSize = 32.sp, fontWeight = FontWeight.ExtraBold, modifier = Modifier.weight(1f))
                Text(ui.tienda, color = Color(0xFFCFDAE8), fontSize = 18.sp)
                IconButton(onClick = { pedirClave = true }) { Icon(Icons.Filled.Settings, contentDescription = "Configurar equipo", tint = Color(0xFF5C6B7F)) }
            }
            Row(Modifier.weight(1f), horizontalArrangement = Arrangement.spacedBy(20.dp)) {
                Column(Modifier.weight(1.4f), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                    Surface(color = Color(0xFF1565C0), shape = RoundedCornerShape(24.dp), modifier = Modifier.fillMaxWidth()) {
                        AnimatedContent(
                            targetState = ui.llamado,
                            transitionSpec = { (fadeIn(tween(400)) + scaleIn(tween(400))) togetherWith fadeOut(tween(200)) },
                            label = "llamado",
                        ) { l ->
                            Column(Modifier.fillMaxWidth().padding(24.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                                Text("LLAMANDO", color = Color.White, fontSize = 20.sp, fontWeight = FontWeight.Bold)
                                if (l == null) {
                                    Text("Sin llamados", color = Color.White, fontSize = 48.sp, fontWeight = FontWeight.ExtraBold)
                                } else {
                                    Text("Número ${l.numero}", color = Color.White, fontSize = 64.sp, fontWeight = FontWeight.ExtraBold)
                                    Text("Probador ${l.probadorCodigo.removePrefix("P-").trimStart('0')}", color = Color.White, fontSize = 40.sp)
                                }
                            }
                        }
                    }
                    LazyVerticalGrid(columns = GridCells.Adaptive(120.dp), horizontalArrangement = Arrangement.spacedBy(10.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        items(ui.probadores, key = { it.codigo }) { p ->
                            Surface(color = colorCliente(p.estado), shape = RoundedCornerShape(16.dp)) {
                                Column(Modifier.padding(12.dp).fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
                                    Text(p.codigo, color = Color.White, fontSize = 24.sp, fontWeight = FontWeight.ExtraBold)
                                    Text(if (p.estado == EstadoCliente.LLAMANDO && p.numero != null) "Llamando ${p.numero}" else p.estado.texto,
                                        color = Color.White, fontWeight = FontWeight.Bold)
                                }
                            }
                        }
                    }
                }
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text("En espera", color = Color.White, fontSize = 22.sp, fontWeight = FontWeight.Bold)
                    Text(if (ui.enEspera.isEmpty()) "Sin números en espera" else ui.enEspera.joinToString("  "), color = Color.White, fontSize = 30.sp, fontWeight = FontWeight.ExtraBold)
                    Text("Espera estimada: " + (ui.esperaMin?.let { "$it min" } ?: "Sin estimación"), color = Color(0xFFCFDAE8), fontSize = 18.sp)
                    Spacer(Modifier.height(8.dp))
                    Text("Últimos llamados", color = Color.White, fontSize = 22.sp, fontWeight = FontWeight.Bold)
                    ui.ultimos.forEach { l ->
                        Text("${l.numero} → ${l.probadorCodigo}" + if (l.perdido) " (perdido)" else "", color = Color(0xFFCFDAE8), fontSize = 20.sp)
                    }
                    Spacer(Modifier.weight(1f))
                    Text("Escanee el código QR del departamento para sacar su número.", color = Color(0xFFCFDAE8), fontSize = 16.sp)
                }
            }
        }
    }
    if (pedirClave) DialogoJefeDeTienda(alCerrar = { pedirClave = false }, alVerificar = alConfigurar)
}

/** Pide las credenciales del jefe de tienda para salir del modo equipo de departamento. */
@Composable
private fun DialogoJefeDeTienda(alCerrar: () -> Unit, alVerificar: suspend (String, String) -> Boolean) {
    var correo by remember { mutableStateOf("") }
    var clave by remember { mutableStateOf("") }
    var error by remember { mutableStateOf<String?>(null) }
    val alcance = rememberCoroutineScope()
    AlertDialog(
        onDismissRequest = alCerrar,
        title = { Text("Configurar equipo") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("Solo el jefe de tienda puede cambiar la configuración del equipo.")
                CampoFormulario(correo, { correo = it; error = null }, "Correo", Icons.Filled.Email, null, teclado = KeyboardType.Email)
                CampoFormulario(clave, { clave = it; error = null }, "Contraseña", Icons.Filled.Lock, error,
                    teclado = KeyboardType.Password, transformacion = PasswordVisualTransformation())
            }
        },
        confirmButton = {
            TextButton(onClick = {
                alcance.launch {
                    if (alVerificar(correo, clave)) alCerrar() else error = "Credenciales de jefe de tienda incorrectas"
                }
            }, enabled = correo.isNotBlank() && clave.isNotEmpty()) { Text("Continuar") }
        },
        dismissButton = { TextButton(onClick = alCerrar) { Text("Cancelar") } },
    )
}

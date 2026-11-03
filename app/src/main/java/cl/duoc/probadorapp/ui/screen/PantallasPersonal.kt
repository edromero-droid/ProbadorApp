@file:OptIn(ExperimentalMaterial3Api::class)

package cl.duoc.probadorapp.ui.screen

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
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Notes
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.Badge
import androidx.compose.material.icons.filled.CalendarToday
import androidx.compose.material.icons.filled.Category
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Groups
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.SwapHoriz
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import cl.duoc.probadorapp.domain.model.EstadoSolicitud
import cl.duoc.probadorapp.ui.components.BarraSuperior
import cl.duoc.probadorapp.ui.components.CampoFormulario
import cl.duoc.probadorapp.ui.components.GrupoOpciones
import cl.duoc.probadorapp.ui.components.MostrarMensaje
import cl.duoc.probadorapp.ui.components.SelectorDepartamento
import cl.duoc.probadorapp.ui.viewmodel.HistorialViewModel
import cl.duoc.probadorapp.ui.viewmodel.JefaturaViewModel
import cl.duoc.probadorapp.ui.viewmodel.MoverPersonaViewModel
import cl.duoc.probadorapp.ui.viewmodel.PersonaUi
import cl.duoc.probadorapp.ui.viewmodel.PersonalViewModel
import cl.duoc.probadorapp.ui.viewmodel.ResponderSolicitudViewModel
import cl.duoc.probadorapp.ui.viewmodel.SolicitudUi
import cl.duoc.probadorapp.ui.viewmodel.SolicitudesViewModel

/** Columna centrada y desplazable para formularios, con ancho máximo en tablet. */
@Composable
private fun ColumnaFormulario(relleno: androidx.compose.foundation.layout.PaddingValues, contenido: @Composable () -> Unit) {
    Box(Modifier.fillMaxSize().padding(relleno), contentAlignment = Alignment.TopCenter) {
        Column(
            Modifier.widthIn(max = 640.dp).fillMaxWidth().verticalScroll(rememberScrollState()).padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) { contenido() }
    }
}

// ---------------------------------------------------------------------- UI-13 Personal y cobertura

@Composable
fun PantallaPersonal(
    vm: PersonalViewModel,
    alMover: (String) -> Unit,
    alVerHistorial: (String) -> Unit,
    alCambiarJefatura: () -> Unit,
    alSolicitudes: () -> Unit,
) {
    val ui by vm.estado.collectAsStateWithLifecycle()
    Scaffold(topBar = { BarraSuperior("Personal y cobertura") }) { relleno ->
        LazyColumn(
            Modifier.fillMaxSize().padding(relleno).padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            item { SelectorDepartamento(ui.departamentos, ui.departamentoId, ui.puedeElegirDepartamento, vm::elegirDepartamento) }
            item {
                val cumple = ui.cobertura >= ui.minimo
                val (fondo, texto) = if (cumple) Color(0xFFE8F5E9) to Color(0xFF1B5E20) else Color(0xFFFFEBEE) to Color(0xFFB71C1C)
                Surface(color = fondo, shape = RoundedCornerShape(16.dp), modifier = Modifier.fillMaxWidth()) {
                    Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                        Icon(if (cumple) Icons.Filled.Groups else Icons.Filled.Warning, contentDescription = null, tint = texto, modifier = Modifier.size(32.dp))
                        Spacer(Modifier.width(12.dp))
                        Column(Modifier.weight(1f)) {
                            Text("Cobertura: ${ui.cobertura} de mínimo ${ui.minimo}", color = texto, fontWeight = FontWeight.ExtraBold, fontSize = 18.sp)
                            Text(if (cumple) "Cumple el mínimo de vendedores en turno" else "Bajo el mínimo de vendedores en turno", color = texto)
                            Text("Jefe: ${ui.jefe}", color = texto, style = MaterialTheme.typography.bodySmall)
                        }
                    }
                }
            }
            item {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    AssistChip(onClick = alSolicitudes, label = { Text("Solicitudes de refuerzo") }, leadingIcon = { Icon(Icons.AutoMirrored.Filled.Send, null) })
                    if (ui.esJefeDeTienda) {
                        AssistChip(onClick = alCambiarJefatura, label = { Text("Cambiar jefatura") }, leadingIcon = { Icon(Icons.Filled.Badge, null) })
                    }
                }
            }
            items(ui.personas, key = { it.uid }) { p -> TarjetaPersona(p, { alMover(p.uid) }, { alVerHistorial(p.uid) }, Modifier.animateItem()) }
            item { Spacer(Modifier.height(16.dp)) }
        }
    }
}

@Composable
private fun TarjetaPersona(p: PersonaUi, alMover: () -> Unit, alHistorial: () -> Unit, modifier: Modifier) {
    Card(modifier.fillMaxWidth()) {
        Row(Modifier.padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(
                Modifier.size(12.dp).background(if (p.enTurno) Color(0xFF2E7D32) else Color(0xFF9E9E9E), CircleShape),
            )
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(p.nombre, fontWeight = FontWeight.Bold)
                Text("${p.rol} · ${p.detalle}", style = MaterialTheme.typography.bodySmall)
            }
            TextButton(onClick = alHistorial) { Icon(Icons.Filled.History, contentDescription = "Ver historial de ${p.nombre}") }
            if (p.puedeMover) {
                OutlinedButton(onClick = alMover) {
                    Icon(Icons.Filled.SwapHoriz, contentDescription = null)
                    Spacer(Modifier.size(6.dp))
                    Text("Mover")
                }
            }
        }
    }
}

// ---------------------------------------------------------------------- UI-14 Mover persona

@Composable
fun PantallaMoverPersona(vm: MoverPersonaViewModel, alVolver: () -> Unit) {
    val ui by vm.estado.collectAsStateWithLifecycle()
    val mensaje by vm.mensaje.collectAsStateWithLifecycle()
    val avisos = remember { SnackbarHostState() }
    var confirmar by remember { mutableStateOf(false) }
    MostrarMensaje(mensaje, avisos, vm::mensajeMostrado)
    LaunchedEffect(ui.terminado) { if (ui.terminado) alVolver() }
    Scaffold(topBar = { BarraSuperior("Mover persona", alVolver) }, snackbarHost = { SnackbarHost(avisos) }) { relleno ->
        ColumnaFormulario(relleno) {
            CampoFormulario(
                "${ui.nombre} · ${ui.actual}" + if (ui.enTurno) " · En turno" else " · Fuera de turno",
                {}, "Persona", Icons.Filled.Person, null, habilitado = false,
            )
            GrupoOpciones(
                "Departamento de destino", Icons.Filled.Category,
                ui.departamentos.map { it.id to it.nombre }, ui.destino, vm::elegirDestino, ui.error,
                deshabilitadas = setOfNotNull(ui.actualId),
            )
            ui.advertencia?.let {
                Surface(color = Color(0xFFFFF3D6), shape = RoundedCornerShape(12.dp)) {
                    Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Filled.Warning, contentDescription = null, tint = Color(0xFF7A4F01))
                        Spacer(Modifier.size(8.dp))
                        Text(it, color = Color(0xFF7A4F01))
                    }
                }
            }
            Button(onClick = { confirmar = true }, enabled = ui.puedeEnviar, modifier = Modifier.fillMaxWidth().height(52.dp)) {
                if (ui.guardando) CircularProgressIndicator(Modifier.size(22.dp), strokeWidth = 2.dp) else Text("Mover", fontWeight = FontWeight.Bold)
            }
        }
    }
    if (confirmar) {
        val destino = ui.departamentos.firstOrNull { it.id == ui.destino }?.nombre ?: ""
        AlertDialog(
            onDismissRequest = { confirmar = false },
            title = { Text("Mover persona") },
            text = { Text("¿Mover a ${ui.nombre} de ${ui.actual} a $destino?" + (ui.advertencia?.let { " $it" } ?: "")) },
            confirmButton = { TextButton(onClick = { confirmar = false; vm.mover() }) { Text("Mover") } },
            dismissButton = { TextButton(onClick = { confirmar = false }) { Text("Cancelar") } },
        )
    }
}

// ---------------------------------------------------------------------- UI-15 Solicitudes de refuerzo

@Composable
fun PantallaSolicitudes(vm: SolicitudesViewModel, alResponder: (String) -> Unit) {
    val ui by vm.estado.collectAsStateWithLifecycle()
    val mensaje by vm.mensaje.collectAsStateWithLifecycle()
    val avisos = remember { SnackbarHostState() }
    MostrarMensaje(mensaje, avisos, vm::mensajeMostrado)
    Scaffold(topBar = { BarraSuperior("Solicitudes de refuerzo") }, snackbarHost = { SnackbarHost(avisos) }) { relleno ->
        LazyColumn(
            Modifier.fillMaxSize().padding(relleno).padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            if (ui.puedeSolicitar) {
                item {
                    Card {
                        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                            Text("Pedir un vendedor", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                            GrupoOpciones("Departamento al que se pide", Icons.Filled.Category,
                                ui.opciones.map { it.id to it.nombre }, ui.pedido, vm::elegir, ui.errorPedido)
                            Button(onClick = vm::enviar, enabled = ui.puedeEnviar, modifier = Modifier.fillMaxWidth().height(48.dp)) {
                                Icon(Icons.AutoMirrored.Filled.Send, contentDescription = null)
                                Spacer(Modifier.size(8.dp))
                                Text("Enviar solicitud")
                            }
                        }
                    }
                }
            }
            item { Text("Recibidas", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold) }
            if (ui.recibidas.isEmpty()) item { Text("No hay solicitudes recibidas.") }
            items(ui.recibidas, key = { "r" + it.id }) { s -> TarjetaSolicitud(s, { alResponder(s.id) }, null) }
            if (ui.puedeSolicitar) {
                item { Text("Enviadas", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold) }
                if (ui.enviadas.isEmpty()) item { Text("No ha enviado solicitudes.") }
                items(ui.enviadas, key = { "e" + it.id }) { s -> TarjetaSolicitud(s, null, { vm.cancelar(s.id) }) }
            }
            item { Spacer(Modifier.height(16.dp)) }
        }
    }
}

@Composable
private fun TarjetaSolicitud(s: SolicitudUi, alResponder: (() -> Unit)?, alCancelar: (() -> Unit)?) {
    val color = when (s.estado) {
        EstadoSolicitud.PENDIENTE -> Color(0xFF1565C0)
        EstadoSolicitud.ESCALADA -> Color(0xFFC62828)
        EstadoSolicitud.ACEPTADA -> Color(0xFF2E7D32)
        EstadoSolicitud.RECHAZADA, EstadoSolicitud.CANCELADA -> Color(0xFF546E7A)
    }
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(s.titulo, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
                Surface(color = color, shape = RoundedCornerShape(10.dp)) {
                    Text(s.estado.texto, color = Color.White, fontSize = 12.sp, fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp))
                }
            }
            Text(s.detalle, style = MaterialTheme.typography.bodySmall)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                if (alResponder != null && s.puedeResponder) Button(onClick = alResponder) { Text("Responder") }
                if (alCancelar != null && s.puedeCancelar) OutlinedButton(onClick = alCancelar) { Text("Cancelar solicitud") }
            }
        }
    }
}

// ---------------------------------------------------------------------- F-08 Responder solicitud

@Composable
fun PantallaResponderSolicitud(vm: ResponderSolicitudViewModel, alVolver: () -> Unit) {
    val ui by vm.estado.collectAsStateWithLifecycle()
    val mensaje by vm.mensaje.collectAsStateWithLifecycle()
    val avisos = remember { SnackbarHostState() }
    MostrarMensaje(mensaje, avisos, vm::mensajeMostrado)
    LaunchedEffect(ui.terminado) { if (ui.terminado) alVolver() }
    Scaffold(topBar = { BarraSuperior("Responder solicitud", alVolver) }, snackbarHost = { SnackbarHost(avisos) }) { relleno ->
        ColumnaFormulario(relleno) {
            Text(ui.titulo, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
            if (!ui.abierta) Text("Esta solicitud ya fue respondida.", color = MaterialTheme.colorScheme.error)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                FilterChip(selected = ui.aceptar == true, onClick = { vm.elegirRespuesta(true) }, label = { Text("Aceptar") },
                    leadingIcon = { Icon(Icons.Filled.Check, null) })
                FilterChip(selected = ui.aceptar == false, onClick = { vm.elegirRespuesta(false) }, label = { Text("Rechazar") },
                    leadingIcon = { Icon(Icons.Filled.Close, null) })
            }
            when (ui.aceptar) {
                true -> GrupoOpciones("Vendedor que se cede", Icons.Filled.Person, ui.vendedores, ui.vendedor, vm::elegirVendedor, ui.errorVendedor)
                false -> CampoFormulario(ui.motivo, vm::alCambiarMotivo, "Motivo", Icons.AutoMirrored.Filled.Notes, ui.errorMotivo, lineas = 2, apoyo = ui.contador)
                null -> Text("Elija si acepta o rechaza la solicitud.", style = MaterialTheme.typography.bodyMedium)
            }
            Button(onClick = vm::enviar, enabled = ui.puedeEnviar, modifier = Modifier.fillMaxWidth().height(52.dp)) {
                Text(if (ui.aceptar == false) "Rechazar solicitud" else "Aceptar y ceder", fontWeight = FontWeight.Bold)
            }
        }
    }
}

// ---------------------------------------------------------------------- UI-16 Cambio de jefatura

@Composable
fun PantallaJefatura(vm: JefaturaViewModel, alVolver: () -> Unit) {
    val ui by vm.estado.collectAsStateWithLifecycle()
    val mensaje by vm.mensaje.collectAsStateWithLifecycle()
    val avisos = remember { SnackbarHostState() }
    var confirmar by remember { mutableStateOf(false) }
    MostrarMensaje(mensaje, avisos, vm::mensajeMostrado)
    LaunchedEffect(ui.terminado) { if (ui.terminado) alVolver() }
    Scaffold(topBar = { BarraSuperior("Cambio de jefatura", alVolver) }, snackbarHost = { SnackbarHost(avisos) }) { relleno ->
        ColumnaFormulario(relleno) {
            Selector("Departamento", ui.departamentos, ui.departamento, vm::elegirDepartamento, Icons.Filled.Category, null)
            Selector("Nuevo jefe", ui.jefes, ui.nuevoJefe, vm::elegirNuevo, Icons.Filled.Badge, null)
            ui.aviso?.let {
                Surface(color = Color(0xFFE3F2FD), shape = RoundedCornerShape(12.dp)) {
                    Text(it, color = Color(0xFF0D47A1), fontWeight = FontWeight.SemiBold, modifier = Modifier.padding(12.dp))
                }
            }
            ui.vacantes.forEach { v ->
                Selector("Jefe de ${v.departamento}", v.opciones, v.eleccion, { vm.elegirVacante(v.departamentoId, it) }, Icons.Filled.Badge, v.error)
            }
            HorizontalDivider()
            Text("Resumen", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            ui.resumen.forEach { (dep, antes, despues) ->
                Row(Modifier.fillMaxWidth()) {
                    Text(dep, fontWeight = FontWeight.Bold, modifier = Modifier.width(90.dp))
                    Text(antes, Modifier.weight(1f), style = MaterialTheme.typography.bodySmall)
                    Text("→", Modifier.padding(horizontal = 6.dp))
                    Text(despues, Modifier.weight(1f), fontWeight = if (antes != despues) FontWeight.Bold else FontWeight.Normal)
                }
            }
            Button(onClick = { confirmar = true }, enabled = ui.puedeConfirmar, modifier = Modifier.fillMaxWidth().height(52.dp)) {
                Text("Confirmar cambio", fontWeight = FontWeight.Bold)
            }
        }
    }
    if (confirmar) {
        AlertDialog(
            onDismissRequest = { confirmar = false },
            title = { Text("Cambio de jefatura") },
            text = { Text(ui.resumen.filter { it.second != it.third }.joinToString("\n") { "${it.first}: ${it.second} → ${it.third}" }) },
            confirmButton = { TextButton(onClick = { confirmar = false; vm.confirmar() }) { Text("Confirmar") } },
            dismissButton = { TextButton(onClick = { confirmar = false }) { Text("Cancelar") } },
        )
    }
}

// ---------------------------------------------------------------------- UI-17 Historial del personal

@Composable
fun PantallaHistorial(vm: HistorialViewModel, alVolver: (() -> Unit)?) {
    val ui by vm.estado.collectAsStateWithLifecycle()
    var elegirFecha by remember { mutableStateOf(false) }
    Scaffold(topBar = { BarraSuperior("Historial del personal", alVolver) }) { relleno ->
        Column(Modifier.fillMaxSize().padding(relleno).padding(horizontal = 16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.Top) {
                Column(Modifier.weight(1f)) {
                    OutlinedButton(onClick = { elegirFecha = true }, modifier = Modifier.fillMaxWidth().height(56.dp)) {
                        Icon(Icons.Filled.CalendarToday, contentDescription = null)
                        Spacer(Modifier.size(8.dp))
                        Text("Fecha: ${ui.fecha}")
                    }
                    ui.errorFecha?.let { Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall) }
                }
                Selector(
                    "Persona", listOf("" to "Todas") + ui.personas, ui.persona ?: "",
                    { vm.elegirPersona(it.ifEmpty { null }) }, Icons.Filled.Person, null, Modifier.weight(1f),
                )
            }
            if (ui.cargando) {
                Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
            } else if (ui.entradas.isEmpty() && ui.errorFecha == null) {
                Text("No hay registros para esta fecha.")
            }
            LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                items(ui.entradas) { h ->
                    Card(Modifier.fillMaxWidth()) {
                        Row(Modifier.padding(12.dp)) {
                            Text(h.hora, fontWeight = FontWeight.Bold, modifier = Modifier.width(56.dp))
                            Column {
                                Text(h.titulo, fontWeight = FontWeight.SemiBold)
                                if (h.detalle.isNotEmpty()) Text(h.detalle, style = MaterialTheme.typography.bodySmall)
                            }
                        }
                    }
                }
                item { Spacer(Modifier.height(16.dp)) }
            }
        }
    }
    if (elegirFecha) {
        val estado = rememberDatePickerState()
        DatePickerDialog(
            onDismissRequest = { elegirFecha = false },
            confirmButton = {
                TextButton(onClick = {
                    estado.selectedDateMillis?.let { vm.elegirFechaMs(it) }
                    elegirFecha = false
                }) { Text("Aceptar") }
            },
            dismissButton = { TextButton(onClick = { elegirFecha = false }) { Text("Cancelar") } },
        ) {
            DatePicker(state = estado)
        }
    }
}

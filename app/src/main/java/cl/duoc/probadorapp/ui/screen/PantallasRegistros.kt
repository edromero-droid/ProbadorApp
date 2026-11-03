@file:OptIn(ExperimentalMaterial3Api::class)

package cl.duoc.probadorapp.ui.screen

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
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Category
import androidx.compose.material.icons.filled.Checkroom
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.Groups
import androidx.compose.material.icons.filled.Inventory2
import androidx.compose.material.icons.filled.Link
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.SensorsOff
import androidx.compose.material.icons.filled.SwapHoriz
import androidx.compose.material.icons.filled.Timer
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import cl.duoc.probadorapp.domain.model.TipoAlerta
import cl.duoc.probadorapp.domain.model.TipoObjeto
import cl.duoc.probadorapp.ui.components.BarraSuperior
import cl.duoc.probadorapp.ui.components.CampoFormulario
import cl.duoc.probadorapp.ui.components.FotoGuardada
import cl.duoc.probadorapp.ui.components.MostrarMensaje
import cl.duoc.probadorapp.ui.components.SeccionFoto
import cl.duoc.probadorapp.ui.components.SelectorDepartamento
import cl.duoc.probadorapp.ui.viewmodel.AlertaPersonalUi
import cl.duoc.probadorapp.ui.viewmodel.AlertasViewModel
import cl.duoc.probadorapp.ui.viewmodel.ObjetoUi
import cl.duoc.probadorapp.ui.viewmodel.ObjetosViewModel
import cl.duoc.probadorapp.ui.viewmodel.RegistrarObjetoViewModel

// ---------------------------------------------------------------------- UI-08 Alertas

@Composable
fun PantallaAlertas(vm: AlertasViewModel, alAbrir: (AlertaPersonalUi) -> Unit) {
    val alertas by vm.estado.collectAsStateWithLifecycle()
    Scaffold(topBar = { BarraSuperior("Alertas") }) { relleno ->
        if (alertas.isEmpty()) {
            Box(Modifier.fillMaxSize().padding(relleno), contentAlignment = Alignment.Center) { Text("No tiene alertas activas.") }
            return@Scaffold
        }
        LazyColumn(
            Modifier.fillMaxSize().padding(relleno).padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            items(alertas, key = { it.id }) { a ->
                val (icono, color) = when (a.tipo) {
                    TipoAlerta.TIEMPO_EXCEDIDO -> Icons.Filled.Timer to Color(0xFFC62828)
                    TipoAlerta.PRENDA_OLVIDADA -> Icons.Filled.Checkroom to Color(0xFF546E7A)
                    TipoAlerta.SIN_SENAL -> Icons.Filled.SensorsOff to Color(0xFF757575)
                    TipoAlerta.COBERTURA_BAJA -> Icons.Filled.Groups to Color(0xFF00695C)
                    TipoAlerta.SOLICITUD_REFUERZO -> Icons.Filled.SwapHoriz to Color(0xFF1565C0)
                }
                Card(
                    onClick = { alAbrir(a) },
                    modifier = Modifier.fillMaxWidth().animateItem(),
                    colors = CardDefaults.cardColors(
                        containerColor = if (a.vista) MaterialTheme.colorScheme.surface else MaterialTheme.colorScheme.primaryContainer,
                    ),
                ) {
                    Row(Modifier.padding(14.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        Icon(icono, contentDescription = a.tipo.texto, tint = color)
                        Column(Modifier.weight(1f)) {
                            Text(a.titulo, fontWeight = FontWeight.Bold)
                            Text(a.detalle, style = MaterialTheme.typography.bodySmall)
                            Text(a.hora, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.outline)
                        }
                        if (!a.vista) TextButton(onClick = { vm.marcarVista(a.id) }) { Text("Vista") }
                    }
                }
            }
            item { Spacer(Modifier.height(16.dp)) }
        }
    }
}

// ---------------------------------------------------------------------- UI-11 Objetos encontrados

@Composable
fun PantallaObjetos(vm: ObjetosViewModel, alRegistrar: () -> Unit) {
    val ui by vm.estado.collectAsStateWithLifecycle()
    val mensaje by vm.mensaje.collectAsStateWithLifecycle()
    val avisos = remember { SnackbarHostState() }
    var pestana by rememberSaveable { mutableStateOf(0) }
    var accion by remember { mutableStateOf<Pair<ObjetoUi, Boolean>?>(null) }
    MostrarMensaje(mensaje, avisos, vm::mensajeMostrado)
    Scaffold(
        topBar = { BarraSuperior("Objetos encontrados") },
        snackbarHost = { SnackbarHost(avisos) },
        floatingActionButton = {
            ExtendedFloatingActionButton(onClick = alRegistrar, icon = { Icon(Icons.Filled.Add, null) }, text = { Text("Registrar objeto") })
        },
    ) { relleno ->
        Column(Modifier.fillMaxSize().padding(relleno)) {
            Column(Modifier.padding(horizontal = 16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                SelectorDepartamento(ui.departamentos, ui.departamentoId, ui.puedeElegirDepartamento, vm::elegirDepartamento)
                CampoFormulario(ui.busqueda, vm::buscar, "Buscar por fecha, probador, número o descripción", Icons.Filled.Search, null)
            }
            TabRow(selectedTabIndex = pestana) {
                Tab(selected = pestana == 0, onClick = { pestana = 0 }, text = { Text("Guardados (${ui.guardados.size})") })
                Tab(selected = pestana == 1, onClick = { pestana = 1 }, text = { Text("Entregados y derivados (${ui.cerrados.size})") })
            }
            val lista = if (pestana == 0) ui.guardados else ui.cerrados
            if (lista.isEmpty()) {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { Text("No hay objetos.") }
            } else {
                LazyColumn(Modifier.fillMaxSize().padding(horizontal = 16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    item { Spacer(Modifier.height(4.dp)) }
                    items(lista, key = { it.id }) { o ->
                        Card(Modifier.fillMaxWidth().animateItem()) {
                            Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Icon(Icons.Filled.Inventory2, contentDescription = null)
                                    Spacer(Modifier.width(8.dp))
                                    Text(o.titulo, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
                                    Text(o.estado.texto, fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.primary)
                                }
                                Text(o.descripcion)
                                Text(o.detalle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.outline)
                                o.fotoUri?.let { FotoGuardada(it, alto = 140.dp) }
                                if (pestana == 0) {
                                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                        Button(onClick = { accion = o to true }) { Text("Entregar") }
                                        if (ui.puedeDerivar) OutlinedButton(onClick = { accion = o to false }) { Text("Derivar") }
                                    }
                                }
                            }
                        }
                    }
                    item { Spacer(Modifier.height(88.dp)) }
                }
            }
        }
    }
    accion?.let { (o, entregar) ->
        val tipo = o.titulo.substringBefore(" ·")
        AlertDialog(
            onDismissRequest = { accion = null },
            title = { Text(if (entregar) "Entregar objeto" else "Derivar objeto") },
            text = {
                Text(
                    if (entregar) "¿Entregar $tipo? Confirme que el cliente describió el objeto o indicó su número y la hora."
                    else "¿Derivar $tipo a objetos perdidos de la tienda?",
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    if (entregar) vm.entregar(o.id) else vm.derivar(o.id)
                    accion = null
                }) { Text(if (entregar) "Entregar" else "Derivar") }
            },
            dismissButton = { TextButton(onClick = { accion = null }) { Text("Cancelar") } },
        )
    }
}

// ---------------------------------------------------------------------- UI-12 Registrar objeto encontrado

@Composable
fun PantallaRegistrarObjeto(vm: RegistrarObjetoViewModel, alVolver: () -> Unit, alRegistrar: () -> Unit) {
    val ui by vm.estado.collectAsStateWithLifecycle()
    val mensaje by vm.mensaje.collectAsStateWithLifecycle()
    val avisos = remember { SnackbarHostState() }
    MostrarMensaje(mensaje, avisos, vm::mensajeMostrado)
    LaunchedEffect(ui.form.registrado) { if (ui.form.registrado) alRegistrar() }
    Scaffold(topBar = { BarraSuperior("Registrar objeto encontrado", alVolver) }, snackbarHost = { SnackbarHost(avisos) }) { relleno ->
        Box(Modifier.fillMaxSize().padding(relleno), contentAlignment = Alignment.TopCenter) {
            Column(
                Modifier.widthIn(max = 640.dp).fillMaxWidth().verticalScroll(rememberScrollState()).padding(20.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp),
            ) {
                if (ui.probadorFijo) {
                    CampoFormulario(ui.probadorTexto, {}, "Probador", Icons.Filled.Checkroom, null, habilitado = false)
                } else {
                    Selector("Probador", ui.probadores, ui.form.probadorId, vm::elegirProbador, Icons.Filled.Checkroom, null)
                }
                if (ui.asociacion.isNotEmpty()) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Filled.Link, contentDescription = null, tint = MaterialTheme.colorScheme.outline)
                        Spacer(Modifier.size(8.dp))
                        Text(ui.asociacion, style = MaterialTheme.typography.bodySmall)
                    }
                }
                Selector(
                    "Tipo de objeto", TipoObjeto.entries.map { it.name to it.texto }, ui.form.tipo?.name,
                    { vm.elegirTipo(TipoObjeto.valueOf(it)) }, Icons.Filled.Category, null,
                )
                CampoFormulario(ui.form.descripcion, vm::alCambiarDescripcion, "Descripción", Icons.Filled.Description,
                    ui.errorDescripcion, lineas = 3, apoyo = ui.contador)
                SeccionFoto(ui.form.fotoUri, vm::alCambiarFoto, "Sin permiso de cámara. Puede registrar el objeto sin foto.")
                Button(onClick = vm::enviar, enabled = ui.puedeEnviar, modifier = Modifier.fillMaxWidth().height(52.dp)) {
                    if (ui.form.guardando) CircularProgressIndicator(Modifier.size(22.dp), strokeWidth = 2.dp)
                    else Text("Registrar objeto", fontWeight = FontWeight.Bold)
                }
                Text("No se guardan datos del cliente que retira el objeto.", style = MaterialTheme.typography.bodySmall,
                    modifier = Modifier.align(Alignment.CenterHorizontally))
            }
        }
    }
}

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
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Category
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Checkroom
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.GridView
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.ReportProblem
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
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
import cl.duoc.probadorapp.domain.model.TipoIncidencia
import cl.duoc.probadorapp.ui.components.BotonMenu
import cl.duoc.probadorapp.ui.components.CampoFormulario
import cl.duoc.probadorapp.ui.components.FilaDato
import cl.duoc.probadorapp.ui.components.FotoGuardada
import cl.duoc.probadorapp.ui.components.SeccionFoto
import cl.duoc.probadorapp.ui.components.SelectorDepartamento
import cl.duoc.probadorapp.ui.viewmodel.IncidenciaUi
import cl.duoc.probadorapp.ui.viewmodel.IncidenciasViewModel
import cl.duoc.probadorapp.ui.viewmodel.ReportarIncidenciaViewModel
import cl.duoc.probadorapp.ui.viewmodel.ResumenIncidenciaViewModel

// ---------------------------------------------------------------------- UI-09 Reportar incidencia

/**
 * Formulario F-04. Al registrar, entrega el id de la incidencia a [alRegistrar]
 * para que la navegación abra el resumen con ese dato (guía 11).
 */
@Composable
fun PantallaReportarIncidencia(vm: ReportarIncidenciaViewModel, alVolver: () -> Unit, alRegistrar: (String) -> Unit) {
    val ui by vm.estado.collectAsStateWithLifecycle()
    LaunchedEffect(ui.form.creadaId) {
        ui.form.creadaId?.let {
            vm.navegado()
            alRegistrar(it)
        }
    }
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Reportar incidencia", fontWeight = FontWeight.ExtraBold) },
                navigationIcon = { IconButton(onClick = alVolver) { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Volver") } },
            )
        },
    ) { relleno ->
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
                Selector(
                    "Tipo de incidencia",
                    TipoIncidencia.entries.map { it.name to it.texto },
                    ui.form.tipo?.name,
                    { vm.elegirTipo(TipoIncidencia.valueOf(it)) },
                    Icons.Filled.Category,
                    null,
                )
                CampoFormulario(
                    ui.form.descripcion, vm::alCambiarDescripcion, "Descripción", Icons.Filled.Description,
                    ui.errorDescripcion, lineas = 3, apoyo = ui.contador,
                )
                SeccionFoto(
                    fotoUri = ui.form.fotoUri,
                    alCambiarFoto = vm::alCambiarFoto,
                    mensajeSinPermiso = "Sin permiso de cámara. Puede registrar la incidencia sin foto.",
                )
                Button(onClick = vm::enviar, enabled = ui.puedeEnviar, modifier = Modifier.fillMaxWidth().height(52.dp)) {
                    if (ui.form.guardando) {
                        CircularProgressIndicator(Modifier.size(22.dp), strokeWidth = 2.dp)
                    } else {
                        Icon(Icons.Filled.ReportProblem, contentDescription = null)
                        Spacer(Modifier.size(8.dp))
                        Text("Registrar incidencia", fontWeight = FontWeight.Bold)
                    }
                }
                Text("El botón se habilita cuando el probador, el tipo y la descripción son válidos.",
                    style = MaterialTheme.typography.bodySmall, modifier = Modifier.align(Alignment.CenterHorizontally))
            }
        }
    }
}

// ---------------------------------------------------------------------- UI-10 Resumen de incidencia

@Composable
fun PantallaResumenIncidencia(vm: ResumenIncidenciaViewModel, alVolver: () -> Unit, alVerIncidencias: () -> Unit, alIrAlPanel: () -> Unit) {
    val ui by vm.estado.collectAsStateWithLifecycle()
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Resumen de incidencia", fontWeight = FontWeight.ExtraBold) },
                navigationIcon = { IconButton(onClick = alVolver) { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Volver") } },
            )
        },
    ) { relleno ->
        if (!ui.existe) {
            Box(Modifier.fillMaxSize().padding(relleno), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
            return@Scaffold
        }
        Box(Modifier.fillMaxSize().padding(relleno), contentAlignment = Alignment.TopCenter) {
            Column(
                Modifier.widthIn(max = 640.dp).fillMaxWidth().verticalScroll(rememberScrollState()).padding(20.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp),
            ) {
                Surface(color = if (ui.abierta) Color(0xFFECEFF1) else Color(0xFFE8F5E9), shape = MaterialTheme.shapes.large) {
                    Row(Modifier.fillMaxWidth().padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            if (ui.abierta) Icons.Filled.ReportProblem else Icons.Filled.CheckCircle,
                            contentDescription = null,
                            tint = if (ui.abierta) Color(0xFF546E7A) else Color(0xFF2E7D32),
                            modifier = Modifier.size(32.dp),
                        )
                        Spacer(Modifier.width(12.dp))
                        Column {
                            Text(ui.titulo, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.ExtraBold)
                            Text(ui.efecto)
                        }
                    }
                }
                Card {
                    Column(Modifier.padding(horizontal = 16.dp, vertical = 8.dp)) {
                        ui.datos.forEach { (k, v) -> FilaDato(k, v) }
                    }
                }
                Card {
                    Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Text("Descripción", fontWeight = FontWeight.Bold)
                        Text(ui.descripcion)
                    }
                }
                ui.fotoUri?.let { FotoGuardada(it, alto = 260.dp) }
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    OutlinedButton(onClick = alVerIncidencias, modifier = Modifier.weight(1f).height(52.dp)) { Text("Ver incidencias") }
                    Button(onClick = alIrAlPanel, modifier = Modifier.weight(1f).height(52.dp)) {
                        Icon(Icons.Filled.GridView, contentDescription = null)
                        Spacer(Modifier.size(8.dp))
                        Text("Volver al panel")
                    }
                }
            }
        }
    }
}

// ---------------------------------------------------------------------- UI-11 Incidencias

@Composable
fun PantallaIncidencias(vm: IncidenciasViewModel, alReportar: () -> Unit, alAbrir: (String) -> Unit) {
    val ui by vm.estado.collectAsStateWithLifecycle()
    var pestana by rememberSaveable { mutableStateOf(0) }
    var porCerrar by remember { mutableStateOf<IncidenciaUi?>(null) }
    Scaffold(
        topBar = { TopAppBar(title = { Text("Incidencias", fontWeight = FontWeight.ExtraBold) }, navigationIcon = { BotonMenu() }) },
        floatingActionButton = {
            ExtendedFloatingActionButton(
                onClick = alReportar,
                icon = { Icon(Icons.Filled.Add, contentDescription = null) },
                text = { Text("Reportar incidencia") },
            )
        },
    ) { relleno ->
        Column(Modifier.fillMaxSize().padding(relleno)) {
            Box(Modifier.padding(horizontal = 16.dp)) {
                SelectorDepartamento(ui.departamentos, ui.departamentoId, ui.puedeElegirDepartamento, vm::elegirDepartamento)
            }
            TabRow(selectedTabIndex = pestana) {
                Tab(selected = pestana == 0, onClick = { pestana = 0 }, text = { Text("Abiertas (${ui.abiertas.size})") })
                Tab(selected = pestana == 1, onClick = { pestana = 1 }, text = { Text("Cerradas (${ui.cerradas.size})") })
            }
            val lista = if (pestana == 0) ui.abiertas else ui.cerradas
            if (lista.isEmpty()) {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text(if (pestana == 0) "No hay incidencias abiertas." else "No hay incidencias cerradas.")
                }
            } else {
                LazyColumn(
                    Modifier.fillMaxSize().padding(horizontal = 16.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    item { Spacer(Modifier.height(4.dp)) }
                    items(lista, key = { it.id }) { i ->
                        TarjetaIncidencia(i, alAbrir = { alAbrir(i.id) }, alCerrar = { porCerrar = i }, modifier = Modifier.animateItem())
                    }
                    item { Spacer(Modifier.height(88.dp)) }
                }
            }
        }
    }
    porCerrar?.let { i ->
        AlertDialog(
            onDismissRequest = { porCerrar = null },
            title = { Text("Cerrar incidencia") },
            text = { Text("¿Cerrar la incidencia del probador ${i.codigo}?") },
            confirmButton = { TextButton(onClick = { vm.cerrar(i.id); porCerrar = null }) { Text("Cerrar") } },
            dismissButton = { TextButton(onClick = { porCerrar = null }) { Text("Cancelar") } },
        )
    }
}

@Composable
private fun TarjetaIncidencia(i: IncidenciaUi, alAbrir: () -> Unit, alCerrar: () -> Unit, modifier: Modifier = Modifier) {
    Card(onClick = alAbrir, modifier = modifier.fillMaxWidth()) {
        Row(Modifier.padding(14.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Icon(
                if (i.abierta) Icons.Filled.ReportProblem else Icons.Filled.CheckCircle,
                contentDescription = if (i.abierta) "Abierta" else "Cerrada",
                tint = if (i.abierta) Color(0xFF546E7A) else Color(0xFF2E7D32),
            )
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(i.titulo, fontWeight = FontWeight.Bold)
                Text(i.descripcion, maxLines = 2)
                Row(verticalAlignment = Alignment.CenterVertically) {
                    if (i.fotoUri != null) {
                        Icon(Icons.Filled.Image, contentDescription = "Con fotografía", modifier = Modifier.size(14.dp),
                            tint = MaterialTheme.colorScheme.outline)
                        Spacer(Modifier.width(4.dp))
                    }
                    Text(i.detalle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.outline)
                }
            }
            if (i.abierta) {
                TextButton(onClick = alCerrar) { Text("Cerrar") }
            }
        }
    }
}

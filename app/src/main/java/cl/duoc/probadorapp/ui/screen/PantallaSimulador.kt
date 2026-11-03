@file:OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)

package cl.duoc.probadorapp.ui.screen

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Category
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Checkroom
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.RestartAlt
import androidx.compose.material.icons.filled.Sensors
import androidx.compose.material.icons.filled.Tag
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.MenuAnchorType
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import cl.duoc.probadorapp.ui.components.BotonMenu
import cl.duoc.probadorapp.ui.components.CampoFormulario
import cl.duoc.probadorapp.ui.viewmodel.OpcionSenal
import cl.duoc.probadorapp.ui.viewmodel.SimuladorUi
import cl.duoc.probadorapp.ui.viewmodel.SimuladorViewModel

/** Menú desplegable de solo lectura con ícono y mensaje de error. */
@Composable
fun Selector(
    etiqueta: String,
    opciones: List<Pair<String, String>>,
    seleccion: String?,
    alElegir: (String) -> Unit,
    icono: ImageVector,
    error: String?,
    modifier: Modifier = Modifier,
) {
    var abierto by remember { mutableStateOf(false) }
    Column(modifier) {
        ExposedDropdownMenuBox(expanded = abierto, onExpandedChange = { abierto = it }) {
            OutlinedTextField(
                value = opciones.firstOrNull { it.first == seleccion }?.second ?: "",
                onValueChange = {},
                readOnly = true,
                label = { Text(etiqueta) },
                leadingIcon = { Icon(icono, contentDescription = null) },
                trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = abierto) },
                isError = error != null,
                modifier = Modifier.menuAnchor(MenuAnchorType.PrimaryNotEditable).fillMaxWidth(),
            )
            ExposedDropdownMenu(expanded = abierto, onDismissRequest = { abierto = false }) {
                opciones.forEach { (id, texto) ->
                    DropdownMenuItem(text = { Text(texto) }, onClick = { alElegir(id); abierto = false })
                }
            }
        }
        if (error != null) {
            Text(error, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall,
                modifier = Modifier.padding(start = 16.dp, top = 4.dp))
        }
    }
}

/** UI-21 Simulador. */
@Composable
fun PantallaSimulador(vm: SimuladorViewModel) {
    val ui by vm.estado.collectAsStateWithLifecycle()
    val mensaje by vm.mensaje.collectAsStateWithLifecycle()
    val avisos = remember { SnackbarHostState() }
    var reiniciar by remember { mutableStateOf(false) }
    LaunchedEffect(mensaje) {
        mensaje?.let {
            avisos.showSnackbar(it)
            vm.mensajeMostrado()
        }
    }
    Scaffold(
        topBar = { TopAppBar(title = { Text("Simulador", fontWeight = FontWeight.ExtraBold) }, navigationIcon = { BotonMenu() }) },
        snackbarHost = { SnackbarHost(avisos) },
    ) { relleno ->
        BoxWithConstraints(Modifier.fillMaxSize().padding(relleno)) {
            if (maxWidth >= 840.dp) {
                Row(Modifier.fillMaxSize().padding(20.dp), horizontalArrangement = Arrangement.spacedBy(20.dp)) {
                    FormularioSenal(ui, vm, Modifier.weight(1.1f))
                    ColumnaAutomatico(ui, vm, { reiniciar = true }, Modifier.weight(1f))
                }
            } else {
                Column(Modifier.fillMaxSize().padding(16.dp).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                    FormularioSenal(ui, vm, Modifier.fillMaxWidth())
                    ColumnaAutomatico(ui, vm, { reiniciar = true }, Modifier.fillMaxWidth())
                }
            }
        }
    }
    if (reiniciar) {
        AlertDialog(
            onDismissRequest = { reiniciar = false },
            title = { Text("Reiniciar demostración") },
            text = { Text("¿Borrar los números, ocupaciones, alertas, incidencias y objetos de hoy?") },
            confirmButton = { TextButton(onClick = { vm.reiniciar(); reiniciar = false }) { Text("Reiniciar") } },
            dismissButton = { TextButton(onClick = { reiniciar = false }) { Text("Cancelar") } },
        )
    }
}

@Composable
private fun FormularioSenal(ui: SimuladorUi, vm: SimuladorViewModel, modifier: Modifier) {
    Card(modifier) {
        Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
            Text("Enviar una señal", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Selector("Departamento", ui.departamentos.map { it.id to it.nombre }, ui.form.departamentoId,
                    vm::elegirDepartamento, Icons.Filled.Category, null, Modifier.weight(1f))
                Selector("Probador", ui.probadores.map { it.id to it.codigo }, ui.form.probadorId,
                    vm::elegirProbador, Icons.Filled.Checkroom, if (ui.form.senal != null) ui.errorProbador else null, Modifier.weight(1f))
            }
            Text("Señal", fontWeight = FontWeight.SemiBold)
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                OpcionSenal.entries.forEach { op ->
                    val elegida = ui.form.senal == op
                    val icono: (@Composable () -> Unit)? = if (elegida) {
                        { Icon(Icons.Filled.Check, contentDescription = null) }
                    } else {
                        null
                    }
                    FilterChip(
                        selected = elegida,
                        onClick = { vm.elegirSenal(op) },
                        label = { Text(op.texto) },
                        leadingIcon = icono,
                    )
                }
            }
            if (ui.form.senal == OpcionSenal.PRENDAS_DETECTADAS) {
                CampoFormulario(ui.form.cantidad, vm::alCambiarCantidad, "Cantidad de prendas", Icons.Filled.Tag,
                    ui.errorCantidad, teclado = KeyboardType.Number)
            }
            Spacer(Modifier.height(4.dp))
            Button(onClick = vm::enviar, enabled = ui.puedeEnviar, modifier = Modifier.fillMaxWidth().height(52.dp)) {
                Icon(Icons.Filled.Sensors, contentDescription = null)
                Spacer(Modifier.size(8.dp))
                Text("Enviar señal", fontWeight = FontWeight.Bold)
            }
            Text("El botón se habilita cuando no hay errores", style = MaterialTheme.typography.bodySmall,
                modifier = Modifier.align(Alignment.CenterHorizontally))
        }
    }
}

@Composable
private fun ColumnaAutomatico(ui: SimuladorUi, vm: SimuladorViewModel, alReiniciar: () -> Unit, modifier: Modifier) {
    Column(modifier, verticalArrangement = Arrangement.spacedBy(14.dp)) {
        Card {
            Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Filled.PlayArrow, contentDescription = null)
                    Spacer(Modifier.size(8.dp))
                    Text("Modo automático", fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
                    Switch(checked = ui.modoAutomatico, onCheckedChange = vm::cambiarModoAutomatico)
                }
                Text("Genera números, entradas, salidas y prendas olvidadas al azar, sin intervención.",
                    style = MaterialTheme.typography.bodyMedium)
                OutlinedButton(onClick = alReiniciar, modifier = Modifier.fillMaxWidth()) {
                    Icon(Icons.Filled.RestartAlt, contentDescription = null)
                    Spacer(Modifier.size(8.dp))
                    Text("Reiniciar demostración")
                }
            }
        }
        Card {
            Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text("Últimas señales", fontWeight = FontWeight.Bold)
                if (ui.ultimas.isEmpty()) Text("Todavía no se envían señales.", style = MaterialTheme.typography.bodySmall)
                ui.ultimas.forEach { s ->
                    Column(Modifier.padding(vertical = 4.dp)) {
                        Text(s.titulo, fontWeight = FontWeight.SemiBold)
                        Text(s.detalle, style = MaterialTheme.typography.bodySmall)
                    }
                    HorizontalDivider()
                }
            }
        }
    }
}

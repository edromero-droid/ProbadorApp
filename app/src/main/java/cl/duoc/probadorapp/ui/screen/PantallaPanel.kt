@file:OptIn(ExperimentalMaterial3Api::class)

package cl.duoc.probadorapp.ui.screen

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ConfirmationNumber
import androidx.compose.material.icons.filled.Groups
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.SensorsOff
import androidx.compose.material.icons.filled.Timer
import androidx.compose.material.icons.filled.Checkroom
import androidx.compose.material.icons.filled.SwapHoriz
import androidx.compose.material3.Badge
import androidx.compose.material3.BadgedBox
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import cl.duoc.probadorapp.domain.model.TipoAlerta
import cl.duoc.probadorapp.ui.components.BotonMenu
import cl.duoc.probadorapp.ui.components.SelectorDepartamento
import cl.duoc.probadorapp.ui.components.TarjetaProbador
import cl.duoc.probadorapp.ui.theme.estiloDe
import cl.duoc.probadorapp.ui.viewmodel.AlertaUi
import cl.duoc.probadorapp.ui.viewmodel.PanelUi
import cl.duoc.probadorapp.ui.viewmodel.PanelViewModel

/** UI-04 Panel de probadores. */
@Composable
fun PantallaPanel(vm: PanelViewModel, alAbrirProbador: (String) -> Unit, alNumeroSinCelular: (String) -> Unit) {
    val ui by vm.estado.collectAsStateWithLifecycle()
    var verAlertas by rememberSaveable { mutableStateOf(false) }
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Probadores", fontWeight = FontWeight.ExtraBold) },
                navigationIcon = { BotonMenu() },
                actions = {
                    Surface(shape = RoundedCornerShape(16.dp), color = MaterialTheme.colorScheme.primaryContainer) {
                        Text("Sucursal Norte", Modifier.padding(horizontal = 12.dp, vertical = 6.dp), fontWeight = FontWeight.SemiBold)
                    }
                    Spacer(Modifier.size(12.dp))
                    // En el celular, la campana muestra u oculta la lista de alertas.
                    IconButton(onClick = { verAlertas = !verAlertas }) {
                        BadgedBox(badge = { if (ui.alertas.isNotEmpty()) Badge { Text(ui.alertas.size.toString()) } }) {
                            Icon(Icons.Filled.Notifications, contentDescription = "${ui.alertas.size} alertas activas")
                        }
                    }
                    Spacer(Modifier.size(8.dp))
                },
            )
        },
    ) { relleno ->
        BoxWithConstraints(Modifier.fillMaxSize().padding(relleno)) {
            if (maxWidth >= 840.dp) {
                Row(Modifier.fillMaxSize().padding(16.dp), horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                    ZonaProbadores(ui, vm, alAbrirProbador, alNumeroSinCelular, Modifier.weight(1f))
                    ListaAlertas(ui.alertas, vm::marcarVista, alAbrirProbador, Modifier.width(300.dp).fillMaxHeight())
                }
            } else {
                // Pantalla angosta: las alertas se despliegan con una animación bajo la barra.
                Column(Modifier.fillMaxSize().padding(12.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    AnimatedVisibility(visible = verAlertas) {
                        ListaAlertas(ui.alertas, vm::marcarVista, alAbrirProbador, Modifier.fillMaxWidth().height(240.dp))
                    }
                    ZonaProbadores(ui, vm, alAbrirProbador, alNumeroSinCelular, Modifier.weight(1f))
                }
            }
        }
    }
}

@Composable
private fun ZonaProbadores(
    ui: PanelUi,
    vm: PanelViewModel,
    alAbrirProbador: (String) -> Unit,
    alNumeroSinCelular: (String) -> Unit,
    modifier: Modifier,
) {
    Column(modifier, verticalArrangement = Arrangement.spacedBy(12.dp)) {
        // Departamentos: el jefe de tienda elige; los demás ven el suyo (RN-03 y RN-26).
        SelectorDepartamento(ui.departamentos, ui.departamentoId, ui.puedeElegirDepartamento, vm::elegirDepartamento)
        // Resumen por estado
        Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            ui.resumen.forEach { (estado, n) ->
                val e = estiloDe(estado)
                Surface(color = e.fondo, shape = RoundedCornerShape(14.dp)) {
                    Row(Modifier.padding(horizontal = 10.dp, vertical = 5.dp), verticalAlignment = Alignment.CenterVertically) {
                        Box(Modifier.size(9.dp).background(e.color, CircleShape))
                        Spacer(Modifier.size(6.dp))
                        Text("$n ${estado.texto}", color = e.texto, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
                    }
                }
            }
        }
        LazyVerticalGrid(
            columns = GridCells.Adaptive(150.dp),
            modifier = Modifier.weight(1f),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            items(ui.tarjetas, key = { it.id }) { t -> TarjetaProbador(t, alTocar = { alAbrirProbador(t.id) }) }
        }
        // Cola del departamento y entrega de números sin celular (RF-07 y RF-12).
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
            Card(Modifier.weight(1f)) {
                Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Filled.ConfirmationNumber, contentDescription = null)
                        Spacer(Modifier.size(8.dp))
                        val cola = if (ui.enEspera.isEmpty()) "sin números en espera" else "${ui.enEspera.size} en espera (${ui.enEspera.joinToString(", ")})"
                        Text("Cola: $cola", fontWeight = FontWeight.Bold)
                    }
                    ui.llamando?.let { Text("Llamando: $it", color = Color(0xFF0D47A1), fontWeight = FontWeight.Bold) }
                    Text("Espera estimada: " + (ui.esperaMin?.let { "$it min" } ?: "Sin estimación"), style = MaterialTheme.typography.bodySmall)
                }
            }
            Button(onClick = { alNumeroSinCelular(ui.departamentoId) }, modifier = Modifier.height(84.dp), shape = RoundedCornerShape(18.dp)) {
                Icon(Icons.Filled.Add, contentDescription = null, modifier = Modifier.size(28.dp))
                Spacer(Modifier.size(8.dp))
                Text("Número\nsin celular", fontWeight = FontWeight.ExtraBold, fontSize = 16.sp)
            }
        }
    }
}

private fun iconoAlerta(t: TipoAlerta): Pair<ImageVector, Color> = when (t) {
    TipoAlerta.TIEMPO_EXCEDIDO -> Icons.Filled.Timer to Color(0xFFC62828)
    TipoAlerta.PRENDA_OLVIDADA -> Icons.Filled.Checkroom to Color(0xFF546E7A)
    TipoAlerta.SIN_SENAL -> Icons.Filled.SensorsOff to Color(0xFF757575)
    TipoAlerta.COBERTURA_BAJA -> Icons.Filled.Groups to Color(0xFF00695C)
    TipoAlerta.SOLICITUD_REFUERZO -> Icons.Filled.SwapHoriz to Color(0xFF1565C0)
}

@Composable
private fun ListaAlertas(alertas: List<AlertaUi>, marcarVista: (String) -> Unit, abrir: (String) -> Unit, modifier: Modifier) {
    Column(modifier, verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text("Alertas activas (${alertas.size})", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
        if (alertas.isEmpty()) {
            Text("No hay alertas activas.", style = MaterialTheme.typography.bodyMedium)
        }
        LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            items(alertas, key = { it.id }) { a ->
                val (icono, color) = iconoAlerta(a.tipo)
                Card(
                    onClick = { a.probadorId?.let(abrir) },
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                ) {
                    Row(Modifier.padding(12.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        Icon(icono, contentDescription = null, tint = color)
                        Column(Modifier.weight(1f)) {
                            Text(a.titulo, fontWeight = FontWeight.Bold, fontSize = 14.sp)
                            Text(a.detalle, fontSize = 13.sp)
                            Text(a.hora, fontSize = 12.sp, color = MaterialTheme.colorScheme.outline)
                        }
                        TextButton(onClick = { marcarVista(a.id) }) { Text("Vista") }
                    }
                }
            }
        }
    }
}

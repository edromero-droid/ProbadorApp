@file:OptIn(ExperimentalMaterial3Api::class)

package cl.duoc.probadorapp.ui.screen

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.scaleIn
import androidx.compose.animation.togetherWith
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.LocalOffer
import androidx.compose.material.icons.filled.ReportProblem
import androidx.compose.material.icons.filled.Inventory2
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
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import cl.duoc.probadorapp.ui.components.FilaDato
import cl.duoc.probadorapp.ui.theme.estiloDe
import cl.duoc.probadorapp.ui.viewmodel.DetalleUi
import cl.duoc.probadorapp.ui.viewmodel.DetalleViewModel
import cl.duoc.probadorapp.ui.viewmodel.NumeroSinCelularViewModel

/** UI-05 Detalle de probador. */
@Composable
fun PantallaDetalle(vm: DetalleViewModel, alVolver: () -> Unit, alReportar: () -> Unit, alRegistrarObjeto: () -> Unit) {
    val ui by vm.estado.collectAsStateWithLifecycle()
    var confirmar by remember { mutableStateOf(false) }
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Probador ${ui.codigo}" + if (ui.departamento.isNotEmpty()) " · ${ui.departamento}" else "", fontWeight = FontWeight.ExtraBold) },
                navigationIcon = { IconButton(onClick = alVolver) { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Volver") } },
            )
        },
    ) { relleno ->
        if (!ui.existe) {
            Box(Modifier.fillMaxSize().padding(relleno), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
            return@Scaffold
        }
        BoxWithConstraints(Modifier.fillMaxSize().padding(relleno)) {
            val ancho = maxWidth >= 840.dp
            val izquierda: @Composable (Modifier) -> Unit = { m -> ColumnaEstado(ui, m, alReportar, alRegistrarObjeto) { confirmar = true } }
            val derecha: @Composable (Modifier) -> Unit = { m -> ColumnaPrendas(ui, m) }
            if (ancho) {
                Row(Modifier.fillMaxSize().padding(20.dp), horizontalArrangement = Arrangement.spacedBy(20.dp)) {
                    izquierda(Modifier.weight(1f))
                    derecha(Modifier.weight(1f))
                }
            } else {
                Column(Modifier.fillMaxSize().padding(16.dp).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                    izquierda(Modifier.fillMaxWidth())
                    derecha(Modifier.fillMaxWidth())
                }
            }
        }
    }
    if (confirmar) {
        AlertDialog(
            onDismissRequest = { confirmar = false },
            title = { Text("Confirmar revisión") },
            text = { Text("¿Confirma que el probador está vacío y en condiciones de uso?") },
            confirmButton = { TextButton(onClick = { vm.confirmarRevision(); confirmar = false }) { Text("Confirmar") } },
            dismissButton = { TextButton(onClick = { confirmar = false }) { Text("Cancelar") } },
        )
    }
}

@Composable
private fun ColumnaEstado(ui: DetalleUi, modifier: Modifier, alReportar: () -> Unit, alRegistrarObjeto: () -> Unit, alConfirmar: () -> Unit) {
    val e = estiloDe(ui.estado)
    Column(modifier, verticalArrangement = Arrangement.spacedBy(14.dp)) {
        Surface(color = e.fondo, shape = RoundedCornerShape(18.dp), border = BorderStroke(2.dp, e.color)) {
            Row(Modifier.fillMaxWidth().padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                Icon(e.icono, contentDescription = null, tint = e.color, modifier = Modifier.size(36.dp))
                Spacer(Modifier.size(12.dp))
                Column {
                    Text(ui.estado.texto, fontSize = 22.sp, fontWeight = FontWeight.ExtraBold, color = e.texto)
                    Text(ui.motivo, color = e.texto)
                }
            }
        }
        Card {
            Column(Modifier.padding(horizontal = 16.dp, vertical = 8.dp)) {
                ui.datos.forEach { (k, v) -> FilaDato(k, v) }
            }
        }
        Button(onClick = alConfirmar, enabled = ui.puedeConfirmar, modifier = Modifier.fillMaxWidth().height(52.dp)) {
            Icon(Icons.Filled.Check, contentDescription = null)
            Spacer(Modifier.size(8.dp))
            Text("Confirmar revisión", fontWeight = FontWeight.Bold)
        }
        if (!ui.puedeConfirmar) {
            Text("La revisión se confirma solo cuando el probador está En revisión.", style = MaterialTheme.typography.bodySmall)
        }
        // Abre el formulario de incidencia con este probador ya elegido (UI-05 a UI-09).
        OutlinedButton(onClick = alReportar, modifier = Modifier.fillMaxWidth().height(52.dp)) {
            Icon(Icons.Filled.ReportProblem, contentDescription = null)
            Spacer(Modifier.size(8.dp))
            Text("Reportar incidencia", fontWeight = FontWeight.Bold)
        }
        // Objeto personal de un cliente (UI-05 a UI-12).
        OutlinedButton(onClick = alRegistrarObjeto, modifier = Modifier.fillMaxWidth().height(52.dp)) {
            Icon(Icons.Filled.Inventory2, contentDescription = null)
            Spacer(Modifier.size(8.dp))
            Text("Registrar objeto encontrado", fontWeight = FontWeight.Bold)
        }
    }
}

@Composable
private fun ColumnaPrendas(ui: DetalleUi, modifier: Modifier) {
    Column(modifier, verticalArrangement = Arrangement.spacedBy(14.dp)) {
        Card {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text("Prendas detectadas (${ui.prendas.size})", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                if (ui.prendas.isEmpty()) Text("El lector no detecta prendas.")
                ui.prendas.forEach { p ->
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Filled.LocalOffer, contentDescription = null)
                        Spacer(Modifier.size(10.dp))
                        Column {
                            Text(p.descripcion, fontWeight = FontWeight.Bold)
                            Text(p.detalle, style = MaterialTheme.typography.bodySmall)
                        }
                    }
                }
            }
        }
        if (ui.alertas.isNotEmpty()) {
            Card {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Filled.Notifications, contentDescription = null)
                        Spacer(Modifier.size(8.dp))
                        Text("Alertas activas", fontWeight = FontWeight.Bold)
                    }
                    ui.alertas.forEach { Text(it) }
                }
            }
        }
        Row {
            Icon(Icons.Filled.Info, contentDescription = null, tint = MaterialTheme.colorScheme.outline)
            Spacer(Modifier.size(8.dp))
            Text("Al retirar la prenda, el lector deja de detectarla y el probador queda Libre. Use \"Confirmar revisión\" si el lector sigue detectando una prenda que ya se retiró.",
                style = MaterialTheme.typography.bodySmall)
        }
    }
}

/** UI-06 Número sin celular. */
@Composable
fun PantallaNumeroSinCelular(vm: NumeroSinCelularViewModel, alTerminar: () -> Unit) {
    val ui by vm.estado.collectAsStateWithLifecycle()
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Número sin celular", fontWeight = FontWeight.ExtraBold) },
                navigationIcon = { IconButton(onClick = alTerminar) { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Volver") } },
            )
        },
    ) { relleno ->
        Box(Modifier.fillMaxSize().padding(relleno), contentAlignment = Alignment.Center) {
            Card(Modifier.widthIn(max = 560.dp).padding(24.dp)) {
                Column(Modifier.padding(32.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text("Número para el cliente", style = MaterialTheme.typography.titleLarge)
                    AnimatedContent(
                        targetState = ui.numero,
                        transitionSpec = { (fadeIn(tween(300)) + scaleIn(tween(300))) togetherWith fadeOut(tween(150)) },
                        label = "numero",
                    ) { n ->
                        if (n == null) {
                            CircularProgressIndicator(Modifier.padding(40.dp))
                        } else {
                            Text(n.toString(), fontSize = 150.sp, fontWeight = FontWeight.ExtraBold, color = MaterialTheme.colorScheme.primary)
                        }
                    }
                    val texto = when {
                        ui.cargando -> ""
                        ui.probador != null -> "Pase al probador ${ui.probador!!.removePrefix("P-").trimStart('0')}."
                        else -> "Hay ${ui.antes} números antes · Espera estimada: " + (ui.esperaMin?.let { "$it min" } ?: "Sin estimación")
                    }
                    Text(texto, fontSize = 18.sp, textAlign = TextAlign.Center)
                    Text("Anótelo en una ficha del departamento ${ui.departamento} y entréguela al cliente.",
                        textAlign = TextAlign.Center, style = MaterialTheme.typography.bodyMedium)
                    Button(onClick = alTerminar, enabled = !ui.cargando, modifier = Modifier.fillMaxWidth().height(52.dp)) {
                        Text("Listo", fontWeight = FontWeight.Bold)
                    }
                }
            }
        }
    }
}

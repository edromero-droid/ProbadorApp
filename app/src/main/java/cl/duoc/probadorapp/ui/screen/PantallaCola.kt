@file:OptIn(ExperimentalMaterial3Api::class)

package cl.duoc.probadorapp.ui.screen

import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ConfirmationNumber
import androidx.compose.material.icons.filled.QrCode2
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import cl.duoc.probadorapp.domain.model.Canal
import cl.duoc.probadorapp.domain.model.EstadoNumero
import cl.duoc.probadorapp.ui.components.BotonMenu
import cl.duoc.probadorapp.ui.components.SelectorDepartamento
import cl.duoc.probadorapp.ui.viewmodel.ColaViewModel
import cl.duoc.probadorapp.ui.viewmodel.NumeroColaUi

/** Color de fondo y de texto de cada estado de un número. */
private fun coloresNumero(estado: EstadoNumero): Pair<Color, Color> = when (estado) {
    EstadoNumero.EN_ESPERA -> Color(0xFFFFF3D6) to Color(0xFF7A4F01)
    EstadoNumero.LLAMADO -> Color(0xFFE3F2FD) to Color(0xFF0D47A1)
    EstadoNumero.ATENDIDO -> Color(0xFFE8F5E9) to Color(0xFF1B5E20)
    EstadoNumero.PERDIDO -> Color(0xFFECEFF1) to Color(0xFF37474F)
}

/** UI-07 Cola del día. */
@Composable
fun PantallaCola(vm: ColaViewModel) {
    val ui by vm.estado.collectAsStateWithLifecycle()
    Scaffold(
        topBar = { TopAppBar(title = { Text("Cola del día", fontWeight = FontWeight.ExtraBold) }, navigationIcon = { BotonMenu() }) },
    ) { relleno ->
        Column(
            Modifier.fillMaxSize().padding(relleno).padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            SelectorDepartamento(ui.departamentos, ui.departamentoId, ui.puedeElegirDepartamento, vm::elegirDepartamento)
            Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                ui.resumen.forEach { (estado, n) ->
                    val (fondo, texto) = coloresNumero(estado)
                    Surface(color = fondo, shape = RoundedCornerShape(14.dp)) {
                        Text("$n ${estado.texto}", color = texto, fontWeight = FontWeight.SemiBold, fontSize = 13.sp,
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp))
                    }
                }
            }
            if (ui.numeros.isEmpty()) {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text("Todavía no se entregan números hoy en este departamento.", style = MaterialTheme.typography.bodyLarge)
                }
            } else {
                LazyColumn(
                    Modifier.fillMaxWidth().widthIn(max = 900.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    items(ui.numeros, key = { it.id }) { n -> FilaNumero(n, Modifier.animateItem()) }
                    item { Spacer(Modifier.size(16.dp)) }
                }
            }
        }
    }
}

@Composable
private fun FilaNumero(n: NumeroColaUi, modifier: Modifier = Modifier) {
    val (fondo, texto) = coloresNumero(n.estado)
    Card(modifier.fillMaxWidth()) {
        Row(Modifier.padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(56.dp).background(fondo, CircleShape), contentAlignment = Alignment.Center) {
                Text(n.numero.toString(), fontSize = 22.sp, fontWeight = FontWeight.ExtraBold, color = texto)
            }
            Spacer(Modifier.width(14.dp))
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Surface(color = fondo, shape = RoundedCornerShape(10.dp)) {
                        Text(n.estado.texto, color = texto, fontWeight = FontWeight.Bold, fontSize = 12.sp,
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp))
                    }
                    Spacer(Modifier.width(8.dp))
                    Icon(
                        if (n.canal == Canal.QR) Icons.Filled.QrCode2 else Icons.Filled.ConfirmationNumber,
                        contentDescription = null, modifier = Modifier.size(16.dp), tint = MaterialTheme.colorScheme.outline,
                    )
                    Spacer(Modifier.width(4.dp))
                    Text("${n.canal.texto} · ${n.emitido}", style = MaterialTheme.typography.bodySmall)
                }
                Text(n.detalle, style = MaterialTheme.typography.bodyMedium)
            }
        }
    }
}

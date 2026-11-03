@file:OptIn(ExperimentalMaterial3Api::class)

package cl.duoc.probadorapp.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp

/**
 * Barra superior común. Las pantallas principales muestran el botón de menú
 * (en celular); las pantallas secundarias, la flecha para volver.
 */
@Composable
fun BarraSuperior(titulo: String, alVolver: (() -> Unit)? = null, acciones: @Composable RowScope.() -> Unit = {}) {
    TopAppBar(
        title = { Text(titulo, fontWeight = FontWeight.ExtraBold) },
        navigationIcon = {
            if (alVolver != null) {
                IconButton(onClick = alVolver) { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Volver") }
            } else {
                BotonMenu()
            }
        },
        actions = acciones,
    )
}

/** Muestra en el Snackbar el mensaje del ViewModel y avisa que ya se mostró. */
@Composable
fun MostrarMensaje(mensaje: String?, avisos: SnackbarHostState, alMostrar: () -> Unit) {
    LaunchedEffect(mensaje) {
        if (mensaje != null) {
            avisos.showSnackbar(mensaje)
            alMostrar()
        }
    }
}

/**
 * Botones de opción con ícono, título y error bajo el grupo (por ejemplo,
 * el departamento de destino de F-06).
 */
@Composable
fun GrupoOpciones(
    titulo: String,
    icono: ImageVector,
    opciones: List<Pair<String, String>>,
    elegida: String?,
    alElegir: (String) -> Unit,
    error: String?,
    deshabilitadas: Set<String> = emptySet(),
) {
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(icono, contentDescription = null)
            Spacer(Modifier.size(8.dp))
            Text(titulo, style = MaterialTheme.typography.titleSmall)
        }
        Column(Modifier.selectableGroup()) {
            opciones.forEach { (id, texto) ->
                val habilitada = id !in deshabilitadas
                Row(
                    Modifier
                        .fillMaxWidth()
                        .height(48.dp)
                        .selectable(selected = id == elegida, enabled = habilitada, role = Role.RadioButton, onClick = { alElegir(id) }),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    RadioButton(selected = id == elegida, onClick = null, enabled = habilitada)
                    Spacer(Modifier.size(8.dp))
                    Text(texto, color = if (habilitada) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.outline)
                }
            }
        }
        if (error != null) {
            Text(error, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(start = 16.dp))
        }
    }
}

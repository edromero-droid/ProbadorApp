@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)

package cl.duoc.probadorapp.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material3.Badge
import androidx.compose.material3.BadgedBox
import androidx.compose.material3.FilterChip
import androidx.compose.material3.IconButton
import androidx.compose.runtime.compositionLocalOf
import cl.duoc.probadorapp.domain.model.Departamento
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Error
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import cl.duoc.probadorapp.domain.model.EstadoVisible
import cl.duoc.probadorapp.ui.theme.estiloDe

/**
 * Campo de formulario con ícono, ícono de error y mensaje bajo el campo
 * (comportamiento común de la sección 2.5).
 */
@Composable
fun CampoFormulario(
    valor: String,
    alCambiar: (String) -> Unit,
    etiqueta: String,
    icono: ImageVector,
    error: String?,
    modifier: Modifier = Modifier,
    teclado: KeyboardType = KeyboardType.Text,
    transformacion: VisualTransformation = VisualTransformation.None,
    finalExtra: (@Composable () -> Unit)? = null,
    lineas: Int = 1,
    apoyo: String? = null,
    habilitado: Boolean = true,
) {
    val iconoFinal: (@Composable () -> Unit)? = if (error != null) {
        { Icon(Icons.Filled.Error, contentDescription = "Error", tint = MaterialTheme.colorScheme.error) }
    } else {
        finalExtra
    }
    Column(modifier) {
        OutlinedTextField(
            value = valor,
            onValueChange = alCambiar,
            label = { Text(etiqueta) },
            leadingIcon = { Icon(icono, contentDescription = null) },
            trailingIcon = iconoFinal,
            isError = error != null,
            singleLine = lineas == 1,
            minLines = lineas,
            keyboardOptions = KeyboardOptions(keyboardType = teclado),
            visualTransformation = transformacion,
            enabled = habilitado,
            modifier = Modifier.fillMaxWidth(),
        )
        AnimatedVisibility(visible = error != null) {
            Text(error ?: "", color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall,
                modifier = Modifier.padding(start = 16.dp, top = 4.dp))
        }
        if (apoyo != null) {
            Text(apoyo, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.outline,
                modifier = Modifier.align(Alignment.End).padding(end = 16.dp, top = 2.dp))
        }
    }
}

/**
 * Abre el menú lateral cuando la pantalla es angosta (celular). En pantallas
 * anchas el menú está siempre visible y este valor es null (guía 9).
 */
val LocalAbrirMenu = compositionLocalOf<(() -> Unit)?> { null }

/** Cantidad de alertas y solicitudes pendientes, para marcar el botón de menú. */
val LocalPendientesMenu = compositionLocalOf { 0 }

/**
 * Botón de menú para la barra superior; solo aparece si el menú está oculto.
 * Muestra un punto rojo cuando hay alertas o solicitudes pendientes.
 */
@Composable
fun BotonMenu() {
    val abrir = LocalAbrirMenu.current ?: return
    val pendientes = LocalPendientesMenu.current
    IconButton(onClick = abrir) {
        BadgedBox(badge = { if (pendientes > 0) Badge() }) {
            Icon(Icons.Filled.Menu, contentDescription = if (pendientes > 0) "Abrir menú, $pendientes pendientes" else "Abrir menú")
        }
    }
}

/** Chips de departamento: el jefe de tienda elige; los demás ven el suyo (RN-03). */
@Composable
fun SelectorDepartamento(
    departamentos: List<Departamento>,
    elegido: String,
    puedeElegir: Boolean,
    alElegir: (String) -> Unit,
) {
    Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        departamentos.forEach { d ->
            if (puedeElegir || d.id == elegido) {
                FilterChip(
                    selected = d.id == elegido,
                    onClick = { if (puedeElegir) alElegir(d.id) },
                    label = { Text(d.nombre, fontWeight = FontWeight.Bold) },
                    leadingIcon = { Box(Modifier.size(10.dp).background(Color(d.colorArgb), CircleShape)) },
                )
            }
        }
    }
}

/** Etiqueta del estado con color, ícono y texto. */
@Composable
fun EtiquetaEstado(estado: EstadoVisible, texto: String = estado.texto) {
    val e = estiloDe(estado)
    Surface(color = e.color, contentColor = e.sobreColor, shape = RoundedCornerShape(12.dp)) {
        Row(Modifier.padding(horizontal = 10.dp, vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(e.icono, contentDescription = null, modifier = Modifier.size(14.dp))
            Spacer(Modifier.size(6.dp))
            Text(texto, fontSize = 13.sp, fontWeight = FontWeight.Bold)
        }
    }
}

@Composable
fun TarjetaProbador(t: TarjetaUi, alTocar: () -> Unit, modifier: Modifier = Modifier) {
    val e = estiloDe(t.estado)
    val fondo by animateColorAsState(e.fondo, label = "fondo")
    val borde = when (t.estado) {
        EstadoVisible.EXCEDIDO -> BorderStroke(3.dp, e.color)
        else -> BorderStroke(1.dp, e.color.copy(alpha = 0.5f))
    }
    Card(
        modifier = modifier
            .heightIn(min = 150.dp)
            .clickable(onClick = alTocar)
            .semantics { contentDescription = "Probador ${t.codigo}, ${t.estado.texto}" },
        colors = CardDefaults.cardColors(containerColor = fondo),
        border = borde,
        shape = RoundedCornerShape(16.dp),
    ) {
        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text(t.codigo, fontSize = 22.sp, fontWeight = FontWeight.ExtraBold, color = Color(0xFF1A1C1E))
            EtiquetaEstado(t.estado)
            Spacer(Modifier.height(4.dp))
            t.principal?.let { Text(it, fontSize = 26.sp, fontWeight = FontWeight.ExtraBold, color = e.texto) }
            t.linea1?.let { Text(it, fontSize = 13.sp, color = e.texto) }
            t.linea2?.let { Text(it, fontSize = 13.sp, color = e.texto) }
            t.avance?.let { a ->
                val animado by animateFloatAsState(a, label = "avance")
                LinearProgressIndicator(progress = { animado }, modifier = Modifier.fillMaxWidth(), color = e.color)
            }
        }
    }
}

/** Fila de información con una etiqueta y su valor. */
@Composable
fun FilaDato(etiqueta: String, valor: String) {
    Row(Modifier.fillMaxWidth().padding(vertical = 6.dp), horizontalArrangement = Arrangement.SpaceBetween) {
        Text(etiqueta, style = MaterialTheme.typography.bodyMedium)
        Text(valor, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Bold)
    }
}

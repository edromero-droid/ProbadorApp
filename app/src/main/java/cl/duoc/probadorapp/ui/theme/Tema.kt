package cl.duoc.probadorapp.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Checkroom
import androidx.compose.material.icons.filled.ConfirmationNumber
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.SensorsOff
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import cl.duoc.probadorapp.domain.model.EstadoVisible

val AzulPrimario = Color(0xFF1E3A5F)

private val Claro = lightColorScheme(
    primary = AzulPrimario,
    onPrimary = Color.White,
    primaryContainer = Color(0xFFDCE6F2),
    onPrimaryContainer = AzulPrimario,
    background = Color(0xFFF5F7FA),
    surface = Color.White,
    error = Color(0xFFBA1A1A),
    outline = Color(0xFF8B929C),
    outlineVariant = Color(0xFFD9DEE5),
)

private val Oscuro = darkColorScheme(
    primary = Color(0xFFA9C7F0),
    onPrimary = Color(0xFF0E1A2B),
    primaryContainer = Color(0xFF2B4A70),
    onPrimaryContainer = Color(0xFFDCE6F2),
)

@Composable
fun ProbadorAppTheme(content: @Composable () -> Unit) {
    MaterialTheme(colorScheme = if (isSystemInDarkTheme()) Oscuro else Claro, content = content)
}

/** Color, fondo, texto e ícono de cada estado (sección 3.5). El color nunca es el único indicador. */
data class EstiloEstado(val color: Color, val fondo: Color, val texto: Color, val sobreColor: Color, val icono: ImageVector)

fun estiloDe(estado: EstadoVisible): EstiloEstado = when (estado) {
    EstadoVisible.LIBRE -> EstiloEstado(Color(0xFF2E7D32), Color(0xFFE8F5E9), Color(0xFF1B5E20), Color.White, Icons.Filled.CheckCircle)
    EstadoVisible.RESERVADO -> EstiloEstado(Color(0xFF1565C0), Color(0xFFE3F2FD), Color(0xFF0D47A1), Color.White, Icons.Filled.ConfirmationNumber)
    EstadoVisible.OCUPADO -> EstiloEstado(Color(0xFFF9A825), Color(0xFFFFF3D6), Color(0xFF7A4F01), Color.Black, Icons.Filled.Person)
    EstadoVisible.EXCEDIDO -> EstiloEstado(Color(0xFFC62828), Color(0xFFFFEBEE), Color(0xFFB71C1C), Color.White, Icons.Filled.Warning)
    EstadoVisible.EN_REVISION -> EstiloEstado(Color(0xFF546E7A), Color(0xFFECEFF1), Color(0xFF37474F), Color.White, Icons.Filled.Checkroom)
    EstadoVisible.SIN_SENAL -> EstiloEstado(Color(0xFF757575), Color(0xFFEEEEEE), Color(0xFF424242), Color.White, Icons.Filled.SensorsOff)
}

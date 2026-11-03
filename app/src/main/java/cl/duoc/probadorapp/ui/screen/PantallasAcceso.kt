@file:OptIn(ExperimentalMaterial3Api::class)

package cl.duoc.probadorapp.ui.screen

import androidx.compose.foundation.background
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
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Login
import androidx.compose.material.icons.automirrored.filled.Logout
import androidx.compose.material.icons.filled.Checkroom
import androidx.compose.material.icons.filled.Email
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
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
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import cl.duoc.probadorapp.data.repository.DatosIniciales
import cl.duoc.probadorapp.domain.rules.Marca
import cl.duoc.probadorapp.ui.components.CampoFormulario
import cl.duoc.probadorapp.ui.theme.AzulPrimario
import cl.duoc.probadorapp.ui.viewmodel.CredencialesUi
import cl.duoc.probadorapp.ui.viewmodel.CredencialesViewModel
import cl.duoc.probadorapp.ui.viewmodel.MarcaTurnoViewModel
import cl.duoc.probadorapp.ui.viewmodel.SesionViewModel

/** Campos de correo y contraseña comunes a UI-01 y UI-02. */
@Composable
private fun CamposCredenciales(e: CredencialesUi, vm: CredencialesViewModel) {
    var ver by rememberSaveable { mutableStateOf(false) }
    CampoFormulario(e.correo, vm::alCambiarCorreo, "Correo", Icons.Filled.Email, e.errorCorreo, teclado = KeyboardType.Email)
    CampoFormulario(
        e.clave, vm::alCambiarClave, "Contraseña", Icons.Filled.Lock, e.errorClave,
        teclado = KeyboardType.Password,
        transformacion = if (ver) VisualTransformation.None else PasswordVisualTransformation(),
        finalExtra = {
            IconButton(onClick = { ver = !ver }) {
                Icon(if (ver) Icons.Filled.VisibilityOff else Icons.Filled.Visibility,
                    contentDescription = if (ver) "Ocultar contraseña" else "Mostrar contraseña")
            }
        },
    )
}

@Composable
private fun Mensaje(e: CredencialesUi) {
    val texto = e.mensaje ?: return
    val (fondo, color) = if (e.mensajeEsError) Color(0xFFFFDAD6) to Color(0xFF93000A) else Color(0xFFE8F5E9) to Color(0xFF1B5E20)
    Surface(color = fondo, shape = RoundedCornerShape(12.dp), modifier = Modifier.fillMaxWidth()) {
        Text(texto, color = color, fontWeight = FontWeight.Bold, modifier = Modifier.padding(14.dp))
    }
}

/** UI-01 Iniciar sesión. */
@Composable
fun PantallaInicioSesion(vm: SesionViewModel, alIngresar: () -> Unit, alMarcarTurno: () -> Unit) {
    val e by vm.estado.collectAsStateWithLifecycle()
    LaunchedEffect(e.ingreso) {
        if (e.ingreso) {
            vm.navegado()
            alIngresar()
        }
    }
    BoxWithConstraints(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
        val ancho = maxWidth >= 840.dp
        Row(Modifier.fillMaxSize()) {
            if (ancho) {
                Column(
                    Modifier.weight(1f).fillMaxHeight().background(AzulPrimario).padding(48.dp),
                    verticalArrangement = Arrangement.SpaceBetween,
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Filled.Checkroom, contentDescription = null, tint = Color.White, modifier = Modifier.size(40.dp))
                        Spacer(Modifier.size(12.dp))
                        Text("ProbadorApp", color = Color.White, fontSize = 26.sp, fontWeight = FontWeight.ExtraBold)
                    }
                    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        Text("Tablet del personal", color = Color.White, fontSize = 44.sp, fontWeight = FontWeight.ExtraBold)
                        Text("Probadores, números, alertas y personal del departamento de quien inicia sesión.",
                            color = Color(0xFFCFDAE8), fontSize = 19.sp)
                    }
                    Text("Tienda Departamental Rincón Sur · Sucursal Norte", color = Color(0xFFCFDAE8))
                }
            }
            Box(Modifier.weight(1f).fillMaxHeight(), contentAlignment = Alignment.Center) {
                Column(
                    Modifier.widthIn(max = 460.dp).padding(24.dp).verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(16.dp),
                ) {
                    Text("Iniciar sesión", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.ExtraBold, color = MaterialTheme.colorScheme.primary)
                    CamposCredenciales(e, vm)
                    Mensaje(e)
                    Button(onClick = vm::ingresar, enabled = e.puedeEnviar, modifier = Modifier.fillMaxWidth().height(52.dp)) {
                        if (e.cargando) {
                            CircularProgressIndicator(Modifier.size(22.dp), strokeWidth = 2.dp)
                        } else {
                            Icon(Icons.AutoMirrored.Filled.Login, contentDescription = null)
                            Spacer(Modifier.size(8.dp))
                            Text("Ingresar", fontWeight = FontWeight.Bold)
                        }
                    }
                    Text("El botón se habilita cuando no hay errores en el formulario.",
                        style = MaterialTheme.typography.bodySmall, modifier = Modifier.align(Alignment.CenterHorizontally))
                    TextButton(onClick = alMarcarTurno, modifier = Modifier.align(Alignment.CenterHorizontally)) {
                        Icon(Icons.Filled.Schedule, contentDescription = null)
                        Spacer(Modifier.size(8.dp))
                        Text("Marcar entrada o salida de turno", fontWeight = FontWeight.Bold)
                    }
                    Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer)) {
                        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            Text("Usuarios de prueba", fontWeight = FontWeight.Bold)
                            Text("ana.perez@rinconsur.test (jefa de tienda)", style = MaterialTheme.typography.bodySmall)
                            Text("valentina.rojas@rinconsur.test (jefa de Mujer)", style = MaterialTheme.typography.bodySmall)
                            Text("camila.munoz@rinconsur.test (vendedora de Mujer)", style = MaterialTheme.typography.bodySmall)
                            Text("Contraseña: ${DatosIniciales.CONTRASENA_DEMO}", style = MaterialTheme.typography.bodySmall)
                        }
                    }
                }
            }
        }
    }
}

/** UI-02 Marcar turno. */
@Composable
fun PantallaMarcarTurno(vm: MarcaTurnoViewModel, alVolver: () -> Unit) {
    val e by vm.estado.collectAsStateWithLifecycle()
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Marcar turno", fontWeight = FontWeight.ExtraBold) },
                navigationIcon = { IconButton(onClick = alVolver) { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Volver") } },
            )
        },
    ) { relleno ->
        Box(Modifier.fillMaxSize().padding(relleno), contentAlignment = Alignment.Center) {
            Column(
                Modifier.widthIn(max = 520.dp).padding(24.dp).verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                Text("Registre su entrada o su salida", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.ExtraBold)
                Text("No abre el modo personal. Sirve en cualquier tablet de la tienda.", style = MaterialTheme.typography.bodyMedium)
                CamposCredenciales(e, vm)
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    Button(onClick = { vm.marcar(Marca.ENTRADA) }, enabled = e.puedeEnviar, modifier = Modifier.weight(1f).height(52.dp)) {
                        Icon(Icons.AutoMirrored.Filled.Login, contentDescription = null)
                        Spacer(Modifier.size(8.dp))
                        Text("Marcar entrada")
                    }
                    OutlinedButton(onClick = { vm.marcar(Marca.SALIDA) }, enabled = e.puedeEnviar, modifier = Modifier.weight(1f).height(52.dp)) {
                        Icon(Icons.AutoMirrored.Filled.Logout, contentDescription = null)
                        Spacer(Modifier.size(8.dp))
                        Text("Marcar salida")
                    }
                }
                Mensaje(e)
                Text("Un turno sin salida se cierra solo a las 23:59.", style = MaterialTheme.typography.bodySmall,
                    modifier = Modifier.align(Alignment.CenterHorizontally))
            }
        }
    }
}

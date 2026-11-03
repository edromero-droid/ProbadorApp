@file:OptIn(ExperimentalMaterial3Api::class)

package cl.duoc.probadorapp.ui.screen

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
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
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Logout
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Groups
import androidx.compose.material.icons.filled.HourglassEmpty
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.LockClock
import androidx.compose.material.icons.filled.MyLocation
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.PhotoCamera
import androidx.compose.material.icons.filled.RecordVoiceOver
import androidx.compose.material.icons.filled.Save
import androidx.compose.material.icons.filled.Timer
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import cl.duoc.probadorapp.domain.model.Usuario
import cl.duoc.probadorapp.ui.components.BotonMenu
import cl.duoc.probadorapp.ui.components.CampoFormulario
import cl.duoc.probadorapp.ui.viewmodel.AjustesViewModel
import cl.duoc.probadorapp.ui.viewmodel.DesbloqueoViewModel

// ---------------------------------------------------------------------- UI-20 Ajustes

@Composable
fun PantallaAjustes(vm: AjustesViewModel) {
    val ui by vm.estado.collectAsStateWithLifecycle()
    val context = LocalContext.current
    Scaffold(
        topBar = { TopAppBar(title = { Text("Ajustes", fontWeight = FontWeight.ExtraBold) }, navigationIcon = { BotonMenu() }) },
    ) { relleno ->
        Box(Modifier.fillMaxSize().padding(relleno), contentAlignment = Alignment.TopCenter) {
            Column(
                Modifier.widthIn(max = 640.dp).fillMaxWidth().verticalScroll(rememberScrollState()).padding(20.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp),
            ) {
                Text("Operación", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                if (!ui.editaParametros) {
                    Text("Solo el jefe de tienda modifica estos parámetros.", style = MaterialTheme.typography.bodySmall)
                }
                CampoFormulario(ui.form.tiempoMaximo, vm::alCambiarTiempo, "Tiempo máximo de uso (minutos)", Icons.Filled.Timer,
                    ui.errorTiempo, teclado = KeyboardType.Number, habilitado = ui.editaParametros, apoyo = "Entre 1 y 60")
                CampoFormulario(ui.form.plazoLlamado, vm::alCambiarPlazo, "Plazo del llamado (minutos)", Icons.Filled.HourglassEmpty,
                    ui.errorPlazo, teclado = KeyboardType.Number, habilitado = ui.editaParametros, apoyo = "Entre 1 y 10")
                CampoFormulario(ui.form.bloqueo, vm::alCambiarBloqueo, "Bloqueo por inactividad (minutos)", Icons.Filled.LockClock,
                    ui.errorBloqueo, teclado = KeyboardType.Number, habilitado = ui.editaParametros, apoyo = "Entre 1 y 15")
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Filled.RecordVoiceOver, contentDescription = null)
                    Spacer(Modifier.size(12.dp))
                    Column(Modifier.weight(1f)) {
                        Text("Anuncio por voz", fontWeight = FontWeight.SemiBold)
                        Text("Lo usa el equipo del departamento al llamar un número.", style = MaterialTheme.typography.bodySmall)
                    }
                    Switch(checked = ui.form.anuncioVoz, onCheckedChange = vm::alCambiarVoz, enabled = ui.editaParametros)
                }
                HorizontalDivider()
                Text("Mínimo de personal", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                ui.departamentos.forEach { d ->
                    CampoFormulario(
                        ui.form.minimos[d.id] ?: "", { vm.alCambiarMinimo(d.id, it) }, "Mínimo de ${d.nombre}", Icons.Filled.Groups,
                        ui.errorMinimos[d.id], teclado = KeyboardType.Number, apoyo = "Entre 0 y 10",
                    )
                }
                HorizontalDivider()
                Text("Permisos", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                // Estado de los permisos de los recursos nativos (RF-37).
                val permisos = buildList {
                    add(Triple(Icons.Filled.PhotoCamera, "Cámara", Manifest.permission.CAMERA))
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                        add(Triple(Icons.Filled.Notifications, "Notificaciones", Manifest.permission.POST_NOTIFICATIONS))
                    }
                    add(Triple(Icons.Filled.MyLocation, "Ubicación aproximada", Manifest.permission.ACCESS_COARSE_LOCATION))
                }
                permisos.forEach { (icono, nombre, permiso) ->
                    val concedido = ContextCompat.checkSelfPermission(context, permiso) == PackageManager.PERMISSION_GRANTED
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(icono, contentDescription = null)
                        Spacer(Modifier.size(12.dp))
                        Text(nombre, Modifier.weight(1f))
                        Text(if (concedido) "Concedido" else "No concedido", fontWeight = FontWeight.Bold,
                            color = if (concedido) Color(0xFF2E7D32) else MaterialTheme.colorScheme.outline)
                    }
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Filled.RecordVoiceOver, contentDescription = null)
                    Spacer(Modifier.size(12.dp))
                    Text("Texto a voz", Modifier.weight(1f))
                    Text("No requiere permiso", fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.outline)
                }
                AnimatedVisibility(
                    visible = ui.guardado,
                    enter = fadeIn() + slideInVertically(),
                    exit = fadeOut() + slideOutVertically(),
                ) {
                    Surface(color = Color(0xFFE8F5E9), shape = RoundedCornerShape(12.dp), modifier = Modifier.fillMaxWidth()) {
                        Row(Modifier.padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Filled.CheckCircle, contentDescription = null, tint = Color(0xFF2E7D32))
                            Spacer(Modifier.size(8.dp))
                            Text("Ajustes guardados en el equipo.", color = Color(0xFF1B5E20), fontWeight = FontWeight.Bold)
                        }
                    }
                }
                Button(onClick = vm::guardarAjustes, enabled = ui.puedeGuardar && !ui.guardando, modifier = Modifier.fillMaxWidth().height(52.dp)) {
                    if (ui.guardando) {
                        CircularProgressIndicator(Modifier.size(22.dp), strokeWidth = 2.dp)
                    } else {
                        Icon(Icons.Filled.Save, contentDescription = null)
                        Spacer(Modifier.size(8.dp))
                        Text("Guardar ajustes", fontWeight = FontWeight.Bold)
                    }
                }
            }
        }
    }
}

// ---------------------------------------------------------------------- Bloqueo por inactividad (RF-02)

/**
 * Cubre la pantalla mientras la tablet está bloqueada. Lo que estaba debajo,
 * incluidos los formularios a medio llenar, se conserva.
 */
@Composable
fun PantallaBloqueo(usuario: Usuario, vm: DesbloqueoViewModel) {
    val ui by vm.estado.collectAsStateWithLifecycle()
    var ver by rememberSaveable { mutableStateOf(false) }
    BackHandler { }
    Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
        Box(contentAlignment = Alignment.Center) {
            Card(Modifier.widthIn(max = 440.dp).padding(24.dp)) {
                Column(Modifier.padding(28.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(14.dp)) {
                    Box(Modifier.size(64.dp).background(MaterialTheme.colorScheme.primary, CircleShape), contentAlignment = Alignment.Center) {
                        Icon(Icons.Filled.Lock, contentDescription = null, tint = MaterialTheme.colorScheme.onPrimary, modifier = Modifier.size(32.dp))
                    }
                    Text("Tablet bloqueada", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.ExtraBold)
                    Text("${usuario.nombre} · ${usuario.correo}", style = MaterialTheme.typography.bodyMedium)
                    OutlinedTextField(
                        value = ui.clave,
                        onValueChange = vm::alCambiarClave,
                        label = { Text("Contraseña") },
                        leadingIcon = { Icon(Icons.Filled.Lock, contentDescription = null) },
                        trailingIcon = {
                            TextButton(onClick = { ver = !ver }) { Text(if (ver) "Ocultar" else "Mostrar") }
                        },
                        isError = ui.error != null,
                        supportingText = { ui.error?.let { Text(it) } },
                        singleLine = true,
                        visualTransformation = if (ver) androidx.compose.ui.text.input.VisualTransformation.None else PasswordVisualTransformation(),
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                        modifier = Modifier.fillMaxWidth(),
                    )
                    Button(onClick = vm::desbloquear, enabled = ui.clave.isNotEmpty(), modifier = Modifier.fillMaxWidth().height(52.dp)) {
                        Text("Desbloquear", fontWeight = FontWeight.Bold)
                    }
                    TextButton(onClick = vm::cerrarSesion) {
                        Icon(Icons.AutoMirrored.Filled.Logout, contentDescription = null)
                        Spacer(Modifier.size(8.dp))
                        Text("Cerrar sesión para que entre otra persona")
                    }
                }
            }
        }
    }
}

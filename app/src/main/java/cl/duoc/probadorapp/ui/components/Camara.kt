package cl.duoc.probadorapp.ui.components

import android.Manifest
import android.app.Activity
import android.content.ActivityNotFoundException
import android.content.Context
import android.content.ContextWrapper
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.Crossfade
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.PhotoCamera
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.core.content.FileProvider
import coil.compose.AsyncImage
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/** Crea el archivo donde la cámara guarda la foto, en la carpeta privada de la aplicación. */
private fun crearArchivoFoto(context: Context): File {
    val carpeta = File(context.filesDir, "fotos").apply { if (!exists()) mkdirs() }
    val nombre = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(Date())
    return File(carpeta, "IMG_$nombre.jpg")
}

/** Uri que la aplicación de cámara puede escribir, entregada por el FileProvider. */
private fun uriDe(context: Context, archivo: File): Uri =
    FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", archivo)

private tailrec fun Context.buscarActividad(): Activity? = when (this) {
    is Activity -> this
    is ContextWrapper -> baseContext.buscarActividad()
    else -> null
}

/** Foto guardada, recortada al tamaño indicado. */
@Composable
fun FotoGuardada(uri: String, alto: Dp, modifier: Modifier = Modifier) {
    AsyncImage(
        model = uri,
        contentDescription = "Fotografía de evidencia",
        contentScale = ContentScale.Crop,
        modifier = modifier.fillMaxWidth().height(alto).clip(RoundedCornerShape(12.dp)),
    )
}

/**
 * Campo de fotografía de F-04 y F-05 (guía 13: recursos del teléfono).
 * Sigue el flujo de permisos de la sección 2.6: explica el uso, pide el permiso,
 * sigue sin foto si se deniega y ofrece abrir la configuración si se denegó para siempre.
 */
@Composable
fun SeccionFoto(
    fotoUri: String?,
    alCambiarFoto: (String?) -> Unit,
    mensajeSinPermiso: String,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    var pendiente by rememberSaveable { mutableStateOf<String?>(null) }
    var explicar by remember { mutableStateOf(false) }
    var aviso by rememberSaveable { mutableStateOf<String?>(null) }
    var denegadoParaSiempre by rememberSaveable { mutableStateOf(false) }

    val camara = rememberLauncherForActivityResult(ActivityResultContracts.TakePicture()) { ok ->
        val ruta = pendiente
        if (ok && ruta != null) {
            alCambiarFoto(uriDe(context, File(ruta)).toString())
        } else if (ruta != null) {
            File(ruta).delete()
        }
        pendiente = null
    }

    fun abrirCamara() {
        val archivo = crearArchivoFoto(context)
        pendiente = archivo.absolutePath
        try {
            camara.launch(uriDe(context, archivo))
        } catch (e: ActivityNotFoundException) {
            pendiente = null
            aviso = "No hay una aplicación de cámara disponible."
        }
    }

    val permiso = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { concedido ->
        if (concedido) {
            aviso = null
            abrirCamara()
        } else {
            aviso = mensajeSinPermiso
            val actividad = context.buscarActividad()
            denegadoParaSiempre = actividad != null &&
                !ActivityCompat.shouldShowRequestPermissionRationale(actividad, Manifest.permission.CAMERA)
        }
    }

    Column(modifier, verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Text("Fotografía (opcional)", style = MaterialTheme.typography.titleSmall)
        Crossfade(targetState = fotoUri, label = "foto") { uri ->
            if (uri == null) {
                Box(
                    Modifier.fillMaxWidth().height(140.dp)
                        .background(MaterialTheme.colorScheme.surfaceVariant, RoundedCornerShape(12.dp)),
                    contentAlignment = Alignment.Center,
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Icon(Icons.Filled.PhotoCamera, contentDescription = null, tint = MaterialTheme.colorScheme.outline)
                        Text("Sin fotografía", color = MaterialTheme.colorScheme.outline)
                    }
                }
            } else {
                FotoGuardada(uri, alto = 200.dp)
            }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Button(onClick = {
                val concedido = ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED
                if (concedido) abrirCamara() else explicar = true
            }) {
                Icon(Icons.Filled.PhotoCamera, contentDescription = null)
                Spacer(Modifier.size(8.dp))
                Text(if (fotoUri == null) "Tomar foto" else "Tomar otra foto")
            }
            if (fotoUri != null) {
                OutlinedButton(onClick = { alCambiarFoto(null) }) {
                    Icon(Icons.Filled.Delete, contentDescription = null)
                    Spacer(Modifier.size(8.dp))
                    Text("Quitar foto")
                }
            }
        }
        Text("No fotografíe a personas ni el contenido de documentos o tarjetas.",
            style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.outline)
        AnimatedVisibility(visible = aviso != null) {
            Column {
                Text(aviso ?: "", color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodyMedium)
                if (denegadoParaSiempre) {
                    TextButton(onClick = {
                        val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.fromParts("package", context.packageName, null))
                        context.startActivity(intent)
                    }) { Text("Abrir configuración") }
                }
            }
        }
    }

    if (explicar) {
        AlertDialog(
            onDismissRequest = { explicar = false },
            icon = { Icon(Icons.Filled.PhotoCamera, contentDescription = null) },
            title = { Text("Permiso de cámara") },
            text = { Text("La cámara se usa solo para fotografiar el probador o el objeto como evidencia. La foto queda guardada en la tablet.") },
            confirmButton = {
                TextButton(onClick = {
                    explicar = false
                    permiso.launch(Manifest.permission.CAMERA)
                }) { Text("Continuar") }
            },
            dismissButton = {
                TextButton(onClick = {
                    explicar = false
                    aviso = mensajeSinPermiso
                }) { Text("Ahora no") }
            },
        )
    }
}

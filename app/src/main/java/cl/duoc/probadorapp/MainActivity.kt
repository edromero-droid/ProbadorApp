package cl.duoc.probadorapp

import android.os.Bundle
import android.view.MotionEvent
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.windowsizeclass.ExperimentalMaterial3WindowSizeClassApi
import androidx.compose.ui.Modifier
import androidx.compose.material3.windowsizeclass.calculateWindowSizeClass
import cl.duoc.probadorapp.navigation.NavegacionApp
import cl.duoc.probadorapp.ui.theme.ProbadorAppTheme

class MainActivity : ComponentActivity() {

    private val contenedor get() = (application as ProbadorApp).contenedor

    @OptIn(ExperimentalMaterial3WindowSizeClassApi::class)
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            // Clase de tamaño de la ventana: compacta (celular), mediana o expandida (tablet). Guía 9.
            val clase = calculateWindowSizeClass(this)
            ProbadorAppTheme {
                // Desde Android 15 la app dibuja bajo las barras del sistema: se deja el espacio seguro.
                Box(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background).safeDrawingPadding()) {
                    NavegacionApp(contenedor, clase.widthSizeClass)
                }
            }
        }
    }

    /** Cada toque reinicia el tiempo de inactividad del bloqueo (RF-02). */
    override fun dispatchTouchEvent(ev: MotionEvent): Boolean {
        if (ev.actionMasked == MotionEvent.ACTION_DOWN) contenedor.sesion.registrarActividad()
        return super.dispatchTouchEvent(ev)
    }
}

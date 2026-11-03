package cl.duoc.probadorapp.nativo

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.location.Location
import android.location.LocationManager
import android.os.Build
import androidx.core.content.ContextCompat
import cl.duoc.probadorapp.domain.model.Tienda
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withTimeoutOrNull
import kotlin.coroutines.resume

/**
 * RF-03 y RN-06: identifica la tienda más cercana con la ubicación aproximada
 * del equipo. Recurso nativo: ubicación, con el permiso ACCESS_COARSE_LOCATION.
 */
object Ubicacion {

    /** Distancia máxima para asignar la tienda de forma automática (RN-06). */
    const val DISTANCIA_MAXIMA_M = 2_000f

    fun permitida(context: Context): Boolean =
        ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED

    /** Ubicación actual aproximada, o null si no se pudo obtener en 10 segundos. */
    suspend fun obtener(context: Context): Location? {
        if (!permitida(context)) return null
        val lm = context.getSystemService(Context.LOCATION_SERVICE) as? LocationManager ?: return null
        val proveedor = when {
            lm.isProviderEnabled(LocationManager.NETWORK_PROVIDER) -> LocationManager.NETWORK_PROVIDER
            lm.isProviderEnabled(LocationManager.GPS_PROVIDER) -> LocationManager.GPS_PROVIDER
            else -> return null
        }
        return try {
            val actual = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                withTimeoutOrNull(10_000L) {
                    suspendCancellableCoroutine<Location?> { cont ->
                        lm.getCurrentLocation(proveedor, null, ContextCompat.getMainExecutor(context)) { l -> cont.resume(l) }
                    }
                }
            } else {
                null
            }
            actual ?: lm.getLastKnownLocation(proveedor)
        } catch (e: SecurityException) {
            null
        }
    }

    /** RN-06: tienda registrada más cercana, si está a 2 km o menos. */
    fun tiendaCercana(ubicacion: Location, tiendas: List<Tienda>): Pair<Tienda, Float>? {
        val distancias = tiendas.map { t ->
            val r = FloatArray(1)
            Location.distanceBetween(ubicacion.latitude, ubicacion.longitude, t.latitud, t.longitud, r)
            t to r[0]
        }
        return distancias.minByOrNull { it.second }?.takeIf { it.second <= DISTANCIA_MAXIMA_M }
    }
}

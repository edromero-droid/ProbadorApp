package cl.duoc.probadorapp.data.local

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

private val Context.preferencias by preferencesDataStore(name = "preferencias_equipo")

/**
 * Preferencias guardadas en el equipo con DataStore (sección 4.3.6):
 * modo automático del simulador, sesión abierta y si ya se pidió el permiso de notificaciones. Los ajustes (F-12) se guardan
 * en Room con AjustesDao.
 */
class PreferenciasEquipo(private val context: Context) {

    private val modoAutomaticoClave = booleanPreferencesKey("modo_automatico")
    private val sesionClave = stringPreferencesKey("sesion_uid")

    val modoAutomatico: Flow<Boolean> = context.preferencias.data.map { it[modoAutomaticoClave] ?: false }

    suspend fun guardarModoAutomatico(activo: Boolean) {
        context.preferencias.edit { it[modoAutomaticoClave] = activo }
    }

    private val notificacionesClave = booleanPreferencesKey("notificaciones_preguntadas")

    /** Ya se explicó y pidió el permiso de notificaciones una vez (sección 2.6). */
    val notificacionesPreguntadas: Flow<Boolean> = context.preferencias.data.map { it[notificacionesClave] ?: false }

    suspend fun marcarNotificacionesPreguntadas() {
        context.preferencias.edit { it[notificacionesClave] = true }
    }

    /** Usuario con la sesión abierta en esta tablet, o null. */
    suspend fun leerSesion(): String? = context.preferencias.data.first()[sesionClave]

    suspend fun guardarSesion(uid: String?) {
        context.preferencias.edit { p ->
            p.remove(sesionClave)
            if (uid != null) p[sesionClave] = uid
        }
    }
}

package cl.duoc.probadorapp.nativo

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import cl.duoc.probadorapp.MainActivity
import cl.duoc.probadorapp.R
import cl.duoc.probadorapp.data.repository.RepositorioTienda
import cl.duoc.probadorapp.data.session.RepositorioSesion
import cl.duoc.probadorapp.domain.model.Alerta
import cl.duoc.probadorapp.domain.model.EstadoAlerta
import cl.duoc.probadorapp.domain.model.TipoAlerta
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch

/**
 * RF-21: avisa cada alerta nueva con una notificación del sistema y sonido,
 * solo a las personas que indica RN-31. Recurso nativo: notificaciones, con el
 * permiso POST_NOTIFICATIONS desde Android 13.
 */
class AvisosAlertas(
    private val context: Context,
    private val repo: RepositorioTienda,
    private val sesion: RepositorioSesion,
) {
    companion object {
        const val CANAL_PROBADORES = "alertas_probadores"
        const val CANAL_PERSONAL = "personal"
        private const val MAXIMO_POR_REVISION = 4
    }

    /** Alertas que ya existían al abrir la aplicación: no se notifican. */
    private val previas = mutableSetOf<String>()

    /** Alertas ya avisadas a cada persona ("uid|alerta"), para no repetirlas. */
    private val avisadas = mutableSetOf<String>()

    fun iniciar(scope: CoroutineScope) {
        crearCanales()
        previas += repo.estado.value.alertas.keys
        // Se revisa cada vez que cambian las alertas y cada vez que alguien inicia sesión:
        // así, en una tablet compartida, quien entra recibe las alertas activas que le corresponden.
        scope.launch {
            combine(repo.estado, sesion.usuario) { e, u -> e to u?.uid }.collect { (e, uid) ->
                if (uid == null) return@collect
                val nuevas = e.alertas.values.filter {
                    it.id !in previas && it.estado == EstadoAlerta.ACTIVA && uid in it.destinatarios &&
                        uid !in it.vistaPor && "$uid|${it.id}" !in avisadas
                }
                // Primero las del personal (solicitudes y cobertura) y luego las más recientes.
                // Android descarta notificaciones si una app publica muchas a la vez, así que
                // se publican como máximo 4 por revisión; las demás se ven en la pantalla Alertas.
                val ordenadas = nuevas.sortedWith(
                    compareBy<Alerta> { if (it.tipo == TipoAlerta.SOLICITUD_REFUERZO || it.tipo == TipoAlerta.COBERTURA_BAJA) 0 else 1 }
                        .thenByDescending { it.creada },
                )
                ordenadas.forEachIndexed { i, a ->
                    avisadas += "$uid|${a.id}"
                    if (i < MAXIMO_POR_REVISION) {
                        val codigo = a.probadorId?.let { e.probadores[it]?.codigo }
                        val depto = e.departamentos[a.departamentoId]?.nombre ?: ""
                        notificar(a, a.tipo.texto + (codigo?.let { " · $it" } ?: "") + " · $depto")
                    }
                }
            }
        }
    }

    /** Hay permiso para mostrar notificaciones (antes de Android 13 se concede al instalar). */
    fun permitidas(): Boolean {
        val permiso = Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU ||
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED
        return permiso && NotificationManagerCompat.from(context).areNotificationsEnabled()
    }

    private fun crearCanales() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val manager = context.getSystemService(NotificationManager::class.java) ?: return
        manager.createNotificationChannel(
            NotificationChannel(CANAL_PROBADORES, "Alertas de probadores", NotificationManager.IMPORTANCE_HIGH).apply {
                description = "Tiempo excedido, prendas olvidadas y probadores sin señal"
            },
        )
        manager.createNotificationChannel(
            NotificationChannel(CANAL_PERSONAL, "Personal", NotificationManager.IMPORTANCE_HIGH).apply {
                description = "Cobertura baja y solicitudes de refuerzo"
            },
        )
    }

    private fun notificar(a: Alerta, titulo: String) {
        if (!permitidas()) return
        val canal = when (a.tipo) {
            TipoAlerta.COBERTURA_BAJA, TipoAlerta.SOLICITUD_REFUERZO -> CANAL_PERSONAL
            else -> CANAL_PROBADORES
        }
        val abrir = PendingIntent.getActivity(
            context, 0,
            Intent(context, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        val n = NotificationCompat.Builder(context, canal)
            .setSmallIcon(R.drawable.ic_notificacion)
            .setContentTitle(titulo)
            .setContentText(a.detalle)
            .setStyle(NotificationCompat.BigTextStyle().bigText(a.detalle))
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setDefaults(NotificationCompat.DEFAULT_ALL)
            .setContentIntent(abrir)
            .setAutoCancel(true)
            .build()
        try {
            NotificationManagerCompat.from(context).notify(a.id.hashCode(), n)
        } catch (e: SecurityException) {
            // Sin permiso: la alerta se ve solo dentro de la aplicación (sección 2.6).
        }
    }
}

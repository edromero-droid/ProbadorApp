package cl.duoc.probadorapp.ui

import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/** Duración en minutos y segundos, por ejemplo 03:12. */
fun mmss(ms: Long): String {
    val s = (ms.coerceAtLeast(0) / 1000)
    return "%02d:%02d".format(s / 60, s % 60)
}

/** Duración corta para plazos, por ejemplo 1:40. */
fun mss(ms: Long): String {
    val s = (ms.coerceAtLeast(0) / 1000)
    return "%d:%02d".format(s / 60, s % 60)
}

fun hora(ms: Long): String = SimpleDateFormat("HH:mm", Locale.US).format(Date(ms))

fun horaSegundos(ms: Long): String = SimpleDateFormat("HH:mm:ss", Locale.US).format(Date(ms))

fun fechaHora(ms: Long): String = SimpleDateFormat("dd/MM/yyyy HH:mm", Locale.US).format(Date(ms))

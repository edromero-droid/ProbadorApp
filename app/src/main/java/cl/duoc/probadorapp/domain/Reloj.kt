package cl.duoc.probadorapp.domain

import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/** Fuente de la hora actual. Las pruebas usan un reloj controlado. */
interface Reloj {
    fun ahora(): Long
    fun hoy(): String = SimpleDateFormat("yyyy-MM-dd", Locale.US).format(Date(ahora()))
}

object RelojSistema : Reloj {
    override fun ahora(): Long = System.currentTimeMillis()
}

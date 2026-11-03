package cl.duoc.probadorapp

import cl.duoc.probadorapp.domain.Reloj

/** Reloj controlado por las pruebas. */
class RelojFalso(var ms: Long = 1_760_000_000_000L) : Reloj {
    override fun ahora(): Long = ms
    override fun hoy(): String = "2026-10-21"
    fun avanzar(segundos: Long) { ms += segundos * 1_000L }
}

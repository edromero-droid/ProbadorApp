package cl.duoc.probadorapp.nativo

import android.content.Context
import android.speech.tts.TextToSpeech
import java.util.Locale

/**
 * RF-09: lee en voz alta cada llamado en el equipo del departamento, por ejemplo
 * "Número 12, probador 3". Recurso nativo: texto a voz, que no requiere permisos.
 */
class AnunciadorVoz(context: Context) : TextToSpeech.OnInitListener {

    private val tts = TextToSpeech(context.applicationContext, this)
    private var listo = false

    override fun onInit(estado: Int) {
        if (estado != TextToSpeech.SUCCESS) return
        val chile = tts.setLanguage(Locale("es", "CL"))
        if (chile == TextToSpeech.LANG_MISSING_DATA || chile == TextToSpeech.LANG_NOT_SUPPORTED) {
            tts.setLanguage(Locale("es"))
        }
        listo = true
    }

    fun anunciar(numero: Int, probadorCodigo: String) {
        if (!listo) return
        val probador = probadorCodigo.removePrefix("P-").trimStart('0')
        tts.speak("Número $numero, probador $probador", TextToSpeech.QUEUE_ADD, null, "llamado-$numero")
    }

    fun cerrar() {
        tts.stop()
        tts.shutdown()
    }
}

package cl.duoc.probadorapp.domain.validation

/**
 * Funciones de validación de los formularios (sección 4.3.8 del documento).
 * Cada función devuelve el mensaje de error, o null si el valor es válido.
 */
object Validaciones {

    private val FORMATO_CORREO = Regex("^[A-Za-z0-9._%+-]+@[A-Za-z0-9.-]+\\.[A-Za-z]{2,}$")

    fun correo(valor: String): String? = when {
        valor.isBlank() -> "El correo es obligatorio"
        !FORMATO_CORREO.matches(valor.trim()) -> "Formato de correo inválido"
        else -> null
    }

    fun contrasena(valor: String): String? =
        if (valor.isEmpty()) "La contraseña es obligatoria" else null

    fun seleccion(valor: Any?, mensaje: String): String? =
        if (valor == null || (valor is String && valor.isBlank())) mensaje else null

    /** Texto obligatorio con largo mínimo y máximo, por ejemplo una descripción. */
    fun textoConLargo(valor: String, minimo: Int, maximo: Int, obligatorio: String): String? {
        val t = valor.trim()
        return when {
            t.isEmpty() -> obligatorio
            t.length < minimo -> "Mínimo $minimo caracteres"
            t.length > maximo -> "Máximo $maximo caracteres"
            else -> null
        }
    }

    /**
     * Número entero dentro de un rango, ingresado como texto.
     * [vacio] es el mensaje cuando el campo está vacío.
     */
    fun enteroEnRango(valor: String, minimo: Int, maximo: Int, vacio: String = "Ingrese un valor"): String? {
        val t = valor.trim()
        if (t.isEmpty()) return vacio
        val n = t.toIntOrNull() ?: return "Solo se permiten números"
        return if (n < minimo || n > maximo) "Debe estar entre $minimo y $maximo" else null
    }

    /**
     * Código de sensor (PS-NN) o de lector (LR-NN). [usados] relaciona cada
     * código ya asignado con el nombre del probador que lo usa.
     */
    fun codigoDispositivo(valor: String, prefijo: String, vacio: String, usados: Map<String, String>): String? {
        val t = valor.trim().uppercase()
        return when {
            t.isEmpty() -> vacio
            !Regex("^$prefijo-\\d{2}$").matches(t) -> "Formato $prefijo-NN, por ejemplo $prefijo-03"
            usados.containsKey(t) -> "El código ya está asignado al probador ${usados.getValue(t)}"
            else -> null
        }
    }

    /** Fecha del historial: no puede ser posterior a hoy. Las fechas van como año-mes-día. */
    fun fechaNoFutura(fecha: String, hoy: String): String? =
        if (fecha > hoy) "La fecha no puede ser posterior a hoy" else null
}

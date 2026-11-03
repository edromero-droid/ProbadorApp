package cl.duoc.probadorapp.domain

import cl.duoc.probadorapp.domain.validation.Validaciones
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ValidacionesTest {

    @Test
    fun correoObligatorioYConFormato() {
        assertEquals("El correo es obligatorio", Validaciones.correo(""))
        assertEquals("Formato de correo inválido", Validaciones.correo("ana.perez@"))
        assertNull(Validaciones.correo("ana.perez@rinconsur.test"))
    }

    @Test
    fun contrasenaObligatoria() {
        assertEquals("La contraseña es obligatoria", Validaciones.contrasena(""))
        assertNull(Validaciones.contrasena("x"))
    }

    @Test
    fun enteroEnRango() {
        assertEquals("Ingrese un valor", Validaciones.enteroEnRango("", 1, 10))
        assertEquals("Solo se permiten números", Validaciones.enteroEnRango("a", 1, 10))
        assertEquals("Debe estar entre 1 y 10", Validaciones.enteroEnRango("12", 1, 10))
        assertNull(Validaciones.enteroEnRango("10", 1, 10))
    }

    @Test
    fun textoConLargo() {
        assertEquals("La descripción es obligatoria", Validaciones.textoConLargo(" ", 10, 200, "La descripción es obligatoria"))
        assertEquals("Mínimo 10 caracteres", Validaciones.textoConLargo("corto", 10, 200, "x"))
        assertEquals("Máximo 20 caracteres", Validaciones.textoConLargo("a".repeat(21), 5, 20, "x"))
        assertNull(Validaciones.textoConLargo("Hay restos de etiquetas", 10, 200, "x"))
    }

    @Test
    fun codigoDeDispositivo() {
        val usados = mapOf("PS-03" to "P-03 de Mujer")
        assertEquals("Ingrese el código del sensor", Validaciones.codigoDispositivo("", "PS", "Ingrese el código del sensor", usados))
        assertEquals("Formato PS-NN, por ejemplo PS-03", Validaciones.codigoDispositivo("PS3", "PS", "x", usados))
        assertEquals("El código ya está asignado al probador P-03 de Mujer", Validaciones.codigoDispositivo("ps-03", "PS", "x", usados))
        assertNull(Validaciones.codigoDispositivo("PS-19", "PS", "x", usados))
    }

    @Test
    fun fechaNoFutura() {
        assertEquals("La fecha no puede ser posterior a hoy", Validaciones.fechaNoFutura("2026-10-22", "2026-10-21"))
        assertNull(Validaciones.fechaNoFutura("2026-10-21", "2026-10-21"))
    }
}

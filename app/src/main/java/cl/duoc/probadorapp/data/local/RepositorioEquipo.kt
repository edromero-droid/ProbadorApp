package cl.duoc.probadorapp.data.local

import cl.duoc.probadorapp.domain.model.ConfiguracionEquipo
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/** Configuración de este equipo (F-03), guardada con [EquipoDao]. */
class RepositorioEquipo(private val dao: EquipoDao) {
    private val flujo = MutableStateFlow<ConfiguracionEquipo?>(null)

    /** null mientras el equipo no se configura: se usa como tablet del personal. */
    val configuracion: StateFlow<ConfiguracionEquipo?> = flujo.asStateFlow()

    suspend fun cargar() {
        flujo.value = dao.leer()?.aModelo()
    }

    suspend fun guardar(c: ConfiguracionEquipo) {
        dao.guardar(c.aEntidad())
        flujo.value = c
    }
}

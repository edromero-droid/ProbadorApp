package cl.duoc.probadorapp.data.local

import cl.duoc.probadorapp.data.repository.DatosIniciales
import cl.duoc.probadorapp.domain.model.Prenda
import cl.duoc.probadorapp.signals.Senal
import kotlinx.coroutines.flow.Flow

/** Datos locales del equipo: catálogo de prendas y registro de señales. */
class RepositorioDatosLocales(private val db: BaseDatosLocal) {

    @Volatile
    var catalogo: List<Prenda> = DatosIniciales.catalogo
        private set

    /** Carga el catálogo inicial la primera vez y lo deja en memoria. */
    suspend fun cargarCatalogo() {
        val dao = db.prendaDao()
        if (dao.contar() == 0) {
            dao.insertar(DatosIniciales.catalogo.map { PrendaEntity(it.epc, it.descripcion, it.talla, it.color) })
        }
        catalogo = dao.todas().map { Prenda(it.epc, it.descripcion, it.talla, it.color) }
    }

    suspend fun registrar(senal: Senal) {
        val detalle = if (senal.prendas.isEmpty()) "" else senal.prendas.size.toString()
        db.senalDao().insertar(SenalEntity(dispositivo = senal.dispositivo, tipo = senal.tipo.name, hora = senal.hora, detalle = detalle))
        db.senalDao().borrarAnteriores(senal.hora - 24 * 60 * 60_000L)
    }

    fun ultimasSenales(cantidad: Int = 8): Flow<List<SenalEntity>> = db.senalDao().ultimas(cantidad)

    suspend fun borrarSenales() = db.senalDao().borrarTodo()
}

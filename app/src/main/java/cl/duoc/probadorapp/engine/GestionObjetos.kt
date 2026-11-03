package cl.duoc.probadorapp.engine

import cl.duoc.probadorapp.data.repository.RepositorioTienda
import cl.duoc.probadorapp.domain.Reloj
import cl.duoc.probadorapp.domain.model.EstadoObjeto
import cl.duoc.probadorapp.domain.model.ObjetoEncontrado
import cl.duoc.probadorapp.domain.model.Rol
import cl.duoc.probadorapp.domain.model.TipoObjeto
import cl.duoc.probadorapp.domain.model.Usuario
import java.util.UUID

/** Objetos personales encontrados en los probadores (RF-26, RF-27 y RN-23 a RN-25). */
class GestionObjetos(
    private val repo: RepositorioTienda,
    private val reloj: Reloj,
) {
    /**
     * RN-24: registra el objeto y lo asocia a la última ocupación terminada del
     * probador y a su número atendido, si existe.
     */
    suspend fun registrar(actor: Usuario, probadorId: String, tipo: TipoObjeto, descripcion: String, fotoUri: String?): ObjetoEncontrado? =
        repo.transaccion { e ->
            if (e.probadores[probadorId] == null) return@transaccion e to null
            val ultima = e.ocupaciones.values.filter { it.probadorId == probadorId && it.salida != null }.maxByOrNull { it.salida!! }
            val numero = ultima?.numeroId?.let { e.numeros[it]?.numero }
            val o = ObjetoEncontrado(
                UUID.randomUUID().toString(), probadorId, tipo, descripcion.trim(), fotoUri,
                ultima?.id, numero, actor.uid, reloj.ahora(),
            )
            e.copy(objetos = e.objetos + (o.id to o)) to o
        }

    /** RN-25: cualquier persona del departamento lo entrega; solo los jefes lo derivan. */
    fun puedeDerivar(actor: Usuario): Boolean = actor.rol != Rol.VENDEDOR

    suspend fun entregar(actor: Usuario, id: String): String? = cerrar(actor, id, EstadoObjeto.ENTREGADO)

    suspend fun derivar(actor: Usuario, id: String): String? {
        if (!puedeDerivar(actor)) return "Solo el jefe de departamento o el jefe de tienda deriva objetos"
        return cerrar(actor, id, EstadoObjeto.DERIVADO)
    }

    private suspend fun cerrar(actor: Usuario, id: String, estado: EstadoObjeto): String? = repo.transaccion { e ->
        val o = e.objetos[id] ?: return@transaccion e to "El objeto no existe"
        if (o.estado != EstadoObjeto.GUARDADO) return@transaccion e to "Este objeto ya fue ${o.estado.texto.lowercase()}"
        e.copy(objetos = e.objetos + (id to o.copy(estado = estado, cierre = reloj.ahora(), cerradoPor = actor.uid))) to null
    }
}

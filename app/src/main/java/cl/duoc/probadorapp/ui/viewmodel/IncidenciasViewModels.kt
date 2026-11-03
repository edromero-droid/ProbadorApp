package cl.duoc.probadorapp.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import cl.duoc.probadorapp.data.repository.EstadoTienda
import cl.duoc.probadorapp.data.repository.RepositorioTienda
import cl.duoc.probadorapp.data.session.RepositorioSesion
import cl.duoc.probadorapp.domain.model.Departamento
import cl.duoc.probadorapp.domain.model.EstadoGuardado
import cl.duoc.probadorapp.domain.model.Incidencia
import cl.duoc.probadorapp.domain.model.Rol
import cl.duoc.probadorapp.domain.model.TipoIncidencia
import cl.duoc.probadorapp.domain.model.Usuario
import cl.duoc.probadorapp.domain.validation.Validaciones
import cl.duoc.probadorapp.engine.MotorReglas
import cl.duoc.probadorapp.ui.fechaHora
import cl.duoc.probadorapp.ui.hora
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

const val DESCRIPCION_MIN = 10
const val DESCRIPCION_MAX = 200

// ---------------------------------------------------------------------- UI-09 Reportar incidencia (F-04)

/** Valores del formulario y si el usuario ya modificó la descripción. */
data class IncidenciaForm(
    val probadorId: String? = null,
    val tipo: TipoIncidencia? = null,
    val descripcion: String = "",
    val descripcionTocada: Boolean = false,
    val fotoUri: String? = null,
    val guardando: Boolean = false,
    /** Id de la incidencia registrada, para navegar al resumen. */
    val creadaId: String? = null,
)

data class ReportarUi(
    val form: IncidenciaForm = IncidenciaForm(),
    val probadores: List<Pair<String, String>> = emptyList(),
    val probadorFijo: Boolean = false,
    val probadorTexto: String = "",
    val errorProbador: String? = null,
    val errorTipo: String? = null,
    val errorDescripcion: String? = null,
    val contador: String = "0/$DESCRIPCION_MAX",
    val puedeEnviar: Boolean = false,
)

/**
 * Formulario de incidencia. [probadorInicial] llega como argumento de la
 * navegación cuando el formulario se abre desde el detalle de un probador.
 */
class ReportarIncidenciaViewModel(
    private val probadorInicial: String?,
    repo: RepositorioTienda,
    private val motor: MotorReglas,
    private val sesion: RepositorioSesion,
) : ViewModel() {

    private val form = MutableStateFlow(IncidenciaForm(probadorId = probadorInicial))

    val estado: StateFlow<ReportarUi> = combine(repo.estado, form, sesion.usuario) { e, f, u -> construir(e, f, u) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), ReportarUi(form = form.value))

    fun elegirProbador(id: String) = form.update { it.copy(probadorId = id) }
    fun elegirTipo(tipo: TipoIncidencia) = form.update { it.copy(tipo = tipo) }
    fun alCambiarDescripcion(texto: String) = form.update { it.copy(descripcion = texto, descripcionTocada = true) }
    fun alCambiarFoto(uri: String?) = form.update { it.copy(fotoUri = uri) }
    fun navegado() = form.update { it.copy(creadaId = null) }

    fun enviar() {
        val f = form.value
        val ui = estado.value
        val uid = sesion.usuario.value?.uid ?: return
        if (!ui.puedeEnviar) return
        form.update { it.copy(guardando = true) }
        viewModelScope.launch {
            val inc = motor.reportarIncidencia(f.probadorId!!, f.tipo!!, f.descripcion, f.fotoUri, uid)
            form.update { it.copy(guardando = false, creadaId = inc.id) }
        }
    }

    private fun construir(e: EstadoTienda, f: IncidenciaForm, u: Usuario?): ReportarUi {
        val todos = u?.rol == Rol.JEFE_TIENDA
        val opciones = e.probadores.values
            .filter { it.activo && (todos || it.departamentoId == u?.departamentoId) }
            .sortedWith(compareBy({ it.departamentoId }, { it.codigo }))
            .map { p -> p.id to (if (todos) "${e.departamentos[p.departamentoId]?.nombre} · ${p.codigo}" else p.codigo) }
        val errorProbador = Validaciones.seleccion(f.probadorId, "Seleccione un probador")
        val errorTipo = Validaciones.seleccion(f.tipo, "Seleccione el tipo de incidencia")
        val errorDescripcion = Validaciones.textoConLargo(f.descripcion, DESCRIPCION_MIN, DESCRIPCION_MAX, "La descripción es obligatoria")
        val fijo = probadorInicial?.let { e.probadores[it] }
        return ReportarUi(
            form = f,
            probadores = opciones,
            probadorFijo = fijo != null,
            probadorTexto = fijo?.let { "${e.departamentos[it.departamentoId]?.nombre} · ${it.codigo}" } ?: "",
            errorProbador = errorProbador,
            errorTipo = errorTipo,
            errorDescripcion = if (f.descripcionTocada) errorDescripcion else null,
            contador = "${f.descripcion.trim().length}/$DESCRIPCION_MAX",
            puedeEnviar = errorProbador == null && errorTipo == null && errorDescripcion == null && !f.guardando,
        )
    }
}

// ---------------------------------------------------------------------- UI-10 Resumen de incidencia

data class ResumenIncidenciaUi(
    val existe: Boolean = false,
    val titulo: String = "",
    val datos: List<Pair<String, String>> = emptyList(),
    val descripcion: String = "",
    val abierta: Boolean = true,
    val efecto: String = "",
    val fotoUri: String? = null,
)

/** Muestra los datos de la incidencia recibida como argumento de la navegación (RF-24). */
class ResumenIncidenciaViewModel(
    private val incidenciaId: String,
    repo: RepositorioTienda,
) : ViewModel() {

    val estado: StateFlow<ResumenIncidenciaUi> = repo.estado.map { construir(it) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), ResumenIncidenciaUi())

    private fun construir(e: EstadoTienda): ResumenIncidenciaUi {
        val i = e.incidencias[incidenciaId] ?: return ResumenIncidenciaUi()
        val p = e.probadores[i.probadorId]
        val codigo = p?.codigo ?: "-"
        val efecto = when {
            !i.abierta -> "La incidencia se cerró el ${i.cierre?.let { fechaHora(it) } ?: "-"}."
            p?.estado == EstadoGuardado.OCUPADO ->
                "El probador $codigo está Ocupado. Quedará En revisión cuando el cliente salga."
            else -> "El probador $codigo quedó En revisión y no recibe números hasta cerrar la incidencia."
        }
        return ResumenIncidenciaUi(
            existe = true,
            titulo = "${i.tipo.texto} · $codigo",
            datos = listOf(
                "Probador" to "$codigo · ${p?.let { e.departamentos[it.departamentoId]?.nombre } ?: ""}",
                "Tipo" to i.tipo.texto,
                "Reportada" to fechaHora(i.fecha),
                "Reportada por" to (e.usuarios[i.usuarioUid]?.nombre ?: "-"),
                "Estado" to if (i.abierta) "Abierta" else "Cerrada",
                "Fotografía" to if (i.fotoUri != null) "Sí" else "Sin fotografía",
            ),
            descripcion = i.descripcion,
            abierta = i.abierta,
            efecto = efecto,
            fotoUri = i.fotoUri,
        )
    }
}

// ---------------------------------------------------------------------- UI-11 Incidencias del departamento

data class IncidenciaUi(
    val id: String,
    val titulo: String,
    val codigo: String,
    val descripcion: String,
    val detalle: String,
    val abierta: Boolean,
    val fotoUri: String?,
)

data class IncidenciasUi(
    val departamentos: List<Departamento> = emptyList(),
    val departamentoId: String = "",
    val puedeElegirDepartamento: Boolean = false,
    val abiertas: List<IncidenciaUi> = emptyList(),
    val cerradas: List<IncidenciaUi> = emptyList(),
)

/** RF-25: incidencias abiertas y cerradas del departamento. */
class IncidenciasViewModel(
    repo: RepositorioTienda,
    private val motor: MotorReglas,
    sesion: RepositorioSesion,
) : ViewModel() {

    private val elegido = MutableStateFlow<String?>(null)

    val estado: StateFlow<IncidenciasUi> = combine(repo.estado, elegido, sesion.usuario) { e, dep, u -> construir(e, dep, u) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), IncidenciasUi())

    fun elegirDepartamento(id: String) {
        elegido.value = id
    }

    fun cerrar(id: String) {
        viewModelScope.launch { motor.cerrarIncidencia(id) }
    }

    private fun construir(e: EstadoTienda, elegido: String?, u: Usuario?): IncidenciasUi {
        val depId = departamentoVisible(e, elegido, u) ?: return IncidenciasUi()
        val lista = e.incidencias.values
            .filter { e.probadores[it.probadorId]?.departamentoId == depId }
            .sortedByDescending { it.fecha }
            .map { aUi(e, it) }
        return IncidenciasUi(
            departamentos = e.departamentos.values.toList(),
            departamentoId = depId,
            puedeElegirDepartamento = u?.rol == Rol.JEFE_TIENDA,
            abiertas = lista.filter { it.abierta },
            cerradas = lista.filter { !it.abierta },
        )
    }

    private fun aUi(e: EstadoTienda, i: Incidencia): IncidenciaUi {
        val codigo = e.probadores[i.probadorId]?.codigo ?: "-"
        val quien = e.usuarios[i.usuarioUid]?.nombre ?: "-"
        val detalle = "${fechaHora(i.fecha)} · $quien" + (i.cierre?.let { " · Cerrada a las ${hora(it)}" } ?: "")
        return IncidenciaUi(i.id, "${i.tipo.texto} · $codigo", codigo, i.descripcion, detalle, i.abierta, i.fotoUri)
    }
}

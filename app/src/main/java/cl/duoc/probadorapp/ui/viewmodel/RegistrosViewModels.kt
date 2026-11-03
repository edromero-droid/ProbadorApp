package cl.duoc.probadorapp.ui.viewmodel

import androidx.lifecycle.viewModelScope
import cl.duoc.probadorapp.data.repository.EstadoTienda
import cl.duoc.probadorapp.data.repository.RepositorioTienda
import cl.duoc.probadorapp.data.session.RepositorioSesion
import cl.duoc.probadorapp.domain.model.Departamento
import cl.duoc.probadorapp.domain.model.EstadoAlerta
import cl.duoc.probadorapp.domain.model.EstadoObjeto
import cl.duoc.probadorapp.domain.model.ObjetoEncontrado
import cl.duoc.probadorapp.domain.model.Rol
import cl.duoc.probadorapp.domain.model.TipoAlerta
import cl.duoc.probadorapp.domain.model.TipoObjeto
import cl.duoc.probadorapp.domain.model.Usuario
import cl.duoc.probadorapp.domain.validation.Validaciones
import cl.duoc.probadorapp.engine.GestionObjetos
import cl.duoc.probadorapp.engine.MotorReglas
import cl.duoc.probadorapp.ui.fechaHora
import cl.duoc.probadorapp.ui.hora
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

// ---------------------------------------------------------------------- UI-08 Alertas

/** Pantalla a la que lleva una alerta (sección 3.3). */
enum class DestinoAlerta { PROBADOR, PERSONAL, SOLICITUDES }

data class AlertaPersonalUi(
    val id: String,
    val tipo: TipoAlerta,
    val titulo: String,
    val detalle: String,
    val hora: String,
    val vista: Boolean,
    val destino: DestinoAlerta,
    val probadorId: String?,
)

/** RF-22: alertas activas dirigidas a la persona (RN-31). */
class AlertasViewModel(
    repo: RepositorioTienda,
    private val sesion: RepositorioSesion,
    private val motor: MotorReglas,
) : ViewModelConMensaje() {

    val estado: StateFlow<List<AlertaPersonalUi>> = combine(repo.estado, sesion.usuario) { e, u -> construir(e, u) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    fun marcarVista(id: String) {
        val uid = sesion.usuario.value?.uid ?: return
        viewModelScope.launch { motor.marcarVista(id, uid) }
    }

    private fun construir(e: EstadoTienda, u: Usuario?): List<AlertaPersonalUi> {
        if (u == null) return emptyList()
        return e.alertas.values
            .filter { it.estado == EstadoAlerta.ACTIVA && (u.uid in it.destinatarios || u.rol == Rol.JEFE_TIENDA) }
            .sortedByDescending { it.creada }
            .map { a ->
                val codigo = a.probadorId?.let { e.probadores[it]?.codigo }
                val depto = e.departamentos[a.departamentoId]?.nombre ?: ""
                val personas = a.destinatarios.mapNotNull { e.usuarios[it]?.nombre }
                AlertaPersonalUi(
                    id = a.id,
                    tipo = a.tipo,
                    titulo = a.tipo.texto + (codigo?.let { " · $it" } ?: "") + " · $depto",
                    detalle = a.detalle + " · Avisada a: " + (if (personas.isEmpty()) "Sin personal en turno" else personas.joinToString(", ")),
                    hora = hora(a.creada),
                    vista = u.uid in a.vistaPor,
                    destino = when (a.tipo) {
                        TipoAlerta.COBERTURA_BAJA -> DestinoAlerta.PERSONAL
                        TipoAlerta.SOLICITUD_REFUERZO -> DestinoAlerta.SOLICITUDES
                        else -> DestinoAlerta.PROBADOR
                    },
                    probadorId = a.probadorId,
                )
            }
    }
}

// ---------------------------------------------------------------------- UI-11 Objetos encontrados

data class ObjetoUi(
    val id: String,
    val titulo: String,
    val descripcion: String,
    val detalle: String,
    val estado: EstadoObjeto,
    val fotoUri: String?,
)

data class ObjetosUi(
    val departamentos: List<Departamento> = emptyList(),
    val departamentoId: String = "",
    val puedeElegirDepartamento: Boolean = false,
    val busqueda: String = "",
    val guardados: List<ObjetoUi> = emptyList(),
    val cerrados: List<ObjetoUi> = emptyList(),
    val puedeDerivar: Boolean = false,
)

/** RF-27: objetos del departamento, con búsqueda por fecha, probador, número o descripción. */
class ObjetosViewModel(
    repo: RepositorioTienda,
    private val sesion: RepositorioSesion,
    private val objetos: GestionObjetos,
) : ViewModelConMensaje() {
    private val elegido = MutableStateFlow<String?>(null)
    private val busqueda = MutableStateFlow("")

    val estado: StateFlow<ObjetosUi> = combine(repo.estado, elegido, busqueda, sesion.usuario) { e, d, b, u -> construir(e, d, b, u) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), ObjetosUi())

    fun elegirDepartamento(id: String) {
        elegido.value = id
    }

    fun buscar(texto: String) {
        busqueda.value = texto
    }

    fun entregar(id: String) = accion { objetos.entregar(it, id) ?: "Objeto entregado" }
    fun derivar(id: String) = accion { objetos.derivar(it, id) ?: "Objeto derivado a objetos perdidos" }

    private fun accion(bloque: suspend (Usuario) -> String) {
        val u = sesion.usuario.value ?: return
        viewModelScope.launch { mensajeFlujo.value = bloque(u) }
    }

    private fun construir(e: EstadoTienda, elegido: String?, b: String, u: Usuario?): ObjetosUi {
        val dep = departamentoVisible(e, elegido, u) ?: return ObjetosUi()
        val texto = b.trim().lowercase()
        val lista = e.objetos.values
            .filter { e.probadores[it.probadorId]?.departamentoId == dep }
            .sortedByDescending { it.fecha }
            .map { aUi(e, it) to it }
            .filter { (ui, o) ->
                texto.isEmpty() || listOf(ui.titulo, ui.descripcion, ui.detalle, o.numero?.toString() ?: "").any { it.lowercase().contains(texto) }
            }
            .map { it.first }
        return ObjetosUi(
            departamentos = e.departamentos.values.toList(),
            departamentoId = dep,
            puedeElegirDepartamento = u?.rol == Rol.JEFE_TIENDA,
            busqueda = b,
            guardados = lista.filter { it.estado == EstadoObjeto.GUARDADO },
            cerrados = lista.filter { it.estado != EstadoObjeto.GUARDADO },
            puedeDerivar = u != null && objetos.puedeDerivar(u),
        )
    }

    private fun aUi(e: EstadoTienda, o: ObjetoEncontrado): ObjetoUi {
        val codigo = e.probadores[o.probadorId]?.codigo ?: "-"
        val detalle = buildString {
            append("${fechaHora(o.fecha)} · Probador $codigo")
            append(o.numero?.let { " · Número $it" } ?: " · Sin número")
            append(" · Registró ${e.usuarios[o.registradoPor]?.nombre ?: "-"}")
            if (o.cierre != null) append(" · ${o.estado.texto} a las ${hora(o.cierre)} por ${e.usuarios[o.cerradoPor]?.nombre ?: "-"}")
        }
        return ObjetoUi(o.id, "${o.tipo.texto} · $codigo", o.descripcion, detalle, o.estado, o.fotoUri)
    }
}

// ---------------------------------------------------------------------- UI-12 Registrar objeto encontrado (F-05)

data class ObjetoForm(
    val probadorId: String? = null,
    val tipo: TipoObjeto? = null,
    val descripcion: String = "",
    val descripcionTocada: Boolean = false,
    val fotoUri: String? = null,
    val guardando: Boolean = false,
    val registrado: Boolean = false,
)

data class RegistrarObjetoUi(
    val form: ObjetoForm = ObjetoForm(),
    val probadores: List<Pair<String, String>> = emptyList(),
    val probadorFijo: Boolean = false,
    val probadorTexto: String = "",
    val asociacion: String = "",
    val errorDescripcion: String? = null,
    val contador: String = "0/150",
    val puedeEnviar: Boolean = false,
)

/** Formulario F-05. [probadorInicial] llega desde el detalle del probador. */
class RegistrarObjetoViewModel(
    private val probadorInicial: String?,
    repo: RepositorioTienda,
    private val sesion: RepositorioSesion,
    private val objetos: GestionObjetos,
) : ViewModelConMensaje() {
    private val form = MutableStateFlow(ObjetoForm(probadorId = probadorInicial))

    val estado: StateFlow<RegistrarObjetoUi> = combine(repo.estado, form, sesion.usuario) { e, f, u -> construir(e, f, u) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), RegistrarObjetoUi(form = form.value))

    fun elegirProbador(id: String) = form.update { it.copy(probadorId = id) }
    fun elegirTipo(tipo: TipoObjeto) = form.update { it.copy(tipo = tipo) }
    fun alCambiarDescripcion(v: String) = form.update { it.copy(descripcion = v, descripcionTocada = true) }
    fun alCambiarFoto(uri: String?) = form.update { it.copy(fotoUri = uri) }

    fun enviar() {
        val u = sesion.usuario.value ?: return
        val f = form.value
        if (!estado.value.puedeEnviar) return
        form.update { it.copy(guardando = true) }
        viewModelScope.launch {
            val o = objetos.registrar(u, f.probadorId!!, f.tipo!!, f.descripcion, f.fotoUri)
            if (o == null) {
                form.update { it.copy(guardando = false) }
                mensajeFlujo.value = "No fue posible registrar el objeto"
            } else {
                form.update { it.copy(guardando = false, registrado = true) }
            }
        }
    }

    private fun construir(e: EstadoTienda, f: ObjetoForm, u: Usuario?): RegistrarObjetoUi {
        val todos = u?.rol == Rol.JEFE_TIENDA
        val opciones = e.probadores.values
            .filter { todos || it.departamentoId == u?.departamentoId }
            .sortedWith(compareBy({ it.departamentoId }, { it.codigo }))
            .map { p -> p.id to (if (todos) "${e.departamentos[p.departamentoId]?.nombre} · ${p.codigo}" else p.codigo) }
        val errorProbador = Validaciones.seleccion(f.probadorId, "Seleccione un probador")
        val errorTipo = Validaciones.seleccion(f.tipo, "Seleccione el tipo de objeto")
        val errorDescripcion = Validaciones.textoConLargo(f.descripcion, 5, 150, "La descripción es obligatoria")
        val fijo = probadorInicial?.let { e.probadores[it] }
        // RN-24: se muestra a qué ocupación y número quedará asociado.
        val ultima = f.probadorId?.let { id -> e.ocupaciones.values.filter { it.probadorId == id && it.salida != null }.maxByOrNull { it.salida!! } }
        val asociacion = when {
            f.probadorId == null -> ""
            ultima == null -> "El probador no tiene ocupaciones terminadas hoy."
            else -> "Se asociará a la ocupación de las ${hora(ultima.entrada)}" +
                (ultima.numeroId?.let { e.numeros[it]?.numero }?.let { ", número $it." } ?: ", sin número.")
        }
        return RegistrarObjetoUi(
            form = f,
            probadores = opciones,
            probadorFijo = fijo != null,
            probadorTexto = fijo?.let { "${e.departamentos[it.departamentoId]?.nombre} · ${it.codigo}" } ?: "",
            asociacion = asociacion,
            errorDescripcion = errorDescripcion.takeIf { f.descripcionTocada },
            contador = "${f.descripcion.trim().length}/150",
            puedeEnviar = errorProbador == null && errorTipo == null && errorDescripcion == null && !f.guardando,
        )
    }
}

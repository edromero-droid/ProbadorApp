package cl.duoc.probadorapp.domain.model

/**
 * Modelos del dominio de ProbadorApp. No dependen de Android para poder
 * probarlos con pruebas unitarias (RNF-10).
 */

enum class Rol(val texto: String) {
    VENDEDOR("Vendedor"),
    JEFE_DEPARTAMENTO("Jefe de departamento"),
    JEFE_TIENDA("Jefe de tienda"),
}

/** Estados que se guardan. Excedido y Sin señal se calculan (RN-07). */
enum class EstadoGuardado { LIBRE, RESERVADO, OCUPADO, EN_REVISION }

/** Estado que se muestra al personal. */
enum class EstadoVisible(val texto: String) {
    LIBRE("Libre"),
    RESERVADO("Reservado"),
    OCUPADO("Ocupado"),
    EXCEDIDO("Excedido"),
    EN_REVISION("En revisión"),
    SIN_SENAL("Sin señal"),
}

/** Estado que se muestra en el televisor y en la página del cliente (RN-14). */
enum class EstadoCliente(val texto: String) {
    LIBRE("Libre"),
    LLAMANDO("Llamando"),
    OCUPADO("Ocupado"),
    NO_DISPONIBLE("No disponible"),
}

enum class Canal(val texto: String) { QR("QR"), SIN_CELULAR("Sin celular") }

enum class EstadoNumero(val texto: String) {
    EN_ESPERA("En espera"),
    LLAMADO("Llamado"),
    ATENDIDO("Atendido"),
    PERDIDO("Perdido"),
}

enum class TipoAlerta(val texto: String) {
    TIEMPO_EXCEDIDO("Tiempo excedido"),
    PRENDA_OLVIDADA("Prenda olvidada"),
    SIN_SENAL("Sin señal"),
    COBERTURA_BAJA("Cobertura baja"),
    SOLICITUD_REFUERZO("Solicitud de refuerzo"),
}

enum class EstadoAlerta { ACTIVA, CERRADA }

/** Cómo se cambió a una persona de departamento (RN-27 a RN-29). */
enum class TipoMovimiento(val texto: String) {
    DIRECTO("Movimiento del jefe de tienda"),
    CESION("Cesión del jefe de departamento"),
    SOLICITUD("Refuerzo aceptado"),
    JEFATURA("Cambio de jefatura"),
}

data class Parametros(
    val tiempoMaximoMin: Int = 15,
    val plazoLlamadoMin: Int = 2,
    val bloqueoMin: Int = 5,
    val anuncioVoz: Boolean = true,
)

data class Departamento(
    val id: String,
    val nombre: String,
    val colorArgb: Long,
    val minimoPersonal: Int = 1,
    val jefeUid: String? = null,
)

/** Prenda de la tienda leída por el lector RFID. */
data class Prenda(
    val epc: String,
    val descripcion: String,
    val talla: String,
    val color: String,
)

data class PrendaDetectada(
    val prenda: Prenda,
    val detectada: Long,
    val retirada: Long? = null,
)

data class Probador(
    val id: String,
    val departamentoId: String,
    val codigo: String,
    val sensorCodigo: String,
    val lectorCodigo: String,
    val activo: Boolean = true,
    val estado: EstadoGuardado = EstadoGuardado.LIBRE,
    val sinSenal: Boolean = false,
    val numeroId: String? = null,
    val ocupacionId: String? = null,
    val prendasDentro: List<PrendaDetectada> = emptyList(),
)

data class NumeroAtencion(
    val id: String,
    val departamentoId: String,
    val numero: Int,
    val fecha: String,
    val canal: Canal,
    val estado: EstadoNumero = EstadoNumero.EN_ESPERA,
    val emitido: Long,
    val probadorId: String? = null,
    val llamado: Long? = null,
    val cierre: Long? = null,
    val clienteUid: String? = null,
    val entregadoPor: String? = null,
)

data class Ocupacion(
    val id: String,
    val probadorId: String,
    val numeroId: String?,
    val entrada: Long,
    val salida: Long? = null,
    val tiempoMaximoMin: Int,
    val alertaEmitida: Boolean = false,
    val prendas: List<PrendaDetectada> = emptyList(),
)

data class Alerta(
    val id: String,
    val departamentoId: String,
    val probadorId: String?,
    val tipo: TipoAlerta,
    val detalle: String,
    val creada: Long,
    val destinatarios: List<String>,
    val vistaPor: Set<String> = emptySet(),
    val estado: EstadoAlerta = EstadoAlerta.ACTIVA,
    val cierre: Long? = null,
    /** Solicitud de refuerzo a la que se refiere la alerta, si corresponde. */
    val referenciaId: String? = null,
)

/** Tipos de incidencia (RN-22). */
enum class TipoIncidencia(val texto: String) {
    PROBADOR_DANADO("Probador dañado"),
    PROBADOR_SUCIO("Probador sucio"),
    OTRO("Otro"),
}

/** Incidencia reportada por el personal sobre un probador (RF-23). */
data class Incidencia(
    val id: String,
    val probadorId: String,
    val tipo: TipoIncidencia,
    val descripcion: String,
    val fotoUri: String?,
    val usuarioUid: String,
    val fecha: Long,
    val abierta: Boolean = true,
    val cierre: Long? = null,
)

data class Usuario(
    val uid: String,
    val nombre: String,
    val correo: String,
    val rol: Rol,
    val departamentoId: String?,
)

data class Turno(
    val id: String,
    val usuarioUid: String,
    val entrada: Long,
    val salida: Long? = null,
    val cierreAutomatico: Boolean = false,
)

/** Una llamada de un número a un probador, para la pantalla de turnos. */
data class Llamado(
    val numero: Int,
    val probadorCodigo: String,
    val hora: Long,
    val perdido: Boolean = false,
)

// ---------------------------------------------------------------------- Personal (RF-29 a RF-35)

/** Cambio de departamento de una persona. */
data class Movimiento(
    val id: String,
    val usuarioUid: String,
    val origenId: String?,
    val destinoId: String?,
    val tipo: TipoMovimiento,
    val hora: Long,
    val autorUid: String,
)

enum class EstadoSolicitud(val texto: String) {
    PENDIENTE("Pendiente"),
    ESCALADA("Escalada"),
    ACEPTADA("Aceptada"),
    RECHAZADA("Rechazada"),
    CANCELADA("Cancelada"),
}

/** Solicitud de refuerzo de un departamento a otro (RN-28). */
data class SolicitudRefuerzo(
    val id: String,
    val solicitanteUid: String,
    /** Departamento que pide el refuerzo. */
    val departamentoOrigen: String,
    /** Departamento al que se le pide un vendedor. */
    val departamentoPedido: String,
    val estado: EstadoSolicitud,
    val creada: Long,
    val respuesta: Long? = null,
    val respondidaPor: String? = null,
    val vendedorUid: String? = null,
    val motivo: String? = null,
) {
    val abierta: Boolean get() = estado == EstadoSolicitud.PENDIENTE || estado == EstadoSolicitud.ESCALADA
}

/** Cambio del jefe de un departamento (RN-29). null significa "A cargo del jefe de tienda". */
data class CambioJefatura(
    val id: String,
    val departamentoId: String,
    val anteriorUid: String?,
    val nuevoUid: String?,
    val hora: Long,
    val autorUid: String,
)

// ---------------------------------------------------------------------- Objetos encontrados (RF-26 y RF-27)

enum class TipoObjeto(val texto: String) {
    CELULAR("Celular"),
    BILLETERA("Billetera"),
    LLAVES("Llaves"),
    DOCUMENTO("Documento"),
    BOLSO("Bolso"),
    OTRO("Otro"),
}

enum class EstadoObjeto(val texto: String) {
    GUARDADO("Guardado"),
    ENTREGADO("Entregado"),
    DERIVADO("Derivado"),
}

/** Objeto personal de un cliente encontrado en un probador (RN-23 a RN-25). */
data class ObjetoEncontrado(
    val id: String,
    val probadorId: String,
    val tipo: TipoObjeto,
    val descripcion: String,
    val fotoUri: String?,
    /** Última ocupación terminada del probador al registrar el objeto. */
    val ocupacionId: String?,
    val numero: Int?,
    val registradoPor: String,
    val fecha: Long,
    val estado: EstadoObjeto = EstadoObjeto.GUARDADO,
    val cierre: Long? = null,
    val cerradoPor: String? = null,
)

// ---------------------------------------------------------------------- Configuración del equipo (RF-03)

data class Tienda(
    val id: String,
    val nombre: String,
    val empresa: String,
    val latitud: Double,
    val longitud: Double,
)

enum class UsoEquipo(val texto: String) {
    TABLET_PERSONAL("Tablet del personal"),
    EQUIPO_DEPARTAMENTO("Equipo de departamento"),
}

data class ConfiguracionEquipo(
    val tiendaId: String,
    val uso: UsoEquipo,
    val departamentoId: String? = null,
)

package cl.duoc.probadorapp.domain.rules

import cl.duoc.probadorapp.domain.model.EstadoCliente
import cl.duoc.probadorapp.domain.model.EstadoGuardado
import cl.duoc.probadorapp.domain.model.EstadoNumero
import cl.duoc.probadorapp.domain.model.EstadoVisible
import cl.duoc.probadorapp.domain.model.NumeroAtencion
import cl.duoc.probadorapp.domain.model.Ocupacion
import cl.duoc.probadorapp.domain.model.Probador
import cl.duoc.probadorapp.domain.model.Rol
import cl.duoc.probadorapp.domain.model.TipoAlerta
import kotlin.math.ceil

const val MINUTO_MS = 60_000L

/** Resultado de una entrada informada por el sensor (RN-15 y RN-17). */
enum class ResultadoEntrada { OCUPAR_CON_NUMERO, OCUPAR_SIN_NUMERO, REVISION_DEL_PERSONAL, IGNORAR }

/** Resultado de una salida informada por el sensor (RN-16 y RN-22). */
enum class ResultadoSalida { LIBRE, EN_REVISION_POR_PRENDAS, EN_REVISION_POR_INCIDENCIA }

enum class Marca { ENTRADA, SALIDA }

/** Resultado de validar un movimiento de personal (RN-27). */
enum class ResultadoMovimiento { PERMITIDO, PERMITIDO_CON_ADVERTENCIA, BLOQUEADO_BAJO_MINIMO, NO_AUTORIZADO, MISMO_DEPARTAMENTO }

/**
 * Reglas de negocio de la sección 2.4. Son funciones puras: reciben datos
 * y devuelven un resultado, sin acceder a la base de datos ni a Android.
 */
object Reglas {

    /** RN-07, RN-19 y RN-20: estado que ve el personal. */
    fun estadoVisible(probador: Probador, ocupacion: Ocupacion?, ahora: Long): EstadoVisible = when {
        probador.sinSenal -> EstadoVisible.SIN_SENAL
        probador.estado == EstadoGuardado.OCUPADO && ocupacion != null && excedido(ocupacion, ahora) -> EstadoVisible.EXCEDIDO
        probador.estado == EstadoGuardado.OCUPADO -> EstadoVisible.OCUPADO
        probador.estado == EstadoGuardado.RESERVADO -> EstadoVisible.RESERVADO
        probador.estado == EstadoGuardado.EN_REVISION -> EstadoVisible.EN_REVISION
        else -> EstadoVisible.LIBRE
    }

    /** RN-19: la ocupación alcanzó su tiempo máximo. */
    fun excedido(ocupacion: Ocupacion, ahora: Long): Boolean =
        ocupacion.salida == null && ahora - ocupacion.entrada >= ocupacion.tiempoMaximoMin * MINUTO_MS

    /** RN-08: un probador puede reservarse si está Libre, activo y con señal. */
    fun disponible(probador: Probador): Boolean =
        probador.estado == EstadoGuardado.LIBRE && probador.activo && !probador.sinSenal

    /**
     * RN-11: número en espera más bajo y probador disponible de código más bajo
     * del mismo departamento. Devuelve null si falta alguno de los dos.
     */
    fun elegirAsignacion(
        departamentoId: String,
        numeros: Collection<NumeroAtencion>,
        probadores: Collection<Probador>,
    ): Pair<NumeroAtencion, Probador>? {
        val numero = numeros
            .filter { it.departamentoId == departamentoId && it.estado == EstadoNumero.EN_ESPERA }
            .minByOrNull { it.numero } ?: return null
        val probador = probadores
            .filter { it.departamentoId == departamentoId && disponible(it) }
            .minByOrNull { it.codigo } ?: return null
        return numero to probador
    }

    /** RN-12: venció el plazo del llamado. */
    fun llamadoVencido(numero: NumeroAtencion, ahora: Long, plazoMin: Int): Boolean =
        numero.estado == EstadoNumero.LLAMADO && numero.llamado != null &&
            ahora - numero.llamado >= plazoMin * MINUTO_MS

    /** RN-15, RN-17 y RN-21: qué hacer con una entrada según el estado guardado. */
    fun resultadoEntrada(estado: EstadoGuardado): ResultadoEntrada = when (estado) {
        EstadoGuardado.RESERVADO -> ResultadoEntrada.OCUPAR_CON_NUMERO
        EstadoGuardado.LIBRE -> ResultadoEntrada.OCUPAR_SIN_NUMERO
        EstadoGuardado.EN_REVISION -> ResultadoEntrada.REVISION_DEL_PERSONAL
        EstadoGuardado.OCUPADO -> ResultadoEntrada.IGNORAR
    }

    /** RN-16 y RN-22: estado después de la salida del cliente. */
    fun resultadoSalida(prendasDentro: Int, incidenciasAbiertas: Int): ResultadoSalida = when {
        prendasDentro > 0 -> ResultadoSalida.EN_REVISION_POR_PRENDAS
        incidenciasAbiertas > 0 -> ResultadoSalida.EN_REVISION_POR_INCIDENCIA
        else -> ResultadoSalida.LIBRE
    }

    /** RN-18: un probador En revisión queda Libre sin prendas ni incidencias abiertas. */
    fun liberarRevision(prendasDentro: Int, incidenciasAbiertas: Int): Boolean =
        prendasDentro == 0 && incidenciasAbiertas == 0

    /**
     * RN-22: estado de un probador al reportar una incidencia. Si está Libre o
     * Reservado pasa de inmediato a En revisión; si está Ocupado, sigue Ocupado
     * y pasa a En revisión cuando termina la ocupación.
     */
    fun estadoTrasIncidencia(estado: EstadoGuardado): EstadoGuardado = when (estado) {
        EstadoGuardado.LIBRE, EstadoGuardado.RESERVADO, EstadoGuardado.EN_REVISION -> EstadoGuardado.EN_REVISION
        EstadoGuardado.OCUPADO -> EstadoGuardado.OCUPADO
    }

    /**
     * RN-13: espera estimada en minutos del número en la posición [posicion] (desde 1).
     * Devuelve null cuando no hay probadores útiles ("Sin estimación").
     */
    fun esperaEstimadaMin(posicion: Int, probadoresUtiles: Int, promedioMin: Double?, tiempoMaximoMin: Int): Int? {
        if (probadoresUtiles <= 0) return null
        val t = promedioMin ?: tiempoMaximoMin.toDouble()
        return (ceil(posicion.toDouble() / probadoresUtiles) * t).toInt()
    }

    /** RN-14: estado que ven los clientes. */
    fun estadoCliente(visible: EstadoVisible): EstadoCliente = when (visible) {
        EstadoVisible.LIBRE -> EstadoCliente.LIBRE
        EstadoVisible.RESERVADO -> EstadoCliente.LLAMANDO
        EstadoVisible.OCUPADO, EstadoVisible.EXCEDIDO -> EstadoCliente.OCUPADO
        EstadoVisible.EN_REVISION, EstadoVisible.SIN_SENAL -> EstadoCliente.NO_DISPONIBLE
    }

    /** RN-09: siguiente número del correlativo del día. */
    fun siguienteNumero(ultimo: Int?): Int = (ultimo ?: 0) + 1

    /** Fracción del tiempo máximo transcurrida, entre 0 y 1 (barra del panel). */
    fun avance(entrada: Long, ahora: Long, tiempoMaximoMin: Int): Float {
        val total = tiempoMaximoMin * MINUTO_MS
        if (total <= 0) return 1f
        return ((ahora - entrada).toFloat() / total).coerceIn(0f, 1f)
    }

    /** RN-32: una marca solo es válida según si la persona tiene un turno abierto. */
    fun marcaPermitida(tieneTurnoAbierto: Boolean, marca: Marca): Boolean =
        if (marca == Marca.ENTRADA) !tieneTurnoAbierto else tieneTurnoAbierto

    /** RN-32: la cobertura cumple el mínimo del departamento. */
    fun cumpleMinimo(vendedoresEnTurno: Int, minimo: Int): Boolean = vendedoresEnTurno >= minimo

    /**
     * RN-27: valida que [rolActor] mueva a una persona de [origen] a [destino].
     * [coberturaOrigenDespues] es la cobertura que quedaría en el origen.
     */
    fun movimientoPermitido(
        rolActor: Rol,
        departamentoActor: String?,
        origen: String,
        destino: String,
        coberturaOrigenDespues: Int,
        minimoOrigen: Int,
    ): ResultadoMovimiento {
        if (origen == destino) return ResultadoMovimiento.MISMO_DEPARTAMENTO
        val bajoMinimo = coberturaOrigenDespues < minimoOrigen
        return when (rolActor) {
            Rol.VENDEDOR -> ResultadoMovimiento.NO_AUTORIZADO
            Rol.JEFE_DEPARTAMENTO -> when {
                departamentoActor != origen -> ResultadoMovimiento.NO_AUTORIZADO
                bajoMinimo -> ResultadoMovimiento.BLOQUEADO_BAJO_MINIMO
                else -> ResultadoMovimiento.PERMITIDO
            }
            Rol.JEFE_TIENDA -> if (bajoMinimo) ResultadoMovimiento.PERMITIDO_CON_ADVERTENCIA else ResultadoMovimiento.PERMITIDO
        }
    }

    /** RN-28: una solicitud pendiente se escala a los 10 minutos. */
    fun solicitudVencida(creada: Long, ahora: Long): Boolean = ahora - creada >= 10 * MINUTO_MS

    /**
     * RN-29: departamentos que quedarían sin jefe. [jefes] relaciona cada
     * departamento con su jefe; null significa que queda a cargo del jefe de tienda.
     * Devuelve también los jefes asignados a más de un departamento.
     */
    fun revisarJefaturas(jefes: Map<String, String?>, aCargoDelJefeDeTienda: Set<String>): Pair<List<String>, List<String>> {
        val sinJefe = jefes.filter { (dep, jefe) -> jefe == null && dep !in aCargoDelJefeDeTienda }.keys.sorted()
        val repetidos = jefes.values.filterNotNull().groupBy { it }.filter { it.value.size > 1 }.keys.sorted()
        return sinJefe to repetidos
    }

    /** RN-31: personas que reciben una alerta. */
    fun destinatarios(
        tipo: TipoAlerta,
        vendedoresEnTurno: List<String>,
        jefeDepartamento: String?,
        jefeTienda: String?,
        escalada: Boolean = false,
    ): List<String> = when (tipo) {
        TipoAlerta.TIEMPO_EXCEDIDO, TipoAlerta.PRENDA_OLVIDADA, TipoAlerta.SIN_SENAL ->
            (vendedoresEnTurno + listOfNotNull(jefeDepartamento)).distinct()
        TipoAlerta.COBERTURA_BAJA -> listOfNotNull(jefeDepartamento, jefeTienda).distinct()
        TipoAlerta.SOLICITUD_REFUERZO ->
            if (escalada) listOfNotNull(jefeTienda) else listOfNotNull(jefeDepartamento, jefeTienda).distinct()
    }
}

/**
 * RN-29: pasos del cambio de jefatura. [asignaciones] relaciona cada departamento
 * con su jefe; null significa que el departamento no tiene jefe por ahora.
 */
object Jefaturas {

    /** Asigna [jefe] a [departamento]; si ese jefe dirigía otro departamento, ese queda vacío. */
    fun asignar(asignaciones: Map<String, String?>, departamento: String, jefe: String?): Map<String, String?> =
        asignaciones.mapValues { (dep, actual) ->
            when {
                dep == departamento -> jefe
                jefe != null && actual == jefe -> null
                else -> actual
            }
        }

    /** Departamentos sin jefe que todavía no quedaron a cargo del jefe de tienda. */
    fun vacantes(asignaciones: Map<String, String?>, aCargoDelJefeDeTienda: Set<String>): List<String> =
        asignaciones.filter { (dep, jefe) -> jefe == null && dep !in aCargoDelJefeDeTienda }.keys.sorted()

    /** Jefes de departamento que quedaron sin departamento. */
    fun sinAsignar(asignaciones: Map<String, String?>, jefes: List<String>): List<String> =
        jefes.filter { it !in asignaciones.values }

    /**
     * Un departamento vacío solo puede quedar a cargo del jefe de tienda cuando no
     * queda ningún jefe sin departamento.
     */
    fun puedeQuedarACargoDelJefeDeTienda(asignaciones: Map<String, String?>, jefes: List<String>): Boolean =
        sinAsignar(asignaciones, jefes).isEmpty()
}

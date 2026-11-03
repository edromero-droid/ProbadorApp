package cl.duoc.probadorapp.data.repository

import cl.duoc.probadorapp.domain.model.EstadoGuardado
import cl.duoc.probadorapp.domain.model.EstadoNumero
import cl.duoc.probadorapp.domain.model.Incidencia
import cl.duoc.probadorapp.domain.model.NumeroAtencion
import cl.duoc.probadorapp.domain.model.Ocupacion
import cl.duoc.probadorapp.domain.model.Turno
import cl.duoc.probadorapp.domain.model.CambioJefatura
import cl.duoc.probadorapp.domain.model.Movimiento
import cl.duoc.probadorapp.domain.model.ObjetoEncontrado
import cl.duoc.probadorapp.domain.model.Probador
import cl.duoc.probadorapp.domain.model.SolicitudRefuerzo

/** Registros del día guardados en la base de datos local (Room). */
data class RegistrosGuardados(
    val numeros: List<NumeroAtencion> = emptyList(),
    val ocupaciones: List<Ocupacion> = emptyList(),
    val incidencias: List<Incidencia> = emptyList(),
    val turnos: List<Turno> = emptyList(),
    val objetos: List<ObjetoEncontrado> = emptyList(),
    val movimientos: List<Movimiento> = emptyList(),
    val solicitudes: List<SolicitudRefuerzo> = emptyList(),
    val jefaturas: List<CambioJefatura> = emptyList(),
    /** Departamento actual de cada persona (tabla usuarios). */
    val asignaciones: Map<String, String?> = emptyMap(),
    /** Jefe actual de cada departamento; null es "a cargo del jefe de tienda". */
    val jefes: Map<String, String?> = emptyMap(),
    /** Configuración de los probadores (F-11): códigos y si está activo. */
    val probadores: List<Probador> = emptyList(),
)

/** Cambios entre dos versiones de una colección: qué guardar y qué borrar. */
data class Cambios<T>(val guardar: List<T>, val borrar: List<String>) {
    val vacio: Boolean get() = guardar.isEmpty() && borrar.isEmpty()
}

/**
 * Funciones puras para guardar y recuperar los registros del día.
 * No dependen de Android, por eso tienen pruebas unitarias.
 */
object Restauracion {

    /**
     * Compara dos versiones de una colección. Como los modelos son inmutables,
     * un registro que no cambió conserva la misma instancia.
     */
    fun <T> cambios(anterior: Map<String, T>, actual: Map<String, T>): Cambios<T> {
        if (anterior === actual) return Cambios(emptyList(), emptyList())
        val guardar = actual.filter { (id, v) -> anterior[id] !== v }.values.toList()
        val borrar = anterior.keys.filter { it !in actual }
        return Cambios(guardar, borrar)
    }

    /**
     * Agrega al estado inicial los registros guardados al reiniciar la aplicación:
     * - Los números de hoy continúan el correlativo (RN-09). Un número que estaba
     *   Llamado vuelve a la espera, porque los probadores parten sin reserva.
     * - Las ocupaciones de hoy se conservan; una que seguía abierta se cierra a la
     *   hora del reinicio.
     * - Las incidencias se conservan, y un probador con una incidencia abierta
     *   queda En revisión (RN-22).
     * - Los turnos guardados (marcas de F-02) reemplazan a los iniciales.
     * - Se recuperan los objetos guardados, las solicitudes abiertas, los movimientos
     *   y jefaturas del día, el departamento de cada persona, el jefe de cada
     *   departamento y la configuración de los probadores.
     */
    fun aplicar(e: EstadoTienda, g: RegistrosGuardados, ahora: Long, hoy: String, inicioDelDia: Long): EstadoTienda {
        val numeros = g.numeros.filter { it.fecha == hoy }.map {
            if (it.estado == EstadoNumero.LLAMADO) it.copy(estado = EstadoNumero.EN_ESPERA, probadorId = null, llamado = null) else it
        }
        val contadores = numeros.groupBy { it.departamentoId }
            .map { (dep, lista) -> "$dep|$hoy" to lista.maxOf { it.numero } }.toMap()
        val ocupaciones = g.ocupaciones.filter { it.entrada >= inicioDelDia }.map {
            if (it.salida == null) it.copy(salida = ahora, alertaEmitida = true) else it
        }
        val incidencias = g.incidencias.associateBy { it.id }
        val configuracion = g.probadores.associateBy { it.id }
        val conIncidencia = g.incidencias.filter { it.abierta }.map { it.probadorId }.toSet()
        return e.copy(
            numeros = e.numeros + numeros.associateBy { it.id },
            ocupaciones = e.ocupaciones + ocupaciones.associateBy { it.id },
            incidencias = e.incidencias + incidencias,
            contadores = e.contadores + contadores,
            turnos = if (g.turnos.isEmpty()) e.turnos else g.turnos.associateBy { it.id },
            probadores = e.probadores.mapValues { (id, p) ->
                val config = configuracion[id]
                val base = if (config != null) p.copy(sensorCodigo = config.sensorCodigo, lectorCodigo = config.lectorCodigo, activo = config.activo) else p
                if (id in conIncidencia) base.copy(estado = EstadoGuardado.EN_REVISION) else base
            },
            objetos = e.objetos + g.objetos.associateBy { it.id },
            movimientos = e.movimientos + g.movimientos.filter { it.hora >= inicioDelDia }.associateBy { it.id },
            solicitudes = e.solicitudes + g.solicitudes.filter { it.abierta || it.creada >= inicioDelDia }.associateBy { it.id },
            jefaturas = e.jefaturas + g.jefaturas.filter { it.hora >= inicioDelDia }.associateBy { it.id },
            usuarios = e.usuarios.mapValues { (uid, u) -> if (uid in g.asignaciones) u.copy(departamentoId = g.asignaciones[uid]) else u },
            departamentos = e.departamentos.mapValues { (id, d) -> if (id in g.jefes) d.copy(jefeUid = g.jefes[id]) else d },
        )
    }
}

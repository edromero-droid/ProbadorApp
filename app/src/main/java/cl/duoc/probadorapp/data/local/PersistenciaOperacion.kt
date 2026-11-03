package cl.duoc.probadorapp.data.local

import cl.duoc.probadorapp.data.repository.EstadoTienda
import cl.duoc.probadorapp.data.repository.RegistrosGuardados
import cl.duoc.probadorapp.data.repository.RepositorioTienda
import cl.duoc.probadorapp.data.repository.Restauracion
import cl.duoc.probadorapp.domain.model.Prenda
import cl.duoc.probadorapp.domain.model.Turno
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch

/**
 * Guarda en SQLite (Room) los datos de cada formulario y de la operación cada vez
 * que cambian, cada uno con su DAO, y los recupera al abrir la aplicación.
 * En la etapa con Firebase, esta copia local sirve ante un corte breve (RN-33).
 */
class PersistenciaOperacion(private val db: BaseDatosLocal) {

    /** Lee los registros vigentes y borra los de días anteriores que ya no se usan. */
    suspend fun cargar(hoy: String, inicioDelDia: Long, catalogo: List<Prenda>, turnosIniciales: Collection<Turno>): RegistrosGuardados {
        db.numeroDao().borrarAnteriores(hoy)
        db.ocupacionDao().borrarAnteriores(inicioDelDia)
        // La primera vez se guardan los turnos de los datos de prueba.
        if (db.turnoDao().contar() == 0) db.turnoDao().guardar(turnosIniciales.map { it.aEntidad() })
        val prendas = catalogo.associateBy { it.epc }
        return RegistrosGuardados(
            numeros = db.numeroDao().delDia(hoy).map { it.aModelo() },
            ocupaciones = db.ocupacionDao().desde(inicioDelDia).map { it.aModelo(prendas) },
            incidencias = db.incidenciaDao().todas().map { it.aModelo() },
            turnos = db.turnoDao().vigentes(inicioDelDia).map { it.aModelo() },
            objetos = db.objetoDao().todos().map { it.aModelo() },
            movimientos = db.movimientoDao().entre(inicioDelDia, Long.MAX_VALUE).map { it.aModelo() },
            solicitudes = db.solicitudDao().vigentes(inicioDelDia).map { it.aModelo() },
            jefaturas = db.jefaturaDao().entre(inicioDelDia, Long.MAX_VALUE).map { it.aModelo() },
            asignaciones = db.usuarioDao().todos().associate { it.uid to it.departamentoId },
            jefes = db.jefaturaDao().jefes().associate { it.departamentoId to it.jefeUid },
            probadores = db.probadorDao().todos().map { it.aModelo() },
        )
    }

    /**
     * Observa el estado de la tienda y guarda solo lo que cambió. Se inicia
     * después de recuperar los registros, para no borrar lo guardado.
     */
    fun sincronizar(scope: CoroutineScope, repo: RepositorioTienda): Job = scope.launch {
        var anterior: EstadoTienda = repo.estado.value
        repo.estado.collect { actual ->
            guardarCambios(anterior, actual)
            anterior = actual
        }
    }

    private suspend fun guardarCambios(a: EstadoTienda, b: EstadoTienda) {
        // F-02 Marcar turno
        val turnos = Restauracion.cambios(a.turnos, b.turnos)
        if (turnos.guardar.isNotEmpty()) db.turnoDao().guardar(turnos.guardar.map { it.aEntidad() })
        turnos.borrar.chunked(500).forEach { db.turnoDao().borrar(it) }

        // F-04 Reportar incidencia
        val incidencias = Restauracion.cambios(a.incidencias, b.incidencias)
        if (incidencias.guardar.isNotEmpty()) db.incidenciaDao().guardar(incidencias.guardar.map { it.aEntidad() })
        incidencias.borrar.chunked(500).forEach { db.incidenciaDao().borrar(it) }

        // F-05 Registrar objeto encontrado
        val objetos = Restauracion.cambios(a.objetos, b.objetos)
        if (objetos.guardar.isNotEmpty()) db.objetoDao().guardar(objetos.guardar.map { it.aEntidad() })
        objetos.borrar.chunked(500).forEach { db.objetoDao().borrar(it) }

        // F-06 Mover persona: movimiento y departamento asignado
        val movimientos = Restauracion.cambios(a.movimientos, b.movimientos)
        if (movimientos.guardar.isNotEmpty()) db.movimientoDao().guardar(movimientos.guardar.map { it.aEntidad() })
        for (u in b.usuarios.values) {
            if (a.usuarios[u.uid]?.departamentoId != u.departamentoId) db.usuarioDao().actualizarDepartamento(u.uid, u.departamentoId)
        }

        // F-07 y F-08 Solicitudes de refuerzo
        val solicitudes = Restauracion.cambios(a.solicitudes, b.solicitudes)
        if (solicitudes.guardar.isNotEmpty()) db.solicitudDao().guardar(solicitudes.guardar.map { it.aEntidad() })

        // F-09 Cambio de jefatura
        val jefaturas = Restauracion.cambios(a.jefaturas, b.jefaturas)
        if (jefaturas.guardar.isNotEmpty()) db.jefaturaDao().guardar(jefaturas.guardar.map { it.aEntidad() })
        val jefes = b.departamentos.values.filter { a.departamentos[it.id]?.jefeUid != it.jefeUid }
        if (jefes.isNotEmpty()) db.jefaturaDao().guardarJefes(jefes.map { JefeActualEntity(it.id, it.jefeUid) })

        // F-11 Configurar probador
        val probadores = b.probadores.values.filter { cambioConfiguracion(a.probadores[it.id], it) }
        if (probadores.isNotEmpty()) db.probadorDao().guardar(probadores.map { it.aConfiguracion() })

        // Cola y ocupaciones
        val numeros = Restauracion.cambios(a.numeros, b.numeros)
        if (numeros.guardar.isNotEmpty()) db.numeroDao().guardar(numeros.guardar.map { it.aEntidad() })
        numeros.borrar.chunked(500).forEach { db.numeroDao().borrar(it) }

        val ocupaciones = Restauracion.cambios(a.ocupaciones, b.ocupaciones)
        if (ocupaciones.guardar.isNotEmpty()) db.ocupacionDao().guardar(ocupaciones.guardar.map { it.aEntidad() })
        ocupaciones.borrar.chunked(500).forEach { db.ocupacionDao().borrar(it) }
    }
}

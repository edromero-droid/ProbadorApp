package cl.duoc.probadorapp.data.local

import androidx.room.Dao
import androidx.room.Entity
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.PrimaryKey
import androidx.room.Query
import cl.duoc.probadorapp.domain.model.Canal
import cl.duoc.probadorapp.domain.model.EstadoNumero
import cl.duoc.probadorapp.domain.model.NumeroAtencion
import cl.duoc.probadorapp.domain.model.Ocupacion
import cl.duoc.probadorapp.domain.model.Prenda
import cl.duoc.probadorapp.domain.model.PrendaDetectada

// Datos que genera la operación, no un formulario: números de la cola y ocupaciones.

/** Tabla `numeros`: la cola del día. */
@Entity(tableName = "numeros")
data class NumeroEntity(
    @PrimaryKey val id: String,
    val departamentoId: String,
    val numero: Int,
    val fecha: String,
    val canal: String,
    val estado: String,
    val emitido: Long,
    val probadorId: String?,
    val llamado: Long?,
    val cierre: Long?,
    val clienteUid: String?,
    val entregadoPor: String?,
)

@Dao
interface NumeroDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun guardar(numeros: List<NumeroEntity>)

    @Query("DELETE FROM numeros WHERE id IN (:ids)")
    suspend fun borrar(ids: List<String>)

    @Query("SELECT * FROM numeros WHERE fecha = :fecha ORDER BY numero")
    suspend fun delDia(fecha: String): List<NumeroEntity>

    /** El correlativo se reinicia cada día: los números anteriores ya no se usan. */
    @Query("DELETE FROM numeros WHERE fecha < :fecha")
    suspend fun borrarAnteriores(fecha: String)
}

/** Tabla `ocupaciones`: entradas y salidas de los probadores con sus prendas. */
@Entity(tableName = "ocupaciones")
data class OcupacionEntity(
    @PrimaryKey val id: String,
    val probadorId: String,
    val numeroId: String?,
    val entrada: Long,
    val salida: Long?,
    val tiempoMaximoMin: Int,
    val alertaEmitida: Boolean,
    /** Prendas detectadas como "epc@detectada@retirada" separadas por ";". */
    val prendas: String,
)

@Dao
interface OcupacionDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun guardar(ocupaciones: List<OcupacionEntity>)

    @Query("DELETE FROM ocupaciones WHERE id IN (:ids)")
    suspend fun borrar(ids: List<String>)

    @Query("SELECT * FROM ocupaciones WHERE entrada >= :desde ORDER BY entrada")
    suspend fun desde(desde: Long): List<OcupacionEntity>

    @Query("DELETE FROM ocupaciones WHERE entrada < :desde")
    suspend fun borrarAnteriores(desde: Long)
}

fun NumeroAtencion.aEntidad() = NumeroEntity(
    id, departamentoId, numero, fecha, canal.name, estado.name, emitido, probadorId, llamado, cierre, clienteUid, entregadoPor,
)

fun NumeroEntity.aModelo() = NumeroAtencion(
    id = id, departamentoId = departamentoId, numero = numero, fecha = fecha,
    canal = Canal.valueOf(canal), estado = EstadoNumero.valueOf(estado), emitido = emitido,
    probadorId = probadorId, llamado = llamado, cierre = cierre, clienteUid = clienteUid, entregadoPor = entregadoPor,
)

fun Ocupacion.aEntidad() = OcupacionEntity(
    id, probadorId, numeroId, entrada, salida, tiempoMaximoMin, alertaEmitida,
    prendas.joinToString(";") { "${it.prenda.epc}@${it.detectada}@${it.retirada ?: ""}" },
)

fun OcupacionEntity.aModelo(catalogo: Map<String, Prenda>) = Ocupacion(
    id = id, probadorId = probadorId, numeroId = numeroId, entrada = entrada, salida = salida,
    tiempoMaximoMin = tiempoMaximoMin, alertaEmitida = alertaEmitida,
    prendas = prendas.split(";").filter { it.isNotBlank() }.map { texto ->
        val partes = texto.split("@")
        val epc = partes[0]
        PrendaDetectada(
            prenda = catalogo[epc] ?: Prenda(epc, "Prenda", "-", "-"),
            detectada = partes.getOrNull(1)?.toLongOrNull() ?: entrada,
            retirada = partes.getOrNull(2)?.toLongOrNull(),
        )
    },
)

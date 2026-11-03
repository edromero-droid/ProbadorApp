package cl.duoc.probadorapp.data.repository

import cl.duoc.probadorapp.domain.model.Departamento
import cl.duoc.probadorapp.domain.model.Prenda
import cl.duoc.probadorapp.domain.model.Probador
import cl.duoc.probadorapp.domain.model.Rol
import cl.duoc.probadorapp.domain.model.Tienda
import cl.duoc.probadorapp.domain.model.Turno
import cl.duoc.probadorapp.domain.model.Usuario

/**
 * Datos ficticios de prueba (sección 2.7): Sucursal Norte de Tienda
 * Departamental Rincón Sur, con sus tres departamentos y su personal.
 */
object DatosIniciales {

    const val CONTRASENA_DEMO = "Rincon2026"

    val departamentos = listOf(
        Departamento("mujer", "Mujer", 0xFF6A1B9A, minimoPersonal = 2, jefeUid = "valentina"),
        Departamento("hombre", "Hombre", 0xFF00695C, minimoPersonal = 2, jefeUid = "matias"),
        Departamento("nino", "Niño", 0xFFE65100, minimoPersonal = 1, jefeUid = "fernanda"),
    )

    /** Mujer: P-01 a P-08 (PS-01 a PS-08); Hombre: P-01 a P-06 (PS-09 a PS-14); Niño: P-01 a P-04 (PS-15 a PS-18). */
    val probadores: List<Probador> = buildList {
        var n = 1
        for ((dep, cantidad) in listOf("mujer" to 8, "hombre" to 6, "nino" to 4)) {
            for (i in 1..cantidad) {
                val cod = "%02d".format(n)
                add(Probador(id = "$dep-$i", departamentoId = dep, codigo = "P-%02d".format(i), sensorCodigo = "PS-$cod", lectorCodigo = "LR-$cod"))
                n++
            }
        }
    }

    val usuarios = listOf(
        Usuario("ana", "Ana Pérez", "ana.perez@rinconsur.test", Rol.JEFE_TIENDA, null),
        Usuario("valentina", "Valentina Rojas", "valentina.rojas@rinconsur.test", Rol.JEFE_DEPARTAMENTO, "mujer"),
        Usuario("matias", "Matías González", "matias.gonzalez@rinconsur.test", Rol.JEFE_DEPARTAMENTO, "hombre"),
        Usuario("fernanda", "Fernanda Silva", "fernanda.silva@rinconsur.test", Rol.JEFE_DEPARTAMENTO, "nino"),
        Usuario("camila", "Camila Muñoz", "camila.munoz@rinconsur.test", Rol.VENDEDOR, "mujer"),
        Usuario("javiera", "Javiera Soto", "javiera.soto@rinconsur.test", Rol.VENDEDOR, "mujer"),
        Usuario("daniela", "Daniela Reyes", "daniela.reyes@rinconsur.test", Rol.VENDEDOR, "mujer"),
        Usuario("sebastian", "Sebastián Lagos", "sebastian.lagos@rinconsur.test", Rol.VENDEDOR, "hombre"),
        Usuario("diego", "Diego Torres", "diego.torres@rinconsur.test", Rol.VENDEDOR, "hombre"),
        Usuario("tomas", "Tomás Vargas", "tomas.vargas@rinconsur.test", Rol.VENDEDOR, "nino"),
        Usuario("isidora", "Isidora Castro", "isidora.castro@rinconsur.test", Rol.VENDEDOR, "nino"),
    )

    /**
     * Tiendas del grupo (sección 2.7). Las coordenadas son ficticias, en Santiago;
     * solo Sucursal Norte tiene datos de demostración.
     */
    val tiendas = listOf(
        Tienda("norte", "Sucursal Norte", "Tienda Departamental Rincón Sur", -33.3700, -70.6350),
        Tienda("centro", "Sucursal Centro", "Tienda Departamental Rincón Sur", -33.4400, -70.6500),
        Tienda("sur", "Sucursal Sur", "Tienda Departamental Rincón Sur", -33.5200, -70.6000),
        Tienda("hiper-norte", "Hiper Norte", "Hipermercado Rincón Sur", -33.3900, -70.6200),
    )

    /** Personas en turno al iniciar la demostración. */
    private val enTurno = listOf("ana", "valentina", "matias", "fernanda", "camila", "javiera", "daniela", "sebastian", "tomas")

    fun estado(ahora: Long): EstadoTienda = EstadoTienda(
        departamentos = departamentos.associateBy { it.id },
        probadores = probadores.associateBy { it.id },
        usuarios = usuarios.associateBy { it.uid },
        turnos = enTurno.associate { "turno-$it" to Turno("turno-$it", it, entrada = ahora - 2 * 60 * 60_000L) },
    )

    /** Catálogo de 40 prendas de muestra con su código RFID. */
    val catalogo: List<Prenda> = buildList {
        val tipos = listOf("Pantalón", "Polera", "Camisa", "Vestido", "Chaqueta", "Falda", "Polerón", "Jeans")
        val colores = listOf("azul", "negro", "blanco", "rojo", "verde")
        val tallas = listOf("S", "M", "L", "XL", "38", "40", "42", "44")
        var i = 0
        for (t in tipos) for (c in colores) {
            add(Prenda(epc = "E280%08X".format(0x0A00 + i * 0x3F), descripcion = "$t $c", talla = tallas[i % tallas.size], color = c))
            i++
        }
    }
}

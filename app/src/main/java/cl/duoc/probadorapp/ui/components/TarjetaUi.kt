package cl.duoc.probadorapp.ui.components

import cl.duoc.probadorapp.domain.model.EstadoVisible

/** Datos que muestra la tarjeta de un probador en el panel. */
data class TarjetaUi(
    val id: String,
    val codigo: String,
    val estado: EstadoVisible,
    val principal: String? = null,
    val linea1: String? = null,
    val linea2: String? = null,
    val avance: Float? = null,
)

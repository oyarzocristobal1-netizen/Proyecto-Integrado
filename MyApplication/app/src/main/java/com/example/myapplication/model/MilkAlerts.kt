package com.example.myapplication.model

/** Severidad de una alerta (campo "severidad" en Firestore). */
enum class AlertSeverity(val id: String, val label: String) {
    MEDIA("media", "Media"),
    ALTA("alta", "Alta");

    companion object {
        fun fromId(id: String?): AlertSeverity = entries.firstOrNull { it.id == id } ?: MEDIA
    }
}

/** Alerta detectada en una lectura, antes de guardarla. */
data class AlertCandidate(
    /** temperatura, ph, conductividad o turbidez (campo "tipo") */
    val type: String,
    val severity: AlertSeverity,
    val value: Double,
    val message: String
)

/**
 * Reglas de alerta (RF5: generar una alerta cuando un valor sale de rango).
 * Usa los mismos rangos que la pantalla y la evaluacion de calidad.
 */
fun detectAlerts(reading: MilkQualityReading): List<AlertCandidate> {
    val alerts = mutableListOf<AlertCandidate>()
    val t = reading.temperature
    val ph = reading.ph
    val cond = reading.conductivity
    val turb = reading.turbidity

    if (t > TEMP_COLD_MAX) {
        alerts += AlertCandidate(
            type = "temperatura",
            severity = if (t > TEMP_AMBIENT_MAX) AlertSeverity.ALTA else AlertSeverity.MEDIA,
            value = t.toDouble(),
            message = "Leche a %.1f°C: sobre 4°C, hay que enfriarla.".format(t)
        )
    }
    if (ph !in 6.6f..6.8f) {
        val severe = ph < 6.4f || ph > 7.0f
        alerts += AlertCandidate(
            type = "ph",
            severity = if (severe) AlertSeverity.ALTA else AlertSeverity.MEDIA,
            value = ph.toDouble(),
            message = if (ph < 6.6f) {
                "pH %.2f (ácido): posible carga bacteriana.".format(ph)
            } else {
                "pH %.2f (alcalino): posible mastitis o adulteración.".format(ph)
            }
        )
    }
    if (cond !in 4.0f..6.0f) {
        val severe = cond > 6.2f || cond < 3.5f
        alerts += AlertCandidate(
            type = "conductividad",
            severity = if (severe) AlertSeverity.ALTA else AlertSeverity.MEDIA,
            value = cond.toDouble(),
            message = if (cond > 6.0f) {
                "Conductividad %.2f mS/cm (alta): posible mastitis.".format(cond)
            } else {
                "Conductividad %.2f mS/cm (baja): posible dilución.".format(cond)
            }
        )
    }
    if (turb < 50.0f) {
        alerts += AlertCandidate(
            type = "turbidez",
            severity = AlertSeverity.ALTA,
            value = turb.toDouble(),
            message = "Turbidez %.0f%% (baja): posible aguado.".format(turb)
        )
    }
    return alerts
}

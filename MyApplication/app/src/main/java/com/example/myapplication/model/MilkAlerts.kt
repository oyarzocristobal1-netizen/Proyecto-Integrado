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
 * Usa los rangos vigentes (configuracion/rangos), los mismos de la pantalla y la evaluacion.
 */
fun detectAlerts(
    reading: MilkQualityReading,
    ranges: QualityRanges = QualityRangesHolder.current
): List<AlertCandidate> {
    val alerts = mutableListOf<AlertCandidate>()
    val r = ranges
    val t = reading.temperature
    val ph = reading.ph
    val cond = reading.conductivity
    val turb = reading.turbidity

    if (t > r.tempMax) {
        alerts += AlertCandidate(
            type = "temperatura",
            severity = if (t > TEMP_AMBIENT_MAX) AlertSeverity.ALTA else AlertSeverity.MEDIA,
            value = t.toDouble(),
            message = "Leche a %.1f°C: sobre %.1f°C, hay que enfriarla.".format(t, r.tempMax)
        )
    }
    if (ph !in r.phMin..r.phMax) {
        val severe = ph < r.phMin - 0.2f || ph > r.phMax + 0.2f
        alerts += AlertCandidate(
            type = "ph",
            severity = if (severe) AlertSeverity.ALTA else AlertSeverity.MEDIA,
            value = ph.toDouble(),
            message = if (ph < r.phMin) {
                "pH %.2f (ácido): posible carga bacteriana.".format(ph)
            } else {
                "pH %.2f (alcalino): posible mastitis o adulteración.".format(ph)
            }
        )
    }
    if (cond !in r.condMin..r.condMax) {
        val severe = cond > r.condMax + 0.7f || cond < r.condMin - 0.5f
        alerts += AlertCandidate(
            type = "conductividad",
            severity = if (severe) AlertSeverity.ALTA else AlertSeverity.MEDIA,
            value = cond.toDouble(),
            message = if (cond > r.condMax) {
                "Conductividad %.2f mS/cm (alta): posible mastitis.".format(cond)
            } else {
                "Conductividad %.2f mS/cm (baja): posible dilución.".format(cond)
            }
        )
    }
    if (turb < r.turbMin) {
        alerts += AlertCandidate(
            type = "turbidez",
            severity = AlertSeverity.ALTA,
            value = turb.toDouble(),
            message = "Turbidez %.0f%% (baja): posible aguado.".format(turb)
        )
    }
    return alerts
}

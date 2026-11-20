package com.example.myapplication.model

import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone

/** Datos minimos de una medicion para supervisar y comparar (independiente de Firebase). */
data class SupervisionPoint(
    val milkerName: String,
    val lotId: String,
    val epochMillis: Long,
    /** verde, ambar o rojo */
    val status: String,
    val simulated: Boolean,
    val temperature: Double,
    val ph: Double,
    val conductivity: Double,
    val turbidity: Double
)

/** Resumen de un grupo (un lechero o un estanque) para comparar. */
data class GroupStats(
    val name: String,
    val count: Int,
    /** Porcentaje de mediciones en verde (conformes). */
    val compliantPercent: Double,
    val lastMillis: Long,
    val avgTemperature: Double,
    val avgPh: Double,
    val avgConductivity: Double,
    val avgTurbidity: Double,
    val simulatedCount: Int
)

/** Comparativas y actividad para el encargado. Calculos puros, se prueban sin Android. */
object SupervisionStats {

    /** Agrupa por lo que devuelva [keyOf] (nombre del lechero, estanque...) y ordena por cantidad de mediciones. */
    fun groupBy(points: List<SupervisionPoint>, keyOf: (SupervisionPoint) -> String): List<GroupStats> =
        points.groupBy { keyOf(it).ifBlank { "Sin nombre" } }
            .map { (name, list) ->
                GroupStats(
                    name = name,
                    count = list.size,
                    compliantPercent = list.count { it.status == "verde" } * 100.0 / list.size,
                    lastMillis = list.maxOf { it.epochMillis },
                    avgTemperature = list.map { it.temperature }.average(),
                    avgPh = list.map { it.ph }.average(),
                    avgConductivity = list.map { it.conductivity }.average(),
                    avgTurbidity = list.map { it.turbidity }.average(),
                    simulatedCount = list.count { it.simulated }
                )
            }
            .sortedWith(compareByDescending<GroupStats> { it.count }.thenBy { it.name.lowercase() })

    /** Mediciones desde [fromMillis] (inclusive), por ejemplo desde el inicio del dia. */
    fun since(points: List<SupervisionPoint>, fromMillis: Long): List<SupervisionPoint> =
        points.filter { it.epochMillis >= fromMillis }
}

/** Una linea del resumen diario: un lote. */
data class DailyLotLine(
    val tank: String,
    val count: Int,
    /** Veredicto del analisis de la ultima medicion, o null si no hay. */
    val verdictLabel: String?,
    val lastTimeText: String?,
    val pendingAlerts: Int,
    val saleText: String?
)

/** Texto del resumen del dia, listo para compartir (por ejemplo por WhatsApp). */
object DailySummary {

    fun build(
        farm: String,
        date: Date,
        lots: List<DailyLotLine>,
        totalCount: Int,
        compliantPercent: Double?,
        pendingAlerts: Int,
        simulatedCount: Int,
        generatedBy: String,
        timeZone: TimeZone = TimeZone.getDefault()
    ): String {
        val day = SimpleDateFormat("dd/MM/yyyy", Locale.US).apply { this.timeZone = timeZone }.format(date)
        return buildString {
            appendLine("Resumen del día $day - $farm")
            appendLine()
            if (totalCount == 0) {
                appendLine("Hoy no hay mediciones registradas.")
            } else {
                append("Mediciones: $totalCount")
                compliantPercent?.let { append(" · Conformes: ${"%.0f".format(it)} %") }
                appendLine()
                appendLine("Alertas pendientes: $pendingAlerts")
                appendLine()
                lots.forEach { l ->
                    appendLine("• ${l.tank}: ${l.count} medición(es)")
                    val parts = listOfNotNull(
                        l.verdictLabel?.let { "Último análisis: $it" },
                        l.lastTimeText?.let { "última medición $it" },
                        if (l.pendingAlerts > 0) "${l.pendingAlerts} alerta(s) pendiente(s)" else null,
                        l.saleText
                    )
                    if (parts.isNotEmpty()) appendLine("   " + parts.joinToString(" · "))
                }
            }
            if (simulatedCount > 0) {
                appendLine()
                appendLine(
                    "AVISO: incluye $simulatedCount medición(es) SIMULADA(S) (modo DEMO). " +
                        "No son mediciones reales de un sensor."
                )
            }
            appendLine()
            append("Generado por $generatedBy desde la app de calidad de la leche.")
        }
    }
}

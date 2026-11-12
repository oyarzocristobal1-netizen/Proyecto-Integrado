package com.example.myapplication.model

import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Model representing a sensor reading for Milk Quality evaluation.
 */
data class MilkQualityReading(
    val temperature: Float = 0.0f,    // °C
    val ph: Float = 0.0f,             // Standard pH units
    val conductivity: Float = 0.0f,   // mS/cm
    val turbidity: Float = 0.0f,      // %
    val timestamp: Long = System.currentTimeMillis()
) {
    val formattedTime: String
        get() = SimpleDateFormat("HH:mm:ss", Locale.getDefault()).format(Date(timestamp))

    val formattedDate: String
        get() = SimpleDateFormat("dd/MM/yyyy HH:mm", Locale.getDefault()).format(Date(timestamp))

    val phStatus: PhStatus
        get() {
            val r = QualityRangesHolder.current
            return when {
                ph in r.phMin..r.phMax -> PhStatus.NORMAL
                ph < r.phMin -> PhStatus.ACIDIC
                else -> PhStatus.ALKALINE
            }
        }

    val temperatureStatus: TemperatureStatus
        get() = when {
            temperature <= QualityRangesHolder.current.tempMax -> TemperatureStatus.COLD
            temperature <= TEMP_AMBIENT_MAX -> TemperatureStatus.AMBIENT
            else -> TemperatureStatus.HIGH
        }

    val overallAssessment: MilkQualityAssessment
        get() = evaluateQuality(ph, conductivity, turbidity, temperature)
}

/** Estado de la temperatura: fria (hasta tempMax), ambiente (hasta 25 °C) o alta. */
enum class TemperatureStatus(val label: String) {
    COLD("FRÍA"),
    AMBIENT("AMBIENTE"),
    HIGH("ALTA")
}

enum class PhStatus(val label: String, val description: String) {
    NORMAL("pH Normal", "Valor óptimo para leche fresca."),
    ACIDIC("pH Ácido", "Posible proliferación bacteriana o leche alterada/corta."),
    ALKALINE("pH Alcalino", "Posible presencia de mastitis o adulteración con neutralizantes.")
}

enum class QualityLevel {
    EXCELLENT,
    ACCEPTABLE,
    WARNING,
    REJECTED
}

data class MilkQualityAssessment(
    val level: QualityLevel,
    val title: String,
    val summary: String,
    val recommendations: List<String>
)

/** Margen bajo el pH minimo que se considera "levemente acido" (y no un rechazo). */
private const val PH_MARGIN = 0.2f

/** Evalua la calidad con los rangos vigentes (configuracion/rangos). */
fun evaluateQuality(
    ph: Float,
    conductivity: Float,
    turbidity: Float,
    temperature: Float,
    ranges: QualityRanges = QualityRangesHolder.current
): MilkQualityAssessment {
    val r = ranges
    val phOk = ph in r.phMin..r.phMax
    val phSubAcid = ph >= r.phMin - PH_MARGIN && ph < r.phMin
    val condOk = conductivity in r.condMin..r.condMax
    val turbOk = turbidity >= r.turbMin

    val issues = mutableListOf<String>()
    val recs = mutableListOf<String>()

    if (ph < r.phMin - PH_MARGIN) {
        issues.add("pH severamente bajo ($ph)")
        recs.add("Descartar lote o destinar a procesos industriales autorizados. Alta carga ácida.")
    } else if (phSubAcid) {
        issues.add("pH levemente ácido ($ph)")
        recs.add("Verificar la cadena de frío e higiene en el ordeño.")
    } else if (ph > r.phMax) {
        issues.add("pH alcalino ($ph)")
        recs.add("Verificar salud del hato (posible mastitis) o detección de substancias alcalinas.")
    }

    if (!turbOk) {
        issues.add("Turbidez baja ($turbidity%)")
        recs.add("Inspeccionar posible aguado o adulteración con líquidos transparentes.")
    }

    if (conductivity > r.condMax) {
        issues.add("Conductividad elevada ($conductivity mS/cm)")
        recs.add("Realizar prueba CMT (California Mastitis Test) a vacas sospechosas.")
    } else if (conductivity < r.condMin && conductivity > 0.1f) {
        issues.add("Conductividad baja ($conductivity mS/cm)")
        recs.add("Posible alteración o dilución del producto.")
    }

    val tempText = "%.1f".format(temperature)
    val maxText = "%.1f".format(r.tempMax)
    val tempHigh = temperature > TEMP_AMBIENT_MAX
    val tempCold = temperature <= r.tempMax

    return when {
        phOk && condOk && turbOk && tempHigh -> MilkQualityAssessment(
            level = QualityLevel.WARNING,
            title = "Precaución / Temperatura alta",
            summary = "pH, conductividad y turbidez están en rango, pero la leche está a $tempText°C (más de 25°C).",
            recommendations = listOf(
                "Enfriar la leche a $maxText°C o menos lo antes posible.",
                "Repetir la medición cuando la temperatura baje."
            )
        )
        phOk && condOk && turbOk -> MilkQualityAssessment(
            level = QualityLevel.EXCELLENT,
            title = "Calidad Excelente",
            summary = "Todos los parámetros (pH, CE, Turbidez) están dentro de los rangos vigentes.",
            recommendations = if (tempCold) {
                listOf("Apta para almacenamiento en frío y comercialización directa.", "Mantener cadena de frío (≤ $maxText°C).")
            } else {
                listOf(
                    "La leche está a $tempText°C: enfriarla a $maxText°C o menos antes de almacenarla.",
                    "Una vez enfriada, mantener la cadena de frío (≤ $maxText°C)."
                )
            }
        )
        phOk && (condOk || turbOk) -> MilkQualityAssessment(
            level = QualityLevel.ACCEPTABLE,
            title = "Calidad Aceptable",
            summary = "pH dentro del rango normal. Presenta variaciones en conductividad o turbidez.",
            recommendations = recs.ifEmpty { listOf("Supervisar temperatura de almacenamiento y repetir medición.") }
        )
        phSubAcid -> MilkQualityAssessment(
            level = QualityLevel.WARNING,
            title = "Precaución / Ligeramente Ácida",
            summary = "El pH está rozando el límite de acidez. La leche puede cortarse al hervir.",
            recommendations = recs
        )
        else -> MilkQualityAssessment(
            level = QualityLevel.REJECTED,
            title = "Revisar / No Conforme",
            summary = if (issues.isNotEmpty()) issues.joinToString(", ") else "Valores fuera de especificación.",
            recommendations = recs
        )
    }
}

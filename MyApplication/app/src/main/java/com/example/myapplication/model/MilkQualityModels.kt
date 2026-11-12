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
        get() = when {
            ph in 6.6f..6.8f -> PhStatus.NORMAL
            ph < 6.6f -> PhStatus.ACIDIC
            else -> PhStatus.ALKALINE
        }

    val temperatureStatus: TemperatureStatus
        get() = when {
            temperature <= TEMP_COLD_MAX -> TemperatureStatus.COLD
            temperature <= TEMP_AMBIENT_MAX -> TemperatureStatus.AMBIENT
            else -> TemperatureStatus.HIGH
        }

    val overallAssessment: MilkQualityAssessment
        get() = evaluateQuality(ph, conductivity, turbidity, temperature)
}

/** Estado de la temperatura de la leche: fria (<= 4 C), ambiente (4 a 25 C) o alta (> 25 C). */
enum class TemperatureStatus(val label: String) {
    COLD("FRÍA"),
    AMBIENT("AMBIENTE"),
    HIGH("ALTA")
}

const val TEMP_COLD_MAX = 4.0f
const val TEMP_AMBIENT_MAX = 25.0f

enum class PhStatus(val label: String, val description: String) {
    NORMAL("pH Normal", "Valor óptimo para leche fresca (6.6 - 6.8)."),
    ACIDIC("pH Ácido (< 6.6)", "Posible proliferación bacteriana o leche alterada/corta."),
    ALKALINE("pH Alcalino (> 6.8)", "Posible presencia de mastitis o adulteración con neutralizantes.")
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

fun evaluateQuality(ph: Float, conductivity: Float, turbidity: Float, temperature: Float): MilkQualityAssessment {
    // Standard raw milk parameters:
    // pH: 6.6 - 6.8
    // Conductivity: 4.0 - 5.5 mS/cm (higher indicates mastitis/salt addition, lower indicates water addition)
    // Turbidity: > 70% (lower indicates dilution with water)
    // Temp: <= 4.0°C cold storage; 4-25°C ambient (cool before storing); > 25°C too high

    val phOk = ph in 6.6f..6.8f
    val phSubAcid = ph in 6.4f..6.59f
    val condOk = conductivity in 4.0f..6.0f
    val turbOk = turbidity >= 50.0f

    val issues = mutableListOf<String>()
    val recs = mutableListOf<String>()

    if (ph < 6.4f) {
        issues.add("pH severamente bajo ($ph)")
        recs.add("Descartar lote o destinar a procesos industriales autorizados. Alta carga ácida.")
    } else if (phSubAcid) {
        issues.add("pH levemente ácido ($ph)")
        recs.add("Verificar la cadena de frío e higiene en el ordeño.")
    } else if (ph > 6.8f) {
        issues.add("pH alcalino ($ph)")
        recs.add("Verificar salud del hato (posible mastitis) o detección de substancias alcalinas.")
    }

    if (turbidity < 50.0f) {
        issues.add("Turbidez baja ($turbidity%)")
        recs.add("Inspeccionar posible aguado o adulteración con líquidos transparentes.")
    }

    if (conductivity > 6.2f) {
        issues.add("Conductividad elevada ($conductivity mS/cm)")
        recs.add("Realizar prueba CMT (California Mastitis Test) a vacas sospechosas.")
    } else if (conductivity < 3.5f && conductivity > 0.1f) {
        issues.add("Conductividad anormalmente baja ($conductivity mS/cm)")
        recs.add("Posible alteración o dilución del producto.")
    }

    val tempText = "%.1f".format(temperature)
    val tempHigh = temperature > TEMP_AMBIENT_MAX
    val tempCold = temperature <= TEMP_COLD_MAX

    return when {
        phOk && condOk && turbOk && tempHigh -> MilkQualityAssessment(
            level = QualityLevel.WARNING,
            title = "Precaución / Temperatura alta",
            summary = "pH, conductividad y turbidez están en rango, pero la leche está a $tempText°C (más de 25°C).",
            recommendations = listOf(
                "Enfriar la leche a 4°C o menos lo antes posible.",
                "Repetir la medición cuando la temperatura baje."
            )
        )
        phOk && condOk && turbOk -> MilkQualityAssessment(
            level = QualityLevel.EXCELLENT,
            title = "Calidad Excelente",
            summary = "Todos los parámetros (pH, CE, Turbidez) están dentro de los rangos óptimos para leche de alta calidad.",
            recommendations = if (tempCold) {
                listOf("Apta para almacenamiento en frío y comercialización directa.", "Mantener cadena de frío (≤ 4°C).")
            } else {
                listOf(
                    "La leche está a $tempText°C: enfriarla a 4°C o menos antes de almacenarla.",
                    "Una vez enfriada, mantener la cadena de frío (≤ 4°C)."
                )
            }
        )
        phOk && (condOk || turbOk) -> MilkQualityAssessment(
            level = QualityLevel.ACCEPTABLE,
            title = "Calidad Aceptable",
            summary = "pH dentro del rango normal. Presenta ligeras variaciones en conductividad o turbidez.",
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

package com.example.myapplication.sensor

import com.example.myapplication.model.MilkQualityReading
import com.example.myapplication.model.ReadingSource
import kotlin.math.round
import kotlin.random.Random

/** Muestras de prueba del modo DEMO. Son valores de referencia inventados, no mediciones. */
enum class DemoScenario(
    val label: String,
    val description: String,
    val baseTemp: Float,
    val basePh: Float,
    val baseCond: Float,
    val baseTurb: Float
) {
    NORMAL("Normal", "Leche refrigerada en rango (3,8 °C, pH 6,7)", 3.8f, 6.70f, 4.8f, 85.0f),
    ACIDIC_BACTERIAL("Ácida", "Carga bacteriana (pH 6,25)", 24.5f, 6.25f, 5.8f, 75.0f),
    ALKALINE_MASTITIS("Mastitis", "pH y conductividad altos (pH 7,15)", 25.0f, 7.15f, 6.8f, 60.0f),
    DILUTED_WATER("Diluida", "Turbidez y conductividad bajas (posible aguado)", 21.0f, 6.60f, 2.8f, 35.0f),

    // Muestras para probar el analisis e interpretacion (escenarios A a F). Son valores inventados
    // pensados para provocar cada situacion con los rangos por defecto; no son mediciones.
    ONLY_PH("Solo pH", "Un parámetro alterado: pH alto (6,97)", 3.8f, 6.97f, 4.8f, 85.0f),
    PH_AND_CONDUCTIVITY("pH + CE", "Dos alterados: pH y conductividad altos", 3.8f, 6.97f, 6.2f, 85.0f),
    TEMPERATURE_AND_TURBIDITY("Temp + turbidez", "Dos alterados sin causa común: temperatura alta y turbidez baja", 12.0f, 6.70f, 4.8f, 38.0f),
    THREE_ALTERED("3 alterados", "Temperatura, pH y conductividad fuera de rango", 8.0f, 6.97f, 6.0f, 85.0f),
    FOUR_ALTERED("4 alterados", "Los cuatro parámetros fuera de rango", 27.0f, 7.30f, 7.5f, 30.0f),
    MILD_DEVIATIONS("Leves", "Los cuatro parámetros apenas fuera de rango", 5.0f, 6.85f, 5.7f, 47.0f),
    SUSPICIOUS("Sospechosa", "Conductividad casi nula con muestra opaca (lectura contradictoria)", 3.8f, 6.70f, 0.1f, 85.0f)
}

/**
 * Genera lecturas simuladas alrededor de los valores base de la muestra, con un ruido pequeño
 * parecido al de un sensor. Todas salen marcadas como SIMULATED.
 */
class DemoReadingGenerator(private val random: Random = Random.Default) {

    fun next(scenario: DemoScenario, timestamp: Long): MilkQualityReading {
        fun noise(amplitude: Float) = (random.nextFloat() - 0.5f) * amplitude
        return MilkQualityReading(
            temperature = round1((scenario.baseTemp + noise(0.4f)).coerceIn(0f, 50f)),
            ph = round2(scenario.basePh + noise(0.04f)),
            conductivity = round2(scenario.baseCond + noise(0.1f)),
            turbidity = round1((scenario.baseTurb + noise(1.0f)).coerceIn(0f, 100f)),
            timestamp = timestamp,
            source = ReadingSource.SIMULATED
        )
    }

    private fun round1(v: Float) = round(v * 10f) / 10f
    private fun round2(v: Float) = round(v * 100f) / 100f
}

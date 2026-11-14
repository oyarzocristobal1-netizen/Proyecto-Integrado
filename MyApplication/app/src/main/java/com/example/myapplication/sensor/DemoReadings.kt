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
    DILUTED_WATER("Diluida", "Turbidez y conductividad bajas (posible aguado)", 21.0f, 6.60f, 2.8f, 35.0f)
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

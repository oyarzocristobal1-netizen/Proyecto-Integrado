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
    /** Cada medicion nueva sale distinta: unas buenas y otras con uno o varios parametros alterados. */
    VARIED("Variada", "Cada medición sale distinta: unas buenas y otras con parámetros alterados", 3.8f, 6.70f, 4.8f, 85.0f),
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

/** Valores de una muestra simulada. Varias lecturas de la misma muestra salen con ruido pequeño. */
data class DemoSample(val temperature: Float, val ph: Float, val conductivity: Float, val turbidity: Float)

/**
 * Genera lecturas simuladas alrededor de los valores de una muestra, con un ruido pequeño
 * parecido al de un sensor. Todas salen marcadas como SIMULATED.
 *
 * En las muestras fijas los valores base son siempre los mismos (sirven para probar cada caso).
 * En VARIED cada muestra nueva se sortea: la mayoria buenas, pero tambien con uno, dos, tres o
 * los cuatro parametros alterados, y valores distintos cada vez (por ejemplo 3,1 °C, luego 4,6 °C).
 */
class DemoReadingGenerator(private val random: Random = Random.Default) {

    /** Valores de una muestra nueva del escenario (sorteados si es VARIED). */
    fun sampleFor(scenario: DemoScenario): DemoSample =
        if (scenario == DemoScenario.VARIED) variedSample()
        else DemoSample(scenario.baseTemp, scenario.basePh, scenario.baseCond, scenario.baseTurb)

    fun next(scenario: DemoScenario, timestamp: Long, sample: DemoSample = sampleFor(scenario)): MilkQualityReading {
        fun noise(amplitude: Float) = (random.nextFloat() - 0.5f) * amplitude
        return MilkQualityReading(
            temperature = round1((sample.temperature + noise(0.4f)).coerceIn(0f, 50f)),
            ph = round2(sample.ph + noise(0.04f)),
            conductivity = round2(sample.conductivity + noise(0.1f)),
            turbidity = round1((sample.turbidity + noise(1.0f)).coerceIn(0f, 100f)),
            timestamp = timestamp,
            source = ReadingSource.SIMULATED
        )
    }

    private fun between(min: Float, max: Float) = min + random.nextFloat() * (max - min)

    /** Sortea un caso y los valores de cada parametro (inventados, con los rangos por defecto de la app). */
    private fun variedSample(): DemoSample {
        // Buena (con margen para el ruido de cada lectura): 2,5 a 3,7 °C; pH 6,64 a 6,76; CE 4,2 a 5,3; turbidez 58 a 95.
        var t = between(2.5f, 3.7f)
        var ph = between(6.64f, 6.76f)
        var c = between(4.2f, 5.3f)
        var tb = between(58f, 95f)

        val alterations = listOf<() -> Unit>(
            { t = between(4.3f, 9f) },
            { ph = if (random.nextBoolean()) between(6.86f, 7.1f) else between(6.3f, 6.54f) },
            { c = if (random.nextFloat() < 0.65f) between(5.8f, 6.9f) else between(2.9f, 3.7f) },
            { tb = between(30f, 46f) }
        )

        val roll = random.nextFloat()
        when {
            roll < 0.45f -> Unit // buena
            roll < 0.68f -> alterations.random(random)() // un parametro
            roll < 0.82f -> alterations.shuffled(random).take(2).forEach { it() } // dos
            roll < 0.90f -> { // leves: apenas fuera de rango, en 1 a 3 parametros
                val keep = (0..3).shuffled(random).take(random.nextInt(1, 4)).toSet()
                if (0 in keep) t = between(4.4f, 5.5f)
                if (1 in keep) ph = if (random.nextBoolean()) between(6.84f, 6.88f) else between(6.52f, 6.56f)
                if (2 in keep) c = between(5.6f, 5.78f)
                if (3 in keep) tb = between(46f, 49f)
            }
            roll < 0.96f -> alterations.shuffled(random).take(3).forEach { it() } // tres
            roll < 0.99f -> { // cuatro, algunos muy fuera de rango
                t = between(12f, 28f); ph = between(7.15f, 7.35f); c = between(6.8f, 7.8f); tb = between(25f, 40f)
            }
            else -> c = between(0.05f, 0.2f) // lectura contradictoria: conductividad casi nula con muestra opaca
        }
        return DemoSample(t, ph, c, tb)
    }

    private fun round1(v: Float) = round(v * 10f) / 10f
    private fun round2(v: Float) = round(v * 100f) / 100f
}

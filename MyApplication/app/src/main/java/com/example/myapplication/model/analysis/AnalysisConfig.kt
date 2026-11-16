package com.example.myapplication.model.analysis

/**
 * Margenes que separan una desviacion ligera, moderada, considerable y critica.
 * Cada numero es la distancia maxima al limite del rango (en la unidad del parametro).
 * Mas alla de "considerable" la desviacion es critica.
 */
data class Band(val slight: Float, val moderate: Float, val considerable: Float)

/** Un valor por parametro. */
data class PerParameter(
    val temperature: Float,
    val ph: Float,
    val conductivity: Float,
    val turbidity: Float
) {
    operator fun get(p: Parameter): Float = when (p) {
        Parameter.TEMPERATURE -> temperature
        Parameter.PH -> ph
        Parameter.CONDUCTIVITY -> conductivity
        Parameter.TURBIDITY -> turbidity
    }
}

/**
 * Reglas configurables del analisis. Los RANGOS de calidad no estan aqui: salen de
 * QualityRanges (configuracion/rangos) para no inventar rangos tecnicos.
 *
 * Los margenes de gravedad parten de los umbrales que la app ya usaba para sus alertas
 * (pH severo a +-0,2 del rango; conductividad severa a +0,7 o -0,5; temperatura critica sobre
 * 25 °C). Los demas margenes son propuestas iniciales que hay que validar con referencias tecnicas
 * y con mediciones reales de la caja.
 */
data class AnalysisConfig(
    // --- gravedad por parametro (distancia al limite violado)
    val tempHigh: Band = Band(slight = 2f, moderate = 6f, considerable = 21f),
    /** Sobre esta temperatura la desviacion es siempre critica (leche a temperatura ambiente). */
    val tempCriticalAbove: Float = 25f,
    val phBand: Band = Band(slight = 0.1f, moderate = 0.2f, considerable = 0.4f),
    val condHigh: Band = Band(slight = 0.3f, moderate = 0.7f, considerable = 1.5f),
    val condLow: Band = Band(slight = 0.25f, moderate = 0.5f, considerable = 1.0f),
    val turbLow: Band = Band(slight = 5f, moderate = 15f, considerable = 30f),
    /**
     * Turbidez maxima (%). El proyecto solo define un minimo, asi que por defecto no se evalua la
     * turbidez elevada. Si el equipo la define tras validar con mediciones reales, el analisis la usa.
     */
    val turbidityMax: Float? = null,
    val turbHigh: Band = Band(slight = 5f, moderate = 15f, considerable = 30f),

    // --- lecturas sospechosas
    /** Bajo esta temperatura (°C) la lectura es dudosa: la leche se congela cerca de -0,5 °C. */
    val temperatureFloor: Float = 0f,
    /** Conductividad casi nula (mS/cm): con una muestra opaca es contradictoria. */
    val nearZeroConductivity: Float = 0.3f,

    // --- historial
    /** Mediciones del mismo lote separadas por menos de este tiempo se cuentan como una sola muestra. */
    val sameSampleWindowMs: Long = 10 * 60 * 1000L,
    /** Muestras distintas minimas para hablar de tendencia. */
    val minSamplesForTrend: Int = 3,
    /** Muestras que se miran hacia atras para la tendencia. */
    val trendWindow: Int = 5,
    /** Cambio total minimo para considerar que hay una tendencia (en la unidad de cada parametro). */
    val trendMinChange: PerParameter = PerParameter(temperature = 1.0f, ph = 0.05f, conductivity = 0.2f, turbidity = 3f),
    /** Diferencia con el promedio anterior que se considera un cambio importante. */
    val bigChange: PerParameter = PerParameter(temperature = 2.0f, ph = 0.15f, conductivity = 0.6f, turbidity = 10f),
    /** Muestras consecutivas alteradas para decir que la condicion "se repite". */
    val repeatedAfter: Int = 2,
    /** Muestras consecutivas alteradas para decir que es persistente y subir la prioridad. */
    val persistentAfter: Int = 3
)

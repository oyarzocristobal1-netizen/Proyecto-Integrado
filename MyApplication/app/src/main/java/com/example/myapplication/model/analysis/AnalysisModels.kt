package com.example.myapplication.model.analysis

import com.example.myapplication.model.MilkQualityReading
import com.example.myapplication.model.ReadingSource

/** Los cuatro parametros que mide la caja. */
enum class Parameter(val label: String, val unit: String) {
    TEMPERATURE("Temperatura", "°C"),
    PH("pH", ""),
    CONDUCTIVITY("Conductividad", "mS/cm"),
    TURBIDITY("Turbidez", "%")
}

/** Hacia que lado del rango se salio el valor. */
enum class Direction { LOW, HIGH, NONE }

/**
 * Que tan lejos del rango esta un parametro. UNRELIABLE no es una desviacion: significa que el
 * valor es sospechoso (sensor desconectado, saturado, contradictorio o aun inestable) y no se
 * puede interpretar hasta repetir la medicion.
 */
enum class DeviationLevel(val label: String, val rank: Int) {
    IN_RANGE("Dentro del rango", 0),
    SLIGHT("Ligera", 1),
    MODERATE("Moderada", 2),
    CONSIDERABLE("Considerable", 3),
    CRITICAL("Crítica", 4),
    UNRELIABLE("Lectura dudosa", -1);

    /** Esta fuera de rango (ligera, moderada, considerable o critica). */
    val isDeviation: Boolean get() = rank > 0
}

/** Prioridad general de una medicion (NORMAL, PREVENTIVO, ADVERTENCIA o CRITICO). */
enum class PriorityLevel(val label: String, val meaning: String) {
    NORMAL("Normal", "Todos los parámetros están dentro de los rangos establecidos."),
    PREVENTIVE("Preventivo", "Hay desviaciones leves que requieren seguimiento."),
    WARNING("Advertencia", "Uno o varios parámetros presentan alteraciones importantes."),
    CRITICAL("Crítico", "Condición que requiere atención inmediata y comprobaciones adicionales.");

    fun escalated(): PriorityLevel = entries.getOrElse(ordinal + 1) { CRITICAL }
}

/** Situacion general detectada (los escenarios A a F del diseno). */
enum class AnalysisScenario(val label: String) {
    NORMAL("Todo dentro del rango"),
    SINGLE("Un parámetro alterado"),
    TWO("Dos parámetros alterados"),
    THREE("Tres parámetros alterados"),
    FOUR("Cuatro parámetros alterados"),
    MILD("Desviaciones leves"),
    SUSPECT("Lectura sospechosa")
}

/** Resultado de analizar un parametro. */
data class ParameterFinding(
    val parameter: Parameter,
    val value: Float,
    val level: DeviationLevel,
    val direction: Direction,
    /** Distancia al limite violado, en la unidad del parametro (0 si esta en rango). */
    val distance: Float,
    /** Rango o limite de referencia en texto, por ejemplo "6.6 a 6.8". */
    val reference: String,
    /** Por que la lectura es dudosa (solo si level == UNRELIABLE). */
    val suspectReason: String? = null
)

/** Valor con su unidad para mostrarlo, por ejemplo "6.97" o "12.0 °C". */
val ParameterFinding.valueText: String get() = formatValue(parameter, value)

/** Estado en pocas palabras para mostrar en un recuadro, por ejemplo "Dentro del rango" o "Leve · alta". */
val ParameterFinding.statusText: String
    get() {
        val word = when (direction) {
            Direction.HIGH -> if (parameter == Parameter.PH) "alto" else "alta"
            Direction.LOW -> if (parameter == Parameter.PH) "bajo" else "baja"
            Direction.NONE -> ""
        }
        fun with(prefix: String) = if (word.isEmpty()) prefix else "$prefix · $word"
        return when (level) {
            DeviationLevel.IN_RANGE -> "Dentro del rango"
            DeviationLevel.UNRELIABLE -> "Lectura dudosa"
            DeviationLevel.SLIGHT -> with("Leve")
            DeviationLevel.MODERATE -> with("Moderada")
            DeviationLevel.CONSIDERABLE -> if (word.isEmpty()) "Considerable" else "Muy $word"
            DeviationLevel.CRITICAL -> with("Crítica")
        }
    }

enum class CardKind { PARAMETER, COMBINATION, HISTORY, RELIABILITY }

/**
 * Tarjeta de recomendacion. Responde las cuatro preguntas: que esta sucediendo, que podria
 * estar causandolo, que debe revisar el encargado y que accion se recomienda.
 */
data class AnalysisCard(
    val kind: CardKind,
    val title: String,
    val whatHappens: String,
    val possibleCauses: List<String>,
    val whatToCheck: List<String>,
    val recommendation: String,
    val parameters: Set<Parameter> = emptySet(),
    val level: DeviationLevel? = null
)

/** Lectura minima que necesita el analisis (sirve para la medicion actual y para las guardadas). */
data class AnalysisReading(
    val timestamp: Long,
    val temperature: Float,
    val ph: Float,
    val conductivity: Float,
    val turbidity: Float,
    val source: ReadingSource = ReadingSource.SIMULATED,
    val lotId: String? = null
) {
    fun value(p: Parameter): Float = when (p) {
        Parameter.TEMPERATURE -> temperature
        Parameter.PH -> ph
        Parameter.CONDUCTIVITY -> conductivity
        Parameter.TURBIDITY -> turbidity
    }

    val isSimulated: Boolean get() = source == ReadingSource.SIMULATED
}

fun MilkQualityReading.toAnalysisReading(lotId: String? = null) = AnalysisReading(
    timestamp = timestamp,
    temperature = temperature,
    ph = ph,
    conductivity = conductivity,
    turbidity = turbidity,
    source = source,
    lotId = lotId
)

/** Resultado completo del analisis de una medicion. */
data class MilkAnalysis(
    val scenario: AnalysisScenario,
    val priority: PriorityLevel,
    /** Titulo corto, por ejemplo "Advertencia: pH y conductividad fuera de rango". */
    val headline: String,
    /** Frase que explica el estado general en palabras simples. */
    val summary: String,
    val findings: List<ParameterFinding>,
    val parameterCards: List<AnalysisCard>,
    val combinedCards: List<AnalysisCard>,
    val historyCards: List<AnalysisCard>,
    val reliabilityCards: List<AnalysisCard>,
    /** Acciones recomendadas, ordenadas de la mas urgente a la menos urgente y sin repetir. */
    val actions: List<String>,
    /** Que hacer a continuacion (seguimiento). */
    val followUp: String,
    /** false si alguna lectura es dudosa: no se debe interpretar hasta verificarla. */
    val reliable: Boolean,
    /** Aviso cuando no hay mediciones anteriores para comparar, o null. */
    val historyNote: String?,
    /** Muestras distintas del lote que se usaron en el analisis historico (incluye la actual). */
    val samplesAnalyzed: Int,
    val simulated: Boolean,
    val disclaimer: String,
    /** Hacia donde va cada parametro si sigue la tendencia (estimacion, no prediccion validada). */
    val forecast: ForecastResult = ForecastResult(emptyList(), null),
    /** En cuantos minutos conviene volver a medir, o null si no hace falta. */
    val recheckInMinutes: Int? = null
) {
    val altered: List<ParameterFinding> get() = findings.filter { it.level.isDeviation }
    val suspect: List<ParameterFinding> get() = findings.filter { it.level == DeviationLevel.UNRELIABLE }
}

const val ANALYSIS_DISCLAIMER =
    "Las causas indicadas son posibilidades, no diagnósticos confirmados: los sensores solo miden valores " +
        "y esta interpretación es orientativa. Los rangos y márgenes deben validarse con referencias " +
        "técnicas y mediciones reales."

const val ANALYSIS_DEMO_NOTICE =
    "Análisis de datos SIMULADOS (modo DEMO): muestra cómo se interpretaría una lectura, no corresponde a leche real."

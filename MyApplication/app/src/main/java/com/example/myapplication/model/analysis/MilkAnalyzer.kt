package com.example.myapplication.model.analysis

import com.example.myapplication.model.QualityRanges
import com.example.myapplication.model.QualityRangesHolder
import com.example.myapplication.model.StabilityState

/**
 * Motor de analisis e interpretacion de la calidad de la leche.
 *
 * Es logica pura (sin Android ni Firebase) y no depende de la interfaz. Evalua el valor de cada
 * parametro, su rango de referencia, la magnitud de la desviacion, la combinacion de parametros
 * alterados, la repeticion, la evolucion historica y la confiabilidad de la lectura. Los rangos
 * salen de QualityRanges y los margenes de AnalysisConfig, ambos configurables.
 *
 * Importante: las causas que muestra son posibilidades, no diagnosticos.
 */
object MilkAnalyzer {

    /**
     * @param current medicion a interpretar
     * @param previous mediciones anteriores (el motor usa las del mismo lote y origen; puede estar vacia)
     * @param stability estado de estabilidad de la lectura en vivo, si se conoce
     */
    fun analyze(
        current: AnalysisReading,
        previous: List<AnalysisReading> = emptyList(),
        ranges: QualityRanges = QualityRangesHolder.current,
        config: AnalysisConfig = AnalysisConfig(),
        stability: StabilityState? = null
    ): MilkAnalysis {
        val issues = ReliabilityChecker.check(current, ranges, config, stability)
        val suspectReasons = Parameter.entries.associateWith { p -> issues.firstOrNull { p in it.parameters }?.reason }

        val findings = Parameter.entries.map { p ->
            val reason = suspectReasons[p]
            if (reason != null) {
                ParameterFinding(
                    p, current.value(p), DeviationLevel.UNRELIABLE, Direction.NONE, 0f,
                    ParameterClassifier.referenceText(p, ranges, config), reason
                )
            } else ParameterClassifier.classify(p, current.value(p), ranges, config)
        }
        val altered = findings.filter { it.level.isDeviation }
        val reliable = issues.isEmpty()

        val scenario = when {
            !reliable -> AnalysisScenario.SUSPECT
            altered.isEmpty() -> AnalysisScenario.NORMAL
            altered.size == 1 -> AnalysisScenario.SINGLE
            altered.all { it.level == DeviationLevel.SLIGHT } -> AnalysisScenario.MILD
            altered.size == 2 -> AnalysisScenario.TWO
            altered.size == 3 -> AnalysisScenario.THREE
            else -> AnalysisScenario.FOUR
        }

        // El historial solo se interpreta si la lectura actual es confiable.
        val history = if (reliable) HistoryAnalyzer.analyze(current, previous, ranges, config)
        else HistoryResult(emptyList(), false, PriorityLevel.NORMAL, 1, "No se analiza el historial mientras la lectura actual sea dudosa.")

        val forecast = if (reliable) ForecastEngine.forecast(current, previous, ranges, config)
        else ForecastResult(emptyList(), "No se proyecta mientras la lectura actual sea dudosa.")

        var priority = basePriority(altered)
        if (history.escalate && priority != PriorityLevel.NORMAL) priority = priority.escalated()
        if (history.minPriority > priority) priority = history.minPriority
        if (!reliable && priority < PriorityLevel.PREVENTIVE) priority = PriorityLevel.PREVENTIVE
        // Una salida de rango prevista pronto pide al menos seguimiento preventivo.
        if (forecast.soonestExit != null && priority < PriorityLevel.PREVENTIVE) priority = PriorityLevel.PREVENTIVE

        val parameterCards = altered.sortedByDescending { it.level.rank }.map { ParameterCards.card(it) }
        val reliabilityCards = issues.map { ParameterCards.reliabilityCard(it) }
        val combined = CombinationCards.cards(altered, scenario)

        val actions = buildList {
            reliabilityCards.forEach { add(it.recommendation) }
            parameterCards.forEach { add(it.recommendation) }
            combined.forEach { add(it.recommendation) }
            history.cards.forEach { add(it.recommendation) }
        }.distinct().take(MAX_ACTIONS)

        return MilkAnalysis(
            scenario = scenario,
            priority = priority,
            headline = headline(priority, scenario, altered),
            summary = summary(scenario, altered, findings, history),
            findings = findings,
            parameterCards = parameterCards,
            combinedCards = combined,
            historyCards = history.cards,
            reliabilityCards = reliabilityCards,
            actions = actions,
            followUp = followUp(priority, reliable),
            reliable = reliable,
            historyNote = history.note,
            samplesAnalyzed = history.samples,
            simulated = current.isSimulated,
            disclaimer = if (current.isSimulated) "$ANALYSIS_DEMO_NOTICE $ANALYSIS_DISCLAIMER" else ANALYSIS_DISCLAIMER,
            forecast = forecast,
            recheckInMinutes = recheckMinutes(priority, reliable, forecast, config)
        )
    }

    /** Cuando volver a medir: segun la prioridad, y antes si se preve salir del rango pronto. */
    internal fun recheckMinutes(priority: PriorityLevel, reliable: Boolean, forecast: ForecastResult, c: AnalysisConfig): Int? {
        val base = when {
            !reliable -> c.recheckSuspectMinutes
            priority == PriorityLevel.CRITICAL -> c.recheckCriticalMinutes
            priority == PriorityLevel.WARNING -> c.recheckWarningMinutes
            priority == PriorityLevel.PREVENTIVE -> c.recheckPreventiveMinutes
            else -> null
        }
        val exit = forecast.soonestExit?.minutesToEvent?.let { (it / 2).coerceAtLeast(5) }
        return when {
            base == null -> exit
            exit == null -> base
            else -> minOf(base, exit)
        }
    }

    /** Gravedad segun la magnitud de las desviaciones y cuantos parametros las tienen (no solo la cantidad). */
    internal fun basePriority(altered: List<ParameterFinding>): PriorityLevel {
        if (altered.isEmpty()) return PriorityLevel.NORMAL
        val worst = altered.maxOf { it.level.rank }
        val n = altered.size
        val severe = altered.count { it.level.rank >= DeviationLevel.CONSIDERABLE.rank }
        return when {
            worst >= DeviationLevel.CRITICAL.rank -> PriorityLevel.CRITICAL
            n >= 4 && worst >= DeviationLevel.MODERATE.rank -> PriorityLevel.CRITICAL
            n >= 3 && worst >= DeviationLevel.CONSIDERABLE.rank -> PriorityLevel.CRITICAL
            n >= 2 && severe >= 2 -> PriorityLevel.CRITICAL
            worst >= DeviationLevel.MODERATE.rank -> PriorityLevel.WARNING
            else -> PriorityLevel.PREVENTIVE
        }
    }

    private fun headline(
        priority: PriorityLevel,
        scenario: AnalysisScenario,
        altered: List<ParameterFinding>
    ): String = when (scenario) {
        AnalysisScenario.SUSPECT -> "No se puede interpretar con confianza"
        AnalysisScenario.NORMAL ->
            if (priority == PriorityLevel.NORMAL) "Normal: parámetros dentro del rango" else "Preventivo: tendencia a vigilar"
        else -> "${priority.label}: ${joinNames(altered.map { it.parameter.label })} fuera de rango"
    }

    private fun summary(
        scenario: AnalysisScenario,
        altered: List<ParameterFinding>,
        findings: List<ParameterFinding>,
        history: HistoryResult
    ): String = when (scenario) {
        AnalysisScenario.SUSPECT -> {
            val names = joinNames(findings.filter { it.level == DeviationLevel.UNRELIABLE }.map { it.parameter.label.lowercase() })
            "Hay lecturas dudosas ($names). No es posible una interpretación confiable hasta verificar la medición."
        }
        AnalysisScenario.NORMAL -> if (history.minPriority > PriorityLevel.NORMAL) {
            "Los valores están dentro de los rangos, pero hay una tendencia que conviene vigilar."
        } else "Los cuatro parámetros están dentro de los rangos establecidos."
        AnalysisScenario.SINGLE -> {
            val f = altered.first()
            "Solo ${f.parameter.label.lowercase()} está fuera de rango (desviación ${f.level.label.lowercase()}); los demás parámetros están normales."
        }
        AnalysisScenario.MILD ->
            "${joinNames(altered.map { it.parameter.label })} están ligeramente fuera de rango. Es una desviación leve que requiere seguimiento."
        AnalysisScenario.TWO, AnalysisScenario.THREE, AnalysisScenario.FOUR ->
            "${altered.size} parámetros están fuera de rango: ${joinNames(altered.map { it.parameter.label })}."
    }

    private fun followUp(priority: PriorityLevel, reliable: Boolean): String = when {
        !reliable -> "Verifique los sensores y repita la medición antes de tomar decisiones con este resultado."
        priority == PriorityLevel.NORMAL -> "Mantenga el seguimiento habitual: una medición por muestra y compárela con las siguientes."
        priority == PriorityLevel.PREVENTIVE -> "Repita la medición en unos minutos y compare; haga seguimiento en las próximas muestras."
        priority == PriorityLevel.WARNING -> "Repita la medición para confirmar y haga las comprobaciones indicadas antes de usar o despachar esa leche."
        else -> "Requiere atención inmediata: no clasifique la muestra como satisfactoria hasta verificar sensores y condiciones, y repita la medición."
    }

    private const val MAX_ACTIONS = 5
}

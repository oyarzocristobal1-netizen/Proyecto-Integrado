package com.example.myapplication.model.analysis

import com.example.myapplication.model.QualityRanges
import kotlin.math.abs

/** Resultado del analisis de las mediciones anteriores del lote. */
internal data class HistoryResult(
    val cards: List<AnalysisCard>,
    /** Sube un nivel la prioridad (condicion persistente o varios parametros alterandose a la vez). */
    val escalate: Boolean,
    /** Prioridad minima por una tendencia que empeora, aunque el valor aun este en rango. */
    val minPriority: PriorityLevel,
    /** Muestras distintas usadas, incluyendo la actual. */
    val samples: Int,
    val note: String?
)

/**
 * Analiza la evolucion de un lote: repeticion, tendencias y cambios importantes.
 *
 * Varias mediciones de una misma muestra (tomadas con pocos minutos de diferencia en el mismo lote)
 * NO son muestras independientes: se agrupan y cuentan como una sola.
 */
internal object HistoryAnalyzer {

    fun analyze(
        current: AnalysisReading,
        previous: List<AnalysisReading>,
        r: QualityRanges,
        c: AnalysisConfig
    ): HistoryResult {
        // Solo mediciones anteriores, del mismo origen (DEMO y sensor no se mezclan) y del mismo lote;
        // se descartan las lecturas dudosas.
        val usable = previous.filter {
            it.timestamp < current.timestamp &&
                it.source == current.source &&
                it.lotId == current.lotId &&
                ReliabilityChecker.check(it, r, c, null).isEmpty()
        }
        val ordered = (usable + current).sortedBy { it.timestamp }

        // Agrupa en muestras: lecturas separadas por menos de la ventana son la misma muestra.
        val clusters = mutableListOf<MutableList<AnalysisReading>>()
        for (reading in ordered) {
            val last = clusters.lastOrNull()?.last()
            if (last != null && reading.timestamp - last.timestamp <= c.sameSampleWindowMs) clusters.last().add(reading)
            else clusters.add(mutableListOf(reading))
        }
        val samples = clusters.map { it.last() }
        val merged = ordered.size - samples.size
        val n = samples.size

        val mergedNote = if (merged > 0) {
            "Se agruparon $merged medición(es) tomadas con pocos minutos de diferencia como parte de la misma muestra."
        } else null

        if (n < 2) {
            val note = when {
                usable.isEmpty() -> "Aún no hay mediciones anteriores de este lote para comparar tendencias."
                else -> "Las mediciones anteriores son de la misma muestra (tomadas con pocos minutos de diferencia), " +
                    "por lo que no cuentan como muestras distintas para ver tendencias."
            }
            return HistoryResult(emptyList(), false, PriorityLevel.NORMAL, n, note)
        }

        val altered: List<Map<Parameter, ParameterFinding>> = samples.map { s ->
            Parameter.entries.associateWith { p -> ParameterClassifier.classify(p, s.value(p), r, c) }
        }
        val cards = mutableListOf<AnalysisCard>()
        var escalate = false
        var minPriority = PriorityLevel.NORMAL
        /** Parametros que ya tienen una tarjeta del historial (para no repetir el mismo hallazgo). */
        val covered = mutableSetOf<Parameter>()
        val currentFindings = altered.last()

        // --- tendencia (necesita varias muestras distintas). Se calcula primero porque una tendencia
        // que mejora cambia como se interpreta la repeticion.
        val trendCards = mutableListOf<AnalysisCard>()
        val improving = mutableSetOf<Parameter>()
        if (n >= c.minSamplesForTrend) {
            val window = samples.takeLast(c.trendWindow)
            for (p in Parameter.entries) {
                val values = window.map { it.value(p) }
                val change = values.last() - values.first()
                val rising = values.zipWithNext().all { (a, b) -> b >= a - 1e-4f }
                val falling = values.zipWithNext().all { (a, b) -> b <= a + 1e-4f }
                if (abs(change) < c.trendMinChange[p] || !(rising || falling)) continue
                val up = change > 0
                val verb = if (up) "aumenta" else "disminuye"
                val path = "pasó de ${formatValue(p, values.first())} a ${formatValue(p, values.last())} en ${values.size} muestras"
                val now = currentFindings.getValue(p)
                if (isWorsening(p, up, values.first(), values.last(), r)) {
                    minPriority = PriorityLevel.PREVENTIVE
                    val dir = when (p) {
                        Parameter.TEMPERATURE -> Direction.HIGH
                        Parameter.TURBIDITY -> Direction.LOW
                        else -> if (up) Direction.HIGH else Direction.LOW
                    }
                    val t = ParameterKnowledge.topic(p, dir)
                    trendCards += AnalysisCard(
                        kind = CardKind.HISTORY,
                        title = "${p.label} $verb progresivamente",
                        whatHappens = "${p.label} $path. Se está alejando de su valor normal" +
                            if (now.level.isDeviation) " y ya está fuera de rango." else ", aunque todavía puede estar dentro del rango.",
                        possibleCauses = t.causes.take(3),
                        whatToCheck = t.checks,
                        recommendation = "Actúe antes de que salga de rango: ${t.action.lowerFirst()}",
                        parameters = setOf(p),
                        level = now.level
                    )
                    if (now.level.isDeviation) escalate = true
                } else {
                    improving += p
                    trendCards += AnalysisCard(
                        kind = CardKind.HISTORY,
                        title = "${p.label} $verb hacia su valor normal",
                        whatHappens = "${p.label} $path. La tendencia va hacia su valor normal.",
                        possibleCauses = listOf("La situación se está corrigiendo (por ejemplo, la leche se está enfriando)."),
                        whatToCheck = listOf("Que la mejora continúe en las siguientes mediciones."),
                        recommendation = "Continúe el seguimiento hasta que el valor se estabilice dentro del rango.",
                        parameters = setOf(p),
                        level = DeviationLevel.IN_RANGE
                    )
                }
                covered += p
            }
        }

        // --- repeticion: el parametro esta alterado en las ultimas muestras consecutivas
        for (p in Parameter.entries) {
            val f = currentFindings.getValue(p)
            var k = 0
            for (i in altered.indices.reversed()) {
                if (altered[i].getValue(p).level.isDeviation) k++ else break
            }
            if (k >= c.repeatedAfter && f.level.isDeviation) {
                // Si ya viene mejorando hacia lo normal, la tarjeta de tendencia lo explica y no se sube la prioridad.
                if (p in improving) continue
                val t = ParameterKnowledge.topic(p, f.direction)
                val persistent = k >= c.persistentAfter
                if (persistent) escalate = true
                covered += p
                cards += AnalysisCard(
                    kind = CardKind.HISTORY,
                    title = if (persistent) "${p.label} fuera de rango de forma persistente" else "${p.label} vuelve a aparecer fuera de rango",
                    whatHappens = "En las últimas $k muestras distintas del lote, ${p.label} estuvo fuera de rango. " +
                        if (persistent) "La condición es persistente: no parece un hecho aislado."
                        else "La condición se está repitiendo.",
                    possibleCauses = listOf("Un problema que continúa (conservación, refrigeración o equipo) y no una variación aislada.") + t.causes.take(2),
                    whatToCheck = t.checks,
                    recommendation = if (persistent) {
                        "Priorice la revisión del origen del problema: ${t.action.lowerFirst()} No lo trate como una variación aislada."
                    } else {
                        "Realice una revisión más detallada: ${t.action.lowerFirst()}"
                    },
                    parameters = setOf(p),
                    level = f.level
                )
            } else if (!f.level.isDeviation && altered[n - 2].getValue(p).level.isDeviation) {
                covered += p
                cards += AnalysisCard(
                    kind = CardKind.HISTORY,
                    title = "${p.label} volvió al rango",
                    whatHappens = "${p.label} estaba fuera de rango en la muestra anterior y ahora está dentro. " +
                        "Puede ser que la situación se haya corregido.",
                    possibleCauses = listOf("La causa se corrigió o fue transitoria.", "Variación normal entre muestras."),
                    whatToCheck = listOf("Que el resultado se mantenga en la siguiente medición."),
                    recommendation = "Confirme con la siguiente medición antes de dar el problema por resuelto.",
                    parameters = setOf(p),
                    level = DeviationLevel.IN_RANGE
                )
            }
        }
        cards += trendCards

        // --- cambio importante respecto al promedio de las muestras anteriores
        val before = samples.dropLast(1)
        for (p in Parameter.entries) {
            if (p in covered) continue
            val mean = before.map { it.value(p) }.average().toFloat()
            val diff = current.value(p) - mean
            if (abs(diff) >= c.bigChange[p]) {
                cards += AnalysisCard(
                    kind = CardKind.HISTORY,
                    title = "Cambio importante en ${p.label.lowercase()}",
                    whatHappens = "${p.label} es ${formatValue(p, current.value(p))}, ${if (diff > 0) "más alto" else "más bajo"} " +
                        "que el promedio de las muestras anteriores del lote (${formatValue(p, mean)}).",
                    possibleCauses = listOf("Un cambio real en la muestra o en las condiciones.", "Una medición errónea."),
                    whatToCheck = listOf("Que la muestra sea la misma y esté bien mezclada.", "Estado y limpieza del sensor."),
                    recommendation = "Repita la medición para confirmar el cambio antes de actuar.",
                    parameters = setOf(p),
                    level = currentFindings.getValue(p).level.takeIf { it.isDeviation } ?: DeviationLevel.IN_RANGE
                )
            }
        }

        // --- varios parametros comienzan a alterarse a la vez
        val nowAltered = currentFindings.values.filter { it.level.isDeviation }
        val prevAltered = altered[n - 2].values.count { it.level.isDeviation }
        if (nowAltered.size >= 2 && prevAltered == 0) {
            escalate = true
            cards += AnalysisCard(
                kind = CardKind.HISTORY,
                title = "Varios parámetros comenzaron a alterarse a la vez",
                whatHappens = "En la muestra anterior todo estaba dentro del rango y ahora ${joinNames(nowAltered.map { it.parameter.label })} " +
                    "están fuera de rango al mismo tiempo.",
                possibleCauses = listOf(
                    "Un cambio importante en la muestra o en su conservación.",
                    "Un problema de medición que afecta a varios sensores."
                ),
                whatToCheck = listOf("Estado y calibración de los sensores.", "Condiciones de almacenamiento y manipulación de la muestra."),
                recommendation = "Verifique los sensores y repita la medición para descartar un error antes de actuar.",
                parameters = nowAltered.map { it.parameter }.toSet(),
                level = nowAltered.maxOf { it.level.rank }.let { rank -> DeviationLevel.entries.first { it.rank == rank } }
            )
        }
        return HistoryResult(cards, escalate, minPriority, n, mergedNote)
    }

    /** true si el cambio aleja el valor de su rango normal. */
    private fun isWorsening(p: Parameter, up: Boolean, first: Float, last: Float, r: QualityRanges): Boolean = when (p) {
        Parameter.TEMPERATURE -> up
        Parameter.TURBIDITY -> !up
        Parameter.PH -> abs(last - (r.phMin + r.phMax) / 2) > abs(first - (r.phMin + r.phMax) / 2)
        Parameter.CONDUCTIVITY -> abs(last - (r.condMin + r.condMax) / 2) > abs(first - (r.condMin + r.condMax) / 2)
    }

    private fun String.lowerFirst() = replaceFirstChar { it.lowercase() }
}

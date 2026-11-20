package com.example.myapplication.model.analysis

/** Veredicto simple para el trabajador: si la muestra esta bien, hay que revisarla o no se debe usar todavia. */
enum class VerdictLevel(val label: String) {
    GOOD("Buena calidad"),
    REVIEW("Revisar"),
    STOP("No usar hasta verificar")
}

/**
 * Resumen de una sola mirada de un analisis: un estado, una frase y los pasos a seguir ahora.
 * Se deriva del analisis, no se escribe a mano.
 */
data class Verdict(
    val level: VerdictLevel,
    val title: String,
    val message: String,
    /** Que hacer ahora, de lo mas urgente a lo menos urgente (maximo 4). */
    val steps: List<String>
)

private const val MAX_STEPS = 4

fun MilkAnalysis.verdict(): Verdict {
    val recheck = recheckInMinutes?.let { "Vuelva a medir en ${ForecastEngine.formatMinutes(it)} y compare con este resultado." }
    val exit = forecast.soonestExit

    return when {
        !reliable -> Verdict(
            level = VerdictLevel.REVIEW,
            title = "Medición no confiable",
            message = "$summary No tome decisiones con este resultado todavía.",
            steps = (reliabilityCards.map { it.recommendation } + listOfNotNull(recheck)).distinct().take(MAX_STEPS)
        )

        priority == PriorityLevel.CRITICAL -> Verdict(
            level = VerdictLevel.STOP,
            title = "No usar hasta verificar",
            message = summary,
            steps = (
                listOf("No clasifique ni despache esta leche como satisfactoria hasta verificarla.") +
                    actions.take(2) + listOfNotNull(recheck)
                ).distinct().take(MAX_STEPS)
        )

        priority == PriorityLevel.NORMAL -> Verdict(
            level = VerdictLevel.GOOD,
            title = "Buena calidad",
            message = "Los cuatro parámetros están dentro de los rangos configurados. " +
                "Este resultado considera solo temperatura, pH, conductividad y turbidez.",
            steps = listOf("Registre la medición en el lote.", "Siga midiendo cada muestra para vigilar cambios.")
        )

        else -> Verdict(
            level = VerdictLevel.REVIEW,
            title = if (priority == PriorityLevel.WARNING) "Revisar antes de usar" else "Atención preventiva",
            message = if (exit != null) "$summary ${exit.headline}." else summary,
            steps = (actions.take(2) + listOfNotNull(recheck)).distinct().take(MAX_STEPS)
        )
    }
}

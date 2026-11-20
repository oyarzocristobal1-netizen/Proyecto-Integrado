package com.example.myapplication.model.analysis

import com.example.myapplication.model.QualityRanges

data class GlossaryEntry(val title: String, val text: String)

/** Explicacion en palabras simples de que mide cada parametro, con los rangos vigentes de la app. */
object ParameterGlossary {

    fun entries(r: QualityRanges): List<GlossaryEntry> = listOf(
        GlossaryEntry(
            "Temperatura",
            "Qué tan fría está la leche. Debe mantenerse refrigerada (referencia: hasta ${fmt(r.tempMax, 1)} °C). " +
                "Si sube, la leche se echa a perder más rápido."
        ),
        GlossaryEntry(
            "pH",
            "Mide qué tan ácida o alcalina es la leche (referencia: ${fmt(r.phMin, 2)} a ${fmt(r.phMax, 2)}). " +
                "Un cambio puede relacionarse con la conservación, la manipulación o alteraciones de la leche."
        ),
        GlossaryEntry(
            "Conductividad",
            "Mide cuánta electricidad conduce la leche, que depende de las sales disueltas (referencia: " +
                "${fmt(r.condMin, 1)} a ${fmt(r.condMax, 1)} mS/cm). Un valor alto puede ser una señal de alerta de salud de la ubre, " +
                "como la mastitis, pero no la diagnostica: solo indica que conviene hacer más controles."
        ),
        GlossaryEntry(
            "Turbidez",
            "Mide qué tan opaca o translúcida es la muestra (referencia: desde ${fmt(r.turbMin, 0)} %). " +
                "Un valor bajo puede indicar que la leche está diluida. No mide el espesor ni la viscosidad."
        )
    )
}

package com.example.myapplication.model.analysis

import com.example.myapplication.model.QualityRanges

/** Conocimiento de cada parametro: que significa, posibles causas y que revisar. */
internal data class Topic(
    val meaning: String,
    val causes: List<String>,
    val checks: List<String>,
    val action: String,
    /** Que puede y que no puede decir ese sensor (evita sobreinterpretar). */
    val sensorNote: String
)

internal object ParameterKnowledge {

    fun topic(p: Parameter, d: Direction): Topic = when (p) {
        Parameter.TEMPERATURE -> TEMP_HIGH
        Parameter.PH -> if (d == Direction.LOW) PH_LOW else PH_HIGH
        Parameter.CONDUCTIVITY -> if (d == Direction.LOW) COND_LOW else COND_HIGH
        Parameter.TURBIDITY -> if (d == Direction.HIGH) TURB_HIGH else TURB_LOW
    }

    private val TEMP_HIGH = Topic(
        meaning = "La leche está más caliente de lo recomendado para su conservación.",
        causes = listOf(
            "Problemas en el sistema de refrigeración.",
            "Funcionamiento incorrecto del tanque de enfriamiento.",
            "Tiempo de enfriamiento insuficiente.",
            "Apertura frecuente del tanque o incorporación de leche recién ordeñada (caliente).",
            "Condiciones ambientales cálidas o sensor de temperatura mal ubicado."
        ),
        checks = listOf(
            "Estado del sistema de refrigeración y del compresor.",
            "Funcionamiento del tanque de enfriamiento.",
            "Cómo evoluciona la temperatura en las siguientes mediciones.",
            "Posición y estado del sensor de temperatura."
        ),
        action = "Verifique el sistema de enfriamiento y mida de nuevo para comprobar si la temperatura comienza a bajar.",
        sensorNote = "La temperatura describe la conservación; por sí sola no indica la composición de la leche."
    )

    private val PH_HIGH = Topic(
        meaning = "La muestra es más alcalina de lo esperado.",
        causes = listOf(
            "Cambios en la composición de la leche.",
            "Condiciones de almacenamiento inadecuadas.",
            "Contaminación o sustancias ajenas a la leche.",
            "Problemas en la manipulación de la muestra.",
            "En algunos contextos, cambios asociados a la salud de la ubre (el pH solo no lo confirma)."
        ),
        checks = listOf(
            "Condiciones de conservación y tiempo desde el ordeño.",
            "Limpieza de los recipientes y de la sonda de pH.",
            "Calibración del sensor de pH."
        ),
        action = "Revise las condiciones de conservación y realice una nueva medición para confirmar el resultado.",
        sensorNote = "El pH solo indica qué tan ácida o alcalina es la muestra; no identifica la causa."
    )

    private val PH_LOW = Topic(
        meaning = "La muestra es más ácida de lo esperado.",
        causes = listOf(
            "Crecimiento de bacterias que acidifican la leche, favorecido por un enfriamiento insuficiente.",
            "Tiempo prolongado entre el ordeño y la medición.",
            "Higiene deficiente en el ordeño o en los recipientes.",
            "Sonda de pH sin calibrar o contaminada."
        ),
        checks = listOf(
            "Cadena de frío y temperatura de almacenamiento.",
            "Tiempo transcurrido desde el ordeño.",
            "Higiene de equipos y recipientes.",
            "Calibración del sensor de pH."
        ),
        action = "Revise la cadena de frío y la higiene, y repita la medición para confirmar.",
        sensorNote = "El pH solo indica qué tan ácida o alcalina es la muestra; no identifica la causa."
    )

    private val COND_HIGH = Topic(
        meaning = "La leche conduce más electricidad de lo esperado, lo que indica más sales disueltas.",
        causes = listOf(
            "Cambios en la composición iónica de la leche.",
            "En ciertos contextos puede relacionarse con problemas de salud de la ubre, como la mastitis; la conductividad por sí sola no permite diagnosticarla.",
            "Muestra a mayor temperatura (la conductividad aumenta con la temperatura).",
            "Sonda sucia o con burbujas de aire."
        ),
        checks = listOf(
            "Temperatura de la muestra al medir.",
            "Limpieza de la sonda de conductividad.",
            "Estado de las vacas sospechosas con pruebas de campo (por ejemplo, CMT)."
        ),
        action = "Repita la medición y realice controles adicionales; si se mantiene, solicite una evaluación veterinaria. No concluya una enfermedad solo con este valor.",
        sensorNote = "La conductividad mide cuánta electricidad conduce la leche (sales disueltas); no identifica enfermedades por sí sola."
    )

    private val COND_LOW = Topic(
        meaning = "La leche conduce menos electricidad de lo esperado.",
        causes = listOf(
            "Posible dilución de la leche con agua u otro líquido.",
            "Cambios en la composición de la leche.",
            "Sonda con mal contacto, mal sumergida o con aire."
        ),
        checks = listOf(
            "Que la sonda esté bien sumergida y limpia.",
            "Posible entrada de agua en el recipiente o en el circuito de la leche.",
            "Turbidez de la misma muestra (si también está baja)."
        ),
        action = "Verifique el contacto de la sonda, repita la medición y revise posibles fuentes de dilución.",
        sensorNote = "La conductividad mide cuánta electricidad conduce la leche (sales disueltas); no identifica enfermedades por sí sola."
    )

    private val TURB_LOW = Topic(
        meaning = "La muestra es menos opaca de lo esperado.",
        causes = listOf(
            "Posible dilución con agua.",
            "Cambios en las características físicas de la muestra.",
            "Sensor óptico sucio, mal posicionado o expuesto a luz ambiental."
        ),
        checks = listOf(
            "Limpieza del sensor óptico y del recipiente.",
            "Posición del sensor dentro de la muestra.",
            "Conductividad de la misma muestra."
        ),
        action = "Limpie el sensor, repita la medición y revise posibles fuentes de dilución.",
        sensorNote = "La turbidez mide qué tan opaca es la muestra (dispersión de la luz). No mide viscosidad ni espesor."
    )

    private val TURB_HIGH = Topic(
        meaning = "La muestra es más opaca de lo esperado, lo que puede indicar partículas o material en suspensión.",
        causes = listOf(
            "Cambios en las características físicas de la muestra.",
            "Presencia de partículas o material en suspensión.",
            "Equipos o recipientes con residuos."
        ),
        checks = listOf(
            "Limpieza de los equipos y del recipiente.",
            "Manipulación de la muestra (agitado, burbujas).",
            "Suciedad en el sensor óptico."
        ),
        action = "Revise la limpieza de los equipos y la manipulación de la muestra, y repita la medición.",
        sensorNote = "La turbidez mide qué tan opaca es la muestra (dispersión de la luz). No mide viscosidad ni espesor."
    )
}

/** Arma la tarjeta de un parametro fuera de rango. Cada nivel de gravedad tiene su propio texto. */
internal object ParameterCards {

    fun card(f: ParameterFinding): AnalysisCard {
        val t = ParameterKnowledge.topic(f.parameter, f.direction)
        val name = f.parameter.label
        val shown = formatValue(f.parameter, f.value)
        val side = if (f.direction == Direction.HIGH) "por encima" else "por debajo"
        val lead = when (f.level) {
            DeviationLevel.SLIGHT ->
                "$name ligeramente $side del rango de referencia ($shown; referencia: ${f.reference}). " +
                    "Es una desviación pequeña: puede ser variación normal de la muestra o que el sensor aún se está ajustando."
            DeviationLevel.MODERATE ->
                "$name moderadamente $side del rango ($shown; referencia: ${f.reference}). " +
                    "La desviación ya es apreciable y conviene confirmarla con otra medición."
            DeviationLevel.CONSIDERABLE ->
                "$name considerablemente $side del rango ($shown; referencia: ${f.reference}). " +
                    "Es una desviación grande y poco probable que sea solo variación normal de la muestra."
            DeviationLevel.CRITICAL ->
                "$name muy $side del rango ($shown; referencia: ${f.reference}). " +
                    "Es una condición crítica que requiere atención inmediata."
            else -> "$name: $shown."
        }
        val action = when (f.level) {
            DeviationLevel.SLIGHT -> "Preventivo: ${t.action.lowerFirst()} Haga seguimiento en las próximas mediciones."
            DeviationLevel.MODERATE -> t.action
            DeviationLevel.CONSIDERABLE -> "${t.action} Confirme el resultado antes de usar o despachar esa leche."
            DeviationLevel.CRITICAL ->
                "Atención inmediata: no clasifique la muestra como satisfactoria. ${t.action} Verifique también el sensor."
            else -> t.action
        }
        return AnalysisCard(
            kind = CardKind.PARAMETER,
            title = title(f),
            whatHappens = "$lead ${t.meaning} ${t.sensorNote}",
            possibleCauses = t.causes,
            whatToCheck = t.checks,
            recommendation = action,
            parameters = setOf(f.parameter),
            level = f.level
        )
    }

    private fun title(f: ParameterFinding): String {
        val feminine = f.parameter != Parameter.PH
        val word = when (f.direction) {
            Direction.HIGH -> if (feminine) "elevada" else "elevado"
            else -> if (feminine) "baja" else "bajo"
        }
        val adverb = when (f.level) {
            DeviationLevel.SLIGHT -> "ligeramente"
            DeviationLevel.MODERATE -> "moderadamente"
            DeviationLevel.CONSIDERABLE -> "considerablemente"
            else -> "críticamente"
        }
        return "${f.parameter.label} $adverb $word"
    }

    fun reliabilityCard(issue: ReliabilityIssue): AnalysisCard {
        val names = issue.parameters.sortedBy { it.ordinal }.joinToString(", ") { it.label }
        return AnalysisCard(
            kind = CardKind.RELIABILITY,
            title = "Lectura dudosa: $names",
            whatHappens = issue.reason,
            possibleCauses = listOf(
                "Sensor desconectado o mal conectado.",
                "Sonda sin contacto con la muestra o con burbujas de aire.",
                "Trama dañada o calibración incorrecta.",
                "Muestra que aún está cambiando."
            ),
            whatToCheck = listOf(
                "Conexiones y cables del sensor.",
                "Que las sondas estén sumergidas y limpias.",
                "Calibración de los sensores."
            ),
            recommendation = "No interprete esta medición como confiable: verifique el sensor y repita la medición. " +
                "No la use para clasificar la leche hasta confirmarla.",
            parameters = issue.parameters,
            level = DeviationLevel.UNRELIABLE
        )
    }

    private fun String.lowerFirst() = replaceFirstChar { it.lowercase() }
}

internal fun joinNames(names: List<String>): String = when (names.size) {
    0 -> ""
    1 -> names[0]
    else -> names.dropLast(1).joinToString(", ") + " y " + names.last()
}

internal fun QualityRanges.describe(): String =
    "temperatura máx. ${fmt(tempMax, 1)} °C, pH ${fmt(phMin, 2)}–${fmt(phMax, 2)}, " +
        "conductividad ${fmt(condMin, 2)}–${fmt(condMax, 2)} mS/cm, turbidez mín. ${fmt(turbMin, 0)} %"

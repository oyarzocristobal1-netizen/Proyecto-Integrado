package com.example.myapplication.model.analysis

/**
 * Interpretacion conjunta: relaciona los parametros alterados en vez de mostrar alertas sueltas.
 * Solo propone relaciones plausibles y siempre como posibilidades, sin asumir una causa comun.
 */
internal object CombinationCards {

    fun cards(altered: List<ParameterFinding>, scenario: AnalysisScenario): List<AnalysisCard> {
        if (altered.size < 2) return emptyList()
        val result = mutableListOf<AnalysisCard>()

        val all = altered.all { it.level == DeviationLevel.SLIGHT }
        val pairs = pairCards(altered)

        when {
            altered.size == 4 && !all -> result += fourCard()
            altered.size == 3 && !all -> result += threeCard(altered, pairs)
        }
        if (all) result += mildCard(altered)
        // Con desviaciones solo leves no se sugieren relaciones (seria alarmista); en los demas casos,
        // las relaciones especificas se muestran cuando existen; en un par sin relacion conocida,
        // una tarjeta generica explica que no se debe asumir una causa comun.
        if (!all) result += pairs
        if (altered.size == 2 && !all && pairs.isEmpty()) result += genericPair(altered)
        return result
    }

    private fun has(a: List<ParameterFinding>, p: Parameter, d: Direction) =
        a.any { it.parameter == p && it.direction == d }

    private fun pairCards(a: List<ParameterFinding>): List<AnalysisCard> {
        val out = mutableListOf<AnalysisCard>()
        val ph = Parameter.PH; val cond = Parameter.CONDUCTIVITY; val temp = Parameter.TEMPERATURE; val turb = Parameter.TURBIDITY

        if (has(a, ph, Direction.HIGH) && has(a, cond, Direction.HIGH)) out += AnalysisCard(
            kind = CardKind.COMBINATION,
            title = "pH y conductividad elevados",
            whatHappens = "El pH y la conductividad están por encima del rango al mismo tiempo. Este patrón puede aparecer cuando " +
                "cambia la composición de la leche, por ejemplo con problemas de salud de la ubre como la mastitis, " +
                "pero también con otras causas (almacenamiento, contaminación o medición).",
            possibleCauses = listOf(
                "Cambios en la composición de la leche, posiblemente asociados a la salud de la ubre.",
                "Contaminación o manipulación inadecuada de la muestra.",
                "Sondas sucias o mal calibradas."
            ),
            whatToCheck = listOf(
                "Pruebas de campo en vacas sospechosas (por ejemplo, CMT).",
                "Limpieza y calibración de las sondas de pH y conductividad.",
                "Condiciones de conservación de la muestra."
            ),
            recommendation = "Repita la medición, realice controles adicionales en el hato y, si el patrón se mantiene, " +
                "solicite una evaluación veterinaria. Estos valores no bastan para diagnosticar una enfermedad.",
            parameters = setOf(ph, cond)
        )

        if (has(a, ph, Direction.LOW) && has(a, temp, Direction.HIGH)) out += AnalysisCard(
            kind = CardKind.COMBINATION,
            title = "pH bajo con temperatura elevada",
            whatHappens = "La leche está caliente y además más ácida de lo esperado. Con temperatura alta, las bacterias que " +
                "acidifican la leche se multiplican más rápido, por lo que ambos resultados pueden estar relacionados.",
            possibleCauses = listOf(
                "Enfriamiento insuficiente o interrumpido.",
                "Tiempo prolongado entre el ordeño y el enfriamiento.",
                "Higiene deficiente de equipos y recipientes."
            ),
            whatToCheck = listOf(
                "Funcionamiento del tanque de enfriamiento.",
                "Tiempo transcurrido desde el ordeño.",
                "Higiene de los equipos."
            ),
            recommendation = "Enfríe la leche, revise la cadena de frío y repita la medición. Si el pH sigue bajando, trate el lote como sospechoso.",
            parameters = setOf(ph, temp)
        )

        if (has(a, cond, Direction.LOW) && has(a, turb, Direction.LOW)) out += AnalysisCard(
            kind = CardKind.COMBINATION,
            title = "Conductividad y turbidez bajas",
            whatHappens = "Ambas mediciones sugieren una muestra más diluida de lo normal. Puede estar relacionado con agua u otro " +
                "líquido mezclado con la leche, aunque también con un problema de las sondas.",
            possibleCauses = listOf(
                "Posible dilución con agua (por ejemplo, restos del lavado).",
                "Cambios en la composición de la leche.",
                "Sondas mal sumergidas o sucias."
            ),
            whatToCheck = listOf(
                "Posibles fuentes de agua en el recipiente o en el circuito.",
                "Limpieza y posición de las sondas.",
                "Repetir con otra porción de la muestra."
            ),
            recommendation = "Verifique las sondas, repita la medición con otra porción y revise posibles fuentes de dilución.",
            parameters = setOf(cond, turb)
        )

        if (has(a, temp, Direction.HIGH) && has(a, cond, Direction.HIGH)) out += AnalysisCard(
            kind = CardKind.COMBINATION,
            title = "Temperatura y conductividad elevadas",
            whatHappens = "La conductividad aumenta con la temperatura, por lo que parte del aumento podría deberse a que la " +
                "muestra está caliente y no a un cambio real en la leche.",
            possibleCauses = listOf(
                "Muestra medida sin haberse enfriado.",
                "Cambios reales en la composición de la leche (a confirmar)."
            ),
            whatToCheck = listOf(
                "Enfriar la muestra y volver a medir la conductividad.",
                "Funcionamiento del sistema de enfriamiento."
            ),
            recommendation = "Enfríe la leche y repita la medición antes de sospechar un problema de salud de la ubre.",
            parameters = setOf(temp, cond)
        )

        if (has(a, temp, Direction.HIGH) && a.any { it.parameter == turb }) out += AnalysisCard(
            kind = CardKind.COMBINATION,
            title = "Temperatura y turbidez fuera de rango",
            whatHappens = "La temperatura y la turbidez están fuera de rango, pero no se debe asumir una causa común: la temperatura " +
                "describe la conservación y la turbidez las características físicas de la muestra.",
            possibleCauses = listOf(
                "Un problema de refrigeración (temperatura).",
                "Dilución, partículas o suciedad del sensor (turbidez).",
                "Dos situaciones independientes que coinciden."
            ),
            whatToCheck = listOf(
                "Refrigeración y tanque de enfriamiento.",
                "Limpieza del sensor y manipulación de la muestra."
            ),
            recommendation = "Atienda por separado cada alteración y repita la medición después de enfriar y limpiar.",
            parameters = setOf(temp, turb)
        )
        return out
    }

    private fun genericPair(a: List<ParameterFinding>): AnalysisCard {
        val names = joinNames(a.map { it.parameter.label })
        return AnalysisCard(
            kind = CardKind.COMBINATION,
            title = "Dos parámetros fuera de rango",
            whatHappens = "$names están fuera de rango. No hay una relación única conocida entre ellos para estos valores: " +
                "pueden tener causas independientes.",
            possibleCauses = listOf(
                "Dos situaciones independientes.",
                "Un problema común de manipulación o conservación de la muestra.",
                "Una falla de medición."
            ),
            whatToCheck = listOf(
                "Cada parámetro por separado, según su tarjeta.",
                "Condiciones de almacenamiento y manipulación."
            ),
            recommendation = "Repita la medición para confirmar si ambos se mantienen y atienda cada alteración.",
            parameters = a.map { it.parameter }.toSet()
        )
    }

    private fun threeCard(a: List<ParameterFinding>, pairs: List<AnalysisCard>): AnalysisCard {
        val names = joinNames(a.map { it.parameter.label })
        val relations = if (pairs.isEmpty()) "No se identifica una relación única entre ellos."
        else "Posibles relaciones: " + pairs.joinToString("; ") { it.title.lowerFirst() } + "."
        return AnalysisCard(
            kind = CardKind.COMBINATION,
            title = "Tres parámetros alterados: requiere mayor atención",
            whatHappens = "Hay tres parámetros fuera de rango a la vez ($names). $relations " +
                "Esta situación merece más atención que una alteración aislada.",
            possibleCauses = listOf(
                "Un problema de fondo en la conservación o manipulación de la muestra.",
                "Varias situaciones independientes.",
                "Falla o mala calibración de los sensores."
            ),
            whatToCheck = listOf(
                "Cada tarjeta individual, empezando por la alteración más grave.",
                "Condiciones de almacenamiento.",
                "Estado y calibración de los sensores."
            ),
            recommendation = "Repita la medición con otra porción de la muestra y revise primero la alteración más grave. " +
                "Hasta confirmar, no clasifique la muestra como satisfactoria.",
            parameters = a.map { it.parameter }.toSet()
        )
    }

    private fun fourCard() = AnalysisCard(
        kind = CardKind.COMBINATION,
        title = "Cuatro parámetros alterados: atención prioritaria",
        whatHappens = "Los cuatro parámetros están fuera de rango. Que todos se alteren a la vez puede indicar un problema importante " +
            "en las condiciones de la muestra, su conservación, su manipulación o en la propia medición.",
        possibleCauses = listOf(
            "Muestra deteriorada o contaminada.",
            "Conservación y manipulación inadecuadas.",
            "Sensores desconectados, sucios o mal calibrados.",
            "Lectura tomada antes de que la muestra se estabilizara."
        ),
        whatToCheck = listOf(
            "Conexión, limpieza y calibración de los cuatro sensores.",
            "Condiciones de almacenamiento y temperatura.",
            "Que la muestra sea la correcta y esté bien mezclada."
        ),
        recommendation = "Detenga la clasificación automática de la muestra como satisfactoria, verifique los sensores, " +
            "repita la medición y revise las condiciones de almacenamiento.",
        parameters = Parameter.entries.toSet()
    )

    private fun mildCard(a: List<ParameterFinding>): AnalysisCard {
        val names = joinNames(a.map { it.parameter.label })
        val extra = if (a.size >= 3) {
            " Que varios parámetros se desvíen un poco a la vez también puede deberse a la medición (sondas sucias o muestra sin estabilizar)."
        } else ""
        return AnalysisCard(
            kind = CardKind.COMBINATION,
            title = "Desviaciones leves: seguimiento preventivo",
            whatHappens = "$names están ligeramente fuera de rango. Son desviaciones pequeñas: no se consideran críticas, pero conviene vigilarlas.$extra",
            possibleCauses = listOf(
                "Variación normal de la muestra.",
                "Sensores que aún se están ajustando.",
                "Inicio de un cambio en la conservación."
            ),
            whatToCheck = listOf(
                "Una nueva medición en unos minutos.",
                "Limpieza de las sondas."
            ),
            recommendation = "Use esto como advertencia preventiva: repita la medición y haga seguimiento en las siguientes muestras.",
            parameters = a.map { it.parameter }.toSet()
        )
    }

    private fun String.lowerFirst() = replaceFirstChar { it.lowercase() }
}

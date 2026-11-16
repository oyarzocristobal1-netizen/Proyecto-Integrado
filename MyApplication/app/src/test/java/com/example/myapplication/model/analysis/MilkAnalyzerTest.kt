package com.example.myapplication.model.analysis

import com.example.myapplication.model.QualityRanges
import com.example.myapplication.model.ReadingSource
import com.example.myapplication.model.StabilityState
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

private const val HOUR = 3_600_000L
private const val MIN = 60_000L

/** Lectura base normal con los rangos por defecto (temp <= 4, pH 6.6-6.8, CE 4.0-5.5, turbidez >= 50). */
private fun reading(
    t: Float = 3.8f, ph: Float = 6.7f, cond: Float = 4.8f, turb: Float = 85f,
    ts: Long = 10 * HOUR, lot: String? = "lote-1", source: ReadingSource = ReadingSource.SIMULATED
) = AnalysisReading(ts, t, ph, cond, turb, source, lot)

private fun analyze(
    current: AnalysisReading,
    previous: List<AnalysisReading> = emptyList(),
    config: AnalysisConfig = AnalysisConfig(),
    ranges: QualityRanges = QualityRanges(),
    stability: StabilityState? = null
) = MilkAnalyzer.analyze(current, previous, ranges, config, stability)

private fun MilkAnalysis.level(p: Parameter) = findings.first { it.parameter == p }.level
private fun MilkAnalysis.allCards() = parameterCards + combinedCards + historyCards + reliabilityCards

class MilkAnalyzerTest {

    // ----------------------------------------------------------- normal y un parametro (escenario A)

    @Test
    fun todoNormalDaPrioridadNormalSinTarjetas() {
        val a = analyze(reading())
        assertEquals(AnalysisScenario.NORMAL, a.scenario)
        assertEquals(PriorityLevel.NORMAL, a.priority)
        assertTrue(a.allCards().isEmpty())
        assertTrue(a.reliable)
        assertTrue(a.headline.startsWith("Normal"))
    }

    @Test
    fun unSoloParametroAlteradoExplicaSoloEse() {
        val a = analyze(reading(ph = 7.0f))
        assertEquals(AnalysisScenario.SINGLE, a.scenario)
        assertEquals(1, a.parameterCards.size)
        assertEquals(setOf(Parameter.PH), a.parameterCards.first().parameters)
        assertEquals(DeviationLevel.IN_RANGE, a.level(Parameter.CONDUCTIVITY))
        assertTrue(a.combinedCards.isEmpty())
        assertTrue(a.parameterCards.first().whatHappens.contains("por encima"))
        assertTrue(a.parameterCards.first().possibleCauses.isNotEmpty())
        assertTrue(a.parameterCards.first().whatToCheck.isNotEmpty())
        assertTrue(a.summary.contains("Solo"))
    }

    // ----------------------------------------------------------- niveles de gravedad

    @Test
    fun phTieneCincoNivelesYCadaUnoExplicaDistinto() {
        val values = listOf(6.85f to DeviationLevel.SLIGHT, 7.0f to DeviationLevel.MODERATE,
            7.1f to DeviationLevel.CONSIDERABLE, 7.4f to DeviationLevel.CRITICAL)
        val cards = values.map { (v, expected) ->
            val a = analyze(reading(ph = v))
            assertEquals("pH $v", expected, a.level(Parameter.PH))
            a.parameterCards.first()
        }
        assertEquals(4, cards.map { it.whatHappens }.toSet().size)
        assertEquals(4, cards.map { it.recommendation }.toSet().size)
        assertEquals(4, cards.map { it.title }.toSet().size)
    }

    @Test
    fun cadaParametroClasificaLigeroModeradoConsiderableYCritico() {
        fun levels(make: (Float) -> AnalysisReading, vararg v: Float, p: Parameter) = v.map { analyze(make(it)).level(p) }
        assertEquals(
            listOf(DeviationLevel.SLIGHT, DeviationLevel.MODERATE, DeviationLevel.CONSIDERABLE, DeviationLevel.CRITICAL),
            levels({ reading(t = it) }, 5.0f, 8.0f, 15.0f, 26.0f, p = Parameter.TEMPERATURE)
        )
        assertEquals(
            listOf(DeviationLevel.SLIGHT, DeviationLevel.MODERATE, DeviationLevel.CONSIDERABLE, DeviationLevel.CRITICAL),
            levels({ reading(cond = it) }, 5.7f, 6.0f, 6.8f, 7.5f, p = Parameter.CONDUCTIVITY)
        )
        assertEquals(
            listOf(DeviationLevel.SLIGHT, DeviationLevel.MODERATE, DeviationLevel.CONSIDERABLE, DeviationLevel.CRITICAL),
            levels({ reading(cond = it) }, 3.8f, 3.6f, 3.2f, 2.5f, p = Parameter.CONDUCTIVITY)
        )
        assertEquals(
            listOf(DeviationLevel.SLIGHT, DeviationLevel.MODERATE, DeviationLevel.CONSIDERABLE, DeviationLevel.CRITICAL),
            levels({ reading(turb = it) }, 47f, 40f, 28f, 10f, p = Parameter.TURBIDITY)
        )
    }

    @Test
    fun elRedondeoDeFloatNoCambiaElNivel() {
        // 7.0 - 6.8 da 0.20000005 en Float: debe seguir siendo "moderada" (limite 0.2), no "considerable".
        assertEquals(DeviationLevel.MODERATE, analyze(reading(ph = 7.0f)).level(Parameter.PH))
    }

    @Test
    fun temperaturaSobre25EsSiempreCritica() {
        assertEquals(DeviationLevel.CRITICAL, analyze(reading(t = 25.5f)).level(Parameter.TEMPERATURE))
        assertEquals(PriorityLevel.CRITICAL, analyze(reading(t = 26f)).priority)
    }

    @Test
    fun laPrioridadNoDependeSoloDeLaCantidadDeParametros() {
        // Un solo parametro critico es CRITICO; cuatro desviaciones leves siguen siendo PREVENTIVO.
        assertEquals(PriorityLevel.CRITICAL, analyze(reading(t = 26f)).priority)
        val leves = analyze(reading(t = 5.0f, ph = 6.85f, cond = 5.7f, turb = 47f))
        assertEquals(4, leves.altered.size)
        assertEquals(PriorityLevel.PREVENTIVE, leves.priority)
    }

    // ----------------------------------------------------------- combinaciones (escenarios B a E)

    @Test
    fun escenarioBPhYConductividadElevadosSeExplicanJuntos() {
        val a = analyze(reading(ph = 7.0f, cond = 6.4f))
        assertEquals(AnalysisScenario.TWO, a.scenario)
        assertEquals(PriorityLevel.WARNING, a.priority)
        val combo = a.combinedCards.single { it.title == "pH y conductividad elevados" }
        assertTrue(combo.whatHappens.contains("mastitis"))
        assertTrue("no debe diagnosticar", combo.recommendation.contains("no bastan para diagnosticar"))
        assertEquals(2, a.parameterCards.size)
    }

    @Test
    fun escenarioBTemperaturaYTurbidezNoAsumenCausaComun() {
        val a = analyze(reading(t = 12f, turb = 38f))
        assertEquals(AnalysisScenario.TWO, a.scenario)
        val combo = a.combinedCards.single { it.title == "Temperatura y turbidez fuera de rango" }
        assertTrue(combo.whatHappens.contains("no se debe asumir una causa común"))
    }

    @Test
    fun escenarioBPhBajoConTemperaturaAltaYDilucion() {
        assertTrue(analyze(reading(t = 14f, ph = 6.3f)).combinedCards.any { it.title == "pH bajo con temperatura elevada" })
        assertTrue(analyze(reading(cond = 3.0f, turb = 40f)).combinedCards.any { it.title == "Conductividad y turbidez bajas" })
        assertTrue(analyze(reading(t = 14f, cond = 6.2f)).combinedCards.any { it.title == "Temperatura y conductividad elevadas" })
    }

    @Test
    fun dosParametrosSinRelacionConocidaUsanTarjetaGenerica() {
        val a = analyze(reading(ph = 6.3f, cond = 6.4f))
        assertEquals(AnalysisScenario.TWO, a.scenario)
        val card = a.combinedCards.single()
        assertEquals("Dos parámetros fuera de rango", card.title)
        assertTrue(card.whatHappens.contains("causas independientes"))
    }

    @Test
    fun escenarioCTresParametrosRequiereMayorAtencion() {
        val a = analyze(reading(t = 8f, ph = 7.0f, cond = 6.0f))
        assertEquals(AnalysisScenario.THREE, a.scenario)
        assertEquals(PriorityLevel.WARNING, a.priority)
        assertTrue(a.combinedCards.any { it.title.startsWith("Tres parámetros alterados") })
        assertEquals(3, a.parameterCards.size)
    }

    @Test
    fun escenarioDCuatroParametrosEsAtencionPrioritaria() {
        val a = analyze(reading(t = 27f, ph = 7.3f, cond = 7.5f, turb = 30f))
        assertEquals(AnalysisScenario.FOUR, a.scenario)
        assertEquals(PriorityLevel.CRITICAL, a.priority)
        val card = a.combinedCards.first { it.title.startsWith("Cuatro parámetros alterados") }
        assertTrue(card.recommendation.contains("clasificación automática"))
        assertTrue(card.recommendation.contains("verifique los sensores"))
        assertTrue(card.recommendation.contains("condiciones de almacenamiento"))
    }

    @Test
    fun escenarioELeveSeTrataComoPreventivoYNoComoCritico() {
        val a = analyze(reading(t = 5.0f, ph = 6.85f, cond = 5.7f, turb = 47f))
        assertEquals(AnalysisScenario.MILD, a.scenario)
        assertEquals(PriorityLevel.PREVENTIVE, a.priority)
        assertTrue(a.combinedCards.single().title.startsWith("Desviaciones leves"))
        assertFalse(a.combinedCards.any { it.title.contains("mastitis") || it.whatHappens.contains("mastitis") })
    }

    @Test
    fun unaDesviacionLeveSugierePreventivoYSeguimiento() {
        val a = analyze(reading(ph = 6.85f))
        assertEquals(PriorityLevel.PREVENTIVE, a.priority)
        assertTrue(a.parameterCards.first().recommendation.startsWith("Preventivo"))
    }

    // ----------------------------------------------------------- lecturas invalidas y contradictorias (escenario F)

    @Test
    fun escenarioFConductividadNulaConMuestraOpacaEsContradictoria() {
        val a = analyze(reading(cond = 0.1f))
        assertEquals(AnalysisScenario.SUSPECT, a.scenario)
        assertFalse(a.reliable)
        assertTrue(a.priority >= PriorityLevel.PREVENTIVE)
        assertTrue(a.headline.contains("No se puede interpretar"))
        assertTrue(a.reliabilityCards.single().whatHappens.contains("contradictorios"))
        assertTrue(a.followUp.contains("repita la medición"))
        assertEquals(DeviationLevel.UNRELIABLE, a.level(Parameter.CONDUCTIVITY))
    }

    @Test
    fun temperaturaBajoCeroOValoresEnElExtremoSonDudosos() {
        assertEquals(AnalysisScenario.SUSPECT, analyze(reading(t = -3f)).scenario)
        assertEquals(AnalysisScenario.SUSPECT, analyze(reading(ph = 14f)).scenario)
        assertEquals(AnalysisScenario.SUSPECT, analyze(reading(turb = 0f)).scenario)
        assertEquals(AnalysisScenario.SUSPECT, analyze(reading(t = 0f, ph = 0f, cond = 0f, turb = 0f)).scenario)
    }

    @Test
    fun valoresNoNumericosOFueraDeLosLimitesDelSensorNoRompenElAnalisis() {
        val nan = analyze(reading(ph = Float.NaN))
        assertEquals(AnalysisScenario.SUSPECT, nan.scenario)
        assertEquals(DeviationLevel.UNRELIABLE, nan.level(Parameter.PH))
        assertEquals(AnalysisScenario.SUSPECT, analyze(reading(t = -127f)).scenario)
        assertEquals(AnalysisScenario.SUSPECT, analyze(reading(cond = 50f)).scenario)
    }

    @Test
    fun unaLecturaInestableNoSeInterpretaComoConfiable() {
        val a = analyze(reading(), stability = StabilityState(isStable = false, samples = 2, required = 4))
        assertEquals(AnalysisScenario.SUSPECT, a.scenario)
        assertTrue(a.reliabilityCards.single().whatHappens.contains("estabiliza"))
        val ok = analyze(reading(), stability = StabilityState(isStable = true, samples = 4, required = 4))
        assertEquals(AnalysisScenario.NORMAL, ok.scenario)
    }

    @Test
    fun conUnaLecturaDudosaNoSeAnalizaElHistorial() {
        val a = analyze(reading(cond = 0.1f), previous = listOf(reading(ts = 8 * HOUR), reading(ts = 9 * HOUR)))
        assertTrue(a.historyCards.isEmpty())
        assertTrue(a.historyNote!!.contains("dudosa"))
    }

    // ----------------------------------------------------------- precision de lo que mide cada sensor

    @Test
    fun laTurbidezNuncaSeInterpretaComoViscosidad() {
        val a = analyze(reading(turb = 40f))
        assertTrue(a.parameterCards.single().whatHappens.contains("No mide viscosidad ni espesor"))
    }

    @Test
    fun laTurbidezElevadaSoloSeEvaluaSiSeDefineUnMaximo() {
        assertEquals(AnalysisScenario.NORMAL, analyze(reading(turb = 98f)).scenario)
        val cfg = AnalysisConfig(turbidityMax = 90f)
        val a = analyze(reading(turb = 98f), config = cfg)
        assertEquals(DeviationLevel.MODERATE, a.level(Parameter.TURBIDITY))
        val card = a.parameterCards.single()
        assertTrue(card.possibleCauses.any { it.contains("partículas") })
        assertTrue(card.whatHappens.contains("No mide viscosidad ni espesor"))
        assertTrue(card.recommendation.contains("limpieza de los equipos"))
    }

    @Test
    fun laConductividadElevadaNoDiagnosticaYSugiereControlesYVeterinario() {
        val card = analyze(reading(cond = 6.0f)).parameterCards.single()
        assertTrue(card.whatHappens.contains("no identifica enfermedades por sí sola"))
        assertTrue(card.possibleCauses.any { it.contains("mastitis") && it.contains("no permite diagnosticarla") })
        assertTrue(card.recommendation.contains("evaluación veterinaria"))
        assertTrue(card.recommendation.contains("No concluya una enfermedad"))
    }

    @Test
    fun laTemperaturaElevadaExplicaRefrigeracionYTanque() {
        val card = analyze(reading(t = 8f)).parameterCards.single()
        assertTrue(card.possibleCauses.any { it.contains("refrigeración") })
        assertTrue(card.possibleCauses.any { it.contains("tanque") })
        assertTrue(card.whatToCheck.any { it.contains("compresor") })
    }

    @Test
    fun laConductividadAltaConTemperaturaAltaAvisaQueParteSePuedeDeberALaTemperatura() {
        val combo = analyze(reading(t = 14f, cond = 6.2f)).combinedCards.single { it.title == "Temperatura y conductividad elevadas" }
        assertTrue(combo.whatHappens.contains("aumenta con la temperatura"))
    }

    // ----------------------------------------------------------- todas las combinaciones

    @Test
    fun lasDieciseisCombinacionesDeParametrosAlteradosTienenInterpretacionCoherente() {
        for (mask in 0 until 16) {
            val r = reading(
                t = if (mask and 1 != 0) 8f else 3.8f,
                ph = if (mask and 2 != 0) 7.0f else 6.7f,
                cond = if (mask and 4 != 0) 6.0f else 4.8f,
                turb = if (mask and 8 != 0) 38f else 85f
            )
            val n = Integer.bitCount(mask)
            val a = analyze(r)
            val label = "combinación $mask"
            assertEquals(label, n, a.altered.size)
            assertEquals(label, n, a.parameterCards.size)
            assertEquals(label, a.altered.map { it.parameter }.toSet(), a.parameterCards.flatMap { it.parameters }.toSet())
            assertTrue(label, a.headline.isNotBlank() && a.summary.isNotBlank() && a.followUp.isNotBlank())
            when (n) {
                0 -> { assertEquals(label, AnalysisScenario.NORMAL, a.scenario); assertEquals(label, PriorityLevel.NORMAL, a.priority) }
                1 -> assertEquals(label, AnalysisScenario.SINGLE, a.scenario)
                2 -> assertEquals(label, AnalysisScenario.TWO, a.scenario)
                3 -> assertEquals(label, AnalysisScenario.THREE, a.scenario)
                else -> assertEquals(label, AnalysisScenario.FOUR, a.scenario)
            }
            if (n >= 2) assertTrue(label, a.combinedCards.isNotEmpty())
            if (n in 1..3) assertEquals(label, PriorityLevel.WARNING, a.priority)
            if (n == 4) assertEquals(label, PriorityLevel.CRITICAL, a.priority)
            if (n > 0) assertTrue(label, a.actions.isNotEmpty())
            a.allCards().forEach {
                assertTrue(label, it.possibleCauses.isNotEmpty() && it.whatToCheck.isNotEmpty() && it.recommendation.isNotBlank())
            }
        }
    }

    // ----------------------------------------------------------- historial

    @Test
    fun sinHistorialSeAvisaYNoSeInventanTendencias() {
        val a = analyze(reading(ph = 7.0f))
        assertEquals(1, a.samplesAnalyzed)
        assertTrue(a.historyCards.isEmpty())
        assertTrue(a.historyNote!!.contains("Aún no hay mediciones anteriores"))
    }

    @Test
    fun phElevadoUnaVezEsAlertaPreventivaYRepetidoSeAdviertePersistente() {
        val once = analyze(reading(ph = 7.0f))
        assertEquals(PriorityLevel.WARNING, once.priority)
        assertTrue(once.historyCards.isEmpty())

        val twice = analyze(reading(ph = 7.0f, ts = 10 * HOUR), listOf(reading(ph = 7.0f, ts = 9 * HOUR)))
        assertTrue(twice.historyCards.any { it.title == "pH vuelve a aparecer fuera de rango" })
        assertEquals(PriorityLevel.WARNING, twice.priority)

        val prev = listOf(reading(ph = 7.0f, ts = 8 * HOUR), reading(ph = 7.0f, ts = 9 * HOUR))
        val thrice = analyze(reading(ph = 7.0f, ts = 10 * HOUR), prev)
        val card = thrice.historyCards.first { it.title.startsWith("pH fuera de rango de forma persistente") }
        assertTrue(card.whatHappens.contains("3 muestras distintas"))
        assertEquals("la condicion persistente sube un nivel", PriorityLevel.CRITICAL, thrice.priority)
    }

    @Test
    fun variasMedicionesDeLaMismaMuestraNoCuentanComoVariasMuestras() {
        val prev = listOf(reading(ph = 7.0f, ts = 10 * HOUR - 2 * MIN), reading(ph = 7.0f, ts = 10 * HOUR - 1 * MIN))
        val a = analyze(reading(ph = 7.0f, ts = 10 * HOUR), prev)
        assertEquals(1, a.samplesAnalyzed)
        assertTrue(a.historyCards.isEmpty())
        assertEquals(PriorityLevel.WARNING, a.priority)
        assertTrue(a.historyNote!!.contains("misma muestra"))
    }

    @Test
    fun temperaturaQueAumentaProgresivamenteSeDetectaYSubeLaPrioridad() {
        val prev = listOf(reading(t = 3.5f, ts = 7 * HOUR), reading(t = 4.0f, ts = 8 * HOUR), reading(t = 5.5f, ts = 9 * HOUR))
        val a = analyze(reading(t = 7.0f, ts = 10 * HOUR), prev)
        val trend = a.historyCards.single { it.title == "Temperatura aumenta progresivamente" }
        assertTrue(trend.whatHappens.contains("en 4 muestras"))
        assertTrue(a.historyCards.any { it.title.startsWith("Temperatura") && it.title.contains("persistente") || it.title.contains("vuelve") })
        assertEquals(PriorityLevel.CRITICAL, a.priority) // WARNING + tendencia/repeticion
    }

    @Test
    fun unaTendenciaQueEmpeoraAunDentroDelRangoGeneraAvisoPreventivo() {
        val prev = listOf(reading(t = 2.0f, ts = 8 * HOUR), reading(t = 2.8f, ts = 9 * HOUR))
        val a = analyze(reading(t = 3.6f, ts = 10 * HOUR), prev)
        assertEquals(AnalysisScenario.NORMAL, a.scenario)
        assertEquals(PriorityLevel.PREVENTIVE, a.priority)
        assertTrue(a.headline.startsWith("Preventivo"))
        assertTrue(a.historyCards.any { it.title == "Temperatura aumenta progresivamente" })
    }

    @Test
    fun conductividadConIncrementosRepetidosYTurbidezQueBajaSeDetectan() {
        val cond = analyze(
            reading(cond = 6.0f, ts = 10 * HOUR),
            listOf(reading(cond = 4.8f, ts = 7 * HOUR), reading(cond = 5.2f, ts = 8 * HOUR), reading(cond = 5.7f, ts = 9 * HOUR))
        )
        assertTrue(cond.historyCards.any { it.title == "Conductividad aumenta progresivamente" })
        assertTrue(cond.historyCards.any { it.title.contains("vuelve a aparecer") || it.title.contains("persistente") })

        val turb = analyze(
            reading(turb = 55f, ts = 10 * HOUR),
            listOf(reading(turb = 85f, ts = 8 * HOUR), reading(turb = 70f, ts = 9 * HOUR))
        )
        assertEquals(AnalysisScenario.NORMAL, turb.scenario)
        assertTrue(turb.historyCards.any { it.title == "Turbidez disminuye progresivamente" })
        assertEquals(PriorityLevel.PREVENTIVE, turb.priority)
    }

    @Test
    fun unaTendenciaDescendenteHaciaLoNormalSeReconoceComoMejora() {
        val prev = listOf(reading(t = 9f, ts = 8 * HOUR), reading(t = 7f, ts = 9 * HOUR))
        val a = analyze(reading(t = 5.0f, ts = 10 * HOUR), prev)
        val card = a.historyCards.single { it.title == "Temperatura disminuye hacia su valor normal" }
        assertEquals(DeviationLevel.IN_RANGE, card.level)
        assertEquals(PriorityLevel.PREVENTIVE, a.priority) // sigue ligeramente alta, pero mejorando
    }

    @Test
    fun conSoloDosMuestrasNoSeHablaDeTendencia() {
        val a = analyze(reading(t = 3.9f, ts = 10 * HOUR), listOf(reading(t = 1.0f, ts = 9 * HOUR)))
        assertFalse(a.historyCards.any { it.title.contains("progresivamente") })
    }

    @Test
    fun variosParametrosQueEmpiezanAAlterarseALaVezSeAdvierte() {
        val a = analyze(reading(ph = 7.0f, cond = 6.0f, ts = 10 * HOUR), listOf(reading(ts = 9 * HOUR)))
        assertTrue(a.historyCards.any { it.title == "Varios parámetros comenzaron a alterarse a la vez" })
        assertEquals(PriorityLevel.CRITICAL, a.priority)
    }

    @Test
    fun unParametroQueVuelveAlRangoSeIndicaYBajaLaPrioridad() {
        val a = analyze(reading(ts = 10 * HOUR), listOf(reading(ph = 7.0f, ts = 9 * HOUR)))
        assertEquals(PriorityLevel.NORMAL, a.priority)
        assertTrue(a.historyCards.single().title == "pH volvió al rango")
    }

    @Test
    fun elHistorialDeOtroLoteOOrigenNoSeMezcla() {
        val otroLote = reading(ph = 7.0f, ts = 9 * HOUR, lot = "lote-2")
        val real = reading(ph = 7.0f, ts = 8 * HOUR, source = ReadingSource.DEVICE)
        val a = analyze(reading(ph = 7.0f, ts = 10 * HOUR), listOf(otroLote, real))
        assertEquals(1, a.samplesAnalyzed)
        assertTrue(a.historyCards.isEmpty())
    }

    @Test
    fun elHistorialIgnoraLecturasDudosasYPosteriores() {
        val dudosa = reading(cond = 0.1f, ts = 9 * HOUR)
        val futura = reading(ph = 7.0f, ts = 11 * HOUR)
        val a = analyze(reading(ph = 7.0f, ts = 10 * HOUR), listOf(dudosa, futura))
        assertEquals(1, a.samplesAnalyzed)
    }

    // ----------------------------------------------------------- configuracion, DEMO y limites

    @Test
    fun losRangosYMargenesSonConfigurables() {
        assertEquals(DeviationLevel.IN_RANGE, analyze(reading(ph = 7.0f), ranges = QualityRanges(phMax = 7.2f)).level(Parameter.PH))
        val strict = AnalysisConfig(phBand = Band(slight = 0.05f, moderate = 0.1f, considerable = 0.15f))
        assertEquals(DeviationLevel.CRITICAL, analyze(reading(ph = 7.0f), config = strict).level(Parameter.PH))
    }

    @Test
    fun losResultadosDemoSeIdentificanYNuncaSePresentanComoReales() {
        val demo = analyze(reading(ph = 7.0f, source = ReadingSource.SIMULATED))
        assertTrue(demo.simulated)
        assertTrue(demo.disclaimer.contains("SIMULADOS"))
        val real = analyze(reading(ph = 7.0f, source = ReadingSource.DEVICE))
        assertFalse(real.simulated)
        assertFalse(real.disclaimer.contains("SIMULADOS"))
    }

    @Test
    fun lasCausasSePresentanComoPosibilidadesNoComoDiagnosticos() {
        val a = analyze(reading(t = 27f, ph = 7.3f, cond = 7.5f, turb = 30f))
        assertTrue(a.disclaimer.contains("no diagnósticos confirmados"))
        a.allCards().forEach { card ->
            val text = (listOf(card.whatHappens, card.recommendation) + card.possibleCauses).joinToString(" ").lowercase()
            assertFalse(card.title, text.contains("tiene mastitis") || text.contains("diagnóstico confirmado") || text.contains("se confirma"))
        }
    }

    @Test
    fun lasAccionesSonPocasYSinRepetirYLaMasUrgentePrimero() {
        val a = analyze(reading(t = 27f, ph = 7.3f, cond = 7.5f, turb = 30f))
        assertTrue(a.actions.size <= 5)
        assertEquals(a.actions.size, a.actions.toSet().size)
        assertTrue(a.actions.first().startsWith("Atención inmediata"))
    }

    @Test
    fun textosDeDistintoNivelNoSonIguales() {
        val slight = analyze(reading(t = 5f)).parameterCards.single()
        val critical = analyze(reading(t = 27f)).parameterCards.single()
        assertNotEquals(slight.whatHappens, critical.whatHappens)
        assertNotEquals(slight.recommendation, critical.recommendation)
    }
}

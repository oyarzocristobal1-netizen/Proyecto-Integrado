package com.example.myapplication.model

import com.example.myapplication.model.analysis.VerdictLevel

/** Decision del encargado sobre si un lote se puede vender (se guarda en el lote). */
enum class SaleDecision(val id: String, val label: String) {
    APTO("apto", "Apto para venta"),
    NO_APTO("no_apto", "No apto para venta");

    companion object {
        fun fromId(id: String?): SaleDecision? = entries.firstOrNull { it.id == id }
    }
}

/**
 * Reglas para liberar un lote para venta. El analisis solo sugiere: la decision es del encargado,
 * y cuando decide algo distinto de lo que sugiere el analisis debe dejar escrito por que.
 * Con datos simulados (modo DEMO) la decision es solo de demostracion.
 */
object SaleRelease {
    const val NOTE_MAX = 200
    const val NOTE_MIN_WHEN_REQUIRED = 3

    /** Decision que sugiere el veredicto del analisis; null si el encargado debe evaluar con criterio propio. */
    fun suggestion(verdict: VerdictLevel?): SaleDecision? = when (verdict) {
        VerdictLevel.GOOD -> SaleDecision.APTO
        VerdictLevel.STOP -> SaleDecision.NO_APTO
        VerdictLevel.REVIEW, null -> null
    }

    /** Motivo por el que la nota es obligatoria, o null si es opcional. */
    fun noteRequiredReason(decision: SaleDecision, verdict: VerdictLevel?, overdue: Boolean): String? = when {
        decision != SaleDecision.APTO -> null
        verdict == VerdictLevel.STOP -> "El análisis marcó este lote como \"No usar hasta verificar\": explica por qué lo liberas."
        verdict == VerdictLevel.REVIEW -> "El análisis pide revisar este lote: explica por qué lo liberas."
        overdue -> "La última medición del lote está atrasada: explica por qué lo liberas igual."
        else -> null
    }

    /** Devuelve un mensaje si la decision no se puede guardar, o null si esta bien. */
    fun validate(
        decision: SaleDecision?,
        verdict: VerdictLevel?,
        note: String,
        measurementCount: Int,
        overdue: Boolean
    ): String? {
        val clean = note.trim()
        val required = decision?.let { noteRequiredReason(it, verdict, overdue) }
        return when {
            decision == null -> "Elige si el lote es apto o no apto para venta."
            measurementCount <= 0 -> "El lote no tiene mediciones válidas: mide antes de decidir."
            clean.length > NOTE_MAX -> "La nota no puede superar $NOTE_MAX caracteres."
            required != null && clean.length < NOTE_MIN_WHEN_REQUIRED -> required
            else -> null
        }
    }
}

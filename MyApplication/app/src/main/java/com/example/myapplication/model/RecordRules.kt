package com.example.myapplication.model

import java.text.Normalizer
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone

/** Reglas de registro independientes de Firebase (se prueban sin Android). */
object RecordRules {
    /** Caja de sensores real registrada en Firestore. */
    const val DEVICE_ID = "caja-01"

    /** "Dispositivo" con el que se guardan las mediciones del modo DEMO. */
    const val SIMULATOR_DEVICE_ID = "simulador"

    fun deviceIdFor(source: ReadingSource): String =
        if (source == ReadingSource.DEVICE) DEVICE_ID else SIMULATOR_DEVICE_ID

    /** Semaforo del informe (RF3): verde, ambar o rojo. */
    fun statusFor(level: QualityLevel): String = when (level) {
        QualityLevel.EXCELLENT -> "verde"
        QualityLevel.ACCEPTABLE, QualityLevel.WARNING -> "ambar"
        QualityLevel.REJECTED -> "rojo"
    }

    /**
     * ID del lote: 20261112-estanque-1 (fecha local + estanque sin tildes ni espacios).
     * Asi hay un solo lote por estanque y dia aunque dos personas lo creen a la vez.
     */
    fun lotIdFor(tank: String, date: Date, timeZone: TimeZone = TimeZone.getDefault()): String {
        val day = SimpleDateFormat("yyyyMMdd", Locale.US).apply { this.timeZone = timeZone }.format(date)
        val slug = Normalizer.normalize(tank.trim().lowercase(Locale.ROOT), Normalizer.Form.NFD)
            .replace(Regex("\\p{M}"), "")
            .replace(Regex("[^a-z0-9]+"), "-")
            .trim('-')
            .ifEmpty { "estanque" }
        return "$day-$slug"
    }
}

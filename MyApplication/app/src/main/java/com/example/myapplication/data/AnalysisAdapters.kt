package com.example.myapplication.data

import com.example.myapplication.model.analysis.AnalysisReading

/** Convierte una medicion guardada en la lectura que usa el motor de analisis. */
fun SavedMeasurement.asAnalysisReading() = AnalysisReading(
    timestamp = dateTime.time,
    temperature = temperature.toFloat(),
    ph = ph.toFloat(),
    conductivity = conductivity.toFloat(),
    turbidity = turbidity.toFloat(),
    source = source,
    lotId = lotId
)

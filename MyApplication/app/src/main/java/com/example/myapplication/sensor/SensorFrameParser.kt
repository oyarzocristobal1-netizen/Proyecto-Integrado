package com.example.myapplication.sensor

import com.example.myapplication.model.MilkQualityReading
import com.example.myapplication.model.ReadingSource

/** Resultado de interpretar una linea recibida de la caja. */
sealed interface FrameResult {
    /** Linea completa y bien formada: se obtuvo una lectura del sensor. */
    data class Reading(val reading: MilkQualityReading) : FrameResult

    /** Parte de un ciclo de varias lineas; aun faltan valores. */
    data object Partial : FrameResult

    /** Linea con el formato esperado pero con datos faltantes o corruptos: se descarta. */
    data class Invalid(val reason: String) : FrameResult

    /** Linea que no es de datos (mensajes de depuracion, lineas vacias): se ignora. */
    data object Ignored : FrameResult
}

/**
 * Interpreta las lineas que envia la caja por Bluetooth (protocolo en docs/protocolo-caja.md).
 *
 * Formatos aceptados:
 *  1. `DATA:<temp>,<ph>,<ce>,<turb>`         (formato principal del firmware)
 *  2. `T:<temp>,PH:<ph>,CE:<ce>,TURB:<turb>`  (clave:valor, en cualquier orden)
 *  3. Ciclo de 4 lineas: `Temperatura: x` / `pH: x` / `Conductividad: x` / `Turbidez: x`
 *
 * Nunca completa valores faltantes con numeros por defecto: si falta un dato o no es un
 * numero, la linea se descarta (Invalid). Los decimales deben usar punto.
 */
class SensorFrameParser(private val clock: () -> Long = System::currentTimeMillis) {

    private val pending = mutableMapOf<String, Float>()

    fun parse(rawLine: String): FrameResult {
        val line = rawLine.trim().trimEnd('\r')
        if (line.isEmpty()) return FrameResult.Ignored

        return when {
            line.startsWith("DATA:", ignoreCase = true) -> parseData(line.substring(5))
            KEY_VALUE.containsMatchIn(line) && line.contains(',') -> parseKeyValue(line)
            else -> parseMultiline(line)
        }
    }

    private fun parseData(payload: String): FrameResult {
        val parts = payload.split(',').map { it.trim() }
        if (parts.size != 4) return FrameResult.Invalid("Se esperaban 4 valores y llegaron ${parts.size}.")
        val values = parts.map { parseNumber(it) ?: return FrameResult.Invalid("Valor no numérico: \"$it\".") }
        return reading(values[0], values[1], values[2], values[3])
    }

    private fun parseKeyValue(line: String): FrameResult {
        val found = mutableMapOf<String, Float>()
        for (part in line.split(',')) {
            val kv = part.split(':')
            if (kv.size != 2) return FrameResult.Invalid("Par clave:valor mal formado: \"${part.trim()}\".")
            val key = canonicalKey(kv[0]) ?: return FrameResult.Invalid("Clave desconocida: \"${kv[0].trim()}\".")
            val value = parseNumber(kv[1].trim()) ?: return FrameResult.Invalid("Valor no numérico en $key.")
            if (found.put(key, value) != null) return FrameResult.Invalid("Clave repetida: $key.")
        }
        val missing = KEYS.filter { it !in found }
        if (missing.isNotEmpty()) return FrameResult.Invalid("Faltan valores: ${missing.joinToString()}.")
        return reading(found.getValue(TEMP), found.getValue(PH), found.getValue(COND), found.getValue(TURB))
    }

    private fun parseMultiline(line: String): FrameResult {
        val idx = line.indexOf(':')
        if (idx <= 0) return FrameResult.Ignored
        val key = canonicalKey(line.substring(0, idx)) ?: return FrameResult.Ignored
        val number = NUMBER.find(line.substring(idx + 1))?.value
        val value = number?.let { parseNumber(it) }
        if (value == null) {
            pending.clear()
            return FrameResult.Invalid("Valor no numérico en $key.")
        }
        if (key in pending) {
            // Llego un valor repetido antes de completar el ciclo: el ciclo anterior estaba incompleto.
            pending.clear()
            pending[key] = value
            return FrameResult.Invalid("Ciclo incompleto: se repitió $key antes de recibir los 4 valores.")
        }
        pending[key] = value
        if (pending.size < KEYS.size) return FrameResult.Partial
        val result = reading(pending.getValue(TEMP), pending.getValue(PH), pending.getValue(COND), pending.getValue(TURB))
        pending.clear()
        return result
    }

    private fun reading(temp: Float, ph: Float, cond: Float, turb: Float): FrameResult =
        FrameResult.Reading(
            MilkQualityReading(
                temperature = temp,
                ph = ph,
                conductivity = cond,
                turbidity = turb,
                timestamp = clock(),
                source = ReadingSource.DEVICE
            )
        )

    private fun parseNumber(text: String): Float? {
        if (!STRICT_NUMBER.matches(text)) return null
        return text.toFloatOrNull()?.takeIf { !it.isNaN() && !it.isInfinite() }
    }

    private fun canonicalKey(raw: String): String? = when (raw.trim().uppercase()) {
        "T", "TEMP", "TEMPERATURA" -> TEMP
        "PH" -> PH
        "CE", "COND", "CONDUCTIVIDAD" -> COND
        "TU", "TURB", "TURBIDEZ" -> TURB
        else -> null
    }

    private companion object {
        const val TEMP = "temperatura"
        const val PH = "pH"
        const val COND = "conductividad"
        const val TURB = "turbidez"
        val KEYS = listOf(TEMP, PH, COND, TURB)
        val STRICT_NUMBER = Regex("^[-+]?[0-9]+(\\.[0-9]+)?$")
        val NUMBER = Regex("[-+]?[0-9]+(\\.[0-9]+)?")
        val KEY_VALUE = Regex("^\\s*[A-Za-zÁÉÍÓÚáéíóú]+\\s*:")
    }
}

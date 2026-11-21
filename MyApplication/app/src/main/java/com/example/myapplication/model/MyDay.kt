package com.example.myapplication.model

import com.example.myapplication.model.analysis.LotAnalysis
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale
import java.util.TimeZone

/** Resumen del dia de un trabajador (lo que ve en "Mi dia"). */
data class MyDaySummary(
    val count: Int,
    val compliantCount: Int,
    /** Porcentaje de mediciones en verde; null si hoy no midio nada. */
    val compliantPercent: Double?,
    val lastMillis: Long?,
    val simulatedCount: Int
)

/** Calculos de "Mi dia": puros, se prueban sin Android. */
object MyDay {

    /** Resume las mediciones propias hechas desde [dayStartMillis] (inclusive). */
    fun summarize(own: List<SupervisionPoint>, dayStartMillis: Long): MyDaySummary {
        val today = own.filter { it.epochMillis >= dayStartMillis }
        val compliant = today.count { it.status == "verde" }
        return MyDaySummary(
            count = today.size,
            compliantCount = compliant,
            compliantPercent = if (today.isEmpty()) null else compliant * 100.0 / today.size,
            lastMillis = today.maxOfOrNull { it.epochMillis },
            simulatedCount = today.count { it.simulated }
        )
    }

    /** Medianoche local del dia de [nowMillis]. */
    fun startOfDay(nowMillis: Long): Long = Calendar.getInstance().apply {
        timeInMillis = nowMillis
        set(Calendar.HOUR_OF_DAY, 0)
        set(Calendar.MINUTE, 0)
        set(Calendar.SECOND, 0)
        set(Calendar.MILLISECOND, 0)
    }.timeInMillis
}

/** Hora de un aviso dicha con naturalidad: "a las 16:03", "mañana a las 04:03", "ayer a las 22:00" o "el 23/11 a las 09:00". */
object DayTime {
    fun label(timeMillis: Long, nowMillis: Long, timeZone: TimeZone = TimeZone.getDefault()): String {
        val clock = SimpleDateFormat("HH:mm", Locale.US).apply { this.timeZone = timeZone }.format(Date(timeMillis))
        val days = dayNumber(timeMillis, timeZone) - dayNumber(nowMillis, timeZone)
        return when (days) {
            0L -> "a las $clock"
            1L -> "mañana a las $clock"
            -1L -> "ayer a las $clock"
            else -> {
                val date = SimpleDateFormat("dd/MM", Locale.US).apply { this.timeZone = timeZone }.format(Date(timeMillis))
                "el $date a las $clock"
            }
        }
    }

    /** Dia calendario (en la zona horaria dada) como numero, para restar dias sin errores de horario de verano. */
    private fun dayNumber(millis: Long, timeZone: TimeZone): Long {
        val c = Calendar.getInstance(timeZone).apply { timeInMillis = millis }
        return java.time.LocalDate.of(c.get(Calendar.YEAR), c.get(Calendar.MONTH) + 1, c.get(Calendar.DAY_OF_MONTH)).toEpochDay()
    }
}

/** Un lote al que le toca una nueva medicion. */
data class ReminderItem(val lotId: String, val tank: String, val dueAtMillis: Long)

/** Lo que falta medir: lo atrasado (ya paso la hora) y lo proximo (por avisar con una notificacion). */
data class ReminderPlan(val overdue: List<ReminderItem>, val upcoming: List<ReminderItem>)

/** Recordatorios de la proxima medicion. Usa la hora sugerida por el analisis de cada lote. */
object Reminders {
    /** Solo se programan avisos de las proximas 12 horas. */
    const val HORIZON_MS = 12 * 60 * 60 * 1000L

    /**
     * @param analyses analisis por lote (con la hora en que conviene volver a medir)
     * @param eligibleLotIds lotes de hoy que siguen abiertos; los demas no generan recordatorios
     */
    fun plan(
        analyses: List<LotAnalysis>,
        tankOf: (String) -> String,
        eligibleLotIds: Set<String>,
        nowMillis: Long,
        horizonMillis: Long = HORIZON_MS
    ): ReminderPlan {
        val items = analyses
            .filter { it.lotId in eligibleLotIds }
            .map { ReminderItem(it.lotId, tankOf(it.lotId), it.dueAtMillis) }
        return ReminderPlan(
            overdue = items.filter { it.dueAtMillis <= nowMillis }.sortedBy { it.dueAtMillis },
            upcoming = items.filter { it.dueAtMillis > nowMillis && it.dueAtMillis - nowMillis <= horizonMillis }
                .sortedBy { it.dueAtMillis }
        )
    }

    fun notificationText(tank: String): String = "Toca medir la leche del lote $tank."
}

/** Nota que acompana una medicion y peticion de revision al encargado. */
object ReviewRules {
    const val NOTE_MAX = 200

    /** Mensaje si la nota no sirve, o null si esta bien (puede ir vacia). */
    fun validateNote(note: String): String? =
        if (note.trim().length > NOTE_MAX) "La nota no puede superar $NOTE_MAX caracteres." else null

    /** Nota lista para guardar: sin espacios sobrantes. */
    fun clean(note: String): String = note.trim()
}

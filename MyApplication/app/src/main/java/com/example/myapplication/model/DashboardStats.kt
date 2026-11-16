package com.example.myapplication.model

import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale
import java.util.TimeZone

/** Datos minimos de una medicion guardada para calcular el panel (independiente de Firebase). */
data class MeasurementPoint(
    val epochMillis: Long,
    val temperature: Double,
    val ph: Double,
    /** verde, ambar o rojo */
    val status: String,
    val simulated: Boolean
)

/** Promedios de un dia para los graficos de tendencia (null si ese dia no hubo mediciones). */
data class DayPoint(
    val dayStartMillis: Long,
    val label: String,
    val count: Int,
    val avgPh: Double?,
    val avgTemperature: Double?
)

data class DashboardSummary(
    val todayCount: Int,
    /** Porcentaje de mediciones de hoy en verde (null si hoy no hay mediciones). */
    val todayCompliantPercent: Double?,
    val lastMeasurementMillis: Long?,
    /** Ultimos N dias, del mas antiguo al de hoy. */
    val days: List<DayPoint>,
    val totalCount: Int,
    val simulatedCount: Int
)

/** Calculos del panel principal: se prueban sin Android ni Firebase. */
object DashboardStats {

    fun compute(
        points: List<MeasurementPoint>,
        nowMillis: Long,
        zone: TimeZone = TimeZone.getDefault(),
        dayCount: Int = 7
    ): DashboardSummary {
        val todayStart = startOfDay(nowMillis, zone)
        val labelFormat = SimpleDateFormat("dd/MM", Locale.US).apply { timeZone = zone }

        val days = (dayCount - 1 downTo 0).map { back ->
            val start = addDays(todayStart, -back, zone)
            val end = addDays(start, 1, zone)
            val inDay = points.filter { it.epochMillis in start until end }
            DayPoint(
                dayStartMillis = start,
                label = labelFormat.format(start),
                count = inDay.size,
                avgPh = inDay.takeIf { it.isNotEmpty() }?.map { it.ph }?.average(),
                avgTemperature = inDay.takeIf { it.isNotEmpty() }?.map { it.temperature }?.average()
            )
        }

        val today = points.filter { it.epochMillis >= todayStart && it.epochMillis < addDays(todayStart, 1, zone) }
        return DashboardSummary(
            todayCount = today.size,
            todayCompliantPercent = if (today.isEmpty()) null else today.count { it.status == "verde" } * 100.0 / today.size,
            lastMeasurementMillis = points.maxOfOrNull { it.epochMillis },
            days = days,
            totalCount = points.size,
            simulatedCount = points.count { it.simulated }
        )
    }

    private fun startOfDay(millis: Long, zone: TimeZone): Long =
        Calendar.getInstance(zone).apply {
            timeInMillis = millis
            set(Calendar.HOUR_OF_DAY, 0)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }.timeInMillis

    /** Suma dias de calendario (respeta cambios de horario de verano). */
    private fun addDays(dayStart: Long, days: Int, zone: TimeZone): Long =
        Calendar.getInstance(zone).apply {
            timeInMillis = dayStart
            add(Calendar.DAY_OF_YEAR, days)
        }.timeInMillis
}

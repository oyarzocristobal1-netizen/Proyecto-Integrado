package com.example.myapplication.ui.theme

import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color

/** Par de colores para una etiqueta o tarjeta de estado: fondo y texto/icono. */
@Immutable
data class StatusTone(val container: Color, val content: Color)

/**
 * Colores con significado fijo en toda la app (semaforo del informe, RF3):
 * verde = conforme, ambar = precaucion, rojo = fuera de rango, azul = informacion y
 * violeta = dato SIMULADO (modo DEMO), distinto del semaforo para no confundirlo con una medicion real.
 */
@Immutable
data class StatusColors(
    val good: StatusTone,
    val warning: StatusTone,
    val alert: StatusTone,
    val info: StatusTone,
    val simulated: StatusTone
) {
    /** Tono para el estado guardado en Firestore: verde, ambar o rojo. */
    fun forStatus(status: String): StatusTone = when (status) {
        "verde" -> good
        "ambar" -> warning
        "rojo" -> alert
        else -> info
    }
}

val LightStatusColors = StatusColors(
    good = StatusTone(Color(0xFFE3F1E4), Color(0xFF1B5E20)),
    warning = StatusTone(Color(0xFFFFF1D0), Color(0xFF6B4500)),
    alert = StatusTone(Color(0xFFFDE4E4), Color(0xFFB3261E)),
    info = StatusTone(Color(0xFFE3EEFB), Color(0xFF0D47A1)),
    simulated = StatusTone(Color(0xFFEDE7F6), Color(0xFF4527A0))
)

val DarkStatusColors = StatusColors(
    good = StatusTone(Color(0xFF1E3A24), Color(0xFFA5D6A7)),
    warning = StatusTone(Color(0xFF3D2E00), Color(0xFFFFD27A)),
    alert = StatusTone(Color(0xFF4A1515), Color(0xFFFFB4AB)),
    info = StatusTone(Color(0xFF0F2B4A), Color(0xFF9CC8FF)),
    simulated = StatusTone(Color(0xFF2A2142), Color(0xFFCBBEFF))
)

val LocalStatusColors = staticCompositionLocalOf { LightStatusColors }

/** Acceso corto desde cualquier pantalla: AppTheme.status.good */
object AppTheme {
    val status: StatusColors
        @Composable
        @ReadOnlyComposable
        get() = LocalStatusColors.current
}

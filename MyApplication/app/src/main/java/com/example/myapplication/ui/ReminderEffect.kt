package com.example.myapplication.ui

import android.Manifest
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import com.example.myapplication.data.asAnalysisReading
import com.example.myapplication.model.ReminderPlan
import com.example.myapplication.model.Reminders
import com.example.myapplication.model.UserSession
import com.example.myapplication.model.analysis.LotAnalyzer
import com.example.myapplication.model.MyDay
import com.example.myapplication.reminders.MeasurementReminders
import com.example.myapplication.reminders.RoleNoticesScheduler
import com.example.myapplication.viewmodel.RecordsUiState
import kotlinx.coroutines.delay

/** Recordatorios de las mediciones propias: lotes de hoy, abiertos, segun la hora que sugiere el analisis. */
fun ownReminderPlan(state: RecordsUiState, uid: String, now: Long): ReminderPlan {
    val dayStart = MyDay.startOfDay(now)
    val eligible = state.lots.filter { it.date.time >= dayStart && !it.closed }.map { it.id }.toSet()
    val own = state.recentValid.filter { it.milkerUid == uid }
    val analyses = LotAnalyzer.analyze(own.map { it.asAnalysisReading() }, state.ranges, maxLots = Int.MAX_VALUE, nowMillis = now)
    return Reminders.plan(analyses, state::lotName, eligible, now)
}

/**
 * Programa las notificaciones de "toca medir" mientras la app esta abierta (y quedan programadas
 * aunque se cierre). El permiso de notificaciones lo pide [RoleNoticesEffect].
 */
@Composable
fun MeasurementReminderEffect(state: RecordsUiState, session: UserSession) {
    val context = LocalContext.current
    var now by remember { mutableLongStateOf(System.currentTimeMillis()) }
    LaunchedEffect(Unit) {
        while (true) {
            delay(60_000)
            now = System.currentTimeMillis()
        }
    }
    val plan = remember(state.recent, state.lots, state.ranges, session.uid, now) {
        ownReminderPlan(state, session.uid, now)
    }
    LaunchedEffect(plan.upcoming) {
        MeasurementReminders.sync(context, session.uid, plan.upcoming)
    }
}

/**
 * Avisos por rol con la app cerrada (alertas, revisiones, lotes atrasados): deja programada la revision
 * periodica y pide el permiso de notificaciones una sola vez por inicio de sesion, para todos los roles.
 */
@Composable
fun RoleNoticesEffect(session: UserSession) {
    val context = LocalContext.current
    var asked by rememberSaveable(session.uid) { mutableStateOf(false) }
    val permissionLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { }

    LaunchedEffect(session.uid) {
        RoleNoticesScheduler.schedule(context)
        if (!asked && Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU && !MeasurementReminders.canNotify(context)) {
            asked = true
            permissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
    }
}

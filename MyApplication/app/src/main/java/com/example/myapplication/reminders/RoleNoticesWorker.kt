package com.example.myapplication.reminders

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.NetworkType
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.example.myapplication.MilkApp
import com.example.myapplication.data.RecordsRepository
import com.example.myapplication.data.asAnalysisReading
import com.example.myapplication.model.MyDay
import com.example.myapplication.model.Notice
import com.example.myapplication.model.NoticeKind
import com.example.myapplication.model.Reminders
import com.example.myapplication.model.RoleNotices
import com.example.myapplication.model.analysis.LotAnalyzer
import java.util.Date
import kotlinx.coroutines.CancellationException
import java.util.concurrent.TimeUnit

/**
 * Avisos por rol con la app cerrada: cada cierto rato (Android no permite menos de 15 minutos) revisa
 * Firestore y muestra una notificacion si hay algo nuevo para el rol de la sesion:
 * - encargado y administrador: alertas sin atender, revisiones pedidas y lotes con la medicion atrasada;
 * - lechero: el encargado respondio una revision suya.
 *
 * No es en tiempo real: puede tardar minutos (Android agrupa las tareas para ahorrar bateria).
 * El aviso inmediato necesitaria un servidor (Cloud Functions y notificaciones push).
 */
class RoleNoticesWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        try {
            revisar()
        } catch (e: CancellationException) {
            throw e
        } catch (_: Exception) {
            // Sin red o sin permiso en este momento: se vuelve a intentar en la proxima revision.
        }
        return Result.success()
    }

    private suspend fun revisar() {
        val container = (applicationContext as MilkApp).container
        val uid = container.auth.currentUser?.uid ?: return
        val role = container.authRepository.activeRoleOf(uid) ?: return
        val repo = container.recordsRepository
        val now = System.currentTimeMillis()

        val current = mutableMapOf<NoticeKind, Set<String>>()
        if (role.canAttendAlerts) {
            runCatching { repo.fetchPendingAlerts().map { it.id }.toSet() }
                .onSuccess { current[NoticeKind.ALERTS] = it }
            runCatching {
                repo.fetchReviewRequests(onlyOfUid = null)
                    .filter { it.reviewRequested && !it.reviewDone && !it.annulled }
                    .map { it.id }.toSet()
            }.onSuccess { current[NoticeKind.REVIEW_REQUESTS] = it }
            runCatching { overdueKeys(repo, now) }
                .onSuccess { current[NoticeKind.OVERDUE_LOTS] = it }
        } else {
            val since = now - ANSWER_WINDOW_MS
            runCatching {
                repo.fetchReviewRequests(onlyOfUid = uid)
                    .filter { it.reviewDone && (it.reviewAt?.time ?: 0L) >= since }
                    .map { it.id }.toSet()
            }.onSuccess { current[NoticeKind.REVIEW_ANSWERED] = it }
        }
        if (current.isEmpty()) return

        val state = NoticeState(applicationContext, uid)
        val notified = state.load()
        // Los tipos que no se pudieron leer ahora se ignoran: conservan lo que ya se habia avisado.
        val plan = RoleNotices.plan(role, current, notified)
        // Sin permiso de notificaciones no se marca nada como avisado: cuando lo den, les llegara.
        if (!MeasurementReminders.canNotify(applicationContext)) return
        plan.notices.filter { it.kind in current.keys }.forEach { show(applicationContext, it) }
        state.save(notified + current.keys.associateWith { plan.nextNotified[it].orEmpty() })
    }

    /** Lotes de hoy, abiertos, a los que ya les tocaba medir segun el analisis (clave: lote + hora debida). */
    private suspend fun overdueKeys(repo: RecordsRepository, now: Long): Set<String> {
        val dayStart = MyDay.startOfDay(now)
        val since = Date(dayStart)
        val lots = repo.fetchLotsSince(since)
        val eligible = lots.filter { !it.closed }.map { it.id }.toSet()
        if (eligible.isEmpty()) return emptySet()
        val readings = repo.fetchMeasurementsSince(since).filterNot { it.annulled }.map { it.asAnalysisReading() }
        val ranges = repo.fetchRanges()
        val analyses = LotAnalyzer.analyze(readings, ranges, maxLots = Int.MAX_VALUE, nowMillis = now)
        val plan = Reminders.plan(analyses, { id -> lots.firstOrNull { it.id == id }?.tank ?: id }, eligible, now)
        return plan.overdue.map { RoleNotices.overdueKey(it.lotId, it.dueAtMillis) }.toSet()
    }

    private fun show(context: Context, notice: Notice) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            manager.createNotificationChannel(
                NotificationChannel(CHANNEL_ID, "Avisos de la app", NotificationManager.IMPORTANCE_DEFAULT).apply {
                    description = "Alertas por atender, revisiones pedidas o respondidas y mediciones atrasadas."
                }
            )
        }
        val open = context.packageManager.getLaunchIntentForPackage(context.packageName)?.let {
            PendingIntent.getActivity(context, 0, it, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
        }
        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.ic_popup_reminder)
            .setContentTitle(notice.title)
            .setContentText(notice.text)
            .setStyle(NotificationCompat.BigTextStyle().bigText(notice.text))
            .setAutoCancel(true)
            .setContentIntent(open)
            .build()
        try {
            NotificationManagerCompat.from(context).notify(NOTIFICATION_BASE_ID + notice.kind.ordinal, notification)
        } catch (_: SecurityException) {
            // Sin permiso: el dato sigue visible dentro de la app.
        }
    }

    companion object {
        private const val CHANNEL_ID = "avisos_por_rol"
        private const val NOTIFICATION_BASE_ID = 9100

        /** Una revision respondida solo se avisa durante 3 dias (evita avisar de respuestas viejas). */
        private const val ANSWER_WINDOW_MS = 3 * 24 * 60 * 60 * 1000L
    }
}

/** Lo ya avisado por persona y por tipo, para no repetir la misma notificacion en cada revision. */
private class NoticeState(context: Context, private val uid: String) {
    private val prefs = context.getSharedPreferences("avisos_estado", Context.MODE_PRIVATE)

    fun load(): Map<NoticeKind, Set<String>> =
        NoticeKind.entries.associateWith { prefs.getStringSet(key(it), emptySet()).orEmpty().toSet() }

    fun save(state: Map<NoticeKind, Set<String>>) {
        prefs.edit().apply {
            state.forEach { (kind, keys) -> putStringSet(key(kind), keys) }
        }.apply()
    }

    private fun key(kind: NoticeKind) = "$uid:${kind.name}"
}

/** Programa (una sola vez) la revision periodica de avisos. */
object RoleNoticesScheduler {
    private const val WORK_NAME = "avisos_por_rol"

    /** Cada 15 minutos (el minimo de Android), solo con conexion. Si ya esta programada, no hace nada. */
    fun schedule(context: Context) {
        val request = PeriodicWorkRequestBuilder<RoleNoticesWorker>(15, TimeUnit.MINUTES)
            .setConstraints(Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build())
            .build()
        WorkManager.getInstance(context).enqueueUniquePeriodicWork(WORK_NAME, ExistingPeriodicWorkPolicy.KEEP, request)
    }
}

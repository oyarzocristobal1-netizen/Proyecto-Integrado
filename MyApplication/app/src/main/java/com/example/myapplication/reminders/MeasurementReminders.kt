package com.example.myapplication.reminders

import android.Manifest
import android.app.AlarmManager
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import com.example.myapplication.model.ReminderItem
import com.example.myapplication.model.Reminders
import com.google.firebase.auth.FirebaseAuth

/**
 * Avisos en el telefono ("Toca medir el lote X") para la hora en que el analisis recomienda volver a medir.
 * Se programan con AlarmManager (aproximados, sin permisos especiales) y funcionan con la app cerrada.
 * Si el telefono se reinicia, se vuelven a programar cuando se abre la app.
 */
object MeasurementReminders {
    private const val CHANNEL_ID = "recordatorios_medicion"
    private const val PREFS = "recordatorios"
    private const val KEY_SCHEDULED = "lotes"
    internal const val EXTRA_UID = "uid"
    internal const val EXTRA_LOT = "lote"
    internal const val EXTRA_TANK = "estanque"

    private fun pendingIntent(context: Context, uid: String, item: ReminderItem?, lotId: String): PendingIntent {
        val intent = Intent(context, ReminderReceiver::class.java).apply {
            data = Uri.parse("recordatorio://lote/$lotId")
            putExtra(EXTRA_UID, uid)
            putExtra(EXTRA_LOT, lotId)
            putExtra(EXTRA_TANK, item?.tank.orEmpty())
        }
        return PendingIntent.getBroadcast(
            context,
            lotId.hashCode(),
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
    }

    /** Deja programados exactamente los avisos de [upcoming] y cancela los que ya no corresponden. */
    fun sync(context: Context, uid: String, upcoming: List<ReminderItem>) {
        val alarms = context.getSystemService(Context.ALARM_SERVICE) as? AlarmManager ?: return
        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val previous = prefs.getStringSet(KEY_SCHEDULED, emptySet()).orEmpty()
        val keep = upcoming.map { it.lotId }.toSet()
        (previous - keep).forEach { lotId -> alarms.cancel(pendingIntent(context, uid, null, lotId)) }
        upcoming.forEach { item ->
            alarms.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, item.dueAtMillis, pendingIntent(context, uid, item, item.lotId))
        }
        prefs.edit().putStringSet(KEY_SCHEDULED, keep).apply()
    }

    /** Las notificaciones estan permitidas (en Android 13 o superior hay que aceptar el permiso). */
    fun canNotify(context: Context): Boolean {
        val permitted = Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU ||
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED
        return permitted && NotificationManagerCompat.from(context).areNotificationsEnabled()
    }

    internal fun show(context: Context, lotId: String, tank: String) {
        if (!canNotify(context)) return
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            manager.createNotificationChannel(
                NotificationChannel(CHANNEL_ID, "Recordatorios de medición", NotificationManager.IMPORTANCE_DEFAULT).apply {
                    description = "Avisa cuando toca volver a medir la leche de un lote."
                }
            )
        }
        val open = context.packageManager.getLaunchIntentForPackage(context.packageName)?.let {
            PendingIntent.getActivity(context, 0, it, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
        }
        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.ic_popup_reminder)
            .setContentTitle("Hora de medir")
            .setContentText(Reminders.notificationText(tank.ifBlank { "de hoy" }))
            .setAutoCancel(true)
            .setContentIntent(open)
            .build()
        try {
            NotificationManagerCompat.from(context).notify(lotId.hashCode(), notification)
        } catch (_: SecurityException) {
            // Sin permiso de notificaciones: el recordatorio sigue visible en "Mi día".
        }
    }
}

/** Recibe la alarma y muestra el aviso, solo si sigue la misma sesion que lo programo. */
class ReminderReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val uid = intent.getStringExtra(MeasurementReminders.EXTRA_UID) ?: return
        if (FirebaseAuth.getInstance().currentUser?.uid != uid) return
        val lotId = intent.getStringExtra(MeasurementReminders.EXTRA_LOT) ?: return
        MeasurementReminders.show(context, lotId, intent.getStringExtra(MeasurementReminders.EXTRA_TANK).orEmpty())
    }
}

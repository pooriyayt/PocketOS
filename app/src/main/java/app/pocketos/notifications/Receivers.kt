package app.pocketos.notifications

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import androidx.core.app.NotificationManagerCompat
import app.pocketos.PocketOsApp
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull

/** Runs [block] off the main thread while keeping the receiver alive (goAsync). */
internal fun BroadcastReceiver.runAsync(block: suspend () -> Unit) {
    val pending = goAsync()
    CoroutineScope(SupervisorJob() + Dispatchers.Default).launch {
        try {
            withTimeoutOrNull(9_000) { block() }
        } finally {
            pending.finish()
        }
    }
}

/** Fired by AlarmManager for reminders and renewal reminders. */
class AlarmReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != AlarmScheduler.ACTION_ALARM) return
        val (kind, id) = AlarmScheduler.parse(intent.data) ?: return
        val container = (context.applicationContext as PocketOsApp).container
        runAsync { container.notificationScheduler.onAlarm(kind, id) }
    }
}

/** Notification buttons: Complete / Snooze / Remind me later. */
class NotificationActionReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val id = intent.getStringExtra(EXTRA_ID) ?: return
        val notificationId = intent.getIntExtra(EXTRA_NOTIFICATION_ID, 0)
        val container = (context.applicationContext as PocketOsApp).container
        runAsync {
            when (intent.action) {
                ACTION_COMPLETE -> container.reminders.complete(id)
                ACTION_SNOOZE -> container.reminders.snooze(id, container.settings.current().snoozeMinutes.toLong())
                ACTION_SNOOZE_RENEWAL -> container.notificationScheduler.snoozeRenewal(id, RENEWAL_SNOOZE_MINUTES)
                ACTION_MARK_CHECK_CLEARED -> container.finance.markCheckStatus(id, app.pocketos.domain.finance.CheckStatus.CLEARED)
            }
            NotificationManagerCompat.from(context).cancel(notificationId)
        }
    }

    companion object {
        const val ACTION_COMPLETE = "app.pocketos.action.COMPLETE"
        const val ACTION_SNOOZE = "app.pocketos.action.SNOOZE"
        const val ACTION_SNOOZE_RENEWAL = "app.pocketos.action.SNOOZE_RENEWAL"
        const val ACTION_MARK_CHECK_CLEARED = "app.pocketos.action.MARK_CHECK_CLEARED"
        const val EXTRA_ID = "id"
        const val EXTRA_NOTIFICATION_ID = "notification_id"
        private const val RENEWAL_SNOOZE_MINUTES = 3L * 60
    }
}

/**
 * Re-creates alarms after events that clear or invalidate them: reboot, app
 * update, manual time or time-zone change, and exact-alarm permission grant.
 */
class SystemEventsReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        when (intent.action) {
            Intent.ACTION_BOOT_COMPLETED,
            Intent.ACTION_MY_PACKAGE_REPLACED,
            Intent.ACTION_TIME_CHANGED,
            Intent.ACTION_TIMEZONE_CHANGED,
            Intent.ACTION_LOCALE_CHANGED,
            "android.app.action.SCHEDULE_EXACT_ALARM_PERMISSION_STATE_CHANGED",
            -> {
                val container = (context.applicationContext as PocketOsApp).container
                runAsync {
                    container.subscriptions.rollForwardRenewals()
                    container.notificationScheduler.reconcileAll()
                    container.widgetUpdater.updateNow()
                }
                container.maintenance.schedule()
            }
        }
    }
}

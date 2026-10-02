package app.pocketos.notifications

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build

/**
 * Thin wrapper over AlarmManager. Uses exact, Doze-exempt alarms when the
 * user has granted "Alarms & reminders" (SCHEDULE_EXACT_ALARM), otherwise
 * falls back to inexact alarms that Android may delay (typically minutes,
 * longer in deep Doze). The UI explains this and offers the permission.
 */
class AlarmScheduler(private val context: Context) {
    private val alarmManager = context.getSystemService(AlarmManager::class.java)

    fun canScheduleExact(): Boolean =
        Build.VERSION.SDK_INT < Build.VERSION_CODES.S || alarmManager?.canScheduleExactAlarms() == true

    fun schedule(kind: Kind, id: String, triggerAtMillis: Long) {
        val am = alarmManager ?: return
        val pi = pendingIntent(kind, id, triggerAtMillis, PendingIntent.FLAG_UPDATE_CURRENT) ?: return
        try {
            if (canScheduleExact()) {
                am.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerAtMillis, pi)
            } else {
                am.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerAtMillis, pi)
            }
        } catch (_: SecurityException) {
            // Permission revoked between the check and the call.
            am.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerAtMillis, pi)
        }
    }

    fun cancel(kind: Kind, id: String) {
        val pi = pendingIntent(kind, id, 0, PendingIntent.FLAG_NO_CREATE) ?: return
        alarmManager?.cancel(pi)
        pi.cancel()
    }

    private fun pendingIntent(kind: Kind, id: String, trigger: Long, flags: Int): PendingIntent? {
        val intent = Intent(context, AlarmReceiver::class.java).apply {
            action = ACTION_ALARM
            data = uri(kind, id)
            putExtra(EXTRA_TRIGGER, trigger)
        }
        return PendingIntent.getBroadcast(context, 0, intent, flags or PendingIntent.FLAG_IMMUTABLE)
    }

    enum class Kind(val path: String) { REMINDER("reminder"), RENEWAL("renewal") }

    companion object {
        const val ACTION_ALARM = "app.pocketos.action.ALARM"
        const val EXTRA_TRIGGER = "trigger"

        fun uri(kind: Kind, id: String): Uri = Uri.parse("pocketos://alarm/${kind.path}/$id")

        fun parse(uri: Uri?): Pair<Kind, String>? {
            val segments = uri?.pathSegments ?: return null
            if (uri.host != "alarm" || segments.size != 2) return null
            val kind = Kind.entries.firstOrNull { it.path == segments[0] } ?: return null
            return kind to segments[1]
        }
    }
}

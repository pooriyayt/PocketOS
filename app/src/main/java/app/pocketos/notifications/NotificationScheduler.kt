package app.pocketos.notifications

import app.pocketos.core.AppClock
import app.pocketos.data.local.DatabaseManager
import app.pocketos.data.local.set
import app.pocketos.data.local.toDomain
import app.pocketos.data.prefs.SettingsRepository
import java.time.Instant
import java.time.temporal.ChronoUnit

/**
 * Keeps exactly one pending alarm per reminder / subscription and handles
 * fired alarms. Called after every local or synced change, and reconciled
 * after boot, time / time-zone changes, app updates and daily maintenance.
 */
class NotificationScheduler(
    private val databases: DatabaseManager,
    private val settings: SettingsRepository,
    private val alarms: AlarmScheduler,
    private val notifier: Notifier,
    private val clock: AppClock,
) {
    private val db get() = databases.current

    suspend fun scheduleReminder(id: String) {
        val entity = db.reminders().get(id)
        if (entity == null || entity.deletedAt != null) {
            alarms.cancel(AlarmScheduler.Kind.REMINDER, id)
            notifier.cancel("r", id)
            return
        }
        val reminder = entity.toDomain()
        val s = settings.current()
        val last = db.meta().get(notifiedKey("r", id))?.toLongOrNull()?.let(Instant::ofEpochMilli)
        val trigger = TriggerCalculator.reminderTrigger(reminder, clock.now(), clock.zone(), s.reminderDefaultTime, last)
        if (trigger == null) alarms.cancel(AlarmScheduler.Kind.REMINDER, id) else alarms.schedule(AlarmScheduler.Kind.REMINDER, id, trigger.toEpochMilli())
        if (reminder.isCompleted) notifier.cancel("r", id)
    }

    suspend fun scheduleRenewal(id: String) {
        val entity = db.subscriptions().get(id)
        if (entity == null || entity.deletedAt != null) {
            alarms.cancel(AlarmScheduler.Kind.RENEWAL, id)
            notifier.cancel("s", id)
            return
        }
        val sub = entity.toDomain()
        val s = settings.current()
        val last = db.meta().get(notifiedKey("s", id))?.toLongOrNull()?.let(Instant::ofEpochMilli)
        val snooze = db.meta().get(snoozeKey(id))?.toLongOrNull()?.let(Instant::ofEpochMilli)
        val trigger = TriggerCalculator.renewalTrigger(sub, clock.now(), clock.zone(), s.renewalReminderTime, last, snooze)
        if (trigger == null) alarms.cancel(AlarmScheduler.Kind.RENEWAL, id) else alarms.schedule(AlarmScheduler.Kind.RENEWAL, id, trigger.toEpochMilli())
    }

    suspend fun reconcileAll() {
        db.reminders().schedulable().forEach { scheduleReminder(it.id) }
        db.subscriptions().allActive().forEach { scheduleRenewal(it.id) }
    }

    /** Handles a fired alarm: shows the notification, records it, schedules the next one. */
    suspend fun onAlarm(kind: AlarmScheduler.Kind, id: String) {
        val s = settings.current()
        val now = clock.now()
        when (kind) {
            AlarmScheduler.Kind.REMINDER -> {
                val entity = db.reminders().get(id)?.takeIf { it.deletedAt == null } ?: return
                val reminder = entity.toDomain()
                if (reminder.isCompleted) return
                notifier.showReminder(reminder, s.snoozeMinutes, s.appLockEnabled)
                db.meta().set(notifiedKey("r", id), now.toEpochMilli().toString())
                scheduleReminder(id)
            }
            AlarmScheduler.Kind.RENEWAL -> {
                val entity = db.subscriptions().get(id)?.takeIf { it.deletedAt == null } ?: return
                val sub = entity.toDomain()
                val today = clock.today()
                val trialEnding = sub.trialEnd != null && !sub.trialEnd.isBefore(today) &&
                    ChronoUnit.DAYS.between(today, sub.trialEnd) <= 1 && sub.trialEnd.isBefore(sub.nextRenewal)
                notifier.showRenewal(
                    sub,
                    daysUntil = ChronoUnit.DAYS.between(today, sub.nextRenewal),
                    trialEnding = trialEnding,
                    showSensitive = s.notificationsShowSensitive,
                    requireAuthForActions = s.appLockEnabled,
                )
                db.meta().set(notifiedKey("s", id), now.toEpochMilli().toString())
                db.meta().remove(snoozeKey(id))
                scheduleRenewal(id)
            }
        }
    }

    suspend fun snoozeRenewal(id: String, minutes: Long) {
        db.meta().set(snoozeKey(id), clock.now().plusSeconds(minutes * 60).toEpochMilli().toString())
        notifier.cancel("s", id)
        scheduleRenewal(id)
    }

    private fun notifiedKey(prefix: String, id: String) = "notified:$prefix:$id"
    private fun snoozeKey(id: String) = "snooze:s:$id"
}

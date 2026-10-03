package app.pocketos.notifications

import app.pocketos.data.repository.toDomain
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

    /**
     * Installments notify [InstallmentPlan.reminderDays] before the next due
     * date and again on the day itself, at the user's renewal reminder time.
     */
    suspend fun scheduleInstallment(id: String) {
        val plan = db.installments().get(id)?.toDomain()
        val due = plan?.nextDue
        if (plan == null || due == null) {
            alarms.cancel(AlarmScheduler.Kind.INSTALLMENT, id)
            notifier.cancel("i", id)
            return
        }
        val trigger = nextDueTrigger(due, plan.reminderDays, notifiedKey("i", id))
        if (trigger == null) alarms.cancel(AlarmScheduler.Kind.INSTALLMENT, id) else alarms.schedule(AlarmScheduler.Kind.INSTALLMENT, id, trigger.toEpochMilli())
    }

    /** Debts with a due date notify a day before and on the day, until settled. */
    suspend fun scheduleDebt(id: String) {
        val debt = db.debts().get(id)?.toDomain()
        val due = debt?.dueDate
        if (debt == null || due == null || debt.isSettled) {
            alarms.cancel(AlarmScheduler.Kind.DEBT, id)
            notifier.cancel("d", id)
            return
        }
        val trigger = nextDueTrigger(due, 1, notifiedKey("d", id))
        if (trigger == null) alarms.cancel(AlarmScheduler.Kind.DEBT, id) else alarms.schedule(AlarmScheduler.Kind.DEBT, id, trigger.toEpochMilli())
    }

    /** The first of (due - daysBefore, due) at the reminder time that is still ahead and not yet notified. */
    private suspend fun nextDueTrigger(due: java.time.LocalDate, daysBefore: Int, key: String): Instant? {
        val time = settings.current().renewalReminderTime
        val now = clock.now()
        val last = db.meta().get(key)?.toLongOrNull()?.let(Instant::ofEpochMilli)
        return listOf(due.minusDays(daysBefore.toLong().coerceAtLeast(0)), due)
            .distinct()
            .map { it.atTime(time).atZone(clock.zone()).toInstant() }
            .firstOrNull { it.isAfter(now) && (last == null || it.isAfter(last)) }
    }

    suspend fun reconcileAll() {
        db.reminders().schedulable().forEach { scheduleReminder(it.id) }
        db.subscriptions().allActive().forEach { scheduleRenewal(it.id) }
        db.installments().everything().forEach { scheduleInstallment(it.id) }
        db.debts().everything().forEach { scheduleDebt(it.id) }
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
            AlarmScheduler.Kind.INSTALLMENT -> {
                val plan = db.installments().get(id)?.toDomain() ?: return
                val due = plan.nextDue ?: return
                notifier.showInstallment(plan, ChronoUnit.DAYS.between(clock.today(), due), s.notificationsShowSensitive)
                db.meta().set(notifiedKey("i", id), now.toEpochMilli().toString())
                scheduleInstallment(id)
            }
            AlarmScheduler.Kind.DEBT -> {
                val debt = db.debts().get(id)?.toDomain()?.takeIf { !it.isSettled } ?: return
                val due = debt.dueDate ?: return
                notifier.showDebt(debt, ChronoUnit.DAYS.between(clock.today(), due), s.notificationsShowSensitive)
                db.meta().set(notifiedKey("d", id), now.toEpochMilli().toString())
                scheduleDebt(id)
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

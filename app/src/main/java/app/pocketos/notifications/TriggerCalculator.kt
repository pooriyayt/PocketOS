package app.pocketos.notifications

import app.pocketos.domain.model.Reminder
import app.pocketos.domain.model.Subscription
import app.pocketos.domain.model.SubscriptionStatus
import app.pocketos.domain.recurrence.RecurrenceEngine
import java.time.Duration
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId

/**
 * Pure calculation of when a reminder or renewal should notify next.
 * Kept free of Android types so it is fully unit-tested.
 */
object TriggerCalculator {

    /** Reminders missed while the device was off are still shown if less than this old. */
    val MISSED_GRACE: Duration = Duration.ofHours(12)

    fun reminderTrigger(
        reminder: Reminder,
        now: Instant,
        deviceZone: ZoneId,
        defaultTime: LocalTime,
        lastNotified: Instant?,
    ): Instant? {
        if (reminder.isCompleted) return null
        reminder.snoozedUntil?.let { if (it.isAfter(now)) return it }
        val due = reminder.dueDate ?: return null
        val zone = reminder.timeZone?.let { runCatching { ZoneId.of(it) }.getOrNull() } ?: deviceZone
        val time = if (reminder.allDay) defaultTime else reminder.dueTime ?: defaultTime

        fun triggerFor(date: LocalDate): Instant =
            RecurrenceEngine.toZoned(date, time, zone).toInstant().minusSeconds(reminder.leadMinutes * 60L)

        val base = triggerFor(due)
        if (base.isAfter(now)) return base
        val alreadyNotified = lastNotified != null && !lastNotified.isBefore(base)
        if (!alreadyNotified && Duration.between(base, now) < MISSED_GRACE) {
            // Missed (device off / app updated): notify now, once.
            return now.plusSeconds(1)
        }
        val rule = reminder.recurrence ?: return null
        var cursor = due
        repeat(400) {
            cursor = RecurrenceEngine.nextAfter(rule, due, cursor) ?: return null
            val t = triggerFor(cursor)
            if (t.isAfter(now)) return t
        }
        return null
    }

    /** Candidate notification moments for a subscription (renewal offsets + trial end). */
    fun renewalTriggers(sub: Subscription, zone: ZoneId, time: LocalTime): List<Instant> {
        if (sub.status != SubscriptionStatus.ACTIVE) return emptyList()
        val renewal = sub.reminderOffsets.map { offset ->
            RecurrenceEngine.toZoned(sub.nextRenewal.minusDays(offset.toLong()), time, zone).toInstant()
        }
        val trial = sub.trialEnd?.let { listOf(RecurrenceEngine.toZoned(it.minusDays(1), time, zone).toInstant()) }.orEmpty()
        return (renewal + trial).distinct().sorted()
    }

    fun renewalTrigger(sub: Subscription, now: Instant, zone: ZoneId, time: LocalTime, lastNotified: Instant?, snoozedUntil: Instant?): Instant? {
        if (sub.status != SubscriptionStatus.ACTIVE) return null
        snoozedUntil?.let { if (it.isAfter(now)) return it }
        val candidates = renewalTriggers(sub, zone, time)
        candidates.firstOrNull { it.isAfter(now) }?.let { return it }
        // A renewal reminder missed while the device was off (still before the renewal itself).
        val missed = candidates.lastOrNull { !it.isAfter(now) && (lastNotified == null || lastNotified.isBefore(it)) }
        if (missed != null && Duration.between(missed, now) < MISSED_GRACE) return now.plusSeconds(1)
        return null
    }
}

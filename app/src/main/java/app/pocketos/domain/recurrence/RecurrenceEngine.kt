package app.pocketos.domain.recurrence

import app.pocketos.domain.model.Frequency
import app.pocketos.domain.model.RecurrenceRule
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.YearMonth
import java.time.ZoneId
import java.time.ZonedDateTime
import java.time.temporal.ChronoUnit
import java.time.temporal.TemporalAdjusters

/**
 * Pure recurrence calculations. Occurrences are always derived from the
 * series start ("anchor") rather than from the previous occurrence, so a
 * monthly series starting on Jan 31 yields Feb 28/29, Mar 31, Apr 30...
 * without drifting.
 */
object RecurrenceEngine {

    /**
     * The first occurrence strictly after [after], or null when the series
     * has ended (UNTIL/COUNT). [start] is the first occurrence of the series.
     */
    fun nextAfter(rule: RecurrenceRule, start: LocalDate, after: LocalDate): LocalDate? {
        if (after < start) {
            val first = firstOccurrence(rule, start)
            return first?.takeIf { withinBounds(rule, it, 0) }
        }
        return when (rule.frequency) {
            Frequency.DAILY -> {
                val step = rule.interval.toLong()
                val k = ChronoUnit.DAYS.between(start, after) / step + 1
                candidate(rule, start.plusDays(k * step), k)
            }
            Frequency.WEEKLY -> nextWeekly(rule, start, after)
            Frequency.MONTHLY -> {
                val anchor = YearMonth.from(start)
                fun at(k: Long): LocalDate {
                    val ym = anchor.plusMonths(k * rule.interval)
                    val day = rule.byMonthDay ?: start.dayOfMonth
                    return ym.atDay(minOf(day, ym.lengthOfMonth()))
                }
                var k = ChronoUnit.MONTHS.between(anchor, YearMonth.from(after)) / rule.interval
                var date = at(k)
                while (date <= after || date < start) {
                    k++
                    date = at(k)
                }
                candidate(rule, date, k)
            }
            Frequency.YEARLY -> {
                var k = (after.year - start.year).toLong() / rule.interval
                var date = start.plusYears(k * rule.interval)
                while (date <= after) {
                    k++
                    date = start.plusYears(k * rule.interval)
                }
                candidate(rule, date, k)
            }
        }
    }

    /** First occurrence on or after [from]. */
    fun nextOnOrAfter(rule: RecurrenceRule, start: LocalDate, from: LocalDate): LocalDate? =
        nextAfter(rule, start, from.minusDays(1))

    /** All occurrences within [from]..[to] (inclusive), capped at [limit]. */
    fun occurrencesBetween(rule: RecurrenceRule, start: LocalDate, from: LocalDate, to: LocalDate, limit: Int = 500): List<LocalDate> {
        val result = ArrayList<LocalDate>()
        var cursor = nextOnOrAfter(rule, start, from) ?: return result
        while (cursor <= to && result.size < limit) {
            result += cursor
            cursor = nextAfter(rule, start, cursor) ?: break
        }
        return result
    }

    /**
     * The due date after completing the occurrence on [currentDue]: the next
     * occurrence after [currentDue] that is not already in the past.
     */
    fun nextAfterCompletion(rule: RecurrenceRule, start: LocalDate, currentDue: LocalDate, today: LocalDate): LocalDate? {
        val after = maxOf(currentDue, today.minusDays(1))
        return nextAfter(rule, start, after)
    }

    /**
     * Resolves a floating local date/time to an instant. ZonedDateTime.of
     * shifts times inside a DST gap forward and picks the earlier offset in
     * an overlap, so reminders fire exactly once around DST transitions.
     */
    fun toZoned(date: LocalDate, time: LocalTime, zone: ZoneId): ZonedDateTime =
        ZonedDateTime.of(LocalDateTime.of(date, time), zone)

    private fun firstOccurrence(rule: RecurrenceRule, start: LocalDate): LocalDate? =
        if (rule.frequency == Frequency.WEEKLY && rule.byDays.isNotEmpty() && start.dayOfWeek !in rule.byDays) {
            nextWeeklyRaw(rule, start, start)
        } else {
            start
        }

    private fun nextWeekly(rule: RecurrenceRule, start: LocalDate, after: LocalDate): LocalDate? {
        val date = nextWeeklyRaw(rule, start, after) ?: return null
        return candidate(rule, date, countIndexWeekly(rule, start, date))
    }

    /** Zero-based index of [date] in a weekly series (only needed with COUNT). */
    private fun countIndexWeekly(rule: RecurrenceRule, start: LocalDate, date: LocalDate): Long {
        if (rule.count == null) return 0
        var index = 0L
        var cursor = firstOccurrence(rule, start) ?: return 0
        while (cursor < date && index < 10_000) {
            index++
            cursor = nextWeeklyRaw(rule, start, cursor) ?: break
        }
        return index
    }

    private fun nextWeeklyRaw(rule: RecurrenceRule, start: LocalDate, after: LocalDate): LocalDate? {
        val days = rule.byDays.ifEmpty { setOf(start.dayOfWeek) }.sorted()
        val startWeek = start.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY))
        var weekIndex = ChronoUnit.WEEKS.between(startWeek, after.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY)))
        val remainder = weekIndex % rule.interval
        if (remainder != 0L) weekIndex += rule.interval - remainder
        repeat(3) {
            val weekStart = startWeek.plusWeeks(weekIndex)
            for (day in days) {
                val date = weekStart.plusDays((day.value - 1).toLong())
                if (date > after && date >= start) return date
            }
            weekIndex += rule.interval
        }
        return null
    }

    private fun candidate(rule: RecurrenceRule, date: LocalDate, index: Long): LocalDate? =
        date.takeIf { withinBounds(rule, it, index) }

    private fun withinBounds(rule: RecurrenceRule, date: LocalDate, index: Long): Boolean {
        if (rule.until != null && date > rule.until) return false
        if (rule.count != null && index >= rule.count) return false
        return true
    }
}

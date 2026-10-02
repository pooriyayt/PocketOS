package app.pocketos.domain

import app.pocketos.domain.model.Frequency
import app.pocketos.domain.model.RecurrenceRule
import app.pocketos.domain.recurrence.RecurrenceEngine
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId

class RecurrenceEngineTest {

    private fun d(s: String) = LocalDate.parse(s)

    @Test
    fun dailyWithInterval() {
        val rule = RecurrenceRule.daily(3)
        assertEquals(d("2026-03-04"), RecurrenceEngine.nextAfter(rule, d("2026-03-01"), d("2026-03-01")))
        assertEquals(d("2026-03-04"), RecurrenceEngine.nextAfter(rule, d("2026-03-01"), d("2026-03-03")))
        assertEquals(d("2026-03-01"), RecurrenceEngine.nextAfter(rule, d("2026-03-01"), d("2026-02-20")))
    }

    @Test
    fun weeklyOnSelectedDays() {
        val rule = RecurrenceRule.weekly(1, setOf(DayOfWeek.MONDAY, DayOfWeek.WEDNESDAY))
        // 2026-03-02 is a Monday.
        assertEquals(d("2026-03-04"), RecurrenceEngine.nextAfter(rule, d("2026-03-02"), d("2026-03-02")))
        assertEquals(d("2026-03-09"), RecurrenceEngine.nextAfter(rule, d("2026-03-02"), d("2026-03-04")))
    }

    @Test
    fun everyOtherWeekRespectsInterval() {
        val rule = RecurrenceRule.weekly(2, setOf(DayOfWeek.FRIDAY))
        // Start Friday 2026-03-06; next occurrences 03-20, 04-03.
        assertEquals(d("2026-03-20"), RecurrenceEngine.nextAfter(rule, d("2026-03-06"), d("2026-03-06")))
        assertEquals(d("2026-04-03"), RecurrenceEngine.nextAfter(rule, d("2026-03-06"), d("2026-03-21")))
    }

    @Test
    fun weeklyStartNotOnSelectedDay() {
        val rule = RecurrenceRule.weekly(1, setOf(DayOfWeek.MONDAY))
        // Start on a Tuesday: the first occurrence is the following Monday.
        assertEquals(d("2026-03-09"), RecurrenceEngine.nextOnOrAfter(rule, d("2026-03-03"), d("2026-03-03")))
    }

    @Test
    fun monthlyOnThe31stDoesNotDrift() {
        val rule = RecurrenceRule.monthly()
        val start = d("2026-01-31")
        val feb = RecurrenceEngine.nextAfter(rule, start, start)
        assertEquals(d("2026-02-28"), feb)
        assertEquals(d("2026-03-31"), RecurrenceEngine.nextAfter(rule, start, feb!!))
        assertEquals(d("2026-04-30"), RecurrenceEngine.nextAfter(rule, start, d("2026-03-31")))
    }

    @Test
    fun completingMonthlyAfterShortMonthReturnsToAnchorDay() {
        val rule = RecurrenceRule.monthly().anchoredTo(d("2026-01-31"))
        // The reminder's due date has advanced to Feb 28; the next one is Mar 31, not Mar 28.
        assertEquals(d("2026-03-31"), RecurrenceEngine.nextAfterCompletion(rule, d("2026-02-28"), d("2026-02-28"), d("2026-02-28")))
        assertEquals("FREQ=MONTHLY;BYMONTHDAY=31", rule.toRRule())
    }

    @Test
    fun yearlyOnLeapDay() {
        val rule = RecurrenceRule.yearly()
        val start = d("2024-02-29")
        assertEquals(d("2025-02-28"), RecurrenceEngine.nextAfter(rule, start, start))
        assertEquals(d("2028-02-29"), RecurrenceEngine.nextAfter(rule, start, d("2027-03-01")))
    }

    @Test
    fun untilAndCountEndTheSeries() {
        val until = RecurrenceRule(Frequency.DAILY, 1, until = d("2026-03-03"))
        assertEquals(d("2026-03-03"), RecurrenceEngine.nextAfter(until, d("2026-03-01"), d("2026-03-02")))
        assertNull(RecurrenceEngine.nextAfter(until, d("2026-03-01"), d("2026-03-03")))
        val count = RecurrenceRule(Frequency.DAILY, 1, count = 3)
        assertEquals(d("2026-03-03"), RecurrenceEngine.nextAfter(count, d("2026-03-01"), d("2026-03-02")))
        assertNull(RecurrenceEngine.nextAfter(count, d("2026-03-01"), d("2026-03-03")))
    }

    @Test
    fun completionSkipsMissedOccurrences() {
        val rule = RecurrenceRule.daily()
        // Due 3 days ago; completing it moves to today, not to the day after the stale due date.
        val next = RecurrenceEngine.nextAfterCompletion(rule, d("2026-03-01"), d("2026-03-07"), today = d("2026-03-10"))
        assertEquals(d("2026-03-10"), next)
        // Completing today's occurrence moves to tomorrow.
        assertEquals(d("2026-03-11"), RecurrenceEngine.nextAfterCompletion(rule, d("2026-03-01"), d("2026-03-10"), d("2026-03-10")))
    }

    @Test
    fun occurrencesBetween() {
        val rule = RecurrenceRule.weekly(1, setOf(DayOfWeek.SATURDAY, DayOfWeek.SUNDAY))
        val list = RecurrenceEngine.occurrencesBetween(rule, d("2026-03-07"), d("2026-03-01"), d("2026-03-15"))
        assertEquals(listOf(d("2026-03-07"), d("2026-03-08"), d("2026-03-14"), d("2026-03-15")), list)
    }

    @Test
    fun dstGapShiftsForward() {
        // Europe/Berlin springs forward 2026-03-29 02:00 -> 03:00.
        val zoned = RecurrenceEngine.toZoned(d("2026-03-29"), LocalTime.of(2, 30), ZoneId.of("Europe/Berlin"))
        assertEquals(LocalTime.of(3, 30), zoned.toLocalTime())
    }

    @Test
    fun rruleRoundTrip() {
        val rule = RecurrenceRule(Frequency.WEEKLY, 2, setOf(DayOfWeek.WEDNESDAY, DayOfWeek.MONDAY), until = d("2026-12-31"))
        val text = rule.toRRule()
        assertEquals("FREQ=WEEKLY;INTERVAL=2;BYDAY=MO,WE;UNTIL=20261231", text)
        assertEquals(rule, RecurrenceRule.parse(text))
        assertNull(RecurrenceRule.parse("FREQ=HOURLY"))
        assertNull(RecurrenceRule.parse("FREQ=DAILY;BYHOUR=3"))
    }
}

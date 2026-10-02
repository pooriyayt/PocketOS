package app.pocketos.domain.model

import java.time.DayOfWeek
import java.time.LocalDate
import java.time.format.DateTimeFormatter

enum class Frequency { DAILY, WEEKLY, MONTHLY, YEARLY }

/**
 * Subset of RFC 5545 RRULE shared with the server:
 * FREQ=DAILY|WEEKLY|MONTHLY|YEARLY;INTERVAL=n;BYDAY=MO,TU;UNTIL=YYYYMMDD;COUNT=n
 */
data class RecurrenceRule(
    val frequency: Frequency,
    val interval: Int = 1,
    val byDays: Set<DayOfWeek> = emptySet(),
    val until: LocalDate? = null,
    val count: Int? = null,
    /** Day of month for MONTHLY rules (keeps "the 31st" from drifting after short months). */
    val byMonthDay: Int? = null,
) {
    init {
        require(interval in 1..999) { "interval out of range" }
        require(count == null || count in 1..9999) { "count out of range" }
        require(byMonthDay == null || byMonthDay in 1..31) { "byMonthDay out of range" }
    }

    /**
     * Re-anchors the rule to a newly chosen start [date]: monthly rules
     * starting on the 29th-31st remember that day so later occurrences
     * return to it after shorter months.
     */
    fun anchoredTo(date: LocalDate?): RecurrenceRule =
        if (frequency == Frequency.MONTHLY && date != null && date.dayOfMonth > 28) copy(byMonthDay = date.dayOfMonth)
        else copy(byMonthDay = null)

    fun toRRule(): String = buildString {
        append("FREQ=").append(frequency.name)
        if (interval != 1) append(";INTERVAL=").append(interval)
        if (byDays.isNotEmpty() && frequency == Frequency.WEEKLY) {
            append(";BYDAY=").append(byDays.sorted().joinToString(",") { CODES.getValue(it) })
        }
        if (byMonthDay != null && frequency == Frequency.MONTHLY) append(";BYMONTHDAY=").append(byMonthDay)
        until?.let { append(";UNTIL=").append(it.format(UNTIL_FORMAT)) }
        count?.let { append(";COUNT=").append(it) }
    }

    companion object {
        private val UNTIL_FORMAT: DateTimeFormatter = DateTimeFormatter.BASIC_ISO_DATE
        private val CODES = mapOf(
            DayOfWeek.MONDAY to "MO", DayOfWeek.TUESDAY to "TU", DayOfWeek.WEDNESDAY to "WE",
            DayOfWeek.THURSDAY to "TH", DayOfWeek.FRIDAY to "FR", DayOfWeek.SATURDAY to "SA", DayOfWeek.SUNDAY to "SU",
        )
        private val DAYS = CODES.entries.associate { (k, v) -> v to k }

        val WEEKDAYS: Set<DayOfWeek> = setOf(DayOfWeek.MONDAY, DayOfWeek.TUESDAY, DayOfWeek.WEDNESDAY, DayOfWeek.THURSDAY, DayOfWeek.FRIDAY)

        fun daily(interval: Int = 1) = RecurrenceRule(Frequency.DAILY, interval)
        fun weekly(interval: Int = 1, days: Set<DayOfWeek> = emptySet()) = RecurrenceRule(Frequency.WEEKLY, interval, days)
        fun monthly(interval: Int = 1) = RecurrenceRule(Frequency.MONTHLY, interval)
        fun yearly(interval: Int = 1) = RecurrenceRule(Frequency.YEARLY, interval)

        /** Parses an RRULE string; returns null for anything unsupported. */
        fun parse(rule: String?): RecurrenceRule? {
            if (rule.isNullOrBlank()) return null
            var frequency: Frequency? = null
            var interval = 1
            var days = emptySet<DayOfWeek>()
            var until: LocalDate? = null
            var count: Int? = null
            var monthDay: Int? = null
            for (part in rule.uppercase().split(';')) {
                val (key, value) = part.split('=', limit = 2).takeIf { it.size == 2 } ?: return null
                when (key) {
                    "FREQ" -> frequency = runCatching { Frequency.valueOf(value) }.getOrNull() ?: return null
                    "INTERVAL" -> interval = value.toIntOrNull()?.takeIf { it in 1..999 } ?: return null
                    "BYDAY" -> days = value.split(',').map { DAYS[it] ?: return null }.toSet()
                    "UNTIL" -> until = runCatching { LocalDate.parse(value.take(8), UNTIL_FORMAT) }.getOrNull() ?: return null
                    "COUNT" -> count = value.toIntOrNull()?.takeIf { it in 1..9999 } ?: return null
                    "BYMONTHDAY" -> monthDay = value.toIntOrNull()?.takeIf { it in 1..31 } ?: return null
                    else -> return null
                }
            }
            return frequency?.let {
                RecurrenceRule(it, interval, if (it == Frequency.WEEKLY) days else emptySet(), until, count, if (it == Frequency.MONTHLY) monthDay else null)
            }
        }
    }
}

package app.pocketos.core.time

import java.time.LocalDate
import java.time.chrono.HijrahChronology
import java.time.chrono.HijrahDate
import java.time.temporal.ChronoField

/** The calendar a user reads dates in. Storage is always ISO (Gregorian) LocalDate. */
enum class CalendarKind { GREGORIAN, SOLAR_HIJRI, LUNAR_HIJRI }

/** Year / month / day in some calendar. Months are 1-based. */
data class CalendarDate(val year: Int, val month: Int, val day: Int)

/**
 * Month arithmetic in any supported calendar. "Every month" for an
 * installment means the same day of the month *in the user's calendar*
 * (e.g. the 5th of every Solar Hijri month), clamped to short months.
 */
object CalendarMath {

    fun fromDate(date: LocalDate, kind: CalendarKind): CalendarDate = when (kind) {
        CalendarKind.GREGORIAN -> CalendarDate(date.year, date.monthValue, date.dayOfMonth)
        CalendarKind.SOLAR_HIJRI -> JalaliCalendar.fromGregorian(date).let { CalendarDate(it.year, it.month, it.day) }
        CalendarKind.LUNAR_HIJRI -> HijrahDate.from(date).let {
            CalendarDate(it.get(ChronoField.YEAR), it.get(ChronoField.MONTH_OF_YEAR), it.get(ChronoField.DAY_OF_MONTH))
        }
    }

    fun toDate(kind: CalendarKind, year: Int, month: Int, day: Int): LocalDate = when (kind) {
        CalendarKind.GREGORIAN -> LocalDate.of(year, month, day)
        CalendarKind.SOLAR_HIJRI -> JalaliCalendar.toGregorian(year, month, day)
        CalendarKind.LUNAR_HIJRI -> LocalDate.from(HijrahChronology.INSTANCE.date(year, month, day))
    }

    fun monthLength(kind: CalendarKind, year: Int, month: Int): Int = when (kind) {
        CalendarKind.GREGORIAN -> java.time.YearMonth.of(year, month).lengthOfMonth()
        CalendarKind.SOLAR_HIJRI -> JalaliCalendar.monthLength(year, month)
        CalendarKind.LUNAR_HIJRI -> HijrahChronology.INSTANCE.date(year, month, 1).lengthOfMonth()
    }

    /** First day of the month containing [date]. */
    fun monthStart(date: LocalDate, kind: CalendarKind): LocalDate =
        fromDate(date, kind).let { toDate(kind, it.year, it.month, 1) }

    /**
     * Adds whole months in [kind], keeping the day of month (clamped to the target month).
     * Always count from the original date (start + n months) so a 31st returns to the 31st.
     */
    fun plusMonths(date: LocalDate, months: Int, kind: CalendarKind): LocalDate {
        val d = fromDate(date, kind)
        val index = d.year * 12 + (d.month - 1) + months
        val year = Math.floorDiv(index, 12)
        val month = Math.floorMod(index, 12) + 1
        return toDate(kind, year, month, minOf(d.day, monthLength(kind, year, month)))
    }
}

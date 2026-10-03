package app.pocketos.ui.format

import app.pocketos.core.time.CalendarMath
import app.pocketos.core.time.CalendarKind
import android.content.Context
import androidx.compose.runtime.staticCompositionLocalOf
import app.pocketos.R
import app.pocketos.core.money.Digits
import app.pocketos.core.money.MoneyFormatter
import app.pocketos.core.time.JalaliCalendar
import app.pocketos.domain.model.BillingCycle
import app.pocketos.domain.model.BillingUnit
import app.pocketos.domain.model.Frequency
import app.pocketos.domain.model.RecurrenceRule
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.LocalTime
import java.time.format.DateTimeFormatter
import java.time.format.DecimalStyle
import java.time.format.FormatStyle
import java.time.format.TextStyle
import java.time.temporal.ChronoUnit
import java.util.Locale

/**
 * All user-visible formatting of dates, times, numbers and money. Follows the
 * app locale (digits, separators, RTL) and the chosen calendar system, while
 * every amount keeps its own currency.
 */
class UiFormatter(private val context: Context, val locale: Locale, val calendar: CalendarKind) {
    val solarHijri: Boolean get() = calendar == CalendarKind.SOLAR_HIJRI
    private val res = context.resources
    private val isPersian = locale.language == "fa"
    private val decimalStyle = DecimalStyle.of(locale)

    private fun digits(s: String) = if (isPersian) Digits.toPersian(s) else s

    fun number(n: Long): String = java.text.NumberFormat.getIntegerInstance(locale).format(n)

    fun time(t: LocalTime): String = DateTimeFormatter.ofLocalizedTime(FormatStyle.SHORT).withLocale(locale).withDecimalStyle(decimalStyle).format(t)

    fun weekdayShort(day: DayOfWeek): String = day.getDisplayName(TextStyle.SHORT, locale)

    fun weekdayLong(day: DayOfWeek): String = day.getDisplayName(TextStyle.FULL, locale)

    /** "12 Oct" / "12 Oct 2027" (Gregorian) or "۲۰ مهر" (Solar Hijri). */
    fun date(d: LocalDate, withYear: Boolean = d.year != LocalDate.now().year, withWeekday: Boolean = false): String {
        val core = if (calendar != CalendarKind.GREGORIAN) {
            val j = CalendarMath.fromDate(d, calendar)
            val month = monthNames(calendar)[j.month - 1]
            buildString {
                append(digits(j.day.toString())).append(' ').append(month)
                if (withYear) append(' ').append(digits(j.year.toString()))
            }
        } else {
            val pattern = if (withYear) "d MMM yyyy" else "d MMM"
            DateTimeFormatter.ofPattern(if (locale.country == "US" && !isPersian) (if (withYear) "MMM d, yyyy" else "MMM d") else pattern, locale)
                .withDecimalStyle(decimalStyle).format(d)
        }
        return if (withWeekday) "${weekdayShort(d.dayOfWeek)}، $core".let { if (isPersian) it else it.replace("،", ",") } else core
    }

    private fun monthNames(kind: CalendarKind): Array<String> = res.getStringArray(
        if (kind == CalendarKind.LUNAR_HIJRI) R.array.hijri_months else R.array.jalali_months
    )

    fun monthTitle(year: Int, month: Int, kind: CalendarKind = calendar): String = if (kind != CalendarKind.GREGORIAN) {
        "${monthNames(kind)[month - 1]} ${digits(year.toString())}"
    } else {
        DateTimeFormatter.ofPattern("LLLL yyyy", locale).withDecimalStyle(decimalStyle).format(LocalDate.of(year, month, 1))
    }

    fun dayOfMonth(d: LocalDate): String = digits(CalendarMath.fromDate(d, calendar).day.toString())

    /** "Today", "Tomorrow", "Yesterday", weekday name within a week, else a date. */
    fun dayLabel(d: LocalDate, today: LocalDate): String {
        val diff = ChronoUnit.DAYS.between(today, d)
        return when {
            diff == 0L -> res.getString(R.string.today)
            diff == 1L -> res.getString(R.string.tomorrow)
            diff == -1L -> res.getString(R.string.yesterday)
            diff in 2..6 -> weekdayLong(d.dayOfWeek)
            else -> date(d)
        }
    }

    /** "in 4 days" / "3 days ago" / "today". */
    fun relative(d: LocalDate, today: LocalDate): String {
        val diff = ChronoUnit.DAYS.between(today, d).toInt()
        return when {
            diff == 0 -> res.getString(R.string.today)
            diff == 1 -> res.getString(R.string.tomorrow)
            diff == -1 -> res.getString(R.string.yesterday)
            diff > 0 -> res.getQuantityString(R.plurals.in_days, diff, diff).let(::digits)
            else -> res.getQuantityString(R.plurals.days_ago, -diff, -diff).let(::digits)
        }
    }

    fun dateTime(d: LocalDate?, t: LocalTime?, today: LocalDate, allDay: Boolean = false): String {
        if (d == null) return t?.let(::time) ?: res.getString(R.string.no_date)
        val day = dayLabel(d, today)
        return if (t != null && !allDay) "$day · ${time(t)}" else day
    }

    fun money(amountMinor: Long, currency: String, compact: Boolean = false): String =
        MoneyFormatter.format(amountMinor, currency, locale, compact)

    /** Just the grouped number ("32,000,000"), for places where the currency is already shown. */
    fun amount(amountMinor: Long, currency: String): String {
        val digits = app.pocketos.core.money.Currencies.info(currency).fractionDigits
        val value = java.math.BigDecimal.valueOf(amountMinor).movePointLeft(digits)
        val nf = java.text.NumberFormat.getNumberInstance(locale).apply {
            maximumFractionDigits = digits
            minimumFractionDigits = if (value.stripTrailingZeros().scale() > 0) digits else 0
        }
        return nf.format(value)
    }


    fun billing(cycle: BillingCycle): String {
        val n = cycle.interval
        return when (cycle.unit) {
            BillingUnit.DAY -> if (n == 1) res.getString(R.string.billing_daily) else res.getQuantityString(R.plurals.billing_every_days, n, n)
            BillingUnit.WEEK -> if (n == 1) res.getString(R.string.billing_weekly) else res.getQuantityString(R.plurals.billing_every_weeks, n, n)
            BillingUnit.MONTH -> if (n == 1) res.getString(R.string.billing_monthly) else res.getQuantityString(R.plurals.billing_every_months, n, n)
            BillingUnit.YEAR -> if (n == 1) res.getString(R.string.billing_yearly) else res.getQuantityString(R.plurals.billing_every_years, n, n)
        }.let(::digits)
    }

    /** "/mo", "/yr"... short suffix for amounts. */
    fun billingSuffix(cycle: BillingCycle): String = if (cycle.interval != 1) "" else when (cycle.unit) {
        BillingUnit.DAY -> res.getString(R.string.per_day_short)
        BillingUnit.WEEK -> res.getString(R.string.per_week_short)
        BillingUnit.MONTH -> res.getString(R.string.per_month_short)
        BillingUnit.YEAR -> res.getString(R.string.per_year_short)
    }

    fun recurrence(rule: RecurrenceRule?): String {
        if (rule == null) return res.getString(R.string.repeat_never)
        val n = rule.interval
        val text = when (rule.frequency) {
            Frequency.DAILY -> if (n == 1) res.getString(R.string.repeat_daily) else res.getQuantityString(R.plurals.repeat_every_days, n, n)
            Frequency.WEEKLY -> when {
                rule.byDays == RecurrenceRule.WEEKDAYS && n == 1 -> res.getString(R.string.repeat_weekdays)
                rule.byDays.isNotEmpty() -> {
                    val days = rule.byDays.sorted().joinToString(if (isPersian) "، " else ", ") { weekdayShort(it) }
                    if (n == 1) res.getString(R.string.repeat_weekly_on, days) else res.getQuantityString(R.plurals.repeat_every_weeks_on, n, n, days)
                }
                n == 1 -> res.getString(R.string.repeat_weekly)
                else -> res.getQuantityString(R.plurals.repeat_every_weeks, n, n)
            }
            Frequency.MONTHLY -> if (n == 1) res.getString(R.string.repeat_monthly) else res.getQuantityString(R.plurals.repeat_every_months, n, n)
            Frequency.YEARLY -> if (n == 1) res.getString(R.string.repeat_yearly) else res.getQuantityString(R.plurals.repeat_every_years, n, n)
        }
        return digits(text)
    }

    fun offsets(days: List<Int>): String {
        if (days.isEmpty()) return res.getString(R.string.reminders_off)
        return days.sortedDescending().joinToString(if (isPersian) "، " else ", ") {
            when (it) {
                0 -> res.getString(R.string.on_the_day)
                else -> res.getQuantityString(R.plurals.days_before, it, it)
            }
        }.let(::digits)
    }

    fun localizeDigits(text: String) = digits(text)
}

val LocalFormatter = staticCompositionLocalOf<UiFormatter> { error("UiFormatter not provided") }

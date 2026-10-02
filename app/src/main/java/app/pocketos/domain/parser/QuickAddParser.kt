package app.pocketos.domain.parser

import app.pocketos.core.money.Currencies
import app.pocketos.core.money.Digits
import app.pocketos.core.money.MoneyFormatter
import app.pocketos.core.time.JalaliCalendar
import app.pocketos.domain.catalog.ServiceInfo
import app.pocketos.domain.catalog.ServiceMatcher
import app.pocketos.domain.catalog.TextNormalizer
import app.pocketos.domain.categories.Categories
import app.pocketos.domain.model.BillingCycle
import app.pocketos.domain.model.BillingUnit
import app.pocketos.domain.model.Frequency
import app.pocketos.domain.model.Money
import app.pocketos.domain.model.Priority
import app.pocketos.domain.model.RecurrenceRule
import app.pocketos.domain.recurrence.BillingCalculator
import app.pocketos.domain.recurrence.RecurrenceEngine
import app.pocketos.domain.smart.SmartDefaults
import java.math.BigDecimal
import java.math.RoundingMode
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.temporal.TemporalAdjusters

enum class QuickAddType { REMINDER, TASK, SUBSCRIPTION }

/**
 * The parser's interpretation of a sentence. "Assumed" flags mark values the
 * parser guessed (not stated by the user) so the confirmation card can call
 * them out. Nothing is saved until the user confirms.
 */
data class QuickAddResult(
    val input: String,
    val type: QuickAddType,
    val title: String,
    val service: ServiceInfo?,
    val amount: Money?,
    val currencyAssumed: Boolean,
    val date: LocalDate?,
    val time: LocalTime?,
    val dateAssumed: Boolean,
    val timeAssumed: Boolean,
    val recurrence: RecurrenceRule?,
    val billing: BillingCycle?,
    val billingAssumed: Boolean,
    val category: String,
    val priority: Priority,
    val reminderOffsets: List<Int>,
) {
    val isEmpty: Boolean get() = input.isBlank()
}

/**
 * Deterministic natural-language parser for Quick Add (English and Persian).
 *
 * Works on a length-preserving normalised copy of the input. Each recognised
 * phrase is blanked out in that copy; whatever remains becomes the title.
 */
class QuickAddParser(
    private val matcher: ServiceMatcher,
    private val defaultCurrency: String = "USD",
    private val defaultReminderTime: LocalTime = LocalTime.of(9, 0),
    private val monthFirstDates: Boolean = true,
) {

    private class Work(original: String) {
        val original: String = original
        var text: String = normalizeForParsing(original)
        fun consume(range: IntRange) {
            val chars = text.toCharArray()
            for (i in range) if (i in chars.indices) chars[i] = ' '
            text = String(chars)
        }
    }

    fun parse(input: String, now: LocalDateTime): QuickAddResult {
        val today = now.toLocalDate()
        val w = Work(input)

        val priority = extractPriority(w)
        stripLeadPhrases(w)
        var money = extractMoney(w)
        val recurrence = extractRecurrence(w)
        var time: LocalTime? = null
        var timeAssumed = false
        extractTime(w)?.let { (t, assumed) -> time = t; timeAssumed = assumed }
        var date: LocalDate? = null
        extractDate(w, today)?.let { (d, impliedTime) ->
            date = d
            if (time == null && impliedTime != null) {
                time = impliedTime
                timeAssumed = true
            }
        }

        val remaining = w.text
        val hasPaymentWord = PAYMENT_WORDS.containsMatchIn(remaining)
        val serviceMatch = matcher.findIn(titleFrom(w))
        val service = serviceMatch?.service

        var currencyAssumed = false
        if (money == null && (service != null || recurrence?.billing != null || hasPaymentWord)) {
            extractBareAmount(w)?.let {
                money = it
                currencyAssumed = true
            }
        }

        val isSubscription = when {
            recurrence?.rule?.byDays?.isNotEmpty() == true -> false
            recurrence?.billing != null && (money != null || service != null || hasPaymentWord) -> true
            money != null && service != null -> true
            money != null && recurrence?.billing != null -> true
            else -> false
        }
        val rawTitle = titleFrom(w)

        if (isSubscription) {
            var billing = recurrence?.billing
            var billingAssumed = false
            if (billing == null) {
                billing = service?.billing?.toCycle() ?: BillingCycle.MONTHLY
                billingAssumed = true
            }
            var dateAssumed = false
            val renewal = date ?: run {
                dateAssumed = true
                BillingCalculator.occurrence(today, billing, 1)
            }
            val name = subscriptionName(rawTitle, service)
            val category = service?.category ?: Categories.suggestSubscriptionCategory(input) ?: "other"
            return QuickAddResult(
                input = input, type = QuickAddType.SUBSCRIPTION, title = name, service = service,
                amount = money, currencyAssumed = currencyAssumed,
                date = renewal, time = null, dateAssumed = dateAssumed, timeAssumed = false,
                recurrence = null, billing = billing, billingAssumed = billingAssumed,
                category = category, priority = priority,
                reminderOffsets = SmartDefaults.subscriptionReminderOffsets(billing, renewal, today),
            )
        }

        val rule = recurrence?.rule
        var dateAssumed = false
        if (date == null && rule != null) {
            date = firstOccurrenceFrom(rule, today, time, now)
            dateAssumed = true
        }
        if (date == null && time != null) {
            date = if (time!! > now.toLocalTime()) today else today.plusDays(1)
            dateAssumed = true
        }
        if (date != null && time == null) {
            time = defaultReminderTime
            timeAssumed = true
        }
        val type = if (date == null && rule == null) QuickAddType.TASK else QuickAddType.REMINDER
        val category = Categories.suggestReminderCategory(input) ?: if (hasPaymentWord || money != null) "finance" else "personal"
        return QuickAddResult(
            input = input, type = type, title = capitalize(rawTitle).ifBlank { service?.name ?: "" }, service = service,
            amount = money, currencyAssumed = currencyAssumed,
            date = date, time = time, dateAssumed = dateAssumed, timeAssumed = timeAssumed,
            recurrence = rule?.anchoredTo(date), billing = null, billingAssumed = false,
            category = category, priority = priority, reminderOffsets = emptyList(),
        )
    }

    // ---------------------------------------------------------------- money

    private fun extractMoney(w: Work): Money? {
        for (pattern in moneyPatterns) {
            val m = pattern.regex.find(w.text) ?: continue
            val currency = pattern.currency(m) ?: continue
            val amount = toMinor(m.groups["num"]!!.value, m.groups["mult"]?.value, currency) ?: continue
            w.consume(m.range)
            return Money(amount, currency)
        }
        return null
    }

    private fun extractBareAmount(w: Work): Money? {
        val m = BARE_AMOUNT.find(w.text) ?: return null
        val amount = toMinor(m.groups["num"]!!.value, m.groups["mult"]?.value, defaultCurrency) ?: return null
        if (amount <= 0) return null
        w.consume(m.range)
        return Money(amount, defaultCurrency)
    }

    private fun toMinor(number: String, multiplier: String?, currency: String): Long? {
        val decimal = MoneyFormatter.parseDecimal(number.replace(" ", "")) ?: return null
        val factor = when (multiplier?.trim()) {
            null, "" -> BigDecimal.ONE
            "k", "thousand", "هزار" -> BigDecimal(1_000)
            else -> BigDecimal(1_000_000)
        }
        val digits = Currencies.info(currency).fractionDigits
        return runCatching { decimal.multiply(factor).setScale(digits, RoundingMode.HALF_UP).movePointRight(digits).longValueExact() }.getOrNull()
    }

    // ----------------------------------------------------------- recurrence

    private class ParsedRecurrence(val rule: RecurrenceRule, val billing: BillingCycle?)

    private fun extractRecurrence(w: Work): ParsedRecurrence? {
        // Weekday lists: "every monday and wednesday", "هر شنبه"
        WEEKDAY_LIST.find(w.text)?.let { m ->
            val days = WEEKDAY_TOKEN.findAll(m.groups["days"]!!.value).mapNotNull { weekday(it.value) }.toSet()
            if (days.isNotEmpty()) {
                w.consume(m.range)
                return ParsedRecurrence(RecurrenceRule.weekly(1, days), null)
            }
        }
        WEEKDAYS_PHRASE.find(w.text)?.let { m ->
            w.consume(m.range)
            return ParsedRecurrence(RecurrenceRule.weekly(1, RecurrenceRule.WEEKDAYS), null)
        }
        WEEKENDS_PHRASE.find(w.text)?.let { m ->
            w.consume(m.range)
            return ParsedRecurrence(RecurrenceRule.weekly(1, setOf(DayOfWeek.SATURDAY, DayOfWeek.SUNDAY)), null)
        }
        EVERY_N.find(w.text)?.let { m ->
            val n = m.groups["n"]!!.value.toIntOrNull()?.takeIf { it in 1..365 } ?: return@let
            val unit = unitOf(m.groups["unit"]!!.value) ?: return@let
            w.consume(m.range)
            return recurrenceOf(unit, n)
        }
        for ((regex, unit, n) in SIMPLE_RECURRENCE) {
            val m = regex.find(w.text) ?: continue
            w.consume(m.range)
            return recurrenceOf(unit, n)
        }
        return null
    }

    private fun recurrenceOf(unit: BillingUnit, n: Int): ParsedRecurrence {
        val freq = when (unit) {
            BillingUnit.DAY -> Frequency.DAILY
            BillingUnit.WEEK -> Frequency.WEEKLY
            BillingUnit.MONTH -> Frequency.MONTHLY
            BillingUnit.YEAR -> Frequency.YEARLY
        }
        return ParsedRecurrence(RecurrenceRule(freq, n), BillingCycle(unit, n))
    }

    private fun firstOccurrenceFrom(rule: RecurrenceRule, today: LocalDate, time: LocalTime?, now: LocalDateTime): LocalDate {
        val startToday = time == null || time > now.toLocalTime()
        val from = if (startToday) today else today.plusDays(1)
        if (rule.frequency == Frequency.WEEKLY && rule.byDays.isNotEmpty()) {
            return RecurrenceEngine.nextOnOrAfter(rule, from, from) ?: from
        }
        return from
    }

    // ----------------------------------------------------------------- time

    private fun extractTime(w: Work): Pair<LocalTime, Boolean>? {
        PERSIAN_TIME.find(w.text)?.let { m ->
            var hour = m.groups["h"]!!.value.toInt()
            val minute = m.groups["m"]?.value?.toInt() ?: 0
            val part = m.groups["part"]?.value?.replace(" ", "")
            if (hour !in 0..23 || minute !in 0..59) return@let
            var assumed = false
            when {
                part == null -> if (hour in 1..6) { hour += 12; assumed = true }
                part.startsWith("صبح") -> if (hour == 12) hour = 0
                part == "ظهر" -> if (hour < 11) hour += 12
                else -> if (hour < 12) hour += 12 // بعدازظهر، عصر، شب
            }
            w.consume(m.range)
            return LocalTime.of(hour % 24, minute) to assumed
        }
        for (regex in listOf(AT_TIME, CLOCK_TIME, HOUR_AMPM)) {
            val m = regex.find(w.text) ?: continue
            var hour = m.groups["h"]!!.value.toInt()
            val minute = m.groups["m"]?.value?.toInt() ?: 0
            val ampm = m.groups["ampm"]?.value?.replace(".", "")
            if (minute !in 0..59) continue
            var assumed = false
            when (ampm) {
                "am" -> { if (hour !in 1..12) continue; if (hour == 12) hour = 0 }
                "pm" -> { if (hour !in 1..12) continue; if (hour < 12) hour += 12 }
                else -> {
                    if (hour !in 0..23) continue
                    if (regex === AT_TIME && m.groups["m"] == null && hour in 1..6) { hour += 12; assumed = true }
                }
            }
            w.consume(m.range)
            return LocalTime.of(hour, minute) to assumed
        }
        for ((regex, value) in PART_OF_DAY) {
            val m = regex.find(w.text) ?: continue
            w.consume(m.range)
            return value to true
        }
        return null
    }

    // ----------------------------------------------------------------- date

    /** Returns the date and, for words like "tonight", an implied time. */
    private fun extractDate(w: Work, today: LocalDate): Pair<LocalDate, LocalTime?>? {
        for ((regex, offset) in RELATIVE_DAYS) {
            val m = regex.find(w.text) ?: continue
            w.consume(m.range)
            val implied = if (m.value.contains("tonight") || m.value.contains("امشب")) LocalTime.of(20, 0) else null
            return today.plusDays(offset) to implied
        }
        IN_N.find(w.text)?.let { m ->
            val n = numberWord(m.groups["n"]!!.value) ?: return@let
            val unit = unitOf(m.groups["unit"]!!.value) ?: return@let
            w.consume(m.range)
            return BillingCalculator.occurrence(today, BillingCycle(unit, 1), n.toLong()) to null
        }
        PERSIAN_IN_N.find(w.text)?.let { m ->
            val n = m.groups["n"]!!.value.toIntOrNull() ?: return@let
            val unit = unitOf(m.groups["unit"]!!.value) ?: return@let
            w.consume(m.range)
            return BillingCalculator.occurrence(today, BillingCycle(unit, 1), n.toLong()) to null
        }
        for (regex in listOf(NEXT_PERIOD, PERSIAN_NEXT_PERIOD)) {
            val m = regex.find(w.text) ?: continue
            val unit = unitOf(m.groups["unit"]!!.value) ?: continue
            w.consume(m.range)
            return BillingCalculator.occurrence(today, BillingCycle(unit, 1), 1) to null
        }
        END_OF_MONTH.find(w.text)?.let { m ->
            w.consume(m.range)
            val last = today.with(TemporalAdjusters.lastDayOfMonth())
            return (if (last == today) today.plusMonths(1).with(TemporalAdjusters.lastDayOfMonth()) else last) to null
        }
        MONTH_DAY.find(w.text)?.let { m ->
            val month = monthNumber(m.groups["month"]!!.value) ?: return@let
            val day = m.groups["day"]!!.value.toInt()
            val year = m.groups["year"]?.value?.toInt()
            dateOrNull(year, month, day, today)?.let { w.consume(m.range); return it to null }
        }
        DAY_MONTH.find(w.text)?.let { m ->
            val month = monthNumber(m.groups["month"]!!.value) ?: return@let
            val day = m.groups["day"]!!.value.toInt()
            val year = m.groups["year"]?.value?.toInt()
            dateOrNull(year, month, day, today)?.let { w.consume(m.range); return it to null }
        }
        JALALI_DAY_MONTH.find(w.text)?.let { m ->
            val month = JALALI_MONTHS.indexOfFirst { it == m.groups["month"]!!.value.replace(" ", "") } + 1
            val day = m.groups["day"]!!.value.toInt()
            jalaliDate(m.groups["year"]?.value?.toInt(), month, day, today)?.let { w.consume(m.range); return it to null }
        }
        ISO_DATE.find(w.text)?.let { m ->
            val y = m.groups["y"]!!.value.toInt()
            val mo = m.groups["mo"]!!.value.toInt()
            val d = m.groups["d"]!!.value.toInt()
            val date = if (y in 1300..1499) jalaliDate(y, mo, d, today) else runCatching { LocalDate.of(y, mo, d) }.getOrNull()
            date?.let { w.consume(m.range); return it to null }
        }
        SLASH_DATE.find(w.text)?.let { m ->
            val a = m.groups["a"]!!.value.toInt()
            val b = m.groups["b"]!!.value.toInt()
            val y = m.groups["y"]?.value?.toInt()?.let { if (it < 100) 2000 + it else it }
            val (month, day) = if (monthFirstDates) a to b else b to a
            dateOrNull(y, month, day, today)?.let { w.consume(m.range); return it to null }
        }
        WEEKDAY_DATE.find(w.text)?.let { m ->
            val day = weekday(m.groups["day"]!!.value) ?: return@let
            val qualifier = m.groups["q"]?.value?.trim()
            w.consume(m.range)
            val date = if (qualifier == "this" && today.dayOfWeek == day) today else today.with(TemporalAdjusters.next(day))
            return date to null
        }
        DAY_OF_MONTH.find(w.text)?.let { m ->
            val day = m.groups["day"]!!.value.toInt()
            if (day !in 1..31) return@let
            w.consume(m.range)
            var candidate = today.withDayOfMonth(1)
            repeat(13) {
                if (day <= candidate.lengthOfMonth()) {
                    val d = candidate.withDayOfMonth(day)
                    if (d >= today) return d to null
                }
                candidate = candidate.plusMonths(1)
            }
        }
        return null
    }

    private fun dateOrNull(year: Int?, month: Int, day: Int, today: LocalDate): LocalDate? {
        if (month !in 1..12 || day !in 1..31) return null
        if (year != null) return runCatching { LocalDate.of(year, month, day) }.getOrNull()
        val thisYear = runCatching { LocalDate.of(today.year, month, day) }.getOrNull()
        if (thisYear != null && thisYear >= today) return thisYear
        return runCatching { LocalDate.of(today.year + 1, month, day) }.getOrNull()
            ?: runCatching { LocalDate.of(today.year + 1, month, day - 1) }.getOrNull()
    }

    private fun jalaliDate(year: Int?, month: Int, day: Int, today: LocalDate): LocalDate? {
        if (month !in 1..12 || day !in 1..31) return null
        val currentJy = JalaliCalendar.fromGregorian(today).year
        fun build(y: Int) = runCatching { JalaliCalendar.toGregorian(y, month, day) }.getOrNull()
        if (year != null) return build(year)
        val candidate = build(currentJy)
        return if (candidate != null && candidate >= today) candidate else build(currentJy + 1)
    }

    // ---------------------------------------------------------------- misc

    private fun extractPriority(w: Work): Priority {
        val m = PRIORITY_HIGH.find(w.text) ?: return Priority.NORMAL
        w.consume(m.range)
        return Priority.HIGH
    }

    private fun stripLeadPhrases(w: Work) {
        LEAD_PHRASE.find(w.text)?.let { w.consume(it.range) }
    }

    private fun titleFrom(w: Work): String {
        val source = if (w.original.length == w.text.length) {
            buildString {
                for (i in w.original.indices) append(if (w.text[i] == ' ' && w.original[i] != ' ') ' ' else w.original[i])
            }
        } else {
            w.text
        }
        var t = source.replace(Regex("\\s+"), " ").trim()
        repeat(4) {
            t = t.replace(EDGE_WORDS_START, "").replace(EDGE_WORDS_END, "").trim().trim(',', '.', ';', ':', '-', '–', '،', '!').trim()
        }
        return t
    }

    private fun subscriptionName(rawTitle: String, service: ServiceInfo?): String {
        if (service == null) return capitalize(rawTitle).ifBlank { "" }
        val stripped = TextNormalizer.normalize(rawTitle).replace(SUBSCRIPTION_FILLER, " ").replace(Regex("\\s+"), " ").trim()
        val terms = (listOf(service.name) + service.aliases).map { TextNormalizer.normalize(it) }
        val matchedAlias = (listOf(service.name) + service.aliases).firstOrNull { TextNormalizer.normalize(it) == stripped }
        return when {
            stripped.isEmpty() -> service.name
            matchedAlias != null && service.generic && !matchedAlias.any { it.code < 128 } -> rawTitle.trim()
            stripped in terms -> service.name
            else -> capitalize(rawTitle)
        }
    }

    private fun capitalize(s: String): String = s.trim().replaceFirstChar { if (it.isLowerCase()) it.titlecase() else it.toString() }

    companion object {
        private const val B0 = "(?<![\\p{L}\\p{N}])"
        private const val B1 = "(?![\\p{L}\\p{N}])"
        private fun rx(pattern: String) = Regex(pattern, RegexOption.IGNORE_CASE)

        /** Length-preserving normalisation: ASCII digits, lower case, unified separators. */
        internal fun normalizeForParsing(text: String): String {
            val ascii = Digits.toAscii(text)
            val sb = StringBuilder(ascii.length)
            for (c in ascii) {
                sb.append(
                    when (c) {
                        '٬' -> ','
                        '٫' -> '.'
                        '‌', '‍', ' ' -> ' '
                        'ي' -> 'ی'
                        'ك' -> 'ک'
                        else -> if (c.isUpperCase() && c.lowercaseChar().toString().length == 1) c.lowercaseChar() else c
                    }
                )
            }
            return sb.toString()
        }

        private const val NUM = "(?<num>\\d{1,3}(?:[,.]\\d{3})+(?:[.,]\\d{1,2})?|\\d+(?:[.,]\\d{1,2})?)"
        private const val MULT = "(?:\\s*(?<mult>k|thousand|هزار|million|mil|میلیون))?"

        private class MoneyPattern(val regex: Regex, val currency: (MatchResult) -> String?)

        private val CURRENCY_WORDS: List<Pair<String, String>> = listOf(
            "dollars?|bucks?|usd|دلار" to "USD",
            "euros?|eur|یورو" to "EUR",
            "pounds?|gbp|پوند" to "GBP",
            "tomans?|تومان|تومن" to "IRT",
            "rials?|irr|ریال" to "IRR",
            "lira|try|لیر" to "TRY",
            "dirhams?|aed|درهم" to "AED",
            "rupees?|inr" to "INR",
            "yen|jpy" to "JPY",
            "cad" to "CAD",
            "aud" to "AUD",
            "chf|francs?" to "CHF",
            "rubles?|rub|روبل" to "RUB",
            "yuan|cny|یوان" to "CNY",
        )
        private val SYMBOLS = mapOf('$' to "USD", '€' to "EUR", '£' to "GBP", '₹' to "INR", '¥' to "JPY")
        private val CURRENCY_ALT = CURRENCY_WORDS.joinToString("|") { "(?:${it.first})" }

        private fun currencyFor(word: String): String? =
            CURRENCY_WORDS.firstOrNull { (pattern, _) -> Regex("^(?:$pattern)$", RegexOption.IGNORE_CASE).matches(word.trim()) }?.second

        private val moneyPatterns = listOf(
            MoneyPattern(rx("(?<sym>[$€£₹¥])\\s?$NUM$MULT${B1}")) { SYMBOLS[it.groups["sym"]!!.value[0]] },
            MoneyPattern(rx("${B0}$NUM$MULT\\s*(?<cur>$CURRENCY_ALT)${B1}")) { currencyFor(it.groups["cur"]!!.value) },
            MoneyPattern(rx("${B0}(?<cur>usd|eur|gbp|irr|irt|try|aed|inr|jpy|cad|aud|chf|rub|cny)\\s?$NUM$MULT${B1}")) {
                it.groups["cur"]!!.value.uppercase()
            },
        )
        private val BARE_AMOUNT = rx("${B0}$NUM(?:\\s*(?<mult>k|هزار|میلیون))?${B1}")

        private val UNIT_WORDS = "day|days|week|weeks|month|months|year|years|روز|هفته|ماه|سال"
        private val EVERY_N = rx("${B0}(?:every|each|هر)\\s+(?<n>\\d{1,3})\\s*(?<unit>$UNIT_WORDS)${B1}")

        private val SIMPLE_RECURRENCE: List<Triple<Regex, BillingUnit, Int>> = listOf(
            Triple(rx("${B0}(?:every|each)\\s+other\\s+day${B1}|${B0}یک\\s+روز\\s+در\\s+میان${B1}"), BillingUnit.DAY, 2),
            Triple(rx("${B0}(?:every|each)\\s+other\\s+week${B1}|${B0}(?:bi-?weekly|fortnightly)${B1}|${B0}هر\\s+دو\\s+هفته${B1}"), BillingUnit.WEEK, 2),
            Triple(rx("${B0}(?:every|each)\\s+other\\s+month${B1}|${B0}bi-?monthly${B1}|${B0}هر\\s+دو\\s+ماه${B1}"), BillingUnit.MONTH, 2),
            Triple(rx("${B0}quarterly${B1}|${B0}(?:every|each)\\s+quarter${B1}|${B0}فصلی${B1}|${B0}هر\\s+سه\\s+ماه${B1}"), BillingUnit.MONTH, 3),
            Triple(rx("${B0}(?:semi-?annually|twice\\s+a\\s+year)${B1}|${B0}هر\\s+شش\\s+ماه${B1}"), BillingUnit.MONTH, 6),
            Triple(rx("${B0}(?:daily|every\\s*day|each\\s+day|per\\s+day|a\\s+day)${B1}|${B0}(?:روزانه|هر\\s*روز)${B1}"), BillingUnit.DAY, 1),
            Triple(rx("${B0}(?:weekly|every\\s+week|each\\s+week|per\\s+week|a\\s+week)${B1}|/(?:wk|week)${B1}|${B0}(?:هفتگی|هر\\s+هفته)${B1}"), BillingUnit.WEEK, 1),
            Triple(rx("${B0}(?:monthly|every\\s+month|each\\s+month|per\\s+month|a\\s+month)${B1}|/(?:mo|month)${B1}|${B0}(?:ماهانه|ماهیانه|هر\\s+ماه)${B1}"), BillingUnit.MONTH, 1),
            Triple(rx("${B0}(?:yearly|annually|annual|every\\s+year|each\\s+year|per\\s+year|a\\s+year)${B1}|/(?:yr|year)${B1}|${B0}(?:سالانه|سالیانه|هر\\s+سال)${B1}"), BillingUnit.YEAR, 1),
        )

        private const val EN_DAYS = "monday|mon|tuesday|tues|tue|wednesday|wed|thursday|thurs|thu|friday|fri|saturday|sat|sunday|sun"
        private const val FA_DAYS = "یکشنبه|یک\\s+شنبه|دوشنبه|دو\\s+شنبه|سه\\s*شنبه|چهارشنبه|چهار\\s+شنبه|پنجشنبه|پنج\\s*شنبه|جمعه|شنبه"
        private val WEEKDAY_TOKEN = rx("${B0}(?:$EN_DAYS|$FA_DAYS)${B1}")
        private val WEEKDAY_LIST = rx(
            "${B0}(?:every|each|on|هر)\\s+(?<days>(?:$EN_DAYS|$FA_DAYS)s?(?:\\s*(?:,|and|&|و|،)\\s*(?:$EN_DAYS|$FA_DAYS)s?)*)${B1}"
        )
        private val WEEKDAYS_PHRASE = rx("${B0}(?:every\\s+weekday|on\\s+weekdays|weekdays|روزهای\\s+کاری)${B1}")
        private val WEEKENDS_PHRASE = rx("${B0}(?:every\\s+weekend|on\\s+weekends|weekends)${B1}")
        private val WEEKDAY_DATE = rx("${B0}(?:(?<q>next|this|on|coming)\\s+)?(?<day>$EN_DAYS|$FA_DAYS)${B1}")

        private fun weekday(token: String): DayOfWeek? {
            val t = token.trim().lowercase().replace(Regex("\\s+"), "").removeSuffix("s")
            return when {
                t.startsWith("mon") || t == "دوشنبه" -> DayOfWeek.MONDAY
                t.startsWith("tue") || t == "سهشنبه" -> DayOfWeek.TUESDAY
                t.startsWith("wed") || t == "چهارشنبه" -> DayOfWeek.WEDNESDAY
                t.startsWith("thu") || t == "پنجشنبه" -> DayOfWeek.THURSDAY
                t.startsWith("fri") || t == "جمعه" -> DayOfWeek.FRIDAY
                t.startsWith("sat") || t == "شنبه" -> DayOfWeek.SATURDAY
                t.startsWith("sun") || t == "یکشنبه" -> DayOfWeek.SUNDAY
                else -> null
            }
        }

        private fun unitOf(word: String): BillingUnit? = when (word.lowercase().trim()) {
            "day", "days", "روز" -> BillingUnit.DAY
            "week", "weeks", "هفته" -> BillingUnit.WEEK
            "month", "months", "ماه" -> BillingUnit.MONTH
            "year", "years", "سال" -> BillingUnit.YEAR
            else -> null
        }

        private fun numberWord(word: String): Int? = when (word.lowercase()) {
            "a", "an", "one" -> 1
            "two" -> 2
            "three" -> 3
            "four" -> 4
            "five" -> 5
            "six" -> 6
            "seven" -> 7
            "ten" -> 10
            else -> word.toIntOrNull()?.takeIf { it in 0..3650 }
        }

        private val AT_TIME = rx("${B0}(?:at|@|by)\\s*(?<h>\\d{1,2})(?::(?<m>\\d{2}))?\\s*(?<ampm>a\\.?m\\.?|p\\.?m\\.?)?(?![\\p{L}\\p{N}])")
        private val CLOCK_TIME = rx("${B0}(?<h>\\d{1,2}):(?<m>\\d{2})\\s*(?<ampm>a\\.?m\\.?|p\\.?m\\.?)?(?![\\p{L}\\p{N}])")
        private val HOUR_AMPM = rx("${B0}(?<h>\\d{1,2})\\s*(?<ampm>am|pm|a\\.m\\.|p\\.m\\.)(?![\\p{L}\\p{N}])")
        private val PERSIAN_TIME = rx("${B0}ساعت\\s*(?<h>\\d{1,2})(?::(?<m>\\d{2}))?(?:\\s*(?<part>صبح|ظهر|بعد\\s*از\\s*ظهر|بعدازظهر|عصر|شب))?${B1}")
        private val PART_OF_DAY: List<Pair<Regex, LocalTime>> = listOf(
            rx("${B0}(?:at\\s+)?(?:noon|midday)${B1}|${B0}ظهر${B1}") to LocalTime.NOON,
            rx("${B0}(?:at\\s+)?midnight${B1}|${B0}نیمه\\s*شب${B1}") to LocalTime.of(23, 59),
            rx("${B0}(?:in\\s+the\\s+)?morning${B1}|${B0}صبح${B1}") to LocalTime.of(9, 0),
            rx("${B0}(?:in\\s+the\\s+)?afternoon${B1}|${B0}بعد\\s*از\\s*ظهر${B1}|${B0}بعدازظهر${B1}") to LocalTime.of(15, 0),
            rx("${B0}(?:in\\s+the\\s+)?evening${B1}|${B0}عصر${B1}") to LocalTime.of(18, 0),
            rx("${B0}(?:at\\s+)?night${B1}|${B0}شب${B1}") to LocalTime.of(21, 0),
        )

        private val RELATIVE_DAYS: List<Pair<Regex, Long>> = listOf(
            rx("${B0}(?:the\\s+)?day\\s+after\\s+tomorrow${B1}|${B0}پس\\s*فردا${B1}") to 2L,
            rx("${B0}(?:tomorrow|tmrw|tmr)${B1}|${B0}فردا${B1}") to 1L,
            rx("${B0}(?:today|tonight)${B1}|${B0}(?:امروز|امشب)${B1}") to 0L,
        )
        private val IN_N = rx("${B0}in\\s+(?<n>\\d{1,3}|a|an|one|two|three|four|five|six|seven|ten)\\s+(?<unit>days?|weeks?|months?|years?)${B1}")
        private val PERSIAN_IN_N = rx("${B0}(?<n>\\d{1,3})\\s+(?<unit>روز|هفته|ماه|سال)\\s+(?:دیگه|دیگر|بعد)${B1}")
        private val NEXT_PERIOD = rx("${B0}next\\s+(?<unit>week|month|year)${B1}")
        private val PERSIAN_NEXT_PERIOD = rx("${B0}(?<unit>هفته|ماه|سال)\\s+(?:بعد|آینده|دیگه)${B1}")
        private val END_OF_MONTH = rx("${B0}(?:at\\s+the\\s+)?end\\s+of\\s+(?:the\\s+)?month${B1}|${B0}آخر\\s+ماه${B1}")

        private val MONTHS = listOf(
            "january|jan", "february|feb", "march|mar", "april|apr", "may", "june|jun",
            "july|jul", "august|aug", "september|sept|sep", "october|oct", "november|nov", "december|dec",
        )
        private val MONTH_ALT = MONTHS.joinToString("|")
        private fun monthNumber(word: String): Int? {
            val w = word.lowercase().trimEnd('.')
            return MONTHS.indexOfFirst { alt -> alt.split('|').any { it == w } }.takeIf { it >= 0 }?.plus(1)
        }
        private val MONTH_DAY = rx("${B0}(?:on\\s+)?(?<month>$MONTH_ALT)\\.?\\s+(?<day>\\d{1,2})(?:st|nd|rd|th)?(?:,?\\s+(?<year>\\d{4}))?${B1}")
        private val DAY_MONTH = rx("${B0}(?:on\\s+)?(?:the\\s+)?(?<day>\\d{1,2})(?:st|nd|rd|th)?\\s+(?:of\\s+)?(?<month>$MONTH_ALT)\\.?(?:,?\\s+(?<year>\\d{4}))?${B1}")
        private val JALALI_MONTHS = listOf("فروردین", "اردیبهشت", "خرداد", "تیر", "مرداد", "شهریور", "مهر", "آبان", "آذر", "دی", "بهمن", "اسفند")
        private val JALALI_DAY_MONTH = rx("${B0}(?<day>\\d{1,2})(?:\\s*ام)?\\s+(?<month>${JALALI_MONTHS.joinToString("|")})(?:\\s+(?<year>\\d{4}))?${B1}")
        private val ISO_DATE = rx("${B0}(?<y>\\d{4})[-/](?<mo>\\d{1,2})[-/](?<d>\\d{1,2})${B1}")
        private val SLASH_DATE = rx("${B0}(?:on\\s+)?(?<a>\\d{1,2})/(?<b>\\d{1,2})(?:/(?<y>\\d{2}|\\d{4}))?${B1}")
        private val DAY_OF_MONTH = rx("${B0}on\\s+the\\s+(?<day>\\d{1,2})(?:st|nd|rd|th)?${B1}")

        private val PRIORITY_HIGH = rx("${B0}(?:urgent|important|asap|high\\s+priority|فوری|مهم)${B1}|!!+")
        private val LEAD_PHRASE = rx(
            "^\\s*(?:please\\s+)?(?:remind\\s+me\\s+(?:to|about|of)|remind\\s+me|reminder\\s*(?:to|:)?|don'?t\\s+forget\\s+(?:to)?|remember\\s+to|todo\\s*:?|task\\s*:?|" +
                "یادم\\s*(?:بنداز|بیار|باشه)(?:\\s+که)?|یادآوری\\s*(?:کن)?(?:\\s+که)?|یادت\\s+باشه(?:\\s+که)?)\\s*"
        )
        private val PAYMENT_WORDS = rx("${B0}(?:pay|paying|payment|bill|renew|renewal|subscription|subscribe|fee|membership|plan|premium|پرداخت|قبض|تمدید|اشتراک|حق\\s+عضویت|شارژ)${B1}")
        private val SUBSCRIPTION_FILLER = Regex(
            "(?<![\\p{L}\\p{N}])(?:pay|paying|payment|for|my|the|bill|renew|renewal|subscription|subscribe|plan|membership|fee|account|اشتراک|پرداخت|تمدید|قبض|برای|من)(?![\\p{L}\\p{N}])"
        )
        private val EDGE_WORDS_START = rx("^(?:on|at|in|for|by|the|every|to|from|and|و|در|هر|ساعت|برای|که)(?:\\s+|$)")
        private val EDGE_WORDS_END = rx("(?:^|\\s+)(?:on|at|in|for|by|the|every|to|from|and|of|و|در|هر|ساعت|برای|که|،)$")
    }
}

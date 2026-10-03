package app.pocketos.core.money

import java.math.BigDecimal
import java.math.RoundingMode
import java.text.NumberFormat
import java.util.Currency
import java.util.Locale

data class CurrencyInfo(
    val code: String,
    val fractionDigits: Int,
    val englishName: String,
    val persianName: String,
    val symbol: String?,
)

/**
 * Currency metadata. Amounts are always stored with their own currency and
 * never converted. "IRT" (Iranian toman = 10 rials) is not an ISO 4217 code
 * but is used by convention in Iran; it is supported explicitly.
 */
object Currencies {

    private val custom = mapOf(
        "IRT" to CurrencyInfo("IRT", 0, "Toman", "تومان", null),
        "IRR" to CurrencyInfo("IRR", 0, "Rial", "ریال", null),
    )

    val common: List<String> = listOf("USD", "EUR", "GBP", "IRT", "IRR", "AED", "TRY", "CAD", "AUD", "JPY", "INR", "CHF", "CNY", "RUB", "SAR")

    private val persianNames = mapOf(
        "USD" to "دلار", "EUR" to "یورو", "GBP" to "پوند", "AED" to "درهم", "TRY" to "لیر",
        "CAD" to "دلار کانادا", "AUD" to "دلار استرالیا", "JPY" to "ین", "INR" to "روپیه",
        "CHF" to "فرانک", "CNY" to "یوان", "RUB" to "روبل", "SAR" to "ریال سعودی",
    )

    /**
     * Every currency in circulation today: the current currency of each ISO
     * country (so historic codes like FRF are excluded), plus Toman. Common
     * ones first, the rest alphabetical.
     */
    val all: List<String> by lazy {
        val current = Locale.getISOCountries().mapNotNull { country ->
            runCatching { Currency.getInstance(Locale("", country))?.currencyCode }.getOrNull()
        }.filter { it.length == 3 && it != "XXX" }.toSet()
        common + (current + custom.keys - common.toSet()).sorted()
    }

    /** Matches code, English name, Persian name or a country's name (e.g. "japan" finds JPY). */
    fun search(query: String, locale: Locale): List<String> {
        val q = query.trim().lowercase(locale)
        if (q.isEmpty()) return all
        return all.filter { code ->
            val info = info(code)
            code.lowercase(Locale.ROOT).contains(q) ||
                info.englishName.lowercase(Locale.ROOT).contains(q) ||
                info.persianName.contains(q) ||
                countryNames(code, locale).any { it.contains(q) }
        }
    }

    private val countriesByCurrency: Map<String, List<String>> by lazy {
        Locale.getISOCountries().groupBy { country ->
            runCatching { Currency.getInstance(Locale("", country))?.currencyCode }.getOrNull() ?: ""
        }
    }

    private fun countryNames(code: String, locale: Locale): List<String> {
        val countries = countriesByCurrency[if (code == "IRT") "IRR" else code].orEmpty()
        return countries.flatMap { c ->
            val l = Locale("", c)
            listOf(l.getDisplayCountry(Locale.ENGLISH).lowercase(Locale.ROOT), l.getDisplayCountry(locale).lowercase(locale))
        }
    }

    fun isSupported(code: String): Boolean =
        code in custom || runCatching { Currency.getInstance(code) }.isSuccess

    fun info(code: String): CurrencyInfo {
        custom[code]?.let { return it }
        val currency = runCatching { Currency.getInstance(code) }.getOrNull()
            ?: return CurrencyInfo(code, 2, code, code, null)
        return CurrencyInfo(
            code = code,
            fractionDigits = currency.defaultFractionDigits.coerceAtLeast(0),
            englishName = currency.getDisplayName(Locale.ENGLISH),
            persianName = persianNames[code] ?: currency.getDisplayName(Locale.forLanguageTag("fa")),
            symbol = currency.getSymbol(Locale.US).takeIf { it != code },
        )
    }

    /** Currencies whose code doesn't start with their issuer's ISO 3166 region. */
    private val flagRegions = mapOf("EUR" to "EU", "IRT" to "IR", "IRR" to "IR", "ANG" to "CW", "XCD" to "AG")
    private val isoRegions: Set<String> by lazy { Locale.getISOCountries().toSet() }

    /**
     * Flag emoji for a currency (USD -> US flag, EUR -> EU flag, IRT -> Iran
     * flag), or a globe for supranational / commodity codes without a flag.
     */
    fun flag(code: String): String {
        val upper = code.uppercase(Locale.ROOT)
        val region = flagRegions[upper]
            ?: upper.take(2).takeIf { upper.length == 3 && !upper.startsWith("X") && it in isoRegions }
            ?: return "🌐"
        return buildString { region.forEach { ch -> appendCodePoint(0x1F1E6 + (ch - 'A')) } }
    }

    fun displayName(code: String, locale: Locale): String {
        val info = info(code)
        return if (locale.language == "fa") info.persianName else info.englishName
    }
}

object MoneyFormatter {

    /**
     * Formats [amountMinor] in [currencyCode] using [locale]'s number style
     * (digits, separators, RTL) while keeping the record's own currency.
     */
    fun format(amountMinor: Long, currencyCode: String, locale: Locale, compact: Boolean = false): String {
        val info = Currencies.info(currencyCode)
        val value = BigDecimal.valueOf(amountMinor).movePointLeft(info.fractionDigits)
        val showFraction = !compact || value.stripTrailingZeros().scale() > 0
        if (currencyCode in setOf("IRT", "IRR") || info.symbol == null && locale.language == "fa") {
            val nf = NumberFormat.getNumberInstance(locale).apply {
                maximumFractionDigits = if (showFraction) info.fractionDigits else 0
                minimumFractionDigits = if (showFraction) info.fractionDigits else 0
            }
            val name = if (locale.language == "fa") info.persianName else info.englishName
            return "${nf.format(value)} $name"
        }
        val nf = NumberFormat.getCurrencyInstance(locale).apply {
            runCatching { currency = Currency.getInstance(currencyCode) }
            maximumFractionDigits = if (showFraction) info.fractionDigits else 0
            minimumFractionDigits = if (showFraction) info.fractionDigits else 0
        }
        return nf.format(value)
    }

    /** Plain number without currency (for input fields). */
    fun formatPlain(amountMinor: Long, currencyCode: String): String {
        val info = Currencies.info(currencyCode)
        return BigDecimal.valueOf(amountMinor).movePointLeft(info.fractionDigits).stripTrailingZeros().toPlainString()
    }

    /**
     * Parses user input such as "15", "15.99", "1,234.50", "۴۵۰٬۰۰۰" or
     * "450.000" into minor units. Returns null when not a valid amount.
     */
    fun parse(input: String, currencyCode: String): Long? {
        val normalized = Digits.toAscii(input).trim()
            .replace('٬', ',').replace('٫', '.').replace(" ", "").replace(" ", "")
        if (normalized.isEmpty() || !normalized.matches(Regex("[0-9.,]+"))) return null
        val decimal = parseDecimal(normalized) ?: return null
        if (decimal.signum() < 0) return null
        val digits = Currencies.info(currencyCode).fractionDigits
        return runCatching { decimal.setScale(digits, RoundingMode.HALF_UP).movePointRight(digits).longValueExact() }.getOrNull()
            ?.takeIf { it <= 999_999_999_999_999L }
    }

    internal fun parseDecimal(text: String): BigDecimal? {
        val lastComma = text.lastIndexOf(',')
        val lastDot = text.lastIndexOf('.')
        val cleaned = when {
            lastComma >= 0 && lastDot >= 0 -> {
                // The right-most separator is the decimal separator.
                if (lastDot > lastComma) text.replace(",", "") else text.replace(".", "").replace(',', '.')
            }
            lastComma >= 0 -> {
                val groups = text.split(',')
                if (groups.size == 2 && groups[1].length in 1..2) text.replace(',', '.') else text.replace(",", "")
            }
            lastDot >= 0 -> {
                val groups = text.split('.')
                if (groups.size > 2 || (groups.size == 2 && groups[1].length == 3 && groups[0].length in 1..3 && groups[0] != "0")) {
                    text.replace(".", "") // "450.000" style thousands grouping
                } else {
                    text
                }
            }
            else -> text
        }
        return cleaned.toBigDecimalOrNull()
    }
}

object Digits {
    private const val PERSIAN = "۰۱۲۳۴۵۶۷۸۹"
    private const val ARABIC = "٠١٢٣٤٥٦٧٨٩"

    /** Converts Persian/Arabic-Indic digits to ASCII; length is preserved. */
    fun toAscii(text: String): String {
        if (text.none { it in PERSIAN || it in ARABIC }) return text
        val sb = StringBuilder(text.length)
        for (c in text) {
            val p = PERSIAN.indexOf(c)
            val a = ARABIC.indexOf(c)
            sb.append(
                when {
                    p >= 0 -> '0' + p
                    a >= 0 -> '0' + a
                    else -> c
                }
            )
        }
        return sb.toString()
    }

    fun toPersian(text: String): String = buildString(text.length) {
        for (c in text) append(if (c in '0'..'9') PERSIAN[c - '0'] else c)
    }

    fun localize(text: String, locale: Locale): String = if (locale.language == "fa") toPersian(text) else text
}

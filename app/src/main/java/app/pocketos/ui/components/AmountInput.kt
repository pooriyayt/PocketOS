package app.pocketos.ui.components

import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.input.OffsetMapping
import androidx.compose.ui.text.input.TransformedText
import androidx.compose.ui.text.input.VisualTransformation
import app.pocketos.core.money.Currencies
import app.pocketos.core.money.Digits

/**
 * Amount entry: the field stores a plain machine value ("1250000.5") while
 * the user sees it grouped in threes ("1,250,000.5", or "۱٬۲۵۰٬۰۰۰٫۵" in
 * Persian). Separators are display-only, so the cursor, deletion and
 * parsing all stay simple.
 */
object AmountInput {
    private const val MAX_INTEGER_DIGITS = 15

    /**
     * Normalises raw keyboard input: Persian/Arabic digits become ASCII, any
     * of `. , ٫` act as the decimal point (only the first one counts, and
     * only when [currency] has minor units), everything else is dropped.
     */
    fun sanitize(input: String, currency: String): String {
        val fraction = Currencies.info(currency).fractionDigits
        val ascii = Digits.toAscii(input)
        // A pasted "1,234.50" uses commas for grouping: drop them first.
        val text = if ((ascii.contains('.') && ascii.contains(',')) || ascii.count { it == ',' } > 1) ascii.replace(",", "") else ascii
        val sb = StringBuilder()
        var seenPoint = false
        var fractionLen = 0
        var intLen = 0
        for (ch in text) {
            when {
                ch in '0'..'9' -> {
                    if (seenPoint) {
                        if (fractionLen < fraction) { sb.append(ch); fractionLen++ }
                    } else if (intLen < MAX_INTEGER_DIGITS) {
                        sb.append(ch); intLen++
                    }
                }
                (ch == '.' || ch == ',' || ch == '٫') && !seenPoint && fraction > 0 -> {
                    if (intLen == 0) { sb.append('0'); intLen = 1 }
                    sb.append('.'); seenPoint = true
                }
            }
        }
        // Drop leading zeros ("007" -> "7") but keep "0" and "0.x".
        val out = sb.toString()
        val point = out.indexOf('.')
        val intPart = if (point >= 0) out.substring(0, point) else out
        val trimmed = intPart.trimStart('0').ifEmpty { if (intPart.isNotEmpty()) "0" else "" }
        return trimmed + (if (point >= 0) out.substring(point) else "")
    }

    /** Re-fits an already-clean value to another currency's minor units (USD 12.50 -> IRT 12). */
    fun adapt(value: String, currency: String): String {
        val fraction = Currencies.info(currency).fractionDigits
        val point = value.indexOf('.')
        if (point < 0) return value
        val frac = value.substring(point + 1).take(fraction)
        return value.substring(0, point) + if (fraction > 0) ".$frac" else ""
    }

    /** Groups the integer part in threes; localises digits and separators for Persian. */
    class Grouping(private val persian: Boolean) : VisualTransformation {
        override fun filter(text: AnnotatedString): TransformedText {
            val raw = text.text
            val point = raw.indexOf('.').let { if (it < 0) raw.length else it }
            val group = if (persian) '٬' else ','
            val decimal = if (persian) '٫' else '.'
            val out = StringBuilder()
            val origToTrans = IntArray(raw.length + 1)
            val transToOrig = ArrayList<Int>(raw.length * 2)
            for (i in raw.indices) {
                if (i in 1 until point && (point - i) % 3 == 0) {
                    out.append(group)
                    transToOrig.add(i)
                }
                origToTrans[i] = out.length
                val ch = raw[i]
                out.append(
                    when {
                        ch == '.' -> decimal
                        persian && ch in '0'..'9' -> '۰' + (ch - '0')
                        else -> ch
                    }
                )
                transToOrig.add(i)
            }
            origToTrans[raw.length] = out.length
            transToOrig.add(raw.length)
            val mapping = object : OffsetMapping {
                override fun originalToTransformed(offset: Int): Int = origToTrans[offset.coerceIn(0, raw.length)]
                override fun transformedToOriginal(offset: Int): Int = transToOrig[offset.coerceIn(0, transToOrig.lastIndex)]
            }
            return TransformedText(AnnotatedString(out.toString()), mapping)
        }

        override fun equals(other: Any?): Boolean = other is Grouping && other.persian == persian
        override fun hashCode(): Int = persian.hashCode()
    }
}

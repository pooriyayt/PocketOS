package app.pocketos.domain.catalog

import app.pocketos.core.money.Digits
import java.text.Normalizer
import java.util.Locale

/** Normalisation used for matching user text against names and aliases. */
object TextNormalizer {
    private val marks = Regex("\\p{Mn}+")
    private val nonWord = Regex("[^\\p{L}\\p{N}+]+")

    fun normalize(text: String): String {
        var s = Digits.toAscii(text).lowercase(Locale.ROOT)
        // Unify Arabic/Persian letter variants and remove zero-width joiners.
        s = s.replace('ي', 'ی').replace('ك', 'ک').replace('ة', 'ه').replace('‌', ' ').replace('‍', ' ')
        s = Normalizer.normalize(s, Normalizer.Form.NFD).replace(marks, "")
        return s.replace(nonWord, " ").trim().replace(Regex("\\s+"), " ")
    }

    /** Collapses everything to letters/digits only ("Disney+" -> "disney+", "You Tube" -> "youtube"). */
    fun compact(text: String): String = normalize(text).replace(" ", "")

    /** Optimal string alignment distance, bounded for speed. */
    fun editDistance(a: String, b: String, max: Int = 3): Int {
        if (kotlin.math.abs(a.length - b.length) > max) return max + 1
        val rows = a.length + 1
        val cols = b.length + 1
        val d = Array(rows) { IntArray(cols) }
        for (i in 0 until rows) d[i][0] = i
        for (j in 0 until cols) d[0][j] = j
        for (i in 1 until rows) {
            var rowMin = Int.MAX_VALUE
            for (j in 1 until cols) {
                val cost = if (a[i - 1] == b[j - 1]) 0 else 1
                var v = minOf(d[i - 1][j] + 1, d[i][j - 1] + 1, d[i - 1][j - 1] + cost)
                if (i > 1 && j > 1 && a[i - 1] == b[j - 2] && a[i - 2] == b[j - 1]) v = minOf(v, d[i - 2][j - 2] + 1)
                d[i][j] = v
                rowMin = minOf(rowMin, v)
            }
            if (rowMin > max) return max + 1
        }
        return d[a.length][b.length]
    }
}

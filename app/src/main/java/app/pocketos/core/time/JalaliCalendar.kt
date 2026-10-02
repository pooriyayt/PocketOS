package app.pocketos.core.time

import java.time.LocalDate

/**
 * Solar Hijri (Jalali / Persian) calendar conversion based on the widely used
 * "jalaali" break-year algorithm (valid for Jalali years -61..3177).
 */
object JalaliCalendar {

    data class JalaliDate(val year: Int, val month: Int, val day: Int)

    private val BREAKS = intArrayOf(-61, 9, 38, 199, 426, 686, 756, 818, 1111, 1181, 1210, 1635, 2060, 2097, 2192, 2262, 2324, 2394, 2456, 3178)
    private const val EPOCH_JDN_OFFSET = 2440588L // JDN of 1970-01-01

    fun fromGregorian(date: LocalDate): JalaliDate = d2j(date.toEpochDay() + EPOCH_JDN_OFFSET)

    fun toGregorian(year: Int, month: Int, day: Int): LocalDate {
        require(month in 1..12 && day in 1..monthLength(year, month)) { "invalid Jalali date $year/$month/$day" }
        return LocalDate.ofEpochDay(j2d(year, month, day) - EPOCH_JDN_OFFSET)
    }

    fun isLeapYear(year: Int): Boolean = jalCal(year).leap == 0

    fun monthLength(year: Int, month: Int): Int = when {
        month <= 6 -> 31
        month <= 11 -> 30
        isLeapYear(year) -> 30
        else -> 29
    }

    private class CalResult(val leap: Int, val gy: Int, val march: Int)

    private fun jalCal(jy: Int): CalResult {
        val bl = BREAKS.size
        val gy = jy + 621
        var leapJ = -14
        var jp = BREAKS[0]
        require(jy >= jp && jy < BREAKS[bl - 1]) { "Jalali year out of range: $jy" }
        var jump = 0
        for (i in 1 until bl) {
            val jm = BREAKS[i]
            jump = jm - jp
            if (jy < jm) break
            leapJ += jump / 33 * 8 + (jump % 33) / 4
            jp = jm
        }
        var n = jy - jp
        leapJ += n / 33 * 8 + ((n % 33) + 3) / 4
        if (jump % 33 == 4 && jump - n == 4) leapJ += 1
        val leapG = gy / 4 - (gy / 100 + 1) * 3 / 4 - 150
        val march = 20 + leapJ - leapG
        if (jump - n < 6) n = n - jump + (jump + 4) / 33 * 33
        var leap = ((n + 1) % 33 - 1) % 4
        if (leap == -1) leap = 4
        return CalResult(leap, gy, march)
    }

    private fun g2d(gy: Int, gm: Int, gd: Int): Long = LocalDate.of(gy, gm, gd).toEpochDay() + EPOCH_JDN_OFFSET

    private fun j2d(jy: Int, jm: Int, jd: Int): Long {
        val r = jalCal(jy)
        return g2d(r.gy, 3, r.march) + (jm - 1) * 31 - jm / 7 * (jm - 7) + jd - 1
    }

    private fun d2j(jdn: Long): JalaliDate {
        val gy = LocalDate.ofEpochDay(jdn - EPOCH_JDN_OFFSET).year
        var jy = gy - 621
        val r = jalCal(jy)
        val jdn1f = g2d(gy, 3, r.march)
        var k = (jdn - jdn1f).toInt()
        if (k >= 0) {
            if (k <= 185) {
                return JalaliDate(jy, 1 + k / 31, k % 31 + 1)
            }
            k -= 186
        } else {
            jy -= 1
            k += 179
            if (r.leap == 1) k += 1
        }
        return JalaliDate(jy, 7 + k / 30, k % 30 + 1)
    }
}

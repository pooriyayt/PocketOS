package app.pocketos.domain

import app.pocketos.core.money.MoneyFormatter
import app.pocketos.core.time.JalaliCalendar
import app.pocketos.domain.model.BillingCycle
import app.pocketos.domain.model.BillingUnit
import app.pocketos.domain.model.Money
import app.pocketos.domain.model.Subscription
import app.pocketos.domain.recurrence.BillingCalculator
import app.pocketos.domain.smart.SmartDefaults
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Instant
import java.time.LocalDate
import java.util.Locale

class BillingAndMoneyTest {

    private fun d(s: String) = LocalDate.parse(s)

    private fun sub(next: String, start: String? = null, cycle: BillingCycle = BillingCycle.MONTHLY) = Subscription(
        id = "x", name = "Test", amount = Money(1500, "USD"), billing = cycle,
        startDate = start?.let(::d), nextRenewal = d(next), createdAt = Instant.EPOCH, updatedAt = Instant.EPOCH,
    )

    @Test
    fun rollForwardKeepsAnchorDay() {
        val s = sub(next = "2026-01-31", start = "2025-10-31")
        assertEquals(d("2026-03-31"), BillingCalculator.rolledForward(s, today = d("2026-03-05")))
        assertEquals(d("2026-02-28"), BillingCalculator.rolledForward(s, today = d("2026-02-01")))
    }

    @Test
    fun startDateOutsideTheSeriesIsNotUsedAsAnchor() {
        // Started on the 5th but billed on the 15th: renewals stay on the 15th.
        val s = sub(next = "2026-01-15", start = "2025-11-05")
        assertEquals(d("2026-03-15"), BillingCalculator.rolledForward(s, today = d("2026-03-10")))
    }

    @Test
    fun rollForwardDoesNothingForFutureDates() {
        assertEquals(d("2026-05-01"), BillingCalculator.rolledForward(sub("2026-05-01"), d("2026-03-05")))
    }

    @Test
    fun every30DaysSeries() {
        val s = sub(next = "2026-03-12", cycle = BillingCycle(BillingUnit.DAY, 30))
        assertEquals(listOf(d("2026-03-12"), d("2026-04-11"), d("2026-05-11")), BillingCalculator.renewalsBetween(s, d("2026-03-01"), d("2026-05-31")))
    }

    @Test
    fun monthlyEstimates() {
        assertEquals(1500.0, BillingCalculator.monthlyEstimate(1500, BillingCycle.MONTHLY), 0.001)
        assertEquals(100.0, BillingCalculator.monthlyEstimate(1200, BillingCycle.YEARLY), 0.001)
        assertEquals(456553.125, BillingCalculator.monthlyEstimate(450000, BillingCycle(BillingUnit.DAY, 30)), 0.001)
    }

    @Test
    fun smartReminderOffsets() {
        val today = d("2026-03-10")
        assertEquals(listOf(1), SmartDefaults.subscriptionReminderOffsets(BillingCycle.MONTHLY, d("2026-03-20"), today))
        assertEquals(listOf(7, 1), SmartDefaults.subscriptionReminderOffsets(BillingCycle.YEARLY, d("2026-11-20"), today))
        // Renews tomorrow: a 7-day reminder is impossible, keep "1 day before".
        assertEquals(listOf(1), SmartDefaults.subscriptionReminderOffsets(BillingCycle.YEARLY, d("2026-03-11"), today))
        assertEquals(listOf(0), SmartDefaults.subscriptionReminderOffsets(BillingCycle.MONTHLY, today, today))
    }

    @Test
    fun preferredBillingNeedsConsistentHistory() {
        assertNull(SmartDefaults.preferredBilling(listOf(BillingCycle.MONTHLY, BillingCycle.MONTHLY)))
        assertEquals(BillingCycle.MONTHLY, SmartDefaults.preferredBilling(listOf(BillingCycle.MONTHLY, BillingCycle.MONTHLY, BillingCycle.YEARLY, BillingCycle.MONTHLY)))
        assertNull(SmartDefaults.preferredBilling(listOf(BillingCycle.MONTHLY, BillingCycle.YEARLY, BillingCycle.WEEKLY)))
    }

    @Test
    fun moneyParsing() {
        assertEquals(1599L, MoneyFormatter.parse("15.99", "USD"))
        assertEquals(1500L, MoneyFormatter.parse("15", "USD"))
        assertEquals(123450L, MoneyFormatter.parse("1,234.50", "USD"))
        assertEquals(123450L, MoneyFormatter.parse("1.234,50", "EUR"))
        assertEquals(450000L, MoneyFormatter.parse("۴۵۰٬۰۰۰", "IRT"))
        assertEquals(450000L, MoneyFormatter.parse("450.000", "IRT"))
        assertNull(MoneyFormatter.parse("abc", "USD"))
        assertNull(MoneyFormatter.parse("-5", "USD"))
    }

    @Test
    fun moneyFormattingKeepsCurrency() {
        assertEquals("$15.99", MoneyFormatter.format(1599, "USD", Locale.US))
        assertEquals("450,000 Toman", MoneyFormatter.format(450000, "IRT", Locale.US))
        assertTrue(MoneyFormatter.format(450000, "IRT", Locale.forLanguageTag("fa")).endsWith("تومان"))
        assertFalse(MoneyFormatter.format(1500, "EUR", Locale.US).contains("$"))
    }

    @Test
    fun jalaliConversion() {
        assertEquals(d("2024-03-20"), JalaliCalendar.toGregorian(1403, 1, 1))
        assertEquals(d("2025-03-21"), JalaliCalendar.toGregorian(1404, 1, 1))
        assertEquals(JalaliCalendar.JalaliDate(1405, 7, 10), JalaliCalendar.fromGregorian(d("2026-10-02")))
        assertTrue(JalaliCalendar.isLeapYear(1403))
        assertEquals(30, JalaliCalendar.monthLength(1403, 12))
        assertEquals(29, JalaliCalendar.monthLength(1404, 12))
        // Round trip over two years.
        var date = d("2025-01-01")
        repeat(730) {
            val j = JalaliCalendar.fromGregorian(date)
            assertEquals(date, JalaliCalendar.toGregorian(j.year, j.month, j.day))
            date = date.plusDays(1)
        }
    }
}

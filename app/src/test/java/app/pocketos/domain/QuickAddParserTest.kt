package app.pocketos.domain

import app.pocketos.domain.catalog.ServiceMatcher
import app.pocketos.domain.model.BillingCycle
import app.pocketos.domain.model.BillingUnit
import app.pocketos.domain.model.Frequency
import app.pocketos.domain.model.Money
import app.pocketos.domain.model.Priority
import app.pocketos.domain.parser.QuickAddParser
import app.pocketos.domain.parser.QuickAddType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime

class QuickAddParserTest {

    private val parser = QuickAddParser(ServiceMatcher(TestCatalog.catalog), defaultCurrency = "USD")

    // Friday 2 October 2026, 10:00
    private val now = LocalDateTime.of(2026, 10, 2, 10, 0)

    @Test
    fun netflixFifteenDollarsEveryMonth() {
        val r = parser.parse("Netflix 15 dollars every month", now)
        assertEquals(QuickAddType.SUBSCRIPTION, r.type)
        assertEquals("Netflix", r.title)
        assertEquals("netflix", r.service?.id)
        assertEquals(Money(1500, "USD"), r.amount)
        assertFalse(r.currencyAssumed)
        assertEquals(BillingCycle.MONTHLY, r.billing)
        assertFalse(r.billingAssumed)
        assertEquals("entertainment", r.category)
        // No date given: assumes the next period and says so.
        assertEquals(LocalDate.of(2026, 11, 2), r.date)
        assertTrue(r.dateAssumed)
        assertEquals(listOf(1), r.reminderOffsets)
    }

    @Test
    fun internetInTomanEvery30Days() {
        val r = parser.parse("Internet 450000 toman every 30 days", now)
        assertEquals(QuickAddType.SUBSCRIPTION, r.type)
        assertEquals(Money(450000, "IRT"), r.amount)
        assertEquals(BillingCycle(BillingUnit.DAY, 30), r.billing)
        assertEquals("utilities", r.category)
        assertEquals("internet", r.service?.id)
        assertEquals("Internet", r.title)
    }

    @Test
    fun renewDomainEveryYearOnNovember20() {
        val r = parser.parse("Renew my domain every year on November 20", now)
        assertEquals(QuickAddType.SUBSCRIPTION, r.type)
        assertEquals(BillingCycle.YEARLY, r.billing)
        assertEquals(LocalDate.of(2026, 11, 20), r.date)
        assertFalse(r.dateAssumed)
        assertEquals("domains", r.category)
        assertEquals("Domain renewal", r.title)
        assertNull(r.amount)
        assertEquals(listOf(7, 1), r.reminderOffsets)
    }

    @Test
    fun callMomTomorrowAt7pm() {
        val r = parser.parse("Call Mom tomorrow at 7 PM", now)
        assertEquals(QuickAddType.REMINDER, r.type)
        assertEquals("Call Mom", r.title)
        assertEquals(LocalDate.of(2026, 10, 3), r.date)
        assertEquals(LocalTime.of(19, 0), r.time)
        assertFalse(r.timeAssumed)
        assertEquals("personal", r.category)
    }

    @Test
    fun payHostingOnOctober15() {
        val r = parser.parse("Pay hosting on October 15", now)
        assertEquals(QuickAddType.REMINDER, r.type)
        assertEquals("Pay hosting", r.title)
        assertEquals(LocalDate.of(2026, 10, 15), r.date)
        assertEquals(LocalTime.of(9, 0), r.time)
        assertTrue(r.timeAssumed)
        assertEquals("finance", r.category)
    }

    @Test
    fun symbolAmountsAndAliases() {
        val r = parser.parse("ChatGPT Plus $20/month", now)
        assertEquals(QuickAddType.SUBSCRIPTION, r.type)
        assertEquals("chatgpt", r.service?.id)
        assertEquals(Money(2000, "USD"), r.amount)
        assertEquals(BillingCycle.MONTHLY, r.billing)
        assertEquals("ChatGPT", r.title)
    }

    @Test
    fun serviceWithAmountButNoCadenceUsesCatalogHint() {
        val r = parser.parse("Spotify 10.99 EUR", now)
        assertEquals(QuickAddType.SUBSCRIPTION, r.type)
        assertEquals(Money(1099, "EUR"), r.amount)
        assertEquals(BillingCycle.MONTHLY, r.billing)
        assertTrue(r.billingAssumed)
    }

    @Test
    fun bareNumberIsAssumedDefaultCurrency() {
        val r = parser.parse("Spotify 9.99 monthly", now)
        assertEquals(Money(999, "USD"), r.amount)
        assertTrue(r.currencyAssumed)
    }

    @Test
    fun taskWithoutDate() {
        val r = parser.parse("Buy milk", now)
        assertEquals(QuickAddType.TASK, r.type)
        assertEquals("Buy milk", r.title)
        assertNull(r.date)
        assertEquals("shopping", r.category)
    }

    @Test
    fun remindMePrefixAndWeekdays() {
        val r = parser.parse("remind me to take vitamins every monday and wednesday at 8am", now)
        assertEquals(QuickAddType.REMINDER, r.type)
        assertEquals("Take vitamins", r.title)
        assertEquals(Frequency.WEEKLY, r.recurrence?.frequency)
        assertEquals(setOf(DayOfWeek.MONDAY, DayOfWeek.WEDNESDAY), r.recurrence?.byDays)
        assertEquals(LocalTime.of(8, 0), r.time)
        assertEquals(LocalDate.of(2026, 10, 5), r.date) // next Monday
        assertEquals("health", r.category)
    }

    @Test
    fun relativeDatesAndUrgency() {
        val r = parser.parse("Submit tax report in 3 days urgent", now)
        assertEquals(LocalDate.of(2026, 10, 5), r.date)
        assertEquals(Priority.HIGH, r.priority)
        assertEquals("Submit tax report", r.title)
        val t = parser.parse("Dentist next friday at 3:30pm", now)
        assertEquals(LocalDate.of(2026, 10, 9), t.date)
        assertEquals(LocalTime.of(15, 30), t.time)
        assertEquals("Dentist", t.title)
    }

    @Test
    fun timeOnlyPicksTodayOrTomorrow() {
        assertEquals(LocalDate.of(2026, 10, 2), parser.parse("Standup at 11:30", now).date)
        assertEquals(LocalDate.of(2026, 10, 3), parser.parse("Water plants at 8am", now).date)
    }

    @Test
    fun persianReminder() {
        val r = parser.parse("فردا ساعت ۷ عصر به مامان زنگ بزن", now)
        assertEquals(QuickAddType.REMINDER, r.type)
        assertEquals(LocalDate.of(2026, 10, 3), r.date)
        assertEquals(LocalTime.of(19, 0), r.time)
        assertEquals("به مامان زنگ بزن", r.title)
        assertEquals("personal", r.category)
    }

    @Test
    fun persianSubscriptionWithThousandMultiplier() {
        val r = parser.parse("اینترنت ۴۵۰ هزار تومان هر ۳۰ روز", now)
        assertEquals(QuickAddType.SUBSCRIPTION, r.type)
        assertEquals(Money(450000, "IRT"), r.amount)
        assertEquals(BillingCycle(BillingUnit.DAY, 30), r.billing)
        assertEquals("اینترنت", r.title)
        assertEquals("utilities", r.category)
    }

    @Test
    fun persianSolarHijriDate() {
        // 20 Aban 1405 = 11 November 2026
        val r = parser.parse("تمدید دامنه ۲۰ آبان هر سال", now)
        assertEquals(QuickAddType.SUBSCRIPTION, r.type)
        assertEquals(LocalDate.of(2026, 11, 11), r.date)
        assertEquals(BillingCycle.YEARLY, r.billing)
    }

    @Test
    fun pastMonthDayRollsToNextYear() {
        val r = parser.parse("Car insurance renewal March 3 yearly", now)
        assertEquals(LocalDate.of(2027, 3, 3), r.date)
        assertEquals("finance", r.category)
    }

    @Test
    fun emptyInput() {
        val r = parser.parse("   ", now)
        assertTrue(r.isEmpty)
        assertEquals(QuickAddType.TASK, r.type)
    }
}

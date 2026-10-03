package app.pocketos.domain

import app.pocketos.core.time.CalendarKind
import app.pocketos.core.time.CalendarMath
import app.pocketos.domain.catalog.ServiceMatcher
import app.pocketos.domain.finance.Debt
import app.pocketos.domain.finance.DebtDirection
import app.pocketos.domain.finance.InstallmentPlan
import app.pocketos.domain.finance.ObligationCalculator
import app.pocketos.domain.model.Money
import app.pocketos.domain.parser.QuickAddParser
import app.pocketos.domain.parser.QuickAddType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime

class MoneyParsingAndObligationsTest {

    private val now = LocalDateTime.of(2026, 10, 3, 12, 0)
    private val fa = QuickAddParser(ServiceMatcher(TestCatalog.catalog), defaultCurrency = "IRT")
    private val en = QuickAddParser(ServiceMatcher(TestCatalog.catalog), defaultCurrency = "USD")

    @Test
    fun `colloquial toman expense`() {
        val r = fa.parse("۱۵۰ تومن ساندویچ", now)
        assertEquals(QuickAddType.EXPENSE, r.type)
        assertEquals(Money(150_000, "IRT"), r.amount)
        assertTrue(r.amountScaled)
        assertEquals("food", r.category)
        assertEquals("ساندویچ", r.title)
        assertEquals(now.toLocalDate(), r.date)
    }

    @Test
    fun `explicit thousands are not scaled again`() {
        val r = fa.parse("اسنپ ۸۰ هزار تومان", now)
        assertEquals(QuickAddType.EXPENSE, r.type)
        assertEquals(Money(80_000, "IRT"), r.amount)
        assertEquals("transport", r.category)
    }

    @Test
    fun `salary is income with bare amount in default currency`() {
        val r = fa.parse("حقوق ۲۵ میلیون", now)
        assertEquals(QuickAddType.INCOME, r.type)
        assertEquals(Money(25_000_000, "IRT"), r.amount)
        assertEquals("salary", r.category)
    }

    @Test
    fun `english lunch expense`() {
        val r = en.parse("lunch 12$", now)
        assertEquals(QuickAddType.EXPENSE, r.type)
        assertEquals(Money(1200, "USD"), r.amount)
        assertEquals("food", r.category)
    }

    @Test
    fun `reminders with amounts stay reminders`() {
        val r = en.parse("remind me to buy milk 5 dollars tomorrow", now)
        assertEquals(QuickAddType.REMINDER, r.type)
    }

    @Test
    fun `recurring payments stay subscriptions`() {
        val r = en.parse("Netflix 15 dollars every month", now)
        assertEquals(QuickAddType.SUBSCRIPTION, r.type)
    }

    @Test
    fun `jalali months keep the day of month`() {
        // 5 Mehr -> 5 Aban; 31 Shahrivar clamps to 30 in the 30-day months and returns to 31 in Farvardin.
        val fifthMehr = CalendarMath.toDate(CalendarKind.SOLAR_HIJRI, 1405, 7, 5)
        val next = CalendarMath.plusMonths(fifthMehr, 1, CalendarKind.SOLAR_HIJRI)
        assertEquals(CalendarMath.toDate(CalendarKind.SOLAR_HIJRI, 1405, 8, 5), next)
        val lastShahrivar = CalendarMath.toDate(CalendarKind.SOLAR_HIJRI, 1405, 6, 31)
        assertEquals(CalendarMath.toDate(CalendarKind.SOLAR_HIJRI, 1405, 7, 30), CalendarMath.plusMonths(lastShahrivar, 1, CalendarKind.SOLAR_HIJRI))
        assertEquals(CalendarMath.toDate(CalendarKind.SOLAR_HIJRI, 1405, 8, 30), CalendarMath.plusMonths(lastShahrivar, 2, CalendarKind.SOLAR_HIJRI))
        assertEquals(CalendarMath.toDate(CalendarKind.SOLAR_HIJRI, 1406, 1, 31), CalendarMath.plusMonths(lastShahrivar, 7, CalendarKind.SOLAR_HIJRI))
    }

    @Test
    fun `lunar hijri round trip`() {
        val d = LocalDate.of(2026, 10, 3)
        val h = CalendarMath.fromDate(d, CalendarKind.LUNAR_HIJRI)
        assertEquals(d, CalendarMath.toDate(CalendarKind.LUNAR_HIJRI, h.year, h.month, h.day))
        assertTrue(CalendarMath.monthLength(CalendarKind.LUNAR_HIJRI, h.year, h.month) in 29..30)
    }

    private fun plan(paid: Int, total: Int = 12) = InstallmentPlan(
        "p", "Car", null, 5_000_000, "IRT", total, paid,
        CalendarMath.toDate(CalendarKind.SOLAR_HIJRI, 1405, 1, 10), 1, CalendarKind.SOLAR_HIJRI, 3, null, null, Instant.EPOCH, Instant.EPOCH,
    )

    @Test
    fun `installment schedule follows the jalali calendar`() {
        val p = plan(paid = 6)
        assertEquals(CalendarMath.toDate(CalendarKind.SOLAR_HIJRI, 1405, 7, 10), p.nextDue)
        assertEquals(CalendarMath.toDate(CalendarKind.SOLAR_HIJRI, 1405, 12, 10), p.lastDue)
        assertEquals(6 * 5_000_000L, p.remainingMinor)
        assertNull(plan(paid = 12).nextDue)
        val mehr = app.pocketos.domain.finance.MonthPeriod(1405, 7, CalendarKind.SOLAR_HIJRI)
        assertEquals(mapOf("IRT" to 5_000_000L), ObligationCalculator.dueBetween(listOf(p), mehr.start, mehr.end))
    }

    @Test
    fun `debt summary nets both directions`() {
        fun debt(dir: DebtDirection, amount: Long, settled: Long) =
            Debt("d$amount", "Ali", dir, amount, "IRT", settled, LocalDate.of(2026, 10, 1), null, null, Instant.EPOCH, Instant.EPOCH)
        val s = ObligationCalculator.debtSummary(listOf(
            debt(DebtDirection.I_OWE, 1_000_000, 400_000),
            debt(DebtDirection.OWED_TO_ME, 3_000_000, 0),
            debt(DebtDirection.OWED_TO_ME, 500_000, 500_000), // settled: excluded
        )).single()
        assertEquals(600_000, s.iOweMinor)
        assertEquals(3_000_000, s.owedToMeMinor)
        assertEquals(2_400_000, s.netMinor)
    }

    @Test
    fun `check summary calculates pending issued and received checks`() {
        fun check(id: String, dir: app.pocketos.domain.finance.CheckDirection, status: app.pocketos.domain.finance.CheckStatus, amount: Long) =
            app.pocketos.domain.finance.CheckItem(
                id = id,
                title = "Check $id",
                counterparty = "Counterparty",
                direction = dir,
                amountMinor = amount,
                currency = "USD",
                sayadNumber = null,
                bankName = "Chase",
                dueDate = LocalDate.of(2026, 10, 15),
                issueDate = LocalDate.of(2026, 10, 1),
                status = status,
                reminderDays = 3,
                note = null,
                walletId = null,
                createdAt = Instant.EPOCH,
                updatedAt = Instant.EPOCH,
            )

        val list = listOf(
            check("c1", app.pocketos.domain.finance.CheckDirection.ISSUED, app.pocketos.domain.finance.CheckStatus.PENDING, 1500),
            check("c2", app.pocketos.domain.finance.CheckDirection.ISSUED, app.pocketos.domain.finance.CheckStatus.CLEARED, 500),
            check("c3", app.pocketos.domain.finance.CheckDirection.RECEIVED, app.pocketos.domain.finance.CheckStatus.PENDING, 2200),
        )

        val s = ObligationCalculator.checkSummary(list).single()
        assertEquals(1500, s.pendingIssuedMinor)
        assertEquals(2200, s.pendingReceivedMinor)
        assertEquals(500, s.clearedMinor)
        assertEquals(700, s.netPendingMinor)
        assertEquals(2, ObligationCalculator.upcomingChecks(list).size)
    }
}


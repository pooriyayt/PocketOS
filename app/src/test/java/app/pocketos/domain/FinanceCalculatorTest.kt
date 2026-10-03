package app.pocketos.domain

import app.pocketos.domain.finance.FinanceCalculator
import app.pocketos.domain.finance.MonthPeriod
import app.pocketos.domain.finance.Transaction
import app.pocketos.domain.finance.TxType
import app.pocketos.domain.finance.Wallet
import app.pocketos.domain.finance.WalletType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Instant
import java.time.LocalDate

class FinanceCalculatorTest {

    private val t0 = Instant.EPOCH
    private val cash = Wallet("cash", "Cash", WalletType.CASH, "IRT", 1_000_000, null, 0, false, t0, t0)
    private val card = Wallet("card", "Card", WalletType.CARD, "IRT", 5_000_000, null, 1, false, t0, t0)
    private val usd = Wallet("usd", "Dollars", WalletType.SAVINGS, "USD", 10_000, null, 2, false, t0, t0)

    private fun tx(id: String, type: TxType, amount: Long, wallet: String, date: LocalDate, to: String? = null, cat: String = "food", cur: String = "IRT") =
        Transaction(id, type, amount, cur, wallet, to, cat, null, date, t0, t0)

    private val d = LocalDate.of(2026, 10, 3)

    @Test
    fun `balances follow income, expenses and transfers`() {
        val txs = listOf(
            tx("1", TxType.EXPENSE, 200_000, "cash", d),
            tx("2", TxType.INCOME, 3_000_000, "card", d, cat = "salary"),
            tx("3", TxType.TRANSFER, 500_000, "card", d, to = "cash", cat = "transfer"),
        )
        assertEquals(1_000_000 - 200_000 + 500_000, FinanceCalculator.balance(cash, txs))
        assertEquals(5_000_000 + 3_000_000 - 500_000, FinanceCalculator.balance(card, txs))
        val worth = FinanceCalculator.netWorth(listOf(cash, card, usd), txs)
        assertEquals(1_300_000L + 7_500_000L, worth["IRT"])
        assertEquals(10_000L, worth["USD"])
    }

    @Test
    fun `monthly totals exclude transfers and other months`() {
        val period = MonthPeriod.of(d, jalali = false)
        val txs = listOf(
            tx("1", TxType.EXPENSE, 200_000, "cash", d),
            tx("2", TxType.EXPENSE, 100_000, "cash", d.minusMonths(1)),
            tx("3", TxType.INCOME, 900_000, "card", d, cat = "salary"),
            tx("4", TxType.TRANSFER, 500_000, "card", d, to = "cash", cat = "transfer"),
        )
        val totals = FinanceCalculator.totals(txs, period).single()
        assertEquals(900_000, totals.incomeMinor)
        assertEquals(200_000, totals.expenseMinor)
        assertEquals(700_000, totals.netMinor)
    }

    @Test
    fun `spending by category is sorted with shares`() {
        val period = MonthPeriod.of(d, jalali = false)
        val txs = listOf(
            tx("1", TxType.EXPENSE, 300, "cash", d, cat = "food"),
            tx("2", TxType.EXPENSE, 100, "cash", d, cat = "transport"),
            tx("3", TxType.EXPENSE, 100, "cash", d, cat = "food"),
        )
        val shares = FinanceCalculator.spendingByCategory(txs, period, "IRT")
        assertEquals(listOf("food", "transport"), shares.map { it.category })
        assertEquals(0.8f, shares[0].share, 0.0001f)
    }

    @Test
    fun `jalali month covers mehr 1405`() {
        val mehr = MonthPeriod.of(d, jalali = true)
        assertEquals(1405, mehr.year)
        assertEquals(7, mehr.month)
        assertEquals(LocalDate.of(2026, 9, 23), mehr.start)
        assertEquals(LocalDate.of(2026, 10, 22), mehr.end)
        assertTrue(d in mehr)
        assertFalse(LocalDate.of(2026, 10, 23) in mehr)
        assertEquals(MonthPeriod(1406, 1, true), MonthPeriod(1405, 12, true).plus(1))
        assertEquals(MonthPeriod(1405, 12, true), MonthPeriod(1406, 1, true).plus(-1))
    }

    @Test
    fun `daily spending has one slot per day`() {
        val period = MonthPeriod.of(d, jalali = false)
        val series = FinanceCalculator.dailySpending(listOf(tx("1", TxType.EXPENSE, 50, "cash", d)), period, "IRT")
        assertEquals(31, series.size)
        assertEquals(50L, series[2])
    }
}

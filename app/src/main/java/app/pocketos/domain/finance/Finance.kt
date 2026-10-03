package app.pocketos.domain.finance

import app.pocketos.core.time.CalendarKind
import app.pocketos.core.time.CalendarMath
import java.time.Instant
import java.time.LocalDate

enum class WalletType(val wire: String) {
    CASH("cash"), BANK("bank"), CARD("card"), SAVINGS("savings");

    companion object {
        fun fromWire(value: String?): WalletType = entries.firstOrNull { it.wire == value } ?: CASH
    }
}

/** A place money lives: cash in hand, a bank account, a card, savings. */
data class Wallet(
    val id: String,
    val name: String,
    val type: WalletType,
    val currency: String,
    val openingBalanceMinor: Long,
    val color: String?,
    val sortOrder: Int,
    val archived: Boolean,
    val createdAt: Instant,
    val updatedAt: Instant,
)

enum class TxType(val wire: String) {
    EXPENSE("expense"), INCOME("income"), TRANSFER("transfer");

    companion object {
        fun fromWire(value: String?): TxType = entries.firstOrNull { it.wire == value } ?: EXPENSE
    }
}

/**
 * One money movement. [amountMinor] is always positive; [type] gives the
 * direction. Transfers move money from [walletId] to [toWalletId] (both in
 * the same currency).
 */
data class Transaction(
    val id: String,
    val type: TxType,
    val amountMinor: Long,
    val currency: String,
    val walletId: String,
    val toWalletId: String?,
    val category: String,
    val note: String?,
    val date: LocalDate,
    val createdAt: Instant,
    val updatedAt: Instant,
)

/**
 * Built-in money category; the UI maps [id] to an icon and a name.
 * [hidden] categories are written by the app (debts) and not offered in the
 * picker; [isSpending] false keeps them out of income/spending reports while
 * they still move wallet balances.
 */
data class FinanceCategory(val id: String, val type: TxType, val color: Long, val hidden: Boolean = false, val isSpending: Boolean = true)

object FinanceCategories {
    const val TRANSFER = "transfer"
    const val INSTALLMENTS = "installments"
    const val LENT = "lent"
    const val BORROWED = "borrowed"
    const val DEBT_PAID = "debt_paid"
    const val DEBT_RECEIVED = "debt_received"

    val expense = listOf(
        FinanceCategory("food", TxType.EXPENSE, 0xFFF97316),
        FinanceCategory("groceries", TxType.EXPENSE, 0xFF22C55E),
        FinanceCategory("transport", TxType.EXPENSE, 0xFF3B82F6),
        FinanceCategory("shopping", TxType.EXPENSE, 0xFFEC4899),
        FinanceCategory("bills", TxType.EXPENSE, 0xFFEAB308),
        FinanceCategory("housing", TxType.EXPENSE, 0xFF8B5CF6),
        FinanceCategory("health", TxType.EXPENSE, 0xFFEF4444),
        FinanceCategory("fun", TxType.EXPENSE, 0xFFA855F7),
        FinanceCategory("education", TxType.EXPENSE, 0xFF0EA5E9),
        FinanceCategory("travel", TxType.EXPENSE, 0xFF14B8A6),
        FinanceCategory("gifts", TxType.EXPENSE, 0xFFF43F5E),
        FinanceCategory("subscriptions", TxType.EXPENSE, 0xFF6366F1),
        FinanceCategory("installments", TxType.EXPENSE, 0xFF0EA5E9),
        FinanceCategory("other_expense", TxType.EXPENSE, 0xFF94A3B8),
        FinanceCategory(LENT, TxType.EXPENSE, 0xFF14B8A6, hidden = true, isSpending = false),
        FinanceCategory(DEBT_PAID, TxType.EXPENSE, 0xFF14B8A6, hidden = true, isSpending = false),
    )

    val income = listOf(
        FinanceCategory("salary", TxType.INCOME, 0xFF22C55E),
        FinanceCategory("freelance", TxType.INCOME, 0xFF06B6D4),
        FinanceCategory("business", TxType.INCOME, 0xFF8B5CF6),
        FinanceCategory("investment", TxType.INCOME, 0xFFF59E0B),
        FinanceCategory("gift_income", TxType.INCOME, 0xFFEC4899),
        FinanceCategory("refund", TxType.INCOME, 0xFF3B82F6),
        FinanceCategory("other_income", TxType.INCOME, 0xFF94A3B8),
        FinanceCategory(BORROWED, TxType.INCOME, 0xFF14B8A6, hidden = true, isSpending = false),
        FinanceCategory(DEBT_RECEIVED, TxType.INCOME, 0xFF14B8A6, hidden = true, isSpending = false),
    )

    fun forType(type: TxType): List<FinanceCategory> = when (type) {
        TxType.EXPENSE -> expense.filterNot { it.hidden }
        TxType.INCOME -> income.filterNot { it.hidden }
        TxType.TRANSFER -> emptyList()
    }

    fun find(id: String): FinanceCategory? = (expense + income).firstOrNull { it.id == id }

    /** Does a transaction in [id] count as real income/spending in reports? */
    fun isSpending(id: String): Boolean = find(id)?.isSpending ?: true

    fun defaultFor(type: TxType): String = when (type) {
        TxType.EXPENSE -> "food"
        TxType.INCOME -> "salary"
        TxType.TRANSFER -> TRANSFER
    }
}

/**
 * A calendar month in the user's calendar (Gregorian, Solar Hijri or
 * lunar Hijri), with its inclusive date range.
 */
data class MonthPeriod(val year: Int, val month: Int, val calendar: CalendarKind) {
    val start: LocalDate
        get() = CalendarMath.toDate(calendar, year, month, 1)

    val end: LocalDate
        get() = CalendarMath.toDate(calendar, year, month, CalendarMath.monthLength(calendar, year, month))

    operator fun contains(date: LocalDate): Boolean = !date.isBefore(start) && !date.isAfter(end)

    fun plus(months: Int): MonthPeriod {
        val index = year * 12 + (month - 1) + months
        return copy(year = Math.floorDiv(index, 12), month = Math.floorMod(index, 12) + 1)
    }

    companion object {
        fun of(date: LocalDate, calendar: CalendarKind): MonthPeriod =
            CalendarMath.fromDate(date, calendar).let { MonthPeriod(it.year, it.month, calendar) }
    }
}

data class CurrencyTotals(val currency: String, val incomeMinor: Long, val expenseMinor: Long) {
    val netMinor: Long get() = incomeMinor - expenseMinor
}

data class CategoryShare(val category: String, val amountMinor: Long, val share: Float)

data class DayGroup(val date: LocalDate, val transactions: List<Transaction>)

object FinanceCalculator {

    /** Current balance: opening balance plus everything that moved in or out. */
    fun balance(wallet: Wallet, transactions: List<Transaction>): Long {
        var total = wallet.openingBalanceMinor
        for (t in transactions) {
            when (t.type) {
                TxType.INCOME -> if (t.walletId == wallet.id) total += t.amountMinor
                TxType.EXPENSE -> if (t.walletId == wallet.id) total -= t.amountMinor
                TxType.TRANSFER -> {
                    if (t.walletId == wallet.id) total -= t.amountMinor
                    if (t.toWalletId == wallet.id) total += t.amountMinor
                }
            }
        }
        return total
    }

    /** Sum of all (non-archived) wallet balances, per currency. */
    fun netWorth(wallets: List<Wallet>, transactions: List<Transaction>): Map<String, Long> =
        wallets.filterNot { it.archived }.groupBy { it.currency }
            .mapValues { (_, ws) -> ws.sumOf { balance(it, transactions) } }

    /** Income and spending inside [period], per currency (transfers excluded). */
    fun totals(transactions: List<Transaction>, period: MonthPeriod): List<CurrencyTotals> =
        transactions.filter { it.date in period && it.type != TxType.TRANSFER && FinanceCategories.isSpending(it.category) }
            .groupBy { it.currency }
            .map { (currency, list) ->
                CurrencyTotals(
                    currency,
                    incomeMinor = list.filter { it.type == TxType.INCOME }.sumOf { it.amountMinor },
                    expenseMinor = list.filter { it.type == TxType.EXPENSE }.sumOf { it.amountMinor },
                )
            }
            .sortedByDescending { it.expenseMinor + it.incomeMinor }

    /** Spending by category in [period] for one [currency], largest first. */
    fun spendingByCategory(transactions: List<Transaction>, period: MonthPeriod, currency: String): List<CategoryShare> {
        val spent = transactions.filter { it.type == TxType.EXPENSE && it.currency == currency && it.date in period && FinanceCategories.isSpending(it.category) }
        val total = spent.sumOf { it.amountMinor }.takeIf { it > 0 } ?: return emptyList()
        return spent.groupBy { it.category }
            .map { (cat, list) -> list.sumOf { it.amountMinor }.let { CategoryShare(cat, it, it.toFloat() / total) } }
            .sortedByDescending { it.amountMinor }
    }

    /** Newest day first; within a day, newest entry first. */
    fun groupByDay(transactions: List<Transaction>): List<DayGroup> =
        transactions.sortedWith(compareByDescending<Transaction> { it.date }.thenByDescending { it.createdAt })
            .groupBy { it.date }
            .map { (date, list) -> DayGroup(date, list) }

    /** Daily spending for the period (one value per day), for the trend sparkline. */
    fun dailySpending(transactions: List<Transaction>, period: MonthPeriod, currency: String): List<Long> {
        val start = period.start
        val days = (period.end.toEpochDay() - start.toEpochDay() + 1).toInt()
        val out = LongArray(days)
        transactions.filter { it.type == TxType.EXPENSE && it.currency == currency && it.date in period && FinanceCategories.isSpending(it.category) }
            .forEach { out[(it.date.toEpochDay() - start.toEpochDay()).toInt()] += it.amountMinor }
        return out.toList()
    }
}

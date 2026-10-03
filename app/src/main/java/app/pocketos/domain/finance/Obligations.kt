package app.pocketos.domain.finance

import app.pocketos.core.time.CalendarKind
import app.pocketos.core.time.CalendarMath
import java.time.Instant
import java.time.LocalDate
import java.time.temporal.ChronoUnit

/**
 * A loan or purchase paid in equal installments: [totalCount] payments of
 * [amountMinor], the first on [firstDue], then every [intervalMonths] months
 * on the same day of the month in [calendar] (so a Solar Hijri plan due on
 * the 5th stays on the 5th of each Jalali month).
 */
data class InstallmentPlan(
    val id: String,
    val title: String,
    val lender: String?,
    val amountMinor: Long,
    val currency: String,
    val totalCount: Int,
    val paidCount: Int,
    val firstDue: LocalDate,
    val intervalMonths: Int,
    val calendar: CalendarKind,
    val reminderDays: Int,
    val walletId: String?,
    val note: String?,
    val createdAt: Instant,
    val updatedAt: Instant,
) {
    val remainingCount: Int get() = (totalCount - paidCount).coerceAtLeast(0)
    val isFinished: Boolean get() = paidCount >= totalCount
    val remainingMinor: Long get() = amountMinor * remainingCount
    val progress: Float get() = if (totalCount == 0) 1f else paidCount.toFloat() / totalCount

    /** Due date of installment number [index] (0-based). */
    fun dueDate(index: Int): LocalDate = CalendarMath.plusMonths(firstDue, index * intervalMonths, calendar)

    /** Due date of the next unpaid installment, or null when paid off. */
    val nextDue: LocalDate? get() = if (isFinished) null else dueDate(paidCount)

    val lastDue: LocalDate get() = dueDate((totalCount - 1).coerceAtLeast(0))
}

enum class DebtDirection(val wire: String) {
    /** I borrowed: I have to pay this person back. */
    I_OWE("i_owe"),

    /** I lent: this person has to pay me back. */
    OWED_TO_ME("owed_to_me");

    companion object {
        fun fromWire(value: String?): DebtDirection = entries.firstOrNull { it.wire == value } ?: I_OWE
    }
}

/** Money between the user and another person, settled in one or more repayments. */
data class Debt(
    val id: String,
    val person: String,
    val direction: DebtDirection,
    val amountMinor: Long,
    val currency: String,
    val settledMinor: Long,
    val date: LocalDate,
    val dueDate: LocalDate?,
    val note: String?,
    val createdAt: Instant,
    val updatedAt: Instant,
) {
    val remainingMinor: Long get() = (amountMinor - settledMinor).coerceAtLeast(0)
    val isSettled: Boolean get() = settledMinor >= amountMinor
    val progress: Float get() = if (amountMinor <= 0) 1f else (settledMinor.toFloat() / amountMinor).coerceIn(0f, 1f)
}

data class DebtSummary(val currency: String, val iOweMinor: Long, val owedToMeMinor: Long) {
    val netMinor: Long get() = owedToMeMinor - iOweMinor
}

enum class CheckDirection(val wire: String) {
    /** Issued / payable check (چک پرداختی / صادره). */
    ISSUED("issued"),

    /** Received check (چک دریافتی). */
    RECEIVED("received");

    companion object {
        fun fromWire(value: String?): CheckDirection = entries.firstOrNull { it.wire == value } ?: ISSUED
    }
}

enum class CheckStatus(val wire: String) {
    /** Pending clearance (در انتظار وصول). */
    PENDING("pending"),

    /** Cleared / Paid (پاس شده). */
    CLEARED("cleared"),

    /** Bounced / Returned (برگشت خورده). */
    BOUNCED("bounced");

    companion object {
        fun fromWire(value: String?): CheckStatus = entries.firstOrNull { it.wire == value } ?: PENDING
    }
}

/** Bank check issued or received with Sayad tracking and clearance status. */
data class CheckItem(
    val id: String,
    val title: String,
    val counterparty: String,
    val direction: CheckDirection,
    val amountMinor: Long,
    val currency: String,
    val sayadNumber: String?,
    val bankName: String?,
    val dueDate: LocalDate,
    val issueDate: LocalDate?,
    val status: CheckStatus,
    val reminderDays: Int,
    val note: String?,
    val walletId: String?,
    val createdAt: Instant,
    val updatedAt: Instant,
) {
    val isPending: Boolean get() = status == CheckStatus.PENDING
    val isCleared: Boolean get() = status == CheckStatus.CLEARED
    val isBounced: Boolean get() = status == CheckStatus.BOUNCED
}

data class CheckSummary(
    val currency: String,
    val pendingIssuedMinor: Long,
    val pendingReceivedMinor: Long,
    val clearedMinor: Long,
) {
    val netPendingMinor: Long get() = pendingReceivedMinor - pendingIssuedMinor
}

object ObligationCalculator {

    /** Plans with something left to pay, soonest first. */
    fun upcoming(plans: List<InstallmentPlan>): List<InstallmentPlan> =
        plans.filterNot { it.isFinished }.sortedBy { it.nextDue }

    /** What is still owed on all installment plans, per currency. */
    fun remainingByCurrency(plans: List<InstallmentPlan>): Map<String, Long> =
        plans.filterNot { it.isFinished }.groupBy { it.currency }.mapValues { (_, list) -> list.sumOf { it.remainingMinor } }

    /** Installments falling due within [from]..[to] (inclusive), per currency. */
    fun dueBetween(plans: List<InstallmentPlan>, from: LocalDate, to: LocalDate): Map<String, Long> {
        val out = HashMap<String, Long>()
        for (p in plans) {
            for (i in p.paidCount until p.totalCount) {
                val due = p.dueDate(i)
                if (due.isAfter(to)) break
                if (!due.isBefore(from)) out[p.currency] = (out[p.currency] ?: 0) + p.amountMinor
            }
        }
        return out
    }

    fun daysUntil(date: LocalDate, today: LocalDate): Long = ChronoUnit.DAYS.between(today, date)

    /** Open (unsettled) debts summed per currency, in both directions. */
    fun debtSummary(debts: List<Debt>): List<DebtSummary> =
        debts.filterNot { it.isSettled }.groupBy { it.currency }.map { (currency, list) ->
            DebtSummary(
                currency,
                iOweMinor = list.filter { it.direction == DebtDirection.I_OWE }.sumOf { it.remainingMinor },
                owedToMeMinor = list.filter { it.direction == DebtDirection.OWED_TO_ME }.sumOf { it.remainingMinor },
            )
        }

    /** Pending checks sorted by due date, soonest first. */
    fun upcomingChecks(checks: List<CheckItem>): List<CheckItem> =
        checks.filter { it.isPending }.sortedBy { it.dueDate }

    /** Pending checks summary per currency. */
    fun checkSummary(checks: List<CheckItem>): List<CheckSummary> =
        checks.groupBy { it.currency }.map { (currency, list) ->
            CheckSummary(
                currency = currency,
                pendingIssuedMinor = list.filter { it.isPending && it.direction == CheckDirection.ISSUED }.sumOf { it.amountMinor },
                pendingReceivedMinor = list.filter { it.isPending && it.direction == CheckDirection.RECEIVED }.sumOf { it.amountMinor },
                clearedMinor = list.filter { it.isCleared }.sumOf { it.amountMinor },
            )
        }
}

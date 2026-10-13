package app.pocketos.domain.finance

import app.pocketos.domain.model.Subscription
import app.pocketos.domain.model.SubscriptionStatus
import java.time.LocalDate
import java.time.temporal.ChronoUnit

/**
 * An upcoming financial outgoing commitment within the next N days.
 */
data class CashFlowCommitment(
    val id: String,
    val title: String,
    val sourceName: String,
    val amountMinor: Long,
    val currency: String,
    val dueDate: LocalDate,
    val walletId: String?,
    val type: CommitmentType,
)

enum class CommitmentType {
    INSTALLMENT,
    CHECK,
    SUBSCRIPTION,
    DEBT,
}

/**
 * Shortfall alert indicating an upcoming check or installment linked to a specific
 * wallet where the wallet's current balance is less than the required amount.
 */
data class ShortfallAlert(
    val commitmentTitle: String,
    val walletName: String,
    val requiredMinor: Long,
    val currentBalanceMinor: Long,
    val shortfallMinor: Long,
    val currency: String,
    val dueDate: LocalDate,
    val daysRemaining: Long,
)

data class CashFlowForecast(
    val totalRequiredByCurrency: Map<String, Long>,
    val commitments: List<CashFlowCommitment>,
    val shortfallAlerts: List<ShortfallAlert>,
)

object CashFlowCalculator {

    /**
     * Calculates upcoming commitments and balance shortfalls for the next [windowDays] days.
     */
    fun forecast(
        today: LocalDate,
        windowDays: Long = 30,
        wallets: List<Wallet>,
        transactions: List<Transaction>,
        installments: List<InstallmentPlan>,
        checks: List<CheckItem>,
        debts: List<Debt>,
        subscriptions: List<Subscription>,
    ): CashFlowForecast {
        val endDate = today.plusDays(windowDays)
        val commitments = mutableListOf<CashFlowCommitment>()
        val walletBalances = wallets.associate { it.id to FinanceCalculator.balance(it, transactions) }
        val walletMap = wallets.associateBy { it.id }

        // 1. Installments
        for (plan in installments) {
            if (plan.isFinished) continue
            for (i in plan.paidCount until plan.totalCount) {
                val due = plan.dueDate(i)
                if (due.isAfter(endDate)) break
                if (!due.isBefore(today)) {
                    commitments.add(
                        CashFlowCommitment(
                            id = "${plan.id}_$i",
                            title = plan.title,
                            sourceName = plan.lender ?: plan.title,
                            amountMinor = plan.amountMinor,
                            currency = plan.currency,
                            dueDate = due,
                            walletId = plan.walletId,
                            type = CommitmentType.INSTALLMENT,
                        )
                    )
                }
            }
        }

        // 2. Issued checks (Pending)
        for (check in checks) {
            if (check.isPending && check.direction == CheckDirection.ISSUED) {
                if (!check.dueDate.isBefore(today) && !check.dueDate.isAfter(endDate)) {
                    commitments.add(
                        CashFlowCommitment(
                            id = check.id,
                            title = check.title,
                            sourceName = check.bankName ?: check.counterparty,
                            amountMinor = check.amountMinor,
                            currency = check.currency,
                            dueDate = check.dueDate,
                            walletId = check.walletId,
                            type = CommitmentType.CHECK,
                        )
                    )
                }
            }
        }

        // 3. Debts (I owe, due within window)
        for (debt in debts) {
            if (!debt.isSettled && debt.direction == DebtDirection.I_OWE && debt.dueDate != null) {
                if (!debt.dueDate.isBefore(today) && !debt.dueDate.isAfter(endDate)) {
                    commitments.add(
                        CashFlowCommitment(
                            id = debt.id,
                            title = debt.person,
                            sourceName = debt.person,
                            amountMinor = debt.remainingMinor,
                            currency = debt.currency,
                            dueDate = debt.dueDate,
                            walletId = null,
                            type = CommitmentType.DEBT,
                        )
                    )
                }
            }
        }

        // 4. Subscriptions
        for (sub in subscriptions) {
            if (sub.status == SubscriptionStatus.ACTIVE && sub.amount != null) {
                if (!sub.nextRenewal.isBefore(today) && !sub.nextRenewal.isAfter(endDate)) {
                    commitments.add(
                        CashFlowCommitment(
                            id = sub.id,
                            title = sub.name,
                            sourceName = sub.name,
                            amountMinor = sub.amount.amountMinor,
                            currency = sub.amount.currency,
                            dueDate = sub.nextRenewal,
                            walletId = null,
                            type = CommitmentType.SUBSCRIPTION,
                        )
                    )
                }
            }
        }

        commitments.sortBy { it.dueDate }

        // Sum by currency
        val totalByCurrency = commitments.groupBy { it.currency }
            .mapValues { (_, list) -> list.sumOf { it.amountMinor } }

        // Check for specific wallet shortfalls (for checks and installments linked to a wallet)
        val shortfalls = mutableListOf<ShortfallAlert>()
        for (c in commitments) {
            val wid = c.walletId ?: continue
            val wallet = walletMap[wid] ?: continue
            if (wallet.currency != c.currency) continue
            val currentBal = walletBalances[wid] ?: 0L
            if (currentBal < c.amountMinor) {
                val diff = c.amountMinor - currentBal
                val days = ChronoUnit.DAYS.between(today, c.dueDate).coerceAtLeast(0)
                shortfalls.add(
                    ShortfallAlert(
                        commitmentTitle = c.title,
                        walletName = wallet.name,
                        requiredMinor = c.amountMinor,
                        currentBalanceMinor = currentBal,
                        shortfallMinor = diff,
                        currency = c.currency,
                        dueDate = c.dueDate,
                        daysRemaining = days,
                    )
                )
            }
        }

        return CashFlowForecast(
            totalRequiredByCurrency = totalByCurrency,
            commitments = commitments,
            shortfallAlerts = shortfalls,
        )
    }
}

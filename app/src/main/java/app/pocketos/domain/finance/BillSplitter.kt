package app.pocketos.domain.finance

import java.util.UUID

/**
 * A participant in a split bill / event (e.g. "پوریا", "علی", "رضا").
 */
data class SplitMember(
    val id: String = UUID.randomUUID().toString(),
    val name: String,
)

/**
 * An expense paid by [paidById] on behalf of [participantIds].
 */
data class SplitExpense(
    val id: String = UUID.randomUUID().toString(),
    val title: String,
    val amountMinor: Long,
    val paidById: String,
    val participantIds: List<String>,
)

/**
 * A simplified settlement transfer: [debtorName] owes [creditorName] the [amountMinor].
 */
data class SplitSettlement(
    val debtorId: String,
    val debtorName: String,
    val creditorId: String,
    val creditorName: String,
    val amountMinor: Long,
    val currency: String,
)

object SplitCalculator {

    /**
     * Calculates net balances for each member and finds the minimum number of
     * transactions to settle all debts (Greedy Settlement Algorithm).
     */
    fun calculateSettlement(
        members: List<SplitMember>,
        expenses: List<SplitExpense>,
        currency: String,
    ): List<SplitSettlement> {
        if (members.isEmpty() || expenses.isEmpty()) return emptyList()

        val memberMap = members.associateBy { it.id }
        val netBalance = mutableMapOf<String, Long>()
        for (m in members) {
            netBalance[m.id] = 0L
        }

        for (e in expenses) {
            if (e.participantIds.isEmpty() || e.amountMinor <= 0) continue
            // Payer gets credited
            netBalance[e.paidById] = (netBalance[e.paidById] ?: 0L) + e.amountMinor

            // Each participant gets debited equal share
            val share = e.amountMinor / e.participantIds.size
            var remainder = e.amountMinor % e.participantIds.size

            for (pid in e.participantIds) {
                val memberShare = share + if (remainder > 0) { remainder--; 1L } else 0L
                netBalance[pid] = (netBalance[pid] ?: 0L) - memberShare
            }
        }

        // Debtors (negative balance) and Creditors (positive balance)
        val debtors = mutableListOf<Pair<String, Long>>()
        val creditors = mutableListOf<Pair<String, Long>>()

        for ((id, balance) in netBalance) {
            if (balance < 0) {
                debtors.add(id to -balance)
            } else if (balance > 0) {
                creditors.add(id to balance)
            }
        }

        val settlements = mutableListOf<SplitSettlement>()
        var dIndex = 0
        var cIndex = 0

        while (dIndex < debtors.size && cIndex < creditors.size) {
            val (debtorId, debtorOwes) = debtors[dIndex]
            val (creditorId, creditorDue) = creditors[cIndex]

            val settleAmount = minOf(debtorOwes, creditorDue)
            if (settleAmount > 0) {
                settlements.add(
                    SplitSettlement(
                        debtorId = debtorId,
                        debtorName = memberMap[debtorId]?.name ?: debtorId,
                        creditorId = creditorId,
                        creditorName = memberMap[creditorId]?.name ?: creditorId,
                        amountMinor = settleAmount,
                        currency = currency,
                    )
                )
            }

            val newDebtorOwes = debtorOwes - settleAmount
            val newCreditorDue = creditorDue - settleAmount

            if (newDebtorOwes == 0L) {
                dIndex++
            } else {
                debtors[dIndex] = debtorId to newDebtorOwes
            }

            if (newCreditorDue == 0L) {
                cIndex++
            } else {
                creditors[cIndex] = creditorId to newCreditorDue
            }
        }

        return settlements
    }
}

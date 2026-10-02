package app.pocketos.domain.smart

import app.pocketos.domain.model.BillingCycle
import app.pocketos.domain.model.BillingUnit
import app.pocketos.domain.recurrence.BillingCalculator
import java.time.LocalDate
import java.time.temporal.ChronoUnit

/**
 * Context-aware defaults. Every value produced here is only a suggestion
 * shown to the user for confirmation - nothing is applied silently.
 */
object SmartDefaults {

    /**
     * Reminder offsets (days before renewal):
     *  - yearly/long cycles: a week ahead plus the day before,
     *  - monthly-ish cycles: the day before,
     *  - weekly/daily cycles: on the day.
     * Offsets that would already be in the past are dropped.
     */
    fun subscriptionReminderOffsets(billing: BillingCycle, nextRenewal: LocalDate, today: LocalDate): List<Int> {
        val daysUntil = ChronoUnit.DAYS.between(today, nextRenewal).toInt()
        val period = BillingCalculator.approxPeriodDays(billing)
        val ideal = when {
            billing.unit == BillingUnit.YEAR || period >= 180 -> listOf(7, 1)
            period >= 28 -> listOf(1)
            else -> listOf(0)
        }
        val feasible = ideal.filter { it <= daysUntil || daysUntil < 0 }
        return feasible.ifEmpty { listOf(0) }
    }

    /**
     * Suggests a billing cycle when the user has consistently used one
     * (at least 3 recent subscriptions, 60%+ share). Returns null otherwise.
     */
    fun preferredBilling(recent: List<BillingCycle>): BillingCycle? {
        if (recent.size < 3) return null
        val (cycle, count) = recent.groupingBy { it }.eachCount().maxByOrNull { it.value } ?: return null
        return cycle.takeIf { count * 10 >= recent.size * 6 }
    }

    /** Most frequently used currency among the user's subscriptions. */
    fun preferredCurrency(recent: List<String>, fallback: String): String =
        recent.groupingBy { it }.eachCount().maxByOrNull { it.value }?.key ?: fallback
}

package app.pocketos.domain.recurrence

import app.pocketos.domain.model.BillingCycle
import app.pocketos.domain.model.BillingUnit
import app.pocketos.domain.model.Subscription
import java.time.LocalDate
import java.time.temporal.ChronoUnit

/**
 * Renewal-date and cost-estimate maths for subscriptions. Renewal series are
 * anchored on the start date (or the stored renewal date) so monthly
 * renewals on the 31st do not drift to the 28th.
 */
object BillingCalculator {

    private const val DAYS_PER_YEAR = 365.2425

    /** The k-th renewal after [anchor] (k = 0 returns the anchor). */
    fun occurrence(anchor: LocalDate, cycle: BillingCycle, k: Long): LocalDate = when (cycle.unit) {
        BillingUnit.DAY -> anchor.plusDays(k * cycle.interval)
        BillingUnit.WEEK -> anchor.plusWeeks(k * cycle.interval)
        BillingUnit.MONTH -> anchor.plusMonths(k * cycle.interval)
        BillingUnit.YEAR -> anchor.plusYears(k * cycle.interval)
    }

    /** The first renewal on or after [from] in the series anchored at [anchor]. */
    fun nextOnOrAfter(anchor: LocalDate, cycle: BillingCycle, from: LocalDate): LocalDate {
        if (anchor >= from) return anchor
        val unit = when (cycle.unit) {
            BillingUnit.DAY -> ChronoUnit.DAYS
            BillingUnit.WEEK -> ChronoUnit.WEEKS
            BillingUnit.MONTH -> ChronoUnit.MONTHS
            BillingUnit.YEAR -> ChronoUnit.YEARS
        }
        var k = maxOf(0L, unit.between(anchor, from) / cycle.interval)
        var date = occurrence(anchor, cycle, k)
        while (date < from) {
            k++
            date = occurrence(anchor, cycle, k)
        }
        return date
    }

    /** The renewal following [current]. */
    fun nextAfter(anchor: LocalDate, cycle: BillingCycle, current: LocalDate): LocalDate =
        nextOnOrAfter(anchor, cycle, current.plusDays(1))

    /**
     * Rolls a renewal date that has passed forward to the next upcoming
     * renewal. Returns [Subscription.nextRenewal] unchanged when it is today
     * or later.
     */
    fun rolledForward(subscription: Subscription, today: LocalDate): LocalDate {
        if (subscription.nextRenewal >= today) return subscription.nextRenewal
        return nextOnOrAfter(anchorFor(subscription), subscription.billing, today)
    }

    /**
     * The series anchor: the start date when the stored renewal date belongs
     * to the same series (so "the 31st" survives short months), otherwise the
     * renewal date itself.
     */
    fun anchorFor(subscription: Subscription): LocalDate {
        val start = subscription.startDate ?: return subscription.nextRenewal
        if (start > subscription.nextRenewal) return subscription.nextRenewal
        return if (nextOnOrAfter(start, subscription.billing, subscription.nextRenewal) == subscription.nextRenewal) start else subscription.nextRenewal
    }

    /** Renewal dates of [subscription] within [from]..[to]. */
    fun renewalsBetween(subscription: Subscription, from: LocalDate, to: LocalDate, limit: Int = 400): List<LocalDate> {
        val anchor = anchorFor(subscription)
        val result = ArrayList<LocalDate>()
        var date = nextOnOrAfter(anchor, subscription.billing, maxOf(from, subscription.nextRenewal))
        while (date <= to && result.size < limit) {
            result += date
            date = nextAfter(anchor, subscription.billing, date)
        }
        return result
    }

    /** Average cost per month in minor units (an estimate, not a payment). */
    fun monthlyEstimate(amountMinor: Long, cycle: BillingCycle): Double {
        val interval = cycle.interval.toDouble()
        return when (cycle.unit) {
            BillingUnit.DAY -> amountMinor * (DAYS_PER_YEAR / 12.0) / interval
            BillingUnit.WEEK -> amountMinor * (DAYS_PER_YEAR / 7.0 / 12.0) / interval
            BillingUnit.MONTH -> amountMinor / interval
            BillingUnit.YEAR -> amountMinor / (12.0 * interval)
        }
    }

    fun annualEstimate(amountMinor: Long, cycle: BillingCycle): Double = monthlyEstimate(amountMinor, cycle) * 12.0

    /** Approximate length of one billing period in days (for heuristics only). */
    fun approxPeriodDays(cycle: BillingCycle): Int = when (cycle.unit) {
        BillingUnit.DAY -> cycle.interval
        BillingUnit.WEEK -> cycle.interval * 7
        BillingUnit.MONTH -> cycle.interval * 30
        BillingUnit.YEAR -> cycle.interval * 365
    }
}

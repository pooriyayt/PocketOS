package app.pocketos.domain.insights

import app.pocketos.domain.model.BillingUnit
import app.pocketos.domain.model.Reminder
import app.pocketos.domain.model.ReminderEvent
import app.pocketos.domain.model.ReminderEventType
import app.pocketos.domain.model.ReminderKind
import app.pocketos.domain.model.Subscription
import app.pocketos.domain.model.SubscriptionStatus
import app.pocketos.domain.recurrence.BillingCalculator
import java.time.DayOfWeek
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import java.time.temporal.ChronoUnit
import java.time.temporal.TemporalAdjusters
import kotlin.math.roundToLong

/** Estimated recurring cost in one currency. Different currencies are never summed. */
data class CurrencyTotals(val currency: String, val monthlyMinor: Long, val annualMinor: Long, val count: Int)

data class CategorySlice(val category: String, val monthlyMinor: Long, val share: Float)

data class UpcomingPayment(val subscription: Subscription, val date: LocalDate, val amountMinor: Long?)

data class WeekBucket(val weekStart: LocalDate, val completed: Int)

data class InsightsSnapshot(
    val totals: List<CurrencyTotals>,
    /** Category split per currency (largest first). */
    val distribution: Map<String, List<CategorySlice>>,
    val upcoming: List<UpcomingPayment>,
    val renewalDays: Map<LocalDate, List<Subscription>>,
    val completionTrend: List<WeekBucket>,
    val recentlyCompleted: List<Reminder>,
    val completedThisWeek: Int,
    val completedLastWeek: Int,
    val activeSubscriptions: Int,
)

object InsightsCalculator {

    fun compute(
        subscriptions: List<Subscription>,
        reminders: List<Reminder>,
        events: List<ReminderEvent>,
        today: LocalDate,
        zone: ZoneId,
        upcomingDays: Long = 30,
        weeks: Int = 8,
    ): InsightsSnapshot {
        val active = subscriptions.filter { it.status == SubscriptionStatus.ACTIVE }
        val priced = active.filter { it.amount != null }

        val totals = priced.groupBy { it.amount!!.currency }.map { (currency, subs) ->
            val monthly = subs.sumOf { BillingCalculator.monthlyEstimate(it.amount!!.amountMinor, it.billing) }
            CurrencyTotals(currency, monthly.roundToLong(), (monthly * 12).roundToLong(), subs.size)
        }.sortedByDescending { it.count }

        val distribution = priced.groupBy { it.amount!!.currency }.mapValues { (_, subs) ->
            val byCategory = subs.groupBy { it.category }.mapValues { (_, list) ->
                list.sumOf { BillingCalculator.monthlyEstimate(it.amount!!.amountMinor, it.billing) }
            }
            val total = byCategory.values.sum().takeIf { it > 0 } ?: 1.0
            byCategory.map { (category, value) -> CategorySlice(category, value.roundToLong(), (value / total).toFloat()) }
                .sortedByDescending { it.monthlyMinor }
        }

        val horizon = today.plusDays(upcomingDays)
        val upcoming = active.flatMap { sub ->
            BillingCalculator.renewalsBetween(sub, today, horizon, limit = 40).map { UpcomingPayment(sub, it, sub.amount?.amountMinor) }
        }.sortedWith(compareBy<UpcomingPayment> { it.date }.thenBy { it.subscription.name })

        val calendarEnd = today.plusDays(62)
        val renewalDays = active.flatMap { sub -> BillingCalculator.renewalsBetween(sub, today.withDayOfMonth(1), calendarEnd, 70).map { it to sub } }
            .groupBy({ it.first }, { it.second })

        val weekStart = today.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY))
        val completions = events.filter { it.type == ReminderEventType.COMPLETED }
        val trend = (weeks - 1 downTo 0).map { back ->
            val start = weekStart.minusWeeks(back.toLong())
            val end = start.plusWeeks(1)
            WeekBucket(start, completions.count {
                val d = LocalDate.ofInstant(it.at, zone)
                !d.isBefore(start) && d.isBefore(end)
            })
        }

        val recentlyCompleted = reminders.filter { it.completedAt != null }.sortedByDescending { it.completedAt }.take(5)

        return InsightsSnapshot(
            totals = totals,
            distribution = distribution,
            upcoming = upcoming,
            renewalDays = renewalDays,
            completionTrend = trend,
            recentlyCompleted = recentlyCompleted,
            completedThisWeek = trend.lastOrNull()?.completed ?: 0,
            completedLastWeek = trend.getOrNull(trend.size - 2)?.completed ?: 0,
            activeSubscriptions = active.size,
        )
    }
}

/** A data-backed hint. The UI renders text from the type and numbers; nothing is invented. */
sealed interface Suggestion {
    val id: String

    data class RenewalsThisMonth(val count: Int) : Suggestion { override val id = "renewals_month" }
    data class AnnualRenewalsSoon(val count: Int, val firstDate: LocalDate) : Suggestion { override val id = "annual_soon" }
    data class TrialEnding(val subscription: Subscription, val days: Long) : Suggestion { override val id = "trial_${subscription.id}" }
    data class NewSubscriptions(val count: Int, val currency: String, val addedMonthlyMinor: Long) : Suggestion { override val id = "new_subs" }
    data class StaleReminder(val reminder: Reminder, val days: Long) : Suggestion { override val id = "stale_${reminder.id}" }
    data class OverdueBacklog(val count: Int) : Suggestion { override val id = "overdue" }
}

object SuggestionEngine {

    fun suggestions(subscriptions: List<Subscription>, reminders: List<Reminder>, today: LocalDate, now: Instant, zone: ZoneId): List<Suggestion> {
        val out = ArrayList<Suggestion>()
        val active = subscriptions.filter { it.status == SubscriptionStatus.ACTIVE }

        active.filter { it.trialEnd != null && !it.trialEnd.isBefore(today) && ChronoUnit.DAYS.between(today, it.trialEnd) <= 3 }
            .minByOrNull { it.trialEnd!! }
            ?.let { out += Suggestion.TrialEnding(it, ChronoUnit.DAYS.between(today, it.trialEnd)) }

        val monthEnd = today.with(TemporalAdjusters.lastDayOfMonth())
        val renewingThisMonth = active.count { BillingCalculator.renewalsBetween(it, today, monthEnd, 1).isNotEmpty() }
        if (renewingThisMonth >= 3) out += Suggestion.RenewalsThisMonth(renewingThisMonth)

        val annual = active.filter { it.billing.unit == BillingUnit.YEAR && !it.nextRenewal.isBefore(today) && ChronoUnit.DAYS.between(today, it.nextRenewal) <= 30 }
        if (annual.isNotEmpty()) out += Suggestion.AnnualRenewalsSoon(annual.size, annual.minOf { it.nextRenewal })

        val monthAgo = now.minus(30, ChronoUnit.DAYS)
        val recent = active.filter { it.createdAt.isAfter(monthAgo) && it.amount != null }
        if (recent.isNotEmpty() && active.size > recent.size) {
            val currency = recent.groupingBy { it.amount!!.currency }.eachCount().maxBy { it.value }.key
            val added = recent.filter { it.amount!!.currency == currency }.sumOf { BillingCalculator.monthlyEstimate(it.amount!!.amountMinor, it.billing) }
            out += Suggestion.NewSubscriptions(recent.size, currency, added.roundToLong())
        }

        val open = reminders.filter { !it.isCompleted && it.dueDate != null }
        val overdue = open.filter { it.dueDate!!.isBefore(today) }
        overdue.filter { it.recurrence == null && ChronoUnit.DAYS.between(it.dueDate, today) >= 90 }
            .minByOrNull { it.dueDate!! }
            ?.let { out += Suggestion.StaleReminder(it, ChronoUnit.DAYS.between(it.dueDate, today)) }
        if (overdue.size >= 3) out += Suggestion.OverdueBacklog(overdue.size)
        return out
    }
}

enum class DayPeriod { MORNING, AFTERNOON, EVENING, NIGHT }

/** One row in the "needs attention" / timeline lists. */
sealed interface AgendaItem {
    val date: LocalDate
    val sortTime: LocalTime

    data class ReminderItem(val reminder: Reminder, override val date: LocalDate, val overdue: Boolean) : AgendaItem {
        override val sortTime: LocalTime get() = reminder.dueTime ?: LocalTime.of(23, 59)
    }

    data class RenewalItem(val subscription: Subscription, override val date: LocalDate, val trialEnd: Boolean = false) : AgendaItem {
        override val sortTime: LocalTime get() = LocalTime.of(0, 0)
    }
}

data class DashboardModel(
    val period: DayPeriod,
    val attention: List<AgendaItem>,
    val timeline: List<AgendaItem>,
    val dueToday: Int,
    val overdue: Int,
    val completedToday: Int,
    val openTasks: Int,
    val paymentsNext7Days: Int,
    val renewals: List<UpcomingPayment>,
    val isEmpty: Boolean,
)

object DashboardBuilder {

    fun period(time: LocalTime): DayPeriod = when (time.hour) {
        in 5..11 -> DayPeriod.MORNING
        in 12..16 -> DayPeriod.AFTERNOON
        in 17..21 -> DayPeriod.EVENING
        else -> DayPeriod.NIGHT
    }

    fun build(
        reminders: List<Reminder>,
        subscriptions: List<Subscription>,
        events: List<ReminderEvent>,
        today: LocalDate,
        nowTime: LocalTime,
        zone: ZoneId,
    ): DashboardModel {
        val open = reminders.filter { !it.isCompleted }
        val dated = open.filter { it.dueDate != null }
        val overdue = dated.filter { it.dueDate!!.isBefore(today) }
        val dueToday = dated.filter { it.dueDate == today }
        val active = subscriptions.filter { it.status == SubscriptionStatus.ACTIVE }

        val soonRenewals = active.filter { !it.nextRenewal.isBefore(today) && ChronoUnit.DAYS.between(today, it.nextRenewal) <= 3 }
            .map { AgendaItem.RenewalItem(it, it.nextRenewal) }
        val trials = active.filter { it.trialEnd != null && !it.trialEnd.isBefore(today) && ChronoUnit.DAYS.between(today, it.trialEnd) <= 3 }
            .map { AgendaItem.RenewalItem(it, it.trialEnd!!, trialEnd = true) }

        val attention = (overdue.map { AgendaItem.ReminderItem(it, it.dueDate!!, overdue = true) } +
            dueToday.map { AgendaItem.ReminderItem(it, today, overdue = false) } + soonRenewals + trials)
            .sortedWith(compareBy<AgendaItem> { it.date }.thenBy { it.sortTime })

        val horizon = today.plusDays(14)
        val upcomingReminders = dated.filter { it.dueDate!!.isAfter(today) && !it.dueDate.isAfter(horizon) }
            .map { AgendaItem.ReminderItem(it, it.dueDate!!, overdue = false) }
        val upcomingRenewals = active.flatMap { sub ->
            BillingCalculator.renewalsBetween(sub, today.plusDays(4), horizon, 4).map { AgendaItem.RenewalItem(sub, it) }
        }
        val timeline = (upcomingReminders + upcomingRenewals).sortedWith(compareBy<AgendaItem> { it.date }.thenBy { it.sortTime }).take(20)

        val completedToday = events.count { it.type == ReminderEventType.COMPLETED && LocalDate.ofInstant(it.at, zone) == today }
        val renewals = active.flatMap { sub -> BillingCalculator.renewalsBetween(sub, today, today.plusDays(45), 2).map { UpcomingPayment(sub, it, sub.amount?.amountMinor) } }
            .sortedBy { it.date }.take(6)

        return DashboardModel(
            period = period(nowTime),
            attention = attention,
            timeline = timeline,
            dueToday = dueToday.size,
            overdue = overdue.size,
            completedToday = completedToday,
            openTasks = open.count { it.kind == ReminderKind.TASK },
            paymentsNext7Days = active.count { s -> BillingCalculator.renewalsBetween(s, today, today.plusDays(7), 1).isNotEmpty() },
            renewals = renewals,
            isEmpty = reminders.isEmpty() && subscriptions.isEmpty(),
        )
    }
}

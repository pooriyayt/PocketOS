package app.pocketos.domain.model

import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime

enum class ReminderKind(val wire: String) {
    REMINDER("reminder"),
    TASK("task");

    companion object {
        fun fromWire(value: String?): ReminderKind = entries.firstOrNull { it.wire == value } ?: REMINDER
    }
}

enum class Priority(val level: Int) {
    LOW(0),
    NORMAL(1),
    HIGH(2);

    companion object {
        fun fromLevel(level: Int): Priority = entries.firstOrNull { it.level == level } ?: NORMAL
    }
}

/**
 * A reminder or task. Dates are "floating" local dates/times interpreted in
 * [timeZone] when set, otherwise in the device's current zone (so "7 PM"
 * stays 7 PM when travelling).
 */
data class Reminder(
    val id: String,
    val kind: ReminderKind = ReminderKind.REMINDER,
    val title: String,
    val notes: String? = null,
    val dueDate: LocalDate? = null,
    val dueTime: LocalTime? = null,
    val allDay: Boolean = false,
    val timeZone: String? = null,
    val recurrence: RecurrenceRule? = null,
    val priority: Priority = Priority.NORMAL,
    val category: String = "personal",
    val leadMinutes: Int = 0,
    val completedAt: Instant? = null,
    val snoozedUntil: Instant? = null,
    /** Device-local notification sound (never synchronised). */
    val soundUri: String? = null,
    val createdAt: Instant,
    val updatedAt: Instant,
) {
    val isCompleted: Boolean get() = completedAt != null
    val isRecurring: Boolean get() = recurrence != null
}

enum class SubscriptionStatus(val wire: String) {
    ACTIVE("active"),
    PAUSED("paused"),
    CANCELLED("cancelled"),
    EXPIRED("expired");

    companion object {
        fun fromWire(value: String?): SubscriptionStatus = entries.firstOrNull { it.wire == value } ?: ACTIVE
    }
}

enum class BillingUnit(val wire: String) {
    DAY("day"),
    WEEK("week"),
    MONTH("month"),
    YEAR("year");

    companion object {
        fun fromWire(value: String?): BillingUnit = entries.firstOrNull { it.wire == value } ?: MONTH
    }
}

data class BillingCycle(val unit: BillingUnit, val interval: Int = 1) {
    init {
        require(interval in 1..365) { "interval must be 1..365" }
    }

    companion object {
        val MONTHLY = BillingCycle(BillingUnit.MONTH, 1)
        val YEARLY = BillingCycle(BillingUnit.YEAR, 1)
        val WEEKLY = BillingCycle(BillingUnit.WEEK, 1)
    }
}

/** Money as integer minor units of [currency] (cents, whole rials/tomans...). */
data class Money(val amountMinor: Long, val currency: String)

data class Subscription(
    val id: String,
    val name: String,
    val serviceId: String? = null,
    val category: String = "other",
    val amount: Money?,
    val billing: BillingCycle,
    val startDate: LocalDate? = null,
    val nextRenewal: LocalDate,
    val trialEnd: LocalDate? = null,
    val cancellationUrl: String? = null,
    val notes: String? = null,
    /** Days before renewal to notify, e.g. [7, 1]. Empty = no reminders. */
    val reminderOffsets: List<Int> = listOf(1),
    val status: SubscriptionStatus = SubscriptionStatus.ACTIVE,
    val color: String? = null,
    val createdAt: Instant,
    val updatedAt: Instant,
) {
    val currency: String get() = amount?.currency ?: "USD"
    val isActive: Boolean get() = status == SubscriptionStatus.ACTIVE
}

enum class CategoryKind(val wire: String) {
    REMINDER("reminder"),
    SUBSCRIPTION("subscription");

    companion object {
        fun fromWire(value: String?): CategoryKind = entries.firstOrNull { it.wire == value } ?: REMINDER
    }
}

/** User-created category. Built-in categories are defined in [app.pocketos.domain.categories.Categories]. */
data class CustomCategory(
    val id: String,
    val kind: CategoryKind,
    val name: String,
    val color: String?,
    val icon: String?,
    val createdAt: Instant,
    val updatedAt: Instant,
)

/** Local-only history of reminder interactions (completion trends, history view). */
enum class ReminderEventType { CREATED, COMPLETED, SNOOZED, RESCHEDULED, UNCOMPLETED }

data class ReminderEvent(
    val id: Long,
    val reminderId: String,
    val type: ReminderEventType,
    val at: Instant,
    val occurrenceDate: LocalDate?,
)

package app.pocketos.data.local

import app.pocketos.domain.model.BillingCycle
import app.pocketos.domain.model.BillingUnit
import app.pocketos.domain.model.CategoryKind
import app.pocketos.domain.model.CustomCategory
import app.pocketos.domain.model.Money
import app.pocketos.domain.model.Priority
import app.pocketos.domain.model.RecurrenceRule
import app.pocketos.domain.model.Reminder
import app.pocketos.domain.model.ReminderEvent
import app.pocketos.domain.model.ReminderEventType
import app.pocketos.domain.model.ReminderKind
import app.pocketos.domain.model.Subscription
import app.pocketos.domain.model.SubscriptionStatus
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.format.DateTimeFormatter

private val TIME: DateTimeFormatter = DateTimeFormatter.ofPattern("HH:mm")

fun LocalDate.iso(): String = toString()
fun LocalTime.hhmm(): String = format(TIME)
fun String.toLocalDateOrNull(): LocalDate? = runCatching { LocalDate.parse(this) }.getOrNull()
fun String.toLocalTimeOrNull(): LocalTime? = runCatching { LocalTime.parse(this) }.getOrNull()

fun ReminderEntity.toDomain(): Reminder = Reminder(
    id = id,
    kind = ReminderKind.fromWire(kind),
    title = title,
    notes = notes,
    dueDate = dueDate?.toLocalDateOrNull(),
    dueTime = dueTime?.toLocalTimeOrNull(),
    allDay = allDay,
    timeZone = timeZone,
    recurrence = RecurrenceRule.parse(recurrence),
    priority = Priority.fromLevel(priority),
    category = category,
    leadMinutes = leadMinutes,
    completedAt = completedAt?.let(Instant::ofEpochMilli),
    snoozedUntil = snoozedUntil?.let(Instant::ofEpochMilli),
    soundUri = soundUri,
    createdAt = Instant.ofEpochMilli(createdAt),
    updatedAt = Instant.ofEpochMilli(updatedAt),
)

/** Maps a domain reminder onto an entity, preserving the sync bookkeeping of [existing]. */
fun Reminder.toEntity(existing: ReminderEntity?, dirty: Boolean): ReminderEntity = ReminderEntity(
    id = id,
    kind = kind.wire,
    title = title,
    notes = notes,
    dueDate = dueDate?.iso(),
    dueTime = dueTime?.hhmm(),
    allDay = allDay,
    timeZone = timeZone,
    recurrence = recurrence?.toRRule(),
    priority = priority.level,
    category = category,
    leadMinutes = leadMinutes,
    completedAt = completedAt?.toEpochMilli(),
    snoozedUntil = snoozedUntil?.toEpochMilli(),
    soundUri = soundUri,
    createdAt = createdAt.toEpochMilli(),
    updatedAt = updatedAt.toEpochMilli(),
    deletedAt = null,
    serverVersion = existing?.serverVersion ?: 0,
    dirty = dirty || existing?.dirty == true,
)

fun SubscriptionEntity.toDomain(): Subscription = Subscription(
    id = id,
    name = name,
    serviceId = serviceId,
    category = category,
    amount = amountMinor?.let { Money(it, currency) },
    billing = runCatching { BillingCycle(BillingUnit.fromWire(billingUnit), billingInterval) }.getOrDefault(BillingCycle.MONTHLY),
    startDate = startDate?.toLocalDateOrNull(),
    nextRenewal = nextRenewalDate.toLocalDateOrNull() ?: LocalDate.now(),
    trialEnd = trialEndDate?.toLocalDateOrNull(),
    cancellationUrl = cancellationUrl,
    notes = notes,
    reminderOffsets = reminderOffsets.split(',').mapNotNull { it.trim().toIntOrNull() }.filter { it in 0..365 }.distinct().sortedDescending(),
    status = SubscriptionStatus.fromWire(status),
    color = color,
    createdAt = Instant.ofEpochMilli(createdAt),
    updatedAt = Instant.ofEpochMilli(updatedAt),
)

fun Subscription.toEntity(existing: SubscriptionEntity?, dirty: Boolean, currencyFallback: String = "USD"): SubscriptionEntity = SubscriptionEntity(
    id = id,
    name = name,
    serviceId = serviceId,
    category = category,
    amountMinor = amount?.amountMinor,
    currency = amount?.currency ?: existing?.currency ?: currencyFallback,
    billingUnit = billing.unit.wire,
    billingInterval = billing.interval,
    startDate = startDate?.iso(),
    nextRenewalDate = nextRenewal.iso(),
    trialEndDate = trialEnd?.iso(),
    cancellationUrl = cancellationUrl,
    notes = notes,
    reminderOffsets = reminderOffsets.distinct().sortedDescending().take(5).joinToString(","),
    status = status.wire,
    color = color,
    createdAt = createdAt.toEpochMilli(),
    updatedAt = updatedAt.toEpochMilli(),
    deletedAt = null,
    serverVersion = existing?.serverVersion ?: 0,
    dirty = dirty || existing?.dirty == true,
)

fun CategoryEntity.toDomain(): CustomCategory = CustomCategory(
    id = id,
    kind = CategoryKind.fromWire(kind),
    name = name,
    color = color,
    icon = icon,
    createdAt = Instant.ofEpochMilli(createdAt),
    updatedAt = Instant.ofEpochMilli(updatedAt),
)

fun ReminderEventEntity.toDomain(): ReminderEvent = ReminderEvent(
    id = id,
    reminderId = reminderId,
    type = runCatching { ReminderEventType.valueOf(type) }.getOrDefault(ReminderEventType.CREATED),
    at = Instant.ofEpochMilli(at),
    occurrenceDate = occurrenceDate?.toLocalDateOrNull(),
)

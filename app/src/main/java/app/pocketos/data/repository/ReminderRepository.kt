package app.pocketos.data.repository

import app.pocketos.core.AppClock
import app.pocketos.data.local.DatabaseManager
import app.pocketos.data.local.ReminderEntity
import app.pocketos.data.local.ReminderEventEntity
import app.pocketos.data.local.iso
import app.pocketos.data.local.toDomain
import app.pocketos.data.local.toEntity
import app.pocketos.domain.model.Reminder
import app.pocketos.domain.model.ReminderEvent
import app.pocketos.domain.model.ReminderEventType
import app.pocketos.domain.recurrence.RecurrenceEngine
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.util.UUID

/**
 * Local-first reminder storage. Every write lands in Room immediately (the UI
 * updates from Flows), then side effects reschedule alarms, refresh widgets
 * and queue a sync. Reminders never depend on the network.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class ReminderRepository(
    private val databases: DatabaseManager,
    private val clock: AppClock,
    private val effects: SideEffects,
) {
    private val dao get() = databases.current.reminders()

    val reminders: Flow<List<Reminder>> = databases.active.flatMapLatest { it.db.reminders().observeAll() }
        .map { list -> list.map { it.toDomain() } }

    fun observe(id: String): Flow<Reminder?> = databases.active.flatMapLatest { it.db.reminders().observe(id) }
        .map { it?.toDomain() }

    fun eventsSince(from: Instant): Flow<List<ReminderEvent>> =
        databases.active.flatMapLatest { it.db.reminders().observeEventsSince(from.toEpochMilli()) }
            .map { list -> list.map { it.toDomain() } }

    fun history(id: String): Flow<List<ReminderEvent>> =
        databases.active.flatMapLatest { it.db.reminders().observeEventsFor(id) }.map { list -> list.map { it.toDomain() } }

    suspend fun get(id: String): Reminder? = dao.get(id)?.takeIf { it.deletedAt == null }?.toDomain()

    fun newId(): String = UUID.randomUUID().toString()

    /** Creates or updates a reminder. Returns the saved reminder. */
    suspend fun save(reminder: Reminder): Reminder {
        val existing = dao.get(reminder.id)
        val now = clock.now()
        val saved = reminder.copy(
            title = reminder.title.trim().take(200),
            notes = reminder.notes?.trim()?.take(5000)?.ifEmpty { null },
            createdAt = existing?.let { Instant.ofEpochMilli(it.createdAt) } ?: now,
            updatedAt = now,
        )
        dao.upsert(saved.toEntity(existing, dirty = true))
        if (existing == null) logEvent(saved.id, ReminderEventType.CREATED, saved.dueDate)
        effects.onReminderChanged(saved.id, deleted = false)
        effects.onDataChanged()
        return saved
    }

    /** Result of completing a reminder; [previous] allows undo. */
    data class Completion(val previous: ReminderEntity, val seriesFinished: Boolean, val nextDue: LocalDate?)

    /**
     * Completes a reminder. Recurring reminders advance to their next
     * occurrence (skipping occurrences already in the past); one-off
     * reminders are marked complete.
     */
    suspend fun complete(id: String): Completion? {
        val entity = dao.get(id)?.takeIf { it.deletedAt == null } ?: return null
        val reminder = entity.toDomain()
        val now = clock.now()
        val today = clock.today()
        val rule = reminder.recurrence
        val due = reminder.dueDate
        val updated: Reminder
        var next: LocalDate? = null
        if (rule != null && due != null) {
            next = RecurrenceEngine.nextAfterCompletion(rule, due, due, today)
            updated = if (next != null) {
                reminder.copy(dueDate = next, snoozedUntil = null, completedAt = null, updatedAt = now)
            } else {
                reminder.copy(completedAt = now, snoozedUntil = null, updatedAt = now)
            }
        } else {
            updated = reminder.copy(completedAt = now, snoozedUntil = null, updatedAt = now)
        }
        dao.upsert(updated.toEntity(entity, dirty = true))
        logEvent(id, ReminderEventType.COMPLETED, due)
        effects.onReminderChanged(id, deleted = false)
        effects.onDataChanged()
        return Completion(entity, seriesFinished = rule != null && next == null, nextDue = next)
    }

    suspend fun uncomplete(id: String) {
        val entity = dao.get(id) ?: return
        val reminder = entity.toDomain().copy(completedAt = null, updatedAt = clock.now())
        dao.upsert(reminder.toEntity(entity, dirty = true))
        logEvent(id, ReminderEventType.UNCOMPLETED, reminder.dueDate)
        effects.onReminderChanged(id, deleted = false)
        effects.onDataChanged()
    }

    /** Restores a previous state (undo for complete / snooze / delete). */
    suspend fun restore(previous: ReminderEntity) {
        val existing = dao.get(previous.id)
        dao.upsert(
            previous.copy(
                deletedAt = null,
                updatedAt = clock.nowMs(),
                dirty = true,
                serverVersion = existing?.serverVersion ?: previous.serverVersion,
            )
        )
        effects.onReminderChanged(previous.id, deleted = false)
        effects.onDataChanged()
    }

    suspend fun snooze(id: String, minutes: Long): ReminderEntity? {
        val entity = dao.get(id)?.takeIf { it.deletedAt == null } ?: return null
        val until = clock.now().plusSeconds(minutes * 60)
        val reminder = entity.toDomain().copy(snoozedUntil = until, updatedAt = clock.now())
        dao.upsert(reminder.toEntity(entity, dirty = true))
        logEvent(id, ReminderEventType.SNOOZED, reminder.dueDate)
        effects.onReminderChanged(id, deleted = false)
        effects.onDataChanged()
        return entity
    }

    suspend fun reschedule(id: String, date: LocalDate?, time: LocalTime?): ReminderEntity? {
        val entity = dao.get(id)?.takeIf { it.deletedAt == null } ?: return null
        val reminder = entity.toDomain().copy(dueDate = date, dueTime = time, snoozedUntil = null, completedAt = null, updatedAt = clock.now())
        dao.upsert(reminder.toEntity(entity, dirty = true))
        logEvent(id, ReminderEventType.RESCHEDULED, date)
        effects.onReminderChanged(id, deleted = false)
        effects.onDataChanged()
        return entity
    }

    suspend fun setCategory(id: String, category: String) {
        val entity = dao.get(id)?.takeIf { it.deletedAt == null } ?: return
        dao.upsert(entity.toDomain().copy(category = category, updatedAt = clock.now()).toEntity(entity, dirty = true))
        effects.onDataChanged()
    }

    suspend fun duplicate(id: String): Reminder? {
        val source = get(id) ?: return null
        val now = clock.now()
        return save(source.copy(id = newId(), title = source.title, completedAt = null, snoozedUntil = null, createdAt = now, updatedAt = now))
    }

    /**
     * Deletes a reminder. Never-synced rows are removed outright; synced rows
     * become tombstones so the deletion reaches other devices.
     * Returns the previous row for undo.
     */
    suspend fun delete(id: String): ReminderEntity? {
        val entity = dao.get(id)?.takeIf { it.deletedAt == null } ?: return null
        if (entity.serverVersion == 0L) {
            dao.deleteHard(id)
        } else {
            val now = clock.nowMs()
            dao.upsert(entity.copy(deletedAt = now, updatedAt = now, dirty = true))
        }
        effects.onReminderChanged(id, deleted = true)
        effects.onDataChanged()
        return entity
    }

    private suspend fun logEvent(id: String, type: ReminderEventType, occurrence: LocalDate?) {
        dao.insertEvent(ReminderEventEntity(reminderId = id, type = type.name, at = clock.nowMs(), occurrenceDate = occurrence?.iso()))
    }
}

package app.pocketos.data.local

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/*
 * Room entities. Dates are stored as ISO strings ("2026-03-10", "19:00") so
 * they sort lexicographically; instants as epoch milliseconds.
 *
 * Sync bookkeeping on synchronised tables:
 *  - serverVersion: last server version this row is based on (0 = never synced)
 *  - dirty: local change not yet acknowledged by the server
 *  - deletedAt: tombstone, kept until the deletion has been pushed
 */

@Entity(
    tableName = "reminders",
    indices = [Index("dueDate"), Index("deletedAt"), Index("dirty"), Index("completedAt")],
)
data class ReminderEntity(
    @PrimaryKey val id: String,
    val kind: String,
    val title: String,
    val notes: String?,
    val dueDate: String?,
    val dueTime: String?,
    val allDay: Boolean,
    val timeZone: String?,
    val recurrence: String?,
    val priority: Int,
    val category: String,
    val leadMinutes: Int,
    val completedAt: Long?,
    val snoozedUntil: Long?,
    /** Device-local only; never synchronised. */
    val soundUri: String?,
    val createdAt: Long,
    val updatedAt: Long,
    val deletedAt: Long?,
    @ColumnInfo(defaultValue = "0") val serverVersion: Long = 0,
    @ColumnInfo(defaultValue = "0") val dirty: Boolean = false,
)

@Entity(
    tableName = "subscriptions",
    indices = [Index("nextRenewalDate"), Index("deletedAt"), Index("dirty"), Index("status")],
)
data class SubscriptionEntity(
    @PrimaryKey val id: String,
    val name: String,
    val serviceId: String?,
    val category: String,
    val amountMinor: Long?,
    val currency: String,
    val billingUnit: String,
    val billingInterval: Int,
    val startDate: String?,
    val nextRenewalDate: String,
    val trialEndDate: String?,
    val cancellationUrl: String?,
    val notes: String?,
    val reminderOffsets: String,
    val status: String,
    val color: String?,
    val createdAt: Long,
    val updatedAt: Long,
    val deletedAt: Long?,
    @ColumnInfo(defaultValue = "0") val serverVersion: Long = 0,
    @ColumnInfo(defaultValue = "0") val dirty: Boolean = false,
)

@Entity(tableName = "categories", indices = [Index("dirty")])
data class CategoryEntity(
    @PrimaryKey val id: String,
    val kind: String,
    val name: String,
    val color: String?,
    val icon: String?,
    val createdAt: Long,
    val updatedAt: Long,
    val deletedAt: Long?,
    @ColumnInfo(defaultValue = "0") val serverVersion: Long = 0,
    @ColumnInfo(defaultValue = "0") val dirty: Boolean = false,
)

/** Allow-listed preferences that follow the account across devices. */
@Entity(tableName = "synced_preferences")
data class PreferenceEntity(
    @PrimaryKey val key: String,
    val value: String?,
    val createdAt: Long,
    val updatedAt: Long,
    val deletedAt: Long?,
    @ColumnInfo(defaultValue = "0") val serverVersion: Long = 0,
    @ColumnInfo(defaultValue = "0") val dirty: Boolean = false,
)

/** Local-only reminder history (completion trends, history view). */
@Entity(tableName = "reminder_events", indices = [Index("reminderId"), Index("at")])
data class ReminderEventEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val reminderId: String,
    val type: String,
    val at: Long,
    val occurrenceDate: String?,
)

/** Local-only usage statistics for the service picker ("recent", "frequent"). */
@Entity(tableName = "service_usage")
data class ServiceUsageEntity(
    @PrimaryKey val serviceId: String,
    val useCount: Int,
    val lastUsedAt: Long,
)

/** Key/value bookkeeping for this profile (sync cursor, last notification...). */
@Entity(tableName = "meta")
data class MetaEntity(
    @PrimaryKey val key: String,
    val value: String,
)

/** Accounting: a wallet (cash, bank account, card, savings). */
@Entity(tableName = "wallets")
data class WalletEntity(
    @PrimaryKey val id: String,
    val name: String,
    val type: String,
    val currency: String,
    val openingBalanceMinor: Long,
    val color: String?,
    val sortOrder: Int,
    val archived: Boolean,
    val createdAt: Long,
    val updatedAt: Long,
)

/** Accounting: one income, expense or transfer. [date] is ISO yyyy-MM-dd. */
@Entity(tableName = "transactions", indices = [Index("date"), Index("walletId")])
data class TransactionEntity(
    @PrimaryKey val id: String,
    val type: String,
    val amountMinor: Long,
    val currency: String,
    val walletId: String,
    val toWalletId: String?,
    val category: String,
    val note: String?,
    val date: String,
    val createdAt: Long,
    val updatedAt: Long,
)

/** Accounting: an installment plan. Dates are ISO yyyy-MM-dd; [calendar] is the CalendarKind name. */
@Entity(tableName = "installments")
data class InstallmentEntity(
    @PrimaryKey val id: String,
    val title: String,
    val lender: String?,
    val amountMinor: Long,
    val currency: String,
    val totalCount: Int,
    val paidCount: Int,
    val firstDue: String,
    val intervalMonths: Int,
    val calendar: String,
    val reminderDays: Int,
    val walletId: String?,
    val note: String?,
    val createdAt: Long,
    val updatedAt: Long,
)

/** Accounting: money owed between the user and another person. */
@Entity(tableName = "debts")
data class DebtEntity(
    @PrimaryKey val id: String,
    val person: String,
    val direction: String,
    val amountMinor: Long,
    val currency: String,
    val settledMinor: Long,
    val date: String,
    val dueDate: String?,
    val note: String?,
    val createdAt: Long,
    val updatedAt: Long,
)

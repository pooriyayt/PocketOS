package app.pocketos.data.local

import androidx.room.AutoMigration
import androidx.room.Database
import androidx.room.RoomDatabase

@Database(
    entities = [
        ReminderEntity::class,
        SubscriptionEntity::class,
        CategoryEntity::class,
        PreferenceEntity::class,
        ReminderEventEntity::class,
        ServiceUsageEntity::class,
        MetaEntity::class,
        WalletEntity::class,
        TransactionEntity::class,
        InstallmentEntity::class,
        DebtEntity::class,
    ],
    version = 3,
    exportSchema = true,
    // v2 adds wallets/transactions, v3 installments/debts; Room generates the
    // migrations from the exported schemas.
    autoMigrations = [AutoMigration(from = 1, to = 2), AutoMigration(from = 2, to = 3)],
)
abstract class PocketDatabase : RoomDatabase() {
    abstract fun reminders(): ReminderDao
    abstract fun subscriptions(): SubscriptionDao
    abstract fun categories(): CategoryDao
    abstract fun preferences(): PreferenceDao
    abstract fun serviceUsage(): ServiceUsageDao
    abstract fun meta(): MetaDao
    abstract fun wallets(): WalletDao
    abstract fun transactions(): TransactionDao
    abstract fun installments(): InstallmentDao
    abstract fun debts(): DebtDao

    companion object {
        /** Future schema changes add Migration objects here (never destructive fallbacks). */
        val MIGRATIONS = emptyArray<androidx.room.migration.Migration>()
    }
}

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
    ],
    version = 2,
    exportSchema = true,
    // v2 adds the accounting tables; Room generates the migration from the exported schemas.
    autoMigrations = [AutoMigration(from = 1, to = 2)],
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

    companion object {
        /** Future schema changes add Migration objects here (never destructive fallbacks). */
        val MIGRATIONS = emptyArray<androidx.room.migration.Migration>()
    }
}

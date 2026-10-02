package app.pocketos.data.local

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
    ],
    version = 1,
    exportSchema = true,
)
abstract class PocketDatabase : RoomDatabase() {
    abstract fun reminders(): ReminderDao
    abstract fun subscriptions(): SubscriptionDao
    abstract fun categories(): CategoryDao
    abstract fun preferences(): PreferenceDao
    abstract fun serviceUsage(): ServiceUsageDao
    abstract fun meta(): MetaDao

    companion object {
        /** Future schema changes add Migration objects here (never destructive fallbacks). */
        val MIGRATIONS = emptyArray<androidx.room.migration.Migration>()
    }
}

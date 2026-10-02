package app.pocketos.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Upsert
import kotlinx.coroutines.flow.Flow

@Dao
interface ReminderDao {
    @Query(
        """SELECT * FROM reminders WHERE deletedAt IS NULL
           ORDER BY completedAt IS NOT NULL, dueDate IS NULL, dueDate, dueTime IS NULL, dueTime, createdAt"""
    )
    fun observeAll(): Flow<List<ReminderEntity>>

    @Query("SELECT * FROM reminders WHERE id = :id AND deletedAt IS NULL")
    fun observe(id: String): Flow<ReminderEntity?>

    @Query("SELECT * FROM reminders WHERE id = :id")
    suspend fun get(id: String): ReminderEntity?

    @Query("SELECT * FROM reminders WHERE deletedAt IS NULL AND completedAt IS NULL AND dueDate IS NOT NULL")
    suspend fun schedulable(): List<ReminderEntity>

    @Query("SELECT * FROM reminders WHERE deletedAt IS NULL")
    suspend fun allActive(): List<ReminderEntity>

    @Query("SELECT * FROM reminders")
    suspend fun everything(): List<ReminderEntity>

    @Query("SELECT * FROM reminders WHERE dirty = 1 ORDER BY updatedAt LIMIT :limit")
    suspend fun dirty(limit: Int): List<ReminderEntity>

    @Query("SELECT COUNT(*) FROM reminders WHERE dirty = 1")
    fun observeDirtyCount(): Flow<Int>

    @Upsert
    suspend fun upsert(entity: ReminderEntity)

    @Upsert
    suspend fun upsertAll(entities: List<ReminderEntity>)

    @Query("DELETE FROM reminders WHERE id = :id")
    suspend fun deleteHard(id: String)

    /** Clears the dirty flag only if the row was not edited while the push was in flight. */
    @Query("UPDATE reminders SET dirty = 0, serverVersion = :version WHERE id = :id AND updatedAt = :updatedAt")
    suspend fun markSynced(id: String, version: Long, updatedAt: Long): Int

    @Query("UPDATE reminders SET serverVersion = :version WHERE id = :id")
    suspend fun setServerVersion(id: String, version: Long)

    @Query("UPDATE reminders SET dirty = 1, serverVersion = 0")
    suspend fun markAllDirtyForResync()

    @Query("DELETE FROM reminders WHERE deletedAt IS NOT NULL AND dirty = 0")
    suspend fun purgeSyncedTombstones()

    @Query("DELETE FROM reminders")
    suspend fun clear()

    @Insert
    suspend fun insertEvent(event: ReminderEventEntity)

    @Query("SELECT * FROM reminder_events WHERE at >= :from ORDER BY at DESC")
    fun observeEventsSince(from: Long): Flow<List<ReminderEventEntity>>

    @Query("SELECT * FROM reminder_events WHERE reminderId = :reminderId ORDER BY at DESC LIMIT 50")
    fun observeEventsFor(reminderId: String): Flow<List<ReminderEventEntity>>

    @Query("SELECT * FROM reminder_events ORDER BY at")
    suspend fun allEvents(): List<ReminderEventEntity>

    @Query("DELETE FROM reminder_events WHERE at < :before")
    suspend fun pruneEvents(before: Long)
}

@Dao
interface SubscriptionDao {
    @Query("SELECT * FROM subscriptions WHERE deletedAt IS NULL ORDER BY nextRenewalDate, name")
    fun observeAll(): Flow<List<SubscriptionEntity>>

    @Query("SELECT * FROM subscriptions WHERE id = :id AND deletedAt IS NULL")
    fun observe(id: String): Flow<SubscriptionEntity?>

    @Query("SELECT * FROM subscriptions WHERE id = :id")
    suspend fun get(id: String): SubscriptionEntity?

    @Query("SELECT * FROM subscriptions WHERE deletedAt IS NULL")
    suspend fun allActive(): List<SubscriptionEntity>

    @Query("SELECT * FROM subscriptions")
    suspend fun everything(): List<SubscriptionEntity>

    @Query("SELECT * FROM subscriptions WHERE dirty = 1 ORDER BY updatedAt LIMIT :limit")
    suspend fun dirty(limit: Int): List<SubscriptionEntity>

    @Query("SELECT COUNT(*) FROM subscriptions WHERE dirty = 1")
    fun observeDirtyCount(): Flow<Int>

    @Upsert
    suspend fun upsert(entity: SubscriptionEntity)

    @Upsert
    suspend fun upsertAll(entities: List<SubscriptionEntity>)

    @Query("DELETE FROM subscriptions WHERE id = :id")
    suspend fun deleteHard(id: String)

    @Query("UPDATE subscriptions SET dirty = 0, serverVersion = :version WHERE id = :id AND updatedAt = :updatedAt")
    suspend fun markSynced(id: String, version: Long, updatedAt: Long): Int

    @Query("UPDATE subscriptions SET serverVersion = :version WHERE id = :id")
    suspend fun setServerVersion(id: String, version: Long)

    @Query("UPDATE subscriptions SET dirty = 1, serverVersion = 0")
    suspend fun markAllDirtyForResync()

    @Query("DELETE FROM subscriptions WHERE deletedAt IS NOT NULL AND dirty = 0")
    suspend fun purgeSyncedTombstones()

    @Query("DELETE FROM subscriptions")
    suspend fun clear()
}

@Dao
interface CategoryDao {
    @Query("SELECT * FROM categories WHERE deletedAt IS NULL ORDER BY name")
    fun observeAll(): Flow<List<CategoryEntity>>

    @Query("SELECT * FROM categories WHERE id = :id")
    suspend fun get(id: String): CategoryEntity?

    @Query("SELECT * FROM categories")
    suspend fun everything(): List<CategoryEntity>

    @Query("SELECT * FROM categories WHERE dirty = 1 ORDER BY updatedAt LIMIT :limit")
    suspend fun dirty(limit: Int): List<CategoryEntity>

    @Upsert
    suspend fun upsert(entity: CategoryEntity)

    @Query("DELETE FROM categories WHERE id = :id")
    suspend fun deleteHard(id: String)

    @Query("UPDATE categories SET dirty = 0, serverVersion = :version WHERE id = :id AND updatedAt = :updatedAt")
    suspend fun markSynced(id: String, version: Long, updatedAt: Long): Int

    @Query("UPDATE categories SET serverVersion = :version WHERE id = :id")
    suspend fun setServerVersion(id: String, version: Long)

    @Query("UPDATE categories SET dirty = 1, serverVersion = 0")
    suspend fun markAllDirtyForResync()

    @Query("DELETE FROM categories WHERE deletedAt IS NOT NULL AND dirty = 0")
    suspend fun purgeSyncedTombstones()

    @Query("DELETE FROM categories")
    suspend fun clear()
}

@Dao
interface PreferenceDao {
    @Query("SELECT * FROM synced_preferences WHERE deletedAt IS NULL")
    fun observeAll(): Flow<List<PreferenceEntity>>

    @Query("SELECT * FROM synced_preferences WHERE `key` = :key")
    suspend fun get(key: String): PreferenceEntity?

    @Query("SELECT * FROM synced_preferences")
    suspend fun everything(): List<PreferenceEntity>

    @Query("SELECT * FROM synced_preferences WHERE dirty = 1 LIMIT :limit")
    suspend fun dirty(limit: Int): List<PreferenceEntity>

    @Upsert
    suspend fun upsert(entity: PreferenceEntity)

    @Query("DELETE FROM synced_preferences WHERE `key` = :key")
    suspend fun deleteHard(key: String)

    @Query("UPDATE synced_preferences SET dirty = 0, serverVersion = :version WHERE `key` = :key AND updatedAt = :updatedAt")
    suspend fun markSynced(key: String, version: Long, updatedAt: Long): Int

    @Query("UPDATE synced_preferences SET serverVersion = :version WHERE `key` = :key")
    suspend fun setServerVersion(key: String, version: Long)

    @Query("UPDATE synced_preferences SET dirty = 1, serverVersion = 0")
    suspend fun markAllDirtyForResync()

    @Query("DELETE FROM synced_preferences")
    suspend fun clear()
}

@Dao
interface ServiceUsageDao {
    @Query("SELECT * FROM service_usage ORDER BY lastUsedAt DESC LIMIT :limit")
    fun observeRecent(limit: Int): Flow<List<ServiceUsageEntity>>

    @Query("SELECT * FROM service_usage ORDER BY useCount DESC, lastUsedAt DESC LIMIT :limit")
    fun observeFrequent(limit: Int): Flow<List<ServiceUsageEntity>>

    @Query("SELECT * FROM service_usage WHERE serviceId = :id")
    suspend fun get(id: String): ServiceUsageEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun put(entity: ServiceUsageEntity)
}

@Dao
interface MetaDao {
    @Query("SELECT value FROM meta WHERE `key` = :key")
    suspend fun get(key: String): String?

    @Query("SELECT value FROM meta WHERE `key` = :key")
    fun observe(key: String): Flow<String?>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun put(entity: MetaEntity)

    @Query("DELETE FROM meta WHERE `key` = :key")
    suspend fun remove(key: String)
}

suspend fun MetaDao.set(key: String, value: String) = put(MetaEntity(key, value))

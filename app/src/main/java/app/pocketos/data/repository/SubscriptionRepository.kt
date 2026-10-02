package app.pocketos.data.repository

import app.pocketos.core.AppClock
import app.pocketos.data.local.DatabaseManager
import app.pocketos.data.local.ServiceUsageEntity
import app.pocketos.data.local.SubscriptionEntity
import app.pocketos.data.local.iso
import app.pocketos.data.local.toDomain
import app.pocketos.data.local.toEntity
import app.pocketos.domain.model.BillingCycle
import app.pocketos.domain.model.Subscription
import app.pocketos.domain.model.SubscriptionStatus
import app.pocketos.domain.recurrence.BillingCalculator
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import java.time.Instant
import java.util.UUID

@OptIn(ExperimentalCoroutinesApi::class)
class SubscriptionRepository(
    private val databases: DatabaseManager,
    private val clock: AppClock,
    private val effects: SideEffects,
) {
    private val dao get() = databases.current.subscriptions()

    val subscriptions: Flow<List<Subscription>> = databases.active.flatMapLatest { it.db.subscriptions().observeAll() }
        .map { list -> list.map { it.toDomain() } }

    fun observe(id: String): Flow<Subscription?> = databases.active.flatMapLatest { it.db.subscriptions().observe(id) }
        .map { it?.toDomain() }

    /** Recently and frequently used catalog services (local only). */
    val recentServiceIds: Flow<List<String>> = databases.active.flatMapLatest { it.db.serviceUsage().observeRecent(8) }
        .map { list -> list.map { it.serviceId } }
    val frequentServiceIds: Flow<List<String>> = databases.active.flatMapLatest { it.db.serviceUsage().observeFrequent(8) }
        .map { list -> list.map { it.serviceId } }

    suspend fun get(id: String): Subscription? = dao.get(id)?.takeIf { it.deletedAt == null }?.toDomain()

    fun newId(): String = UUID.randomUUID().toString()

    suspend fun save(subscription: Subscription): Subscription {
        val existing = dao.get(subscription.id)
        val now = clock.now()
        val saved = subscription.copy(
            name = subscription.name.trim().take(120),
            notes = subscription.notes?.trim()?.take(5000)?.ifEmpty { null },
            cancellationUrl = subscription.cancellationUrl?.trim()?.ifEmpty { null },
            createdAt = existing?.let { Instant.ofEpochMilli(it.createdAt) } ?: now,
            updatedAt = now,
        )
        dao.upsert(saved.toEntity(existing, dirty = true))
        saved.serviceId?.let { recordServiceUse(it) }
        effects.onSubscriptionChanged(saved.id, deleted = false)
        effects.onDataChanged()
        return saved
    }

    suspend fun setStatus(id: String, status: SubscriptionStatus): SubscriptionEntity? {
        val entity = dao.get(id)?.takeIf { it.deletedAt == null } ?: return null
        dao.upsert(entity.toDomain().copy(status = status, updatedAt = clock.now()).toEntity(entity, dirty = true))
        effects.onSubscriptionChanged(id, deleted = false)
        effects.onDataChanged()
        return entity
    }

    /** Postpones the next renewal reminder notification only (not the renewal). */
    suspend fun duplicate(id: String): Subscription? {
        val source = get(id) ?: return null
        val now = clock.now()
        return save(source.copy(id = newId(), name = source.name, createdAt = now, updatedAt = now))
    }

    suspend fun delete(id: String): SubscriptionEntity? {
        val entity = dao.get(id)?.takeIf { it.deletedAt == null } ?: return null
        if (entity.serverVersion == 0L) {
            dao.deleteHard(id)
        } else {
            val now = clock.nowMs()
            dao.upsert(entity.copy(deletedAt = now, updatedAt = now, dirty = true))
        }
        effects.onSubscriptionChanged(id, deleted = true)
        effects.onDataChanged()
        return entity
    }

    suspend fun restore(previous: SubscriptionEntity) {
        val existing = dao.get(previous.id)
        dao.upsert(previous.copy(deletedAt = null, updatedAt = clock.nowMs(), dirty = true, serverVersion = existing?.serverVersion ?: previous.serverVersion))
        effects.onSubscriptionChanged(previous.id, deleted = false)
        effects.onDataChanged()
    }

    /**
     * Moves renewal dates that have passed to the next upcoming renewal for
     * active subscriptions. Returns how many were updated.
     */
    suspend fun rollForwardRenewals(): Int {
        val today = clock.today()
        var changed = 0
        for (entity in dao.allActive()) {
            val sub = entity.toDomain()
            if (sub.status != SubscriptionStatus.ACTIVE) continue
            val next = BillingCalculator.rolledForward(sub, today)
            if (next != sub.nextRenewal) {
                dao.upsert(entity.copy(nextRenewalDate = next.iso(), updatedAt = clock.nowMs(), dirty = true))
                effects.onSubscriptionChanged(entity.id, deleted = false)
                changed++
            }
        }
        if (changed > 0) effects.onDataChanged()
        return changed
    }

    /** Billing cycles of the user's recent subscriptions (for smart suggestions). */
    suspend fun recentBillingCycles(limit: Int = 10): List<BillingCycle> =
        dao.allActive().sortedByDescending { it.createdAt }.take(limit).map { it.toDomain().billing }

    suspend fun recentCurrencies(limit: Int = 10): List<String> =
        dao.allActive().filter { it.amountMinor != null }.sortedByDescending { it.createdAt }.take(limit).map { it.currency }

    private suspend fun recordServiceUse(serviceId: String) {
        val usage = databases.current.serviceUsage()
        val current = usage.get(serviceId)
        usage.put(ServiceUsageEntity(serviceId, (current?.useCount ?: 0) + 1, clock.nowMs()))
    }

    suspend fun recordServicePicked(serviceId: String) = recordServiceUse(serviceId)
}

package app.pocketos.data.repository

import app.pocketos.core.AppClock
import app.pocketos.data.local.CategoryEntity
import app.pocketos.data.local.DatabaseManager
import app.pocketos.data.local.toDomain
import app.pocketos.domain.model.CategoryKind
import app.pocketos.domain.model.CustomCategory
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import java.util.UUID

@OptIn(ExperimentalCoroutinesApi::class)
class CategoryRepository(
    private val databases: DatabaseManager,
    private val clock: AppClock,
    private val effects: SideEffects,
) {
    val custom: Flow<List<CustomCategory>> = databases.active.flatMapLatest { it.db.categories().observeAll() }
        .map { list -> list.map { it.toDomain() } }

    fun custom(kind: CategoryKind): Flow<List<CustomCategory>> = custom.map { list -> list.filter { it.kind == kind } }

    suspend fun create(kind: CategoryKind, name: String, color: String?, icon: String?): CustomCategory {
        val now = clock.nowMs()
        val entity = CategoryEntity(
            id = UUID.randomUUID().toString(),
            kind = kind.wire,
            name = name.trim().take(60),
            color = color,
            icon = icon,
            createdAt = now,
            updatedAt = now,
            deletedAt = null,
            dirty = true,
        )
        databases.current.categories().upsert(entity)
        effects.onDataChanged()
        return entity.toDomain()
    }

    suspend fun rename(id: String, name: String) {
        val dao = databases.current.categories()
        val entity = dao.get(id) ?: return
        dao.upsert(entity.copy(name = name.trim().take(60), updatedAt = clock.nowMs(), dirty = true))
        effects.onDataChanged()
    }

    suspend fun delete(id: String) {
        val dao = databases.current.categories()
        val entity = dao.get(id) ?: return
        if (entity.serverVersion == 0L) dao.deleteHard(id) else dao.upsert(entity.copy(deletedAt = clock.nowMs(), updatedAt = clock.nowMs(), dirty = true))
        effects.onDataChanged()
    }
}

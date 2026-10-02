package app.pocketos.data.repository

import android.content.Context
import app.pocketos.domain.catalog.ServiceCatalog
import app.pocketos.domain.catalog.ServiceMatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext

/**
 * Service catalog: Bundled directly with the app in assets/service_catalog.json.
 *
 * Privacy-First & Local-First:
 * - 100+ popular global and regional services (streaming, utilities, software, fitness, etc.)
 * - Icons are validated vector paths embedded in the catalog.
 * - Works 100% offline with zero server calls.
 */
class CatalogRepository(private val context: Context) {
    private val _catalog = MutableStateFlow(ServiceCatalog.EMPTY)
    val catalog: StateFlow<ServiceCatalog> = _catalog.asStateFlow()

    private val _matcher = MutableStateFlow(ServiceMatcher(ServiceCatalog.EMPTY))
    val matcher: StateFlow<ServiceMatcher> = _matcher.asStateFlow()

    suspend fun load() = withContext(Dispatchers.IO) {
        val bundled = runCatching {
            context.assets.open("service_catalog.json").bufferedReader().use { it.readText() }
        }.getOrNull()?.let(ServiceCatalog::parse) ?: ServiceCatalog.EMPTY
        publish(bundled)
    }

    private fun publish(catalog: ServiceCatalog) {
        _catalog.value = catalog
        _matcher.value = ServiceMatcher(catalog)
    }
}

package app.pocketos.domain.catalog

import app.pocketos.domain.model.BillingCycle
import app.pocketos.domain.model.BillingUnit
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

@Serializable
data class CatalogFile(
    val version: Long,
    @SerialName("generated_at") val generatedAt: String? = null,
    @SerialName("icon_source") val iconSource: IconSource? = null,
    val categories: List<CatalogCategory> = emptyList(),
    val services: List<ServiceInfo> = emptyList(),
)

@Serializable
data class IconSource(val name: String, val version: String? = null, val license: String? = null, val url: String? = null, val notice: String? = null)

@Serializable
data class CatalogCategory(val id: String, val name: String, val icon: String? = null)

@Serializable
data class ServiceIconData(val path: String, val viewbox: Int = 24, val source: String? = null)

@Serializable
data class BillingHint(val unit: String, val interval: Int) {
    fun toCycle(): BillingCycle? = runCatching { BillingCycle(BillingUnit.fromWire(unit), interval) }.getOrNull()
}

@Serializable
data class ServiceInfo(
    val id: String,
    val name: String,
    val aliases: List<String> = emptyList(),
    val category: String = "other",
    val website: String? = null,
    val color: String? = null,
    val icon: ServiceIconData? = null,
    val billing: BillingHint? = null,
    val regions: List<String> = emptyList(),
    val keywords: List<String> = emptyList(),
    val generic: Boolean = false,
)

/**
 * Validated, immutable service catalog. Entries that fail validation are
 * dropped, so a corrupted or tampered remote catalog can never inject
 * malformed icon data or colours into the UI.
 */
class ServiceCatalog private constructor(
    val version: Long,
    val services: List<ServiceInfo>,
    val categories: List<CatalogCategory>,
    val iconSource: IconSource?,
) {
    private val byId: Map<String, ServiceInfo> = services.associateBy { it.id }

    fun find(id: String?): ServiceInfo? = id?.let { byId[it] }

    companion object {
        private val ID = Regex("^[a-z0-9_]{2,64}$")
        private val COLOR = Regex("^#[0-9A-Fa-f]{6}$")
        private val PATH = Regex("^[MmZzLlHhVvCcSsQqTtAa0-9eE.,\\-\\s]+$")
        private val json = Json { ignoreUnknownKeys = true; isLenient = false }

        val EMPTY = ServiceCatalog(0, emptyList(), emptyList(), null)

        fun parse(text: String): ServiceCatalog? = runCatching {
            from(json.decodeFromString(CatalogFile.serializer(), text))
        }.getOrNull()

        fun from(file: CatalogFile): ServiceCatalog {
            val services = file.services.mapNotNull { s ->
                if (!ID.matches(s.id) || s.name.isBlank() || s.name.length > 80) return@mapNotNull null
                s.copy(
                    name = s.name.trim(),
                    aliases = s.aliases.filter { it.isNotBlank() && it.length <= 60 }.take(20),
                    keywords = s.keywords.filter { it.isNotBlank() && it.length <= 40 }.take(20),
                    color = s.color?.takeIf { COLOR.matches(it) },
                    icon = s.icon?.takeIf { it.viewbox in 1..1024 && it.path.length <= 12_000 && PATH.matches(it.path) },
                    website = s.website?.takeIf { it.length <= 200 && !it.contains("://") && !it.contains(' ') },
                )
            }.distinctBy { it.id }
            val categories = file.categories.filter { ID.matches(it.id) }
            return ServiceCatalog(file.version, services, categories, file.iconSource)
        }
    }
}

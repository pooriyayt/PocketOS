package app.pocketos.domain

import app.pocketos.domain.catalog.ServiceCatalog
import java.io.File

/** Loads the real bundled catalog so tests exercise production data. */
object TestCatalog {
    val catalog: ServiceCatalog by lazy {
        val candidates = listOf(File("src/main/assets/service_catalog.json"), File("app/src/main/assets/service_catalog.json"))
        val file = candidates.first { it.exists() }
        ServiceCatalog.parse(file.readText()) ?: error("catalog failed to parse")
    }
}

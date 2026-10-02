package app.pocketos.domain

import app.pocketos.domain.catalog.CatalogFile
import app.pocketos.domain.catalog.ServiceCatalog
import app.pocketos.domain.catalog.ServiceIconData
import app.pocketos.domain.catalog.ServiceInfo
import app.pocketos.domain.catalog.ServiceMatcher
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ServiceMatcherTest {

    private val matcher = ServiceMatcher(TestCatalog.catalog)

    @Test
    fun catalogLoadsWithIcons() {
        val catalog = TestCatalog.catalog
        assertTrue(catalog.services.size > 100)
        assertNotNull(catalog.find("netflix")?.icon)
        assertNotNull(catalog.find("cloudflare")?.icon)
        assertEquals("entertainment", catalog.find("netflix")?.category)
    }

    @Test
    fun recognizesExactNamesAndAliases() {
        assertEquals("netflix", matcher.recognize("Netflix")?.id)
        assertEquals("spotify", matcher.recognize("spotify")?.id)
        assertEquals("cloudflare", matcher.recognize("Cloudflare")?.id)
        assertEquals("youtube_premium", matcher.recognize("YouTube Premium")?.id)
        assertEquals("chatgpt", matcher.recognize("chat gpt")?.id)
        assertEquals("microsoft_365", matcher.recognize("Office 365")?.id)
        assertEquals("internet", matcher.recognize("اینترنت")?.id)
        assertNull(matcher.recognize("my grandmother's bakery"))
    }

    @Test
    fun searchSupportsPrefixKeywordsAndTypos() {
        assertEquals("netflix", matcher.search("Net").first().service.id)
        assertEquals("spotify", matcher.search("spot").first().service.id)
        assertTrue(matcher.search("music").take(8).any { it.service.id == "spotify" })
        assertEquals("netflix", matcher.search("netflx").first().service.id)
        assertEquals("github", matcher.search("githb").first().service.id)
        assertTrue(matcher.search("").isEmpty())
    }

    @Test
    fun findsServiceInsideSentences() {
        assertEquals("netflix", matcher.findIn("pay my netflix bill")?.service?.id)
        assertEquals("domain_renewal", matcher.findIn("renew my domain")?.service?.id)
        // Specific brands beat generic matches.
        assertEquals("hetzner", matcher.findIn("hetzner server")?.service?.id)
    }

    @Test
    fun invalidCatalogEntriesAreDropped() {
        val file = CatalogFile(
            version = 1,
            services = listOf(
                ServiceInfo(id = "ok", name = "Ok", color = "#112233", icon = ServiceIconData("M0 0h24v24H0z")),
                ServiceInfo(id = "Bad Id", name = "Bad"),
                ServiceInfo(id = "evil", name = "Evil", color = "red;", icon = ServiceIconData("<script>")),
            ),
        )
        val catalog = ServiceCatalog.from(file)
        assertEquals(listOf("ok", "evil"), catalog.services.map { it.id })
        assertNull(catalog.find("evil")?.icon)
        assertNull(catalog.find("evil")?.color)
    }
}

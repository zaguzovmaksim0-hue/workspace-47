package dev.junta.firmamobile.browser

import java.net.URI
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SiteDataCleanerTest {
    @Test
    fun currentSiteNeverFallsBackToGlobalDeletionWhenCookieInfoIsUnavailable() {
        val cookies = FakeCookieStore()
        val storage = FakeSiteWebStorage()
        val cleaner = SiteDataCleaner(cookies, storage)

        val result = cleaner.clearOrigin(
            URI("https://tramita.unizar.es/private/path?ticket=redacted"),
            capabilities(getCookieInfo = false),
        )

        assertEquals(SiteClearResult.WEB_STORAGE_CLEARED_COOKIE_CLEAR_UNAVAILABLE, result)
        assertEquals(listOf("https://tramita.unizar.es"), storage.deletedOrigins)
        assertEquals(0, storage.deleteAllCalls)
        assertEquals(0, cookies.removeAllCalls)
        assertTrue(cookies.infoUrls.isEmpty())
        assertTrue(cookies.writes.isEmpty())
    }

    @Test
    fun supportedCookieInfoExpiresOnlyNamesAtTheirExactDomainAndPath() {
        val secretOne = "secret-one"
        val secretTwo = "secret-two"
        val cookies = FakeCookieStore(
            cookieInfo = listOf(
                "SESSION=$secretOne; Domain=tramita.unizar.es; Path=/private; Secure; HttpOnly",
                "PREF=$secretTwo; Path=/; SameSite=Lax",
            ),
        )
        val storage = FakeSiteWebStorage()
        val cleaner = SiteDataCleaner(cookies, storage)

        val result = cleaner.clearOrigin(
            URI("https://tramita.unizar.es/private/path?ticket=redacted#fragment"),
            capabilities(getCookieInfo = true),
        )

        assertEquals(SiteClearResult.CLEARED_EXACTLY, result)
        assertEquals(listOf("https://tramita.unizar.es"), storage.deletedOrigins)
        assertEquals(
            listOf("https://tramita.unizar.es/private/path"),
            cookies.infoUrls,
        )
        assertEquals(2, cookies.writes.size)
        val expiryHeaders = cookies.writes.map { it.second }
        assertTrue(expiryHeaders.any { it.startsWith("SESSION=;") && "Domain=tramita.unizar.es" in it && "Path=/private" in it })
        assertTrue(expiryHeaders.any { it.startsWith("PREF=;") && "Path=/" in it })
        assertTrue(expiryHeaders.all { "Max-Age=0" in it && "Expires=Thu, 01 Jan 1970 00:00:00 GMT" in it })
        assertTrue(expiryHeaders.none { secretOne in it || secretTwo in it })
        assertEquals(1, cookies.flushCalls)
        assertEquals(0, cookies.removeAllCalls)
        assertEquals(0, storage.deleteAllCalls)
    }

    @Test
    fun malformedCookieMetadataLeavesCookiesUntouchedAndReportsLimitedClear() {
        val cookies = FakeCookieStore(
            cookieInfo = listOf("SESSION=secret; Domain=unizar.es; Path=/"),
        )
        val storage = FakeSiteWebStorage()
        val cleaner = SiteDataCleaner(cookies, storage)

        val result = cleaner.clearOrigin(
            URI("https://tramita.unizar.es/private"),
            capabilities(getCookieInfo = true),
        )

        assertEquals(SiteClearResult.WEB_STORAGE_CLEARED_COOKIE_CLEAR_UNAVAILABLE, result)
        assertTrue(cookies.writes.isEmpty())
        assertEquals(0, cookies.flushCalls)
        assertEquals(0, cookies.removeAllCalls)
    }

    @Test
    fun invalidOriginOrStorageFailureFailsWithoutCookieOrGlobalOperations() {
        val cookies = FakeCookieStore()
        val invalidStorage = FakeSiteWebStorage(failDeleteOrigin = true)
        val cleaner = SiteDataCleaner(cookies, invalidStorage)

        assertEquals(
            SiteClearResult.FAILED,
            cleaner.clearOrigin(URI("http://tramita.unizar.es/private"), capabilities(true)),
        )
        assertEquals(
            SiteClearResult.FAILED,
            cleaner.clearOrigin(URI("https://tramita.unizar.es/private"), capabilities(true)),
        )
        assertTrue(cookies.infoUrls.isEmpty())
        assertTrue(cookies.writes.isEmpty())
        assertEquals(0, cookies.removeAllCalls)
        assertEquals(0, invalidStorage.deleteAllCalls)
    }

    @Test
    fun globalDeletionRunsOnlyThroughTheSeparatelyConfirmedMethod() {
        val cookies = FakeCookieStore(removeAllResult = true)
        val storage = FakeSiteWebStorage()
        val cleaner = SiteDataCleaner(cookies, storage)
        var callback: Boolean? = null

        cleaner.clearAllConfirmed { callback = it }

        assertEquals(1, cookies.removeAllCalls)
        assertEquals(1, storage.deleteAllCalls)
        assertEquals(1, cookies.flushCalls)
        assertEquals(true, callback)
    }

    private fun capabilities(getCookieInfo: Boolean) = WebViewProfileCapabilities(
        providerPackage = "test.webview",
        providerVersion = "1",
        multiProfile = false,
        getCookieInfo = getCookieInfo,
        webMessageListener = true,
        documentStartScript = true,
    )

    private class FakeSiteWebStorage(
        private val failDeleteOrigin: Boolean = false,
    ) : SiteWebStorage {
        val deletedOrigins = mutableListOf<String>()
        var deleteAllCalls = 0

        override fun deleteOrigin(origin: String) {
            if (failDeleteOrigin) error("storage unavailable")
            deletedOrigins += origin
        }

        override fun deleteAllData() {
            deleteAllCalls += 1
        }
    }

    private class FakeCookieStore(
        private val cookieInfo: List<String> = emptyList(),
        private val removeAllResult: Boolean = true,
    ) : WebCookieStore {
        val infoUrls = mutableListOf<String>()
        val writes = mutableListOf<Pair<String, String>>()
        var flushCalls = 0
        var removeAllCalls = 0

        override fun getCookie(url: String): String? = null

        override fun getCookieInfo(url: String): List<String> {
            infoUrls += url
            return cookieInfo
        }

        override fun setCookie(url: String, value: String) {
            writes += url to value
        }

        override fun flush() {
            flushCalls += 1
        }

        override fun removeAllCookies(callback: (Boolean) -> Unit) {
            removeAllCalls += 1
            callback(removeAllResult)
        }
    }
}

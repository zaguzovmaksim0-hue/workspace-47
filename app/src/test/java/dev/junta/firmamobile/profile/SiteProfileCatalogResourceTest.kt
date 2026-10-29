package dev.junta.firmamobile.profile

import dev.junta.firmamobile.BuildConfig
import java.security.MessageDigest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertThrows
import org.junit.Test

class SiteProfileCatalogResourceTest {
    private val resourcePath = "dev/junta/firmamobile/profile/site_profiles_v1.json"

    private fun resourceBytes(): ByteArray =
        SiteProfileCatalogResource::class.java.classLoader
            ?.getResourceAsStream(resourcePath)
            ?.use { it.readBytes() }
            ?: throw AssertionError("Missing site profile catalog resource")

    private fun sha256(bytes: ByteArray): String =
        MessageDigest.getInstance("SHA-256").digest(bytes)
            .joinToString(separator = "") { "%02x".format(it) }

    @Test
    fun `resource matches build config digest`() {
        val bytes = resourceBytes()
        assertEquals(BuildConfig.SITE_PROFILE_CATALOG_SHA256, sha256(bytes))
    }

    @Test
    fun `built in catalog is cached and parses`() {
        val catalog = BuiltInSiteProfiles.catalog
        assertSame(catalog, BuiltInSiteProfiles.catalog)
        assertEquals(catalog, SiteProfileCatalogParser.parse(BuiltInSiteProfiles.JSON))
        assertNotNull(BuiltInSiteProfiles.releaseRegistry)
        assertNotNull(BuiltInSiteProfiles.qaRegistry)
    }

    @Test
    fun `tampered bytes are rejected`() {
        val originalBytes = resourceBytes()
        val expectedSha256 = sha256(originalBytes)
        val tamperedBytes = originalBytes.clone()
        tamperedBytes[0] = (tamperedBytes[0].toInt() xor 1).toByte()
        assertThrows(IllegalStateException::class.java) {
            SiteProfileCatalogResource.decode(tamperedBytes, expectedSha256)
        }
    }

    @Test
    fun `oversized bytes are rejected`() {
        val bytes = ByteArray(1_048_577) { 'a'.code.toByte() }
        assertThrows(IllegalStateException::class.java) {
            SiteProfileCatalogResource.decode(bytes, sha256(bytes))
        }
    }

    @Test
    fun `invalid utf-8 is rejected when digest matches`() {
        val bytes = byteArrayOf(0xC3.toByte(), 0x28)
        assertThrows(IllegalStateException::class.java) {
            SiteProfileCatalogResource.decode(bytes, sha256(bytes))
        }
    }

    @Test
    fun `empty bytes are rejected`() {
        assertThrows(IllegalStateException::class.java) {
            SiteProfileCatalogResource.decode(ByteArray(0), sha256(ByteArray(0)))
        }
    }
}

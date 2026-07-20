package dev.junta.firmamobile.browser

import android.os.Bundle
import dev.junta.firmamobile.profile.BuiltInSiteProfiles
import dev.junta.firmamobile.profile.ProfileId
import java.net.URI
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.ConscryptMode
import org.robolectric.annotation.GraphicsMode
import org.robolectric.annotation.SQLiteMode

@RunWith(RobolectricTestRunner::class)
@ConscryptMode(ConscryptMode.Mode.OFF)
@GraphicsMode(GraphicsMode.Mode.LEGACY)
@SQLiteMode(SQLiteMode.Mode.LEGACY)
class BrowserSessionStatePolicyTest {
    @Test
    fun removesOnlyLegacyRawWebViewHistoryFromSavedInstanceState() {
        val saved = Bundle().apply {
            putBundle(
                BrowserSessionStatePolicy.LEGACY_WEBVIEW_HISTORY_KEY,
                Bundle().apply { putString("token-canary", "secret") },
            )
            putString("unrelated", "preserved")
        }

        BrowserSessionStatePolicy.discardLegacyWebViewState(saved)

        assertFalse(saved.containsKey(BrowserSessionStatePolicy.LEGACY_WEBVIEW_HISTORY_KEY))
        assertEquals("preserved", saved.getString("unrelated"))
    }

    @Test
    fun legacyDiscardIsIdempotentForNullAndEmptyState() {
        BrowserSessionStatePolicy.discardLegacyWebViewState(null)
        val empty = Bundle()

        BrowserSessionStatePolicy.discardLegacyWebViewState(empty)
        BrowserSessionStatePolicy.discardLegacyWebViewState(empty)

        assertEquals(0, empty.size())
    }

    @Test
    fun acceptsOnlyASelectedProfilesTrustedCatalogEntry() {
        val junta = ProfileId("junta-andalucia")
        val startUrl = BuiltInSiteProfiles.qaRegistry.profile(junta)!!.startUrl

        assertEquals(
            startUrl.toASCIIString(),
            BrowserSessionStatePolicy.validatedEntryUrl(
                registry = BuiltInSiteProfiles.qaRegistry,
                profileId = junta,
                entryUrl = startUrl,
            ),
        )
    }

    @Test
    fun rejectsNonHttpsUserInfoNonDefaultPortAndFragmentEntries() {
        val junta = ProfileId("junta-andalucia")
        listOf(
            "http://www.juntadeandalucia.es/path",
            "https://user@www.juntadeandalucia.es/path",
            "https://www.juntadeandalucia.es:8443/path",
            "https://www.juntadeandalucia.es/path#fragment",
        ).forEach { rawUrl ->
            assertNull(
                BrowserSessionStatePolicy.validatedEntryUrl(
                    registry = BuiltInSiteProfiles.qaRegistry,
                    profileId = junta,
                    entryUrl = URI(rawUrl),
                ),
            )
        }
    }

    @Test
    fun rejectsCrossProfileUnknownAndClientAuthRequestOrigins() {
        val junta = ProfileId("junta-andalucia")
        val carneJoven = ProfileId("carne-joven-andalucia")

        assertNull(
            BrowserSessionStatePolicy.validatedEntryUrl(
                registry = BuiltInSiteProfiles.qaRegistry,
                profileId = junta,
                entryUrl = BuiltInSiteProfiles.qaRegistry.profile(carneJoven)!!.startUrl,
            ),
        )
        assertNull(
            BrowserSessionStatePolicy.validatedEntryUrl(
                registry = BuiltInSiteProfiles.qaRegistry,
                profileId = junta,
                entryUrl = URI("https://unknown.example/path"),
            ),
        )
        assertNull(
            BrowserSessionStatePolicy.validatedEntryUrl(
                registry = BuiltInSiteProfiles.qaRegistry,
                profileId = carneJoven,
                entryUrl = URI("https://ws235.juntadeandalucia.es/authenticationFacade"),
            ),
        )
    }
}

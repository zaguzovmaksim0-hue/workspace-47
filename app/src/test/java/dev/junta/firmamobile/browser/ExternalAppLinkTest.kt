package dev.junta.firmamobile.browser

import android.content.Intent
import org.junit.Assert.*
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
class ExternalAppLinkTest {
    @Test fun ordinaryCustomLinkPreservesOnlyItsExactNavigationBytes() {
        val raw = "sampleauth://login/callback?state=a%2Fb&nonce=one+two#finish"
        val link = checkNotNull(ExternalAppLink.parse(raw))
        assertEquals(raw, link.uri.toString()); assertEquals("sampleauth", link.scheme)
        val intent = link.minimalIntent()
        assertEquals(Intent.ACTION_VIEW, intent.action)
        assertEquals(setOf(Intent.CATEGORY_BROWSABLE), intent.categories)
        assertEquals(raw, intent.dataString)
        assertNull(intent.extras); assertNull(intent.clipData); assertNull(intent.selector)
        assertNull(intent.component); assertNull(intent.`package`); assertEquals(0, intent.flags)
        assertNull(intent.type); assertNull(intent.identifier)
    }
    @Test fun chromeIntentUriIsRebuiltNotForwarded() {
        val link = checkNotNull(ExternalAppLink.parse("intent://start?state=abc#Intent;scheme=sampleauth;package=dev.synthetic.identity;action=android.intent.action.VIEW;category=android.intent.category.BROWSABLE;end"))
        assertEquals("sampleauth://start?state=abc", link.uri.toString())
        val request = link.minimalIntent()
        assertEquals("dev.synthetic.identity", request.`package`)
        assertNull(request.component); assertNull(request.selector); assertNull(request.extras)
        assertNull(request.clipData); assertEquals(0, request.flags)
    }
    @Test fun fallbackIsSeparateHttpsActionNotAnExtraSentToTheApplication() {
        val link = checkNotNull(ExternalAppLink.parse("intent://start#Intent;scheme=sampleauth;S.browser_fallback_url=https%3A%2F%2Ffallback.example%2Flogin%3Fstate%3Da%252Fb%2Bz;end"))
        assertEquals("https://fallback.example/login?state=a%2Fb+z", link.webFallback.toString())
        assertNull(link.minimalIntent().extras)
        assertEquals(link.webFallback, link.fallbackIntent()!!.data)
        assertNull(link.fallbackIntent()!!.`package`)
        assertEquals(setOf(Intent.CATEGORY_BROWSABLE), link.fallbackIntent()!!.categories)
    }
    @Test fun activeComponentsSelectorsFlagsAndExtrasAreNeverAccepted() {
        for (extra in listOf("component=dev.junta.firmamobile/.MainActivity", "launchFlags=0x3", "SEL", "action=android.intent.action.SEND", "category=android.intent.category.LAUNCHER", "S.payload=secret", "i.flags=3")) {
            assertNull(extra, ExternalAppLink.parse("intent://start#Intent;scheme=sampleauth;$extra;end"))
        }
    }
    @Test fun nativeAfirmaBrowserAndSystemSchemesStayOnExistingRoutes() {
        for (scheme in listOf("afirma", "autofirma", "http", "https", "file", "content", "javascript", "data", "blob", "about", "tel", "sms", "market", "package", "android-app", "chrome", "android.settings")) {
            assertNull(scheme, ExternalAppLink.parse("$scheme://example/path"))
            assertNull(scheme, ExternalAppLink.parse("intent://example#Intent;scheme=$scheme;end"))
        }
    }
    @Test fun ownPackageAndOfficialAutofirmaCannotBeSelectedThroughGenericRouting() {
        for (pkg in listOf("dev.junta.firmamobile", "es.gob.afirma", "not a package", "single", "dev..invalid")) {
            assertNull(ExternalAppLink.parse("intent://start#Intent;scheme=sampleauth;package=$pkg;end"))
        }
    }
    @Test fun unsafeFallbackNeverBecomesAutomaticAlternateNavigation() {
        for (target in listOf("http%3A%2F%2Ffallback.example", "https%3A%2F%2Fuser%40fallback.example", "https%3A%2F%2F127.0.0.1%2F", "intent%3A%2F%2Fagain", "https%3A%2F%2Ffallback.example%3A444")) {
            assertNull(ExternalAppLink.parse("intent://start#Intent;scheme=sampleauth;S.browser_fallback_url=$target;end"))
        }
    }
    @Test fun duplicateOrMalformedDirectivesAreRejectedRatherThanAmbiguouslyChosen() {
        for (suffix in listOf("scheme=sampleauth;scheme=other;end", "scheme=sampleauth;package=dev.one;package=dev.two;end", "scheme=sampleauth;component=;end", "scheme=sampleauth;end;", "scheme=sampleauth", ";scheme=sampleauth;end")) {
            assertNull(suffix, ExternalAppLink.parse("intent://start#Intent;$suffix"))
        }
        assertNull(ExternalAppLink.parse("intent://start#Intent;scheme=sampleauth;S.browser_fallback_url=%GG;end"))
    }
    @Test fun queryIsNotPrintedByRequestDescription() {
        val request = checkNotNull(ExternalAppLink.parse("sampleauth://login?password=private&state=secret#token"))
        assertFalse(request.toString().contains("private")); assertFalse(request.toString().contains("secret"))
        assertFalse(request.toString().contains("password")); assertFalse(request.toString().contains("token"))
        assertTrue(request.toString().contains("sampleauth"))
    }
    @Test fun inputControlsCredentialsAndOversizeAreRejectedWithoutTruncation() {
        for (raw in listOf("sampleauth://user:password@host/", " sampleauth://host", "sampleauth://host\n", "sampleauth://host\\other", "sampleauth://host/%0dheader", "sampleauth://host/%00x", "sampleauth://host/%GG", "sampleauth://host\u202ehidden", "sampleauth://host/" + "x".repeat(16_384))) {
            assertNull(raw.take(90), ExternalAppLink.parse(raw))
        }
    }
}

package dev.junta.firmamobile.network

import android.net.Uri
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
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
class JuntaOriginPolicyTest {
    @Test
    fun allowsOnlyTheSixExactHttpsHostsOnTheDefaultPort() {
        val expectedHosts = setOf(
            "www.juntadeandalucia.es",
            "sede.juntadeandalucia.es",
            "ssoweb.juntadeandalucia.es",
            "pfirma.juntadeandalucia.es",
            "ws024.juntadeandalucia.es",
            "ws050.juntadeandalucia.es",
        )

        assertEquals(expectedHosts, JuntaOriginPolicy.allowedHosts)
        expectedHosts.forEach { host ->
            assertTrue(JuntaOriginPolicy.isAllowed(Uri.parse("https://$host/path")))
            assertTrue(JuntaOriginPolicy.isAllowed(Uri.parse("https://$host:443/path")))
        }
        assertEquals(
            expectedHosts.mapTo(linkedSetOf()) { "https://$it" },
            JuntaOriginPolicy.webMessageOriginRules,
        )
    }

    @Test
    fun rejectsHttpUserInfoNonDefaultPortsAndLookalikeHosts() {
        val rejected = listOf(
            "http://www.juntadeandalucia.es/",
            "https://user@www.juntadeandalucia.es/",
            "https://www.juntadeandalucia.es:8443/",
            "https://www.juntadeandalucia.es.evil.example/",
            "https://juntadeandalucia.es/",
            "https://www.juntadeandalucia.es./",
            "https://xn--juntadeandaluca-2nb.example/",
            "https://127.0.0.1/",
        )

        rejected.forEach { rawUrl ->
            assertFalse(rawUrl, JuntaOriginPolicy.isAllowed(Uri.parse(rawUrl)))
            assertNull(rawUrl, JuntaOriginPolicy.originFor(Uri.parse(rawUrl)))
        }
    }

    @Test
    fun serializesCanonicalOriginWithoutPathQueryOrFragment() {
        val origin = JuntaOriginPolicy.originFor(
            Uri.parse("https://WWW.JUNTADEANDALUCIA.ES:443/a?b=c#fragment"),
        )

        assertEquals(
            TrustedOrigin("https", "www.juntadeandalucia.es", 443),
            origin,
        )
        assertEquals("https://www.juntadeandalucia.es", origin?.serialized)
    }
}

package dev.junta.firmamobile

import dev.junta.firmamobile.profile.ProfileId
import java.net.URI
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class AppBrowserSessionRegistryTest {
    @Test
    fun createdSessionResolvesByOpaqueId() {
        val registry = AppBrowserSessionRegistry()
        val entryUrl = URI("https://public.example/")
        val sessionId = registry.create(profileId = null, entryUrl = entryUrl)

        assertEquals(AppBrowserSession(null, entryUrl), registry.resolve(sessionId))
    }

    @Test
    fun unknownSessionIdFailsClosed() {
        val registry = AppBrowserSessionRegistry()

        assertNull(registry.resolve("missing-session"))
    }

    @Test
    fun invalidatedSessionBecomesUnresolvable() {
        val registry = AppBrowserSessionRegistry()
        val sessionId = registry.create(null, URI("https://public.example/"))

        registry.invalidate(sessionId)

        assertNull(registry.resolve(sessionId))
    }

    @Test
    fun clearAllMakesEverySessionUnresolvable() {
        val registry = AppBrowserSessionRegistry()
        val first = registry.create(null, URI("https://public.example/one"))
        val second = registry.create(ProfileId("melilla-sede"), URI("https://sede.melilla.es/"))

        registry.clearAll()

        assertNull(registry.resolve(first))
        assertNull(registry.resolve(second))
    }

    @Test
    fun unknownProfileSessionFailsClosed() {
        val registry = AppBrowserSessionRegistry()
        val sessionId = registry.create(
            ProfileId("missing-profile"),
            URI("https://public.example/"),
        )

        assertNull(registry.resolve(sessionId))
    }
}

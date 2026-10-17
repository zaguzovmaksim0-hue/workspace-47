package dev.junta.firmamobile.profile

import dev.junta.firmamobile.BuildConfig
import org.junit.After
import org.junit.Assert.assertFalse
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Before
import org.junit.Test

class RuntimeProfileScopeTest {
    @Before
    fun resetScopeBeforeTest() {
        RuntimeProfileScope.resetForTest()
    }

    @After
    fun resetScopeAfterTest() {
        RuntimeProfileScope.resetForTest()
    }

    @Test
    fun `default scope keeps the existing build profile policy`() {
        val expectedRegistry = if (BuildConfig.ALLOW_QA_PROFILES || BuildConfig.ALLOW_UNVERIFIED_PROFILES) {
            BuiltInSiteProfiles.qaRegistry
        } else {
            BuiltInSiteProfiles.releaseRegistry
        }

        assertFalse(RuntimeProfileScope.verifiedOnly())
        assertSame(expectedRegistry, BuiltInSiteProfiles.runtimeRegistry)
    }

    @Test
    fun `verified only selects the release registry singleton`() {
        RuntimeProfileScope.configure(verifiedOnly = true)

        assertTrue(RuntimeProfileScope.verifiedOnly())
        assertSame(BuiltInSiteProfiles.releaseRegistry, BuiltInSiteProfiles.runtimeRegistry)
        assertSame(BuiltInSiteProfiles.runtimeRegistry, BuiltInSiteProfiles.runtimeRegistry)
    }

    @Test
    fun `scope configuration is immutable after its first value`() {
        RuntimeProfileScope.configure(verifiedOnly = false)
        RuntimeProfileScope.configure(verifiedOnly = false)

        try {
            RuntimeProfileScope.configure(verifiedOnly = true)
            fail("Expected conflicting scope configuration to fail")
        } catch (_: IllegalStateException) {
        }
    }
}

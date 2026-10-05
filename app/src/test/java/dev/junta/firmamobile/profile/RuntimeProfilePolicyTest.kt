package dev.junta.firmamobile.profile

import dev.junta.firmamobile.BuildConfig
import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Test

class RuntimeProfilePolicyTest {
    @Test
    fun publicUnverifiedProfilesDoNotEnableQaDiagnostics() {
        if (BuildConfig.BUILD_TYPE == "release") {
            assertEquals(true, BuildConfig.ALLOW_UNVERIFIED_PROFILES)
            assertEquals(false, BuildConfig.ALLOW_QA_PROFILES)
            assertEquals(false, BuildConfig.ENABLE_WEBVIEW_CONTENTS_DEBUGGING)
            assertEquals(false, BuildConfig.ENABLE_WS024_QA_TUNNEL)
        }
    }

    @Test
    fun debugAndQaVariantsUseQaRegistryWhileReleasePolicyRemainsExplicit() {
        val expected = if (BuildConfig.ALLOW_QA_PROFILES || BuildConfig.ALLOW_UNVERIFIED_PROFILES) {
            BuiltInSiteProfiles.qaRegistry
        } else {
            BuiltInSiteProfiles.releaseRegistry
        }

        assertSame(expected, BuiltInSiteProfiles.runtimeRegistry)
        assertEquals(
            BuildConfig.ALLOW_QA_PROFILES || BuildConfig.ALLOW_UNVERIFIED_PROFILES,
            BuiltInSiteProfiles.runtimeRegistry.profile(ProfileId("carne-joven-andalucia")) != null,
        )
        assertEquals(
            null,
            BuiltInSiteProfiles.releaseRegistry.profile(ProfileId("junta-andalucia")),
        )
        assertEquals(
            CompatibilityStatus.EXPERIMENTAL,
            BuiltInSiteProfiles.qaRegistry.profile(ProfileId("junta-andalucia"))
                ?.compatibilityStatus,
        )
        assertEquals(
            null,
            BuiltInSiteProfiles.releaseRegistry.profile(ProfileId("aeat-mis-datos-censales")),
        )
        assertEquals(
            CompatibilityStatus.VERIFIED_CONTRACT,
            BuiltInSiteProfiles.qaRegistry.profile(ProfileId("aeat-mis-datos-censales"))
                ?.compatibilityStatus,
        )
    }
}

package dev.junta.firmamobile.profile

import dev.junta.firmamobile.BuildConfig
import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Test

class RuntimeProfilePolicyTest {
    @Test
    fun debugAndQaVariantsUseQaRegistryWhileReleasePolicyRemainsExplicit() {
        val expected = if (BuildConfig.ALLOW_QA_PROFILES) {
            BuiltInSiteProfiles.qaRegistry
        } else {
            BuiltInSiteProfiles.releaseRegistry
        }

        assertSame(expected, BuiltInSiteProfiles.runtimeRegistry)
        assertEquals(
            BuildConfig.ALLOW_QA_PROFILES,
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
    }
}

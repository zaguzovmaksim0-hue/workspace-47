package dev.junta.firmamobile.network

import dev.junta.firmamobile.BuildConfig
import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SecureTunnelBuildConfigurationTest {
    @Test
    fun buildConfigContainsOnlyPublicTunnelConfigurationAndNeverACredential() {
        val fieldNames = BuildConfig::class.java.declaredFields.mapTo(linkedSetOf()) { it.name }
        assertTrue(REQUIRED_PUBLIC_FIELDS.all(fieldNames::contains))
        assertTrue(
            fieldNames.none { name ->
                val normalized = name.lowercase()
                "credential" in normalized || "token" in normalized || "secret" in normalized
            },
        )

        if (BuildConfig.ENABLE_WS024_QA_TUNNEL) {
            assertEquals("qa", BuildConfig.BUILD_TYPE)
            assertTrue(BuildConfig.WS024_QA_RELAY_HOST.isNotBlank())
            assertTrue(BuildConfig.WS024_QA_RELAY_PORT in 1..65535)
            assertTrue(parsedPins().size >= 2)
        } else {
            assertEquals("", BuildConfig.WS024_QA_RELAY_HOST)
            assertEquals(443, BuildConfig.WS024_QA_RELAY_PORT)
            assertEquals("", BuildConfig.WS024_QA_RELAY_SPKI_PINS)
        }
    }

    @Test
    fun debugAndReleaseArePinnedDirectOnlyWhileQaMayUseOnlyACompletePublicTuple() {
        val gradle = projectFile("app/build.gradle.kts").readText()
        for (field in REQUIRED_PUBLIC_FIELDS) {
            assertTrue("missing $field", gradle.contains("\"$field\""))
        }
        assertTrue(gradle.contains("debug {"))
        assertTrue(gradle.contains("release {"))
        assertTrue(gradle.contains("create(\"qa\")"))
        assertTrue(gradle.contains("ENABLE_WS024_QA_TUNNEL"))
        assertTrue(gradle.contains("JFM_WS024_QA_RELAY_HOST"))
        assertTrue(gradle.contains("JFM_WS024_QA_RELAY_PORT"))
        assertTrue(gradle.contains("JFM_WS024_QA_RELAY_SPKI_PINS"))
        assertFalse(gradle.contains("JFM_WS024_QA_CREDENTIAL"))
        assertFalse(gradle.contains("WS024_QA_CREDENTIAL"))
    }

    @Test
    fun releaseSourceContainsNeitherOneShotLoaderNorCredentialPath() {
        val mainSource = sourceTreeText("app/src/main")
        val releaseSource = sourceTreeText("app/src/release")
        val forbidden = listOf(
            "QaOneShotTunnelCredentialProvider",
            QaOneShotTunnelCredentialProvider.FILE_NAME,
            "JFM_WS024_QA_CREDENTIAL",
        )
        for (value in forbidden) {
            assertFalse("main leaks $value", mainSource.contains(value))
            assertFalse("release leaks $value", releaseSource.contains(value))
        }
        val debugProvider = projectFile(
            "app/src/debug/java/dev/junta/firmamobile/network/QaOneShotTunnelCredentialProvider.kt",
        )
        assertTrue(debugProvider.isFile)
        assertTrue(debugProvider.readText().contains(QaOneShotTunnelCredentialProvider.FILE_NAME))
    }

    @Test
    fun releaseFactoryIsDirectOnlyAndDoesNotReadTunnelBuildConfig() {
        val source = projectFile(
            "app/src/release/java/dev/junta/firmamobile/network/BuildVariantSecureTunnelRuntimeFactory.kt",
        ).readText()
        assertTrue(source.contains("DirectOnlyTunnelRuntime"))
        assertFalse(source.contains("BuildConfig"))
        assertFalse(source.contains("SecureTunnelPublicConfig"))
        assertFalse(source.contains("QaOneShotTunnelCredentialProvider"))
    }

    private fun parsedPins(): List<String> = BuildConfig.WS024_QA_RELAY_SPKI_PINS
        .split(',')
        .map(String::trim)
        .filter(String::isNotEmpty)
        .distinct()

    private fun projectFile(relative: String): File {
        var candidate: File? = File(requireNotNull(System.getProperty("user.dir"))).absoluteFile
        while (candidate != null) {
            val file = File(candidate, relative)
            if (file.exists()) return file
            candidate = candidate.parentFile
        }
        error("Project file not found: $relative")
    }

    private fun sourceTreeText(relative: String): String {
        val root = runCatching { projectFile(relative) }.getOrNull() ?: return ""
        if (!root.exists()) return ""
        return root.walkTopDown()
            .filter(File::isFile)
            .joinToString("\n") { it.readText() }
    }

    private companion object {
        val REQUIRED_PUBLIC_FIELDS = setOf(
            "ENABLE_WS024_QA_TUNNEL",
            "WS024_QA_RELAY_HOST",
            "WS024_QA_RELAY_PORT",
            "WS024_QA_RELAY_SPKI_PINS",
        )
    }
}

package dev.junta.firmamobile.network

import dev.junta.firmamobile.BuildConfig
import org.junit.Assert.assertTrue
import org.junit.Test

class BuildVariantSecureTunnelRuntimeFactoryTest {
    @Test
    fun variantFactoryMatchesOnlyTheCompiledPublicTuple() {
        val directory = java.nio.file.Files.createTempDirectory("ws024-runtime-factory").toFile()
        val runtime = BuildVariantSecureTunnelRuntimeFactory.create(directory)

        if (BuildConfig.ENABLE_WS024_QA_TUNNEL) {
            assertTrue(BuildConfig.BUILD_TYPE == "qa")
            assertTrue(runtime is QaSecureTunnelRuntime)
        } else {
            assertTrue(runtime is DirectOnlyTunnelRuntime)
        }
        directory.deleteRecursively()
    }
}

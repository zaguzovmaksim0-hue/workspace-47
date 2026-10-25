package dev.junta.firmamobile.browser

import android.content.Context
import android.content.res.Resources
import androidx.test.core.app.ApplicationProvider
import dev.junta.firmamobile.profile.BuiltInSiteProfiles
import java.io.IOException
import java.io.Reader
import java.io.StringReader
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertSame
import org.junit.Assert.assertThrows
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
class AfirmaJavascriptShimAssemblerTest {
    private val configuration = AfirmaJavascriptShimConfiguration(
        MiniAppletBridgeMode.FUNCTIONAL,
        false,
        AfirmaShimCompatibilityFlags(),
        "https://sede.example.test",
    )
    private val legacyTemplate: String
        get() = checkNotNull(javaClass.getResourceAsStream("AfirmaJavascriptShimLegacy.js"))
            .bufferedReader(Charsets.UTF_8).use { it.readText() }

    @Test
    fun facadeAndReaderMatchLegacyBytesForEveryPackagedProfileFlagCombination() {
        val flags = BuiltInSiteProfiles.catalog.profiles.flatMap { profile ->
            listOf(false, true).flatMap { active ->
                listOf(false, true).map { batch ->
                    WebMessageBridge.shimCompatibilityFlags(profile.profileId, active, batch)
                }
            }
        }.toSet()
        assertTrue("Packaged profile coverage must not be empty", flags.size > 1)
        assertParity(flags)
    }

    @Test
    fun defaultsEachSingleFlagAndAllEnabledMatchLegacyBytes() {
        assertParity(singleFlags() + AfirmaShimCompatibilityFlags() + allFlags())
    }

    private fun assertParity(flags: Collection<AfirmaShimCompatibilityFlags>) {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val template = legacyTemplate
        for (selection in flags) {
            for (mode in MiniAppletBridgeMode.entries) {
                for (qa in listOf(false, true)) {
                    val config = configuration.copy(mode = mode, qaDiagnosticsEnabled = qa, flags = selection)
                    val expected = AfirmaJavascriptShimLegacy.assemble(
                        template, mode, qa, selection, config.staBatchOrigin,
                    )
                    val assembled = AfirmaJavascriptShimAssembler.readAndAssemble(config) { StringReader(template) }
                    val facade = AfirmaJavascriptShim.load(context, mode, qa, selection, config.staBatchOrigin)
                    assertArrayEquals(expected.toByteArray(Charsets.UTF_8), assembled.toByteArray(Charsets.UTF_8))
                    assertPortalExtractions(expected, facade, selection)
                    assertFalse(assembled.contains("__JFM_"))
                }
            }
        }
    }

    @Test
    fun missingResourcePropagatesWithoutFallbackOrRetry() {
        val missing = Resources.NotFoundException("synthetic missing shim")
        var reads = 0
        val failure = assertThrows(Resources.NotFoundException::class.java) {
            AfirmaJavascriptShimAssembler.readAndAssemble(configuration) {
                reads++
                throw missing
            }
        }
        assertSame(missing, failure)
        assertEquals(1, reads)
    }

    @Test
    fun partialReadFailureClosesReaderAndPropagatesWithoutPartialScript() {
        val failure = IOException("synthetic read failure")
        var closed = false
        var reads = 0
        val reader = object : Reader() {
            override fun read(buffer: CharArray, offset: Int, length: Int): Int {
                if (reads++ > 0) throw failure
                buffer[offset] = 'x'
                return 1
            }
            override fun close() { closed = true }
        }
        assertSame(failure, assertThrows(IOException::class.java) {
            AfirmaJavascriptShimAssembler.readAndAssemble(configuration) { reader }
        })
        assertTrue(closed)
    }

    @Test
    fun everyMissingOrDuplicatedPlaceholderFailsClosed() {
        val template = legacyTemplate
        val tokens = Regex("__JFM_[A-Z_]+__").findAll(template).map { it.value }.toList()
        assertEquals(24, tokens.size)
        assertEquals(24, tokens.toSet().size)
        for (token in tokens) {
            for (invalid in listOf(template.replace(token, ""), template + token)) {
                assertThrows("Invalid token: $token", IllegalStateException::class.java) {
                    AfirmaJavascriptShimAssembler.readAndAssemble(configuration) { StringReader(invalid) }
                }
            }
        }
    }

    @Test
    fun unknownUnresolvedAndBlankTemplatesFailClosed() {
        for (template in listOf("", " \n\t", legacyTemplate + "__JFM_UNKNOWN__", legacyTemplate + "__JFM_")) {
            assertThrows(IllegalStateException::class.java) {
                AfirmaJavascriptShimAssembler.readAndAssemble(configuration) { StringReader(template) }
            }
        }
    }

    @Test
    fun finalCharacterLimitIsInclusiveAfterSubstitutionNotUtf8ByteSize() {
        val template = legacyTemplate
        val baseLength = AfirmaJavascriptShimLegacy.assemble(
            template, configuration.mode, false, configuration.flags, configuration.staBatchOrigin,
        ).length
        for (length in listOf(AfirmaJavascriptShim.MAX_SCRIPT_CHARS - 1, AfirmaJavascriptShim.MAX_SCRIPT_CHARS)) {
            val padded = template + "é".repeat(length - baseLength)
            assertTrue("Unconfigured input may exceed the final cap", padded.length > length)
            val output = AfirmaJavascriptShimAssembler.readAndAssemble(configuration) { StringReader(padded) }
            assertEquals(length, output.length)
            assertTrue(output.toByteArray(Charsets.UTF_8).size > length)
        }
        val tooLarge = template + "x".repeat(AfirmaJavascriptShim.MAX_SCRIPT_CHARS + 1 - baseLength)
        assertThrows(IllegalStateException::class.java) {
            AfirmaJavascriptShimAssembler.readAndAssemble(configuration) { StringReader(tooLarge) }
        }
    }

    @Test
    fun unboundedReaderIsStoppedAndClosedBeforeReadingWholeResource() {
        var readCharacters = 0
        var closed = false
        val reader = object : Reader() {
            override fun read(buffer: CharArray, offset: Int, length: Int): Int {
                buffer.fill('x', offset, offset + length)
                readCharacters += length
                return length
            }
            override fun close() { closed = true }
        }
        assertThrows(IllegalStateException::class.java) {
            AfirmaJavascriptShimAssembler.readAndAssemble(configuration) { reader }
        }
        assertTrue(closed)
        assertTrue(readCharacters <= AfirmaJavascriptShim.MAX_SCRIPT_CHARS + 2048)
    }

    @Test
    fun successfulReaderIsOpenedOnceAndClosed() {
        var opens = 0
        var closed = false
        val reader = object : StringReader(legacyTemplate) {
            override fun close() { closed = true; super.close() }
        }
        val output = AfirmaJavascriptShimAssembler.readAndAssemble(configuration) { opens++; reader }
        assertTrue(output.isNotBlank())
        assertEquals(1, opens)
        assertTrue(closed)
    }

    @Test
    fun acceptedOriginNormalizationAndQuotingMatchLegacyIncludingExplicitHttpsPort() {
        val template = legacyTemplate
        for (origin in listOf(
            "https://sede.example.test", "https://sede.example.test/", "https://sede.example.test:443/",
        )) {
            val config = configuration.copy(staBatchOrigin = origin)
            assertEquals(
                AfirmaJavascriptShimLegacy.assemble(template, config.mode, false, config.flags, origin),
                AfirmaJavascriptShimAssembler.assemble(config, template),
            )
        }
    }

    @Test
    fun invalidOriginIsRejectedEvenWhenAllBatchFlagsAreOff() {
        val template = legacyTemplate
        for (origin in listOf(
            "http://sede.example.test", "https:opaque", "https:///missing", "https://user@sede.example.test",
            "https://sede.example.test:444", "https://sede.example.test/path", "https://sede.example.test?query",
            "https://sede.example.test#fragment",
        )) {
            assertThrows(IllegalArgumentException::class.java) {
                AfirmaJavascriptShimAssembler.assemble(configuration.copy(staBatchOrigin = origin), template)
            }
        }
    }

    private fun singleFlags() = listOf(
        AfirmaShimCompatibilityFlags(ugr = true),
        AfirmaShimCompatibilityFlags(cantabria = true),
        AfirmaShimCompatibilityFlags(jccm = true),
        AfirmaShimCompatibilityFlags(jccmRegistro = true),
        AfirmaShimCompatibilityFlags(sevillaAtse = true),
        AfirmaShimCompatibilityFlags(airef = true),
        AfirmaShimCompatibilityFlags(cdti = true),
        AfirmaShimCompatibilityFlags(policia = true),
        AfirmaShimCompatibilityFlags(granCanaria = true),
        AfirmaShimCompatibilityFlags(fuerteventura = true),
        AfirmaShimCompatibilityFlags(canarias = true),
        AfirmaShimCompatibilityFlags(mineco = true),
        AfirmaShimCompatibilityFlags(melillaBatch = true),
        AfirmaShimCompatibilityFlags(lugoBatch = true),
        AfirmaShimCompatibilityFlags(caibBatch = true),
        AfirmaShimCompatibilityFlags(isciiiCertificateSelection = true),
        AfirmaShimCompatibilityFlags(valenciaCertificateSelection = true),
        AfirmaShimCompatibilityFlags(xuntaGalicia = true),
        AfirmaShimCompatibilityFlags(euskadiClientAuthPost = true),
        AfirmaShimCompatibilityFlags(acceda = true),
        AfirmaShimCompatibilityFlags(badajoz = true),
    )

    private fun allFlags() = AfirmaShimCompatibilityFlags(
        ugr = true,
        cantabria = true,
        jccm = true,
        jccmRegistro = true,
        sevillaAtse = true,
        airef = true,
        cdti = true,
        policia = true,
        granCanaria = true,
        fuerteventura = true,
        canarias = true,
        mineco = true,
        melillaBatch = true,
        lugoBatch = true,
        caibBatch = true,
        isciiiCertificateSelection = true,
        valenciaCertificateSelection = true,
        xuntaGalicia = true,
        euskadiClientAuthPost = true,
        acceda = true,
        badajoz = true,
    )
}

package dev.junta.firmamobile.browser

import java.io.File
import java.io.IOException
import java.io.StringReader
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Assert.assertThrows
import org.junit.Test

class AfirmaJavascriptShimFragmentTest {
    private val configuration = AfirmaJavascriptShimConfiguration(
        MiniAppletBridgeMode.FUNCTIONAL, false, AfirmaShimCompatibilityFlags(), "https://sede.example.test",
    )
    private val raw = listOf(File("src/main/res/raw"), File("app/src/main/res/raw"))
        .first { it.isDirectory }

    @Test
    fun realFragmentsReconstructLegacyTemplateInOrderAndAreReadOnce() {
        val read = mutableListOf<AfirmaJavascriptShimFragment>()
        val template = checkNotNull(javaClass.getResourceAsStream("AfirmaJavascriptShimLegacy.js"))
            .bufferedReader(Charsets.UTF_8).use { it.readText() }
        val actual = AfirmaJavascriptShimAssembler.readFragmentsAndAssemble(configuration) { fragment ->
            read += fragment
            File(raw, "afirma_shim_${fragment.name.lowercase()}.js").reader(Charsets.UTF_8)
        }
        assertArrayEquals(
            AfirmaJavascriptShimLegacy.assemble(
                template, configuration.mode, false, configuration.flags, configuration.staBatchOrigin,
            ).toByteArray(Charsets.UTF_8),
            actual.toByteArray(Charsets.UTF_8),
        )
        assertEquals(AfirmaJavascriptShimFragment.entries, read)
    }

    @Test
    fun missingDuplicateExtraOrReorderedManifestFailsBeforeReading() {
        val fragments = AfirmaJavascriptShimFragment.entries
        for (invalid in listOf(fragments.dropLast(1), fragments + fragments.first(), fragments.reversed())) {
            var reads = 0
            assertThrows(IllegalStateException::class.java) {
                AfirmaJavascriptShimAssembler.readFragmentsAndAssemble(configuration, invalid) {
                    reads++
                    StringReader("unused")
                }
            }
            assertEquals(0, reads)
        }
    }

    @Test
    fun missingOrBlankFragmentFailsWithoutFallback() {
        val failure = IOException("synthetic missing fragment")
        for (missing in AfirmaJavascriptShimFragment.entries) {
            val reads = mutableListOf<AfirmaJavascriptShimFragment>()
            assertSame(failure, assertThrows(IOException::class.java) {
                AfirmaJavascriptShimAssembler.readFragmentsAndAssemble(configuration) {
                    reads += it
                    if (it == missing) throw failure
                    StringReader("nonblank")
                }
            })
            assertEquals(AfirmaJavascriptShimFragment.entries.take(missing.ordinal + 1), reads)
            assertThrows(IllegalStateException::class.java) {
                AfirmaJavascriptShimAssembler.readFragmentsAndAssemble(configuration) {
                    StringReader(if (it == missing) " \n\t" else "nonblank")
                }
            }
        }
    }

    @Test
    fun aggregateReadBoundAppliesAcrossFragmentsAndClosesReaders() {
        var reads = 0
        var closes = 0
        assertThrows(IllegalStateException::class.java) {
            AfirmaJavascriptShimAssembler.readFragmentsAndAssemble(configuration) {
                reads++
                object : StringReader("x".repeat(60 * 1024)) {
                    override fun close() { closes++; super.close() }
                }
            }
        }
        assertEquals(2, reads)
        assertEquals(reads, closes)
    }
}

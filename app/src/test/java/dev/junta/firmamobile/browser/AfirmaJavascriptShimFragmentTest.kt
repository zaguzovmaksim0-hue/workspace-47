package dev.junta.firmamobile.browser

import java.io.File
import java.io.IOException
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

// Robolectric supplies the real org.json used by the assembler.
@RunWith(RobolectricTestRunner::class)
@ConscryptMode(ConscryptMode.Mode.OFF)
@GraphicsMode(GraphicsMode.Mode.LEGACY)
@SQLiteMode(SQLiteMode.Mode.LEGACY)
class AfirmaJavascriptShimFragmentTest {
    private val configuration = AfirmaJavascriptShimConfiguration(
        MiniAppletBridgeMode.FUNCTIONAL, false,
        AfirmaShimCompatibilityFlags(cantabria = true), "https://sede.example.test",
    )
    private val raw = listOf(File("src/main/res/raw"), File("app/src/main/res/raw"))
        .first { it.isDirectory }

    @Test
    fun realFragmentsPreserveLegacyCoreAndTupleInOrderAndAreReadOnce() {
        val read = mutableListOf<AfirmaJavascriptShimFragment>()
        val template = checkNotNull(javaClass.getResourceAsStream("AfirmaJavascriptShimLegacy.js"))
            .bufferedReader(Charsets.UTF_8).use { it.readText() }
        val actual = AfirmaJavascriptShimAssembler.readFragmentsAndAssemble(configuration) { fragment ->
            read += fragment
            File(raw, "afirma_shim_${fragment.name.lowercase()}.js").reader(Charsets.UTF_8)
        }
        assertCantabriaExtraction(
            AfirmaJavascriptShimLegacy.assemble(
                template, configuration.mode, false, configuration.flags, configuration.staBatchOrigin,
            ),
            actual,
            true,
        )
        assertEquals(AfirmaJavascriptShimFragment.entries, read)
    }

    @Test
    fun disabledCantabriaOmitsOnlyItsBodyAndDoesNotReadItsResource() {
        val reads = mutableListOf<AfirmaJavascriptShimFragment>()
        val disabled = configuration.copy(flags = AfirmaShimCompatibilityFlags())
        val output = AfirmaJavascriptShimAssembler.readFragmentsAndAssemble(disabled) {
            reads += it
            check(it != AfirmaJavascriptShimFragment.CANTABRIA)
            File(raw, "afirma_shim_${it.name.lowercase()}.js").reader(Charsets.UTF_8)
        }
        assertEquals(AfirmaJavascriptShimFragment.entries - AfirmaJavascriptShimFragment.CANTABRIA, reads)
        assertFalse(output.contains("https://rec.cantabria.es"))
        assertFalse(output.contains("__JFM_"))
        assertTrue(output.contains("const cantabriaCompatibilityEnabled = false;"))
        val enabled = AfirmaJavascriptShimAssembler.readFragmentsAndAssemble(configuration) {
            File(raw, "afirma_shim_${it.name.lowercase()}.js").reader(Charsets.UTF_8)
        }
        val body = File(raw, "afirma_shim_cantabria.js").readText(Charsets.UTF_8)
        assertEquals(enabled.replace(body, "").replace(
            "const cantabriaCompatibilityEnabled = true;", "const cantabriaCompatibilityEnabled = false;",
        ), output)
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

// Compare every unchanged byte with the frozen fixture after undoing only this extraction.
internal fun assertCantabriaExtraction(legacy: String, actual: String, enabled: Boolean) {
    val constants = legacy.substring(
        legacy.indexOf("  const cantabriaOrigin"), legacy.indexOf("  const ugrLiteral ="),
    )
    val start = legacy.indexOf("    const isExactCantabriaCall =")
    val branch = legacy.substring(start, legacy.indexOf("    const isJccmOrigin =", start))
    val expression = branch.substringAfter("=\n").trimStart()
    val module = "\n  matchesCantabriaSign = args => {\n" +
        constants.replace("  const", "    const") + "    return " + expression + "  };\n"
    assertEquals(enabled, actual.contains(module))
    assertEquals(actual.indexOf(module), actual.lastIndexOf(module))
    val restored = actual.replace(module, "")
        .replace("  let matchesCantabriaSign = () => false;\n", constants)
        .replace("    const isExactCantabriaCall = matchesCantabriaSign(args);\n", branch)
    assertArrayEquals(legacy.toByteArray(Charsets.UTF_8), restored.toByteArray(Charsets.UTF_8))
}

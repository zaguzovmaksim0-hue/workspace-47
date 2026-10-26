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
        AfirmaShimCompatibilityFlags(cantabria = true, jccm = true, jccmRegistro = true, airef = true, cdti = true, policia = true, granCanaria = true),
        "https://sede.example.test",
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
        assertPortalExtractions(
            AfirmaJavascriptShimLegacy.assemble(
                template, configuration.mode, false, configuration.flags, configuration.staBatchOrigin,
            ),
            actual,
            configuration.flags,
        )
        assertEquals(AfirmaJavascriptShimFragment.entries, read)
    }

    @Test
    fun disabledCantabriaOmitsOnlyItsBodyAndDoesNotReadItsResource() {
        val reads = mutableListOf<AfirmaJavascriptShimFragment>()
        val disabled = configuration.copy(flags = configuration.flags.copy(cantabria = false))
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
    fun disabledJccmOmitsOnlyItsBodyAndDoesNotReadItsResource() {
        val reads = mutableListOf<AfirmaJavascriptShimFragment>()
        val disabled = configuration.copy(flags = configuration.flags.copy(jccm = false))
        val output = AfirmaJavascriptShimAssembler.readFragmentsAndAssemble(disabled) {
            reads += it
            check(it != AfirmaJavascriptShimFragment.JCCM)
            File(raw, "afirma_shim_${it.name.lowercase()}.js").reader(Charsets.UTF_8)
        }
        assertEquals(AfirmaJavascriptShimFragment.entries - AfirmaJavascriptShimFragment.JCCM, reads)
        assertFalse(output.contains("https://ventanillaelectronica.jccm.es"))
        assertFalse(output.contains("QUJDREU="))
        assertFalse(output.contains("__JFM_"))
        val enabled = AfirmaJavascriptShimAssembler.readFragmentsAndAssemble(configuration) {
            File(raw, "afirma_shim_${it.name.lowercase()}.js").reader(Charsets.UTF_8)
        }
        val body = File(raw, "afirma_shim_jccm.js").readText(Charsets.UTF_8)
        assertEquals(enabled.replace(body, "").replace(
            "const jccmCompatibilityEnabled = true;", "const jccmCompatibilityEnabled = false;",
        ), output)
    }

    @Test
    fun disabledJccmRegistroOmitsOnlyItsBodyAndDoesNotReadItsResource() {
        val reads = mutableListOf<AfirmaJavascriptShimFragment>()
        val disabled = configuration.copy(flags = configuration.flags.copy(jccmRegistro = false))
        val output = AfirmaJavascriptShimAssembler.readFragmentsAndAssemble(disabled) {
            reads += it
            check(it != AfirmaJavascriptShimFragment.JCCM_REGISTRO)
            File(raw, "afirma_shim_${it.name.lowercase()}.js").reader(Charsets.UTF_8)
        }
        assertEquals(AfirmaJavascriptShimFragment.entries - AfirmaJavascriptShimFragment.JCCM_REGISTRO, reads)
        assertFalse(output.contains("https://registrounicociudadanos.jccm.es"))
        assertFalse(output.contains("/registrounicociudadanos/accesoclvd.do"))
        assertFalse(output.contains("__JFM_"))
        assertTrue(output.contains("const jccmRegistroCompatibilityEnabled = false;"))
        val enabled = AfirmaJavascriptShimAssembler.readFragmentsAndAssemble(configuration) {
            File(raw, "afirma_shim_${it.name.lowercase()}.js").reader(Charsets.UTF_8)
        }
        val body = File(raw, "afirma_shim_jccm_registro.js").readText(Charsets.UTF_8)
        assertEquals(enabled.replace(body, "").replace(
            "const jccmRegistroCompatibilityEnabled = true;", "const jccmRegistroCompatibilityEnabled = false;",
        ), output)
    }

    @Test
    fun disabledAirefOmitsOnlyItsBodyAndDoesNotReadItsResource() {
        val reads = mutableListOf<AfirmaJavascriptShimFragment>()
        val disabled = configuration.copy(flags = configuration.flags.copy(airef = false))
        val output = AfirmaJavascriptShimAssembler.readFragmentsAndAssemble(disabled) {
            reads += it
            check(it != AfirmaJavascriptShimFragment.AIREF)
            File(raw, "afirma_shim_${it.name.lowercase()}.js").reader(Charsets.UTF_8)
        }
        assertEquals(AfirmaJavascriptShimFragment.entries - AfirmaJavascriptShimFragment.AIREF, reads)
        assertFalse(output.contains("https://sede.airef.es"))
        assertFalse(output.contains("/invesiteRE/action/solicitud/view"))
        assertFalse(output.contains("__JFM_"))
        assertTrue(output.contains("const airefCompatibilityEnabled = false;"))
        val enabled = AfirmaJavascriptShimAssembler.readFragmentsAndAssemble(configuration) {
            File(raw, "afirma_shim_${it.name.lowercase()}.js").reader(Charsets.UTF_8)
        }
        val body = File(raw, "afirma_shim_airef.js").readText(Charsets.UTF_8)
        assertEquals(enabled.replace(body, "").replace(
            "const airefCompatibilityEnabled = true;", "const airefCompatibilityEnabled = false;",
        ), output)
    }

    @Test
    fun disabledCdtiOmitsOnlyItsBodyAndDoesNotReadItsResource() {
        val reads = mutableListOf<AfirmaJavascriptShimFragment>()
        val disabled = configuration.copy(flags = configuration.flags.copy(cdti = false))
        val output = AfirmaJavascriptShimAssembler.readFragmentsAndAssemble(disabled) {
            reads += it
            check(it != AfirmaJavascriptShimFragment.CDTI)
            File(raw, "afirma_shim_${it.name.lowercase()}.js").reader(Charsets.UTF_8)
        }
        assertEquals(AfirmaJavascriptShimFragment.entries - AfirmaJavascriptShimFragment.CDTI, reads)
        assertFalse(output.contains("https://sede.cdti.gob.es"))
        assertFalse(output.contains("/AreaPrivada/Expedientes/Common/Certificados/ValidarCertificado.aspx"))
        assertFalse(output.contains("__JFM_"))
        assertTrue(output.contains("const cdtiCompatibilityEnabled = false;"))
        val enabled = AfirmaJavascriptShimAssembler.readFragmentsAndAssemble(configuration) {
            File(raw, "afirma_shim_${it.name.lowercase()}.js").reader(Charsets.UTF_8)
        }
        val body = File(raw, "afirma_shim_cdti.js").readText(Charsets.UTF_8)
        assertEquals(enabled.replace(body, "").replace(
            "const cdtiCompatibilityEnabled = true;", "const cdtiCompatibilityEnabled = false;",
        ), output)
    }

    @Test
    fun disabledPoliciaOmitsOnlyItsBodyAndDoesNotReadItsResource() {
        val reads = mutableListOf<AfirmaJavascriptShimFragment>()
        val disabled = configuration.copy(flags = configuration.flags.copy(policia = false))
        val output = AfirmaJavascriptShimAssembler.readFragmentsAndAssemble(disabled) {
            reads += it
            check(it != AfirmaJavascriptShimFragment.POLICIA)
            File(raw, "afirma_shim_${it.name.lowercase()}.js").reader(Charsets.UTF_8)
        }
        assertEquals(AfirmaJavascriptShimFragment.entries - AfirmaJavascriptShimFragment.POLICIA, reads)
        assertFalse(output.contains("https://sede.policia.gob.es"))
        assertFalse(output.contains("/portalCiudadano/_es/solicitudGenerica.xhtml"))
        assertFalse(output.contains("__JFM_"))
        assertTrue(output.contains("const policiaCompatibilityEnabled = false;"))
        val enabled = AfirmaJavascriptShimAssembler.readFragmentsAndAssemble(configuration) {
            File(raw, "afirma_shim_${it.name.lowercase()}.js").reader(Charsets.UTF_8)
        }
        val body = File(raw, "afirma_shim_policia.js").readText(Charsets.UTF_8)
        assertEquals(enabled.replace(body, "").replace(
            "const policiaCompatibilityEnabled = true;", "const policiaCompatibilityEnabled = false;",
        ), output)
    }

    @Test
    fun disabledGranCanariaOmitsOnlyItsBodyAndDoesNotReadItsResource() {
        val reads = mutableListOf<AfirmaJavascriptShimFragment>()
        val disabled = configuration.copy(flags = configuration.flags.copy(granCanaria = false))
        val output = AfirmaJavascriptShimAssembler.readFragmentsAndAssemble(disabled) {
            reads += it
            check(it != AfirmaJavascriptShimFragment.GRAN_CANARIA)
            File(raw, "afirma_shim_${it.name.lowercase()}.js").reader(Charsets.UTF_8)
        }
        assertEquals(AfirmaJavascriptShimFragment.entries - AfirmaJavascriptShimFragment.GRAN_CANARIA, reads)
        assertFalse(output.contains("https://sede.grancanaria.com"))
        assertFalse(output.contains("const granCanariaExtraProperties ="))
        assertFalse(output.contains("__JFM_"))
        assertTrue(output.contains("const granCanariaCompatibilityEnabled = false;"))
        val enabled = AfirmaJavascriptShimAssembler.readFragmentsAndAssemble(configuration) {
            File(raw, "afirma_shim_${it.name.lowercase()}.js").reader(Charsets.UTF_8)
        }
        val body = File(raw, "afirma_shim_gran_canaria.js").readText(Charsets.UTF_8)
        assertEquals(enabled.replace(body, "").replace(
            "const granCanariaCompatibilityEnabled = true;", "const granCanariaCompatibilityEnabled = false;",
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

// Compare every unchanged byte with the frozen fixture after undoing only the portal extractions.
internal fun assertPortalExtractions(legacy: String, assembled: String, flags: AfirmaShimCompatibilityFlags) {
    val granCanariaRestored = restoreGranCanariaExtraction(legacy, assembled, flags.granCanaria)
    val actual = restoreJccmExtraction(
        legacy, restoreJccmRegistroExtraction(
            legacy, restoreAirefExtraction(
                legacy, restoreCdtiExtraction(
                    legacy, restorePoliciaExtraction(legacy, granCanariaRestored, flags.policia), flags.cdti,
                ), flags.airef,
            ), flags.jccmRegistro,
        ), flags.jccm,
    )
    val enabled = flags.cantabria
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

private fun restoreJccmExtraction(legacy: String, actual: String, enabled: Boolean): String {
    val constants = legacy.substring(
        legacy.indexOf("  const jccmOrigin ="), legacy.indexOf("  const jccmRegistroOrigin ="),
    )
    val start = legacy.indexOf("    const isJccmOrigin =")
    val branch = legacy.substring(start, legacy.indexOf("    const dataB64 =", start))
    val module = "\n  matchesJccmSign = args => {\n" + constants.replace("  const", "    const") +
        branch + "    return isExactJccmCall;\n  };\n"
    assertEquals(enabled, actual.contains(module))
    assertEquals(actual.indexOf(module), actual.lastIndexOf(module))
    return actual.replace(module, "")
        .replace("  let matchesJccmSign = () => false;\n", constants)
        .replace("    const isExactJccmCall = matchesJccmSign(args);\n", branch)
}

private fun restoreJccmRegistroExtraction(legacy: String, actual: String, enabled: Boolean): String {
    val constants = legacy.substring(
        legacy.indexOf("  const jccmRegistroOrigin ="), legacy.indexOf("  const sevillaAtseOrigin ="),
    )
    val start = legacy.indexOf("    const isJccmRegistroOrigin =")
    val branch = legacy.substring(start, legacy.indexOf("    if (isJccmRegistroOrigin && !isExactJccmRegistroCall)", start))
    val module = "\n  matchesJccmRegistroSign = (args, successCallback, errorCallback) => {\n" +
        constants.replace("  const", "    const").replace("\n    \"", "\n      \"") + branch +
        "    return isJccmRegistroOrigin ? isExactJccmRegistroCall : null;\n  };\n"
    assertEquals(enabled, actual.contains(module))
    assertEquals(actual.indexOf(module), actual.lastIndexOf(module))
    return actual.replace(module, "")
        .replace(
            "  // null: not applicable; false: reject; true: exact tuple.\n  let matchesJccmRegistroSign = () => null;\n",
            constants,
        )
        .replace(
            "    const isExactJccmRegistroCall = matchesJccmRegistroSign(args, successCallback, errorCallback);\n",
            branch,
        )
        .replace("if (isExactJccmRegistroCall === false)", "if (isJccmRegistroOrigin && !isExactJccmRegistroCall)")
}

private fun restoreAirefExtraction(legacy: String, actual: String, enabled: Boolean): String {
    val constants = legacy.substring(legacy.indexOf("  const airefOrigin ="), legacy.indexOf("  const cdtiOrigin ="))
    val helpers = legacy.substring(
        legacy.indexOf("  function isValidAirefPayload"), legacy.indexOf("  function isExactCanariasChallenge"),
    )
    val start = legacy.indexOf("    const isAirefOrigin =")
    val branch = legacy.substring(start, legacy.indexOf("    if (isAirefOrigin && !isExactAirefCall)", start))
    val module = "\n  matchesAirefSign = (args, successCallback, errorCallback) => {\n" +
        constants.replace("  const", "    const") + "\n" +
        helpers.split('\n').dropLast(1)
            .joinToString("") { if (it.isBlank()) "\n" else "  $it\n" } +
        branch + "    return isAirefOrigin ? isExactAirefCall : null;\n  };\n"
    assertEquals(enabled, actual.contains(module))
    assertEquals(actual.indexOf(module), actual.lastIndexOf(module))
    return actual.replace(module, "")
        .replace(
            "  // null: not applicable; false: reject; true: exact tuple.\n  let matchesAirefSign = () => null;\n",
            constants,
        )
        .replace("    const isExactAirefCall = matchesAirefSign(args, successCallback, errorCallback);\n", branch)
        .replace("if (isExactAirefCall === false)", "if (isAirefOrigin && !isExactAirefCall)")
        .replace("  function isExactCanariasChallenge", helpers + "  function isExactCanariasChallenge")
}

private fun restoreCdtiExtraction(legacy: String, actual: String, enabled: Boolean): String {
    val constants = legacy.substring(legacy.indexOf("  const cdtiOrigin ="), legacy.indexOf("  const policiaOrigin ="))
    val start = legacy.indexOf("    const isCdtiOrigin =")
    val branch = legacy.substring(start, legacy.indexOf("    if (isCdtiOrigin && !isExactCdtiCall)", start))
    val module = "\n  matchesCdtiSign = (args, successCallback, errorCallback) => {\n" +
        constants.replace("  const", "    const").replace("\n    \"", "\n      \"") + branch +
        "    return isCdtiOrigin ? isExactCdtiCall : null;\n  };\n"
    assertEquals(enabled, actual.contains(module))
    assertEquals(actual.indexOf(module), actual.lastIndexOf(module))
    return actual.replace(module, "")
        .replace(
            "  // null: not applicable; false: reject; true: exact tuple.\n  let matchesCdtiSign = () => null;\n",
            constants,
        )
        .replace("    const isExactCdtiCall = matchesCdtiSign(args, successCallback, errorCallback);\n", branch)
        .replace("if (isExactCdtiCall === false)", "if (isCdtiOrigin && !isExactCdtiCall)")
}

private fun restorePoliciaExtraction(legacy: String, actual: String, enabled: Boolean): String {
    val origin = legacy.substring(
        legacy.indexOf("  const policiaOrigin ="), legacy.indexOf("  const granCanariaOrigin ="),
    )
    val constants = legacy.substring(
        legacy.indexOf("  const policiaProcedurePage ="), legacy.indexOf("  const staBatchOrigin ="),
    )
    val start = legacy.indexOf("    const isPoliciaOrigin =")
    val branch = legacy.substring(start, legacy.indexOf("    if (isPoliciaOrigin && !isExactPoliciaCall)", start))
    val module = "\n  matchesPoliciaSign = (args, successCallback, errorCallback) => {\n" +
        (origin + constants).replace("  const", "    const").replace("\n    \"", "\n      \"") + branch +
        "    return isPoliciaOrigin ? isExactPoliciaCall : null;\n  };\n"
    assertEquals(enabled, actual.contains(module))
    assertEquals(actual.indexOf(module), actual.lastIndexOf(module))
    return actual.replace(module, "")
        .replace(
            "  // null: not applicable; false: reject; true: exact tuple.\n  let matchesPoliciaSign = () => null;\n",
            origin,
        )
        .replace("  const staBatchOrigin =", constants + "  const staBatchOrigin =")
        .replace("    const isExactPoliciaCall = matchesPoliciaSign(args, successCallback, errorCallback);\n", branch)
        .replace("if (isExactPoliciaCall === false)", "if (isPoliciaOrigin && !isExactPoliciaCall)")
}

private fun restoreGranCanariaExtraction(legacy: String, actual: String, enabled: Boolean): String {
    val origin = legacy.substring(legacy.indexOf("  const granCanariaOrigin ="), legacy.indexOf("  const canariasOrigin ="))
    val constants = legacy.substring(
        legacy.indexOf("  const granCanariaExtraProperties ="), legacy.indexOf("  const fuerteventuraOrigin ="),
    )
    val start = legacy.indexOf("    const isGranCanariaOrigin =")
    val branch = legacy.substring(start, legacy.indexOf("    if (isGranCanariaOrigin && !isExactGranCanariaCall)", start))
    val module = "\n  matchesGranCanariaSign = (args, successCallback, errorCallback) => {\n" +
        (origin + constants).replace("  const", "    const").replace("\n    \"", "\n      \"") + branch +
        "    return isGranCanariaOrigin ? isExactGranCanariaCall : null;\n  };\n"
    assertEquals(enabled, actual.contains(module))
    assertEquals(actual.indexOf(module), actual.lastIndexOf(module))
    return actual.replace(module, "")
        .replace(
            "  // null: not applicable; false: reject; true: exact tuple.\n  let matchesGranCanariaSign = () => null;\n",
            origin,
        )
        .replace("  const fuerteventuraOrigin =", constants + "  const fuerteventuraOrigin =")
        .replace("    const isExactGranCanariaCall = matchesGranCanariaSign(args, successCallback, errorCallback);\n", branch)
        .replace("if (isExactGranCanariaCall === false)", "if (isGranCanariaOrigin && !isExactGranCanariaCall)")
}

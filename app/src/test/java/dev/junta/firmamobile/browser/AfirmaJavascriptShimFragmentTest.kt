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
        AfirmaShimCompatibilityFlags(cantabria = true, jccm = true, jccmRegistro = true, airef = true, cdti = true, policia = true, granCanaria = true, fuerteventura = true, mineco = true, canarias = true, xuntaGalicia = true, acceda = true, sevillaAtse = true, ugr = true, badajoz = true, melillaBatch = true, lugoBatch = true, caibBatch = true, euskadiClientAuthPost = true),
        "https://sede.example.test",
    )
    private val raw = listOf(File("src/main/res/raw"), File("app/src/main/res/raw"))
        .first { it.isDirectory }

    @Test
    fun disabledEuskadiOmitsPostHooksAndStateButKeepsSharedSecureIds() {
        val reads = mutableListOf<AfirmaJavascriptShimFragment>()
        val disabled = configuration.copy(flags = configuration.flags.copy(euskadiClientAuthPost = false))
        val output = AfirmaJavascriptShimAssembler.readFragmentsAndAssemble(disabled) {
            reads += it
            check(it != AfirmaJavascriptShimFragment.EUSKADI_POST)
            File(raw, "afirma_shim_${it.name.lowercase()}.js").reader(Charsets.UTF_8)
        }
        assertEquals(AfirmaJavascriptShimFragment.entries - AfirmaJavascriptShimFragment.EUSKADI_POST, reads)
        assertEquals(1, reads.count { it == AfirmaJavascriptShimFragment.REQUEST_IDS })
        for (token in listOf("EUSKADI_CLIENT_AUTH_POST", "euskadiClientAuthPostSent",
            "eidas.izenpe.com", "eidas2.izenpe.com", "isSafeEuskadiOpaqueRequest",
            "HTMLFormElement.prototype.submit", "HTMLFormElement.prototype.requestSubmit")) {
            assertFalse(token, output.contains(token))
        }
        assertTrue(output.contains("function secureRequestId"))
        assertTrue(output.contains("crypto.randomUUID"))
        assertTrue(output.contains("crypto.getRandomValues"))
        assertTrue(output.contains("QA_PORTAL_DIAGNOSTIC"))
        assertTrue(output.contains("MINIAPPLET_SIGN"))
        assertFalse(output.contains("Math.random"))
        assertFalse(output.contains("__JFM_"))
        val enabled = AfirmaJavascriptShimAssembler.readFragmentsAndAssemble(configuration) {
            File(raw, "afirma_shim_${it.name.lowercase()}.js").reader(Charsets.UTF_8)
        }
        val body = File(raw, "afirma_shim_euskadi_post.js").readText(Charsets.UTF_8)
        assertEquals(enabled.replace(body, "").replace(
            "const euskadiClientAuthPostEnabled = true;", "const euskadiClientAuthPostEnabled = false;",
        ), output)
    }

    @Test
    fun euskadiSelectionKeepsActiveProfileGateWithoutSigningModeCoupling() {
        for (active in listOf(false, true)) for (mode in MiniAppletBridgeMode.entries) {
            val flags = WebMessageBridge.shimCompatibilityFlags(
                dev.junta.firmamobile.profile.ProfileId(EuskadiClientAuthPostBridgeAdapter.PROFILE_ID), active, false,
            )
            assertEquals(active, flags.euskadiClientAuthPost)
            assertFalse(flags.melillaBatch)
            assertFalse(flags.lugoBatch)
            assertFalse(flags.caibBatch)
            val reads = mutableListOf<AfirmaJavascriptShimFragment>()
            val output = AfirmaJavascriptShimAssembler.readFragmentsAndAssemble(configuration.copy(flags = flags, mode = mode)) {
                reads += it
                File(raw, "afirma_shim_${it.name.lowercase()}.js").reader(Charsets.UTF_8)
            }
            assertEquals(active, reads.contains(AfirmaJavascriptShimFragment.EUSKADI_POST))
            assertEquals(active, output.contains(EuskadiClientAuthPostBridgeAdapter.TARGET_URL))
            assertTrue(reads.contains(AfirmaJavascriptShimFragment.REQUEST_IDS))
            assertTrue(output.contains("function secureRequestId"))
        }
    }

    @Test
    fun disabledCaibOmitsOnlyItsTokenContractHooksAndResource() {
        val reads = mutableListOf<AfirmaJavascriptShimFragment>()
        val disabled = configuration.copy(flags = configuration.flags.copy(caibBatch = false))
        val output = AfirmaJavascriptShimAssembler.readFragmentsAndAssemble(disabled) {
            reads += it
            check(it != AfirmaJavascriptShimFragment.CAIB_BATCH)
            File(raw, "afirma_shim_${it.name.lowercase()}.js").reader(Charsets.UTF_8)
        }
        assertEquals(AfirmaJavascriptShimFragment.entries - AfirmaJavascriptShimFragment.CAIB_BATCH, reads)
        for (token in listOf("https://intranet.caib.es", "caibSignerPathPattern", "caibSignatureIdPattern",
            "caibClientBase", "caibBatchBase64Pattern", "function decodeCaibUrlBase64",
            "CAIB_SET_FORCE_WS_MODE", "CAIB_SET_SERVLETS", "CAIB_CARGAR_APP_AFIRMA")) {
            assertFalse(token, output.contains(token))
        }
        assertTrue(output.contains("const isCaibBatchDocument = caibRequestToken() !== null;"))
        assertTrue(output.contains("pending.caibXml === true"))
        assertTrue(output.contains("CAIB_XML_BATCH_CANCEL"))
        assertTrue(output.contains("LUGO_XML_BATCH_CANCEL"))
        assertTrue(output.contains("MINIAPPLET_BATCH_RESULT"))
        assertFalse(output.contains("__JFM_"))
        val enabled = AfirmaJavascriptShimAssembler.readFragmentsAndAssemble(configuration) {
            File(raw, "afirma_shim_${it.name.lowercase()}.js").reader(Charsets.UTF_8)
        }
        val body = File(raw, "afirma_shim_caib_batch.js").readText(Charsets.UTF_8)
        assertEquals(enabled.replace(body, "").replace(
            "const caibBatchCompatibilityEnabled = true;", "const caibBatchCompatibilityEnabled = false;",
        ), output)
    }

    @Test
    fun caibResourceSelectionIsIndependentOfStaAndLugo() {
        for (flags in listOf(
            AfirmaShimCompatibilityFlags(),
            AfirmaShimCompatibilityFlags(caibBatch = true),
            AfirmaShimCompatibilityFlags(melillaBatch = true),
            AfirmaShimCompatibilityFlags(lugoBatch = true),
        )) {
            val reads = mutableListOf<AfirmaJavascriptShimFragment>()
            val output = AfirmaJavascriptShimAssembler.readFragmentsAndAssemble(configuration.copy(flags = flags)) {
                reads += it
                File(raw, "afirma_shim_${it.name.lowercase()}.js").reader(Charsets.UTF_8)
            }
            assertEquals(flags.caibBatch, reads.contains(AfirmaJavascriptShimFragment.CAIB_BATCH))
            assertEquals(flags.caibBatch, output.contains("const caibOrigin ="))
            assertEquals(flags.melillaBatch, reads.contains(AfirmaJavascriptShimFragment.MELILLA_BATCH))
            assertEquals(flags.lugoBatch, reads.contains(AfirmaJavascriptShimFragment.LUGO_BATCH))
        }
    }

    @Test
    fun disabledLugoOmitsOnlyItsContractHooksAndResource() {
        val reads = mutableListOf<AfirmaJavascriptShimFragment>()
        val disabled = configuration.copy(flags = configuration.flags.copy(lugoBatch = false))
        val output = AfirmaJavascriptShimAssembler.readFragmentsAndAssemble(disabled) {
            reads += it
            check(it != AfirmaJavascriptShimFragment.LUGO_BATCH)
            File(raw, "afirma_shim_${it.name.lowercase()}.js").reader(Charsets.UTF_8)
        }
        assertEquals(AfirmaJavascriptShimFragment.entries - AfirmaJavascriptShimFragment.LUGO_BATCH, reads)
        for (token in listOf("https://sede.deputacionlugo.org", "lugoClientBase", "lugoExtraProperties", "LUGO_CARGAR_APP_AFIRMA")) {
            assertFalse(token, output.contains(token))
        }
        assertTrue(output.contains("pending.lugoXml === true"))
        assertTrue(output.contains("LUGO_XML_BATCH_CANCEL"))
        assertTrue(output.contains("CAIB_XML_BATCH_CANCEL"))
        assertTrue(output.contains("MINIAPPLET_BATCH_RESULT"))
        assertFalse(output.contains("__JFM_"))
        val enabled = AfirmaJavascriptShimAssembler.readFragmentsAndAssemble(configuration) {
            File(raw, "afirma_shim_${it.name.lowercase()}.js").reader(Charsets.UTF_8)
        }
        val body = File(raw, "afirma_shim_lugo_batch.js").readText(Charsets.UTF_8)
        assertEquals(enabled.replace(body, "").replace(
            "const lugoBatchCompatibilityEnabled = true;", "const lugoBatchCompatibilityEnabled = false;",
        ), output)
    }

    @Test
    fun lugoResourceSelectionIsIndependentOfMelillaAndCaib() {
        for (flags in listOf(
            AfirmaShimCompatibilityFlags(),
            AfirmaShimCompatibilityFlags(lugoBatch = true),
            AfirmaShimCompatibilityFlags(melillaBatch = true),
            AfirmaShimCompatibilityFlags(caibBatch = true),
        )) {
            val reads = mutableListOf<AfirmaJavascriptShimFragment>()
            val output = AfirmaJavascriptShimAssembler.readFragmentsAndAssemble(configuration.copy(flags = flags)) {
                reads += it
                File(raw, "afirma_shim_${it.name.lowercase()}.js").reader(Charsets.UTF_8)
            }
            assertEquals(flags.lugoBatch, reads.contains(AfirmaJavascriptShimFragment.LUGO_BATCH))
            assertEquals(flags.lugoBatch, output.contains("https://sede.deputacionlugo.org"))
            assertEquals(flags.melillaBatch, reads.contains(AfirmaJavascriptShimFragment.MELILLA_BATCH))
        }
    }

    @Test
    fun disabledStaBatchOmitsItsStateHooksAndResourceOnly() {
        val reads = mutableListOf<AfirmaJavascriptShimFragment>()
        val disabled = configuration.copy(flags = configuration.flags.copy(melillaBatch = false))
        val output = AfirmaJavascriptShimAssembler.readFragmentsAndAssemble(disabled) {
            reads += it
            check(it != AfirmaJavascriptShimFragment.MELILLA_BATCH)
            File(raw, "afirma_shim_${it.name.lowercase()}.js").reader(Charsets.UTF_8)
        }
        assertEquals(AfirmaJavascriptShimFragment.entries - AfirmaJavascriptShimFragment.MELILLA_BATCH, reads)
        for (body in listOf("activeMelillaBatch", "function isMelillaBatchPage", "signBatchProcess", "createBatch")) {
            assertFalse(body, output.contains(body))
        }
        assertTrue(output.contains("pendingBatchCallbacks"))
        assertTrue(output.contains("receiveMelillaBatchResult"))
        assertTrue(output.contains("LUGO_XML_BATCH_CANCEL"))
        assertTrue(output.contains("CAIB_XML_BATCH_CANCEL"))
        assertFalse(output.contains("__JFM_"))
        val enabled = AfirmaJavascriptShimAssembler.readFragmentsAndAssemble(configuration) {
            File(raw, "afirma_shim_${it.name.lowercase()}.js").reader(Charsets.UTF_8)
        }
        val body = File(raw, "afirma_shim_melilla_batch.js").readText(Charsets.UTF_8)
        assertEquals(enabled.replace(body, "").replace(
            "const melillaBatchCompatibilityEnabled = true;", "const melillaBatchCompatibilityEnabled = false;",
        ), output)
    }

    @Test
    fun staFallbackProfilesKeepTheirNativeGateAndExactSourceOrigin() {
        val profiles = listOf(
            MelillaBatchBridgeAdapter.PROFILE_ID to MelillaBatchBridgeAdapter.SOURCE_ORIGIN,
            ExtremaduraBatchBridgeAdapter.PROFILE_ID to ExtremaduraBatchBridgeAdapter.SOURCE_ORIGIN,
            LaPalmaBatchBridgeAdapter.PROFILE_ID to LaPalmaBatchBridgeAdapter.SOURCE_ORIGIN,
            HuescaBatchBridgeAdapter.PROFILE_ID to HuescaBatchBridgeAdapter.SOURCE_ORIGIN,
            BurgosBatchBridgeAdapter.PROFILE_ID to BurgosBatchBridgeAdapter.SOURCE_ORIGIN,
        )
        for ((profile, origin) in profiles) for (active in listOf(false, true)) for (gate in listOf(false, true)) {
            val flags = WebMessageBridge.shimCompatibilityFlags(
                dev.junta.firmamobile.profile.ProfileId(profile), active, gate,
            )
            assertEquals(gate, flags.melillaBatch)
            assertFalse(flags.lugoBatch)
            assertFalse(flags.caibBatch)
            val reads = mutableListOf<AfirmaJavascriptShimFragment>()
            val output = AfirmaJavascriptShimAssembler.readFragmentsAndAssemble(
                configuration.copy(flags = flags, staBatchOrigin = origin),
            ) {
                reads += it
                File(raw, "afirma_shim_${it.name.lowercase()}.js").reader(Charsets.UTF_8)
            }
            assertEquals(gate, reads.contains(AfirmaJavascriptShimFragment.MELILLA_BATCH))
            assertEquals(gate, output.contains("function isMelillaBatchPage"))
            assertTrue(output.contains("const staBatchOrigin = ${org.json.JSONObject.quote(origin)};"))
            if (gate) assertTrue(output.contains("window.location.origin === staBatchOrigin"))
        }
    }

    @Test
    fun lugoAndCaibNeverSelectTheStaFallbackResource() {
        for (profile in listOf(LugoBatchBridgeAdapter.PROFILE_ID, CaibBatchBridgeAdapter.PROFILE_ID)) {
            for (active in listOf(false, true)) for (gate in listOf(false, true)) {
                val flags = WebMessageBridge.shimCompatibilityFlags(
                    dev.junta.firmamobile.profile.ProfileId(profile), active, gate,
                )
                assertFalse(flags.melillaBatch)
                assertEquals(gate && profile == LugoBatchBridgeAdapter.PROFILE_ID, flags.lugoBatch)
                assertEquals(gate && profile == CaibBatchBridgeAdapter.PROFILE_ID, flags.caibBatch)
                val output = AfirmaJavascriptShimAssembler.readFragmentsAndAssemble(configuration.copy(flags = flags)) {
                    check(it != AfirmaJavascriptShimFragment.MELILLA_BATCH)
                    File(raw, "afirma_shim_${it.name.lowercase()}.js").reader(Charsets.UTF_8)
                }
                assertFalse(output.contains("activeMelillaBatch"))
            }
        }
    }

    @Test
    fun disabledBadajozOmitsItsContractStateAndDiagnostics() {
        val disabled = configuration.copy(flags = configuration.flags.copy(badajoz = false))
        val output = AfirmaJavascriptShimAssembler.readFragmentsAndAssemble(disabled) {
            check(it.name != "BADAJOZ")
            File(raw, "afirma_shim_${it.name.lowercase()}.js").reader(Charsets.UTF_8)
        }
        for (body in listOf(
            "https://sede.dip-badajoz.es", "badajozSignHookReadyDiagnosticPosted",
            "badajozDiagnosticWrappedFunctions", "badajozDiagnosticPostedStages",
            "BADAJOZ_SIGN_HOOK_READY", "BADAJOZ_LATE_REWRAP_STARTED",
        )) assertFalse(body, output.contains(body))
        assertTrue(output.contains("const badajozCompatibilityEnabled = false;"))
        assertTrue(output.contains("window.setInterval(repairCurrentSigningLibraries, 500)"))
        assertTrue(output.contains("window.clearInterval"))
        assertFalse(output.contains("__JFM_"))
    }

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
    fun disabledUgrOmitsOnlyItsBodyAndDoesNotReadItsResource() {
        val reads = mutableListOf<AfirmaJavascriptShimFragment>()
        val disabled = configuration.copy(flags = configuration.flags.copy(ugr = false))
        val output = AfirmaJavascriptShimAssembler.readFragmentsAndAssemble(disabled) {
            reads += it
            check(it != AfirmaJavascriptShimFragment.UGR)
            File(raw, "afirma_shim_${it.name.lowercase()}.js").reader(Charsets.UTF_8)
        }
        assertEquals(AfirmaJavascriptShimFragment.entries - AfirmaJavascriptShimFragment.UGR, reads)
        assertFalse(output.contains("https://sede.ugr.es"))
        assertFalse(output.contains("Universidad de Granada"))
        assertFalse(output.contains("VW5pdmVyc2lkYWQgZGUgR3JhbmFkYQ=="))
        assertFalse(output.contains("__JFM_"))
        assertTrue(output.contains("const ugrCompatibilityEnabled = false;"))
        val enabled = AfirmaJavascriptShimAssembler.readFragmentsAndAssemble(configuration) {
            File(raw, "afirma_shim_${it.name.lowercase()}.js").reader(Charsets.UTF_8)
        }
        val body = File(raw, "afirma_shim_ugr.js").readText(Charsets.UTF_8)
        assertEquals(enabled.replace(body, "").replace(
            "const ugrCompatibilityEnabled = true;", "const ugrCompatibilityEnabled = false;",
        ), output)
    }

    @Test
    fun disabledSevillaAtseOmitsOnlyItsBodyAndDoesNotReadItsResource() {
        val reads = mutableListOf<AfirmaJavascriptShimFragment>()
        val disabled = configuration.copy(flags = configuration.flags.copy(sevillaAtse = false))
        val output = AfirmaJavascriptShimAssembler.readFragmentsAndAssemble(disabled) {
            reads += it
            check(it != AfirmaJavascriptShimFragment.SEVILLA_ATSE)
            File(raw, "afirma_shim_${it.name.lowercase()}.js").reader(Charsets.UTF_8)
        }
        assertEquals(AfirmaJavascriptShimFragment.entries - AfirmaJavascriptShimFragment.SEVILLA_ATSE, reads)
        assertFalse(output.contains("https://www.sevilla.org"))
        assertFalse(output.contains("sevillaAtseSetupState"))
        assertFalse(output.contains("__JFM_"))
        assertTrue(output.contains("const sevillaAtseCompatibilityEnabled = false;"))
        val enabled = AfirmaJavascriptShimAssembler.readFragmentsAndAssemble(configuration) {
            File(raw, "afirma_shim_${it.name.lowercase()}.js").reader(Charsets.UTF_8)
        }
        val body = File(raw, "afirma_shim_sevilla_atse.js").readText(Charsets.UTF_8)
        assertEquals(enabled.replace(body, "").replace(
            "const sevillaAtseCompatibilityEnabled = true;", "const sevillaAtseCompatibilityEnabled = false;",
        ), output)
    }

    @Test
    fun disabledAccedaOmitsOnlyItsBodyAndDoesNotReadItsResource() {
        val reads = mutableListOf<AfirmaJavascriptShimFragment>()
        val disabled = configuration.copy(flags = configuration.flags.copy(acceda = false))
        val output = AfirmaJavascriptShimAssembler.readFragmentsAndAssemble(disabled) {
            reads += it
            check(it != AfirmaJavascriptShimFragment.ACCEDA)
            File(raw, "afirma_shim_${it.name.lowercase()}.js").reader(Charsets.UTF_8)
        }
        assertEquals(AfirmaJavascriptShimFragment.entries - AfirmaJavascriptShimFragment.ACCEDA, reads)
        assertFalse(output.contains("https://sede.administracionespublicas.gob.es"))
        assertFalse(output.contains("const accedaExtraProperties ="))
        assertFalse(output.contains("__JFM_"))
        assertTrue(output.contains("const accedaCompatibilityEnabled = false;"))
        val enabled = AfirmaJavascriptShimAssembler.readFragmentsAndAssemble(configuration) {
            File(raw, "afirma_shim_${it.name.lowercase()}.js").reader(Charsets.UTF_8)
        }
        val body = File(raw, "afirma_shim_acceda.js").readText(Charsets.UTF_8)
        assertEquals(enabled.replace(body, "").replace(
            "const accedaCompatibilityEnabled = true;", "const accedaCompatibilityEnabled = false;",
        ), output)
    }

    @Test
    fun disabledXuntaOmitsOnlyItsBodyAndDoesNotReadItsResource() {
        val reads = mutableListOf<AfirmaJavascriptShimFragment>()
        val disabled = configuration.copy(flags = configuration.flags.copy(xuntaGalicia = false))
        val output = AfirmaJavascriptShimAssembler.readFragmentsAndAssemble(disabled) {
            reads += it
            check(it != AfirmaJavascriptShimFragment.XUNTA)
            File(raw, "afirma_shim_${it.name.lowercase()}.js").reader(Charsets.UTF_8)
        }
        assertEquals(AfirmaJavascriptShimFragment.entries - AfirmaJavascriptShimFragment.XUNTA, reads)
        assertFalse(output.contains("https://sede.xunta.gal"))
        assertFalse(output.contains("function isExactXuntaProperties"))
        assertFalse(output.contains("__JFM_"))
        assertTrue(output.contains("const xSel = false;"))
        val enabled = AfirmaJavascriptShimAssembler.readFragmentsAndAssemble(configuration) {
            File(raw, "afirma_shim_${it.name.lowercase()}.js").reader(Charsets.UTF_8)
        }
        val body = File(raw, "afirma_shim_xunta.js").readText(Charsets.UTF_8)
        assertEquals(enabled.replace(body, "").replace(
            "const xSel = true;", "const xSel = false;",
        ), output)
    }

    @Test
    fun disabledCanariasOmitsOnlyItsBodyAndDoesNotReadItsResource() {
        val reads = mutableListOf<AfirmaJavascriptShimFragment>()
        val disabled = configuration.copy(flags = configuration.flags.copy(canarias = false))
        val output = AfirmaJavascriptShimAssembler.readFragmentsAndAssemble(disabled) {
            reads += it
            check(it != AfirmaJavascriptShimFragment.CANARIAS)
            File(raw, "afirma_shim_${it.name.lowercase()}.js").reader(Charsets.UTF_8)
        }
        assertEquals(AfirmaJavascriptShimFragment.entries - AfirmaJavascriptShimFragment.CANARIAS, reads)
        assertFalse(output.contains("https://sede.gobiernodecanarias.org"))
        assertFalse(output.contains("function isExactCanariasChallenge"))
        assertFalse(output.contains("__JFM_"))
        assertTrue(output.contains("const canariasCompatibilityEnabled = false;"))
        val enabled = AfirmaJavascriptShimAssembler.readFragmentsAndAssemble(configuration) {
            File(raw, "afirma_shim_${it.name.lowercase()}.js").reader(Charsets.UTF_8)
        }
        val body = File(raw, "afirma_shim_canarias.js").readText(Charsets.UTF_8)
        assertEquals(enabled.replace(body, "").replace(
            "const canariasCompatibilityEnabled = true;", "const canariasCompatibilityEnabled = false;",
        ), output)
    }

    @Test
    fun disabledMinecoOmitsOnlyItsBodyAndDoesNotReadItsResource() {
        val reads = mutableListOf<AfirmaJavascriptShimFragment>()
        val disabled = configuration.copy(flags = configuration.flags.copy(mineco = false))
        val output = AfirmaJavascriptShimAssembler.readFragmentsAndAssemble(disabled) {
            reads += it
            check(it != AfirmaJavascriptShimFragment.MINECO)
            File(raw, "afirma_shim_${it.name.lowercase()}.js").reader(Charsets.UTF_8)
        }
        assertEquals(AfirmaJavascriptShimFragment.entries - AfirmaJavascriptShimFragment.MINECO, reads)
        assertFalse(output.contains("https://serviciosede.mineco.gob.es"))
        assertFalse(output.contains("const minecoExtraProperties ="))
        assertFalse(output.contains("__JFM_"))
        assertTrue(output.contains("const minecoCompatibilityEnabled = false;"))
        val enabled = AfirmaJavascriptShimAssembler.readFragmentsAndAssemble(configuration) {
            File(raw, "afirma_shim_${it.name.lowercase()}.js").reader(Charsets.UTF_8)
        }
        val body = File(raw, "afirma_shim_mineco.js").readText(Charsets.UTF_8)
        assertEquals(enabled.replace(body, "").replace(
            "const minecoCompatibilityEnabled = true;", "const minecoCompatibilityEnabled = false;",
        ), output)
    }

    @Test
    fun disabledFuerteventuraOmitsOnlyItsBodyAndDoesNotReadItsResource() {
        val reads = mutableListOf<AfirmaJavascriptShimFragment>()
        val disabled = configuration.copy(flags = configuration.flags.copy(fuerteventura = false))
        val output = AfirmaJavascriptShimAssembler.readFragmentsAndAssemble(disabled) {
            reads += it
            check(it != AfirmaJavascriptShimFragment.FUERTEVENTURA)
            File(raw, "afirma_shim_${it.name.lowercase()}.js").reader(Charsets.UTF_8)
        }
        assertEquals(AfirmaJavascriptShimFragment.entries - AfirmaJavascriptShimFragment.FUERTEVENTURA, reads)
        assertFalse(output.contains("https://sede.cabildofuer.es"))
        assertFalse(output.contains("const fuerteventuraExtraProperties ="))
        assertFalse(output.contains("__JFM_"))
        assertTrue(output.contains("const fuerteventuraCompatibilityEnabled = false;"))
        val enabled = AfirmaJavascriptShimAssembler.readFragmentsAndAssemble(configuration) {
            File(raw, "afirma_shim_${it.name.lowercase()}.js").reader(Charsets.UTF_8)
        }
        val body = File(raw, "afirma_shim_fuerteventura.js").readText(Charsets.UTF_8)
        assertEquals(enabled.replace(body, "").replace(
            "const fuerteventuraCompatibilityEnabled = true;", "const fuerteventuraCompatibilityEnabled = false;",
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
    val euskadiRestored = restoreEuskadiPostExtraction(assembled, flags.euskadiClientAuthPost)
    val caibRestored = restoreCaibBatchExtraction(euskadiRestored, flags.caibBatch)
    val lugoRestored = restoreLugoBatchExtraction(caibRestored, flags.lugoBatch)
    val melillaRestored = restoreMelillaBatchExtraction(lugoRestored, flags.melillaBatch)
    val badajozRestored = restoreBadajozExtraction(melillaRestored, flags.badajoz)
    val ugrRestored = restoreUgrExtraction(legacy, badajozRestored, flags.ugr)
    val sevillaRestored = restoreSevillaAtseExtraction(legacy, ugrRestored, flags.sevillaAtse)
    val accedaRestored = restoreAccedaExtraction(legacy, sevillaRestored, flags.acceda)
    val xuntaRestored = restoreXuntaExtraction(legacy, accedaRestored, flags.xuntaGalicia)
    val canariasRestored = restoreCanariasExtraction(legacy, xuntaRestored, flags.canarias)
    val minecoRestored = restoreMinecoExtraction(legacy, canariasRestored, flags.mineco)
    val fuerteventuraRestored = restoreFuerteventuraExtraction(legacy, minecoRestored, flags.fuerteventura)
    val granCanariaRestored = restoreGranCanariaExtraction(legacy, fuerteventuraRestored, flags.granCanaria)
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

private fun restoreFuerteventuraExtraction(legacy: String, actual: String, enabled: Boolean): String {
    val constants = legacy.substring(legacy.indexOf("  const fuerteventuraOrigin ="), legacy.indexOf("  const minecoOrigin ="))
    val start = legacy.indexOf("    const isFuerteventuraOrigin =")
    val branch = legacy.substring(start, legacy.indexOf("    if (isFuerteventuraOrigin && !isExactFuerteventuraCall)", start))
    val module = "\n  matchesFuerteventuraSign = (args, successCallback, errorCallback) => {\n" +
        constants.replace("  const", "    const").replace("\n    \"", "\n      \"") + branch +
        "    return isFuerteventuraOrigin ? isExactFuerteventuraCall : null;\n  };\n"
    assertEquals(enabled, actual.contains(module))
    assertEquals(actual.indexOf(module), actual.lastIndexOf(module))
    return actual.replace(module, "")
        .replace(
            "  // null: not applicable; false: reject; true: exact tuple.\n  let matchesFuerteventuraSign = () => null;\n",
            constants,
        )
        .replace("    const isExactFuerteventuraCall = matchesFuerteventuraSign(args, successCallback, errorCallback);\n", branch)
        .replace("if (isExactFuerteventuraCall === false)", "if (isFuerteventuraOrigin && !isExactFuerteventuraCall)")
}

private fun restoreMinecoExtraction(legacy: String, actual: String, enabled: Boolean): String {
    val constants = legacy.substring(legacy.indexOf("  const minecoOrigin ="), legacy.indexOf("  const policiaProcedurePage ="))
    val start = legacy.indexOf("    const isMinecoOrigin =")
    val branch = legacy.substring(start, legacy.indexOf("    if (isMinecoOrigin && !isExactMinecoCall)", start))
    val module = "\n  matchesMinecoSign = (args, successCallback, errorCallback) => {\n" +
        constants.replace("  const", "    const").replace("\n    \"", "\n      \"") + branch +
        "    return isMinecoOrigin ? isExactMinecoCall : null;\n  };\n"
    assertEquals(enabled, actual.contains(module))
    assertEquals(actual.indexOf(module), actual.lastIndexOf(module))
    return actual.replace(module, "")
        .replace(
            "  // null: not applicable; false: reject; true: exact tuple.\n  let matchesMinecoSign = () => null;\n",
            constants,
        )
        .replace("    const isExactMinecoCall = matchesMinecoSign(args, successCallback, errorCallback);\n", branch)
        .replace("if (isExactMinecoCall === false)", "if (isMinecoOrigin && !isExactMinecoCall)")
}

private fun restoreCanariasExtraction(legacy: String, actual: String, enabled: Boolean): String {
    val constants = legacy.substring(
        legacy.indexOf("  const canariasOrigin ="), legacy.indexOf("  const granCanariaExtraProperties ="),
    )
    val helpers = legacy.substring(
        legacy.indexOf("  function isExactCanariasChallenge"), legacy.indexOf("  function isExactXuntaProperties"),
    )
    val start = legacy.indexOf("    const isCanariasOrigin =")
    val branch = legacy.substring(start, legacy.indexOf("    if (isCanariasOrigin && !isExactCanariasCall)", start))
    val module = "\n  matchesCanariasSign = (args, successCallback, errorCallback) => {\n" +
        constants.replace("  const", "    const").replace("\n    \"", "\n      \"") + "\n" +
        helpers.split('\n').dropLast(1)
            .joinToString("") { if (it.isBlank()) "\n" else "  $it\n" } +
        branch + "    return isCanariasOrigin ? isExactCanariasCall : null;\n  };\n"
    assertEquals(enabled, actual.contains(module))
    assertEquals(actual.indexOf(module), actual.lastIndexOf(module))
    return actual.replace(module, "")
        .replace(
            "  // null: not applicable; false: reject; true: exact tuple.\n  let matchesCanariasSign = () => null;\n",
            constants,
        )
        .replace("    const isExactCanariasCall = matchesCanariasSign(args, successCallback, errorCallback);\n", branch)
        .replace("if (isExactCanariasCall === false)", "if (isCanariasOrigin && !isExactCanariasCall)")
        .replace("  function isExactXuntaProperties", helpers + "  function isExactXuntaProperties")
}

private fun restoreXuntaExtraction(legacy: String, actual: String, enabled: Boolean): String {
    val constants = legacy.substring(legacy.indexOf("  const xuntaOrigin ="), legacy.indexOf("  const maxUriChars ="))
    val helpers = legacy.substring(
        legacy.indexOf("  function isExactXuntaProperties"), legacy.indexOf("  function interceptCertificateSelection"),
    )
    val selectionStart = legacy.indexOf("xSel && location.href === xuntaPage")
    val selection = legacy.substring(selectionStart, legacy.indexOf(") {\n      valid = true;", selectionStart))
    val start = legacy.indexOf("    const isXuntaOrigin =")
    val branch = legacy.substring(start, legacy.indexOf("    if (isXuntaOrigin && !isExactXuntaCall)", start))
    fun indent(source: String): String = source.split('\n').dropLast(1)
        .joinToString("") { if (it.isBlank()) "\n" else "  $it\n" }
    val module = "\n  {\n" + indent(constants) + "\n" + indent(helpers) +
        "    matchesXuntaSelection = a =>\n      " + selection + ";\n\n" +
        "    matchesXuntaSign = (args, successCallback, errorCallback) => {\n" + indent(branch) +
        "      return isXuntaOrigin ? isExactXuntaCall : null;\n    };\n  }\n"
    assertEquals(enabled, actual.contains(module))
    assertEquals(actual.indexOf(module), actual.lastIndexOf(module))
    return actual.replace(module, "")
        .replace(
            "  // null: not applicable; false: reject; true: exact tuple.\n" +
                "  let matchesXuntaSign = () => null;\n  let matchesXuntaSelection = () => false;\n",
            constants,
        )
        .replace("matchesXuntaSelection(a)", selection)
        .replace("    const isExactXuntaCall = matchesXuntaSign(args, successCallback, errorCallback);\n", branch)
        .replace("if (isExactXuntaCall === false)", "if (isXuntaOrigin && !isExactXuntaCall)")
        .replace("  function interceptCertificateSelection", helpers + "  function interceptCertificateSelection")
}

private fun restoreAccedaExtraction(legacy: String, actual: String, enabled: Boolean): String {
    val constants = legacy.substring(legacy.indexOf("  const accedaOrigin ="), legacy.indexOf("  const badajozOrigin ="))
    val start = legacy.indexOf("    const isAccedaOrigin =")
    val branch = legacy.substring(start, legacy.indexOf("    if (isAccedaOrigin && !isExactAccedaCall)", start))
    val module = "\n  matchesAccedaSign = (args, successCallback, errorCallback) => {\n" +
        constants.replace("  const", "    const").replace("\n    \"", "\n      \"") + branch +
        "    return isAccedaOrigin ? isExactAccedaCall : null;\n  };\n"
    assertEquals(enabled, actual.contains(module))
    assertEquals(actual.indexOf(module), actual.lastIndexOf(module))
    return actual.replace(module, "")
        .replace(
            "  // null: not applicable; false: reject; true: exact tuple.\n  let matchesAccedaSign = () => null;\n",
            constants,
        )
        .replace("    const isExactAccedaCall = matchesAccedaSign(args, successCallback, errorCallback);\n", branch)
        .replace("if (isExactAccedaCall === false)", "if (isAccedaOrigin && !isExactAccedaCall)")
}

private fun restoreSevillaAtseExtraction(legacy: String, actual: String, enabled: Boolean): String {
    fun section(start: String, end: String): String {
        val at = legacy.indexOf(start)
        return legacy.substring(at, legacy.indexOf(end, at + start.length))
    }
    fun indent(source: String, spaces: Int = 2): String = source.split('\n').dropLast(1)
        .joinToString("") { if (it.isBlank()) "\n" else " ".repeat(spaces) + it + "\n" }
    val constants = section("  const sevillaAtseOrigin =", "  const airefOrigin =")
    val challenge = section("  function isValidSevillaAtseChallenge", "  function isValidAirefPayload")
    val page = section("  function isExactSevillaAtsePage", "  if (functionalSigningEnabled && sevillaAtseCompatibilityEnabled)")
    val click = section("  if (functionalSigningEnabled && sevillaAtseCompatibilityEnabled)", "  function interceptSevillaAtseSetupCall")
    val setup = section("  function interceptSevillaAtseSetupCall", "  function interceptUgrSetupCall")
    val hooks = section("      if (sevillaAtseCompatibilityEnabled)", "      if (includeUgrSetup)")
    val sign = section("    const isSevillaAtseOrigin =", "    const isAccedaOrigin =")
    val branch = sign.substringBefore("    if (isSevillaAtseOrigin && !isExactSevillaAtseCall)")
    val assignedSetup = setup.replace(
        "  function interceptSevillaAtseSetupCall(call, args) {", "  interceptSevillaAtseSetupCall = (call, args) => {",
    ).removeSuffix("  }\n\n") + "  };\n\n"
    val module = "\n  {\n" + indent(constants) + "\n" + indent(challenge) + indent(page) + indent(assignedSetup) +
        "    installSevillaAtseClickHook = () => {\n" + indent(click, 4) + "    };\n\n" +
        "    installSevillaAtseMethodHooks = value => {\n" + hooks + "    };\n\n" +
        "    matchesSevillaAtseSign = (args, successCallback, errorCallback) => {\n" + indent(branch) +
        "      if (isSevillaAtseOrigin && !isExactSevillaAtseCall) sevillaAtseSetupState = 0;\n" +
        "      if (isExactSevillaAtseCall) sevillaAtseSetupState = 0;\n" +
        "      return isSevillaAtseOrigin ? isExactSevillaAtseCall : null;\n    };\n  }\n"
    val stub = "  // null: not applicable; false: reject; true: exact tuple.\n" +
        "  let matchesSevillaAtseSign = () => null;\n" +
        "  let interceptSevillaAtseSetupCall = () => false;\n" +
        "  let installSevillaAtseClickHook = () => {};\n" +
        "  let installSevillaAtseMethodHooks = () => {};\n"
    val dispatch = "    const isExactSevillaAtseCall = matchesSevillaAtseSign(args, successCallback, errorCallback);\n" +
        "    if (isExactSevillaAtseCall === false) {\n      rejectDirectCall(errorCallback, \"INVALID_REQUEST\");\n      return true;\n    }\n"
    assertEquals(enabled, actual.contains(module))
    assertEquals(actual.indexOf(module), actual.lastIndexOf(module))
    return actual.replace(module, "").replace(stub, constants).replace(dispatch, sign)
        .replace("  installSevillaAtseClickHook();\n\n", page + click + setup)
        .replace("      installSevillaAtseMethodHooks(value);\n", hooks)
        .replace("  function interceptCertificateSelection", challenge + "  function interceptCertificateSelection")
}

private fun restoreUgrExtraction(legacy: String, actual: String, enabled: Boolean): String {
    fun section(start: String, end: String): String {
        val at = legacy.indexOf(start)
        return legacy.substring(at, legacy.indexOf(end, at + start.length))
    }
    fun indent(source: String): String = source.split('\n').dropLast(1)
        .joinToString("") { if (it.isBlank()) "\n" else "  $it\n" }
    val origin = section("  const ugrOrigin =", "  const cantabriaOrigin =")
    val constants = section("  const ugrLiteral =", "  const jccmOrigin =")
    val setup = section("  function interceptUgrSetupCall", "  function receiveMelillaBatchResult")
    val hooks = section("      if (includeUgrSetup)", "    } catch (_) {")
    val branch = section("    const isExactUgrLiteralCall =", "    const isExactCantabriaCall =")
    val assignedSetup = setup.replace(
        "  function interceptUgrSetupCall(call, args) {", "  interceptUgrSetupCall = (call, args) => {",
    ).removeSuffix("  }\n\n") + "  };\n\n"
    val module = "\n  {\n" + indent(origin + constants) + "\n" + indent(assignedSetup) +
        "    installUgrMethodHooks = (value, includeUgrSetup) => {\n" + hooks + "    };\n\n" +
        "    encodeUgrLiteral = () => ugrLiteralBase64;\n" +
        "    matchesUgrSign = args => {\n      return " + branch.substringAfter("=\n").trimStart() + "    };\n  }\n"
    val stub = "  let matchesUgrSign = () => false;\n  let interceptUgrSetupCall = () => false;\n" +
        "  let installUgrMethodHooks = () => {};\n  let encodeUgrLiteral = () => null;\n"
    assertEquals(enabled, actual.contains(module))
    assertEquals(actual.indexOf(module), actual.lastIndexOf(module))
    return actual.replace(module, "").replace(stub, origin)
        .replace("  let matchesJccmSign = () => false;\n", constants + "  let matchesJccmSign = () => false;\n")
        .replace("  function receiveMelillaBatchResult", setup + "  function receiveMelillaBatchResult")
        .replace("      installUgrMethodHooks(value, includeUgrSetup);\n", hooks)
        .replace("    const isExactUgrLiteralCall = matchesUgrSign(args);\n", branch)
        .replace("isExactUgrLiteralCall ? encodeUgrLiteral() :", "isExactUgrLiteralCall ? ugrLiteralBase64 :")
}

private fun restoreBadajozExtraction(actual: String, enabled: Boolean): String {
    val module = "\n  {\n    let badajozSignHookReadyDiagnosticPosted = false;\n    const badajozDiagnosticWrappedFunctions = new WeakSet();\n    const badajozDiagnosticPostedStages = new Set();\n    const badajozOrigin = \"https://sede.dip-badajoz.es\";\n    const badajozExtraProperties =\n      \"policy=FirmaAGE\\nheadless=true\\nfilters=nonexpired:true;authCert:true\";\n\n    function markBadajozSignHookReady() {\n      try {\n        window.__jfmBadajozSignHookReady = true;\n      } catch (_) {}\n      if (!badajozSignHookReadyDiagnosticPosted) {\n        badajozSignHookReadyDiagnosticPosted = true;\n        postShimDiagnostic(\"BADAJOZ_SIGN_HOOK_READY\");\n      }\n    }\n\n    function postBadajozDiagnosticOnce(stage) {\n      if (badajozDiagnosticPostedStages.has(stage)) {\n        return;\n      }\n      badajozDiagnosticPostedStages.add(stage);\n      postShimDiagnostic(stage);\n    }\n\n    function wrapBadajozDiagnostic(target, name, stage) {\n      if (target == null || (typeof target !== \"object\" && typeof target !== \"function\")) {\n        return;\n      }\n      const current = target[name];\n      if (typeof current !== \"function\" || badajozDiagnosticWrappedFunctions.has(current)) {\n        return;\n      }\n      const wrapped = function() {\n        postBadajozDiagnosticOnce(stage);\n        return Reflect.apply(current, this, arguments);\n      };\n      try {\n        target[name] = wrapped;\n        badajozDiagnosticWrappedFunctions.add(wrapped);\n      } catch (_) {}\n    }\n\n    getBadajozExtraProperties = () => badajozExtraProperties;\n\n    notifyBadajozSignHook = call => {\n      if (call === \"SIGN\" && badajozCompatibilityEnabled &&\n          !badajozSignHookReadyDiagnosticPosted) {\n        markBadajozSignHookReady();\n      }\n    };\n\n    repairBadajozDiagnostics = () => {\n        if (badajozCompatibilityEnabled) {\n          wrapBadajozDiagnostic(window, \"pulsarFirmarIdentificate\", \"BADAJOZ_PULSAR_SIGN_ENTRY\");\n          wrapBadajozDiagnostic(window, \"firmar\", \"BADAJOZ_FIRMAR_ENTRY\");\n          wrapBadajozDiagnostic(window.MiniApplet, \"getBase64FromText\", \"BADAJOZ_GET_BASE64_ENTRY\");\n          wrapBadajozDiagnostic(window.MiniApplet, \"echo\", \"BADAJOZ_ECHO_ENTRY\");\n          wrapBadajozDiagnostic(window.MiniApplet, \"setForceWSMode\", \"BADAJOZ_FORCE_WS_ENTRY\");\n        }\n    };\n\n    installBadajozLateHooks = () => {\n      if (badajozCompatibilityEnabled) {\n        postShimDiagnostic(\"BADAJOZ_LATE_REWRAP_STARTED\");\n        document.addEventListener(\"click\", event => {\n          if (event.target && event.target.id === \"firmar\") postBadajozDiagnosticOnce(\"BADAJOZ_CERT_BUTTON_CLICK\");\n        }, true);\n      }\n    };\n\n    matchesBadajozSign = (args, successCallback, errorCallback) => {\n      const isBadajozOrigin =\n        badajozCompatibilityEnabled && window.location.origin === badajozOrigin;\n      const isExactBadajozCall =\n        isBadajozOrigin && args.length === 6 &&\n        typeof args[0] === \"string\" && args[0].length > 0 &&\n        args[0].length <= maxDirectDataChars && base64Pattern.test(args[0]) &&\n        args[1] === \"SHA256withRSA\" && typeof args[2] === \"string\" && args[2].toLowerCase() === \"cades\" &&\n        equivalentLiteralProperties(args[3], badajozExtraProperties) &&\n        typeof successCallback === \"function\" && typeof errorCallback === \"function\";\n      return isBadajozOrigin ? isExactBadajozCall : null;\n    };\n  }\n"
    assertEquals(enabled, actual.contains(module))
    assertEquals(actual.indexOf(module), actual.lastIndexOf(module))
    return actual.replace(module, "")
        .replace("extraProperties: isExactBadajozCall ? getBadajozExtraProperties() : args[3]", "extraProperties: isExactBadajozCall ? badajozExtraProperties : args[3]")
        .replace("if (isExactBadajozCall === false)", "if (isBadajozOrigin && !isExactBadajozCall)")
        .replace("    const isExactBadajozCall = matchesBadajozSign(args, successCallback, errorCallback);\n", "    const isBadajozOrigin =\n      badajozCompatibilityEnabled && window.location.origin === badajozOrigin;\n    const isExactBadajozCall =\n      isBadajozOrigin && args.length === 6 &&\n      typeof args[0] === \"string\" && args[0].length > 0 &&\n      args[0].length <= maxDirectDataChars && base64Pattern.test(args[0]) &&\n      args[1] === \"SHA256withRSA\" && typeof args[2] === \"string\" && args[2].toLowerCase() === \"cades\" &&\n      equivalentLiteralProperties(args[3], badajozExtraProperties) &&\n      typeof successCallback === \"function\" && typeof errorCallback === \"function\";\n")
        .replace("    if (typeof current === \"function\") notifyBadajozSignHook(call);\n", "    if (typeof current === \"function\" && call === \"SIGN\" && badajozCompatibilityEnabled &&\n        !badajozSignHookReadyDiagnosticPosted) {\n      markBadajozSignHookReady();\n    }\n")
        .replace("        notifyBadajozSignHook(call);\n", "        if (call === \"SIGN\" && badajozCompatibilityEnabled &&\n            !badajozSignHookReadyDiagnosticPosted) {\n          markBadajozSignHookReady();\n        }\n")
        .replace("    installBadajozLateHooks();\n", "    if (badajozCompatibilityEnabled) {\n      postShimDiagnostic(\"BADAJOZ_LATE_REWRAP_STARTED\");\n      document.addEventListener(\"click\", event => {\n        if (event.target && event.target.id === \"firmar\") postBadajozDiagnosticOnce(\"BADAJOZ_CERT_BUTTON_CLICK\");\n      }, true);\n    }\n")
        .replace("        repairBadajozDiagnostics();\n", "        if (badajozCompatibilityEnabled) {\n          wrapBadajozDiagnostic(window, \"pulsarFirmarIdentificate\", \"BADAJOZ_PULSAR_SIGN_ENTRY\");\n          wrapBadajozDiagnostic(window, \"firmar\", \"BADAJOZ_FIRMAR_ENTRY\");\n          wrapBadajozDiagnostic(window.MiniApplet, \"getBase64FromText\", \"BADAJOZ_GET_BASE64_ENTRY\");\n          wrapBadajozDiagnostic(window.MiniApplet, \"echo\", \"BADAJOZ_ECHO_ENTRY\");\n          wrapBadajozDiagnostic(window.MiniApplet, \"setForceWSMode\", \"BADAJOZ_FORCE_WS_ENTRY\");\n        }\n")
        .replace("  function wrapMiniApplet(", "  function wrapBadajozDiagnostic(target, name, stage) {\n    if (target == null || (typeof target !== \"object\" && typeof target !== \"function\")) {\n      return;\n    }\n    const current = target[name];\n    if (typeof current !== \"function\" || badajozDiagnosticWrappedFunctions.has(current)) {\n      return;\n    }\n    const wrapped = function() {\n      postBadajozDiagnosticOnce(stage);\n      return Reflect.apply(current, this, arguments);\n    };\n    try {\n      target[name] = wrapped;\n      badajozDiagnosticWrappedFunctions.add(wrapped);\n    } catch (_) {}\n  }\n\n  function wrapMiniApplet(")
        .replace("  const euskadiProfileId =", "  const badajozOrigin = \"https://sede.dip-badajoz.es\";\n  const badajozExtraProperties =\n    \"policy=FirmaAGE\\nheadless=true\\nfilters=nonexpired:true;authCert:true\";\n  const euskadiProfileId =")
        .replace("  postShimDiagnostic(\"SCRIPT_INSTALLED\");", "  function postBadajozDiagnosticOnce(stage) {\n    if (badajozDiagnosticPostedStages.has(stage)) {\n      return;\n    }\n    badajozDiagnosticPostedStages.add(stage);\n    postShimDiagnostic(stage);\n  }\n\n  postShimDiagnostic(\"SCRIPT_INSTALLED\");")
        .replace("  function postShimDiagnostic", "  function markBadajozSignHookReady() {\n    try {\n      window.__jfmBadajozSignHookReady = true;\n    } catch (_) {}\n    if (!badajozSignHookReadyDiagnosticPosted) {\n      badajozSignHookReadyDiagnosticPosted = true;\n      postShimDiagnostic(\"BADAJOZ_SIGN_HOOK_READY\");\n    }\n  }\n\n  function postShimDiagnostic")
        .replace("  // null: not applicable; false: reject; true: exact tuple.\n  let matchesBadajozSign = () => null;\n  let getBadajozExtraProperties = () => null;\n  let notifyBadajozSignHook = () => {};\n  let repairBadajozDiagnostics = () => {};\n  let installBadajozLateHooks = () => {};\n", "  let badajozSignHookReadyDiagnosticPosted = false;\n  const badajozDiagnosticWrappedFunctions = new WeakSet();\n  const badajozDiagnosticPostedStages = new Set();\n")
}

private fun restoreMelillaBatchExtraction(actual: String, enabled: Boolean): String {
    val module = "\n  {\n    let activeMelillaBatch = null;\n    function isMelillaBatchPage() {\n      return functionalSigningEnabled && melillaBatchCompatibilityEnabled &&\n        window.location.origin === staBatchOrigin;\n    }\n\n    function isSafeBatchIdentifier(value) {\n      return typeof value === \"string\" && value.length > 0 &&\n        value.length <= maxBatchIdentifierChars &&\n        !Array.from(value).some(character => /\\s/.test(character) ||\n          character.charCodeAt(0) < 0x20 || character.charCodeAt(0) === 0x7f);\n    }\n\n    function batchExtraParams(format) {\n      if (format === \"PAdES\") {\n        return \"signatureSubFilter=ETSI.CAdES.detached\";\n      }\n      if (format === \"XAdES\") {\n        return \"mode=implicit\";\n      }\n      return null;\n    }\n\n    function isSupportedBatchFormat(value) {\n      return value === \"CAdES\" || value === \"PAdES\" || value === \"XAdES\";\n    }\n\n    rememberMelillaBatchCreate = (args) => {\n      if (!isMelillaBatchPage()) {\n        activeMelillaBatch = null;\n        return;\n      }\n      if (args.length !== 4 || args[0] !== \"SHA256withRSA\" ||\n          !isSupportedBatchFormat(args[1]) || args[2] !== \"sign\" || args[3] !== null) {\n        activeMelillaBatch = null;\n        return;\n      }\n      activeMelillaBatch = {\n        algorithm: args[0],\n        format: args[1],\n        suboperation: args[2],\n        documents: [],\n        invalid: false\n      };\n    };\n\n    rememberMelillaBatchDocument = (args) => {\n      const batch = activeMelillaBatch;\n      if (!isMelillaBatchPage() || !batch) {\n        return;\n      }\n      const documentFormat = args[2];\n      const documentSuboperation = args[3];\n      const effectiveFormat = documentFormat === null ? batch.format : documentFormat;\n      const valid = args.length === 5 && isSafeBatchIdentifier(args[0]) &&\n        typeof args[1] === \"string\" && args[1].length > 0 &&\n        args[1].length <= maxBatchUrlChars && !args[1].includes(\"\\u0000\") &&\n        (documentFormat === null || isSupportedBatchFormat(documentFormat)) &&\n        (documentSuboperation === null || documentSuboperation === \"sign\") &&\n        args[4] === batchExtraParams(effectiveFormat) &&\n        !batch.documents.some(document => document.id === args[0]) &&\n        batch.documents.length < maxBatchDocuments;\n      if (!valid) {\n        batch.invalid = true;\n        return;\n      }\n      const document = {\n        id: args[0],\n        datareference: args[1]\n      };\n      if (documentFormat !== null) {\n        document.format = documentFormat;\n      }\n      if (documentSuboperation !== null) {\n        document.suboperation = documentSuboperation;\n      }\n      batch.documents.push(document);\n    };\n\n    interceptMelillaBatchSign = (args) => {\n      if (!isMelillaBatchPage()) {\n        return false;\n      }\n      const errorCallback = args[5];\n      const batch = activeMelillaBatch;\n      if (args.length !== 6 || args[0] !== false || typeof args[1] !== \"string\" ||\n          args[1].length === 0 || args[1].length > maxBatchUrlChars ||\n          typeof args[2] !== \"string\" || args[2].length === 0 ||\n          args[2].length > maxBatchUrlChars || args[3] !== null ||\n          typeof args[4] !== \"function\" || typeof errorCallback !== \"function\" ||\n          !batch || batch.invalid || batch.documents.length === 0 || !probeDocumentId) {\n        rejectDirectCall(errorCallback, \"INVALID_REQUEST\");\n        return true;\n      }\n      if (!bridge || typeof bridge.postMessage !== \"function\") {\n        rejectDirectCall(errorCallback, \"PROTOCOL_FAILED\");\n        return true;\n      }\n      if (pendingCallbacks.size !== 0 || pendingBatchCallbacks.size !== 0) {\n        rejectDirectCall(errorCallback, \"PROTOCOL_FAILED\");\n        return true;\n      }\n      const batchRequestId = secureRequestId();\n      if (!batchRequestId) {\n        rejectDirectCall(errorCallback, \"PROTOCOL_FAILED\");\n        return true;\n      }\n      const timeoutId = setTimeout(() => {\n        const expired = clearPendingBatch(batchRequestId);\n        if (expired) {\n          notifyNativeCancel(batchRequestId, \"MINIAPPLET_BATCH_CANCEL\");\n          rejectDirectCall(expired.errorCallback, \"REQUEST_EXPIRED\");\n        }\n      }, signTimeoutMillis);\n      pendingBatchCallbacks.set(batchRequestId, {\n        successCallback: args[4],\n        errorCallback,\n        timeoutId\n      });\n      const message = {\n        type: \"MINIAPPLET_BATCH\",\n        documentId: probeDocumentId,\n        requestId: batchRequestId,\n        batchPreSignerUrl: args[1],\n        batchPostSignerUrl: args[2],\n        algorithm: batch.algorithm,\n        format: batch.format,\n        suboperation: batch.suboperation,\n        stopOnError: false,\n        documentos: batch.documents.map(document => ({ ...document }))\n      };\n      activeMelillaBatch = null;\n      try {\n        bridge.postMessage(JSON.stringify(message));\n      } catch (_) {\n        clearPendingBatch(batchRequestId);\n        rejectDirectCall(errorCallback, \"PROTOCOL_FAILED\");\n      }\n      return true;\n    };\n\n    resetMelillaBatch = () => { activeMelillaBatch = null; };\n\n    isMelillaBatchDocument = () =>\n      melillaBatchCompatibilityEnabled &&\n      window.location.origin === staBatchOrigin;\n\n    installMelillaBatchMethodHooks = (value, includeMelillaBatch) => {\n      if (includeMelillaBatch) {\n        installMethodHook(value, \"createBatch\", \"BATCH_CREATE\");\n        installMethodHook(value, \"addDocumentToBatch\", \"BATCH_ADD_DOCUMENT\");\n        installMethodHook(value, \"signBatchProcess\", \"BATCH_SIGN\");\n      }\n    };\n  }\n"
    assertEquals(enabled, actual.contains(module))
    assertEquals(actual.indexOf(module), actual.lastIndexOf(module))
    return actual.replace(module, "")
        .replace("    pendingBatchCallbacks.clear();\n    resetMelillaBatch();", "    pendingBatchCallbacks.clear();\n    activeMelillaBatch = null;")
        .replace("    const isStaBatchDocument = isMelillaBatchDocument();\n", "    const isStaBatchDocument = melillaBatchCompatibilityEnabled &&\n      window.location.origin === staBatchOrigin;\n")
        .replace("      installMelillaBatchMethodHooks(value, includeMelillaBatch);\n", "      if (includeMelillaBatch) {\n        installMethodHook(value, \"createBatch\", \"BATCH_CREATE\");\n        installMethodHook(value, \"addDocumentToBatch\", \"BATCH_ADD_DOCUMENT\");\n        installMethodHook(value, \"signBatchProcess\", \"BATCH_SIGN\");\n      }\n")
        .replace("  function interceptLugoSetupCall", "  function isMelillaBatchPage() {\n    return functionalSigningEnabled && melillaBatchCompatibilityEnabled &&\n      window.location.origin === staBatchOrigin;\n  }\n\n  function isSafeBatchIdentifier(value) {\n    return typeof value === \"string\" && value.length > 0 &&\n      value.length <= maxBatchIdentifierChars &&\n      !Array.from(value).some(character => /\\s/.test(character) ||\n        character.charCodeAt(0) < 0x20 || character.charCodeAt(0) === 0x7f);\n  }\n\n  function batchExtraParams(format) {\n    if (format === \"PAdES\") {\n      return \"signatureSubFilter=ETSI.CAdES.detached\";\n    }\n    if (format === \"XAdES\") {\n      return \"mode=implicit\";\n    }\n    return null;\n  }\n\n  function isSupportedBatchFormat(value) {\n    return value === \"CAdES\" || value === \"PAdES\" || value === \"XAdES\";\n  }\n\n  function rememberMelillaBatchCreate(args) {\n    if (!isMelillaBatchPage()) {\n      activeMelillaBatch = null;\n      return;\n    }\n    if (args.length !== 4 || args[0] !== \"SHA256withRSA\" ||\n        !isSupportedBatchFormat(args[1]) || args[2] !== \"sign\" || args[3] !== null) {\n      activeMelillaBatch = null;\n      return;\n    }\n    activeMelillaBatch = {\n      algorithm: args[0],\n      format: args[1],\n      suboperation: args[2],\n      documents: [],\n      invalid: false\n    };\n  }\n\n  function rememberMelillaBatchDocument(args) {\n    const batch = activeMelillaBatch;\n    if (!isMelillaBatchPage() || !batch) {\n      return;\n    }\n    const documentFormat = args[2];\n    const documentSuboperation = args[3];\n    const effectiveFormat = documentFormat === null ? batch.format : documentFormat;\n    const valid = args.length === 5 && isSafeBatchIdentifier(args[0]) &&\n      typeof args[1] === \"string\" && args[1].length > 0 &&\n      args[1].length <= maxBatchUrlChars && !args[1].includes(\"\\u0000\") &&\n      (documentFormat === null || isSupportedBatchFormat(documentFormat)) &&\n      (documentSuboperation === null || documentSuboperation === \"sign\") &&\n      args[4] === batchExtraParams(effectiveFormat) &&\n      !batch.documents.some(document => document.id === args[0]) &&\n      batch.documents.length < maxBatchDocuments;\n    if (!valid) {\n      batch.invalid = true;\n      return;\n    }\n    const document = {\n      id: args[0],\n      datareference: args[1]\n    };\n    if (documentFormat !== null) {\n      document.format = documentFormat;\n    }\n    if (documentSuboperation !== null) {\n      document.suboperation = documentSuboperation;\n    }\n    batch.documents.push(document);\n  }\n\n  function interceptMelillaBatchSign(args) {\n    if (!isMelillaBatchPage()) {\n      return false;\n    }\n    const errorCallback = args[5];\n    const batch = activeMelillaBatch;\n    if (args.length !== 6 || args[0] !== false || typeof args[1] !== \"string\" ||\n        args[1].length === 0 || args[1].length > maxBatchUrlChars ||\n        typeof args[2] !== \"string\" || args[2].length === 0 ||\n        args[2].length > maxBatchUrlChars || args[3] !== null ||\n        typeof args[4] !== \"function\" || typeof errorCallback !== \"function\" ||\n        !batch || batch.invalid || batch.documents.length === 0 || !probeDocumentId) {\n      rejectDirectCall(errorCallback, \"INVALID_REQUEST\");\n      return true;\n    }\n    if (!bridge || typeof bridge.postMessage !== \"function\") {\n      rejectDirectCall(errorCallback, \"PROTOCOL_FAILED\");\n      return true;\n    }\n    if (pendingCallbacks.size !== 0 || pendingBatchCallbacks.size !== 0) {\n      rejectDirectCall(errorCallback, \"PROTOCOL_FAILED\");\n      return true;\n    }\n    const batchRequestId = secureRequestId();\n    if (!batchRequestId) {\n      rejectDirectCall(errorCallback, \"PROTOCOL_FAILED\");\n      return true;\n    }\n    const timeoutId = setTimeout(() => {\n      const expired = clearPendingBatch(batchRequestId);\n      if (expired) {\n        notifyNativeCancel(batchRequestId, \"MINIAPPLET_BATCH_CANCEL\");\n        rejectDirectCall(expired.errorCallback, \"REQUEST_EXPIRED\");\n      }\n    }, signTimeoutMillis);\n    pendingBatchCallbacks.set(batchRequestId, {\n      successCallback: args[4],\n      errorCallback,\n      timeoutId\n    });\n    const message = {\n      type: \"MINIAPPLET_BATCH\",\n      documentId: probeDocumentId,\n      requestId: batchRequestId,\n      batchPreSignerUrl: args[1],\n      batchPostSignerUrl: args[2],\n      algorithm: batch.algorithm,\n      format: batch.format,\n      suboperation: batch.suboperation,\n      stopOnError: false,\n      documentos: batch.documents.map(document => ({ ...document }))\n    };\n    activeMelillaBatch = null;\n    try {\n      bridge.postMessage(JSON.stringify(message));\n    } catch (_) {\n      clearPendingBatch(batchRequestId);\n      rejectDirectCall(errorCallback, \"PROTOCOL_FAILED\");\n    }\n    return true;\n  }\n\n  function interceptLugoSetupCall")
        .replace("  let rememberMelillaBatchCreate = () => {};\n  let rememberMelillaBatchDocument = () => {};\n  let interceptMelillaBatchSign = () => false;\n  let resetMelillaBatch = () => {};\n  let installMelillaBatchMethodHooks = () => {};\n  let isMelillaBatchDocument = () => false;\n", "  let activeMelillaBatch = null;\n")
}

private fun restoreLugoBatchExtraction(actual: String, enabled: Boolean): String {
    val module = "\n  {\n    const lugoOrigin = \"https://sede.deputacionlugo.org\";\n    const lugoClientBase = \"https://sede.deputacionlugo.org/opencms\";\n    const lugoExtraProperties = \"mode=explicit\\nprecalculatedHashAlgorithm=SHA-256\\n\";\n\n    interceptLugoSetupCall = (call, args) => {\n      if (!lugoBatchCompatibilityEnabled || window.location.origin !== lugoOrigin) {\n        return false;\n      }\n      return call === \"LUGO_CARGAR_APP_AFIRMA\" &&\n        args.length === 1 && args[0] === lugoClientBase;\n    };\n\n    interceptLugoBatchSign = (args) => {\n      if (!lugoBatchCompatibilityEnabled || window.location.origin !== lugoOrigin) {\n        return false;\n      }\n      const errorCallback = args[5];\n      if (args.length !== 6 || typeof args[0] !== \"string\" || args[0].length === 0 ||\n          args[0].length > maxBatchResultChars || typeof args[1] !== \"string\" ||\n          args[1].length === 0 || args[1].length > maxBatchUrlChars ||\n          typeof args[2] !== \"string\" || args[2].length === 0 || args[2].length > maxBatchUrlChars ||\n          args[3] !== lugoExtraProperties || typeof args[4] !== \"function\" ||\n          typeof errorCallback !== \"function\") {\n        rejectDirectCall(errorCallback, \"INVALID_REQUEST\");\n        return true;\n      }\n      if (!bridge || typeof bridge.postMessage !== \"function\" || !probeDocumentId ||\n          pendingCallbacks.size !== 0 || pendingBatchCallbacks.size !== 0) {\n        rejectDirectCall(errorCallback, \"PROTOCOL_FAILED\");\n        return true;\n      }\n      const batchRequestId = secureRequestId();\n      if (!batchRequestId) { rejectDirectCall(errorCallback, \"PROTOCOL_FAILED\"); return true; }\n      const timeoutId = setTimeout(() => {\n        const expired = clearPendingBatch(batchRequestId);\n        if (expired) {\n          notifyNativeCancel(batchRequestId, \"LUGO_XML_BATCH_CANCEL\");\n          rejectDirectCall(expired.errorCallback, \"REQUEST_EXPIRED\");\n        }\n      }, signTimeoutMillis);\n      pendingBatchCallbacks.set(batchRequestId, {\n        successCallback: args[4], errorCallback, timeoutId, lugoXml: true\n      });\n      try {\n        bridge.postMessage(JSON.stringify({\n          type: \"LUGO_XML_BATCH\",\n          documentId: probeDocumentId,\n          requestId: batchRequestId,\n          batchXml: args[0],\n          batchPreSignerUrl: args[1],\n          batchPostSignerUrl: args[2],\n          extraProperties: args[3]\n        }));\n      } catch (_) {\n        clearPendingBatch(batchRequestId);\n        rejectDirectCall(errorCallback, \"PROTOCOL_FAILED\");\n      }\n      return true;\n    };\n\n    matchesLugoBatchDocument = () => {\n      const isLugoBatchDocument = lugoBatchCompatibilityEnabled &&\n        window.location.origin === lugoOrigin;\n      return isLugoBatchDocument;\n    };\n\n    installLugoBatchMethodHooks = (value, includeLugoBatch) => {\n      if (includeLugoBatch) {\n        installMethodHook(value, \"cargarAppAfirma\", \"LUGO_CARGAR_APP_AFIRMA\");\n        installMethodHook(value, \"signBatch\", \"LUGO_BATCH_SIGN\");\n      }\n    };\n  }\n"
    assertEquals(enabled, actual.contains(module))
    assertEquals(actual.indexOf(module), actual.lastIndexOf(module))
    return actual.replace(module, "")
        .replace("    const isLugoBatchDocument = matchesLugoBatchDocument();\n", "    const isLugoBatchDocument = lugoBatchCompatibilityEnabled &&\n      window.location.origin === lugoOrigin;\n")
        .replace("      installLugoBatchMethodHooks(value, includeLugoBatch);\n", "      if (includeLugoBatch) {\n        installMethodHook(value, \"cargarAppAfirma\", \"LUGO_CARGAR_APP_AFIRMA\");\n        installMethodHook(value, \"signBatch\", \"LUGO_BATCH_SIGN\");\n      }\n")
        .replace("\n  function caibRequestToken", "\n  function interceptLugoSetupCall(call, args) {\n    if (!lugoBatchCompatibilityEnabled || window.location.origin !== lugoOrigin) {\n      return false;\n    }\n    return call === \"LUGO_CARGAR_APP_AFIRMA\" &&\n      args.length === 1 && args[0] === lugoClientBase;\n  }\n\n  function interceptLugoBatchSign(args) {\n    if (!lugoBatchCompatibilityEnabled || window.location.origin !== lugoOrigin) {\n      return false;\n    }\n    const errorCallback = args[5];\n    if (args.length !== 6 || typeof args[0] !== \"string\" || args[0].length === 0 ||\n        args[0].length > maxBatchResultChars || typeof args[1] !== \"string\" ||\n        args[1].length === 0 || args[1].length > maxBatchUrlChars ||\n        typeof args[2] !== \"string\" || args[2].length === 0 || args[2].length > maxBatchUrlChars ||\n        args[3] !== lugoExtraProperties || typeof args[4] !== \"function\" ||\n        typeof errorCallback !== \"function\") {\n      rejectDirectCall(errorCallback, \"INVALID_REQUEST\");\n      return true;\n    }\n    if (!bridge || typeof bridge.postMessage !== \"function\" || !probeDocumentId ||\n        pendingCallbacks.size !== 0 || pendingBatchCallbacks.size !== 0) {\n      rejectDirectCall(errorCallback, \"PROTOCOL_FAILED\");\n      return true;\n    }\n    const batchRequestId = secureRequestId();\n    if (!batchRequestId) { rejectDirectCall(errorCallback, \"PROTOCOL_FAILED\"); return true; }\n    const timeoutId = setTimeout(() => {\n      const expired = clearPendingBatch(batchRequestId);\n      if (expired) {\n        notifyNativeCancel(batchRequestId, \"LUGO_XML_BATCH_CANCEL\");\n        rejectDirectCall(expired.errorCallback, \"REQUEST_EXPIRED\");\n      }\n    }, signTimeoutMillis);\n    pendingBatchCallbacks.set(batchRequestId, {\n      successCallback: args[4], errorCallback, timeoutId, lugoXml: true\n    });\n    try {\n      bridge.postMessage(JSON.stringify({\n        type: \"LUGO_XML_BATCH\",\n        documentId: probeDocumentId,\n        requestId: batchRequestId,\n        batchXml: args[0],\n        batchPreSignerUrl: args[1],\n        batchPostSignerUrl: args[2],\n        extraProperties: args[3]\n      }));\n    } catch (_) {\n      clearPendingBatch(batchRequestId);\n      rejectDirectCall(errorCallback, \"PROTOCOL_FAILED\");\n    }\n    return true;\n  }\n\n  function caibRequestToken")
        .replace("  let interceptLugoSetupCall = () => false;\n  let interceptLugoBatchSign = () => false;\n  let matchesLugoBatchDocument = () => false;\n  let installLugoBatchMethodHooks = () => {};\n", "  const lugoOrigin = \"https://sede.deputacionlugo.org\";\n  const lugoClientBase = \"https://sede.deputacionlugo.org/opencms\";\n  const lugoExtraProperties = \"mode=explicit\\nprecalculatedHashAlgorithm=SHA-256\\n\";\n")
}

private fun restoreCaibBatchExtraction(actual: String, enabled: Boolean): String {
    val module = "\n  {\n    const caibOrigin = \"https://intranet.caib.es\";\n    const caibClientBase = \"https://intranet.caib.es/portafibback\";\n    const caibSignerPathPattern = /^\\/portafibback\\/public\\/signmodule\\/requestPlugin\\/([A-Za-z0-9_-]{28})\\/-1\\/index\$/;\n    const caibSignatureIdPattern = /^[A-Za-z0-9_-]{40}\$/;\n    const caibBatchBase64Pattern = /^[A-Za-z0-9_-]+={0,2}\$/;\n\n    caibRequestToken = () => {\n      if (!caibBatchCompatibilityEnabled || window.location.origin !== caibOrigin) return null;\n      const match = window.location.pathname.match(caibSignerPathPattern);\n      return match ? match[1] : null;\n    };\n\n    function decodeCaibUrlBase64(value) {\n      if (typeof value !== \"string\" || !caibBatchBase64Pattern.test(value)) return null;\n      try {\n        const normalized = value.replace(/-/g, \"+\").replace(/_/g, \"/\") + \"=\".repeat((4 - value.length % 4) % 4);\n        return atob(normalized);\n      } catch (_) { return null; }\n    }\n\n    function caibSignatureId(batchXml, token) {\n      if (typeof batchXml !== \"string\" || batchXml.length === 0 || batchXml.length > 8192) return null;\n      const xml = decodeCaibUrlBase64(batchXml);\n      if (xml === null || xml.length > 6144) return null;\n      const ids = Array.from(xml.matchAll(/<singlesign\\s+Id=\"([A-Za-z0-9_-]{40})\">/g));\n      if (ids.length !== 1 || !caibSignatureIdPattern.test(ids[0][1])) return null;\n      return decodeCaibUrlBase64(ids[0][1]) === `\${token}|0` ? ids[0][1] : null;\n    }\n\n    function caibExtraProperties(signatureId) {\n      return \"mode=implicit\\n\" +\n        \"signatureSubFilter=ETSI.CAdES.detached\\n\" +\n        `SignatureId=\${signatureId}\\n` +\n        \"signReason=FORM-1.pdf\\n\" +\n        \"formatmobile=PAdEStri\\n\" +\n        \"formatbatch=PAdES\\n\" +\n        \"format=PAdES\\n\" +\n        \"filters.1=nonexpired:\\n\" +\n        \"allowSigningCertifiedPdfs=true\\n\" +\n        \"algorithm=SHA256withRSA\\n\";\n    }\n\n    function caibEndpoint(token, name) {\n      return `\${caibOrigin}/portafibback/public/signmodule/requestPlugin/\${token}/-1/\${name}`;\n    }\n\n    interceptCaibSetupCall = (call, args) => {\n      const token = caibRequestToken();\n      if (!token) return false;\n      if (call === \"CAIB_SET_FORCE_WS_MODE\") return args.length === 1 && args[0] === true;\n      if (call === \"CAIB_CARGAR_APP_AFIRMA\") return args.length === 1 && args[0] === caibClientBase;\n      if (call === \"CAIB_SET_SERVLETS\") {\n        return args.length === 2 && args[0] === caibEndpoint(token, \"StorageService\") &&\n          args[1] === caibEndpoint(token, \"RetrieveService\");\n      }\n      return false;\n    };\n\n    interceptCaibBatchSign = (args) => {\n      const token = caibRequestToken();\n      if (!token) return false;\n      const errorCallback = args[5];\n      const signatureId = caibSignatureId(args[0], token);\n      if (args.length !== 6 || signatureId === null ||\n          args[1] !== caibEndpoint(token, \"BatchPresigner\") ||\n          args[2] !== caibEndpoint(token, \"BatchPostsigner\") ||\n          args[3] !== caibExtraProperties(signatureId) || typeof args[4] !== \"function\" ||\n          typeof errorCallback !== \"function\") {\n        rejectDirectCall(errorCallback, \"INVALID_REQUEST\");\n        return true;\n      }\n      if (!bridge || typeof bridge.postMessage !== \"function\" || !probeDocumentId ||\n          pendingCallbacks.size !== 0 || pendingBatchCallbacks.size !== 0) {\n        rejectDirectCall(errorCallback, \"PROTOCOL_FAILED\");\n        return true;\n      }\n      const batchRequestId = secureRequestId();\n      if (!batchRequestId) { rejectDirectCall(errorCallback, \"PROTOCOL_FAILED\"); return true; }\n      const timeoutId = setTimeout(() => {\n        const expired = clearPendingBatch(batchRequestId);\n        if (expired) {\n          notifyNativeCancel(batchRequestId, \"CAIB_XML_BATCH_CANCEL\");\n          rejectDirectCall(expired.errorCallback, \"REQUEST_EXPIRED\");\n        }\n      }, signTimeoutMillis);\n      pendingBatchCallbacks.set(batchRequestId, {\n        successCallback: args[4], errorCallback, timeoutId, caibXml: true\n      });\n      try {\n        bridge.postMessage(JSON.stringify({\n          type: \"CAIB_XML_BATCH\",\n          documentId: probeDocumentId,\n          requestId: batchRequestId,\n          batchXml: args[0],\n          batchPreSignerUrl: args[1],\n          batchPostSignerUrl: args[2],\n          extraProperties: args[3]\n        }));\n      } catch (_) {\n        clearPendingBatch(batchRequestId);\n        rejectDirectCall(errorCallback, \"PROTOCOL_FAILED\");\n      }\n      return true;\n    };\n\n    installCaibBatchMethodHooks = (value, includeCaibBatch) => {\n      if (includeCaibBatch) {\n        installMethodHook(value, \"setForceWSMode\", \"CAIB_SET_FORCE_WS_MODE\");\n        installMethodHook(value, \"cargarAppAfirma\", \"CAIB_CARGAR_APP_AFIRMA\");\n        installMethodHook(value, \"setServlets\", \"CAIB_SET_SERVLETS\");\n        installMethodHook(value, \"signBatch\", \"CAIB_BATCH_SIGN\");\n      }\n    };\n  }\n"
    assertEquals(enabled, actual.contains(module))
    assertEquals(actual.indexOf(module), actual.lastIndexOf(module))
    return actual.replace(module, "")
        .replace("      installCaibBatchMethodHooks(value, includeCaibBatch);\n", "      if (includeCaibBatch) {\n        installMethodHook(value, \"setForceWSMode\", \"CAIB_SET_FORCE_WS_MODE\");\n        installMethodHook(value, \"cargarAppAfirma\", \"CAIB_CARGAR_APP_AFIRMA\");\n        installMethodHook(value, \"setServlets\", \"CAIB_SET_SERVLETS\");\n        installMethodHook(value, \"signBatch\", \"CAIB_BATCH_SIGN\");\n      }\n")
        .replace("\n  installSevillaAtseClickHook();", "\n  function caibRequestToken() {\n    if (!caibBatchCompatibilityEnabled || window.location.origin !== caibOrigin) return null;\n    const match = window.location.pathname.match(caibSignerPathPattern);\n    return match ? match[1] : null;\n  }\n\n  function decodeCaibUrlBase64(value) {\n    if (typeof value !== \"string\" || !caibBatchBase64Pattern.test(value)) return null;\n    try {\n      const normalized = value.replace(/-/g, \"+\").replace(/_/g, \"/\") + \"=\".repeat((4 - value.length % 4) % 4);\n      return atob(normalized);\n    } catch (_) { return null; }\n  }\n\n  function caibSignatureId(batchXml, token) {\n    if (typeof batchXml !== \"string\" || batchXml.length === 0 || batchXml.length > 8192) return null;\n    const xml = decodeCaibUrlBase64(batchXml);\n    if (xml === null || xml.length > 6144) return null;\n    const ids = Array.from(xml.matchAll(/<singlesign\\s+Id=\"([A-Za-z0-9_-]{40})\">/g));\n    if (ids.length !== 1 || !caibSignatureIdPattern.test(ids[0][1])) return null;\n    return decodeCaibUrlBase64(ids[0][1]) === `\${token}|0` ? ids[0][1] : null;\n  }\n\n  function caibExtraProperties(signatureId) {\n    return \"mode=implicit\\n\" +\n      \"signatureSubFilter=ETSI.CAdES.detached\\n\" +\n      `SignatureId=\${signatureId}\\n` +\n      \"signReason=FORM-1.pdf\\n\" +\n      \"formatmobile=PAdEStri\\n\" +\n      \"formatbatch=PAdES\\n\" +\n      \"format=PAdES\\n\" +\n      \"filters.1=nonexpired:\\n\" +\n      \"allowSigningCertifiedPdfs=true\\n\" +\n      \"algorithm=SHA256withRSA\\n\";\n  }\n\n  function caibEndpoint(token, name) {\n    return `\${caibOrigin}/portafibback/public/signmodule/requestPlugin/\${token}/-1/\${name}`;\n  }\n\n  function interceptCaibSetupCall(call, args) {\n    const token = caibRequestToken();\n    if (!token) return false;\n    if (call === \"CAIB_SET_FORCE_WS_MODE\") return args.length === 1 && args[0] === true;\n    if (call === \"CAIB_CARGAR_APP_AFIRMA\") return args.length === 1 && args[0] === caibClientBase;\n    if (call === \"CAIB_SET_SERVLETS\") {\n      return args.length === 2 && args[0] === caibEndpoint(token, \"StorageService\") &&\n        args[1] === caibEndpoint(token, \"RetrieveService\");\n    }\n    return false;\n  }\n\n  function interceptCaibBatchSign(args) {\n    const token = caibRequestToken();\n    if (!token) return false;\n    const errorCallback = args[5];\n    const signatureId = caibSignatureId(args[0], token);\n    if (args.length !== 6 || signatureId === null ||\n        args[1] !== caibEndpoint(token, \"BatchPresigner\") ||\n        args[2] !== caibEndpoint(token, \"BatchPostsigner\") ||\n        args[3] !== caibExtraProperties(signatureId) || typeof args[4] !== \"function\" ||\n        typeof errorCallback !== \"function\") {\n      rejectDirectCall(errorCallback, \"INVALID_REQUEST\");\n      return true;\n    }\n    if (!bridge || typeof bridge.postMessage !== \"function\" || !probeDocumentId ||\n        pendingCallbacks.size !== 0 || pendingBatchCallbacks.size !== 0) {\n      rejectDirectCall(errorCallback, \"PROTOCOL_FAILED\");\n      return true;\n    }\n    const batchRequestId = secureRequestId();\n    if (!batchRequestId) { rejectDirectCall(errorCallback, \"PROTOCOL_FAILED\"); return true; }\n    const timeoutId = setTimeout(() => {\n      const expired = clearPendingBatch(batchRequestId);\n      if (expired) {\n        notifyNativeCancel(batchRequestId, \"CAIB_XML_BATCH_CANCEL\");\n        rejectDirectCall(expired.errorCallback, \"REQUEST_EXPIRED\");\n      }\n    }, signTimeoutMillis);\n    pendingBatchCallbacks.set(batchRequestId, {\n      successCallback: args[4], errorCallback, timeoutId, caibXml: true\n    });\n    try {\n      bridge.postMessage(JSON.stringify({\n        type: \"CAIB_XML_BATCH\",\n        documentId: probeDocumentId,\n        requestId: batchRequestId,\n        batchXml: args[0],\n        batchPreSignerUrl: args[1],\n        batchPostSignerUrl: args[2],\n        extraProperties: args[3]\n      }));\n    } catch (_) {\n      clearPendingBatch(batchRequestId);\n      rejectDirectCall(errorCallback, \"PROTOCOL_FAILED\");\n    }\n    return true;\n  }\n\n  installSevillaAtseClickHook();")
        .replace("  let caibRequestToken = () => null;\n  let interceptCaibSetupCall = () => false;\n  let interceptCaibBatchSign = () => false;\n  let installCaibBatchMethodHooks = () => {};\n", "  const caibOrigin = \"https://intranet.caib.es\";\n  const caibClientBase = \"https://intranet.caib.es/portafibback\";\n  const caibSignerPathPattern = /^\\/portafibback\\/public\\/signmodule\\/requestPlugin\\/([A-Za-z0-9_-]{28})\\/-1\\/index\$/;\n  const caibSignatureIdPattern = /^[A-Za-z0-9_-]{40}\$/;\n  const caibBatchBase64Pattern = /^[A-Za-z0-9_-]+={0,2}\$/;\n")
}

private fun restoreEuskadiPostExtraction(actual: String, enabled: Boolean): String {
    val module = "\n  {\n    const euskadiProfileId = \"euskadi-sede-electronica\";\n    const euskadiAuthPage =\n      \"https://eidas.izenpe.com/trustedx-authserver/izenpe/authentication\";\n    const euskadiClientAuthTarget =\n      \"https://eidas2.izenpe.com/cert-authn-external-validation/authenticate\";\n    const euskadiFormContentType = \"application/x-www-form-urlencoded\";\n\n    let euskadiClientAuthPostSent = false;\n\n    function isSafeEuskadiOpaqueRequest(value) {\n      if (typeof value !== \"string\" || value.length < 1 || value.length > 4096) {\n        return false;\n      }\n      for (let index = 0; index < value.length; index += 1) {\n        const code = value.charCodeAt(index);\n        if (code < 0x21 || code > 0x7e) return false;\n      }\n      return true;\n    }\n\n    function interceptEuskadiClientAuthPost(form) {\n      if (!euskadiClientAuthPostEnabled || window.top !== window ||\n          location.href !== euskadiAuthPage) {\n        return false;\n      }\n      if (!(form instanceof HTMLFormElement)) return false;\n\n      let action;\n      try {\n        action = new URL(form.action, location.href).href;\n      } catch (_) {\n        return true;\n      }\n      // Unrelated forms on the reviewed page remain untouched. Only the exact client-auth target is mediated.\n      if (action !== euskadiClientAuthTarget) return false;\n      if (form.method.toUpperCase() !== \"POST\" ||\n          form.enctype.toLowerCase() !== euskadiFormContentType) {\n        return true;\n      }\n\n      const namedControls = Array.from(form.elements).filter(control => control && control.name);\n      if (namedControls.length !== 2 || namedControls.some(control => control.type !== \"hidden\")) {\n        return true;\n      }\n      const names = namedControls.map(control => control.name).sort();\n      if (names[0] !== \"request\" || names[1] !== \"x_correlation_id\") return true;\n\n      const requestControl = form.elements.namedItem(\"request\");\n      const correlationControl = form.elements.namedItem(\"x_correlation_id\");\n      const requestValue = requestControl && requestControl.value;\n      const correlationValue = correlationControl && correlationControl.value;\n      if (!isSafeEuskadiOpaqueRequest(requestValue) ||\n          typeof correlationValue !== \"string\" ||\n          !canonicalUuidPattern.test(correlationValue) ||\n          euskadiClientAuthPostSent || !bridge || typeof bridge.postMessage !== \"function\") {\n        return true;\n      }\n\n      const requestId = secureRequestId();\n      if (!requestId || !canonicalUuidPattern.test(requestId)) return true;\n      euskadiClientAuthPostSent = true;\n      try {\n        bridge.postMessage(JSON.stringify({\n          type: \"EUSKADI_CLIENT_AUTH_POST\",\n          profileId: euskadiProfileId,\n          requestId,\n          method: \"POST\",\n          contentType: euskadiFormContentType,\n          targetUrl: euskadiClientAuthTarget,\n          request: requestValue,\n          x_correlation_id: correlationValue\n        }));\n      } catch (_) {\n        euskadiClientAuthPostSent = false;\n      }\n      // The normal WebView never sends this POST; native replays the exact form body only in the isolated TLS WebView.\n      return true;\n    }\n\n    if (euskadiClientAuthPostEnabled && typeof HTMLFormElement === \"function\") {\n      const nativeSubmit = HTMLFormElement.prototype.submit;\n      const nativeRequestSubmit = HTMLFormElement.prototype.requestSubmit;\n      try {\n        HTMLFormElement.prototype.submit = function() {\n          if (interceptEuskadiClientAuthPost(this)) return undefined;\n          return Reflect.apply(nativeSubmit, this, []);\n        };\n        if (typeof nativeRequestSubmit === \"function\") {\n          HTMLFormElement.prototype.requestSubmit = function(submitter) {\n            if (interceptEuskadiClientAuthPost(this)) return undefined;\n            const args = submitter === undefined ? [] : [submitter];\n            return Reflect.apply(nativeRequestSubmit, this, args);\n          };\n        }\n        document.addEventListener(\"submit\", event => {\n          if (interceptEuskadiClientAuthPost(event.target)) {\n            event.preventDefault();\n            event.stopImmediatePropagation();\n          }\n        }, true);\n      } catch (_) {\n        // If the exact hook cannot be installed, do not add any broader fallback interception.\n      }\n    }\n  }\n"
    assertEquals(enabled, actual.contains(module))
    assertEquals(actual.indexOf(module), actual.lastIndexOf(module))
    return actual.replace(module, "")
        .replace("\n  const closedErrorMessages =", "  let euskadiClientAuthPostSent = false;\n\n  function isSafeEuskadiOpaqueRequest(value) {\n    if (typeof value !== \"string\" || value.length < 1 || value.length > 4096) {\n      return false;\n    }\n    for (let index = 0; index < value.length; index += 1) {\n      const code = value.charCodeAt(index);\n      if (code < 0x21 || code > 0x7e) return false;\n    }\n    return true;\n  }\n\n  function interceptEuskadiClientAuthPost(form) {\n    if (!euskadiClientAuthPostEnabled || window.top !== window ||\n        location.href !== euskadiAuthPage) {\n      return false;\n    }\n    if (!(form instanceof HTMLFormElement)) return false;\n\n    let action;\n    try {\n      action = new URL(form.action, location.href).href;\n    } catch (_) {\n      return true;\n    }\n    // Unrelated forms on the reviewed page remain untouched. Only the exact client-auth target is mediated.\n    if (action !== euskadiClientAuthTarget) return false;\n    if (form.method.toUpperCase() !== \"POST\" ||\n        form.enctype.toLowerCase() !== euskadiFormContentType) {\n      return true;\n    }\n\n    const namedControls = Array.from(form.elements).filter(control => control && control.name);\n    if (namedControls.length !== 2 || namedControls.some(control => control.type !== \"hidden\")) {\n      return true;\n    }\n    const names = namedControls.map(control => control.name).sort();\n    if (names[0] !== \"request\" || names[1] !== \"x_correlation_id\") return true;\n\n    const requestControl = form.elements.namedItem(\"request\");\n    const correlationControl = form.elements.namedItem(\"x_correlation_id\");\n    const requestValue = requestControl && requestControl.value;\n    const correlationValue = correlationControl && correlationControl.value;\n    if (!isSafeEuskadiOpaqueRequest(requestValue) ||\n        typeof correlationValue !== \"string\" ||\n        !canonicalUuidPattern.test(correlationValue) ||\n        euskadiClientAuthPostSent || !bridge || typeof bridge.postMessage !== \"function\") {\n      return true;\n    }\n\n    const requestId = secureRequestId();\n    if (!requestId || !canonicalUuidPattern.test(requestId)) return true;\n    euskadiClientAuthPostSent = true;\n    try {\n      bridge.postMessage(JSON.stringify({\n        type: \"EUSKADI_CLIENT_AUTH_POST\",\n        profileId: euskadiProfileId,\n        requestId,\n        method: \"POST\",\n        contentType: euskadiFormContentType,\n        targetUrl: euskadiClientAuthTarget,\n        request: requestValue,\n        x_correlation_id: correlationValue\n      }));\n    } catch (_) {\n      euskadiClientAuthPostSent = false;\n    }\n    // The normal WebView never sends this POST; native replays the exact form body only in the isolated TLS WebView.\n    return true;\n  }\n\n  if (euskadiClientAuthPostEnabled && typeof HTMLFormElement === \"function\") {\n    const nativeSubmit = HTMLFormElement.prototype.submit;\n    const nativeRequestSubmit = HTMLFormElement.prototype.requestSubmit;\n    try {\n      HTMLFormElement.prototype.submit = function() {\n        if (interceptEuskadiClientAuthPost(this)) return undefined;\n        return Reflect.apply(nativeSubmit, this, []);\n      };\n      if (typeof nativeRequestSubmit === \"function\") {\n        HTMLFormElement.prototype.requestSubmit = function(submitter) {\n          if (interceptEuskadiClientAuthPost(this)) return undefined;\n          const args = submitter === undefined ? [] : [submitter];\n          return Reflect.apply(nativeRequestSubmit, this, args);\n        };\n      }\n      document.addEventListener(\"submit\", event => {\n        if (interceptEuskadiClientAuthPost(event.target)) {\n          event.preventDefault();\n          event.stopImmediatePropagation();\n        }\n      }, true);\n    } catch (_) {\n      // If the exact hook cannot be installed, do not add any broader fallback interception.\n    }\n  }\n\n  const closedErrorMessages =")
        .replace("  // null: not applicable; false: reject; true: exact tuple.\n  let matchesXuntaSign = () => null;", "  const euskadiProfileId = \"euskadi-sede-electronica\";\n  const euskadiAuthPage =\n    \"https://eidas.izenpe.com/trustedx-authserver/izenpe/authentication\";\n  const euskadiClientAuthTarget =\n    \"https://eidas2.izenpe.com/cert-authn-external-validation/authenticate\";\n  const euskadiFormContentType = \"application/x-www-form-urlencoded\";\n  // null: not applicable; false: reject; true: exact tuple.\n  let matchesXuntaSign = () => null;")
}

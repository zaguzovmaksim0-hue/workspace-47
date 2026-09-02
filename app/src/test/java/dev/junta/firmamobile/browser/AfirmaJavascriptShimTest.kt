package dev.junta.firmamobile.browser

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import org.json.JSONObject
import org.junit.Assert.assertFalse
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
class AfirmaJavascriptShimTest {
    @Test
    fun shimOnlyForwardsAfirmaOrIntentUrisThroughTheNamedWebMessageObject() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val script = AfirmaJavascriptShim.load(context)

        assertTrue(script.contains("window.JuntaFirmaMobile"))
        assertTrue(script.contains("bridge.postMessage"))
        assertTrue(script.contains("AFIRMA_URI"))
        assertTrue(script.contains("window.open"))
        assertTrue(script.contains("afirma:"))
        assertTrue(script.contains("intent:"))
        assertTrue(script.length <= AfirmaJavascriptShim.MAX_SCRIPT_CHARS)
    }

    @Test
    fun shimDoesNotExposeSecretsOrHardcodePortalCallbacks() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val script = AfirmaJavascriptShim.load(context)

        assertFalse(script.contains("addJavascriptInterface"))
        assertFalse(script.contains("getPrivateKey"))
        assertFalse(script.contains("readFile"))
        assertFalse(script.contains("sendHttpRequest"))
        assertFalse(script.contains("saveSignatureAuthCallback"))
        assertFalse(script.contains("ws024"))
    }

    @Test
    fun shimObservesMiniAppletCallsAndBlocksLoopbackWebSocketsWithoutCallbacks() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val script = AfirmaJavascriptShim.load(context)

        assertTrue(script.contains("window.JuntaFirmaProbe"))
        assertTrue(script.contains("MiniApplet"))
        assertTrue(script.contains("window.AutoScript"))
        assertTrue(script.contains("cargarMiniApplet"))
        assertTrue(script.contains("MINIAPPLET_OBSERVATION"))
        assertTrue(script.contains("RUNTIME_BRANCH_OBSERVATION"))
        assertTrue(script.contains("MINIAPPLET_CALL_END"))
        assertTrue(script.contains("MINIAPPLET_SHIM_DIAGNOSTIC"))
        assertTrue(script.contains("SCRIPT_INSTALLED"))
        assertTrue(script.contains("SIGN_INTERCEPT_ENTRY"))
        assertTrue(script.contains("SIGN_MESSAGE_POSTED"))
        assertTrue(script.contains("BADAJOZ_LATE_REWRAP_STARTED"))
        assertTrue(script.contains("BADAJOZ_SIGN_HOOK_READY"))
        assertTrue(script.contains("BADAJOZ_CERT_BUTTON_CLICK"))
        assertTrue(script.contains("BADAJOZ_PULSAR_SIGN_ENTRY"))
        assertTrue(script.contains("BADAJOZ_FIRMAR_ENTRY"))
        assertTrue(script.contains("BADAJOZ_GET_BASE64_ENTRY"))
        assertTrue(script.contains("BADAJOZ_ECHO_ENTRY"))
        assertTrue(script.contains("BADAJOZ_FORCE_WS_ENTRY"))
        assertTrue(script.contains("installMethodHook(value, \"sign\", \"SIGN\")"))
        assertTrue(script.contains("probeDocumentId"))
        assertTrue(script.contains("documentId: probeDocumentId"))
        assertTrue(script.contains("activeProbeRequestId"))
        assertTrue(script.contains("requestId: activeProbeRequestId"))
        assertTrue(script.contains("tryObserveMiniAppletCall"))
        assertTrue(script.contains("finally"))
        val miniAppletRewrap = script.indexOf("const rewrapCurrentMiniApplet")
        assertTrue(miniAppletRewrap >= 0)
        assertTrue(script.indexOf("wrapMiniApplet(window.MiniApplet", miniAppletRewrap) > miniAppletRewrap)
        val autoScriptRewrap = script.indexOf("const rewrapCurrentAutoScript")
        assertTrue(autoScriptRewrap >= 0)
        assertTrue(script.indexOf("window.AutoScript", autoScriptRewrap) > autoScriptRewrap)
        assertTrue(script.contains("window.addEventListener(\"load\""))
        assertTrue(script.contains("window.WebSocket"))
        assertTrue(script.contains("Reflect.apply"))
        assertTrue(script.contains("window.top !== window"))
        assertTrue(script.contains("btnacceso"))
        assertTrue(script.contains("signInAutcertjs"))
        assertFalse(script.contains("querySelector(\"input[type=button]"))
        assertFalse(script.contains("saveSignatureAuthCallback"))
        assertFalse(script.contains("showLogCallback"))
    }

    @Test
    fun qaModeReportsClosedPortalCallbackStagesWithoutPayloadFields() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val qa = AfirmaJavascriptShim.load(
            context = context,
            mode = MiniAppletBridgeMode.FUNCTIONAL,
            qaDiagnosticsEnabled = true,
        )
        val release = AfirmaJavascriptShim.load(
            context = context,
            mode = MiniAppletBridgeMode.FUNCTIONAL,
            qaDiagnosticsEnabled = false,
        )

        assertTrue(qa.contains("const qaDiagnosticsEnabled = true"))
        assertTrue(release.contains("const qaDiagnosticsEnabled = false"))
        assertTrue(qa.contains("QA_PORTAL_DIAGNOSTIC"))
        assertTrue(qa.contains("RESULT_RECEIVED"))
        assertTrue(qa.contains("RESULT_IGNORED"))
        assertTrue(qa.contains("CALLBACK_STARTED"))
        assertTrue(qa.contains("CALLBACK_RETURNED"))
        assertTrue(qa.contains("CALLBACK_THROWN"))
        assertFalse(qa.contains("certificate: certificateB64"))
        assertFalse(qa.contains("signature: signatureB64"))
    }

    @Test
    fun functionalModeOwnsMiniAppletSignWhileProbeModeKeepsObservationOnly() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val functional = AfirmaJavascriptShim.load(
            context,
            MiniAppletBridgeMode.FUNCTIONAL,
        )
        val observation = AfirmaJavascriptShim.load(
            context,
            MiniAppletBridgeMode.OBSERVATION,
        )

        assertTrue(functional.contains("const functionalSigningEnabled = true"))
        assertTrue(observation.contains("const functionalSigningEnabled = false"))
        assertTrue(functional.contains("MINIAPPLET_SIGN"))
        assertTrue(functional.contains("MINIAPPLET_RESULT"))
        assertTrue(functional.contains("MINIAPPLET_CANCEL"))
        assertTrue(functional.contains("pendingCallbacks"))
        assertTrue(functional.contains("pendingCallbacks.delete"))
        assertTrue(functional.contains("isIdenticalInFlightCall"))
        assertTrue(functional.contains("pending.dataB64 === args[0]"))
        assertTrue(functional.contains("pending.successCallback === args[4]"))
        assertTrue(functional.contains("if (call === \"SIGN\" && interceptMiniAppletSign(args))"))
        assertTrue(functional.contains("SHA512withRSA"))
        assertTrue(functional.contains("XAdES Detached"))
        assertTrue(functional.contains("args[3] === null"))
        assertTrue(functional.contains("successCallback(signatureB64, certificateB64)"))
        assertTrue(functional.contains("errorCallback(errorCode"))
        assertTrue(functional.contains("pagehide"))
        assertFalse(functional.contains("evaluateJavascript"))
        assertFalse(functional.contains("Math.random"))
        assertTrue(functional.contains("const uriRequestId = secureRequestId()"))
        assertTrue(functional.contains("if (!uriRequestId)"))
    }

    @Test
    fun documentStartShimHasTheMissingMelillaBatchBridgeContract() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val functional = AfirmaJavascriptShim.load(
            context = context,
            mode = MiniAppletBridgeMode.FUNCTIONAL,
            qaDiagnosticsEnabled = false,
            melillaBatchCompatibilityEnabled = true,
        )

        assertTrue(
            "The document-start shim must recognize AutoScript.signBatchProcess",
            functional.contains("signBatchProcess"),
        )
        assertTrue(
            "The document-start shim must emit the dedicated MINIAPPLET_BATCH discriminator",
            functional.contains("MINIAPPLET_BATCH"),
        )
        assertTrue(functional.contains("pendingBatchCallbacks"))
        assertTrue(functional.contains("MINIAPPLET_BATCH_RESULT"))
        assertTrue(functional.contains("validationResponse"))
        assertTrue(functional.contains("pending.successCallback(validationResponse)"))
        assertTrue(functional.contains("MINIAPPLET_DOCUMENT_READY"))
        assertTrue(functional.contains("notifyNativeDocumentReady"))
    }

    @Test
    fun melillaBatchShimIsDisabledUnlessTheNativeProfileScopeEnablesIt() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val disabled = AfirmaJavascriptShim.load(
            context = context,
            mode = MiniAppletBridgeMode.FUNCTIONAL,
            qaDiagnosticsEnabled = false,
        )
        val enabled = AfirmaJavascriptShim.load(
            context = context,
            mode = MiniAppletBridgeMode.FUNCTIONAL,
            qaDiagnosticsEnabled = false,
            melillaBatchCompatibilityEnabled = true,
        )

        assertTrue(disabled.contains("const melillaBatchCompatibilityEnabled = false"))
        assertTrue(enabled.contains("const melillaBatchCompatibilityEnabled = true"))
        assertTrue(enabled.contains(
            "wrapMiniApplet(window.AutoScript, ugrCompatibilityEnabled, " +
                "melillaBatchCompatibilityEnabled",
        ))
        assertTrue(enabled.contains("if (includeMelillaBatch)"))
    }

    @Test
    fun staBatchShimBindsTheExactActivePortalOrigin() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val huesca = AfirmaJavascriptShim.load(
            context = context,
            mode = MiniAppletBridgeMode.FUNCTIONAL,
            qaDiagnosticsEnabled = false,
            melillaBatchCompatibilityEnabled = true,
            staBatchOrigin = HuescaBatchBridgeAdapter.SOURCE_ORIGIN,
        )

        assertTrue(
            huesca.contains(
                "const staBatchOrigin = ${JSONObject.quote(HuescaBatchBridgeAdapter.SOURCE_ORIGIN)}",
            ),
        )
        assertTrue(huesca.contains("window.location.origin === staBatchOrigin"))
        assertFalse(huesca.contains("const staBatchOrigin = \"https://sede.melilla.es\""))
        assertFalse(huesca.contains("__JFM_STA_BATCH_ORIGIN__"))
    }

    @Test
    fun lugoXmlBatchShimIsExactOriginAndSeparatelyFeatureGated() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val enabled = AfirmaJavascriptShim.load(
            context = context,
            mode = MiniAppletBridgeMode.FUNCTIONAL,
            qaDiagnosticsEnabled = false,
            lugoBatchCompatibilityEnabled = true,
        )
        val disabled = AfirmaJavascriptShim.load(
            context = context,
            mode = MiniAppletBridgeMode.FUNCTIONAL,
            qaDiagnosticsEnabled = false,
        )

        assertTrue(enabled.contains("const lugoBatchCompatibilityEnabled = true"))
        assertTrue(disabled.contains("const lugoBatchCompatibilityEnabled = false"))
        assertTrue(enabled.contains("const lugoOrigin = \"https://sede.deputacionlugo.org\""))
        assertTrue(enabled.contains("installMethodHook(value, \"signBatch\", \"LUGO_BATCH_SIGN\")"))
        assertTrue(enabled.contains("installMethodHook(value, \"cargarAppAfirma\", \"LUGO_CARGAR_APP_AFIRMA\")"))
        assertTrue(enabled.contains("const lugoClientBase = \"https://sede.deputacionlugo.org/opencms\""))
        assertTrue(enabled.contains("call === \"LUGO_CARGAR_APP_AFIRMA\""))
        assertTrue(enabled.contains("args.length === 1 && args[0] === lugoClientBase"))
        assertTrue(enabled.contains("type: \"LUGO_XML_BATCH\""))
        assertTrue(enabled.contains("const isLugoBatchDocument = lugoBatchCompatibilityEnabled"))
        assertTrue(enabled.contains("window.location.origin === lugoOrigin"))
        assertTrue(enabled.contains("pending.lugoXml === true"))
        assertFalse(enabled.contains("__JFM_LUGO_BATCH_COMPATIBILITY_ENABLED__"))
    }

    @Test
    fun ugrCompatibilityPathIsProfileScopedAndUsesOnlyTheExactObservedContract() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val script = AfirmaJavascriptShim.load(
            context = context,
            mode = MiniAppletBridgeMode.FUNCTIONAL,
            qaDiagnosticsEnabled = true,
            ugrCompatibilityEnabled = true,
        )

        assertTrue(script.contains("const ugrCompatibilityEnabled = true"))
        assertTrue(script.contains("https://sede.ugr.es"))
        assertTrue(script.contains("Universidad de Granada"))
        assertTrue(script.contains("VW5pdmVyc2lkYWQgZGUgR3JhbmFkYQ=="))
        assertTrue(script.contains("SHA1withRSA"))
        assertTrue(script.contains("CAdES"))
        assertTrue(script.contains("args[3] === \"\""))
        assertTrue(script.contains("StorageService"))
        assertTrue(script.contains("RetrieveService"))
        assertTrue(script.contains("setForceWSMode"))
        assertTrue(script.contains("cargarAppAfirma"))
        assertTrue(script.contains("setServlets"))
        assertTrue(script.contains("return undefined"))
    }

    @Test
    fun jccmCompatibilityPathIsProfileScopedToTheExactBase64ProbeContract() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val jccm = AfirmaJavascriptShim.load(
            context = context,
            mode = MiniAppletBridgeMode.FUNCTIONAL,
            qaDiagnosticsEnabled = true,
            jccmCompatibilityEnabled = true,
        )
        val generic = AfirmaJavascriptShim.load(
            context = context,
            mode = MiniAppletBridgeMode.FUNCTIONAL,
            qaDiagnosticsEnabled = true,
            jccmCompatibilityEnabled = false,
        )

        assertTrue(jccm.contains("const jccmCompatibilityEnabled = true"))
        assertTrue(jccm.contains("https://ventanillaelectronica.jccm.es"))
        assertTrue(jccm.contains("QUJDREU="))
        assertTrue(jccm.contains("args[0] === jccmPayloadBase64"))
        assertTrue(jccm.contains("SHA1withRSA"))
        assertTrue(jccm.contains("CAdES"))
        assertTrue(jccm.contains("args[3] === null || args[3] === \"\""))
        assertTrue(jccm.contains("!jccmCompatibilityEnabled"))
        assertFalse(jccm.contains("FORMPROC.submit()"))
        assertTrue(generic.contains("const jccmCompatibilityEnabled = false"))
    }

    @Test
    fun jccmRegistroCompatibilityIsIndependentAndExactToTheProtectedXadesTuple() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val enabled = AfirmaJavascriptShim.load(
            context = context,
            mode = MiniAppletBridgeMode.FUNCTIONAL,
            qaDiagnosticsEnabled = true,
            jccmRegistroCompatibilityEnabled = true,
        )
        val flags = WebMessageBridge.shimCompatibilityFlags(
            profileId = dev.junta.firmamobile.profile.ProfileId("jccm-registro-generico"),
            profileActive = true,
            melillaBatchEnabled = false,
        )

        assertTrue(flags.jccmRegistro)
        assertFalse(flags.jccm)
        assertTrue(enabled.contains("const jccmRegistroCompatibilityEnabled = true"))
        assertTrue(enabled.contains("https://registrounicociudadanos.jccm.es"))
        assertTrue(enabled.contains("/registrounicociudadanos/accesoclvd.do"))
        assertFalse(enabled.contains("jccmRegistroStartPage"))
        assertTrue(enabled.contains("args[1] === \"SHA512withRSA\""))
        assertTrue(enabled.contains("args[2] === \"XADES\""))
        assertTrue(enabled.contains("args[3] === jccmRegistroExtraProperties"))
        assertTrue(enabled.contains("format=XAdES Detached\\nmode=implicit"))
        assertTrue(enabled.contains("if (isJccmRegistroOrigin && !isExactJccmRegistroCall)"))
    }

    @Test
    fun activeSevillaProfileEnablesTheRuntimeAtseShimFlag() {
        val flags = WebMessageBridge.shimCompatibilityFlags(
            profileId = dev.junta.firmamobile.profile.ProfileId("sevilla-atse-certificate-login"),
            profileActive = true,
            melillaBatchEnabled = false,
        )

        assertFalse(flags.ugr)
        assertFalse(flags.cantabria)
        assertFalse(flags.jccm)
        assertTrue(flags.sevillaAtse)
        assertFalse(flags.cdti)
        assertFalse(flags.melillaBatch)
    }

    @Test
    fun sevillaAtseCompatibilityIsProfileScopedToTheExactXadesChallengeTuple() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val script = AfirmaJavascriptShim.load(
            context = context,
            mode = MiniAppletBridgeMode.FUNCTIONAL,
            qaDiagnosticsEnabled = true,
            sevillaAtseCompatibilityEnabled = true,
        )

        assertTrue(script.contains("const sevillaAtseCompatibilityEnabled = true"))
        assertTrue(script.contains("https://www.sevilla.org"))
        assertTrue(script.contains("sevillaAtseChallengePattern"))
        assertTrue(script.contains("atob(value)"))
        assertTrue(script.contains("args[1] === \"SHA1withRSA\""))
        assertTrue(script.contains("args[2] === \"XAdES\""))
        assertTrue(script.contains("args[3] == null"))
        assertTrue(script.contains("if (isSevillaAtseOrigin && !isExactSevillaAtseCall)"))
        assertTrue(script.contains("location.pathname === sevillaAtsePage"))
        assertTrue(script.contains("location.search === \"?modo=Contribuyente\""))
        assertTrue(script.contains("https://www.sevilla.org/ovweb/sign/StorageService"))
        assertTrue(script.contains("https://www.sevilla.org/ovweb/sign/RetrieveService"))
        assertTrue(script.contains("sevillaAtseSetupState === 3"))
        assertTrue(script.contains("SEVILLA_SET_FORCE_WS_MODE"))
        assertTrue(script.contains("SEVILLA_SET_SERVLETS"))
        assertTrue(script.contains("SEVILLA_CARGAR_APP_AFIRMA"))
        assertTrue(script.contains("args[0] === sevillaAtseStorageUrl && args[1] === sevillaAtseRetrieveUrl"))
        assertTrue(script.contains("event.preventDefault()"))
        assertTrue(script.contains("target.getAttribute(\"onclick\") === \"doSign();\""))
        assertTrue(script.contains("a[href=\"#\"][onclick=\"doSign();\"]"))
        assertFalse(script.contains("authenticate("))
    }

    @Test
    fun airefCompatibilityIsProfileScopedToTheExactProtectedXadesTuple() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val enabled = AfirmaJavascriptShim.load(
            context = context,
            mode = MiniAppletBridgeMode.FUNCTIONAL,
            qaDiagnosticsEnabled = true,
            airefCompatibilityEnabled = true,
        )
        val disabled = AfirmaJavascriptShim.load(
            context = context,
            mode = MiniAppletBridgeMode.FUNCTIONAL,
            qaDiagnosticsEnabled = true,
            airefCompatibilityEnabled = false,
        )

        assertTrue(enabled.contains("const airefCompatibilityEnabled = true"))
        assertTrue(disabled.contains("const airefCompatibilityEnabled = false"))
        assertTrue(enabled.contains("https://sede.airef.es"))
        assertTrue(enabled.contains("/invesiteRE/action/solicitud/view"))
        assertTrue(enabled.contains("^\\?id=[0-9]{1,20}$"))
        assertTrue(enabled.contains("decoded.length === 32"))
        assertTrue(enabled.contains("args[1] === \"SHA1withRSA\""))
        assertTrue(enabled.contains("args[2] === \"XAdES\""))
        assertTrue(enabled.contains("args[3] === null"))
        assertTrue(enabled.contains("if (isAirefOrigin && !isExactAirefCall)"))
    }

    @Test
    fun activeAirefProfileEnablesOnlyTheRuntimeAirefShimFlag() {
        val flags = WebMessageBridge.shimCompatibilityFlags(
            profileId = dev.junta.firmamobile.profile.ProfileId("airef-instancia-general"),
            profileActive = true,
            melillaBatchEnabled = false,
        )

        assertTrue(flags.airef)
        assertFalse(flags.ugr)
        assertFalse(flags.cantabria)
        assertFalse(flags.jccm)
        assertFalse(flags.sevillaAtse)
        assertFalse(flags.cdti)
        assertFalse(flags.policia)
        assertFalse(flags.granCanaria)
        assertFalse(flags.melillaBatch)
        assertFalse(flags.lugoBatch)
        assertFalse(flags.isciiiCertificateSelection)
        assertFalse(flags.valenciaCertificateSelection)
    }

    @Test
    fun activeCdtiProfileEnablesOnlyTheRuntimeCdtiShimFlag() {
        val flags = WebMessageBridge.shimCompatibilityFlags(
            profileId = dev.junta.firmamobile.profile.ProfileId("cdti-certificate-validation"),
            profileActive = true,
            melillaBatchEnabled = false,
        )

        assertFalse(flags.ugr)
        assertFalse(flags.cantabria)
        assertFalse(flags.jccm)
        assertFalse(flags.sevillaAtse)
        assertTrue(flags.cdti)
        assertFalse(flags.policia)
        assertFalse(flags.melillaBatch)
        assertFalse(flags.isciiiCertificateSelection)
        assertFalse(flags.valenciaCertificateSelection)
    }

    @Test
    fun cdtiCompatibilityIsProfileScopedToTheExactPublicXadesEnvelopingTuple() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val enabled = AfirmaJavascriptShim.load(
            context = context,
            mode = MiniAppletBridgeMode.FUNCTIONAL,
            qaDiagnosticsEnabled = true,
            cdtiCompatibilityEnabled = true,
        )
        val disabled = AfirmaJavascriptShim.load(
            context = context,
            mode = MiniAppletBridgeMode.FUNCTIONAL,
            qaDiagnosticsEnabled = true,
            cdtiCompatibilityEnabled = false,
        )

        assertTrue(enabled.contains("const cdtiCompatibilityEnabled = true"))
        assertTrue(disabled.contains("const cdtiCompatibilityEnabled = false"))
        assertTrue(enabled.contains("https://sede.cdti.gob.es"))
        assertTrue(enabled.contains("/AreaPrivada/Expedientes/Common/Certificados/ValidarCertificado.aspx"))
        assertTrue(enabled.contains("^CertExp[0-9a-f]{32}[0-9a-z]{24}$"))
        assertTrue(enabled.contains("args[1] === \"SHA512withRSA\""))
        assertTrue(enabled.contains("args[2] === \"XAdES Enveloping\""))
        assertTrue(enabled.contains("args[3] === cdtiExtraProperties"))
        assertTrue(enabled.contains("isExactCdtiCall ? args[0] + \"=\" : args[0]"))
        assertTrue(enabled.contains("if (isCdtiOrigin && !isExactCdtiCall)"))
        assertFalse(enabled.contains("SHA256withRSA\" &&\n      args[2] === \"XAdES Enveloping"))
    }

    @Test
    fun isciiiCompatibilityOwnsOnlyTheExactObservedCertificateSelectionCall() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val enabled = AfirmaJavascriptShim.load(
            context = context,
            mode = MiniAppletBridgeMode.FUNCTIONAL,
            qaDiagnosticsEnabled = false,
            isciiiCertificateSelectionEnabled = true,
        )
        val disabled = AfirmaJavascriptShim.load(
            context = context,
            mode = MiniAppletBridgeMode.FUNCTIONAL,
            qaDiagnosticsEnabled = false,
            isciiiCertificateSelectionEnabled = false,
        )

        assertTrue(enabled.contains("const iSel = true"))
        assertTrue(disabled.contains("const iSel = false"))
        assertTrue(enabled.contains("https://sede.isciii.gob.es"))
        assertTrue(enabled.contains("/cargaApplet.jsp"))
        assertTrue(enabled.contains("accion=generico&recurso.opcion=null"))
        assertTrue(enabled.contains("selectCertificate"))
        assertTrue(enabled.contains("MINIAPPLET_SELECT_CERTIFICATE"))
        assertTrue(enabled.contains("MINIAPPLET_SELECT_CERTIFICATE_RESULT"))
        assertTrue(enabled.contains(
            "serverUrl=http://dtomcat7.isciiides.es:8080/" +
                "afirma-server-triphase-signer/SignatureService",
        ))
        assertTrue(enabled.contains("successCallback(certificateB64)"))
        assertFalse(enabled.contains("form.submit()"))
    }

    @Test
    fun nonUgrShimKeepsTheStrictGenericTransportMode() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val script = AfirmaJavascriptShim.load(
            context = context,
            mode = MiniAppletBridgeMode.FUNCTIONAL,
            qaDiagnosticsEnabled = true,
            ugrCompatibilityEnabled = false,
        )

        assertTrue(script.contains("const ugrCompatibilityEnabled = false"))
        assertTrue(script.contains("!base64Pattern.test(args[0])"))
        assertTrue(script.contains("args[3] === null"))
        assertTrue(script.contains("window.location.origin === ugrOrigin"))
    }

    @Test
    fun cantabriaCompatibilityPathDescribesTheExactProfileScopedMiniAppletContract() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val enabled = AfirmaJavascriptShim.load(
            context = context,
            mode = MiniAppletBridgeMode.FUNCTIONAL,
            qaDiagnosticsEnabled = true,
            ugrCompatibilityEnabled = false,
            cantabriaCompatibilityEnabled = true,
        )
        val generic = AfirmaJavascriptShim.load(
            context = context,
            mode = MiniAppletBridgeMode.FUNCTIONAL,
            qaDiagnosticsEnabled = true,
            ugrCompatibilityEnabled = false,
            cantabriaCompatibilityEnabled = false,
        )

        assertTrue(enabled.contains("const cantabriaCompatibilityEnabled = true"))
        assertTrue(generic.contains("const cantabriaCompatibilityEnabled = false"))
        assertTrue(enabled.contains("window.location.origin === cantabriaOrigin"))
        assertTrue(enabled.contains("https://rec.cantabria.es"))
        assertTrue(enabled.contains("[0-9a-f]{40}"))
        assertTrue(enabled.contains("args[1] === \"SHA512withRSA\""))
        assertTrue(enabled.contains("args[2] === \"CAdES\""))
        assertTrue(enabled.contains("filters=\\nmode=implicit"))
        assertTrue(enabled.contains("globalThis.btoa(args[0])"))
    }

    @Test
    fun activeValenciaProfileEnablesTheRuntimeSelectionShimFlag() {
        val flags = WebMessageBridge.shimCompatibilityFlags(
            profileId = dev.junta.firmamobile.profile.ProfileId("diputacion-valencia-sede"),
            profileActive = true,
            melillaBatchEnabled = false,
        )

        assertFalse(flags.ugr)
        assertFalse(flags.cantabria)
        assertFalse(flags.jccm)
        assertFalse(flags.sevillaAtse)
        assertFalse(flags.policia)
        assertFalse(flags.melillaBatch)
        assertFalse(flags.isciiiCertificateSelection)
        assertTrue(flags.valenciaCertificateSelection)
    }

    @Test
    fun valenciaCompatibilityOwnsOnlyTheExactObservedCertificateSelectionCall() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val enabled = AfirmaJavascriptShim.load(
            context = context,
            mode = MiniAppletBridgeMode.FUNCTIONAL,
            qaDiagnosticsEnabled = false,
            valenciaCertificateSelectionEnabled = true,
        )
        val disabled = AfirmaJavascriptShim.load(
            context = context,
            mode = MiniAppletBridgeMode.FUNCTIONAL,
            qaDiagnosticsEnabled = false,
            valenciaCertificateSelectionEnabled = false,
        )

        assertTrue(enabled.contains("const vSel = true"))
        assertTrue(disabled.contains("const vSel = false"))
        assertTrue(enabled.contains("https://portafirmas.dival.es"))
        assertTrue(enabled.contains("/signingpad/xhtml/login.xhtml"))
        assertTrue(enabled.contains("filters=keyusage.nonrepudiation:true;nonexpired:true\\nheadless=true"))
        assertTrue(enabled.contains("selectCertificate"))
        assertTrue(enabled.contains("MINIAPPLET_SELECT_CERTIFICATE"))
        assertTrue(enabled.contains("MINIAPPLET_SELECT_CERTIFICATE_RESULT"))
        assertTrue(enabled.contains("successCallback(certificateB64)"))
        assertFalse(enabled.contains("validarCertificado"))
    }

    @Test
    fun activePoliciaProfileEnablesTheRuntimePoliciaShimFlag() {
        val flags = WebMessageBridge.shimCompatibilityFlags(
            profileId = dev.junta.firmamobile.profile.ProfileId("policia-solicitud-generica"),
            profileActive = true,
            melillaBatchEnabled = false,
        )

        assertFalse(flags.ugr)
        assertFalse(flags.cantabria)
        assertFalse(flags.jccm)
        assertFalse(flags.sevillaAtse)
        assertFalse(flags.cdti)
        assertTrue(flags.policia)
        assertFalse(flags.melillaBatch)
        assertFalse(flags.isciiiCertificateSelection)
        assertFalse(flags.valenciaCertificateSelection)
    }

    @Test
    fun granCanariaCompatibilityIsProfileScopedToTheExactSha512PadesTuple() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val enabled = AfirmaJavascriptShim.load(
            context = context,
            mode = MiniAppletBridgeMode.FUNCTIONAL,
            qaDiagnosticsEnabled = true,
            granCanariaCompatibilityEnabled = true,
        )
        val disabled = AfirmaJavascriptShim.load(
            context = context,
            mode = MiniAppletBridgeMode.FUNCTIONAL,
            qaDiagnosticsEnabled = true,
            granCanariaCompatibilityEnabled = false,
        )

        assertTrue(enabled.contains("const granCanariaCompatibilityEnabled = true"))
        assertTrue(disabled.contains("const granCanariaCompatibilityEnabled = false"))
        assertTrue(enabled.contains("https://sede.grancanaria.com"))
        assertTrue(enabled.contains("headless=true\\nfilters=nonexpired:"))
        assertTrue(enabled.contains("args[1] === \"SHA512withRSA\""))
        assertTrue(enabled.contains("args[2] === \"PAdES\""))
        assertTrue(enabled.contains("isExactGranCanariaCall"))
    }

    @Test
    fun fuerteventuraCompatibilityIsProfileScopedToTheExactSha256PadesTuple() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val enabled = AfirmaJavascriptShim.load(
            context = context,
            mode = MiniAppletBridgeMode.FUNCTIONAL,
            qaDiagnosticsEnabled = true,
            fuerteventuraCompatibilityEnabled = true,
        )
        val disabled = AfirmaJavascriptShim.load(
            context = context,
            mode = MiniAppletBridgeMode.FUNCTIONAL,
            qaDiagnosticsEnabled = true,
            fuerteventuraCompatibilityEnabled = false,
        )

        assertTrue(enabled.contains("const fuerteventuraCompatibilityEnabled = true"))
        assertTrue(disabled.contains("const fuerteventuraCompatibilityEnabled = false"))
        assertTrue(enabled.contains("https://sede.cabildofuer.es"))
        assertTrue(enabled.contains("action=verYfirmar&modo=cert"))
        assertTrue(enabled.contains("args[1] === \"SHA256withRSA\""))
        assertTrue(enabled.contains("args[2] === \"PAdES\""))
        assertTrue(enabled.contains("obfuscateCertText= true\\n"))
        assertTrue(enabled.contains("isExactFuerteventuraCall"))
    }

    @Test
    fun minecoCompatibilityIsProfileScopedToTheExactSha512PadesFirmaAgeTuple() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val enabled = AfirmaJavascriptShim.load(
            context = context,
            mode = MiniAppletBridgeMode.FUNCTIONAL,
            qaDiagnosticsEnabled = true,
            minecoCompatibilityEnabled = true,
        )
        val disabled = AfirmaJavascriptShim.load(
            context = context,
            mode = MiniAppletBridgeMode.FUNCTIONAL,
            qaDiagnosticsEnabled = true,
            minecoCompatibilityEnabled = false,
        )

        assertTrue(enabled.contains("const minecoCompatibilityEnabled = true"))
        assertTrue(disabled.contains("const minecoCompatibilityEnabled = false"))
        assertTrue(enabled.contains("https://serviciosede.mineco.gob.es"))
        assertTrue(enabled.contains("/FB/solicitud/firma.aspx"))
        assertTrue(enabled.contains("filters=signingCert:;nonexpired:\\nexpPolicy=FirmaAGE\\nsignatureSubFilter=ETSI.CAdES.detached"))
        assertTrue(enabled.contains("args[1] === \"SHA512withRSA\""))
        assertTrue(enabled.contains("args[2] === \"PAdES\""))
        assertTrue(enabled.contains("isExactMinecoCall"))
    }

    @Test
    fun policiaCompatibilityIsProfileScopedToTheExactSha1XadesTuple() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val script = AfirmaJavascriptShim.load(
            context = context,
            mode = MiniAppletBridgeMode.FUNCTIONAL,
            qaDiagnosticsEnabled = true,
            policiaCompatibilityEnabled = true,
        )

        assertTrue(script.contains("const policiaCompatibilityEnabled = true"))
        assertTrue(script.contains("https://sede.policia.gob.es"))
        assertTrue(script.contains("https://sede.policia.gob.es/portalCiudadano/_es/solicitudGenerica.xhtml"))
        assertTrue(script.contains("args[1] === \"SHA1withRSA\""))
        assertTrue(script.contains("args[2] === \"XAdES\""))
        assertTrue(script.contains("isPoliciaProcedurePage"))
    }

    @Test
    fun lugoBatchHookIsPreservedForNonConfigurableAutoScriptObjects() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val script = AfirmaJavascriptShim.load(
            context = context,
            mode = MiniAppletBridgeMode.FUNCTIONAL,
            qaDiagnosticsEnabled = false,
            lugoBatchCompatibilityEnabled = true,
        )
        val fallback = script
            .substringAfter("  } else {\n    wrapMiniApplet(\n      window.AutoScript,", missingDelimiterValue = "")
            .substringBefore("    );", missingDelimiterValue = "")

        assertTrue(fallback.isNotEmpty())
        assertTrue(fallback.contains("lugoBatchCompatibilityEnabled"))
    }
    @Test
    fun xuntaCompatibilityIsProfileScopedToExactPadesTriAndCertificateSelectionTuples() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val enabled = AfirmaJavascriptShim.load(
            context = context,
            mode = MiniAppletBridgeMode.FUNCTIONAL,
            qaDiagnosticsEnabled = true,
            xuntaGaliciaCompatibilityEnabled = true,
        )
        val disabled = AfirmaJavascriptShim.load(
            context = context,
            mode = MiniAppletBridgeMode.FUNCTIONAL,
            qaDiagnosticsEnabled = true,
            xuntaGaliciaCompatibilityEnabled = false,
        )
        assertTrue(enabled.contains("const xSel = true"))
        assertTrue(disabled.contains("const xSel = false"))
        assertTrue(enabled.contains("https://sede.xunta.gal/presenta/novo/PR004A_2025_1"))
        assertTrue(enabled.contains("args[0] === \"doc\""))
        assertTrue(enabled.contains("args[1] === \"SHA1withRSA\""))
        assertTrue(enabled.contains("args[2] === \"PAdEStri\""))
        assertTrue(enabled.contains("filters=nonexpired"))
        assertTrue(enabled.contains("isExactXuntaProperties"))
        assertTrue(enabled.contains("if (isXuntaOrigin && !isExactXuntaCall)"))
    }

    @Test
    fun activeAccedaProfileEnablesTheRuntimeSigningShimFlag() {
        val flags = WebMessageBridge.shimCompatibilityFlags(
            profileId = dev.junta.firmamobile.profile.ProfileId("age-acceda"),
            profileActive = true,
            melillaBatchEnabled = false,
        )

        assertFalse(flags.ugr)
        assertFalse(flags.cantabria)
        assertFalse(flags.jccm)
        assertFalse(flags.sevillaAtse)
        assertFalse(flags.melillaBatch)
        assertFalse(flags.isciiiCertificateSelection)
        assertFalse(flags.valenciaCertificateSelection)
        assertTrue(flags.acceda)
    }

    @Test
    fun accedaCompatibilityOwnsOnlyTheExactObservedPadesSigningCall() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val enabled = AfirmaJavascriptShim.load(
            context = context,
            mode = MiniAppletBridgeMode.FUNCTIONAL,
            qaDiagnosticsEnabled = false,
            accedaCompatibilityEnabled = true,
        )
        val disabled = AfirmaJavascriptShim.load(
            context = context,
            mode = MiniAppletBridgeMode.FUNCTIONAL,
            qaDiagnosticsEnabled = false,
            accedaCompatibilityEnabled = false,
        )

        assertTrue(enabled.contains("const accedaCompatibilityEnabled = true"))
        assertTrue(disabled.contains("const accedaCompatibilityEnabled = false"))
        assertTrue(enabled.contains("https://sede.administracionespublicas.gob.es"))
        assertTrue(enabled.contains("format=PAdES Detached\\nexpPolicy=FirmaAGE\\nnonexpired:true"))
        assertTrue(enabled.contains("args[1] === \"SHA1withRSA\""))
        assertTrue(enabled.contains("args[2] === \"PAdES\""))
    }

    @Test
    fun badajozCompatibilityNormalizesTheObservedCadesSpellingOnlyForItsExactLoginTuple() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val enabled = AfirmaJavascriptShim.load(
            context = context,
            mode = MiniAppletBridgeMode.FUNCTIONAL,
            qaDiagnosticsEnabled = false,
            badajozCompatibilityEnabled = true,
        )
        val disabled = AfirmaJavascriptShim.load(
            context = context,
            mode = MiniAppletBridgeMode.FUNCTIONAL,
            qaDiagnosticsEnabled = false,
            badajozCompatibilityEnabled = false,
        )
        val flags = WebMessageBridge.shimCompatibilityFlags(
            profileId = dev.junta.firmamobile.profile.ProfileId("diputacion-badajoz-portal"),
            profileActive = true,
            melillaBatchEnabled = false,
        )

        assertTrue(flags.badajoz)
        assertTrue(enabled.contains("const badajozCompatibilityEnabled = true"))
        assertTrue(disabled.contains("const badajozCompatibilityEnabled = false"))
        assertTrue(enabled.contains("https://sede.dip-badajoz.es"))
        assertTrue(enabled.contains("args[2] === \"Cades\""))
        assertTrue(enabled.contains("const nativeFormat = isExactBadajozCall ? \"CAdES\" : args[2]"))
        assertTrue(enabled.contains("filters=nonexpired:true;authCert:true"))
    }

    @Test
    fun badajozCompatibilityRehooksGlobalsReplacedAfterDocumentStart() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val enabled = AfirmaJavascriptShim.load(
            context = context,
            mode = MiniAppletBridgeMode.FUNCTIONAL,
            qaDiagnosticsEnabled = false,
            badajozCompatibilityEnabled = true,
        )
        assertTrue(enabled.contains("functionalSigningEnabled && badajozCompatibilityEnabled"))
        assertTrue(enabled.contains("window.setInterval"))
        assertTrue(enabled.contains("window.clearInterval"))
        assertTrue(enabled.contains("wrapMiniApplet(window.MiniApplet"))
        assertTrue(enabled.contains("wrapMiniApplet(window.AutoScript"))
        assertTrue(enabled.contains("signTimeoutMillis"))
    }

}

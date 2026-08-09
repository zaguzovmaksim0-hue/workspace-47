package dev.junta.firmamobile.browser

import android.content.Context
import androidx.test.core.app.ApplicationProvider
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
        assertTrue(script.contains("probeDocumentId"))
        assertTrue(script.contains("documentId: probeDocumentId"))
        assertTrue(script.contains("activeProbeRequestId"))
        assertTrue(script.contains("requestId: activeProbeRequestId"))
        assertTrue(script.contains("tryObserveMiniAppletCall"))
        assertTrue(script.contains("finally"))
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
        val script = AfirmaJavascriptShim.load(
            context = context,
            mode = MiniAppletBridgeMode.FUNCTIONAL,
            qaDiagnosticsEnabled = true,
            ugrCompatibilityEnabled = false,
        )

        assertTrue(script.contains("cantabriaCompatibilityEnabled"))
        assertTrue(script.contains("https://rec.cantabria.es"))
        assertTrue(script.contains("[0-9a-f]{40}"))
        assertTrue(script.contains("SHA512withRSA"))
        assertTrue(script.contains("CAdES"))
        assertTrue(script.contains("filters="))
        assertTrue(script.contains("mode=implicit"))
    }
}

package dev.junta.firmamobile.browser

import android.net.Uri
import dev.junta.firmamobile.network.TrustedOrigin
import dev.junta.firmamobile.profile.ProfileId
import dev.junta.firmamobile.signing.PrecalculatedHashAlgorithm
import dev.junta.firmamobile.signing.SigningErrorCode
import java.util.UUID
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.assertEquals
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
class VeaMultiModeBridgeAdapterTest {
    private val expectedProfileId = ProfileId("junta-andalucia-sede")
    private val expectedOrigin = TrustedOrigin("https", "veaja.cloud.juntadeandalucia.es", 443)
    private val originUri = Uri.parse("https://veaja.cloud.juntadeandalucia.es")
    private val documentId = UUID.randomUUID()
    private val requestId = UUID.randomUUID()

    private val adapter = VeaMultiModeBridgeAdapter(
        activeProfileId = { expectedProfileId },
        currentNavigationEpoch = { 100L },
        currentDocumentId = { documentId },
        currentOrigin = { expectedOrigin },
    )

    @Test
    fun routesValidMultiModeSingleDocumentSignRequest() {
        val hashHex = "e3b0c44298fc1c149afbf4c8996fb92427ae41e4649b934ca495991b7852b855"
        val message = validMessageJson(
            hashes = listOf(hashHex),
            algorithm = "SHA256withRSA",
            format = "CADES",
            params = "mode=explicit\nprecalculatedHashAlgorithm=SHA-256\nfilters=nonexpired:;signingCert;",
        )

        val result = adapter.route(
            rawMessage = message,
            sourceOrigin = originUri,
            isMainFrame = true,
            navigationEpoch = 100L,
        )

        assertTrue(result is VeaMultiModeBridgeRouteResult.Accepted)
        val accepted = (result as VeaMultiModeBridgeRouteResult.Accepted).request
        assertEquals(requestId, accepted.requestId)
        assertEquals(documentId, accepted.documentId)
        assertEquals(1, accepted.arrayLength)
        assertEquals(listOf("sign"), accepted.operationArray)
        assertEquals(listOf(hashHex), accepted.dataArray)
        assertEquals("SHA256withRSA", accepted.algorithm)
        assertEquals("CADES", accepted.format)
        assertEquals(PrecalculatedHashAlgorithm.SHA256, accepted.hashAlgorithm)
        assertEquals(1, accepted.hashes.size)
        assertEquals(32, accepted.hashes[0].size)
    }

    @Test
    fun routesValidMultiModeMultipleDocumentsSignRequest() {
        val hash1 = "11".repeat(32)
        val hash2 = "22".repeat(32)
        val message = validMessageJson(
            hashes = listOf(hash1, hash2),
            algorithm = "SHA256withRSA",
            format = "CADES",
            params = "mode=explicit\nprecalculatedHashAlgorithm=SHA-256\nfilters=nonexpired:;signingCert;qualified:12345",
        )

        val result = adapter.route(
            rawMessage = message,
            sourceOrigin = originUri,
            isMainFrame = true,
            navigationEpoch = 100L,
        )

        assertTrue(result is VeaMultiModeBridgeRouteResult.Accepted)
        val accepted = (result as VeaMultiModeBridgeRouteResult.Accepted).request
        assertEquals(2, accepted.arrayLength)
        assertEquals(listOf("sign", "sign"), accepted.operationArray)
        assertEquals(2, accepted.hashes.size)
    }

    @Test
    fun rejectsUntrustedOriginOrFrameFailClosed() {
        val message = validMessageJson(listOf("aa".repeat(32)))
        val wrongOrigin = adapter.route(
            rawMessage = message,
            sourceOrigin = Uri.parse("https://evil.juntadeandalucia.es"),
            isMainFrame = true,
            navigationEpoch = 100L,
        )
        assertEquals(VeaMultiModeBridgeRouteResult.Rejected(requestId, SigningErrorCode.ORIGIN_NOT_ALLOWED), wrongOrigin)

        val wrongFrame = adapter.route(
            rawMessage = message,
            sourceOrigin = originUri,
            isMainFrame = false,
            navigationEpoch = 100L,
        )
        assertEquals(VeaMultiModeBridgeRouteResult.Rejected(requestId, SigningErrorCode.NAVIGATION_CHANGED), wrongFrame)
    }

    @Test
    fun rejectsMismatchedHashAlgorithmAndDigestLength() {
        val shortHash = "aa".repeat(20) // 20 bytes (SHA-1 length)
        val message = validMessageJson(
            hashes = listOf(shortHash),
            algorithm = "SHA256withRSA",
            format = "CADES",
            params = "mode=explicit\nprecalculatedHashAlgorithm=SHA-256\nfilters=nonexpired:;signingCert;",
        )

        val result = adapter.route(
            rawMessage = message,
            sourceOrigin = originUri,
            isMainFrame = true,
            navigationEpoch = 100L,
        )
        assertEquals(VeaMultiModeBridgeRouteResult.Rejected(requestId, SigningErrorCode.INVALID_REQUEST), result)
    }

    @Test
    fun handlesCancelMessage() {
        val cancelJson = JSONObject()
            .put("type", "MINIAPPLET_MULTIMODE_CANCEL")
            .put("documentId", documentId.toString())
            .put("requestId", requestId.toString())
            .toString()

        val result = adapter.route(
            rawMessage = cancelJson,
            sourceOrigin = originUri,
            isMainFrame = true,
            navigationEpoch = 100L,
        )
        assertEquals(VeaMultiModeBridgeRouteResult.Cancelled(requestId, documentId), result)
    }

    private fun validMessageJson(
        hashes: List<String>,
        algorithm: String = "SHA256withRSA",
        format: String = "CADES",
        params: String = "mode=explicit\nprecalculatedHashAlgorithm=SHA-256\nfilters=nonexpired:;signingCert;",
    ): String = JSONObject()
        .put("type", "MINIAPPLET_MULTIMODE_SIGN")
        .put("documentId", documentId.toString())
        .put("requestId", requestId.toString())
        .put("operationArray", JSONArray(List(hashes.size) { "sign" }))
        .put("dataArray", JSONArray(hashes))
        .put("originalDataArray", JSONObject.NULL)
        .put("arrayLength", hashes.size)
        .put("algorithm", algorithm)
        .put("format", format)
        .put("extraProperties", params)
        .toString()
}

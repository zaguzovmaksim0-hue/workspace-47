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
        currentUrl = { "https://veaja.cloud.juntadeandalucia.es/inicio/" },
    )

    @Test
    fun routesValidMultiModeSingleDocumentSignRequest() {
        val hashHex = "e3b0c44298fc1c149afbf4c8996fb92427ae41e4649b934ca495991b7852b855"
        val message = validMessageJson(
            hashes = listOf(hashHex),
            algorithm = "SHA256withRSA",
            format = "CADES",
            params = "mode=explicit\nprecalculatedHashAlgorithm=SHA-256\nfilters=nonexpired:;signingCert;",
            pageUrl = "https://veaja.cloud.juntadeandalucia.es/inicio/",
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
        assertEquals("https://veaja.cloud.juntadeandalucia.es/inicio/", accepted.pageUrl)
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
            pageUrl = "https://veaja.cloud.juntadeandalucia.es/borrador/draft-999",
        )

        val freshAdapter = VeaMultiModeBridgeAdapter(
            activeProfileId = { expectedProfileId },
            currentNavigationEpoch = { 100L },
            currentDocumentId = { documentId },
            currentOrigin = { expectedOrigin },
            currentUrl = { "https://veaja.cloud.juntadeandalucia.es/borrador/draft-999" },
        )

        val result = freshAdapter.route(
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
    fun acceptsExplicitEmptyOriginalDataArrayMatchingLength() {
        val hashHex = "e3b0c44298fc1c149afbf4c8996fb92427ae41e4649b934ca495991b7852b855"
        val message = validMessageJson(
            hashes = listOf(hashHex),
            originalData = listOf(""),
        )
        val freshAdapter = VeaMultiModeBridgeAdapter(
            activeProfileId = { expectedProfileId },
            currentNavigationEpoch = { 100L },
            currentDocumentId = { documentId },
            currentOrigin = { expectedOrigin },
        )
        val result = freshAdapter.route(
            rawMessage = message,
            sourceOrigin = originUri,
            isMainFrame = true,
            navigationEpoch = 100L,
        )
        assertTrue(result is VeaMultiModeBridgeRouteResult.Accepted)
    }

    @Test
    fun rejectsMismatchedOrNonEmptyOriginalDataArray() {
        val hashHex = "e3b0c44298fc1c149afbf4c8996fb92427ae41e4649b934ca495991b7852b855"
        val freshAdapter = VeaMultiModeBridgeAdapter(
            activeProfileId = { expectedProfileId },
            currentNavigationEpoch = { 100L },
            currentDocumentId = { documentId },
            currentOrigin = { expectedOrigin },
        )

        val mismatchedLen = validMessageJson(hashes = listOf(hashHex), originalData = listOf("", ""))
        assertEquals(
            VeaMultiModeBridgeRouteResult.Rejected(requestId, SigningErrorCode.INVALID_REQUEST),
            freshAdapter.route(mismatchedLen, originUri, true, 100L),
        )

        val nonEmpty = validMessageJson(hashes = listOf(hashHex), originalData = listOf("some-data"))
        assertEquals(
            VeaMultiModeBridgeRouteResult.Rejected(requestId, SigningErrorCode.INVALID_REQUEST),
            freshAdapter.route(nonEmpty, originUri, true, 100L),
        )
    }

    @Test
    fun rejectsUnobservedPageUrlOrDisallowedPath() {
        val hashHex = "e3b0c44298fc1c149afbf4c8996fb92427ae41e4649b934ca495991b7852b855"
        val freshAdapter = VeaMultiModeBridgeAdapter(
            activeProfileId = { expectedProfileId },
            currentNavigationEpoch = { 100L },
            currentDocumentId = { documentId },
            currentOrigin = { expectedOrigin },
        )

        val invalidPathMessage = validMessageJson(
            hashes = listOf(hashHex),
            pageUrl = "https://veaja.cloud.juntadeandalucia.es/admin/secret",
        )
        val result = freshAdapter.route(invalidPathMessage, originUri, true, 100L)
        assertEquals(VeaMultiModeBridgeRouteResult.Rejected(requestId, SigningErrorCode.INVALID_REQUEST), result)

        val foreignHostMessage = validMessageJson(
            hashes = listOf(hashHex),
            pageUrl = "https://attacker.example.com/inicio/",
        )
        val foreignResult = freshAdapter.route(foreignHostMessage, originUri, true, 100L)
        assertEquals(VeaMultiModeBridgeRouteResult.Rejected(requestId, SigningErrorCode.INVALID_REQUEST), foreignResult)
    }

    @Test
    fun rejectsUnsupportedSignFormat() {
        val hashHex = "e3b0c44298fc1c149afbf4c8996fb92427ae41e4649b934ca495991b7852b855"
        val message = validMessageJson(
            hashes = listOf(hashHex),
            format = "UNKNOWN_FORMAT",
        )
        val freshAdapter = VeaMultiModeBridgeAdapter(
            activeProfileId = { expectedProfileId },
            currentNavigationEpoch = { 100L },
            currentDocumentId = { documentId },
            currentOrigin = { expectedOrigin },
        )
        val result = freshAdapter.route(message, originUri, true, 100L)
        assertEquals(VeaMultiModeBridgeRouteResult.Rejected(requestId, SigningErrorCode.INVALID_REQUEST), result)
    }

    @Test
    fun rejectsUnknownExtraPropertiesOrMissingFilters() {
        val hashHex = "e3b0c44298fc1c149afbf4c8996fb92427ae41e4649b934ca495991b7852b855"
        val freshAdapter = VeaMultiModeBridgeAdapter(
            activeProfileId = { expectedProfileId },
            currentNavigationEpoch = { 100L },
            currentDocumentId = { documentId },
            currentOrigin = { expectedOrigin },
        )

        val unknownKey = validMessageJson(
            hashes = listOf(hashHex),
            params = "mode=explicit\nprecalculatedHashAlgorithm=SHA-256\nfilters=nonexpired:;signingCert;\nserverUrl=https://evil.com",
        )
        assertEquals(
            VeaMultiModeBridgeRouteResult.Rejected(requestId, SigningErrorCode.INVALID_REQUEST),
            freshAdapter.route(unknownKey, originUri, true, 100L),
        )

        val missingFilters = validMessageJson(
            hashes = listOf(hashHex),
            params = "mode=explicit\nprecalculatedHashAlgorithm=SHA-256",
        )
        assertEquals(
            VeaMultiModeBridgeRouteResult.Rejected(requestId, SigningErrorCode.INVALID_REQUEST),
            freshAdapter.route(missingFilters, originUri, true, 100L),
        )

        val invalidFilter = validMessageJson(
            hashes = listOf(hashHex),
            params = "mode=explicit\nprecalculatedHashAlgorithm=SHA-256\nfilters=expired:false",
        )
        assertEquals(
            VeaMultiModeBridgeRouteResult.Rejected(requestId, SigningErrorCode.INVALID_REQUEST),
            freshAdapter.route(invalidFilter, originUri, true, 100L),
        )
    }

    @Test
    fun enforcesSingleActiveRequestAndReplayProtection() {
        val hashHex = "e3b0c44298fc1c149afbf4c8996fb92427ae41e4649b934ca495991b7852b855"
        val freshAdapter = VeaMultiModeBridgeAdapter(
            activeProfileId = { expectedProfileId },
            currentNavigationEpoch = { 100L },
            currentDocumentId = { documentId },
            currentOrigin = { expectedOrigin },
        )

        val msg1 = validMessageJson(hashes = listOf(hashHex), reqId = requestId)
        val accepted = freshAdapter.route(msg1, originUri, true, 100L)
        assertTrue(accepted is VeaMultiModeBridgeRouteResult.Accepted)

        // Second concurrent request with different UUID must be rejected
        val secondReqId = UUID.randomUUID()
        val msg2 = validMessageJson(hashes = listOf(hashHex), reqId = secondReqId)
        val concurrent = freshAdapter.route(msg2, originUri, true, 100L)
        assertEquals(VeaMultiModeBridgeRouteResult.Rejected(secondReqId, SigningErrorCode.PROTOCOL_FAILED), concurrent)

        // Abandon first request
        freshAdapter.abandon(requestId)

        // Replaying same requestId must be rejected by replay ledger
        val replayed = freshAdapter.route(msg1, originUri, true, 100L)
        assertEquals(VeaMultiModeBridgeRouteResult.Rejected(requestId, SigningErrorCode.PROTOCOL_FAILED), replayed)
    }

    @Test
    fun rejectsUntrustedOriginOrFrameFailClosed() {
        val message = validMessageJson(listOf("aa".repeat(32)))
        val freshAdapter = VeaMultiModeBridgeAdapter(
            activeProfileId = { expectedProfileId },
            currentNavigationEpoch = { 100L },
            currentDocumentId = { documentId },
            currentOrigin = { expectedOrigin },
        )
        val wrongOrigin = freshAdapter.route(
            rawMessage = message,
            sourceOrigin = Uri.parse("https://evil.juntadeandalucia.es"),
            isMainFrame = true,
            navigationEpoch = 100L,
        )
        assertEquals(VeaMultiModeBridgeRouteResult.Rejected(requestId, SigningErrorCode.ORIGIN_NOT_ALLOWED), wrongOrigin)

        val wrongFrame = freshAdapter.route(
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

        val freshAdapter = VeaMultiModeBridgeAdapter(
            activeProfileId = { expectedProfileId },
            currentNavigationEpoch = { 100L },
            currentDocumentId = { documentId },
            currentOrigin = { expectedOrigin },
        )
        val result = freshAdapter.route(
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

        val freshAdapter = VeaMultiModeBridgeAdapter(
            activeProfileId = { expectedProfileId },
            currentNavigationEpoch = { 100L },
            currentDocumentId = { documentId },
            currentOrigin = { expectedOrigin },
        )
        val result = freshAdapter.route(
            rawMessage = cancelJson,
            sourceOrigin = originUri,
            isMainFrame = true,
            navigationEpoch = 100L,
        )
        assertEquals(VeaMultiModeBridgeRouteResult.Cancelled(requestId, documentId), result)
    }

    private fun validMessageJson(
        hashes: List<String>,
        originalData: List<String>? = null,
        algorithm: String = "SHA256withRSA",
        format: String = "CADES",
        params: String = "mode=explicit\nprecalculatedHashAlgorithm=SHA-256\nfilters=nonexpired:;signingCert;",
        pageUrl: String = "https://veaja.cloud.juntadeandalucia.es/inicio/",
        reqId: UUID = requestId,
    ): String = JSONObject()
        .put("type", "MINIAPPLET_MULTIMODE_SIGN")
        .put("documentId", documentId.toString())
        .put("requestId", reqId.toString())
        .put("operationArray", JSONArray(List(hashes.size) { "sign" }))
        .put("dataArray", JSONArray(hashes))
        .put("originalDataArray", if (originalData == null) JSONObject.NULL else JSONArray(originalData))
        .put("arrayLength", hashes.size)
        .put("algorithm", algorithm)
        .put("format", format)
        .put("extraProperties", params)
        .put("pageUrl", pageUrl)
        .toString()
}

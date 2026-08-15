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
    fun rejectedRequestFailureReplyCannotCarrySuccessMaterial() {
        val json = JSONObject(
            veaMultiModeFailureReplyJson(requestId, SigningErrorCode.INVALID_REQUEST),
        )

        assertEquals(VeaMultiModeBridgeAdapter.RESULT_TYPE, json.getString("type"))
        assertEquals(requestId.toString(), json.getString("requestId"))
        assertEquals("failure", json.getString("status"))
        assertEquals(SigningErrorCode.INVALID_REQUEST.name, json.getString("errorCode"))
        assertFalse(json.has("signature"))
        assertFalse(json.has("certificate"))
    }

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
            params = "mode=explicit\nprecalculatedHashAlgorithm=SHA-256\nfilters=nonexpired:;signingCert;",
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
            currentUrl = { "https://veaja.cloud.juntadeandalucia.es/inicio/" },
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
            currentUrl = { "https://veaja.cloud.juntadeandalucia.es/inicio/" },
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
    fun rejectsArbitraryRootOriginPathsWhileAcceptingExactRoot() {
        val hashHex = "e3b0c44298fc1c149afbf4c8996fb92427ae41e4649b934ca495991b7852b855"

        val rootAdapter = VeaMultiModeBridgeAdapter(
            activeProfileId = { expectedProfileId },
            currentNavigationEpoch = { 100L },
            currentDocumentId = { documentId },
            currentOrigin = { expectedOrigin },
            currentUrl = { "https://veaja.cloud.juntadeandalucia.es/" },
        )
        val exactRootMessage = validMessageJson(
            hashes = listOf(hashHex),
            pageUrl = "https://veaja.cloud.juntadeandalucia.es/",
        )
        val exactRootResult = rootAdapter.route(exactRootMessage, originUri, true, 100L)
        assertTrue(exactRootResult is VeaMultiModeBridgeRouteResult.Accepted)

        val freshAdapter = VeaMultiModeBridgeAdapter(
            activeProfileId = { expectedProfileId },
            currentNavigationEpoch = { 100L },
            currentDocumentId = { documentId },
            currentOrigin = { expectedOrigin },
            currentUrl = { "https://veaja.cloud.juntadeandalucia.es/inicio/" },
        )

        val secretMessage = validMessageJson(
            hashes = listOf(hashHex),
            pageUrl = "https://veaja.cloud.juntadeandalucia.es/admin/secret",
        )
        val secretResult = freshAdapter.route(secretMessage, originUri, true, 100L)
        assertEquals(VeaMultiModeBridgeRouteResult.Rejected(requestId, SigningErrorCode.INVALID_REQUEST), secretResult)

        val anythingMessage = validMessageJson(
            hashes = listOf(hashHex),
            pageUrl = "https://veaja.cloud.juntadeandalucia.es/anything",
        )
        val anythingResult = freshAdapter.route(anythingMessage, originUri, true, 100L)
        assertEquals(VeaMultiModeBridgeRouteResult.Rejected(requestId, SigningErrorCode.INVALID_REQUEST), anythingResult)

        val foreignHostMessage = validMessageJson(
            hashes = listOf(hashHex),
            pageUrl = "https://attacker.example.com/inicio/",
        )
        val foreignResult = freshAdapter.route(foreignHostMessage, originUri, true, 100L)
        assertEquals(VeaMultiModeBridgeRouteResult.Rejected(requestId, SigningErrorCode.INVALID_REQUEST), foreignResult)
    }

    @Test
    fun rejectsEmptyOrMissingPageUrl() {
        val hashHex = "e3b0c44298fc1c149afbf4c8996fb92427ae41e4649b934ca495991b7852b855"
        val freshAdapter = VeaMultiModeBridgeAdapter(
            activeProfileId = { expectedProfileId },
            currentNavigationEpoch = { 100L },
            currentDocumentId = { documentId },
            currentOrigin = { expectedOrigin },
            currentUrl = { "https://veaja.cloud.juntadeandalucia.es/inicio/" },
        )

        val emptyUrlMessage = validMessageJson(
            hashes = listOf(hashHex),
            pageUrl = "",
        )
        val emptyResult = freshAdapter.route(emptyUrlMessage, originUri, true, 100L)
        assertEquals(VeaMultiModeBridgeRouteResult.Rejected(requestId, SigningErrorCode.INVALID_REQUEST), emptyResult)

        val blankUrlMessage = validMessageJson(
            hashes = listOf(hashHex),
            pageUrl = "   ",
        )
        val blankResult = freshAdapter.route(blankUrlMessage, originUri, true, 100L)
        assertEquals(VeaMultiModeBridgeRouteResult.Rejected(requestId, SigningErrorCode.INVALID_REQUEST), blankResult)
    }

    @Test
    fun rejectsMismatchedPageUrlAgainstRuntime() {
        val hashHex = "e3b0c44298fc1c149afbf4c8996fb92427ae41e4649b934ca495991b7852b855"
        val freshAdapter = VeaMultiModeBridgeAdapter(
            activeProfileId = { expectedProfileId },
            currentNavigationEpoch = { 100L },
            currentDocumentId = { documentId },
            currentOrigin = { expectedOrigin },
            currentUrl = { "https://veaja.cloud.juntadeandalucia.es/borrador/draft-1" },
        )

        val mismatchedUrlMessage = validMessageJson(
            hashes = listOf(hashHex),
            pageUrl = "https://veaja.cloud.juntadeandalucia.es/borrador/draft-2",
        )
        val result = freshAdapter.route(mismatchedUrlMessage, originUri, true, 100L)
        assertEquals(VeaMultiModeBridgeRouteResult.Rejected(requestId, SigningErrorCode.NAVIGATION_CHANGED), result)

        val fragmentMatchAdapter = VeaMultiModeBridgeAdapter(
            activeProfileId = { expectedProfileId },
            currentNavigationEpoch = { 100L },
            currentDocumentId = { documentId },
            currentOrigin = { expectedOrigin },
            currentUrl = { "https://veaja.cloud.juntadeandalucia.es/borrador/draft-1#sectionA" },
        )
        val fragmentMessage = validMessageJson(
            hashes = listOf(hashHex),
            pageUrl = "https://veaja.cloud.juntadeandalucia.es/borrador/draft-1#sectionB",
        )
        val fragmentResult = fragmentMatchAdapter.route(fragmentMessage, originUri, true, 100L)
        assertTrue(fragmentResult is VeaMultiModeBridgeRouteResult.Accepted)
    }

    @Test
    fun rejectsUnsupportedSignFormatsXadesAndPades() {
        val hashHex = "e3b0c44298fc1c149afbf4c8996fb92427ae41e4649b934ca495991b7852b855"
        val freshAdapter = VeaMultiModeBridgeAdapter(
            activeProfileId = { expectedProfileId },
            currentNavigationEpoch = { 100L },
            currentDocumentId = { documentId },
            currentOrigin = { expectedOrigin },
            currentUrl = { "https://veaja.cloud.juntadeandalucia.es/inicio/" },
        )

        for (format in listOf("XADES", "PADES", "XAdES", "PAdES", "XMLDSIG", "UNKNOWN_FORMAT")) {
            val message = validMessageJson(
                hashes = listOf(hashHex),
                format = format,
            )
            val result = freshAdapter.route(message, originUri, true, 100L)
            assertEquals(
                "Format $format must be rejected",
                VeaMultiModeBridgeRouteResult.Rejected(requestId, SigningErrorCode.INVALID_REQUEST),
                result,
            )
        }
    }

    @Test
    fun rejectsUnsupportedAlgorithmsSha224AndSha384() {
        val freshAdapter = VeaMultiModeBridgeAdapter(
            activeProfileId = { expectedProfileId },
            currentNavigationEpoch = { 100L },
            currentDocumentId = { documentId },
            currentOrigin = { expectedOrigin },
            currentUrl = { "https://veaja.cloud.juntadeandalucia.es/inicio/" },
        )

        val sha384Hex = "11".repeat(48)
        val sha384Message = validMessageJson(
            hashes = listOf(sha384Hex),
            algorithm = "SHA384withRSA",
            params = "mode=explicit\nprecalculatedHashAlgorithm=SHA-384\nfilters=nonexpired:;signingCert;",
        )
        val sha384Result = freshAdapter.route(sha384Message, originUri, true, 100L)
        assertEquals(
            VeaMultiModeBridgeRouteResult.Rejected(requestId, SigningErrorCode.UNSUPPORTED_PROTOCOL),
            sha384Result,
        )

        val sha224Hex = "11".repeat(28)
        val sha224Message = validMessageJson(
            hashes = listOf(sha224Hex),
            algorithm = "SHA224withRSA",
            params = "mode=explicit\nprecalculatedHashAlgorithm=SHA-224\nfilters=nonexpired:;signingCert;",
        )
        val sha224Result = freshAdapter.route(sha224Message, originUri, true, 100L)
        assertEquals(
            VeaMultiModeBridgeRouteResult.Rejected(requestId, SigningErrorCode.UNSUPPORTED_PROTOCOL),
            sha224Result,
        )
    }

    @Test
    fun rejectsUnknownExtraPropertiesOrQualifiedFilterSuffix() {
        val hashHex = "e3b0c44298fc1c149afbf4c8996fb92427ae41e4649b934ca495991b7852b855"
        val freshAdapter = VeaMultiModeBridgeAdapter(
            activeProfileId = { expectedProfileId },
            currentNavigationEpoch = { 100L },
            currentDocumentId = { documentId },
            currentOrigin = { expectedOrigin },
            currentUrl = { "https://veaja.cloud.juntadeandalucia.es/inicio/" },
        )

        val unknownKey = validMessageJson(
            hashes = listOf(hashHex),
            params = "mode=explicit\nprecalculatedHashAlgorithm=SHA-256\nfilters=nonexpired:;signingCert;\nserverUrl=https://evil.com",
        )
        assertEquals(
            VeaMultiModeBridgeRouteResult.Rejected(requestId, SigningErrorCode.INVALID_REQUEST),
            freshAdapter.route(unknownKey, originUri, true, 100L),
        )

        val qualifiedSuffix = validMessageJson(
            hashes = listOf(hashHex),
            params = "mode=explicit\nprecalculatedHashAlgorithm=SHA-256\nfilters=nonexpired:;signingCert;qualified:12345",
        )
        assertEquals(
            VeaMultiModeBridgeRouteResult.Rejected(requestId, SigningErrorCode.INVALID_REQUEST),
            freshAdapter.route(qualifiedSuffix, originUri, true, 100L),
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
    fun boundsInvalidatedDocumentMemoryToMaximumCapacity() {
        var activeDocId: UUID? = null
        val freshAdapter = VeaMultiModeBridgeAdapter(
            activeProfileId = { expectedProfileId },
            currentNavigationEpoch = { 100L },
            currentDocumentId = { activeDocId },
            currentOrigin = { expectedOrigin },
            currentUrl = { "https://veaja.cloud.juntadeandalucia.es/inicio/" },
        )

        val docIds = List(70) { UUID.randomUUID() }
        for (doc in docIds) {
            freshAdapter.invalidateDocument(doc)
        }

        assertTrue(
            "Invalidated document memory must be bounded by MAX_INVALIDATED_DOCUMENTS (64)",
            freshAdapter.invalidatedDocumentIdsSize() <= 64,
        )
        assertEquals(64, freshAdapter.invalidatedDocumentIdsSize())

        val hashHex = "e3b0c44298fc1c149afbf4c8996fb92427ae41e4649b934ca495991b7852b855"

        // Evicted oldest document (docIds[0]) is no longer rejected due to invalidation
        activeDocId = docIds[0]
        val oldestDocMsg = validMessageJson(hashes = listOf(hashHex), reqId = UUID.randomUUID()).let {
            JSONObject(it).put("documentId", docIds[0].toString()).toString()
        }
        val oldestResult = freshAdapter.route(oldestDocMsg, originUri, true, 100L)
        assertTrue("Evicted oldest document must be accepted", oldestResult is VeaMultiModeBridgeRouteResult.Accepted)

        // Recent document (docIds[69]) must remain rejected
        activeDocId = docIds[69]
        val recentDocMsg = validMessageJson(hashes = listOf(hashHex), reqId = UUID.randomUUID()).let {
            JSONObject(it).put("documentId", docIds[69].toString()).toString()
        }
        val recentResult = freshAdapter.route(recentDocMsg, originUri, true, 100L)
        assertEquals(
            VeaMultiModeBridgeRouteResult.Rejected(null, SigningErrorCode.NAVIGATION_CHANGED),
            (recentResult as? VeaMultiModeBridgeRouteResult.Rejected)?.let {
                VeaMultiModeBridgeRouteResult.Rejected(null, it.code)
            },
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
            currentUrl = { "https://veaja.cloud.juntadeandalucia.es/inicio/" },
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
            currentUrl = { "https://veaja.cloud.juntadeandalucia.es/inicio/" },
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
            currentUrl = { "https://veaja.cloud.juntadeandalucia.es/inicio/" },
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
            currentUrl = { "https://veaja.cloud.juntadeandalucia.es/inicio/" },
        )
        val result = freshAdapter.route(
            rawMessage = cancelJson,
            sourceOrigin = originUri,
            isMainFrame = true,
            navigationEpoch = 100L,
        )
        assertEquals(VeaMultiModeBridgeRouteResult.Cancelled(requestId, documentId), result)
    }

    @Test
    fun rejectsWhenCurrentNavigationEpochIsNull() {
        val message = validMessageJson(
            hashes = listOf("e3b0c44298fc1c149afbf4c8996fb92427ae41e4649b934ca495991b7852b855"),
        )
        val nullEpochAdapter = VeaMultiModeBridgeAdapter(
            activeProfileId = { expectedProfileId },
            currentNavigationEpoch = { null },
            currentDocumentId = { documentId },
            currentOrigin = { expectedOrigin },
            currentUrl = { "https://veaja.cloud.juntadeandalucia.es/inicio/" },
        )

        val result = nullEpochAdapter.route(message, originUri, true, 100L)

        assertEquals(
            VeaMultiModeBridgeRouteResult.Rejected(requestId, SigningErrorCode.NAVIGATION_CHANGED),
            result,
        )
    }

    @Test
    fun rejectsWhenCurrentOriginIsNullOrFailClosed() {
        val hashHex = "e3b0c44298fc1c149afbf4c8996fb92427ae41e4649b934ca495991b7852b855"
        val message = validMessageJson(hashes = listOf(hashHex))

        val nullOriginAdapter = VeaMultiModeBridgeAdapter(
            activeProfileId = { expectedProfileId },
            currentNavigationEpoch = { 100L },
            currentDocumentId = { documentId },
            currentOrigin = { null },
            currentUrl = { "https://veaja.cloud.juntadeandalucia.es/inicio/" },
        )
        val nullResult = nullOriginAdapter.route(message, originUri, true, 100L)
        assertEquals(VeaMultiModeBridgeRouteResult.Rejected(requestId, SigningErrorCode.ORIGIN_NOT_ALLOWED), nullResult)

        val mismatchedOriginAdapter = VeaMultiModeBridgeAdapter(
            activeProfileId = { expectedProfileId },
            currentNavigationEpoch = { 100L },
            currentDocumentId = { documentId },
            currentOrigin = { TrustedOrigin("https", "other.domain.es", 443) },
            currentUrl = { "https://veaja.cloud.juntadeandalucia.es/inicio/" },
        )
        val mismatchResult = mismatchedOriginAdapter.route(message, originUri, true, 100L)
        assertEquals(VeaMultiModeBridgeRouteResult.Rejected(requestId, SigningErrorCode.ORIGIN_NOT_ALLOWED), mismatchResult)
    }

    @Test
    fun rejectsWhenCurrentUrlIsNull() {
        val hashHex = "e3b0c44298fc1c149afbf4c8996fb92427ae41e4649b934ca495991b7852b855"
        val message = validMessageJson(hashes = listOf(hashHex))

        val nullUrlAdapter = VeaMultiModeBridgeAdapter(
            activeProfileId = { expectedProfileId },
            currentNavigationEpoch = { 100L },
            currentDocumentId = { documentId },
            currentOrigin = { expectedOrigin },
            currentUrl = { null },
        )
        val nullResult = nullUrlAdapter.route(message, originUri, true, 100L)
        assertEquals(VeaMultiModeBridgeRouteResult.Rejected(requestId, SigningErrorCode.NAVIGATION_CHANGED), nullResult)
    }

    @Test
    fun rejectsWhenCurrentDocumentIdIsNullOrMismatched() {
        val hashHex = "e3b0c44298fc1c149afbf4c8996fb92427ae41e4649b934ca495991b7852b855"
        val message = validMessageJson(hashes = listOf(hashHex))

        val nullDocAdapter = VeaMultiModeBridgeAdapter(
            activeProfileId = { expectedProfileId },
            currentNavigationEpoch = { 100L },
            currentDocumentId = { null },
            currentOrigin = { expectedOrigin },
            currentUrl = { "https://veaja.cloud.juntadeandalucia.es/inicio/" },
        )
        val nullResult = nullDocAdapter.route(message, originUri, true, 100L)
        assertEquals(VeaMultiModeBridgeRouteResult.Rejected(requestId, SigningErrorCode.NAVIGATION_CHANGED), nullResult)

        val mismatchedDocAdapter = VeaMultiModeBridgeAdapter(
            activeProfileId = { expectedProfileId },
            currentNavigationEpoch = { 100L },
            currentDocumentId = { UUID.randomUUID() },
            currentOrigin = { expectedOrigin },
            currentUrl = { "https://veaja.cloud.juntadeandalucia.es/inicio/" },
        )
        val mismatchResult = mismatchedDocAdapter.route(message, originUri, true, 100L)
        assertEquals(VeaMultiModeBridgeRouteResult.Rejected(requestId, SigningErrorCode.NAVIGATION_CHANGED), mismatchResult)
    }

    @Test
    fun rejectsNonExactPrecalculatedHashAlgorithmSpellings() {
        val aliases = listOf("SHA256", "sha-256", "sha256")
        for (alias in aliases) {
            val freshAdapter = VeaMultiModeBridgeAdapter(
                activeProfileId = { expectedProfileId },
                currentNavigationEpoch = { 100L },
                currentDocumentId = { documentId },
                currentOrigin = { expectedOrigin },
                currentUrl = { "https://veaja.cloud.juntadeandalucia.es/inicio/" },
            )
            val message = validMessageJson(
                hashes = listOf("e3b0c44298fc1c149afbf4c8996fb92427ae41e4649b934ca495991b7852b855"),
                algorithm = "SHA256withRSA",
                params = "mode=explicit\nprecalculatedHashAlgorithm=$alias\nfilters=nonexpired:;signingCert;",
            )

            val result = freshAdapter.route(message, originUri, true, 100L)

            assertEquals(
                "Alias $alias must be rejected at the native boundary",
                VeaMultiModeBridgeRouteResult.Rejected(requestId, SigningErrorCode.INVALID_REQUEST),
                result,
            )
        }
    }

    @Test
    fun acceptsValidExtraPropertiesWithCrlfLineEndings() {
        val hashHex = "e3b0c44298fc1c149afbf4c8996fb92427ae41e4649b934ca495991b7852b855"
        val crlfParams = "mode=explicit\r\nprecalculatedHashAlgorithm=SHA-256\r\nfilters=nonexpired:;signingCert;\r\n"
        val message = validMessageJson(
            hashes = listOf(hashHex),
            params = crlfParams,
        )
        val result = adapter.route(message, originUri, true, 100L)
        assertTrue(result is VeaMultiModeBridgeRouteResult.Accepted)
    }

    @Test
    fun rejectsExtraPropertiesWithDuplicateKeysOrXmodeOrStandaloneCr() {
        val hashHex = "e3b0c44298fc1c149afbf4c8996fb92427ae41e4649b934ca495991b7852b855"

        val xmodeMsg = validMessageJson(
            hashes = listOf(hashHex),
            params = "xmode=explicit\nprecalculatedHashAlgorithm=SHA-256\nfilters=nonexpired:;signingCert;",
        )
        assertEquals(
            VeaMultiModeBridgeRouteResult.Rejected(requestId, SigningErrorCode.INVALID_REQUEST),
            adapter.route(xmodeMsg, originUri, true, 100L),
        )

        val dupKeyMsg = validMessageJson(
            hashes = listOf(hashHex),
            params = "mode=explicit\nmode=explicit\nprecalculatedHashAlgorithm=SHA-256\nfilters=nonexpired:;signingCert;",
        )
        assertEquals(
            VeaMultiModeBridgeRouteResult.Rejected(requestId, SigningErrorCode.INVALID_REQUEST),
            adapter.route(dupKeyMsg, originUri, true, 100L),
        )

        val dupHashMsg = validMessageJson(
            hashes = listOf(hashHex),
            params = "mode=explicit\nprecalculatedHashAlgorithm=SHA-256\nprecalculatedHashAlgorithm=SHA-256\nfilters=nonexpired:;signingCert;",
        )
        assertEquals(
            VeaMultiModeBridgeRouteResult.Rejected(requestId, SigningErrorCode.INVALID_REQUEST),
            adapter.route(dupHashMsg, originUri, true, 100L),
        )

        val standaloneCrMsg = validMessageJson(
            hashes = listOf(hashHex),
            params = "mode=explicit\rprecalculatedHashAlgorithm=SHA-256\rfilters=nonexpired:;signingCert;",
        )
        assertEquals(
            VeaMultiModeBridgeRouteResult.Rejected(requestId, SigningErrorCode.INVALID_REQUEST),
            adapter.route(standaloneCrMsg, originUri, true, 100L),
        )

        val malformedMsg = validMessageJson(
            hashes = listOf(hashHex),
            params = "mode=explicit\nmalformedline\nfilters=nonexpired:;signingCert;",
        )
        assertEquals(
            VeaMultiModeBridgeRouteResult.Rejected(requestId, SigningErrorCode.INVALID_REQUEST),
            adapter.route(malformedMsg, originUri, true, 100L),
        )

        val mismatchedHashMsg = validMessageJson(
            hashes = listOf(hashHex),
            algorithm = "SHA256withRSA",
            params = "mode=explicit\nprecalculatedHashAlgorithm=SHA-1\nfilters=nonexpired:;signingCert;",
        )
        assertEquals(
            VeaMultiModeBridgeRouteResult.Rejected(requestId, SigningErrorCode.INVALID_REQUEST),
            adapter.route(mismatchedHashMsg, originUri, true, 100L),
        )
    }

    @Test
    fun replyChannelFailsClosedWhenOriginUrlOrDocIdIsNull() {
        val reqId = UUID.randomUUID()
        val docId = UUID.randomUUID()
        var postedMessage: String? = null

        // Null current origin
        val channelNullOrigin = VeaMultiModeReplyChannel(
            requestId = reqId,
            documentId = docId,
            navigationEpoch = 100L,
            sourceOrigin = expectedOrigin,
            pageUrl = "https://veaja.cloud.juntadeandalucia.es/inicio/",
            postMessage = { postedMessage = it },
            currentNavigationEpoch = { 100L },
            currentOrigin = { null },
            currentDocumentId = { docId },
            currentPageUrl = { "https://veaja.cloud.juntadeandalucia.es/inicio/" },
        )
        val successNullOrigin = channelNullOrigin.success("sigB64", "certB64")
        org.junit.Assert.assertFalse(successNullOrigin)
        assertTrue(postedMessage?.contains("NAVIGATION_CHANGED") == true)

        // Null current documentId
        postedMessage = null
        val channelNullDoc = VeaMultiModeReplyChannel(
            requestId = reqId,
            documentId = docId,
            navigationEpoch = 100L,
            sourceOrigin = expectedOrigin,
            pageUrl = "https://veaja.cloud.juntadeandalucia.es/inicio/",
            postMessage = { postedMessage = it },
            currentNavigationEpoch = { 100L },
            currentOrigin = { expectedOrigin },
            currentDocumentId = { null },
            currentPageUrl = { "https://veaja.cloud.juntadeandalucia.es/inicio/" },
        )
        val successNullDoc = channelNullDoc.success("sigB64", "certB64")
        org.junit.Assert.assertFalse(successNullDoc)

        // Null current pageUrl
        postedMessage = null
        val channelNullUrl = VeaMultiModeReplyChannel(
            requestId = reqId,
            documentId = docId,
            navigationEpoch = 100L,
            sourceOrigin = expectedOrigin,
            pageUrl = "https://veaja.cloud.juntadeandalucia.es/inicio/",
            postMessage = { postedMessage = it },
            currentNavigationEpoch = { 100L },
            currentOrigin = { expectedOrigin },
            currentDocumentId = { docId },
            currentPageUrl = { null },
        )
        val successNullUrl = channelNullUrl.success("sigB64", "certB64")
        org.junit.Assert.assertFalse(successNullUrl)
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

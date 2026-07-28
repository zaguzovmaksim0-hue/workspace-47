package dev.junta.firmamobile.browser

import android.net.Uri
import dev.junta.firmamobile.signing.LocalSignature
import dev.junta.firmamobile.signing.MiniAppletPayloadCodec
import dev.junta.firmamobile.signing.SensitiveSignatureCopyObserver
import dev.junta.firmamobile.signing.SigningAlgorithm
import dev.junta.firmamobile.signing.SigningErrorCode
import dev.junta.firmamobile.signing.SigningFormat
import dev.junta.firmamobile.signing.JuntaOfvirtualTriPhaseAdapter
import dev.junta.firmamobile.signing.LocalCadesDetachedAdapter
import dev.junta.firmamobile.network.TrustedOrigin
import java.time.Clock
import java.time.Instant
import java.time.ZoneOffset
import java.util.Base64
import org.json.JSONObject
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
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
class MiniAppletBridgeAdapterTest {
    private val adapter = adapterFor("junta-andalucia")

    @Test
    fun exactTrustedJuntaMiniAppletCallNormalizesToOneOwnedRequest() {
        val result = adapter.route(
            rawMessage = message(),
            sourceOrigin = TRUSTED_ORIGIN,
            isMainFrame = true,
        ) as MiniAppletBridgeRouteResult.Accepted

        result.request.normalized.use { request ->
            assertEquals(REQUEST_ID, request.requestId.toString())
            assertEquals(SigningAlgorithm.SHA1_WITH_RSA, request.algorithm)
            assertEquals(SigningFormat.CADES, request.format)
            assertEquals("www.juntadeandalucia.es", request.context.origin.host)
            assertEquals(DOCUMENT_ID, request.context.navigationId.value)
            request.withPayload { payload ->
                MiniAppletPayloadCodec.withDecoded(payload) { data, extraProperties ->
                    assertArrayEquals(DATA, data)
                    assertEquals(EXTRA_PROPERTIES, extraProperties)
                }
            }
        }
    }

    @Test
    fun exactRedSaraAutoScriptCallNormalizesOnlyTheObservedXadesTuple() {
        val result = adapterFor("reg-age-redsara").route(
            rawMessage = JSONObject()
                .put("type", "MINIAPPLET_SIGN")
                .put("documentId", DOCUMENT_ID)
                .put("requestId", REQUEST_ID)
                .put("dataB64", Base64.getEncoder().encodeToString("<r/>".encodeToByteArray()))
                .put("algorithm", "SHA512withRSA")
                .put("format", "XAdES Detached")
                .put("extraProperties", JSONObject.NULL)
                .toString(),
            sourceOrigin = Uri.parse("https://reg.redsara.es"),
            isMainFrame = true,
            navigationEpoch = 9,
        ) as MiniAppletBridgeRouteResult.Accepted

        result.request.normalized.use { request ->
            assertEquals("reg-age-redsara", request.context.profileId)
            assertEquals("reg.redsara.es", request.context.origin.host)
            assertEquals(9, request.context.navigationEpoch)
            assertEquals(SigningAlgorithm.SHA512_WITH_RSA, request.algorithm)
            assertEquals(SigningFormat.XADES, request.format)
            request.withPayload { payload ->
                MiniAppletPayloadCodec.withDecoded(payload) { data, properties ->
                    assertArrayEquals("<r/>".encodeToByteArray(), data)
                    assertEquals("", properties)
                }
            }
        }
    }

    @Test
    fun exactJuntaOfvirtualMiniAppletCallUsesBoundProfileCallbackAndProtocol() {
        val result = adapterFor("junta-ofvirtual").route(
            rawMessage = ofvirtualMessage(),
            sourceOrigin = OFVIRTUAL_ORIGIN,
            isMainFrame = true,
            navigationEpoch = 21,
        ) as MiniAppletBridgeRouteResult.Accepted

        result.request.normalized.use { request ->
            assertEquals("junta-ofvirtual", request.context.profileId)
            assertEquals("ws072.juntadeandalucia.es", request.context.origin.host)
            assertEquals(21, request.context.navigationEpoch)
            assertEquals(JuntaOfvirtualTriPhaseAdapter.ID, request.protocolId)
            assertEquals(SigningAlgorithm.SHA1_WITH_RSA, request.algorithm)
            assertEquals(SigningFormat.CADES, request.format)
            request.withPayload { payload ->
                MiniAppletPayloadCodec.withDecoded(payload) { data, properties ->
                    assertArrayEquals(DATA, data)
                    val parsed = java.util.Properties().apply { load(properties.reader()) }
                    assertEquals(setOf("filters", "serverUrl"), parsed.stringPropertyNames())
                    assertEquals(
                        "keyusage.digitalsignature:true;nonexpired:",
                        parsed.getProperty("filters"),
                    )
                    assertEquals(JuntaOfvirtualTriPhaseAdapter.ENDPOINT, parsed.getProperty("serverUrl"))
                    assertEquals(null, parsed.getProperty("mode"))
                }
            }
        }
    }

    @Test
    fun exactAragonSirawLoginRoutesToLocalDetachedCadesOnlyForSelectedProfile() {
        val challenge = ByteArray(LocalCadesDetachedAdapter.CHALLENGE_BYTES) { index ->
            (index + 1).toByte()
        }
        val rawMessage = JSONObject()
            .put("type", "MINIAPPLET_SIGN")
            .put("documentId", DOCUMENT_ID)
            .put("requestId", REQUEST_ID)
            .put("dataB64", Base64.getEncoder().encodeToString(challenge))
            .put("algorithm", "SHA1withRSA")
            .put("format", "CAdES")
            .put("extraProperties", ARAGON_PROPERTIES)
            .toString()

        val accepted = adapterFor("aragon-siraw").route(
            rawMessage = rawMessage,
            sourceOrigin = ARAGON_ORIGIN,
            isMainFrame = true,
            navigationEpoch = 31,
        ) as MiniAppletBridgeRouteResult.Accepted

        accepted.request.normalized.use { request ->
            assertEquals("aragon-siraw", request.context.profileId)
            assertEquals("aplicaciones.aragon.es", request.context.origin.host)
            assertEquals(31, request.context.navigationEpoch)
            assertEquals(LocalCadesDetachedAdapter.ID, request.protocolId)
            assertEquals(SigningAlgorithm.SHA1_WITH_RSA, request.algorithm)
            assertEquals(SigningFormat.CADES, request.format)
            request.withPayload { payload ->
                MiniAppletPayloadCodec.withDecoded(payload) { data, properties ->
                    assertArrayEquals(challenge, data)
                    assertEquals(ARAGON_PROPERTIES, properties)
                }
            }
        }

        val wrongProfile = adapterFor("junta-andalucia").route(
            rawMessage = rawMessage,
            sourceOrigin = ARAGON_ORIGIN,
            isMainFrame = true,
            navigationEpoch = 31,
        ) as MiniAppletBridgeRouteResult.Rejected
        assertEquals(SigningErrorCode.ORIGIN_NOT_ALLOWED, wrongProfile.code)

        val wrongProperties = adapterFor("aragon-siraw").route(
            rawMessage = rawMessage.replace("filter=nonexpired", "filter=qualified:123"),
            sourceOrigin = ARAGON_ORIGIN,
            isMainFrame = true,
            navigationEpoch = 31,
        ) as MiniAppletBridgeRouteResult.Rejected
        assertEquals(SigningErrorCode.INVALID_REQUEST, wrongProperties.code)
        challenge.fill(0)
    }

    @Test
    fun juntaOfvirtualRejectsWrongActiveProfileAndStaleCallbackClearsFailClosed() {
        val wrongProfile = adapterFor("junta-andalucia").route(
            rawMessage = ofvirtualMessage(),
            sourceOrigin = OFVIRTUAL_ORIGIN,
            isMainFrame = true,
            navigationEpoch = 21,
        ) as MiniAppletBridgeRouteResult.Rejected
        assertEquals(SigningErrorCode.ORIGIN_NOT_ALLOWED, wrongProfile.code)

        val accepted = adapterFor("junta-ofvirtual").route(
            rawMessage = ofvirtualMessage(),
            sourceOrigin = OFVIRTUAL_ORIGIN,
            isMainFrame = true,
            navigationEpoch = 21,
        ) as MiniAppletBridgeRouteResult.Accepted
        var epoch = 21L
        val origin = TrustedOrigin("https", "ws072.juntadeandalucia.es", 443)
        val posted = mutableListOf<String>()
        val registry = MiniAppletReplyRegistry(
            currentNavigationEpoch = { epoch },
            currentOrigin = { origin },
        )
        val channel = checkNotNull(
            registry.create(
                accepted.request.normalized.requestId,
                accepted.request.normalized.context,
                posted::add,
            ),
        )

        epoch++

        assertFalse(channel.success(LocalSignature(byteArrayOf(1)), byteArrayOf(2)))
        assertTrue(posted.isEmpty())
        assertTrue(registry.abandonAll().isEmpty())
        accepted.request.normalized.close()
    }

    @Test
    fun redSaraLookalikeIframeWrongTupleAndPropertiesFailClosed() {
        val redSaraAdapter = adapterFor("reg-age-redsara")
        val valid = JSONObject()
            .put("type", "MINIAPPLET_SIGN")
            .put("documentId", DOCUMENT_ID)
            .put("requestId", REQUEST_ID)
            .put("dataB64", Base64.getEncoder().encodeToString("<r/>".encodeToByteArray()))
            .put("algorithm", "SHA512withRSA")
            .put("format", "XAdES Detached")
            .put("extraProperties", JSONObject.NULL)
            .toString()
        assertRejected(valid, Uri.parse("https://reg.redsara.es.evil.example"), true, redSaraAdapter)
        assertRejected(valid, Uri.parse("https://reg.redsara.es"), false, redSaraAdapter)
        assertRejected(
            valid.replace("SHA512withRSA", "SHA256withRSA"),
            Uri.parse("https://reg.redsara.es"),
            true,
            redSaraAdapter,
        )
        assertRejected(
            valid.replace("XAdES Detached", "CAdES"),
            Uri.parse("https://reg.redsara.es"),
            true,
            redSaraAdapter,
        )
        assertRejected(
            valid.replace("null", "\"mode=explicit\""),
            Uri.parse("https://reg.redsara.es"),
            true,
            redSaraAdapter,
        )
    }

    @Test
    fun trustedOriginIsRejectedWhenEffectiveProfileIsMissing() {
        val adapterWithoutEffectiveProfile = MiniAppletBridgeAdapter(
            clock = Clock.fixed(Instant.parse("2030-01-01T00:00:00Z"), ZoneOffset.UTC),
            activeProfileId = { null },
        )

        val rejected = adapterWithoutEffectiveProfile.route(
            rawMessage = message(),
            sourceOrigin = TRUSTED_ORIGIN,
            isMainFrame = true,
        ) as MiniAppletBridgeRouteResult.Rejected

        assertEquals(SigningErrorCode.ORIGIN_NOT_ALLOWED, rejected.code)
    }

    @Test
    fun catalogSelectedProfileCannotBeChangedByAnotherTrustedOrigin() {
        val selectedJunta = MiniAppletBridgeAdapter(
            activeProfileId = { dev.junta.firmamobile.profile.ProfileId("junta-andalucia") },
        )
        val redSaraMessage = JSONObject()
            .put("type", "MINIAPPLET_SIGN")
            .put("documentId", DOCUMENT_ID)
            .put("requestId", REQUEST_ID)
            .put("dataB64", Base64.getEncoder().encodeToString("<r/>".encodeToByteArray()))
            .put("algorithm", "SHA512withRSA")
            .put("format", "XAdES Detached")
            .put("extraProperties", JSONObject.NULL)
            .toString()

        val rejected = selectedJunta.route(
            rawMessage = redSaraMessage,
            sourceOrigin = Uri.parse("https://reg.redsara.es"),
            isMainFrame = true,
        ) as MiniAppletBridgeRouteResult.Rejected

        assertEquals(SigningErrorCode.ORIGIN_NOT_ALLOWED, rejected.code)
    }

    @Test
    fun exactUnizarChallengeNormalizesOnlyTheObservedLegacyTuple() {
        val hash = ByteArray(20) { index -> (index + 1).toByte() }
        val properties =
            "precalculatedHashAlgorithm=SHA1\nserverUrl=${dev.junta.firmamobile.signing.UnizarTriPhaseAdapter.ENDPOINT}"
        val unizarAdapter = adapterFor("unizar-tramitador")
        val result = unizarAdapter.route(
            rawMessage = JSONObject()
                .put("type", "MINIAPPLET_SIGN")
                .put("documentId", DOCUMENT_ID)
                .put("requestId", REQUEST_ID)
                .put("dataB64", Base64.getEncoder().encodeToString(hash))
                .put("algorithm", "SHA1withRSA")
                .put("format", "CAdES")
                .put("extraProperties", properties)
                .toString(),
            sourceOrigin = Uri.parse("https://tramita.unizar.es"),
            isMainFrame = true,
            navigationEpoch = 11,
        ) as MiniAppletBridgeRouteResult.Accepted

        result.request.normalized.use { request ->
            assertEquals("unizar-tramitador", request.context.profileId)
            assertEquals(11, request.context.navigationEpoch)
            assertEquals(SigningAlgorithm.SHA1_WITH_RSA, request.algorithm)
            assertEquals(SigningFormat.CADES, request.format)
            request.withPayload { payload ->
                MiniAppletPayloadCodec.withDecoded(payload) { data, normalizedProperties ->
                    assertArrayEquals(hash, data)
                    assertEquals(properties, normalizedProperties)
                }
            }
        }

        val wrongProperties = properties.replace("precalculatedHashAlgorithm=SHA1", "mode=explicit")
        assertRejected(
            JSONObject()
                .put("type", "MINIAPPLET_SIGN")
                .put("documentId", DOCUMENT_ID)
                .put("requestId", REQUEST_ID)
                .put("dataB64", Base64.getEncoder().encodeToString(hash))
                .put("algorithm", "SHA1withRSA")
                .put("format", "CAdES")
                .put("extraProperties", wrongProperties)
                .toString(),
            Uri.parse("https://tramita.unizar.es"),
            true,
            unizarAdapter,
        )
        hash.fill(0)
    }

    @Test
    fun unknownOriginSubframeWrongAlgorithmFormatDuplicateAndOversizeFailClosed() {
        assertRejected(message(), Uri.parse("https://evil.example"), true)
        assertRejected(message(), TRUSTED_ORIGIN, false)
        assertRejected(message().replace("SHA1withRSA", "MD5withRSA"), TRUSTED_ORIGIN, true)
        assertRejected(message().replace("CAdES", "XAdES"), TRUSTED_ORIGIN, true)
        assertRejected(
            message().replace(
                "filters=keyusage.digitalsignature:true;nonexpired:",
                "filters=keyusage.nonrepudiation:true;nonexpired:",
            ),
            TRUSTED_ORIGIN,
            true,
        )
        assertRejected(
            message().replace(
                "filters=keyusage.digitalsignature:true;nonexpired:",
                "filters=keyusage.digitalsignature:true;nonexpired:\nnote=unexpected",
            ),
            TRUSTED_ORIGIN,
            true,
        )
        assertRejected(
            message().replace("filters=keyusage.digitalsignature:true;nonexpired:", "mode=explicit"),
            TRUSTED_ORIGIN,
            true,
        )
        assertRejected(
            message().replace(
                "\"requestId\":\"$REQUEST_ID\"",
                "\"requestId\":\"$REQUEST_ID\",\"requestId\":\"$REQUEST_ID\"",
            ),
            TRUSTED_ORIGIN,
            true,
        )
        assertRejected(
            message(dataB64 = "A".repeat(MiniAppletBridgeAdapter.MAX_DATA_BASE64_CHARS + 1)),
            TRUSTED_ORIGIN,
            true,
        )
    }

    @Test
    fun successDeliveryPostsSignatureThenCertificateOnceAndClearsOwnedBytes() {
        val posted = mutableListOf<String>()
        val signatureClears = mutableListOf<Boolean>()
        val signature = LocalSignature(
            bytes = SIGNATURE.copyOf(),
            observer = SensitiveSignatureCopyObserver(signatureClears::add),
        )
        val certificate = CERTIFICATE.copyOf()
        val channel = MiniAppletReplyChannel(
            requestId = java.util.UUID.fromString(REQUEST_ID),
            postMessage = posted::add,
        )

        assertTrue(channel.success(signature, certificate))
        assertFalse(channel.success(LocalSignature(byteArrayOf(9)), byteArrayOf(8)))
        assertFalse(channel.failure(SigningErrorCode.PROTOCOL_FAILED))

        val json = JSONObject(posted.single())
        assertEquals("MINIAPPLET_RESULT", json.getString("type"))
        assertEquals("success", json.getString("status"))
        assertEquals(Base64.getEncoder().encodeToString(SIGNATURE), json.getString("signature"))
        assertEquals(
            Base64.getEncoder().encodeToString(CERTIFICATE),
            json.getString("certificate"),
        )
        assertTrue(certificate.all { it == 0.toByte() })
        assertEquals(listOf(true), signatureClears)
    }

    @Test
    fun closedErrorDeliveryIsOneShotAndContainsNoThrowableOrPayload() {
        val posted = mutableListOf<String>()
        val channel = MiniAppletReplyChannel(
            requestId = java.util.UUID.fromString(REQUEST_ID),
            postMessage = posted::add,
        )

        assertTrue(channel.failure(SigningErrorCode.USER_CANCELLED))
        assertFalse(channel.failure(SigningErrorCode.PROTOCOL_FAILED))

        val json = JSONObject(posted.single())
        assertEquals("error", json.getString("status"))
        assertEquals("USER_CANCELLED", json.getString("errorCode"))
        assertFalse(json.has("signature"))
        assertFalse(json.has("certificate"))
        assertFalse(posted.single().contains("Exception"))
    }

    @Test
    fun trustedCancelMessageReleasesTheExactRequestOnly() {
        val result = adapter.route(
            rawMessage = JSONObject()
                .put("type", "MINIAPPLET_CANCEL")
                .put("documentId", DOCUMENT_ID)
                .put("requestId", REQUEST_ID)
                .toString(),
            sourceOrigin = TRUSTED_ORIGIN,
            isMainFrame = true,
        ) as MiniAppletBridgeRouteResult.Cancelled

        assertEquals(REQUEST_ID, result.requestId.toString())
        assertEquals(DOCUMENT_ID, result.navigationId.value)
    }

    @Test
    fun oversizedNonMiniAppletMessageRemainsAvailableToTheExistingUriRouter() {
        val existingProtocolMessage = JSONObject()
            .put("type", "AFIRMA_URI")
            .put("requestId", REQUEST_ID)
            .put("uri", "afirma://sign?dat=" + "A".repeat(MiniAppletBridgeAdapter.MAX_MESSAGE_CHARS))
            .toString()

        assertTrue(
            adapter.route(existingProtocolMessage, TRUSTED_ORIGIN, true) is
                MiniAppletBridgeRouteResult.NotApplicable,
        )
    }

    private fun adapterFor(profileId: String): MiniAppletBridgeAdapter = MiniAppletBridgeAdapter(
        clock = Clock.fixed(Instant.parse("2030-01-01T00:00:00Z"), ZoneOffset.UTC),
        activeProfileId = { dev.junta.firmamobile.profile.ProfileId(profileId) },
    )

    private fun assertRejected(
        rawMessage: String,
        origin: Uri,
        isMainFrame: Boolean,
        bridgeAdapter: MiniAppletBridgeAdapter = adapter,
    ) {
        assertTrue(
            bridgeAdapter.route(rawMessage, origin, isMainFrame) is
                MiniAppletBridgeRouteResult.Rejected,
        )
    }

    private fun message(
        dataB64: String = Base64.getEncoder().encodeToString(DATA),
    ): String = JSONObject()
        .put("type", "MINIAPPLET_SIGN")
        .put("documentId", DOCUMENT_ID)
        .put("requestId", REQUEST_ID)
        .put("dataB64", dataB64)
        .put("algorithm", "SHA1withRSA")
        .put("format", "CAdES")
        .put("extraProperties", EXTRA_PROPERTIES)
        .toString()

    private fun ofvirtualMessage(): String = JSONObject()
        .put("type", "MINIAPPLET_SIGN")
        .put("documentId", DOCUMENT_ID)
        .put("requestId", REQUEST_ID)
        .put("dataB64", Base64.getEncoder().encodeToString(DATA))
        .put("algorithm", "SHA1withRSA")
        .put("format", "CAdES")
        .put("extraProperties", OFVIRTUAL_PROPERTIES)
        .toString()

    private companion object {
        const val REQUEST_ID = "123e4567-e89b-42d3-a456-426614174000"
        const val DOCUMENT_ID = "123e4567-e89b-42d3-a456-426614174001"
        const val EXTRA_PROPERTIES =
            "serverUrl=https://ws024.juntadeandalucia.es/afirma-validator-miniapplet-1_4/" +
                "sign/TriPhaseSignatureService\nfilters=keyusage.digitalsignature:true;nonexpired:"
        val TRUSTED_ORIGIN: Uri = Uri.parse("https://www.juntadeandalucia.es")
        val OFVIRTUAL_ORIGIN: Uri = Uri.parse("https://ws072.juntadeandalucia.es")
        val ARAGON_ORIGIN: Uri = Uri.parse("https://aplicaciones.aragon.es")
        const val ARAGON_PROPERTIES = "mode=explicit\nfilter=nonexpired"
        const val OFVIRTUAL_PROPERTIES =
            "filters=keyusage.digitalsignature:true;nonexpired:\n" +
                "serverUrl=https://ws024.juntadeandalucia.es/" +
                "afirma-validator-miniapplet-1_5/sign/TriPhaseSignatureService"
        val DATA = "synthetic-miniapplet-data".encodeToByteArray()
        val SIGNATURE = byteArrayOf(1, 2, 3, 4)
        val CERTIFICATE = byteArrayOf(5, 6, 7, 8)
    }
}

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
import dev.junta.firmamobile.signing.ProtocolAdapterBinding
import dev.junta.firmamobile.signing.ProtocolAdapterRegistry
import dev.junta.firmamobile.signing.SigningProtocolId
import dev.junta.firmamobile.network.TrustedOrigin
import dev.junta.firmamobile.profile.BuildTrustPolicy
import dev.junta.firmamobile.profile.CallbackContractId
import dev.junta.firmamobile.profile.Capability
import dev.junta.firmamobile.profile.CertificateFilterRules
import dev.junta.firmamobile.profile.CompatibilityStatus
import dev.junta.firmamobile.profile.ExactOrigin
import dev.junta.firmamobile.profile.OperationPolicy
import dev.junta.firmamobile.profile.ProfileActivation
import dev.junta.firmamobile.profile.ProfileId
import dev.junta.firmamobile.profile.ProtocolInputAdapterId
import dev.junta.firmamobile.profile.ProtocolOperation
import dev.junta.firmamobile.profile.SignatureAlgorithm
import dev.junta.firmamobile.profile.SignatureFormat
import dev.junta.firmamobile.profile.SignaturePackaging
import dev.junta.firmamobile.profile.SiteProfile
import dev.junta.firmamobile.profile.SiteProfileCatalog
import dev.junta.firmamobile.profile.SiteProfileRegistry
import java.net.URI
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
    fun exactUgrAutoScriptCallNormalizesTheLiteralToTheBoundDetachedCadesContract() {
        val result = adapterFor(UGR_PROFILE_ID).route(
            rawMessage = ugrMessage(),
            sourceOrigin = UGR_ORIGIN,
            isMainFrame = true,
            navigationEpoch = 44,
        ) as MiniAppletBridgeRouteResult.Accepted

        result.request.normalized.use { request ->
            assertEquals(UGR_PROFILE_ID, request.context.profileId)
            assertEquals(1, request.context.profileVersion)
            assertEquals("sede.ugr.es", request.context.origin.host)
            assertEquals(44, request.context.navigationEpoch)
            assertEquals(UGR_PROTOCOL_ID, request.protocolId.value)
            assertEquals(SigningAlgorithm.SHA1_WITH_RSA, request.algorithm)
            assertEquals(SigningFormat.CADES, request.format)
            request.withPayload { payload ->
                MiniAppletPayloadCodec.withDecoded(payload) { data, properties ->
                    assertArrayEquals(UGR_DATA, data)
                    assertEquals("", properties)
                }
            }
        }
    }

    @Test
    fun exactCantabriaRecAutoScriptCallNormalizesTheRuntimeChallengeToTheBoundContract() {
        val result = adapterFor(CANTABRIA_PROFILE_ID).route(
            rawMessage = cantabriaMessage(),
            sourceOrigin = CANTABRIA_ORIGIN,
            isMainFrame = true,
            navigationEpoch = 47,
        ) as MiniAppletBridgeRouteResult.Accepted

        result.request.normalized.use { request ->
            assertEquals(CANTABRIA_PROTOCOL_ID, request.protocolId.value)
            assertEquals(CANTABRIA_PROFILE_ID, request.context.profileId)
            assertEquals(1, request.context.profileVersion)
            assertEquals("rec.cantabria.es", request.context.origin.host)
            assertEquals(47, request.context.navigationEpoch)
            assertEquals(SigningAlgorithm.SHA512_WITH_RSA, request.algorithm)
            assertEquals(SigningFormat.CADES, request.format)
            request.withPayload { payload ->
                MiniAppletPayloadCodec.withDecoded(payload) { data, properties ->
                    assertArrayEquals(CANTABRIA_CHALLENGE.encodeToByteArray(), data)
                    assertEquals(CANTABRIA_EXTRA_PROPERTIES, properties)
                }
            }
        }
    }

    @Test
    fun exactTenerifeAutoScriptCallNormalizesTheDownloadedDocumentToSha512Cades() {
        val result = adapterFor(TENERIFE_PROFILE_ID).route(
            rawMessage = tenerifeMessage(),
            sourceOrigin = TENERIFE_ORIGIN,
            isMainFrame = true,
            navigationEpoch = 48,
        ) as MiniAppletBridgeRouteResult.Accepted

        result.request.normalized.use { request ->
            assertEquals(TENERIFE_PROTOCOL_ID, request.protocolId.value)
            assertEquals(TENERIFE_PROFILE_ID, request.context.profileId)
            assertEquals(1, request.context.profileVersion)
            assertEquals("sede.tenerife.es", request.context.origin.host)
            assertEquals(48, request.context.navigationEpoch)
            assertEquals(SigningAlgorithm.SHA512_WITH_RSA, request.algorithm)
            assertEquals(SigningFormat.CADES, request.format)
            request.withPayload { payload ->
                MiniAppletPayloadCodec.withDecoded(payload) { data, properties ->
                    assertArrayEquals(TENERIFE_DOCUMENT, data)
                    assertEquals(TENERIFE_EXTRA_PROPERTIES, properties)
                }
            }
        }
    }

    @Test
    fun tenerifeRejectsWrongProfileOriginTupleAndPropertiesWithoutBroadening() {
        assertTrue(
            adapterFor(TENERIFE_PROFILE_ID).route(
                tenerifeMessage(),
                Uri.parse("https://sede.tenerife.es.evil.example"),
                true,
            ) is MiniAppletBridgeRouteResult.Rejected,
        )
        assertTrue(
            adapterFor("junta-andalucia").route(
                tenerifeMessage(),
                TENERIFE_ORIGIN,
                true,
            ) is MiniAppletBridgeRouteResult.Rejected,
        )
        listOf(
            tenerifeMessage(algorithm = "SHA256withRSA"),
            tenerifeMessage(format = "XAdES Detached"),
            tenerifeMessage(extraProperties = "mode=implicit"),
            tenerifeMessage(extraProperties = JSONObject.NULL),
        ).forEach { message ->
            assertTrue(
                adapterFor(TENERIFE_PROFILE_ID).route(message, TENERIFE_ORIGIN, true) is
                    MiniAppletBridgeRouteResult.Rejected,
            )
        }
    }

    @Test
    fun exactDiputacionLleidaAutoScriptCallNormalizesTheLoginChallengeToSha256Cades() {
        val result = adapterFor(LLEIDA_PROFILE_ID).route(
            rawMessage = lleidaMessage(),
            sourceOrigin = LLEIDA_ORIGIN,
            isMainFrame = true,
            navigationEpoch = 49,
            currentPageUrl = LLEIDA_LOGIN_PAGE_URL,
        ) as MiniAppletBridgeRouteResult.Accepted

        result.request.normalized.use { request ->
            assertEquals(LLEIDA_PROTOCOL_ID, request.protocolId.value)
            assertEquals(LLEIDA_PROFILE_ID, request.context.profileId)
            assertEquals(1, request.context.profileVersion)
            assertEquals("seu.diputaciolleida.cat", request.context.origin.host)
            assertEquals(49, request.context.navigationEpoch)
            assertEquals(SigningAlgorithm.SHA256_WITH_RSA, request.algorithm)
            assertEquals(SigningFormat.CADES, request.format)
            request.withPayload { payload ->
                MiniAppletPayloadCodec.withDecoded(payload) { data, properties ->
                    assertArrayEquals(LLEIDA_CHALLENGE.encodeToByteArray(), data)
                    assertEquals(LLEIDA_EXTRA_PROPERTIES, properties)
                }
            }
        }
    }

    @Test
    fun diputacionLleidaRejectsWrongProfileOriginTupleAndPropertiesWithoutBroadening() {
        assertTrue(
            adapterFor(LLEIDA_PROFILE_ID).route(
                lleidaMessage(),
                Uri.parse("https://seu.diputaciolleida.cat.evil.example"),
                true,
            ) is MiniAppletBridgeRouteResult.Rejected,
        )
        assertTrue(
            adapterFor("junta-andalucia").route(
                lleidaMessage(),
                LLEIDA_ORIGIN,
                true,
            ) is MiniAppletBridgeRouteResult.Rejected,
        )
        assertTrue(
            adapterFor(LLEIDA_PROFILE_ID).route(
                lleidaMessage(),
                LLEIDA_ORIGIN,
                true,
                currentPageUrl = "https://seu.diputaciolleida.cat/portal/inicio.do",
            ) is MiniAppletBridgeRouteResult.Rejected,
        )
        listOf(
            lleidaMessage(algorithm = "SHA1withRSA"),
            lleidaMessage(algorithm = "SHA512withRSA"),
            lleidaMessage(format = "XAdES Detached"),
            lleidaMessage(extraProperties = "mode=implicit"),
            lleidaMessage(extraProperties = JSONObject.NULL),
            lleidaMessage(dataB64 = Base64.getEncoder().encodeToString(byteArrayOf(0x00, 0x01))),
        ).forEach { message ->
            assertTrue(
                adapterFor(LLEIDA_PROFILE_ID).route(
                    message,
                    LLEIDA_ORIGIN,
                    true,
                    currentPageUrl = LLEIDA_LOGIN_PAGE_URL,
                ) is MiniAppletBridgeRouteResult.Rejected,
            )
        }
    }

    @Test
    fun cantabriaRecRejectsWrongProfileOriginChallengeTupleAndPropertiesWithoutGenericBroadening() {
        assertEquals(
            SigningErrorCode.ORIGIN_NOT_ALLOWED,
            cantabriaRejected(activeProfile = "junta-andalucia"),
        )
        assertEquals(
            SigningErrorCode.ORIGIN_NOT_ALLOWED,
            cantabriaRejected(origin = Uri.parse("https://rec.cantabria.es.evil.example")),
        )
        assertEquals(
            SigningErrorCode.INVALID_REQUEST,
            cantabriaRejected(
                cantabriaMessage(
                    dataB64 = Base64.getEncoder().encodeToString(
                        CANTABRIA_CHALLENGE.uppercase().encodeToByteArray(),
                    ),
                ),
            ),
        )
        assertEquals(
            SigningErrorCode.INVALID_REQUEST,
            cantabriaRejected(
                cantabriaMessage(
                    dataB64 = Base64.getEncoder().encodeToString(
                        CANTABRIA_CHALLENGE.dropLast(1).encodeToByteArray(),
                    ),
                ),
            ),
        )
        assertEquals(
            SigningErrorCode.INVALID_REQUEST,
            cantabriaRejected(
                cantabriaMessage(
                    dataB64 = Base64.getEncoder().encodeToString(
                        (CANTABRIA_CHALLENGE + "a").encodeToByteArray(),
                    ),
                ),
            ),
        )
        assertEquals(
            SigningErrorCode.INVALID_REQUEST,
            cantabriaRejected(cantabriaMessage(algorithm = "SHA256withRSA")),
        )
        assertEquals(
            SigningErrorCode.INVALID_REQUEST,
            cantabriaRejected(cantabriaMessage(format = "XAdES Detached")),
        )
        listOf(
            "filters=\nmode=explicit",
            "filters=\nmode=implicit\nextra=value",
            JSONObject.NULL,
        ).forEach { properties ->
            assertEquals(
                SigningErrorCode.INVALID_REQUEST,
                cantabriaRejected(cantabriaMessage(extraProperties = properties)),
            )
        }
        assertEquals(
            SigningErrorCode.INVALID_REQUEST,
            (adapterFor("junta-andalucia").route(
                rawMessage = cantabriaMessage(),
                sourceOrigin = TRUSTED_ORIGIN,
                isMainFrame = true,
            ) as MiniAppletBridgeRouteResult.Rejected).code,
        )
    }

    @Test
    fun ugrBridgeRejectsWrongOriginProfileTuplePropertiesAndPayloadVariants() {
        val valid = ugrMessage()

        assertRejected(valid, Uri.parse("https://sede.ugr.es.evil.example"), true, adapterFor(UGR_PROFILE_ID))
        assertRejected(valid, UGR_ORIGIN, false, adapterFor(UGR_PROFILE_ID))
        assertRejected(valid, UGR_ORIGIN, true, adapterFor("junta-andalucia"))
        assertRejected(
            ugrMessage(algorithm = "SHA256withRSA"),
            UGR_ORIGIN,
            true,
            adapterFor(UGR_PROFILE_ID),
        )
        assertRejected(
            ugrMessage(format = "XAdES Detached"),
            UGR_ORIGIN,
            true,
            adapterFor(UGR_PROFILE_ID),
        )
        listOf("filter=", " ", "mode=explicit").forEach { properties ->
            assertRejected(
                ugrMessage(extraProperties = properties),
                UGR_ORIGIN,
                true,
                adapterFor(UGR_PROFILE_ID),
            )
        }
        assertRejected(
            ugrMessage(extraProperties = JSONObject.NULL),
            UGR_ORIGIN,
            true,
            adapterFor(UGR_PROFILE_ID),
        )
        assertRejected(
            ugrMessage(dataB64 = Base64.getEncoder().encodeToString(ByteArray(22) { 7 })),
            UGR_ORIGIN,
            true,
            adapterFor(UGR_PROFILE_ID),
        )
        assertRejected(
            ugrMessage(dataB64 = "Universidad de Granada"),
            UGR_ORIGIN,
            true,
            adapterFor(UGR_PROFILE_ID),
        )
    }

    @Test
    fun exactSevillaAtseAutoScriptCallNormalizesTheDynamicChallengeToXades() {
        val result = sevillaAdapter().route(
            rawMessage = sevillaMessage(),
            sourceOrigin = SEVILLA_ORIGIN,
            isMainFrame = true,
            navigationEpoch = 48,
            currentPageUrl = SEVILLA_START_URL,
        ) as MiniAppletBridgeRouteResult.Accepted

        result.request.normalized.use { request ->
            assertEquals(SEVILLA_PROFILE_ID, request.context.profileId)
            assertEquals(1, request.context.profileVersion)
            assertEquals("www.sevilla.org", request.context.origin.host)
            assertEquals(48, request.context.navigationEpoch)
            assertEquals(SEVILLA_PROTOCOL_ID, request.protocolId.value)
            assertEquals(SigningAlgorithm.SHA1_WITH_RSA, request.algorithm)
            assertEquals(SigningFormat.XADES, request.format)
            request.withPayload { payload ->
                MiniAppletPayloadCodec.withDecoded(payload) { data, properties ->
                    assertArrayEquals(SEVILLA_CHALLENGE.encodeToByteArray(), data)
                    assertEquals("", properties)
                }
            }
        }
    }

    @Test
    fun sevillaAtseBridgeKeepsWrongPageOriginChallengeTupleAndPropertiesFailClosed() {
        val adapter = sevillaAdapter()
        fun rejected(
            rawMessage: String = sevillaMessage(),
            origin: Uri = SEVILLA_ORIGIN,
            pageUrl: String? = SEVILLA_START_URL,
        ) = adapter.route(
            rawMessage = rawMessage,
            sourceOrigin = origin,
            isMainFrame = true,
            currentPageUrl = pageUrl,
        ) is MiniAppletBridgeRouteResult.Rejected

        assertTrue(rejected(origin = Uri.parse("https://www.sevilla.org.evil.example")))
        assertTrue(rejected(pageUrl = null))
        assertTrue(rejected(pageUrl = "https://www.sevilla.org/ovweb/"))
        assertTrue(rejected(pageUrl = "$SEVILLA_START_URL&unexpected=1"))
        assertTrue(rejected(rawMessage = sevillaMessage(algorithm = "SHA256withRSA")))
        assertTrue(rejected(rawMessage = sevillaMessage(format = "XAdES Detached")))
        assertTrue(rejected(rawMessage = sevillaMessage(extraProperties = "")))
        assertTrue(
            rejected(
                rawMessage = sevillaMessage(
                    dataB64 = Base64.getEncoder().encodeToString(
                        SEVILLA_CHALLENGE.dropLast(1).encodeToByteArray(),
                    ),
                ),
            ),
        )
        assertTrue(
            rejected(
                rawMessage = sevillaMessage(
                    dataB64 = Base64.getEncoder().encodeToString(
                        (SEVILLA_CHALLENGE.dropLast(1) + "!").encodeToByteArray(),
                    ),
                ),
            ),
        )
    }

    @Test
    fun exactCdtiAutoScriptCallNormalizesTheDynamicChallengeToSha512XadesEnveloping() {
        val result = cdtiAdapter().route(
            rawMessage = cdtiMessage(),
            sourceOrigin = CDTI_ORIGIN,
            isMainFrame = true,
            navigationEpoch = 51,
            currentPageUrl = CDTI_START_URL,
        ) as MiniAppletBridgeRouteResult.Accepted

        result.request.normalized.use { request ->
            assertEquals(CDTI_PROFILE_ID, request.context.profileId)
            assertEquals(1, request.context.profileVersion)
            assertEquals("sede.cdti.gob.es", request.context.origin.host)
            assertEquals(51, request.context.navigationEpoch)
            assertEquals(CDTI_PROTOCOL_ID, request.protocolId.value)
            assertEquals(SigningAlgorithm.SHA512_WITH_RSA, request.algorithm)
            assertEquals(SigningFormat.XADES, request.format)
            request.withPayload { payload ->
                MiniAppletPayloadCodec.withDecoded(payload) { data, properties ->
                    assertArrayEquals(
                        Base64.getDecoder().decode(CDTI_CHALLENGE + "="),
                        data,
                    )
                    assertEquals(CDTI_EXTRA_PROPERTIES, properties)
                }
            }
        }
    }

    @Test
    fun cdtiBridgeKeepsWrongPageOriginChallengeTupleAndPropertiesFailClosed() {
        val adapter = cdtiAdapter()
        fun rejected(
            rawMessage: String = cdtiMessage(),
            origin: Uri = CDTI_ORIGIN,
            pageUrl: String? = CDTI_START_URL,
        ) = adapter.route(
            rawMessage = rawMessage,
            sourceOrigin = origin,
            isMainFrame = true,
            currentPageUrl = pageUrl,
        ) is MiniAppletBridgeRouteResult.Rejected

        assertTrue(rejected(origin = Uri.parse("https://sede.cdti.gob.es.evil.example")))
        assertTrue(rejected(pageUrl = null))
        assertTrue(rejected(pageUrl = "https://sede.cdti.gob.es/AreaPrivada/Expedientes/"))
        assertTrue(rejected(pageUrl = "$CDTI_START_URL?unexpected=1"))
        assertTrue(rejected(rawMessage = cdtiMessage(algorithm = "SHA256withRSA")))
        assertTrue(rejected(rawMessage = cdtiMessage(format = "XAdES")))
        assertTrue(rejected(rawMessage = cdtiMessage(format = "XAdES Detached")))
        assertTrue(rejected(rawMessage = cdtiMessage(extraProperties = "filters=expired")))
        assertTrue(
            rejected(
                rawMessage = cdtiMessage(
                    dataB64 = (CDTI_CHALLENGE.dropLast(1) + "A") + "=",
                ),
            ),
        )
        assertTrue(
            rejected(
                rawMessage = cdtiMessage(
                    dataB64 = ("CertExp" + "A".repeat(32) + "a".repeat(24)) + "=",
                ),
            ),
        )
    }

    @Test
    fun exactPoliciaAutoScriptCallNormalizesToOwnedSha1XadesRequest() {
        val result = policiaAdapter().route(
            rawMessage = policiaMessage(),
            sourceOrigin = POLICIA_ORIGIN,
            isMainFrame = true,
            navigationEpoch = 52,
            currentPageUrl = POLICIA_PROCEDURE_PAGE,
        ) as MiniAppletBridgeRouteResult.Accepted

        result.request.normalized.use { request ->
            assertEquals(POLICIA_PROFILE_ID, request.context.profileId)
            assertEquals(1, request.context.profileVersion)
            assertEquals("sede.policia.gob.es", request.context.origin.host)
            assertEquals(52, request.context.navigationEpoch)
            assertEquals(POLICIA_PROTOCOL_ID, request.protocolId.value)
            assertEquals(SigningAlgorithm.SHA1_WITH_RSA, request.algorithm)
            assertEquals(SigningFormat.XADES, request.format)
            request.withPayload { payload ->
                MiniAppletPayloadCodec.withDecoded(payload) { data, properties ->
                    assertArrayEquals(POLICIA_DOCUMENT, data)
                    val parsed = java.util.Properties().apply { load(properties.reader()) }
                    assertEquals("XAdES Detached", parsed.getProperty("format"))
                    assertEquals("dnie:;nonexpired:", parsed.getProperty("filters.1"))
                    assertEquals("keyusage.nonrepudiation:true;nonexpired:", parsed.getProperty("filters.2"))
                }
            }
        }
    }

    @Test
    fun policiaBridgeKeepsWrongPageOriginAlgorithmFormatAndExtraPropertiesFailClosed() {
        val adapter = policiaAdapter()
        fun rejected(
            rawMessage: String = policiaMessage(),
            origin: Uri = POLICIA_ORIGIN,
            pageUrl: String? = POLICIA_PROCEDURE_PAGE,
        ) = adapter.route(
            rawMessage = rawMessage,
            sourceOrigin = origin,
            isMainFrame = true,
            currentPageUrl = pageUrl,
        ) is MiniAppletBridgeRouteResult.Rejected

        // Origin rejection
        assertTrue(rejected(origin = Uri.parse("https://sede.policia.gob.es.evil.example")))
        // Procedure page pin rejection: root, other page, query params, fragment, null
        assertTrue(rejected(pageUrl = POLICIA_START_URL))
        assertTrue(rejected(pageUrl = "https://sede.policia.gob.es/otra.xhtml"))
        assertTrue(rejected(pageUrl = "$POLICIA_PROCEDURE_PAGE?param=val"))
        assertTrue(rejected(pageUrl = "$POLICIA_PROCEDURE_PAGE#step"))
        assertTrue(rejected(pageUrl = null))
        // Algorithm rejection
        assertTrue(rejected(rawMessage = policiaMessage(algorithm = "SHA256withRSA")))
        // Format rejection: XAdES Detached as API format argument is rejected, only XAdES accepted
        assertTrue(rejected(rawMessage = policiaMessage(format = "XAdES Detached")))
        assertTrue(rejected(rawMessage = policiaMessage(format = "CAdES")))
        // Extra properties rejection
        assertTrue(rejected(rawMessage = policiaMessage(extraProperties = "mode=invalid")))
        assertTrue(rejected(rawMessage = policiaMessage(extraProperties = JSONObject.NULL)))
        assertTrue(rejected(rawMessage = policiaMessage(extraProperties = "filters=keyusage.nonrepudiation:true;nonexpired:\nformat=XAdES Detached")))
        assertTrue(rejected(rawMessage = policiaMessage(extraProperties = "format=XAdES Detached\nfilters.1=dnie:;nonexpired:")))
        assertTrue(rejected(rawMessage = policiaMessage(extraProperties = "format=XAdES Detached\nfilters.2=keyusage.nonrepudiation:true;nonexpired:")))
        val reorderedProperties = policiaAdapter().route(
            rawMessage = policiaMessage(
                extraProperties = "filters.1=dnie:;nonexpired:\nfilters.2=keyusage.nonrepudiation:true;nonexpired:\nformat=XAdES Detached",
            ),
            sourceOrigin = POLICIA_ORIGIN,
            isMainFrame = true,
            navigationEpoch = 52,
            currentPageUrl = POLICIA_PROCEDURE_PAGE,
        ) as MiniAppletBridgeRouteResult.Accepted
        reorderedProperties.request.normalized.use { request ->
            request.withPayload { payload ->
                MiniAppletPayloadCodec.withDecoded(payload) { _, properties ->
                    assertEquals(POLICIA_EXTRA_PROPERTIES_STR, properties)
                }
            }
        }
        assertTrue(rejected(rawMessage = policiaMessage(extraProperties = "format=XAdES Detached\nfilters.1=dnie:;nonexpired:\nfilters.2=keyusage.nonrepudiation:true;nonexpired:\nextra=val")))
    }

    @Test
    fun exactJccmProbeNormalizesOnlyTheFiveDecodedAsciiBytes() {
        val result = adapterFor(JCCM_PROFILE_ID).route(
            rawMessage = jccmMessage(),
            sourceOrigin = JCCM_ORIGIN,
            isMainFrame = true,
            navigationEpoch = 45,
            currentPageUrl = JCCM_START_URL,
        ) as MiniAppletBridgeRouteResult.Accepted

        result.request.normalized.use { request ->
            assertEquals(JCCM_PROFILE_ID, request.context.profileId)
            assertEquals(1, request.context.profileVersion)
            assertEquals("ventanillaelectronica.jccm.es", request.context.origin.host)
            assertEquals(45, request.context.navigationEpoch)
            assertEquals(JCCM_PROTOCOL_ID, request.protocolId.value)
            assertEquals(SigningAlgorithm.SHA1_WITH_RSA, request.algorithm)
            assertEquals(SigningFormat.CADES, request.format)
            request.withPayload { payload ->
                MiniAppletPayloadCodec.withDecoded(payload) { data, properties ->
                    assertArrayEquals(JCCM_DATA, data)
                    assertEquals("", properties)
                }
            }
        }
    }

    @Test
    fun jccmProbeAcceptsOnlyTheExactRuntimePageUrl() {
        val accepted = adapterFor(JCCM_PROFILE_ID).route(
            rawMessage = jccmMessage(),
            sourceOrigin = JCCM_ORIGIN,
            isMainFrame = true,
            currentPageUrl = JCCM_START_URL,
        ) as MiniAppletBridgeRouteResult.Accepted
        accepted.request.normalized.close()

        listOf<String?>(
            null,
            "https://ventanillaelectronica.jccm.es/other",
            "$JCCM_START_URL?probe=1",
            "$JCCM_START_URL#fragment",
        ).forEach { currentPageUrl ->
            val rejected = adapterFor(JCCM_PROFILE_ID).route(
                rawMessage = jccmMessage(),
                sourceOrigin = JCCM_ORIGIN,
                isMainFrame = true,
                currentPageUrl = currentPageUrl,
            )
            assertTrue(rejected is MiniAppletBridgeRouteResult.Rejected)
        }
    }

    @Test
    fun jccmProbeRejectsEveryWrongContractDimensionWithoutGenericCadesBroadening() {
        val valid = jccmMessage()

        assertJccmRejected(
            rawMessage = valid,
            origin = Uri.parse("https://ventanillaelectronica.jccm.es.evil.example"),
        )
        assertJccmRejected(rawMessage = valid, isMainFrame = false)
        assertJccmRejected(rawMessage = valid, activeProfileId = "junta-andalucia")
        assertJccmRejected(jccmMessage(algorithm = "SHA256withRSA"))
        assertJccmRejected(jccmMessage(format = "XAdES Detached"))
        assertJccmRejected(
            jccmMessage(dataB64 = Base64.getEncoder().encodeToString("ABCDF".encodeToByteArray())),
        )
        assertJccmRejected(jccmMessage(extraProperties = "unexpected=value"))
        assertJccmRejected(jccmMessage(extraProperties = " "))
        val nullProperties = adapterFor(JCCM_PROFILE_ID).route(
            rawMessage = jccmMessage(extraProperties = JSONObject.NULL),
            sourceOrigin = JCCM_ORIGIN,
            isMainFrame = true,
            currentPageUrl = JCCM_START_URL,
        ) as MiniAppletBridgeRouteResult.Accepted
        nullProperties.request.normalized.close()
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

    private fun sevillaAdapter(): ProfileMiniAppletBridgeAdapter {
        val profile = SiteProfile(
            profileId = ProfileId(SEVILLA_PROFILE_ID),
            profileVersion = 1,
            displayName = "Agencia Tributaria de Sevilla — Acceso con certificado",
            compatibilityStatus = CompatibilityStatus.VERIFIED_CONTRACT,
            activation = ProfileActivation.QA_ONLY,
            startUrl = URI(SEVILLA_START_URL),
            initiatorOrigins = setOf(ExactOrigin.parse(SEVILLA_ORIGIN.toString())),
            redirectOrigins = emptySet(),
            trustedBrowseOrigins = emptySet(),
            endpoints = emptyMap(),
            operationPolicies = mapOf(
                ProtocolOperation.SIGN to OperationPolicy(
                    operation = ProtocolOperation.SIGN,
                    safeDescription = SEVILLA_SAFE_DESCRIPTION,
                    inputAdapterId = ProtocolInputAdapterId("miniapplet-autoscript-v1"),
                    callbackContractId = CallbackContractId("autoscript-sign-callback-v1"),
                    capabilities = setOf(Capability.SIGN, Capability.LEGACY_SHA1),
                    endpointId = null,
                    algorithms = setOf(SignatureAlgorithm.SHA1_WITH_RSA),
                    format = SignatureFormat.XADES,
                    packaging = SignaturePackaging.ATTACHED,
                    mode = null,
                    fixedExtraProperties = emptyMap(),
                    allowedExtraProperties = emptySet(),
                ),
            ),
            capabilities = setOf(Capability.SIGN, Capability.LEGACY_SHA1),
            clientAuthPolicy = null,
            certificateRules = CertificateFilterRules(setOf("RSA"), true),
            evidence = emptyList(),
        )
        return ProfileMiniAppletBridgeAdapter(
            clock = Clock.fixed(Instant.parse("2030-01-01T00:00:00Z"), ZoneOffset.UTC),
            profileRegistry = SiteProfileRegistry(
                SiteProfileCatalog(schemaVersion = 1, catalogVersion = 1, profiles = listOf(profile)),
                BuildTrustPolicy.QA,
            ),
            adapterRegistry = ProtocolAdapterRegistry(
                listOf(
                    ProtocolAdapterBinding(
                        profileId = profile.profileId,
                        operation = ProtocolOperation.SIGN,
                        inputAdapterId = ProtocolInputAdapterId("miniapplet-autoscript-v1"),
                        callbackContractId = CallbackContractId("autoscript-sign-callback-v1"),
                        signingProtocolId = SigningProtocolId(SEVILLA_PROTOCOL_ID),
                    ),
                ),
            ),
            activeProfileId = { profile.profileId },
        )
    }

    private fun cdtiAdapter(): ProfileMiniAppletBridgeAdapter {
        val profile = SiteProfile(
            profileId = ProfileId(CDTI_PROFILE_ID),
            profileVersion = 1,
            displayName = "CDTI — Validación de certificado digital",
            compatibilityStatus = CompatibilityStatus.VERIFIED_CONTRACT,
            activation = ProfileActivation.QA_ONLY,
            startUrl = URI(CDTI_START_URL),
            initiatorOrigins = setOf(ExactOrigin.parse(CDTI_ORIGIN.toString())),
            redirectOrigins = emptySet(),
            trustedBrowseOrigins = emptySet(),
            endpoints = emptyMap(),
            operationPolicies = mapOf(
                ProtocolOperation.SIGN to OperationPolicy(
                    operation = ProtocolOperation.SIGN,
                    safeDescription = CDTI_SAFE_DESCRIPTION,
                    inputAdapterId = ProtocolInputAdapterId("miniapplet-autoscript-v1"),
                    callbackContractId = CallbackContractId("autoscript-sign-callback-v1"),
                    capabilities = setOf(Capability.SIGN),
                    endpointId = null,
                    algorithms = setOf(SignatureAlgorithm.SHA512_WITH_RSA),
                    format = SignatureFormat.XADES,
                    packaging = SignaturePackaging.ATTACHED,
                    mode = null,
                    fixedExtraProperties = linkedMapOf("filters" to "nonexpired"),
                    allowedExtraProperties = emptySet(),
                ),
            ),
            capabilities = setOf(Capability.SIGN),
            clientAuthPolicy = null,
            certificateRules = CertificateFilterRules(setOf("RSA"), true),
            evidence = emptyList(),
        )
        return ProfileMiniAppletBridgeAdapter(
            clock = Clock.fixed(Instant.parse("2030-01-01T00:00:00Z"), ZoneOffset.UTC),
            profileRegistry = SiteProfileRegistry(
                SiteProfileCatalog(schemaVersion = 1, catalogVersion = 1, profiles = listOf(profile)),
                BuildTrustPolicy.QA,
            ),
            adapterRegistry = ProtocolAdapterRegistry(
                listOf(
                    ProtocolAdapterBinding(
                        profileId = profile.profileId,
                        operation = ProtocolOperation.SIGN,
                        inputAdapterId = ProtocolInputAdapterId("miniapplet-autoscript-v1"),
                        callbackContractId = CallbackContractId("autoscript-sign-callback-v1"),
                        signingProtocolId = SigningProtocolId(CDTI_PROTOCOL_ID),
                    ),
                ),
            ),
            activeProfileId = { profile.profileId },
        )
    }

    private fun policiaAdapter(): ProfileMiniAppletBridgeAdapter {
        val profile = SiteProfile(
            profileId = ProfileId(POLICIA_PROFILE_ID),
            profileVersion = 1,
            displayName = "Policía Nacional — Solicitud genérica",
            compatibilityStatus = CompatibilityStatus.VERIFIED_CONTRACT,
            activation = ProfileActivation.QA_ONLY,
            startUrl = URI(POLICIA_START_URL),
            initiatorOrigins = setOf(ExactOrigin.parse(POLICIA_ORIGIN.toString())),
            redirectOrigins = emptySet(),
            trustedBrowseOrigins = emptySet(),
            endpoints = emptyMap(),
            operationPolicies = mapOf(
                ProtocolOperation.SIGN to OperationPolicy(
                    operation = ProtocolOperation.SIGN,
                    safeDescription = POLICIA_SAFE_DESCRIPTION,
                    inputAdapterId = ProtocolInputAdapterId("miniapplet-autoscript-v1"),
                    callbackContractId = CallbackContractId("autoscript-sign-callback-v1"),
                    capabilities = setOf(Capability.SIGN, Capability.LEGACY_SHA1),
                    endpointId = null,
                    algorithms = setOf(SignatureAlgorithm.SHA1_WITH_RSA),
                    format = SignatureFormat.XADES,
                    packaging = SignaturePackaging.DETACHED,
                    mode = null,
                    fixedExtraProperties = linkedMapOf(
                        "format" to "XAdES Detached",
                        "filters.1" to "dnie:;nonexpired:",
                        "filters.2" to "keyusage.nonrepudiation:true;nonexpired:",
                    ),
                    allowedExtraProperties = emptySet(),
                ),
            ),
            capabilities = setOf(Capability.SIGN, Capability.LEGACY_SHA1),
            clientAuthPolicy = null,
            certificateRules = CertificateFilterRules(setOf("RSA"), false),
            evidence = emptyList(),
        )
        return ProfileMiniAppletBridgeAdapter(
            clock = Clock.fixed(Instant.parse("2030-01-01T00:00:00Z"), ZoneOffset.UTC),
            profileRegistry = SiteProfileRegistry(
                SiteProfileCatalog(schemaVersion = 1, catalogVersion = 1, profiles = listOf(profile)),
                BuildTrustPolicy.QA,
            ),
            adapterRegistry = ProtocolAdapterRegistry(
                listOf(
                    ProtocolAdapterBinding(
                        profileId = profile.profileId,
                        operation = ProtocolOperation.SIGN,
                        inputAdapterId = ProtocolInputAdapterId("miniapplet-autoscript-v1"),
                        callbackContractId = CallbackContractId("autoscript-sign-callback-v1"),
                        signingProtocolId = SigningProtocolId(POLICIA_PROTOCOL_ID),
                    ),
                ),
            ),
            activeProfileId = { profile.profileId },
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

    private fun assertJccmRejected(
        rawMessage: String,
        origin: Uri = JCCM_ORIGIN,
        isMainFrame: Boolean = true,
        activeProfileId: String = JCCM_PROFILE_ID,
    ) {
        assertTrue(
            adapterFor(activeProfileId).route(
                rawMessage = rawMessage,
                sourceOrigin = origin,
                isMainFrame = isMainFrame,
                currentPageUrl = JCCM_START_URL,
            ) is MiniAppletBridgeRouteResult.Rejected,
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

    private fun sevillaMessage(
        dataB64: String = Base64.getEncoder().encodeToString(SEVILLA_CHALLENGE.encodeToByteArray()),
        algorithm: String = "SHA1withRSA",
        format: String = "XAdES",
        extraProperties: Any = JSONObject.NULL,
    ): String = JSONObject()
        .put("type", "MINIAPPLET_SIGN")
        .put("documentId", DOCUMENT_ID)
        .put("requestId", REQUEST_ID)
        .put("dataB64", dataB64)
        .put("algorithm", algorithm)
        .put("format", format)
        .put("extraProperties", extraProperties)
        .toString()

    private fun cdtiMessage(
        dataB64: String = CDTI_CHALLENGE + "=",
        algorithm: String = "SHA512withRSA",
        format: String = "XAdES Enveloping",
        extraProperties: Any = CDTI_EXTRA_PROPERTIES,
    ): String = JSONObject()
        .put("type", "MINIAPPLET_SIGN")
        .put("documentId", DOCUMENT_ID)
        .put("requestId", REQUEST_ID)
        .put("dataB64", dataB64)
        .put("algorithm", algorithm)
        .put("format", format)
        .put("extraProperties", extraProperties)
        .toString()

    private fun policiaMessage(
        dataB64: String = Base64.getEncoder().encodeToString(POLICIA_DOCUMENT),
        algorithm: String = "SHA1withRSA",
        format: String = "XAdES",
        extraProperties: Any = POLICIA_EXTRA_PROPERTIES_STR,
    ): String = JSONObject()
        .put("type", "MINIAPPLET_SIGN")
        .put("documentId", DOCUMENT_ID)
        .put("requestId", REQUEST_ID)
        .put("dataB64", dataB64)
        .put("algorithm", algorithm)
        .put("format", format)
        .put("extraProperties", extraProperties)
        .toString()

    private fun jccmMessage(
        dataB64: String = Base64.getEncoder().encodeToString(JCCM_DATA),
        algorithm: String = "SHA1withRSA",
        format: String = "CAdES",
        extraProperties: Any = "",
    ): String = JSONObject()
        .put("type", "MINIAPPLET_SIGN")
        .put("documentId", DOCUMENT_ID)
        .put("requestId", REQUEST_ID)
        .put("dataB64", dataB64)
        .put("algorithm", algorithm)
        .put("format", format)
        .put("extraProperties", extraProperties)
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

    private fun ugrMessage(
        dataB64: String = Base64.getEncoder().encodeToString(UGR_DATA),
        algorithm: String = "SHA1withRSA",
        format: String = "CAdES",
        extraProperties: Any = "",
    ): String = JSONObject()
        .put("type", "MINIAPPLET_SIGN")
        .put("documentId", DOCUMENT_ID)
        .put("requestId", REQUEST_ID)
        .put("dataB64", dataB64)
        .put("algorithm", algorithm)
        .put("format", format)
        .put("extraProperties", extraProperties)
        .toString()

    private fun tenerifeMessage(
        dataB64: String = Base64.getEncoder().encodeToString(TENERIFE_DOCUMENT),
        algorithm: String = "SHA512withRSA",
        format: String = "CAdES",
        extraProperties: Any = TENERIFE_EXTRA_PROPERTIES,
    ): String = JSONObject()
        .put("type", "MINIAPPLET_SIGN")
        .put("documentId", DOCUMENT_ID)
        .put("requestId", REQUEST_ID)
        .put("dataB64", dataB64)
        .put("algorithm", algorithm)
        .put("format", format)
        .put("extraProperties", extraProperties)
        .toString()

    private fun cantabriaMessage(
        dataB64: String = Base64.getEncoder().encodeToString(CANTABRIA_CHALLENGE.encodeToByteArray()),
        algorithm: String = "SHA512withRSA",
        format: String = "CAdES",
        extraProperties: Any = CANTABRIA_EXTRA_PROPERTIES,
    ): String = JSONObject()
        .put("type", "MINIAPPLET_SIGN")
        .put("documentId", DOCUMENT_ID)
        .put("requestId", REQUEST_ID)
        .put("dataB64", dataB64)
        .put("algorithm", algorithm)
        .put("format", format)
        .put("extraProperties", extraProperties)
        .toString()

    private fun cantabriaRejected(
        rawMessage: String = cantabriaMessage(),
        origin: Uri = CANTABRIA_ORIGIN,
        activeProfile: String = CANTABRIA_PROFILE_ID,
    ): SigningErrorCode = (adapterFor(activeProfile).route(
        rawMessage = rawMessage,
        sourceOrigin = origin,
        isMainFrame = true,
    ) as MiniAppletBridgeRouteResult.Rejected).code

    private fun lleidaMessage(
        dataB64: String = Base64.getEncoder().encodeToString(LLEIDA_CHALLENGE.encodeToByteArray()),
        algorithm: String = "SHA256withRSA",
        format: String = "CAdES",
        extraProperties: Any = LLEIDA_EXTRA_PROPERTIES,
    ): String = JSONObject()
        .put("type", "MINIAPPLET_SIGN")
        .put("documentId", DOCUMENT_ID)
        .put("requestId", REQUEST_ID)
        .put("dataB64", dataB64)
        .put("algorithm", algorithm)
        .put("format", format)
        .put("extraProperties", extraProperties)
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
        val UGR_ORIGIN: Uri = Uri.parse("https://sede.ugr.es")
        val JCCM_ORIGIN: Uri = Uri.parse("https://ventanillaelectronica.jccm.es")
        val SEVILLA_ORIGIN: Uri = Uri.parse("https://www.sevilla.org")
        const val SEVILLA_PROFILE_ID = "sevilla-atse-certificate-login"
        const val SEVILLA_PROTOCOL_ID = "sevilla-atse-xades-enveloping-v1"
        const val SEVILLA_START_URL =
            "https://www.sevilla.org/ovweb/ov-web-certificado/index.xhtml?modo=Contribuyente"
        const val SEVILLA_SAFE_DESCRIPTION =
            "Acceso con certificado a la Agencia Tributaria de Sevilla"
        const val SEVILLA_CHALLENGE = "0123456789abcdef0123456789abcdefABCDEFGH"
        val CDTI_ORIGIN: Uri = Uri.parse("https://sede.cdti.gob.es")
        const val CDTI_PROFILE_ID = "cdti-certificate-validation"
        const val CDTI_PROTOCOL_ID = "cdti-xades-enveloping-v1"
        const val CDTI_START_URL =
            "https://sede.cdti.gob.es/AreaPrivada/Expedientes/Common/Certificados/ValidarCertificado.aspx"
        const val CDTI_SAFE_DESCRIPTION = "Validación de certificado digital en CDTI"
        const val CDTI_CHALLENGE =
            "CertExp94e51ba8192c41ccbd1693a238fcd217aranjiffmrytnu55nzy5az45"
        const val CDTI_EXTRA_PROPERTIES = "filters=nonexpired"
        const val UGR_PROFILE_ID = "ugr-certificado-login"
        const val JCCM_PROFILE_ID = "jccm-certificate-login-probe"
        const val JCCM_START_URL =
            "https://ventanillaelectronica.jccm.es/administracion_electronica/" +
                "formularios/identificacion.phtml"
        const val JCCM_PROTOCOL_ID = "jccm-certificate-login-probe-local-cades-v1"
        const val UGR_PROTOCOL_ID = "ugr-certificado-login-local-cades-v1"
        val UGR_DATA = "Universidad de Granada".encodeToByteArray()
        val TENERIFE_ORIGIN: Uri = Uri.parse("https://sede.tenerife.es")
        const val TENERIFE_PROFILE_ID = "tenerife-sede-electronica"
        const val TENERIFE_PROTOCOL_ID = "tenerife-sede-local-cades-v1"
        const val TENERIFE_EXTRA_PROPERTIES = "mode=explicit"
        val TENERIFE_DOCUMENT = "synthetic Tenerife application document".encodeToByteArray()
        val CANTABRIA_ORIGIN: Uri = Uri.parse("https://rec.cantabria.es")
        const val CANTABRIA_PROFILE_ID = "cantabria-rec-cert-login"
        const val CANTABRIA_PROTOCOL_ID = "cantabria-rec-cert-login-cades-v1"
        const val CANTABRIA_CHALLENGE = "0123456789abcdef0123456789abcdef01234567"
        const val CANTABRIA_EXTRA_PROPERTIES = "filters=\nmode=implicit"
        val LLEIDA_ORIGIN: Uri = Uri.parse("https://seu.diputaciolleida.cat")
        const val LLEIDA_PROFILE_ID = "diputacion-lleida-sede"
        const val LLEIDA_PROTOCOL_ID = "diputacion-lleida-login-cades-v1"
        const val LLEIDA_CHALLENGE = "0123456789abcdef0123456789abcdef0123456789abcdef0123456789abcdef"
        const val LLEIDA_EXTRA_PROPERTIES =
            "policy=FirmaAGE\nheadless=true\nfilters=nonexpired:true;authCert:true"
        const val LLEIDA_LOGIN_PAGE_URL =
            "https://seu.diputaciolleida.cat/portal/entidades.do?ent_id=1&idioma=2"
        val JCCM_DATA = "ABCDE".encodeToByteArray()
        const val ARAGON_PROPERTIES = "mode=explicit\nfilter=nonexpired"
        const val OFVIRTUAL_PROPERTIES =
            "filters=keyusage.digitalsignature:true;nonexpired:\n" +
                "serverUrl=https://ws024.juntadeandalucia.es/" +
                "afirma-validator-miniapplet-1_5/sign/TriPhaseSignatureService"
        val DATA = "synthetic-miniapplet-data".encodeToByteArray()
        val SIGNATURE = byteArrayOf(1, 2, 3, 4)
        val CERTIFICATE = byteArrayOf(5, 6, 7, 8)
        val POLICIA_ORIGIN: Uri = Uri.parse("https://sede.policia.gob.es")
        const val POLICIA_PROFILE_ID = "policia-solicitud-generica"
        const val POLICIA_PROTOCOL_ID = "policia-xades-detached-v1"
        const val POLICIA_START_URL = "https://sede.policia.gob.es/"
        const val POLICIA_PROCEDURE_PAGE =
            "https://sede.policia.gob.es/portalCiudadano/_es/solicitudGenerica.xhtml"
        const val POLICIA_SAFE_DESCRIPTION = "Firma de solicitud en la Sede de la Policía Nacional"
        const val POLICIA_EXTRA_PROPERTIES_STR =
            "format=XAdES Detached\nfilters.1=dnie:;nonexpired:\nfilters.2=keyusage.nonrepudiation:true;nonexpired:"
        val POLICIA_DOCUMENT =
            "<?xml version=\"1.0\" encoding=\"UTF-8\"?><solicitud><campo>policia-doc</campo></solicitud>"
                .encodeToByteArray()
    }
}

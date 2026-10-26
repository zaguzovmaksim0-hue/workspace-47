package dev.junta.firmamobile.signing

import dev.junta.firmamobile.browser.NavigationId
import dev.junta.firmamobile.network.TrustedOrigin
import java.nio.ByteBuffer
import java.security.MessageDigest
import java.time.Clock
import java.time.Instant
import java.time.ZoneOffset
import java.util.Base64
import java.util.UUID
import kotlinx.coroutines.runBlocking
import org.bouncycastle.asn1.ASN1OctetString
import org.bouncycastle.asn1.cms.CMSAttributes
import org.bouncycastle.asn1.cms.Time
import org.bouncycastle.asn1.ess.SigningCertificateV2
import org.bouncycastle.asn1.pkcs.PKCSObjectIdentifiers
import org.bouncycastle.cms.CMSProcessableByteArray
import org.bouncycastle.cms.CMSSignedData
import org.bouncycastle.cms.jcajce.JcaSimpleSignerInfoVerifierBuilder
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class EivissaCadesMigrationTest {
    private val identity = syntheticIdentity()
    private val instant = Instant.parse("2030-01-02T03:04:05Z")
    private val clock = Clock.fixed(instant, ZoneOffset.UTC)
    private val legacy = LegacyEivissaCadesDetachedAdapter(clock)
    private val migrated = EivissaCadesDetachedAdapter(clock)
    private val properties = "headless=true\nfilter=encodedcert:" +
        Base64.getEncoder().encodeToString(identity.certificate.encoded) +
        ";filter=nonexpired:\nmode=implicit\n"
    private val challenge = "Wed, 02 Jan 2030 03:04:05 GMT".encodeToByteArray()

    @Test
    fun migrationMatchesLegacySignedBytesAndIndependentlyVerifiedCms() = runBlocking {
        for ((data, extra) in listOf(
            challenge to properties,
            byteArrayOf(0, 1, 127, -1) to properties + "mimeType=application/pdf\n",
            ByteArray(EivissaCadesDetachedAdapter.MAX_PAYLOAD_BYTES) { 42 } to properties,
        )) {
            request(data, extra).use { oldOwner ->
                request(data, extra).use { newOwner ->
                    prepare(legacy, oldOwner).use { old ->
                        prepare(migrated, newOwner).use { new ->
                            old.withBytesToSign { before ->
                                new.withBytesToSign { after -> assertArrayEquals(before, after) }
                            }
                            sign(old).use { local ->
                                val before = legacy.complete(oldOwner, old, local) as ProtocolCompletionResult.Success
                                val after = migrated.complete(newOwner, new, local) as ProtocolCompletionResult.Success
                                before.signature.use { expected ->
                                    after.signature.use { actual ->
                                        expected.withBytes { one ->
                                            actual.withBytes { two ->
                                                assertArrayEquals(one, two)
                                                verifyCms(data, one)
                                                verifyCms(data, two)
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    @Test
    fun rejectsInvalidPayloadPropertiesAndSubstitutedCertificate() = runBlocking {
        val otherCertificate = Base64.getEncoder().encodeToString(freshSyntheticIdentity().certificate.encoded)
        val encodedCertificate = Base64.getEncoder().encodeToString(identity.certificate.encoded)
        for (candidate in listOf(legacy, migrated)) {
            for (data in listOf(ByteArray(0), ByteArray(EivissaCadesDetachedAdapter.MAX_PAYLOAD_BYTES + 1))) {
                request(data).use { owner ->
                    assertPrepareFailure(SigningErrorCode.PROTOCOL_FAILED, candidate.prepare(owner, identity.chain))
                }
            }
            for (extra in listOf(
                "", "$properties\n", " $properties", "${properties}unknown=true\n",
                properties.replace(encodedCertificate, otherCertificate),
                properties.replace("headless=true\n", "") + "headless=true\n",
                properties.replace("\n", "\r\n"),
                "${properties}mimeType=application pdf\n", "${properties}mimeType=\n",
                "${properties}mimeType=${"a".repeat(129)}\n",
                "${properties}mimeType=application/pdf\nunknown=true\n",
                "${properties}${"x".repeat(EivissaCadesDetachedAdapter.MAX_EXTRA_PROPERTIES_CHARS)}",
            )) {
                request(extra = extra).use { owner ->
                    assertPrepareFailure(SigningErrorCode.PROTOCOL_FAILED, candidate.prepare(owner, identity.chain))
                }
                request(extra = extra, description = "wrong").use { owner ->
                    assertPrepareFailure(SigningErrorCode.UNSUPPORTED_PROTOCOL, candidate.prepare(owner, identity.chain))
                }
                request(extra = extra).use { owner ->
                    assertPrepareFailure(SigningErrorCode.UNSUPPORTED_PROTOCOL, candidate.prepare(owner, emptyList()))
                }
            }
        }
    }

    @Test
    fun preservesTupleDescriptionAndChainGuardsWithoutConsumingTheValidOwner() = runBlocking {
        for (candidate in listOf(legacy, migrated)) {
            request().use { owner ->
                for (chain in listOf(emptyList(), List(11) { identity.certificate })) {
                    assertPrepareFailure(SigningErrorCode.UNSUPPORTED_PROTOCOL, candidate.prepare(owner, chain))
                }
                (candidate.prepare(owner, List(10) { identity.certificate })
                    as ProtocolPrepareResult.Success).preSign.close()
                prepare(candidate, owner).use { prepared ->
                    sign(prepared).use { local ->
                        val mutations = listOf(
                            request(protocol = SigningProtocolId("wrong")), request(profile = "wrong"),
                            request(version = 2), request(description = "changed"),
                            request(origin = TrustedOrigin("https", "seu.conselldeivissa.es.evil.example", 443)),
                            request(origin = TrustedOrigin("http", "seu.conselldeivissa.es", 443)),
                            request(origin = TrustedOrigin("https", "seu.conselldeivissa.es", 444)),
                            request(algorithm = SigningAlgorithm.SHA512_WITH_RSA),
                            request(algorithm = SigningAlgorithm.SHA1_WITH_RSA), request(format = SigningFormat.XADES),
                        )
                        for (wrong in mutations) wrong.use {
                            assertPrepareFailure(SigningErrorCode.UNSUPPORTED_PROTOCOL, candidate.prepare(it, identity.chain))
                            assertFailure(SigningErrorCode.UNSUPPORTED_PROTOCOL, candidate.complete(it, prepared, local))
                        }
                        (candidate.complete(owner, prepared, local) as ProtocolCompletionResult.Success).signature.close()
                    }
                }
            }
        }
    }

    @Test
    fun rejectsImpostorAndCrossPortalStateWithoutConsumingIt() = runBlocking {
        for (candidate in listOf(legacy, migrated)) {
            request().use { owner ->
                request().use { impostor ->
                    prepare(candidate, owner).use { prepared ->
                        sign(prepared).use { local ->
                            assertEquals(owner.requestId, impostor.requestId)
                            assertFailure(SigningErrorCode.PROTOCOL_FAILED, candidate.complete(impostor, prepared, local))
                            val other = LocalCadesDetachedAdapter(clock)
                            request(
                                data = ByteArray(20), extra = "mode=explicit\nfilter=nonexpired",
                                protocol = LocalCadesDetachedAdapter.ID, profile = "aragon-siraw",
                                origin = TrustedOrigin("https", "aplicaciones.aragon.es", 443),
                                algorithm = SigningAlgorithm.SHA1_WITH_RSA,
                            ).use { foreign ->
                                prepare(other, foreign).use { foreignPreSign ->
                                    assertFailure(SigningErrorCode.PROTOCOL_FAILED, candidate.complete(owner, foreignPreSign, local))
                                    assertFailure(SigningErrorCode.PROTOCOL_FAILED, other.complete(foreign, prepared, local))
                                    sign(foreignPreSign, SigningAlgorithm.SHA1_WITH_RSA).use { foreignSignature ->
                                        (other.complete(foreign, foreignPreSign, foreignSignature)
                                            as ProtocolCompletionResult.Success).signature.close()
                                    }
                                }
                            }
                            (candidate.complete(owner, prepared, local) as ProtocolCompletionResult.Success).signature.close()
                        }
                    }
                }
            }
        }
    }

    @Test
    fun closedPreSignRejectsCompletionAndClearsSignedBytes() = runBlocking {
        for (candidate in listOf(legacy, migrated)) {
            request().use { owner ->
                prepare(candidate, owner).use { prepared ->
                    val borrowed = prepared.withBytesToSign { it }
                    sign(prepared).use { local ->
                        prepared.close()
                        assertTrue(borrowed.all { it == 0.toByte() })
                        assertFailure(SigningErrorCode.PROTOCOL_FAILED, candidate.complete(owner, prepared, local))
                    }
                }
            }
        }
    }

    @Test
    fun substitutedSigningIdentityFailsAndConsumesState() = runBlocking {
        val substitute = freshSyntheticIdentity()
        for (candidate in listOf(legacy, migrated)) {
            request().use { owner ->
                prepare(candidate, owner).use { prepared ->
                    val borrowed = prepared.withBytesToSign { it }
                    sign(prepared).use { valid ->
                        val wrong = prepared.withBytesToSign {
                            JcaLocalSignatureEngine().sign(it, substitute, SigningAlgorithm.SHA256_WITH_RSA)
                        } as LocalSignatureResult.Success
                        wrong.signature.use {
                            assertFailure(SigningErrorCode.PROTOCOL_FAILED, candidate.complete(owner, prepared, it))
                        }
                        assertTrue(borrowed.all { it == 0.toByte() })
                        assertFailure(SigningErrorCode.PROTOCOL_FAILED, candidate.complete(owner, prepared, valid))
                    }
                }
            }
        }
    }

    @Test
    fun completionUsesPreparedContentClearsStateAndRejectsReplay() = runBlocking {
        for (candidate in listOf(legacy, migrated)) {
            request().use { owner ->
                prepare(candidate, owner).use { prepared ->
                    val borrowed = prepared.withBytesToSign { it }
                    sign(prepared).use { local ->
                        owner.withPayload { it.fill(0) }
                        val completed = candidate.complete(owner, prepared, local) as ProtocolCompletionResult.Success
                        completed.signature.use { result -> result.withBytes { verifyCms(challenge, it) } }
                        assertTrue(borrowed.all { it == 0.toByte() })
                        assertFailure(SigningErrorCode.PROTOCOL_FAILED, candidate.complete(owner, prepared, local))
                    }
                }
            }
        }
    }

    private fun verifyCms(data: ByteArray, bytes: ByteArray) {
        val cms = CMSSignedData(CMSProcessableByteArray(data), bytes)
        assertTrue(cms.isDetachedSignature)
        val signer = cms.signerInfos.signers.single()
        assertEquals("2.16.840.1.101.3.4.2.1", signer.digestAlgOID)
        assertTrue(signer.encryptionAlgOID in setOf("1.2.840.113549.1.1.1", "1.2.840.113549.1.1.11"))
        val attributes = signer.signedAttributes
        assertEquals(PKCSObjectIdentifiers.data, attributes[CMSAttributes.contentType].attrValues.getObjectAt(0))
        assertArrayEquals(
            MessageDigest.getInstance("SHA-256").digest(data),
            ASN1OctetString.getInstance(attributes[CMSAttributes.messageDigest].attrValues.getObjectAt(0)).octets,
        )
        assertEquals(instant, Time.getInstance(attributes[CMSAttributes.signingTime].attrValues.getObjectAt(0)).date.toInstant())
        assertNull(attributes[PKCSObjectIdentifiers.id_aa_ets_sigPolicyId])
        val cert = cms.certificates.getMatches(null).single()
        assertTrue(signer.sid.match(cert))
        assertArrayEquals(identity.certificate.encoded, cert.encoded)
        val binding = SigningCertificateV2.getInstance(
            attributes[PKCSObjectIdentifiers.id_aa_signingCertificateV2].attrValues.getObjectAt(0),
        )
        assertArrayEquals(MessageDigest.getInstance("SHA-256").digest(cert.encoded), binding.certs.single().certHash)
        val verifier = JcaSimpleSignerInfoVerifierBuilder().setProvider(BundledBouncyCastle.provider).build(cert)
        assertTrue(signer.verify(verifier))
        val tampered = data.copyOf().apply { this[0] = (this[0].toInt() xor 1).toByte() }
        try {
            assertFalse(runCatching {
                CMSSignedData(CMSProcessableByteArray(tampered), bytes).signerInfos.signers.single().verify(verifier)
            }.getOrDefault(false))
        } finally {
            tampered.fill(0)
        }
    }

    private suspend fun prepare(candidate: SigningProtocolAdapter, owner: NormalizedSignRequest): PreSignResult =
        (candidate.prepare(owner, identity.chain) as ProtocolPrepareResult.Success).preSign

    private fun sign(prepared: PreSignResult, algorithm: SigningAlgorithm = SigningAlgorithm.SHA256_WITH_RSA): LocalSignature =
        (prepared.withBytesToSign { JcaLocalSignatureEngine().sign(it, identity, algorithm) }
            as LocalSignatureResult.Success).signature

    private fun assertPrepareFailure(code: SigningErrorCode, result: ProtocolPrepareResult) {
        assertEquals(code, (result as ProtocolPrepareResult.Failure).code)
    }

    private fun assertFailure(code: SigningErrorCode, result: ProtocolCompletionResult) {
        assertEquals(code, (result as ProtocolCompletionResult.Failure).code)
    }

    // Build the wire envelope directly to test adapter bounds independently of the store/encoder limit.
    private fun encodePayload(data: ByteArray, extra: String): ByteArray {
        val propertiesBytes = extra.encodeToByteArray()
        return try {
            ByteBuffer.allocate(12 + data.size + propertiesBytes.size)
                .putInt(0x4A464D31)
                .putInt(data.size)
                .putInt(propertiesBytes.size)
                .put(data)
                .put(propertiesBytes)
                .array()
        } finally {
            propertiesBytes.fill(0)
        }
    }

    private fun request(
        data: ByteArray = challenge,
        extra: String = properties,
        protocol: SigningProtocolId = EivissaCadesDetachedAdapter.ID,
        profile: String = EivissaCadesDetachedAdapter.PROFILE_ID,
        version: Int = 1,
        description: String = EivissaCadesDetachedAdapter.SAFE_DESCRIPTION,
        origin: TrustedOrigin = TrustedOrigin("https", "seu.conselldeivissa.es", 443),
        algorithm: SigningAlgorithm = SigningAlgorithm.SHA256_WITH_RSA,
        format: SigningFormat = SigningFormat.CADES,
        pageUrl: String? = null,
    ) = NormalizedSignRequest(
        requestId = UUID.fromString("123e4567-e89b-42d3-a456-426614174000"),
        protocolId = protocol,
        context = SigningContext(profile, version, origin, NavigationId("eivissa-migration"), observedAt = instant, pageUrl = pageUrl),
        algorithm = algorithm,
        format = format,
        safeDescription = description,
        payload = encodePayload(data, extra),
    )
}

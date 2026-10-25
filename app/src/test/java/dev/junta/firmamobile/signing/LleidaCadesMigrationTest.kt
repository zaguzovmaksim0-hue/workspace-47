package dev.junta.firmamobile.signing

import dev.junta.firmamobile.browser.NavigationId
import dev.junta.firmamobile.network.TrustedOrigin
import java.security.MessageDigest
import java.time.Clock
import java.time.Instant
import java.time.ZoneOffset
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

class LleidaCadesMigrationTest {
    private val identity = syntheticIdentity()
    private val instant = Instant.parse("2030-01-02T03:04:05Z")
    private val clock = Clock.fixed(instant, ZoneOffset.UTC)
    private val legacy = LegacyDiputacionLleidaCadesAdapter(clock)
    private val migrated = DiputacionLleidaCadesAdapter(clock)
    private val properties = "policy=FirmaAGE\nheadless=true\nfilters=nonexpired:true;authCert:true"
    private val challenge = "0123456789abcdef".repeat(4).encodeToByteArray()

    @Test
    fun migrationMatchesLegacySignedBytesAndIndependentlyVerifiedCmsAtPrintableBoundaries() = runBlocking {
        for (data in listOf(byteArrayOf(0x20), challenge, ByteArray(512) { 0x7e })) {
            request(data).use { oldOwner ->
                request(data).use { newOwner ->
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
    fun rejectsNonPrintableOrOutOfBoundsChallengesAndNonExactMultilineProperties() = runBlocking {
        val invalidProperties = listOf(
            properties.lines().reversed().joinToString("\n"),
            properties.replace("\n", "\r\n"),
            "$properties\nheadless=true",
            "$properties\nunknown=true",
            "$properties\n",
            properties.replace("headless=true", "headless= true"),
        )
        for (candidate in listOf(legacy, migrated)) {
            for (data in listOf(
                ByteArray(0), ByteArray(513) { 0x20 }, ByteArray(512) { 0x7f }, byteArrayOf(0x1f),
                byteArrayOf(0x7f), byteArrayOf(0x80.toByte()), byteArrayOf(0x0a),
            )) {
                request(data).use { owner ->
                    assertPrepareFailure(SigningErrorCode.PROTOCOL_FAILED, candidate.prepare(owner, identity.chain))
                }
            }
            for (extra in invalidProperties) {
                request(extra = extra).use { owner ->
                    assertPrepareFailure(SigningErrorCode.PROTOCOL_FAILED, candidate.prepare(owner, identity.chain))
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
                            request(origin = TrustedOrigin("https", "seu.diputaciolleida.cat.evil.example", 443)),
                            request(origin = TrustedOrigin("http", "seu.diputaciolleida.cat", 443)),
                            request(origin = TrustedOrigin("https", "seu.diputaciolleida.cat", 444)),
                            request(algorithm = SigningAlgorithm.SHA1_WITH_RSA),
                            request(algorithm = SigningAlgorithm.SHA512_WITH_RSA), request(format = SigningFormat.XADES),
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
    fun revalidatesPayloadBeforeConsumptionThenClearsStateAndRejectsReplay() = runBlocking {
        for (candidate in listOf(legacy, migrated)) {
            request().use { owner ->
                prepare(candidate, owner).use { prepared ->
                    sign(prepared).use { local ->
                        val borrowed = prepared.withBytesToSign { it }
                        val original = owner.payloadCopy()
                        val mutations = listOf(
                            MiniAppletPayloadCodec.encode(ByteArray(64) { 0x1f }, properties),
                            MiniAppletPayloadCodec.encode(challenge, properties.replace("FirmaAGE", "OtherAGE")),
                            ByteArray(original.size),
                        )
                        try {
                            for (mutation in mutations) {
                                assertEquals(original.size, mutation.size)
                                owner.withPayload { mutation.copyInto(it) }
                                assertFailure(SigningErrorCode.PROTOCOL_FAILED, candidate.complete(owner, prepared, local))
                                assertTrue(borrowed.any { it != 0.toByte() })
                            }
                            owner.withPayload { original.copyInto(it) }
                            val completed = candidate.complete(owner, prepared, local) as ProtocolCompletionResult.Success
                            completed.signature.use { result -> result.withBytes { verifyCms(challenge, it) } }
                            assertTrue(borrowed.all { it == 0.toByte() })
                            assertFailure(SigningErrorCode.PROTOCOL_FAILED, candidate.complete(owner, prepared, local))
                        } finally {
                            original.fill(0)
                            mutations.forEach { it.fill(0) }
                        }
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
    fun closedOwnerPreservesStateUntilCloseButInvalidSignatureConsumesIt() = runBlocking {
        for (candidate in listOf(legacy, migrated)) {
            for (closeOwner in listOf(false, true)) {
                request().use { owner ->
                    prepare(candidate, owner).use { prepared ->
                        val borrowed = prepared.withBytesToSign { it }
                        sign(prepared).use { valid ->
                            LocalSignature(byteArrayOf(1, 2, 3)).use { invalid ->
                                if (closeOwner) owner.close()
                                assertFailure(SigningErrorCode.PROTOCOL_FAILED, candidate.complete(owner, prepared, invalid))
                                assertEquals(closeOwner, borrowed.any { it != 0.toByte() })
                            }
                            assertFailure(SigningErrorCode.PROTOCOL_FAILED, candidate.complete(owner, prepared, valid))
                            prepared.close()
                            assertTrue(borrowed.all { it == 0.toByte() })
                            assertFailure(SigningErrorCode.PROTOCOL_FAILED, candidate.complete(owner, prepared, valid))
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

    private fun request(
        data: ByteArray = challenge,
        extra: String = properties,
        protocol: SigningProtocolId = DiputacionLleidaCadesAdapter.ID,
        profile: String = DiputacionLleidaCadesAdapter.PROFILE_ID,
        version: Int = 1,
        description: String = DiputacionLleidaCadesAdapter.SAFE_DESCRIPTION,
        origin: TrustedOrigin = TrustedOrigin("https", "seu.diputaciolleida.cat", 443),
        algorithm: SigningAlgorithm = SigningAlgorithm.SHA256_WITH_RSA,
        format: SigningFormat = SigningFormat.CADES,
    ) = NormalizedSignRequest(
        requestId = UUID.fromString("123e4567-e89b-42d3-a456-426614174000"),
        protocolId = protocol,
        context = SigningContext(profile, version, origin, NavigationId("lleida-migration"), observedAt = instant),
        algorithm = algorithm,
        format = format,
        safeDescription = description,
        payload = MiniAppletPayloadCodec.encode(data, extra),
    )
}

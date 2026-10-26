package dev.junta.firmamobile.signing

import dev.junta.firmamobile.browser.NavigationId
import dev.junta.firmamobile.network.TrustedOrigin
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.security.MessageDigest
import java.security.Signature
import java.time.Clock
import java.time.Instant
import java.time.ZoneOffset
import java.util.Base64
import java.util.UUID
import javax.xml.parsers.DocumentBuilderFactory
import kotlinx.coroutines.runBlocking
import org.apache.xml.security.Init
import org.apache.xml.security.c14n.Canonicalizer
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.w3c.dom.Element

class SevillaXadesEnvelopingMigrationTest {
    private val fixture = SevillaMigrationFixture()
    private val candidates get() = fixture.candidates()

    @Test
    fun validRequestsHaveEquivalentIndependentlyVerifiedEnvelopes() = runBlocking {
        val results = candidates.map { candidate ->
            fixture.request().use { owner ->
                fixture.prepare(candidate, owner).use { prepared ->
                    fixture.sign(prepared).use { local -> fixture.finish(candidate, owner, prepared, local) }
                }
            }
        }
        assertEquals(results[0], results[1])
    }

    @Test
    fun tupleGuardsRejectBeforeConsumingTheValidState() = runBlocking {
        for (candidate in candidates) fixture.request().use { owner ->
            fixture.prepare(candidate, owner).use { prepared ->
                fixture.sign(prepared).use { local ->
                    val mutations = listOf(
                        fixture.request(protocol = SigningProtocolId("wrong")),
                        fixture.request(profile = "wrong"), fixture.request(version = 2),
                        fixture.request(description = "changed"),
                        fixture.request(origin = TrustedOrigin("http", fixture.host, 443)),
                        fixture.request(origin = TrustedOrigin("https", fixture.host + ".evil.example", 443)),
                        fixture.request(origin = TrustedOrigin("https", fixture.host, 444)),
                        fixture.request(algorithm = SigningAlgorithm.SHA256_WITH_RSA),
                        fixture.request(format = SigningFormat.CADES),
                    )
                    for (wrong in mutations) wrong.use {
                        assertEquals(ProtocolPrepareResult.Failure(SigningErrorCode.UNSUPPORTED_PROTOCOL),
                            candidate.prepare(it, fixture.identity.chain))
                        assertEquals(ProtocolCompletionResult.Failure(SigningErrorCode.UNSUPPORTED_PROTOCOL),
                            candidate.complete(it, prepared, local))
                    }
                    fixture.finish(candidate, owner, prepared, local)
                }
            }
        }
    }

    @Test
    fun pageUrlIsNotPartOfTheSevillaTuple() = runBlocking {
        for (candidate in candidates) for (page in listOf(null,
            "https://www.sevilla.org/wrong", fixture.page + "?changed=1",
            fixture.page + "#fragment", fixture.page + "/")) {
            fixture.request(pageUrl = page).use { owner ->
                fixture.prepare(candidate, owner).use { prepared ->
                    fixture.sign(prepared).use { fixture.finish(candidate, owner, prepared, it) }
                }
            }
        }
    }

    @Test
    fun payloadAndPropertiesFailInsidePrepare() = runBlocking {
        val expected = ProtocolPrepareResult.Failure(fixture.payloadError)
        for (candidate in candidates) {
            for (data in listOf(ByteArray(0), ByteArray(fixture.data.size - 1),
                ByteArray(fixture.data.size + 1))) fixture.request(data = data).use {
                assertEquals(expected, candidate.prepare(it, fixture.identity.chain))
            }
            fixture.request(data = ByteArray(40) { 0xff.toByte() }).use {
                assertEquals(expected, candidate.prepare(it, fixture.identity.chain))
            }
            for (extra in listOf("wrong", fixture.extra + " ", fixture.extra + "\n")) {
                fixture.request(extra = extra).use {
                    assertEquals(expected, candidate.prepare(it, fixture.identity.chain))
                }
            }
            fixture.request(raw = byteArrayOf(1, 2, 3)).use {
                assertEquals(expected, candidate.prepare(it, fixture.identity.chain))
            }
            fixture.request().use {
                it.close()
                assertEquals(expected, candidate.prepare(it, fixture.identity.chain))
                assertEquals(ProtocolPrepareResult.Failure(SigningErrorCode.UNSUPPORTED_PROTOCOL),
                    candidate.prepare(it, emptyList()))
            }
        }
    }

    @Test
    fun chainBoundsKeepTheirOriginalErrorCodes() = runBlocking {
        for (candidate in candidates) fixture.request().use { owner ->
            assertEquals(ProtocolPrepareResult.Failure(SigningErrorCode.UNSUPPORTED_PROTOCOL),
                candidate.prepare(owner, emptyList()))
            for (size in listOf(9, 10, 11)) {
                assertEquals(ProtocolPrepareResult.Failure(SigningErrorCode.PROTOCOL_FAILED),
                    candidate.prepare(owner, List(size) { fixture.identity.certificate }))
            }
            (candidate.prepare(owner, List(8) { fixture.identity.certificate })
                as ProtocolPrepareResult.Success).preSign.close()
        }
    }

    @Test
    fun completionDoesNotRecheckPayloadAndConsumesStateOnce() = runBlocking {
        for (candidate in candidates) fixture.request().use { owner ->
            fixture.prepare(candidate, owner).use { prepared ->
                fixture.sign(prepared).use { local ->
                    val borrowed = prepared.withBytesToSign { it }
                    owner.withPayload { it.fill(0) }
                    fixture.finish(candidate, owner, prepared, local)
                    assertTrue(borrowed.all { it == 0.toByte() })
                    assertEquals(ProtocolCompletionResult.Failure(SigningErrorCode.PROTOCOL_FAILED),
                        candidate.complete(owner, prepared, local))
                }
            }
        }
    }

    @Test
    fun closedRequestKeepsTheOriginalCompletionBehavior() = runBlocking {
        for (candidate in candidates) fixture.request().use { owner ->
            fixture.prepare(candidate, owner).use { prepared ->
                fixture.sign(prepared).use { local ->
                    owner.close()
                    fixture.finish(candidate, owner, prepared, local)
                }
            }
        }
    }

    @Test
    fun sameIdImpostorDoesNotConsumeState() = runBlocking {
        for (candidate in candidates) fixture.request().use { owner ->
            fixture.request().use { impostor ->
                fixture.prepare(candidate, owner).use { prepared ->
                    fixture.sign(prepared).use { local ->
                        assertEquals(owner.requestId, impostor.requestId)
                        assertEquals(ProtocolCompletionResult.Failure(SigningErrorCode.PROTOCOL_FAILED),
                            candidate.complete(impostor, prepared, local))
                        fixture.finish(candidate, owner, prepared, local)
                    }
                }
            }
        }
    }

    @Test
    fun invalidOrClosedSignaturesConsumeStateAndRejectReplay() = runBlocking {
        for (candidate in candidates) for (closed in listOf(false, true)) fixture.request().use { owner ->
            fixture.prepare(candidate, owner).use { prepared ->
                val borrowed = prepared.withBytesToSign { it }
                LocalSignature(byteArrayOf(1, 2, 3)).use { invalid ->
                    if (closed) invalid.close()
                    assertEquals(ProtocolCompletionResult.Failure(SigningErrorCode.PROTOCOL_FAILED),
                        candidate.complete(owner, prepared, invalid))
                    assertTrue(borrowed.all { it == 0.toByte() })
                    assertEquals(ProtocolCompletionResult.Failure(SigningErrorCode.PROTOCOL_FAILED),
                        candidate.complete(owner, prepared, invalid))
                }
            }
        }
    }

    @Test
    fun closedAndForeignPreSignStatesFailClosed() = runBlocking {
        for (candidate in candidates) fixture.request().use { owner ->
            fixture.prepare(candidate, owner).use { prepared ->
                fixture.sign(prepared).use { local ->
                    prepared.close()
                    assertEquals(ProtocolCompletionResult.Failure(SigningErrorCode.PROTOCOL_FAILED),
                        candidate.complete(owner, prepared, local))
                    val foreign = object : PreSignState { override fun close() = Unit }
                    PreSignResult(owner, byteArrayOf(1), foreign).use {
                        assertEquals(ProtocolCompletionResult.Failure(SigningErrorCode.PROTOCOL_FAILED),
                            candidate.complete(owner, it, local))
                    }
                    foreign.close()
                }
            }
        }
    }

    @Test
    fun nonRsaCertificatesFailInsidePrepare() = runBlocking {
        val ec = syntheticEcIdentity()
        for (candidate in candidates) fixture.request().use { owner ->
            assertEquals(ProtocolPrepareResult.Failure(SigningErrorCode.PROTOCOL_FAILED),
                candidate.prepare(owner, ec.chain))
        }
    }

    @Test
    fun documentFingerprintAndSignedInfoAreWipedOnEveryCompletionExit() = runBlocking {
        val otherIdentity = freshSyntheticIdentity()
        for (candidate in candidates) for (mode in listOf(
            "success", "invalid", "empty", "oversized", "closed", "cancel", "wrong-key",
        )) fixture.request().use { owner ->
            fixture.prepare(candidate, owner).use { prepared ->
                val state = PreSignResult::class.java.getDeclaredField("ownedState").run {
                    isAccessible = true
                    get(prepared) as PreSignState
                }
                val buffers = listOf("document", "fingerprint").map { name ->
                    state.javaClass.getDeclaredField(name).run {
                        isAccessible = true
                        get(state) as ByteArray
                    }
                } + prepared.withBytesToSign { listOf(it) }
                assertTrue(buffers.all { bytes -> bytes.any { it != 0.toByte() } })
                val local = when (mode) {
                    "success" -> fixture.sign(prepared)
                    "empty" -> LocalSignature(ByteArray(0))
                    "oversized" -> LocalSignature(ByteArray(16_385) { 1 })
                    "wrong-key" -> (prepared.withBytesToSign {
                        JcaLocalSignatureEngine().sign(it, otherIdentity, fixture.algorithm)
                    } as LocalSignatureResult.Success).signature
                    else -> LocalSignature(byteArrayOf(1, 2, 3))
                }
                local.use { signature ->
                    if (mode == "closed") signature.close()
                    if (mode == "cancel") prepared.close()
                    if (mode == "success") fixture.finish(candidate, owner, prepared, signature)
                    else assertEquals(ProtocolCompletionResult.Failure(SigningErrorCode.PROTOCOL_FAILED),
                        candidate.complete(owner, prepared, signature))
                    assertTrue(buffers.all { bytes -> bytes.all { it == 0.toByte() } })
                    assertEquals(ProtocolCompletionResult.Failure(SigningErrorCode.PROTOCOL_FAILED),
                        candidate.complete(owner, prepared, signature))
                    state.close()
                    assertTrue(buffers.all { bytes -> bytes.all { it == 0.toByte() } })
                }
            }
        }
    }

    @Test
    fun challengeAlphabetAndByteBoundariesMatchLegacy() = runBlocking {
        for (candidate in candidates) for (value in 0..255) {
            val allowed = value in 0x30..0x39 || value in 0x41..0x5a ||
                value in 0x61..0x7a || value == 0x5f || value == 0x2d
            fixture.request(data = ByteArray(40) { value.toByte() }).use { owner ->
                val result = candidate.prepare(owner, fixture.identity.chain)
                if (allowed) (result as ProtocolPrepareResult.Success).preSign.close()
                else assertEquals(ProtocolPrepareResult.Failure(SigningErrorCode.PROTOCOL_FAILED), result)
            }
        }
    }
}

internal class SevillaMigrationFixture {
    val identity = syntheticIdentity()
    val instant: Instant = Instant.parse("2030-01-02T03:04:05Z")
    val clock: Clock = Clock.fixed(instant, ZoneOffset.UTC)
    val host = "www.sevilla.org"
    val protocol = SigningProtocolId("sevilla-atse-xades-enveloping-v1")
    val profile = "sevilla-atse-certificate-login"
    val description = "Acceso con certificado a la Agencia Tributaria de Sevilla"
    val algorithm = SigningAlgorithm.SHA1_WITH_RSA
    val page = "https://www.sevilla.org/ATSE"
    val extra = ""
    val data = "0123456789ABCDEFGHIJKLMNOPQRSTUVWXYZ_-ab".toByteArray()
    val payloadError = SigningErrorCode.PROTOCOL_FAILED

    fun candidates(): List<SigningProtocolAdapter> = listOf(
        LegacySevillaAtseXadesEnvelopingAdapter(clock), SevillaAtseXadesEnvelopingAdapter(clock),
    )

    fun request(
        data: ByteArray = this.data, extra: String = this.extra, protocol: SigningProtocolId = this.protocol,
        profile: String = this.profile, version: Int = 1, description: String = this.description,
        origin: TrustedOrigin = TrustedOrigin("https", host, 443),
        algorithm: SigningAlgorithm = this.algorithm, format: SigningFormat = SigningFormat.XADES,
        pageUrl: String? = page, raw: ByteArray? = null,
    ) = NormalizedSignRequest(
        UUID.fromString("123e4567-e89b-42d3-a456-426614174600"), protocol,
        SigningContext(profile, version, origin, NavigationId("xades-migration"), observedAt = instant, pageUrl = pageUrl),
        algorithm, format, description, raw ?: MiniAppletPayloadCodec.encode(data, extra),
    )

    suspend fun prepare(candidate: SigningProtocolAdapter, owner: NormalizedSignRequest): PreSignResult =
        (candidate.prepare(owner, identity.chain) as ProtocolPrepareResult.Success).preSign

    fun sign(prepared: PreSignResult): LocalSignature =
        (prepared.withBytesToSign { JcaLocalSignatureEngine().sign(it, identity, algorithm) }
            as LocalSignatureResult.Success).signature

    suspend fun finish(candidate: SigningProtocolAdapter, owner: NormalizedSignRequest,
        prepared: PreSignResult, local: LocalSignature): List<String> {
        val result = candidate.complete(owner, prepared, local) as ProtocolCompletionResult.Success
        return result.signature.use { it.withBytes { bytes -> verify(bytes) } }
    }

    // UUID-based XML identifiers differ across runs; compare verified semantics, never raw XML bytes.
    fun verify(bytes: ByteArray): List<String> {
        Init.init()
        val factory = DocumentBuilderFactory.newInstance().apply {
            isNamespaceAware = true
            setFeature("http://apache.org/xml/features/disallow-doctype-decl", true)
        }
        val document = factory.newDocumentBuilder().parse(ByteArrayInputStream(bytes))
        val ds = "http://www.w3.org/2000/09/xmldsig#"
        val all = document.getElementsByTagName("*")
        val elements = (0 until all.length).map { all.item(it) as Element }
        val info = elements.single { it.namespaceURI == ds && it.localName == "SignedInfo" }
        val method = (info.getElementsByTagNameNS(ds, "SignatureMethod").item(0) as Element).getAttribute("Algorithm")
        assertEquals("http://www.w3.org/2000/09/xmldsig#rsa-sha1", method)
        val references = info.getElementsByTagNameNS(ds, "Reference")
        assertEquals(3, references.length)
        for (index in 0 until references.length) {
            val reference = references.item(index) as Element
            assertEquals("http://www.w3.org/2001/04/xmlenc#sha512",
                (reference.getElementsByTagNameNS(ds, "DigestMethod").item(0) as Element).getAttribute("Algorithm"))
            val uri = reference.getAttribute("URI")
            assertTrue(uri.startsWith("#"))
            val target = elements.single { it.getAttribute("Id") == uri.substring(1) }
            val content = if (reference.getAttribute("Type") == ds + "Object") {
                Base64.getDecoder().decode(target.textContent).also { assertArrayEquals(data, it) }
            } else canonical(target)
            assertArrayEquals(MessageDigest.getInstance("SHA-512").digest(content),
                Base64.getDecoder().decode(reference.getElementsByTagNameNS(ds, "DigestValue").item(0).textContent))
        }
        val certificate = elements.single { it.namespaceURI == ds && it.localName == "X509Certificate" }.textContent
        assertArrayEquals(identity.certificate.encoded, Base64.getDecoder().decode(certificate))
        val signature = Base64.getDecoder().decode(
            elements.single { it.namespaceURI == ds && it.localName == "SignatureValue" }.textContent)
        assertTrue(Signature.getInstance("SHA1withRSA").run {
            initVerify(identity.certificate.publicKey); update(canonical(info)); verify(signature)
        })
        val time = elements.single { it.localName == "SigningTime" }.textContent
        assertEquals("2030-01-02T03:04:05Z", time)
        return listOf(method, Base64.getEncoder().encodeToString(data), certificate, time)
    }

    private fun canonical(element: Element): ByteArray = ByteArrayOutputStream().use {
        Canonicalizer.getInstance("http://www.w3.org/TR/2001/REC-xml-c14n-20010315").canonicalizeSubtree(element, it)
        it.toByteArray()
    }
}

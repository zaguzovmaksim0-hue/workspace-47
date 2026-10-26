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
import org.junit.runner.RunWith
import org.junit.runners.Parameterized
import org.w3c.dom.Element

@RunWith(Parameterized::class)
class XadesEnvelopingMigrationTest(cdti: Boolean) {
    private val fixture = XadesMigrationFixture(cdti)
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
    fun pageUrlAndPathAreExactOnlyForCdti() = runBlocking {
        for (candidate in candidates) for (page in listOf(null,
            "https://" + fixture.host + "/wrong", fixture.page + "?changed=1",
            fixture.page + "#fragment", fixture.page + "/")) {
            fixture.request(pageUrl = page).use { owner ->
                val result = candidate.prepare(owner, fixture.identity.chain)
                if (fixture.cdti) {
                    assertEquals(ProtocolPrepareResult.Failure(SigningErrorCode.UNSUPPORTED_PROTOCOL), result)
                } else {
                    (result as ProtocolPrepareResult.Success).preSign.use { prepared ->
                        fixture.sign(prepared).use { fixture.finish(candidate, owner, prepared, it) }
                    }
                }
            }
        }
    }

    @Test
    fun payloadAndPropertiesKeepTheirDifferentFailureStages() = runBlocking {
        val expected = ProtocolPrepareResult.Failure(fixture.payloadError)
        for (candidate in candidates) {
            for (data in listOf(ByteArray(0), ByteArray(fixture.data.size - 1),
                ByteArray(fixture.data.size + 1))) fixture.request(data = data).use {
                assertEquals(expected, candidate.prepare(it, fixture.identity.chain))
            }
            if (fixture.cdti) fixture.request(data = ByteArray(47) { 0xff.toByte() }).use {
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
    fun completionRechecksPayloadOnlyForCdtiBeforeConsumingState() = runBlocking {
        for (candidate in candidates) fixture.request().use { owner ->
            fixture.prepare(candidate, owner).use { prepared ->
                fixture.sign(prepared).use { local ->
                    val borrowed = prepared.withBytesToSign { it }
                    val original = owner.payloadCopy()
                    try {
                        owner.withPayload { it.fill(0) }
                        if (fixture.cdti) {
                            assertEquals(ProtocolCompletionResult.Failure(SigningErrorCode.UNSUPPORTED_PROTOCOL),
                                candidate.complete(owner, prepared, local))
                            assertTrue(borrowed.any { it != 0.toByte() })
                            owner.withPayload { original.copyInto(it) }
                        }
                        fixture.finish(candidate, owner, prepared, local)
                        assertTrue(borrowed.all { it == 0.toByte() })
                        assertEquals(ProtocolCompletionResult.Failure(SigningErrorCode.PROTOCOL_FAILED),
                            candidate.complete(owner, prepared, local))
                    } finally { original.fill(0) }
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
                    if (fixture.cdti) {
                        assertEquals(ProtocolCompletionResult.Failure(SigningErrorCode.UNSUPPORTED_PROTOCOL),
                            candidate.complete(owner, prepared, local))
                    } else fixture.finish(candidate, owner, prepared, local)
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

    companion object {
        @JvmStatic
        @Parameterized.Parameters(name = "CDTI={0}")
        fun portals(): List<Array<Any>> = listOf(arrayOf(false), arrayOf(true))
    }
}

internal class XadesMigrationFixture(val cdti: Boolean) {
    val identity = syntheticIdentity()
    val instant: Instant = Instant.parse("2030-01-02T03:04:05Z")
    val clock: Clock = Clock.fixed(instant, ZoneOffset.UTC)
    val host = if (cdti) "sede.cdti.gob.es" else "sede.airef.es"
    val protocol = SigningProtocolId(if (cdti) "cdti-xades-enveloping-v1" else "airef-xades-enveloping-v1")
    val profile = if (cdti) "cdti-certificate-validation" else "airef-instancia-general"
    val description = if (cdti) "Validación de certificado digital en CDTI" else
        "Firma de la solicitud de Instancia General de la AIReF"
    val algorithm = if (cdti) SigningAlgorithm.SHA512_WITH_RSA else SigningAlgorithm.SHA1_WITH_RSA
    val page = if (cdti) "https://sede.cdti.gob.es/AreaPrivada/Expedientes/Common/Certificados/ValidarCertificado.aspx"
        else "https://sede.airef.es/invesiteRE/action/solicitud/view"
    val extra = if (cdti) "filters=nonexpired" else ""
    val data: ByteArray = if (cdti) Base64.getDecoder().decode(
        "CertExp4780e003d3164fc3ac515844b4eb30f8s15utgmbrvl0kdvkk01oaz55=") else ByteArray(32) { (it + 1).toByte() }
    val payloadError = if (cdti) SigningErrorCode.UNSUPPORTED_PROTOCOL else SigningErrorCode.PROTOCOL_FAILED

    fun candidates(): List<SigningProtocolAdapter> = if (cdti)
        listOf(LegacyCdtiXadesEnvelopingAdapter(clock), CdtiXadesEnvelopingAdapter(clock)) else
        listOf(LegacyAirefXadesEnvelopingAdapter(clock), AirefXadesEnvelopingAdapter(clock))

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
        assertEquals(if (cdti) "http://www.w3.org/2001/04/xmldsig-more#rsa-sha512" else
            "http://www.w3.org/2000/09/xmldsig#rsa-sha1", method)
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
        assertTrue(Signature.getInstance(if (cdti) "SHA512withRSA" else "SHA1withRSA").run {
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

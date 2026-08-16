package dev.junta.firmamobile.signing

import dev.junta.firmamobile.browser.NavigationId
import dev.junta.firmamobile.certificate.UnlockedIdentity
import dev.junta.firmamobile.network.TrustedOrigin
import java.io.ByteArrayInputStream
import java.math.BigInteger
import java.security.KeyPair
import java.security.KeyPairGenerator
import java.security.MessageDigest
import java.security.Signature
import java.security.cert.CertificateFactory
import java.security.cert.X509Certificate
import java.security.interfaces.RSAPrivateKey
import java.time.Clock
import java.time.Instant
import java.time.ZoneOffset
import java.util.Base64
import java.util.Date
import java.util.UUID
import javax.xml.XMLConstants
import javax.xml.parsers.DocumentBuilderFactory
import kotlinx.coroutines.test.runTest
import org.apache.xml.security.Init
import org.apache.xml.security.c14n.Canonicalizer
import org.bouncycastle.asn1.DERPrintableString
import org.bouncycastle.asn1.DERUTF8String
import org.bouncycastle.asn1.x500.RDN
import org.bouncycastle.asn1.x500.X500Name
import org.bouncycastle.asn1.x500.style.BCStyle
import org.bouncycastle.asn1.x509.Extension
import org.bouncycastle.asn1.x509.KeyUsage
import org.bouncycastle.cert.jcajce.JcaX509CertificateConverter
import org.bouncycastle.cert.jcajce.JcaX509v3CertificateBuilder
import org.bouncycastle.jce.provider.BouncyCastleProvider
import org.bouncycastle.operator.jcajce.JcaContentSignerBuilder
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.w3c.dom.Document
import org.w3c.dom.Element
import org.w3c.dom.Node

class PoliciaXadesDetachedAdapterTest {
    private val clock = Clock.fixed(Instant.parse("2030-01-02T03:04:05Z"), ZoneOffset.UTC)
    private val adapter = PoliciaXadesDetachedAdapter(clock)

    private val nonRepudiationIdentity by lazy {
        testIdentity(
            commonName = "Ciudadano No Repudio",
            keyUsage = KeyUsage.digitalSignature or KeyUsage.nonRepudiation,
        )
    }

    private val dnieIdentity by lazy {
        testIdentity(
            commonName = "Ciudadano DNIe",
            issuerCn = "AC DNIE 004",
            issuerOu = "DNIE",
            issuerOrg = "DIRECCION GENERAL DE LA POLICIA",
            keyUsage = KeyUsage.nonRepudiation,
        )
    }

    private val dnieLookalikeIdentity by lazy {
        testIdentity(
            commonName = "Ciudadano DNIe falso",
            issuerCn = "AC DNIE 004",
            issuerOrg = "DIRECCION GENERAL DE LA POLICIA",
            keyUsage = KeyUsage.nonRepudiation,
        )
    }

    private val digitalSignatureOnlyIdentity by lazy {
        testIdentity(
            commonName = "Ciudadano Solo Firma",
            keyUsage = KeyUsage.digitalSignature,
        )
    }

    private val expiredIdentity by lazy {
        testIdentity(
            commonName = "Ciudadano Expirado",
            notBefore = Instant.parse("2020-01-01T00:00:00Z"),
            notAfter = Instant.parse("2021-01-01T00:00:00Z"),
            keyUsage = KeyUsage.digitalSignature or KeyUsage.nonRepudiation,
        )
    }

    @Test
    fun createsAutoFirmaCompatibleSha1XadesDetachedWithBinaryPayloadAndValidatesSignature() = runTest {
        val binaryData = byteArrayOf(0x00, 0x01, 0x02, 0xfe.toByte(), 0xff.toByte(), 0x10, 0x20, 0x30)
        val request = request(data = binaryData.copyOf())
        val prepared = adapter.prepare(request, nonRepudiationIdentity.chain) as ProtocolPrepareResult.Success
        val local = prepared.preSign.withBytesToSign { signedInfo ->
            JcaLocalSignatureEngine().sign(
                signedInfo,
                nonRepudiationIdentity,
                SigningAlgorithm.SHA1_WITH_RSA,
            )
        } as LocalSignatureResult.Success

        val completed = adapter.complete(request, prepared.preSign, local.signature)
            as ProtocolCompletionResult.Success
        val result = completed.signature.withBytes { it.copyOf() }

        verifyAutoFirmaDetachedBinary(result, binaryData, nonRepudiationIdentity.certificate)

        val fingerprint = MessageDigest.getInstance("SHA-256").digest(nonRepudiationIdentity.certificate.encoded)
        assertTrue(PoliciaXadesDetachedCodec.validate(result, fingerprint))

        val recoveredData = PoliciaXadesDetachedCodec.recoverSignedData(result)
        assertArrayEquals(binaryData, recoveredData)

        val tampered = result.copyOf().also { bytes ->
            val b64Data = Base64.getEncoder().encode(binaryData)
            val start = bytes.indexOfSubsequence(b64Data)
            check(start >= 0)
            bytes[start] = (bytes[start].toInt() xor 1).toByte()
        }
        assertFalse(PoliciaXadesDetachedCodec.validate(tampered, fingerprint))

        result.fill(0)
        tampered.fill(0)
        fingerprint.fill(0)
        completed.signature.close()
        local.signature.close()
        prepared.preSign.close()
        request.close()
    }

    @Test
    fun createsAutoFirmaCompatibleSha1XadesDetachedWithHtmlFragmentPayload() = runTest {
        val htmlPayload = "<div><br><p>Solicitud genérica de prueba</p></div>".encodeToByteArray()
        val request = request(data = htmlPayload.copyOf())
        val prepared = adapter.prepare(request, dnieIdentity.chain) as ProtocolPrepareResult.Success
        val local = prepared.preSign.withBytesToSign { signedInfo ->
            JcaLocalSignatureEngine().sign(
                signedInfo,
                dnieIdentity,
                SigningAlgorithm.SHA1_WITH_RSA,
            )
        } as LocalSignatureResult.Success

        val completed = adapter.complete(request, prepared.preSign, local.signature)
            as ProtocolCompletionResult.Success
        val result = completed.signature.withBytes { it.copyOf() }

        val recovered = PoliciaXadesDetachedCodec.recoverSignedData(result)
        assertArrayEquals(htmlPayload, recovered)
        verifyAutoFirmaDetachedBinary(result, htmlPayload, dnieIdentity.certificate)

        val fingerprint = MessageDigest.getInstance("SHA-256").digest(dnieIdentity.certificate.encoded)
        assertTrue(PoliciaXadesDetachedCodec.validate(result, fingerprint))

        result.fill(0)
        completed.signature.close()
        local.signature.close()
        prepared.preSign.close()
        request.close()
    }

    @Test
    fun createsAutoFirmaCompatibleSha1XadesDetachedWithXmlPayload() = runTest {
        val request = request(data = XML.copyOf())
        val prepared = adapter.prepare(request, nonRepudiationIdentity.chain) as ProtocolPrepareResult.Success
        val local = prepared.preSign.withBytesToSign { signedInfo ->
            JcaLocalSignatureEngine().sign(
                signedInfo,
                nonRepudiationIdentity,
                SigningAlgorithm.SHA1_WITH_RSA,
            )
        } as LocalSignatureResult.Success

        val completed = adapter.complete(request, prepared.preSign, local.signature)
            as ProtocolCompletionResult.Success
        val result = completed.signature.withBytes { it.copyOf() }

        verifyAutoFirmaDetachedXml(result, nonRepudiationIdentity.certificate)

        val fingerprint = MessageDigest.getInstance("SHA-256").digest(nonRepudiationIdentity.certificate.encoded)
        assertTrue(PoliciaXadesDetachedCodec.validate(result, fingerprint))

        result.fill(0)
        completed.signature.close()
        local.signature.close()
        prepared.preSign.close()
        request.close()
    }

    @Test
    fun rejectsExactTupleViolations() = runTest {
        val missingFilter1 = request(
            extraProperties = "format=XAdES Detached\nfilters.2=keyusage.nonrepudiation:true;nonexpired:",
        )
        assertTrue(adapter.prepare(missingFilter1, nonRepudiationIdentity.chain) is ProtocolPrepareResult.Failure)
        missingFilter1.close()

        val missingFilter2 = request(
            extraProperties = "format=XAdES Detached\nfilters.1=dnie:;nonexpired:",
        )
        assertTrue(adapter.prepare(missingFilter2, nonRepudiationIdentity.chain) is ProtocolPrepareResult.Failure)
        missingFilter2.close()

        val collapsedFilters = request(
            extraProperties = "filters=keyusage.nonrepudiation:true;nonexpired:\nformat=XAdES Detached",
        )
        assertTrue(adapter.prepare(collapsedFilters, nonRepudiationIdentity.chain) is ProtocolPrepareResult.Failure)
        collapsedFilters.close()

        val reorderedProperties = request(
            extraProperties = "filters.1=dnie:;nonexpired:\nfilters.2=keyusage.nonrepudiation:true;nonexpired:\nformat=XAdES Detached",
        )
        val reorderedResult = adapter.prepare(reorderedProperties, nonRepudiationIdentity.chain)
        assertTrue(reorderedResult is ProtocolPrepareResult.Success)
        (reorderedResult as ProtocolPrepareResult.Success).preSign.close()
        reorderedProperties.close()

        val extraProperties = request(
            extraProperties = "format=XAdES Detached\nfilters.1=dnie:;nonexpired:\nfilters.2=keyusage.nonrepudiation:true;nonexpired:\nmode=implicit",
        )
        assertTrue(adapter.prepare(extraProperties, nonRepudiationIdentity.chain) is ProtocolPrepareResult.Failure)
        extraProperties.close()

        val wrongFormat = request(
            extraProperties = "format=CAdES\nfilters.1=dnie:;nonexpired:\nfilters.2=keyusage.nonrepudiation:true;nonexpired:",
        )
        assertTrue(adapter.prepare(wrongFormat, nonRepudiationIdentity.chain) is ProtocolPrepareResult.Failure)
        wrongFormat.close()
    }

    @Test
    fun enforcesStrictCertificateFiltering() = runTest {
        // Non-repudiation certificate accepted
        val req1 = request()
        assertTrue(adapter.prepare(req1, nonRepudiationIdentity.chain) is ProtocolPrepareResult.Success)
        req1.close()

        // DNIe certificate accepted
        val req2 = request()
        assertTrue(adapter.prepare(req2, dnieIdentity.chain) is ProtocolPrepareResult.Success)
        req2.close()

        // It is not a DNIe certificate, but it still matches the alternative nonRepudiation filter.
        assertFalse(PoliciaXadesDetachedAdapter.isDnieCertificate(dnieLookalikeIdentity.certificate))
        assertTrue(PoliciaXadesDetachedAdapter.hasNonRepudiationKeyUsage(dnieLookalikeIdentity.certificate))
        val lookalike = request()
        val lookalikeResult = adapter.prepare(lookalike, dnieLookalikeIdentity.chain)
        assertTrue(lookalikeResult is ProtocolPrepareResult.Success)
        (lookalikeResult as ProtocolPrepareResult.Success).preSign.close()
        lookalike.close()

        // Digital signature only (not DNIe, no nonRepudiation) rejected
        val req3 = request()
        val result3 = adapter.prepare(req3, digitalSignatureOnlyIdentity.chain)
        assertTrue(result3 is ProtocolPrepareResult.Failure)
        assertEquals(SigningErrorCode.INVALID_REQUEST, (result3 as ProtocolPrepareResult.Failure).code)
        req3.close()

        // Expired certificate rejected
        val req4 = request()
        val result4 = adapter.prepare(req4, expiredIdentity.chain)
        assertTrue(result4 is ProtocolPrepareResult.Failure)
        assertEquals(SigningErrorCode.INVALID_REQUEST, (result4 as ProtocolPrepareResult.Failure).code)
        req4.close()

        // Non-RSA (EC) certificate rejected
        val ecIdentity = testEcIdentity()
        val req5 = request()
        val result5 = adapter.prepare(req5, ecIdentity.chain)
        assertTrue(result5 is ProtocolPrepareResult.Failure)
        assertEquals(SigningErrorCode.INVALID_REQUEST, (result5 as ProtocolPrepareResult.Failure).code)
        req5.close()
    }

    @Test
    fun acceptsExactDnieIssuerAttributesInDifferentRdnOrder() = runTest {
        val reorderedIssuer = X500Name(
            arrayOf(
                RDN(BCStyle.C, DERPrintableString("ES")),
                RDN(BCStyle.O, DERUTF8String("DIRECCION GENERAL DE LA POLICIA")),
                RDN(BCStyle.OU, DERUTF8String("DNIE")),
                RDN(BCStyle.CN, DERUTF8String("AC DNIE 004")),
            ),
        )
        val reorderedIdentity = testIdentity(
            commonName = "Ciudadano DNIe Reordenado",
            issuer = reorderedIssuer,
            keyUsage = KeyUsage.nonRepudiation,
        )

        assertTrue(PoliciaXadesDetachedAdapter.isDnieCertificate(reorderedIdentity.certificate))

        val req = request()
        val result = adapter.prepare(req, reorderedIdentity.chain)
        assertTrue(result is ProtocolPrepareResult.Success)
        req.close()
    }

    @Test
    fun rejectsDnieIssuerWithMissingOu() = runTest {
        val missingOuIssuer = X500Name(
            arrayOf(
                RDN(BCStyle.CN, DERUTF8String("AC DNIE 004")),
                RDN(BCStyle.O, DERUTF8String("DIRECCION GENERAL DE LA POLICIA")),
                RDN(BCStyle.C, DERPrintableString("ES")),
            ),
        )
        val identity = testIdentity(
            commonName = "Ciudadano DNIe Sin OU",
            issuer = missingOuIssuer,
            keyUsage = KeyUsage.digitalSignature,
        )

        assertFalse(PoliciaXadesDetachedAdapter.isDnieCertificate(identity.certificate))

        val req = request()
        val result = adapter.prepare(req, identity.chain)
        assertTrue(result is ProtocolPrepareResult.Failure)
        assertEquals(SigningErrorCode.INVALID_REQUEST, (result as ProtocolPrepareResult.Failure).code)
        req.close()
    }

    @Test
    fun rejectsDnieIssuerWithWrongOrgSubstring() = runTest {
        val wrongOrgIssuer = X500Name(
            arrayOf(
                RDN(BCStyle.CN, DERUTF8String("AC DNIE 004")),
                RDN(BCStyle.OU, DERUTF8String("DNIE")),
                RDN(BCStyle.O, DERUTF8String("DIRECCION GENERAL DE LA POLICIA NACIONAL")),
                RDN(BCStyle.C, DERPrintableString("ES")),
            ),
        )
        val identity = testIdentity(
            commonName = "Ciudadano Org Erronea",
            issuer = wrongOrgIssuer,
            keyUsage = KeyUsage.digitalSignature,
        )

        assertFalse(PoliciaXadesDetachedAdapter.isDnieCertificate(identity.certificate))

        val req = request()
        val result = adapter.prepare(req, identity.chain)
        assertTrue(result is ProtocolPrepareResult.Failure)
        assertEquals(SigningErrorCode.INVALID_REQUEST, (result as ProtocolPrepareResult.Failure).code)
        req.close()
    }

    @Test
    fun rejectsDnieIssuerWithDuplicateCnOrOu() = runTest {
        val duplicateCnIssuer = X500Name(
            arrayOf(
                RDN(BCStyle.CN, DERUTF8String("AC DNIE 004")),
                RDN(BCStyle.CN, DERUTF8String("AC DNIE 005")),
                RDN(BCStyle.OU, DERUTF8String("DNIE")),
                RDN(BCStyle.O, DERUTF8String("DIRECCION GENERAL DE LA POLICIA")),
                RDN(BCStyle.C, DERPrintableString("ES")),
            ),
        )
        val duplicateCnIdentity = testIdentity(
            commonName = "Ciudadano CN Duplicado",
            issuer = duplicateCnIssuer,
            keyUsage = KeyUsage.digitalSignature,
        )

        assertFalse(PoliciaXadesDetachedAdapter.isDnieCertificate(duplicateCnIdentity.certificate))

        val req1 = request()
        val result1 = adapter.prepare(req1, duplicateCnIdentity.chain)
        assertTrue(result1 is ProtocolPrepareResult.Failure)
        assertEquals(SigningErrorCode.INVALID_REQUEST, (result1 as ProtocolPrepareResult.Failure).code)
        req1.close()

        val duplicateOuIssuer = X500Name(
            arrayOf(
                RDN(BCStyle.CN, DERUTF8String("AC DNIE 004")),
                RDN(BCStyle.OU, DERUTF8String("DNIE")),
                RDN(BCStyle.OU, DERUTF8String("OTRA UNIDAD")),
                RDN(BCStyle.O, DERUTF8String("DIRECCION GENERAL DE LA POLICIA")),
                RDN(BCStyle.C, DERPrintableString("ES")),
            ),
        )
        val duplicateOuIdentity = testIdentity(
            commonName = "Ciudadano OU Duplicado",
            issuer = duplicateOuIssuer,
            keyUsage = KeyUsage.digitalSignature,
        )

        assertFalse(PoliciaXadesDetachedAdapter.isDnieCertificate(duplicateOuIdentity.certificate))

        val req2 = request()
        val result2 = adapter.prepare(req2, duplicateOuIdentity.chain)
        assertTrue(result2 is ProtocolPrepareResult.Failure)
        assertEquals(SigningErrorCode.INVALID_REQUEST, (result2 as ProtocolPrepareResult.Failure).code)
        req2.close()
    }

    @Test
    fun rejectsSubjectOnlyDnieLookalike() = runTest {
        val subjectDnieIssuerOther = testIdentity(
            commonName = "AC DNIE 004",
            subjectOu = "DNIE",
            subjectOrg = "DIRECCION GENERAL DE LA POLICIA",
            issuer = X500Name("CN=Autoridad Emisora Distinta,O=Otra Entidad,C=ES"),
            keyUsage = KeyUsage.digitalSignature,
        )

        assertFalse(PoliciaXadesDetachedAdapter.isDnieCertificate(subjectDnieIssuerOther.certificate))

        val req = request()
        val result = adapter.prepare(req, subjectDnieIssuerOther.chain)
        assertTrue(result is ProtocolPrepareResult.Failure)
        assertEquals(SigningErrorCode.INVALID_REQUEST, (result as ProtocolPrepareResult.Failure).code)
        req.close()
    }

    @Test
    fun rejectsDnieIssuerWithoutNonRepudiationKeyUsage() = runTest {
        val dnieNoNonRepudiationIdentity = testIdentity(
            commonName = "Ciudadano DNIe Sin No Repudio",
            issuerCn = "AC DNIE 004",
            issuerOu = "DNIE",
            issuerOrg = "DIRECCION GENERAL DE LA POLICIA",
            keyUsage = KeyUsage.digitalSignature,
        )

        assertFalse(PoliciaXadesDetachedAdapter.isDnieCertificate(dnieNoNonRepudiationIdentity.certificate))
        assertFalse(PoliciaXadesDetachedAdapter.hasNonRepudiationKeyUsage(dnieNoNonRepudiationIdentity.certificate))

        val req = request()
        val result = adapter.prepare(req, dnieNoNonRepudiationIdentity.chain)
        assertTrue(result is ProtocolPrepareResult.Failure)
        assertEquals(SigningErrorCode.INVALID_REQUEST, (result as ProtocolPrepareResult.Failure).code)
        req.close()
    }

    @Test
    fun rejectsWrongAlgorithmWrongOriginWrongProfileAndDoctype() = runTest {
        val wrongAlgorithm = request(algorithm = SigningAlgorithm.SHA256_WITH_RSA)
        assertTrue(adapter.prepare(wrongAlgorithm, nonRepudiationIdentity.chain) is ProtocolPrepareResult.Failure)
        wrongAlgorithm.close()

        val wrongOrigin = NormalizedSignRequest(
            requestId = UUID.fromString("123e4567-e89b-42d3-a456-426614175000"),
            protocolId = PoliciaXadesDetachedAdapter.ID,
            context = SigningContext(
                profileId = PoliciaXadesDetachedAdapter.PROFILE_ID,
                profileVersion = 1,
                origin = TrustedOrigin("https", "evil.example.es", 443),
                navigationId = NavigationId("123e4567-e89b-42d3-a456-426614175001"),
                navigationEpoch = 10,
                observedAt = clock.instant(),
            ),
            algorithm = SigningAlgorithm.SHA1_WITH_RSA,
            format = SigningFormat.XADES,
            safeDescription = PoliciaXadesDetachedAdapter.SAFE_DESCRIPTION,
            payload = MiniAppletPayloadCodec.encode(XML.copyOf(), EXTRA_PROPERTIES),
        )
        assertTrue(adapter.prepare(wrongOrigin, nonRepudiationIdentity.chain) is ProtocolPrepareResult.Failure)
        wrongOrigin.close()

        val doctype = request(
            data = """<!DOCTYPE solicitud [<!ENTITY xxe SYSTEM "file:///etc/passwd">]><solicitud>&xxe;</solicitud>"""
                .encodeToByteArray(),
        )
        assertTrue(adapter.prepare(doctype, nonRepudiationIdentity.chain) is ProtocolPrepareResult.Failure)
        doctype.close()
    }

    private fun request(
        data: ByteArray = XML.copyOf(),
        extraProperties: String = EXTRA_PROPERTIES,
        algorithm: SigningAlgorithm = SigningAlgorithm.SHA1_WITH_RSA,
    ) = NormalizedSignRequest(
        requestId = UUID.fromString("123e4567-e89b-42d3-a456-426614175000"),
        protocolId = PoliciaXadesDetachedAdapter.ID,
        context = SigningContext(
            profileId = PoliciaXadesDetachedAdapter.PROFILE_ID,
            profileVersion = 1,
            origin = TrustedOrigin("https", "sede.policia.gob.es", 443),
            navigationId = NavigationId("123e4567-e89b-42d3-a456-426614175001"),
            navigationEpoch = 10,
            observedAt = clock.instant(),
        ),
        algorithm = algorithm,
        format = SigningFormat.XADES,
        safeDescription = PoliciaXadesDetachedAdapter.SAFE_DESCRIPTION,
        payload = MiniAppletPayloadCodec.encode(data, extraProperties),
    ).also { data.fill(0) }

    private fun verifyAutoFirmaDetachedBinary(
        result: ByteArray,
        expectedData: ByteArray,
        expectedCertificate: X509Certificate,
    ) {
        Init.init()
        val document = parse(result)
        val root = document.documentElement
        assertEquals("AFIRMA", root.nodeName)

        val content = root.singleChild("CONTENT")
        assertEquals(OCTET_STREAM_MIME_TYPE, content.getAttribute("MimeType"))
        assertEquals(BASE64_ENCODING_URI, content.getAttribute("Encoding"))
        assertTrue(content.getAttribute("Id").startsWith("CONTENT-"))
        assertEquals(Base64.getEncoder().encodeToString(expectedData), content.textContent)

        val signature = root.singleChild(DS_NS, "Signature")
        val signedInfo = signature.singleChild(DS_NS, "SignedInfo")
        assertEquals(
            RSA_SHA1,
            signedInfo.singleChild(DS_NS, "SignatureMethod").getAttribute("Algorithm"),
        )
        assertEquals(
            C14N,
            signedInfo.singleChild(DS_NS, "CanonicalizationMethod").getAttribute("Algorithm"),
        )

        val references = signedInfo.directChildren(DS_NS, "Reference")
        assertEquals(3, references.size)

        val contentReference = references.single { it.getAttribute("URI") == "#${content.getAttribute("Id")}" }
        assertEquals(SHA512_URI, contentReference.digestMethod())
        assertEquals(
            BASE64_ENCODING_URI,
            contentReference.getElementsByTagNameNS(DS_NS, "Transform").asElements()
                .single().getAttribute("Algorithm"),
        )
        assertArrayEquals(
            sha512(expectedData),
            Base64.getDecoder().decode(contentReference.digestValue()),
        )

        val signedPropertiesReference = references.single {
            it.getAttribute("Type") == SIGNED_PROPERTIES_TYPE
        }
        assertEquals(SHA512_URI, signedPropertiesReference.digestMethod())
        val signedProperties = document.findById(signedPropertiesReference.fragmentId())
        assertEquals(XADES_NS, signedProperties.namespaceURI)
        assertEquals("SignedProperties", signedProperties.localName)
        assertArrayEquals(
            sha512(canonicalize(signedProperties)),
            Base64.getDecoder().decode(signedPropertiesReference.digestValue()),
        )

        val dataObjectFormat = signedProperties.getElementsByTagNameNS(XADES_NS, "DataObjectFormat")
            .asElements().single()
        assertEquals("#${contentReference.getAttribute("Id")}", dataObjectFormat.getAttribute("ObjectReference"))
        assertEquals(OCTET_STREAM_MIME_TYPE, dataObjectFormat.singleChild(XADES_NS, "MimeType").textContent)
        assertEquals(BASE64_ENCODING_URI, dataObjectFormat.singleChild(XADES_NS, "Encoding").textContent)

        val keyInfoReference = references.single { reference ->
            val target = document.findById(reference.fragmentId())
            target.namespaceURI == DS_NS && target.localName == "KeyInfo"
        }
        assertEquals(SHA512_URI, keyInfoReference.digestMethod())
        val keyInfo = document.findById(keyInfoReference.fragmentId())
        assertArrayEquals(
            sha512(canonicalize(keyInfo)),
            Base64.getDecoder().decode(keyInfoReference.digestValue()),
        )

        val certificateBytes = Base64.getDecoder().decode(
            keyInfo.getElementsByTagNameNS(DS_NS, "X509Certificate").item(0).textContent,
        )
        assertArrayEquals(expectedCertificate.encoded, certificateBytes)
        val certificate = CertificateFactory.getInstance("X.509")
            .generateCertificate(ByteArrayInputStream(certificateBytes)) as X509Certificate
        val signatureValue = Base64.getDecoder().decode(
            signature.singleChild(DS_NS, "SignatureValue").textContent,
        )
        assertTrue(Signature.getInstance("SHA1withRSA").run {
            initVerify(certificate.publicKey)
            update(canonicalize(signedInfo))
            verify(signatureValue)
        })

        val qualifyingProperties = signature.getElementsByTagNameNS(XADES_NS, "QualifyingProperties")
            .asElements().single()
        assertEquals("#${signature.getAttribute("Id")}", qualifyingProperties.getAttribute("Target"))
        assertEquals(
            clock.instant().toString(),
            signature.getElementsByTagNameNS(XADES_NS, "SigningTime").item(0).textContent,
        )
    }

    private fun verifyAutoFirmaDetachedXml(result: ByteArray, expectedCertificate: X509Certificate) {
        Init.init()
        val document = parse(result)
        val root = document.documentElement
        assertEquals("AFIRMA", root.nodeName)

        val content = root.singleChild("CONTENT")
        assertEquals(XML_MIME_TYPE, content.getAttribute("MimeType"))
        assertTrue(content.getAttribute("Id").startsWith("CONTENT-"))

        val signature = root.singleChild(DS_NS, "Signature")
        val signedInfo = signature.singleChild(DS_NS, "SignedInfo")
        assertEquals(
            RSA_SHA1,
            signedInfo.singleChild(DS_NS, "SignatureMethod").getAttribute("Algorithm"),
        )
        assertEquals(
            C14N,
            signedInfo.singleChild(DS_NS, "CanonicalizationMethod").getAttribute("Algorithm"),
        )

        val references = signedInfo.directChildren(DS_NS, "Reference")
        assertEquals(3, references.size)

        val contentReference = references.single { it.getAttribute("URI") == "#${content.getAttribute("Id")}" }
        assertEquals(SHA512_URI, contentReference.digestMethod())
        assertEquals(
            C14N,
            contentReference.getElementsByTagNameNS(DS_NS, "Transform").asElements()
                .single().getAttribute("Algorithm"),
        )
        assertArrayEquals(
            sha512(canonicalize(content)),
            Base64.getDecoder().decode(contentReference.digestValue()),
        )
    }

    private fun parse(bytes: ByteArray): Document = DocumentBuilderFactory.newInstance().apply {
        isNamespaceAware = true
        runCatching { isXIncludeAware = false }
        runCatching { isExpandEntityReferences = false }
        runCatching { setFeature(XMLConstants.FEATURE_SECURE_PROCESSING, true) }
        runCatching { setFeature("http://apache.org/xml/features/disallow-doctype-decl", true) }
        runCatching { setFeature("http://xml.org/sax/features/external-general-entities", false) }
        runCatching { setFeature("http://xml.org/sax/features/external-parameter-entities", false) }
        runCatching { setFeature("http://apache.org/xml/features/nonvalidating/load-external-dtd", false) }
    }.newDocumentBuilder().parse(ByteArrayInputStream(bytes))

    private fun canonicalize(node: Node): ByteArray {
        val output = java.io.ByteArrayOutputStream()
        Canonicalizer.getInstance(C14N).canonicalizeSubtree(node, output)
        return output.toByteArray()
    }

    private fun sha512(bytes: ByteArray): ByteArray = MessageDigest.getInstance("SHA-512").digest(bytes)

    private fun Element.digestMethod(): String =
        singleChild(DS_NS, "DigestMethod").getAttribute("Algorithm")

    private fun Element.digestValue(): String = singleChild(DS_NS, "DigestValue").textContent

    private fun Element.fragmentId(): String = getAttribute("URI").also {
        require(it.startsWith("#") && it.length > 1)
    }.substring(1)

    private fun Element.singleChild(name: String): Element =
        childNodes.asElements().single { it.nodeName == name }

    private fun Element.singleChild(namespace: String, name: String): Element =
        directChildren(namespace, name).single()

    private fun Element.directChildren(namespace: String, name: String): List<Element> =
        childNodes.asElements().filter { it.namespaceURI == namespace && it.localName == name }

    private fun org.w3c.dom.NodeList.asElements(): List<Element> =
        (0 until length).mapNotNull { item(it) as? Element }

    private fun Document.findById(id: String): Element =
        documentElement.walkElements().single { it.getAttribute("Id") == id }

    private fun Element.walkElements(): Sequence<Element> = sequence {
        yield(this@walkElements)
        childNodes.asElements().forEach { yieldAll(it.walkElements()) }
    }

    private fun ByteArray.indexOfSubsequence(needle: ByteArray): Int {
        for (start in 0..size - needle.size) {
            if (needle.indices.all { this[start + it] == needle[it] }) return start
        }
        return -1
    }

    private fun testIdentity(
        commonName: String,
        issuerCn: String = commonName,
        issuerOu: String? = null,
        issuerOrg: String = "Junta Firma Mobile Tests",
        subjectOu: String? = null,
        subjectOrg: String = issuerOrg,
        issuer: X500Name? = null,
        notBefore: Instant = Instant.parse("2029-01-01T00:00:00Z"),
        notAfter: Instant = Instant.parse("2031-01-01T00:00:00Z"),
        keyUsage: Int = KeyUsage.digitalSignature or KeyUsage.nonRepudiation,
    ): UnlockedIdentity {
        val generator = KeyPairGenerator.getInstance("RSA")
        generator.initialize(2048)
        val keyPair = generator.generateKeyPair()
        val subject = X500Name(
            listOfNotNull(
                "CN=$commonName",
                subjectOu?.let { "OU=$it" },
                "O=$subjectOrg",
                "C=ES",
            ).joinToString(","),
        )
        val effectiveIssuer = issuer ?: X500Name(
            listOfNotNull(
                "CN=$issuerCn",
                issuerOu?.let { "OU=$it" },
                "O=$issuerOrg",
                "C=ES",
            ).joinToString(","),
        )
        val builder = JcaX509v3CertificateBuilder(
            effectiveIssuer,
            BigInteger.valueOf(System.nanoTime()),
            Date.from(notBefore),
            Date.from(notAfter),
            subject,
            keyPair.public,
        )
        builder.addExtension(Extension.keyUsage, true, KeyUsage(keyUsage))
        val signer = JcaContentSignerBuilder("SHA256withRSA")
            .setProvider(BouncyCastleProvider())
            .build(keyPair.private)
        val certificate = JcaX509CertificateConverter()
            .setProvider(BouncyCastleProvider())
            .getCertificate(builder.build(signer))
        return UnlockedIdentity(
            privateKey = keyPair.private as RSAPrivateKey,
            certificate = certificate,
            chain = listOf(certificate),
            summary = dev.junta.firmamobile.certificate.CertificateSummary(
                ownerName = commonName,
                issuerName = issuerCn,
                validFrom = notBefore,
                validUntil = notAfter,
            ),
        )
    }

    private fun testEcIdentity(): UnlockedIdentity {
        val generator = KeyPairGenerator.getInstance("EC")
        generator.initialize(256)
        val keyPair = generator.generateKeyPair()
        val subject = X500Name("CN=EC Identity,O=Junta Firma Mobile Tests,C=ES")
        val builder = JcaX509v3CertificateBuilder(
            subject,
            BigInteger.valueOf(System.nanoTime()),
            Date.from(Instant.parse("2029-01-01T00:00:00Z")),
            Date.from(Instant.parse("2031-01-01T00:00:00Z")),
            subject,
            keyPair.public,
        )
        builder.addExtension(Extension.keyUsage, true, KeyUsage(KeyUsage.digitalSignature or KeyUsage.nonRepudiation))
        val signer = JcaContentSignerBuilder("SHA256withECDSA")
            .setProvider(BouncyCastleProvider())
            .build(keyPair.private)
        val certificate = JcaX509CertificateConverter()
            .setProvider(BouncyCastleProvider())
            .getCertificate(builder.build(signer))
        return UnlockedIdentity(
            privateKey = keyPair.private,
            certificate = certificate,
            chain = listOf(certificate),
            summary = dev.junta.firmamobile.certificate.CertificateSummary(
                ownerName = "EC Identity",
                issuerName = "EC Identity",
                validFrom = Instant.parse("2029-01-01T00:00:00Z"),
                validUntil = Instant.parse("2031-01-01T00:00:00Z"),
            ),
        )
    }

    private companion object {
        val XML = """<?xml version="1.0" encoding="UTF-8"?><solicitud><campo>solicitud-policia</campo></solicitud>"""
            .encodeToByteArray()
        const val EXTRA_PROPERTIES =
            "format=XAdES Detached\nfilters.1=dnie:;nonexpired:\nfilters.2=keyusage.nonrepudiation:true;nonexpired:"
        const val DS_NS = "http://www.w3.org/2000/09/xmldsig#"
        const val XADES_NS = "http://uri.etsi.org/01903/v1.3.2#"
        const val C14N = "http://www.w3.org/TR/2001/REC-xml-c14n-20010315"
        const val RSA_SHA1 = "http://www.w3.org/2000/09/xmldsig#rsa-sha1"
        const val SHA512_URI = "http://www.w3.org/2001/04/xmlenc#sha512"
        const val SIGNED_PROPERTIES_TYPE = "http://uri.etsi.org/01903#SignedProperties"
        const val XML_MIME_TYPE = "text/xml"
        const val OCTET_STREAM_MIME_TYPE = "application/octet-stream"
        const val BASE64_ENCODING_URI = "http://www.w3.org/2000/09/xmldsig#base64"
    }
}

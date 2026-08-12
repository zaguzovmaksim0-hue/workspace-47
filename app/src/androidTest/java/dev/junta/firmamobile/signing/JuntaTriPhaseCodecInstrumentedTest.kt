package dev.junta.firmamobile.signing

import android.util.Base64
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import dev.junta.firmamobile.browser.NavigationId
import dev.junta.firmamobile.certificate.CertificateLoadResult
import dev.junta.firmamobile.certificate.Pkcs12Loader
import dev.junta.firmamobile.network.SafeNetworkUrlPolicy
import dev.junta.firmamobile.network.TrustedOrigin
import java.io.ByteArrayInputStream
import java.time.Instant
import java.util.UUID
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class JuntaTriPhaseCodecInstrumentedTest {
    @Test
    fun androidParserAcceptsSyntheticSessionAndRejectsDoctype() = runBlocking {
        val identityBytes = syntheticPkcs12()
        val password = TEST_PASSPHRASE.toCharArray()
        val identity = try {
            val loaded = Pkcs12Loader().load(
                ByteArrayInputStream(identityBytes),
                identityBytes.size.toLong(),
                password,
            )
            (loaded as CertificateLoadResult.Success).identity
        } finally {
            password.fill('\u0000')
            identityBytes.fill(0)
        }
        val codec = JuntaTriPhaseCodec()

        val validRequest = request()
        val validData = codec.decodeRequest(validRequest, identity.chain)
        val validXml =
            "<xml frmt=\"CAdES\" op=\"FIRMAR\"><firmas><firma Id=\"one\"><param n=\"PRE\">cHJl</param></firma></firmas></xml>"
        val preSign = codec.parsePreResponse(
            validData,
            java.util.Base64.getUrlEncoder().encode(validXml.encodeToByteArray()),
        )
        preSign.withBytesToSign { assertTrue(it.contentEquals("pre".encodeToByteArray())) }
        preSign.close()
        validRequest.close()

        val blockedRequest = request()
        val blockedData = codec.decodeRequest(blockedRequest, identity.chain)
        val doctype =
            "<!DOCTYPE x [<!ENTITY e SYSTEM \"file:///system/build.prop\">]><xml><firmas><firma Id=\"one\"><param n=\"PRE\">cHJl</param></firma></firmas></xml>"
        val error = try {
            codec.parsePreResponse(
                blockedData,
                java.util.Base64.getUrlEncoder().encode(doctype.encodeToByteArray()),
            )
            error("DOCTYPE must be rejected")
        } catch (expected: TriPhaseCodecException) {
            expected
        }
        assertEquals(TriPhaseCodecError.RESPONSE_FORMAT_INVALID, error.code)
        blockedData.close()
        blockedRequest.close()

        val utf16Request = request()
        val utf16Data = codec.decodeRequest(utf16Request, identity.chain)
        val utf16Doctype =
            "<?xml version=\"1.0\" encoding=\"UTF-16\"?><!DOCTYPE x [<!ENTITY e \"expanded\">]><xml><firmas><firma Id=\"one\"><param n=\"PRE\">cHJl</param></firma></firmas></xml>"
                .toByteArray(Charsets.UTF_16LE)
        val utf16Error = try {
            codec.parsePreResponse(utf16Data, java.util.Base64.getUrlEncoder().encode(utf16Doctype))
            error("UTF-16 DOCTYPE must be rejected")
        } catch (expected: TriPhaseCodecException) {
            expected
        } finally {
            utf16Doctype.fill(0)
        }
        assertEquals(TriPhaseCodecError.RESPONSE_FORMAT_INVALID, utf16Error.code)
        utf16Data.close()
        utf16Request.close()
    }

    private fun request(): NormalizedSignRequest {
        val data = "synthetic-document".encodeToByteArray()
        val payload = MiniAppletPayloadCodec.encode(
            data,
            "serverUrl=${SafeNetworkUrlPolicy.JUNTA_TRIPHASE_ENDPOINT}\nmode=explicit\n",
        )
        data.fill(0)
        return NormalizedSignRequest(
            requestId = UUID.randomUUID(),
            protocolId = JuntaTriPhaseAdapter.ID,
            context = SigningContext(
                profileId = "junta-andalucia",
                profileVersion = 1,
                origin = TrustedOrigin("https", "www.juntadeandalucia.es", 443),
                navigationId = NavigationId(UUID.randomUUID().toString()),
                observedAt = Instant.now(),
            ),
            algorithm = SigningAlgorithm.SHA1_WITH_RSA,
            format = SigningFormat.CADES,
            safeDescription = "Synthetic authentication",
            payload = payload,
        )
    }

    private fun syntheticPkcs12(): ByteArray {
        val context = InstrumentationRegistry.getInstrumentation().context
        val encoded = context.assets.open("synthetic-identity.p12.b64")
            .bufferedReader()
            .use { it.readText() }
        return Base64.decode(encoded, Base64.DEFAULT)
    }

    private companion object {
        const val TEST_PASSPHRASE = "test-password-123"
    }
}

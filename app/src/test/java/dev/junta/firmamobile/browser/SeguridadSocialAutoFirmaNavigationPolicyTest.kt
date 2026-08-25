package dev.junta.firmamobile.browser

import dev.junta.firmamobile.profile.ProfileId
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
class SeguridadSocialAutoFirmaNavigationPolicyTest {
    private val policy = JuntaNavigationPolicy(ProfileId(PROFILE_ID))
    private val source =
        "https://sede.seg-social.gob.es/wps/myportal/sede/!ut/p/z1/portal-state/" +
            "?A=&N3=&idApp=826" +
            "&idContenido=a061f401-c3ed-426e-9428-82bd9198c223" +
            "&idPagina=com.ss.sede.RegistroElectronicoDeApoderamiento"

    @Test
    fun delegatesOnlyExactOfficialAutoFirmaSignIntentFromBoundSedessReturn() {
        val intent =
            "intent://sign?algorithm=SHA256withRSA&format=PAdES&fileid=opaque" +
                "&rtservlet=https%3A%2F%2Fexample.invalid%2Fretrieve" +
                "&stservlet=https%3A%2F%2Fexample.invalid%2Fstorage" +
                "#Intent;scheme=afirma;package=es.gob.afirma;end"

        val decision = policy.decide(intent, source) as NavigationDecision.OpenOfficialAutoFirma

        assertEquals("afirma", decision.uri.scheme)
        assertEquals("sign", decision.uri.host)
        assertEquals("opaque", decision.uri.getQueryParameter("fileid"))
        assertTrue(decision.uri.toString().startsWith("afirma://sign?"))
    }

    @Test
    fun delegatesDirectAfirmaSignUriToOfficialPackagePathWithoutOwningSignature() {
        val direct = "afirma://sign?algorithm=SHA256withRSA&format=CAdES&dat=YWJj"

        val decision = policy.decide(direct, source)

        assertTrue(decision is NavigationDecision.OpenOfficialAutoFirma)
        assertEquals(direct, (decision as NavigationDecision.OpenOfficialAutoFirma).uri.toString())
    }

    @Test
    fun rejectsWrongPackageFallbackAndNonSignOperations() {
        val wrongPackage = policy.decide(
            "intent://sign?algorithm=SHA256withRSA&format=CAdES&dat=YWJj" +
                "#Intent;scheme=afirma;package=dev.junta.firmamobile;end",
            source,
        ) as NavigationDecision.Block
        assertEquals(NavigationBlockReason.UNSUPPORTED_EXTERNAL_INTENT, wrongPackage.reason)

        val fallback = policy.decide(
            "intent://sign?algorithm=SHA256withRSA&format=CAdES&dat=YWJj" +
                "#Intent;scheme=afirma;package=es.gob.afirma;" +
                "S.browser_fallback_url=https%3A%2F%2Fplay.google.com%2Fstore;end",
            source,
        ) as NavigationDecision.Block
        assertEquals(NavigationBlockReason.UNSUPPORTED_EXTERNAL_INTENT, fallback.reason)

        val selectCert = policy.decide("afirma://selectcert?fileid=opaque", source)
            as NavigationDecision.Block
        assertEquals(NavigationBlockReason.INVALID_AFIRMA_URI, selectCert.reason)
    }

    @Test
    fun rejectsSourceNearMissesAndExtraReturnParameters() {
        val request = "afirma://sign?algorithm=SHA256withRSA&format=CAdES&dat=YWJj"
        val nearMisses = listOf(
            source.replace("idApp=826", "idApp=827"),
            source.replace("sede.seg-social.gob.es", "sede.seg-social.gob.es.evil.example"),
            source.replace("/wps/myportal/sede/", "/wps/portal/sede/"),
            "$source&extra=1",
            source.replace("?A=", "?A=x"),
            source.replace("&N3=", "&N3=x"),
        )

        nearMisses.forEach { page ->
            val blocked = policy.decide(request, page) as NavigationDecision.Block
            assertEquals(page, NavigationBlockReason.UNTRUSTED_AFIRMA_ORIGIN, blocked.reason)
        }
    }

    @Test
    fun rejectsMalformedOrAmbiguousAutoFirmaQueries() {
        val invalid = listOf(
            "afirma://sign?algorithm=SHA256withRSA&algorithm=SHA512withRSA&format=CAdES&dat=YWJj",
            "afirma://sign?format=CAdES&dat=YWJj",
            "afirma://sign?algorithm=SHA256withRSA&format=CAdES&fileid=one&fileid=two",
            "afirma://sign?algorithm=SHA256withRSA&format=CAdES&dat=%ZZ",
            "afirma://sign?algorithm=SHA256withRSA&format=CAdES&dat=YWJj&fileid=opaque",
            "afirma://sign?algorithm=SHA256withRSA&format=CAdES&fileid=opaque&rtservlet=http%3A%2F%2Fexample.invalid%2Fretrieve",
            "afirma://sign?algorithm=SHA256withRSA&format=CAdES&fileid=opaque&stservlet=https%3A%2F%2Fuser%40example.invalid%2Fstorage",
            "afirma://sign?algorithm=SHA256withRSA&format=CAdES&fileid=opaque&serverurl=https%3A%2F%2Fexample.invalid%3A8443%2Ftri",
            "afirma://sign?algorithm=SHA256withRSA&format=CAdES&dat=YWJj&returnurl=custom%3A%2F%2Fcallback",
            "afirma://sign?algorithm=SHA256withRSA&format=CAdES&dat=YWJj&unknown=1",
        )

        invalid.forEach { target ->
            val blocked = policy.decide(target, source) as NavigationDecision.Block
            assertEquals(target, NavigationBlockReason.INVALID_AFIRMA_URI, blocked.reason)
        }
    }

    private companion object {
        const val PROFILE_ID = "seguridad-social-sede-autofirma"
    }
}

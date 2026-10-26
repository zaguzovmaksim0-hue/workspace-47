package dev.junta.firmamobile.profile

import dev.junta.firmamobile.browser.ClientAuthNavigationAuthorizer
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Test

class LaRiojaTlsContractTest {
    @Test
    fun fixedCatalogCorpusPreservesTheReviewedContractAndRejectsMutations() {
        val profile = SiteProfileCatalogParser.parse(FIXTURE).profiles.single()
        assertEquals(PROFILE, profile.profileId)
        val mutations = listOf(
            "la-rioja-oficina-electronica" to "unreviewed-rioja",
            "https://ias1.larioja.org" to "https://foreign.example",
            "https://ias1.larioja.org" to "https://ias1.larioja.org.evil.example",
            "https://ias1.larioja.org" to "http://ias1.larioja.org",
            "https://ias1.larioja.org" to "https://ias1.larioja.org:444",
            "/casLR/login" to "/casLR/login/extra",
            "/clientcertSSL/login" to "/clientcertSSL/login/extra",
            "REDIRECT_AFTER_SOURCE" to "DIRECT_FROM_SOURCE",
            "\"requestPort\": 443" to "\"requestPort\": 444",
            "\"grantTtlSeconds\": 15" to "\"grantTtlSeconds\": 16",
            "\"allowEmptyIssuerList\": true" to "\"allowEmptyIssuerList\": false",
            "\"OFIVIR\"" to "\"OTHER\"",
            "\"TARGET\"" to "\"otherTarget\"",
            "\"fixedQueryParameters\": {}" to "\"fixedQueryParameters\": {\"extra\":\"value\"}",
        )
        mutations.forEach { (old, replacement) ->
            check(old in FIXTURE)
            assertThrows(old, IllegalArgumentException::class.java) {
                SiteProfileCatalogParser.parse(FIXTURE.replace(old, replacement))
            }
        }
    }

    @Test
    fun fixedNavigationCorpusRejectsForeignHostPortHttpPathAndQueryExpansion() {
        assertNotNull(authorize(SOURCE, TARGET))
        assertNotNull(authorize(SOURCE, TARGET.replace(".org/", ".org:443/")))
        val wrongSources = listOf(
            SOURCE.replace("ias1.larioja.org", "foreign.example"),
            SOURCE.replace("ias1.larioja.org", "ias1.larioja.org.evil.example"),
            SOURCE.replace("https:", "http:"),
            SOURCE.replace(".org/", ".org:444/"),
            SOURCE.replace("/login?", "/login/extra?"),
            SOURCE.replace("OFIVIR", "OTHER"),
            SOURCE + "&extra=value",
            SOURCE + "&param=duplicate",
        )
        wrongSources.forEach { assertNull(it, authorize(it, TARGET)) }
        val wrongTargets = listOf(
            TARGET.replace("ias1.larioja.org", "foreign.example"),
            TARGET.replace("ias1.larioja.org", "ias1.larioja.org.evil.example"),
            TARGET.replace("https:", "http:"),
            TARGET.replace(".org/", ".org:444/"),
            TARGET + "/extra",
            TARGET + "?extra=value",
            TARGET + "#fragment",
        )
        wrongTargets.forEach { assertNull(it, authorize(SOURCE, it)) }
    }

    @Test
    fun navigationBindingTtlOneShotAndReleaseEligibilityStayUnchanged() {
        val catalog = SiteProfileCatalogParser.parse(FIXTURE)
        assertNull(SiteProfileRegistry(catalog, BuildTrustPolicy.RELEASE).profile(PROFILE))
        var now = 0L
        val registry = SiteProfileRegistry(catalog, BuildTrustPolicy.QA)
        val authorizer = ClientAuthNavigationAuthorizer(registry) { now }
        assertNull(authorizer.observeTopLevelNavigation(PROFILE, START, TARGET, 3, true))
        assertNull(authorizer.observeTopLevelNavigation(PROFILE, START, SOURCE, 3, true))
        assertNull(authorizer.observeTopLevelNavigation(PROFILE, SOURCE, TARGET, 3, false))
        assertNull(authorizer.observeTopLevelNavigation(PROFILE, SOURCE, TARGET, 3, true))
        assertNull(authorizer.observeTopLevelNavigation(PROFILE, START, SOURCE, 3, true))
        now = 16_000_000_000L
        assertNull(authorizer.observeTopLevelNavigation(PROFILE, SOURCE, TARGET, 4, true))
        assertNull(authorizer.observeTopLevelNavigation(PROFILE, START, SOURCE, 5, true))
        assertNull(authorizer.observeTopLevelNavigation(PROFILE, SOURCE, TARGET, 7, true))
        assertNull(authorizer.observeTopLevelNavigation(PROFILE, START, SOURCE, 8, true))
        assertNull(authorizer.observeTopLevelNavigation(ProfileId("other-profile"), SOURCE, TARGET, 8, true))
        assertNull(authorizer.observeTopLevelNavigation(PROFILE, START, SOURCE, 9, true))
        val accepted = authorizer.observeTopLevelNavigation(PROFILE, SOURCE, TARGET, 10, true)
        assertNotNull(accepted)
        assertEquals(PROFILE, accepted?.profileId)
        assertNull(authorizer.observeTopLevelNavigation(PROFILE, SOURCE, TARGET, 10, true))
    }

    private fun authorize(source: String, target: String): Any? {
        val catalog = SiteProfileCatalogParser.parse(FIXTURE)
        val authorizer = ClientAuthNavigationAuthorizer(SiteProfileRegistry(catalog, BuildTrustPolicy.QA)) { 0L }
        assertNull(authorizer.observeTopLevelNavigation(PROFILE, START, source, 3, true))
        return authorizer.observeTopLevelNavigation(PROFILE, source, target, 4, true)
    }

    private companion object {
        val PROFILE = ProfileId("la-rioja-oficina-electronica")
        const val START = "https://ias1.larioja.org/oficinavirtual/presentacion?act_codi=24697"
        const val SOURCE = "https://ias1.larioja.org/casLR/login?inst=G&apli=OFIVIR&nodo=CIUDANO&param=opaque&TARGET=opaque"
        const val TARGET = "https://ias1.larioja.org/clientcertSSL/login"
        val FIXTURE = """
            {
              "schemaVersion": 1,
              "catalogVersion": 1,
              "profiles": [
                {
                  "profileId": "la-rioja-oficina-electronica",
                  "profileVersion": 1,
                  "displayName": "Gobierno de La Rioja — Oficina electrónica",
                  "compatibilityStatus": "VERIFIED_CONTRACT",
                  "activation": "QA_ONLY",
                  "startUrl": "https://ias1.larioja.org/oficinavirtual/presentacion?act_codi=24697",
                  "initiatorOrigins": [
                    "https://ias1.larioja.org"
                  ],
                  "redirectOrigins": [],
                  "trustedBrowseOrigins": [],
                  "endpoints": [],
                  "operationPolicies": [],
                  "capabilities": [
                    "CLIENT_TLS_AUTH"
                  ],
                  "clientAuthPolicy": {
                    "transitionMode": "REDIRECT_AFTER_SOURCE",
                    "requestOrigins": [
                      "https://ias1.larioja.org"
                    ],
                    "sourceUrls": [
                      "https://ias1.larioja.org/casLR/login"
                    ],
                    "sourceFixedQueryParameters": {
                      "inst": "G",
                      "apli": "OFIVIR",
                      "nodo": "CIUDANO"
                    },
                    "sourceRequiredEphemeralQueryParameters": [
                      "param",
                      "TARGET"
                    ],
                    "linkedEphemeralQueryParameters": [],
                    "requestPath": "/clientcertSSL/login",
                    "fixedQueryParameters": {},
                    "requiredEphemeralQueryParameters": [],
                    "allowEmptyIssuerList": true,
                    "grantTtlSeconds": 15,
                    "requestPort": 443
                  },
                  "certificateRules": {
                    "allowedKeyAlgorithms": [
                      "RSA",
                      "EC"
                    ],
                    "requireDigitalSignatureKeyUsage": false
                  },
                  "evidence": [
                    {
                      "url": "https://web.larioja.org/oficina-electronica/tramite?n=24697",
                      "reviewedOn": "2026-08-18"
                    },
                    {
                      "url": "https://ias1.larioja.org/oficinavirtual/presentacion?act_codi=24697",
                      "reviewedOn": "2026-08-18"
                    },
                    {
                      "url": "https://ias1.larioja.org/casLR/login",
                      "reviewedOn": "2026-08-18"
                    },
                    {
                      "url": "https://ias1.larioja.org/clientcertSSL/login",
                      "reviewedOn": "2026-08-18"
                    }
                  ]
                }
              ]
            }
        """.trimIndent()
    }
}

package dev.junta.firmamobile.profile

import dev.junta.firmamobile.signing.LocalCadesDetachedAdapter
import org.json.JSONObject
import java.time.LocalDate
import java.time.ZoneOffset
import java.net.URI
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertThrows
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
class SiteProfileCatalogParserTest {

    @Test
    fun dgojClavePinRejectsUnreviewedRoutesEvidenceAndWeakenedRules() {
        val mutations = listOf<(JSONObject) -> Unit>(
            { it.put("profileVersion", 1) },
            { it.put("activation", "ENABLED") },
            { it.put("startUrl", it.getString("startUrl").replace("iA=no", "iA=si")) },
            { it.put("startUrl", it.getString("startUrl").replace("limpiarBusqueda=S", "limpiarBusqueda=N")) },
            { it.getJSONArray("initiatorOrigins").put(0, "https://foreign.example") },
            { it.getJSONArray("redirectOrigins").put("https://pasarela-ident.clave.gob.es") },
            { it.getJSONArray("trustedBrowseOrigins").put("https://foreign.example") },
            { it.getJSONArray("capabilities").put("SIGN") },
            { it.getJSONArray("capabilities").put("AFIRMA_URI") },
            { it.getJSONArray("capabilities").put("SELECT_CERTIFICATE") },
            { it.put("shimFeatures", org.json.JSONArray().put("universal-afirma")) },
            { it.getJSONObject("certificateRules").put("requireDigitalSignatureKeyUsage", false) },
            { it.getJSONObject("certificateRules").put("allowedKeyAlgorithms", org.json.JSONArray().put("RSA")) },
            { it.getJSONObject("clientAuthPolicy").put("transitionMode", "DIRECT_FROM_SOURCE") },
            { it.getJSONObject("clientAuthPolicy").getJSONArray("sourceUrls").put(0,
                "https://pasarela.clave.gob.es/Proxy2/ServiceProvider") },
            { it.getJSONObject("clientAuthPolicy").getJSONArray("requestOrigins").put(0,
                "https://foreign.example") },
            { it.getJSONObject("clientAuthPolicy").put("requestPath", "/IdP2/other") },
            { it.getJSONObject("clientAuthPolicy").put("requestMethod", "GET") },
            { it.getJSONObject("clientAuthPolicy").put("requestPort", 444) },
            { it.getJSONObject("clientAuthPolicy").put("grantTtlSeconds", 30) },
            { it.getJSONObject("clientAuthPolicy").put("allowEmptyIssuerList", false) },
            { it.getJSONObject("clientAuthPolicy").put("requireOfferedKeyTypeMatch", false) },
            { it.getJSONObject("clientAuthPolicy").put("requireTlsClientAuthExtendedKeyUsage", false) },
            { it.getJSONObject("clientAuthPolicy").getJSONObject("fixedQueryParameters").put("extra", "1") },
            { it.getJSONObject("clientAuthPolicy").getJSONObject("sourceFixedQueryParameters").put("extra", "1") },
            { it.getJSONObject("clientAuthPolicy").getJSONArray("sourceRequiredEphemeralQueryParameters").put("s") },
            { it.getJSONObject("clientAuthPolicy").getJSONArray("requiredEphemeralQueryParameters").put("s") },
            { it.getJSONArray("evidence").getJSONObject(0).put("reviewedOn", "2026-11-08") },
            { it.getJSONArray("evidence").getJSONObject(6).put("reviewedOn", "2026-11-07") },
            { it.getJSONArray("evidence").getJSONObject(7).put("reviewedOn", "2026-11-07") },
            { it.getJSONArray("evidence").put(it.getJSONArray("evidence").getJSONObject(0)) },
        )
        mutations.forEachIndexed { index, mutate ->
            val root = JSONObject(BuiltInSiteProfiles.JSON)
            mutate(schemaV2Profile(root, "dgoj-public-navigation"))
            assertThrows("DGOJ mutation $index", IllegalArgumentException::class.java) {
                SiteProfileCatalogParser.parse(root.toString())
            }
        }
    }

    @Test
    fun teaSelectorPinRejectsUnreviewedRoutesEvidenceAndWeakenedCertificateRules() {
        val mutations = listOf<(JSONObject) -> Unit>(
            { it.put("profileVersion", 2) },
            { it.put("activation", "ENABLED") },
            { it.put("startUrl", "https://sede.tea.hacienda.gob.es/TEA/alegaciones.html") },
            { it.put("capabilities", org.json.JSONArray().put("CLIENT_TLS_AUTH").put("SIGN")) },
            { it.getJSONObject("certificateRules").put("requireDigitalSignatureKeyUsage", false) },
            { it.getJSONObject("certificateRules").put("allowedKeyAlgorithms", org.json.JSONArray().put("RSA")) },
            { it.getJSONObject("clientAuthPolicy").put("requestPath", "/wlpl/TEAC-TRAM/SedeTRAM") },
            { it.getJSONObject("clientAuthPolicy").put("requestMethod", "POST") },
            { it.getJSONObject("clientAuthPolicy").put("requestPort", 8443) },
            { it.getJSONObject("clientAuthPolicy").put("grantTtlSeconds", 30) },
            { it.getJSONObject("clientAuthPolicy").put("allowEmptyIssuerList", true) },
            { it.getJSONObject("clientAuthPolicy").put("requireOfferedKeyTypeMatch", false) },
            { it.getJSONObject("clientAuthPolicy").put("requireTlsClientAuthExtendedKeyUsage", false) },
            { it.getJSONObject("clientAuthPolicy").put("requestOrigins",
                org.json.JSONArray().put("https://www6.tea.hacienda.gob.es")) },
            { it.getJSONObject("clientAuthPolicy").put("requestOrigins",
                org.json.JSONArray().put("https://www11.tea.hacienda.gob.es")) },
            { it.getJSONObject("clientAuthPolicy").getJSONObject("fixedQueryParameters")
                .put("ref", "/wlpl/TEAC-TRAM/SedeTRAM?tram=2") },
            { it.getJSONObject("clientAuthPolicy").getJSONObject("sourceFixedQueryParameters").put("rep", "S") },
            { it.getJSONArray("evidence").getJSONObject(0).put("reviewedOn", "2026-11-08") },
            { it.getJSONArray("evidence").getJSONObject(2).put("reviewedOn", "2026-08-14") },
            { it.getJSONArray("evidence").put(it.getJSONArray("evidence").getJSONObject(0)) },
        )
        val sourceMutations = listOf(
            "rep=S" to "rep=N", "aut=CPE" to "aut=PE", "tram%3D0" to "tram%3D2",
            "sede.tea.hacienda.gob.es" to "foreign.example",
        ).map { (before, after) ->
            { profile: JSONObject ->
                val policy = profile.getJSONObject("clientAuthPolicy")
                policy.put("sourceUrls", org.json.JSONArray().put(
                    policy.getJSONArray("sourceUrls").getString(0).replace(before, after),
                ))
            }
        }
        (mutations + sourceMutations).forEachIndexed { index, mutate ->
            val root = JSONObject(BuiltInSiteProfiles.JSON)
            mutate(schemaV2Profile(root, "tea-alegaciones-certificado"))
            assertThrows("mutation $index", IllegalArgumentException::class.java) {
                SiteProfileCatalogParser.parse(root.toString())
            }
        }
    }

    private val waveAProfiles = listOf(
        "cnmv-sede-public-home", "csd-sede-public", "policia-solicitud-generica",
        "catastro-solicitudes-genericas", "ministerio-economia-instancia-generica",
        "age-portal-de-la-transparencia", "red-es-sede-public-navigation", "diputacion-almeria-solicitud-general",
        "diputacion-cordoba-solicitud-generica", "diputacion-palencia-solicitud-general", "educacion-convocatoria",
        "extremadura-tramites", "fuerteventura-sede-electronica", "justicia-sede-judicial-private-area",
        "madrid-sede-tarjeta-azul", "mjusticia-fundaciones-idp75",
    )

    private val waveCProfiles = listOf(
        "diputacion-segovia-registro", "el-hierro-solicitud-general", "la-gomera-instancia-general",
        "zamora-sede-public-navigation", "zaragoza-sede-public-navigation", "el-hierro-sede-public-navigation",
        "la-gomera-sede-public-navigation", "lanzarote-sede-public-navigation", "isfas-sede-public-navigation",
        "diputacion-pontevedra-instancia-xenerica",
    )

    @Test
    fun reviewedClaveProfilesPinOnlyTheReviewedPostAndKeepReleaseDisabled() {
        (waveAProfiles + waveCProfiles).forEach { id ->
            val profileId = ProfileId(id)
            val profile = BuiltInSiteProfiles.catalog.profiles.single { it.profileId == profileId }
            val source = if (id == "madrid-sede-tarjeta-azul") "ServiceProvider" else "ServiceRedirect"
            assertEquals(if (id == "educacion-convocatoria") 3 else 2, profile.profileVersion)
            assertEquals(ProfileActivation.QA_ONLY, profile.activation)
            assertEquals(CompatibilityStatus.VERIFIED_CONTRACT, profile.compatibilityStatus)
            assertEquals(
                ClientAuthPolicy(
                    transitionMode = ClientAuthTransitionMode.IN_PLACE_FROM_SOURCE,
                    requestOrigins = setOf(ExactOrigin.parse("https://pasarela-ident.clave.gob.es")),
                    sourceUrls = setOf(URI("https://pasarela.clave.gob.es/Proxy2/$source")),
                    requestPath = "/IdP2/AuthenticateCitizen",
                    fixedQueryParameters = emptyMap(),
                    requiredEphemeralQueryParameters = emptySet(),
                    allowEmptyIssuerList = true,
                    grantTtlSeconds = 15,
                    requestPort = 443,
                    requestMethod = HttpMethod.POST,
                ),
                profile.clientAuthPolicy,
            )
            assertTrue(profile.certificateRules.requireDigitalSignatureKeyUsage)
            assertNull(BuiltInSiteProfiles.releaseRegistry.profile(profileId))
            assertEquals(
                TrustMode.TRUSTED_BROWSE,
                BuiltInSiteProfiles.qaRegistry.resolveForProfile(
                    profileId, URI("https://pasarela.clave.gob.es/Proxy2/$source"),
                )?.trustMode,
            )
            assertEquals(
                TrustMode.TRUSTED_CLIENT_AUTH,
                BuiltInSiteProfiles.qaRegistry.resolveForProfile(
                    profileId, URI("https://pasarela-ident.clave.gob.es/IdP2/AuthenticateCitizen"),
                )?.trustMode,
            )
        }
    }

    @Test
    fun reviewedClaveClientAuthPinsRejectAnUnreviewedRequestPath() {
        (waveAProfiles + waveCProfiles).forEach { id ->
            val root = JSONObject(BuiltInSiteProfiles.JSON)
            schemaV2Profile(root, id).getJSONObject("clientAuthPolicy").put("requestPath", "/unreviewed")
            assertThrows(id, IllegalArgumentException::class.java) {
                SiteProfileCatalogParser.parse(root.toString())
            }
        }
    }

    @Test
    fun schemaV2AcceptsRequiredEmptyFeatures() {
        val parsed = SiteProfileCatalogParser.parse(schemaV2Fixture().toString())
        assertEquals(2, parsed.schemaVersion)
        assertEquals(BuiltInSiteProfiles.catalog.profiles.map { it.profileId }, parsed.profiles.map { it.profileId })
    }

    @Test
    fun schemaV2AcceptsReviewedFeatureOnItsProfile() {
        val root = schemaV2Fixture()
        schemaV2Profile(root, "ugr-certificado-login").put("shimFeatures", org.json.JSONArray().put("ugr-login"))
        assertEquals(2, SiteProfileCatalogParser.parse(root.toString()).schemaVersion)
    }

    @Test
    fun schemaV2RequiresFeaturesOnEveryProfile() {
        val root = schemaV2Fixture()
        val profiles = root.getJSONArray("profiles")
        for (index in 0 until profiles.length()) {
            profiles.getJSONObject(index).remove("shimFeatures")
            assertThrows(RuntimeException::class.java) { SiteProfileCatalogParser.parse(root.toString()) }
            profiles.getJSONObject(index).put("shimFeatures", org.json.JSONArray())
        }
    }

    @Test
    fun schemaV2RejectsUnknownDuplicateAndNonArrayFeatures() {
        val invalid = listOf<Any>(
            org.json.JSONArray().put("unknown"),
            org.json.JSONArray().put("ugr-login").put("ugr-login"),
            org.json.JSONArray().put("https://example.org/shim.js"),
            org.json.JSONArray().put("../shim.js"),
            org.json.JSONArray().put(".*"),
            org.json.JSONArray().put("eval('code')"),
            org.json.JSONArray().put(1), org.json.JSONArray().put(JSONObject.NULL),
            "ugr-login", JSONObject.NULL, JSONObject(),
        )
        for (value in invalid) {
            val root = schemaV2Fixture()
            schemaV2Profile(root, "ugr-certificado-login").put("shimFeatures", value)
            assertThrows(RuntimeException::class.java) { SiteProfileCatalogParser.parse(root.toString()) }
        }
    }

    @Test
    fun schemaV2RejectsExtraAndDuplicateKeysAndForeignFeatures() {
        val extra = schemaV2Fixture()
        schemaV2Profile(extra, "ugr-certificado-login").put("shimUrl", "https://example.org/shim.js")
        assertThrows(RuntimeException::class.java) { SiteProfileCatalogParser.parse(extra.toString()) }
        val duplicate = schemaV2Fixture().toString().replaceFirst(
            "\"shimFeatures\":[]", "\"shimFeatures\":[],\"shimFeatures\":[]",
        )
        assertThrows(RuntimeException::class.java) { SiteProfileCatalogParser.parse(duplicate) }
        val foreign = schemaV2Fixture()
        schemaV2Profile(foreign, "cantabria-rec-cert-login").put(
            "shimFeatures", org.json.JSONArray().put("ugr-login"),
        )
        assertThrows(RuntimeException::class.java) { SiteProfileCatalogParser.parse(foreign.toString()) }
        val batch = schemaV2Fixture()
        schemaV2Profile(batch, "melilla-sede").put(
            "shimFeatures", org.json.JSONArray().put("sta-json-batch").put("lugo-xml-batch"),
        )
        assertThrows(RuntimeException::class.java) { SiteProfileCatalogParser.parse(batch.toString()) }
    }

    @Test
    fun schemaV1StillRejectsShimFeaturesAndUnsupportedVersions() {
        val root = JSONObject(BuiltInSiteProfiles.JSON)
        root.getJSONArray("profiles").getJSONObject(0).put("shimFeatures", org.json.JSONArray())
        assertThrows(RuntimeException::class.java) { SiteProfileCatalogParser.parse(root.toString()) }
        for (version in listOf(0, 3, -1)) {
            val unsupported = schemaV2Fixture().put("schemaVersion", version)
            assertThrows(RuntimeException::class.java) { SiteProfileCatalogParser.parse(unsupported.toString()) }
        }
    }

    @Test
    fun schemaV2RetainsExactOriginAndAdapterPins() {
        for (key in listOf("initiatorOrigins", "operationPolicies")) {
            val root = schemaV2Fixture()
            val profile = schemaV2Profile(root, "ugr-certificado-login")
            profile.put("shimFeatures", org.json.JSONArray().put("ugr-login"))
            if (key == "initiatorOrigins") {
                profile.put(key, org.json.JSONArray().put("https://sede.ugr.es.evil.example"))
            } else {
                profile.getJSONArray(key).getJSONObject(0).put("inputAdapterId", "autoscript-select-certificate-v1")
            }
            assertThrows(RuntimeException::class.java) { SiteProfileCatalogParser.parse(root.toString()) }
        }
    }

    private fun schemaV2Fixture(): JSONObject {
        val root = JSONObject(BuiltInSiteProfiles.JSON).put("schemaVersion", 2)
        val profiles = root.getJSONArray("profiles")
        for (index in 0 until profiles.length()) profiles.getJSONObject(index).put("shimFeatures", org.json.JSONArray())
        return root
    }

    private fun schemaV2Profile(root: JSONObject, id: String): JSONObject {
        val profiles = root.getJSONArray("profiles")
        return (0 until profiles.length()).map { profiles.getJSONObject(it) }.single { it.getString("profileId") == id }
    }

    @Test
    fun escapedSchemaKeyAndJsonWhitespacePreserveTheCatalog() {
        val json = BuiltInSiteProfiles.JSON
        assertTrue(json.contains("\"schemaVersion\""))
        val escaped = json.replaceFirst("\"schemaVersion\"", "\"schema\\u0056ersion\"")
        assertEquals(
            BuiltInSiteProfiles.catalog,
            SiteProfileCatalogParser.parse(" \t\r\n$escaped\n\r\t "),
        )
    }

    @Test
    fun malformedUnicodeEscapesAndRawControlCharactersFailClosed() {
        val invalidKeys = listOf(
            "schema\\u00" to IllegalArgumentException::class.java,
            "schema\\u00xzersion" to IllegalArgumentException::class.java,
            "schema\\qVersion" to IllegalStateException::class.java,
            "schema${0x1f.toChar()}Version" to IllegalStateException::class.java,
        )
        for ((key, errorType) in invalidKeys) {
            val json = BuiltInSiteProfiles.JSON.replaceFirst("\"schemaVersion\"", "\"$key\"")
            assertThrows("Key must be rejected: $key", errorType) {
                SiteProfileCatalogParser.parse(json)
            }
        }
    }

    @Test
    fun lastVerifiedOnPreservesDocumentedE2EDates() {
        val expected = mapOf(
            "unizar-tramitador" to "2026-07-30",
            "carne-joven-andalucia" to "2026-07-21",
            "junta-ofvirtual" to "2026-07-29",
            "aragon-siraw" to "2026-07-28",
        )
        val actual = SiteProfileCatalogParser.parse(BuiltInSiteProfiles.JSON).profiles
            .filter { it.lastVerifiedOn != null }
            .associate { it.profileId.value to it.lastVerifiedOn.toString() }
        assertEquals(expected, actual)
    }

    @Test
    fun lastVerifiedOnIsOptionalAndAcceptsLeapDayAndToday() {
        val withoutDates = JSONObject(BuiltInSiteProfiles.JSON)
        val profiles = withoutDates.getJSONArray("profiles")
        for (index in 0 until profiles.length()) profiles.getJSONObject(index).remove("lastVerifiedOn")
        assertTrue(SiteProfileCatalogParser.parse(withoutDates.toString()).profiles.all { it.lastVerifiedOn == null })
        for (date in listOf("2024-02-29", LocalDate.now(ZoneOffset.UTC).toString())) {
            val json = profileDateJson("unizar-tramitador", date)
            val profile = SiteProfileCatalogParser.parse(json).profiles.single { it.profileId.value == "unizar-tramitador" }
            assertEquals(LocalDate.parse(date), profile.lastVerifiedOn)
        }
    }

    @Test
    fun lastVerifiedOnRejectsInvalidTypesDatesAndFuture() {
        val tomorrow = LocalDate.now(ZoneOffset.UTC).plusDays(1).toString()
        for (date in listOf(JSONObject.NULL, 42, "", "2026-7-21", "2026-02-30", "20260721",
            "2026-07-21T00:00:00Z", " 2026-07-21", "0000-01-01", tomorrow)) {
            assertThrows(RuntimeException::class.java) {
                SiteProfileCatalogParser.parse(profileDateJson("unizar-tramitador", date))
            }
        }
    }

    @Test
    fun collectionSeparatorsAndTrailingInputRemainStrict() {
        val json = """{"schemaVersion":1,"catalogVersion":1,"profiles":[]}"""
        val catalog = SiteProfileCatalogParser.parse(json)
        assertTrue(catalog.profiles.isEmpty())
        val invalid = listOf(
            json.replace("[]", "[,]") to IllegalStateException::class.java,
            json.replace("[]", "[{},]") to IllegalStateException::class.java,
            json.replace("1,\"catalogVersion\"", "1 \"catalogVersion\"") to IllegalArgumentException::class.java,
            (json.dropLast(1) + ",}") to IllegalArgumentException::class.java,
            "$json false" to IllegalArgumentException::class.java,
        )
        for ((candidate, errorType) in invalid) {
            assertThrows(errorType) {
                SiteProfileCatalogParser.parse(candidate)
            }
        }
    }

    @Test
    fun lastVerifiedOnRejectsNonE2EProfiles() {
        for (id in listOf("junta-andalucia", "reg-age-redsara")) {
            assertThrows(RuntimeException::class.java) {
                SiteProfileCatalogParser.parse(profileDateJson(id, "2026-07-21"))
            }
        }
    }

    private fun profileDateJson(profileId: String, value: Any): String {
        val root = JSONObject(BuiltInSiteProfiles.JSON)
        val profiles = root.getJSONArray("profiles")
        for (index in 0 until profiles.length()) {
            val profile = profiles.getJSONObject(index)
            if (profile.getString("profileId") == profileId) profile.put("lastVerifiedOn", value)
        }
        return root.toString()
    }

    @Test
    fun generatedResourceMatchesTheCompiledCatalog() {
        val resourceJson = SiteProfileCatalogResource.read()
        assertEquals(BuiltInSiteProfiles.JSON, resourceJson)
        assertEquals(
            BuiltInSiteProfiles.catalog,
            SiteProfileCatalogParser.parse(resourceJson),
        )
    }

    @Test
    fun preservesTheJuntaGoldenContract() {
        val profile = BuiltInSiteProfiles.catalog.profiles.single {
            it.profileId == ProfileId("junta-andalucia")
        }
        assertEquals(ProfileId("junta-andalucia"), profile.profileId)
        assertEquals(CompatibilityStatus.EXPERIMENTAL, profile.compatibilityStatus)
        assertEquals(ProfileActivation.ENABLED, profile.activation)
        assertEquals(
            "https://www.juntadeandalucia.es/empleoformacionytrabajoautonomo/ovorion/auth/signInAutcertjs",
            profile.startUrl.toString(),
        )
        assertEquals(
            setOf(
                "www.juntadeandalucia.es", "sede.juntadeandalucia.es",
                "ssoweb.juntadeandalucia.es", "pfirma.juntadeandalucia.es",
                "ws024.juntadeandalucia.es", "ws050.juntadeandalucia.es",
            ),
            (profile.initiatorOrigins + profile.redirectOrigins + profile.trustedBrowseOrigins)
                .mapTo(linkedSetOf()) { it.host },
        )
        val operation = profile.operationPolicies.getValue(ProtocolOperation.SIGN)
        assertEquals("Autenticación con certificado", operation.safeDescription)
        assertEquals(setOf(SignatureAlgorithm.SHA1_WITH_RSA, SignatureAlgorithm.SHA256_WITH_RSA), operation.algorithms)
        assertEquals(SignatureFormat.CADES, operation.format)
        assertEquals(SignaturePackaging.DETACHED, operation.packaging)
        assertEquals(SignatureMode.EXPLICIT, operation.mode)
        assertEquals(ProtocolInputAdapterId("miniapplet-autoscript-v1"), operation.inputAdapterId)
        assertEquals(CallbackContractId("miniapplet-sign-callback-v1"), operation.callbackContractId)
        assertEquals(emptySet<String>(), operation.allowedExtraProperties)
        assertEquals(
            "https://ws024.juntadeandalucia.es/afirma-validator-miniapplet-1_4/sign/TriPhaseSignatureService",
            operation.fixedExtraProperties.getValue("serverUrl"),
        )
        assertEquals("keyusage.digitalsignature:true;nonexpired:", operation.fixedExtraProperties.getValue("filters"))
        assertEquals(
            "https://ws024.juntadeandalucia.es/afirma-validator-miniapplet-1_4/sign/TriPhaseSignatureService",
            profile.endpoints.getValue(EndpointId("junta-triphase")).url.toString(),
        )
    }

    @Test
    fun preservesTheExactRegAgeRedSaraContract() {
        val profile = BuiltInSiteProfiles.catalog.profiles.single {
            it.profileId == ProfileId("reg-age-redsara")
        }
        assertEquals(CompatibilityStatus.VERIFIED_CONTRACT, profile.compatibilityStatus)
        assertEquals(ProfileActivation.QA_ONLY, profile.activation)
        assertEquals("https://reg.redsara.es/es/", profile.startUrl.toString())
        assertEquals(setOf("https://reg.redsara.es"), profile.initiatorOrigins.mapTo(linkedSetOf()) { it.serialized })
        // v2: the REC signature is the filing, so only the Cl@ve certificate login is armed.
        assertEquals(2, profile.profileVersion)
        assertEquals(setOf(ExactOrigin.parse("https://pasarela.clave.gob.es")), profile.redirectOrigins)
        assertEquals(setOf(Capability.SIGN, Capability.CLIENT_TLS_AUTH), profile.capabilities)
        val policy = checkNotNull(profile.clientAuthPolicy)
        assertEquals(ClientAuthTransitionMode.IN_PLACE_FROM_SOURCE, policy.transitionMode)
        assertEquals(HttpMethod.POST, policy.requestMethod)
        assertEquals(setOf(ExactOrigin.parse("https://pasarela-ident.clave.gob.es")), policy.requestOrigins)
        assertEquals(setOf(URI("https://pasarela.clave.gob.es/Proxy2/ServiceRedirect")), policy.sourceUrls)
        assertEquals("/IdP2/AuthenticateCitizen", policy.requestPath)
        assertEquals(443, policy.requestPort)
        assertEquals(15, policy.grantTtlSeconds)
        assertTrue(profile.trustedBrowseOrigins.isEmpty())
        assertTrue(profile.endpoints.isEmpty())
        val operation = profile.operationPolicies.getValue(ProtocolOperation.SIGN)
        assertEquals(setOf(SignatureAlgorithm.SHA512_WITH_RSA), operation.algorithms)
        assertEquals(SignatureFormat.XADES, operation.format)
        assertEquals(SignaturePackaging.DETACHED, operation.packaging)
        assertNull(operation.mode)
        assertNull(operation.endpointId)
        assertTrue(operation.fixedExtraProperties.isEmpty())
        assertEquals(CallbackContractId("autoscript-sign-callback-v1"), operation.callbackContractId)
    }

    @Test
    fun preservesTheExactMelillaQaOnlyBatchContract() {
        val profileId = ProfileId("melilla-sede")
        val profile = BuiltInSiteProfiles.catalog.profiles.single { it.profileId == profileId }

        assertEquals(1, profile.profileVersion)
        assertEquals("Ciudad Autónoma de Melilla — Sede Electrónica", profile.displayName)
        assertEquals(CompatibilityStatus.VERIFIED_CONTRACT, profile.compatibilityStatus)
        assertEquals(ProfileActivation.QA_ONLY, profile.activation)
        assertEquals(
            URI(
                "https://sede.melilla.es/sta/CarpetaPublic/doEvent?" +
                    "APP_CODE=STA&PAGE_CODE=CATALOGO&DETALLE=6269000018479610199999",
            ),
            profile.startUrl,
        )
        assertEquals(
            setOf(ExactOrigin.parse("https://sede.melilla.es")),
            profile.initiatorOrigins,
        )
        assertTrue(profile.redirectOrigins.isEmpty())
        assertTrue(profile.trustedBrowseOrigins.isEmpty())
        assertTrue(profile.endpoints.isEmpty())
        assertEquals(setOf(Capability.SIGN), profile.capabilities)
        assertNull(profile.clientAuthPolicy)
        assertEquals(setOf("RSA"), profile.certificateRules.allowedKeyAlgorithms)
        assertTrue(profile.certificateRules.requireDigitalSignatureKeyUsage)
        assertTrue(profile.evidence.isNotEmpty())

        val operation = profile.operationPolicies.getValue(ProtocolOperation.SIGN)
        assertEquals(
            ProtocolInputAdapterId("melilla-batch-autoscript-v1"),
            operation.inputAdapterId,
        )
        assertEquals(
            CallbackContractId("melilla-batch-result-v1"),
            operation.callbackContractId,
        )
        assertEquals("Firma por lotes en la Sede Electrónica de Melilla", operation.safeDescription)
        assertEquals(setOf(Capability.SIGN), operation.capabilities)
        assertNull(operation.endpointId)
        assertEquals(setOf(SignatureAlgorithm.SHA256_WITH_RSA), operation.algorithms)
        assertEquals(SignatureFormat.CADES, operation.format)
        assertEquals(SignaturePackaging.DETACHED, operation.packaging)
        assertNull(operation.mode)
        assertTrue(operation.fixedExtraProperties.isEmpty())
        assertTrue(operation.allowedExtraProperties.isEmpty())

        assertNull(BuiltInSiteProfiles.releaseRegistry.profile(profileId))
        assertEquals(profile, BuiltInSiteProfiles.qaRegistry.profile(profileId))
        assertEquals(
            TrustMode.TRUSTED_SIGNING,
            BuiltInSiteProfiles.qaRegistry.resolve(profile.startUrl)?.trustMode,
        )
        assertNull(BuiltInSiteProfiles.releaseRegistry.resolve(profile.startUrl))
        assertNull(
            BuiltInSiteProfiles.qaRegistry.resolve(URI("https://sede.melilla.es.evil.example/")),
        )
        assertNull(
            BuiltInSiteProfiles.qaRegistry.resolve(URI("https://sede.melilla.es:444/")),
        )
    }

    @Test
    fun preservesTheExactUgrQaOnlyCertificateContract() {
        val profileId = ProfileId("ugr-certificado-login")
        val profile = BuiltInSiteProfiles.catalog.profiles.single { it.profileId == profileId }

        assertEquals(1, profile.profileVersion)
        assertEquals(CompatibilityStatus.VERIFIED_CONTRACT, profile.compatibilityStatus)
        assertEquals(ProfileActivation.QA_ONLY, profile.activation)
        assertEquals(
            URI("https://sede.ugr.es/Hades/jsp/pantallacertificado.jsp"),
            profile.startUrl,
        )
        assertEquals(
            setOf(ExactOrigin.parse("https://sede.ugr.es")),
            profile.initiatorOrigins,
        )
        assertTrue(profile.redirectOrigins.isEmpty())
        assertTrue(profile.trustedBrowseOrigins.isEmpty())
        assertTrue(profile.endpoints.isEmpty())
        assertEquals(setOf(Capability.SIGN, Capability.LEGACY_SHA1), profile.capabilities)

        val operation = profile.operationPolicies.getValue(ProtocolOperation.SIGN)
        assertEquals(
            ProtocolInputAdapterId("miniapplet-autoscript-v1"),
            operation.inputAdapterId,
        )
        assertEquals(
            CallbackContractId("miniapplet-sign-callback-v1"),
            operation.callbackContractId,
        )
        assertEquals(setOf(Capability.SIGN, Capability.LEGACY_SHA1), operation.capabilities)
        assertNull(operation.endpointId)
        assertEquals(setOf(SignatureAlgorithm.SHA1_WITH_RSA), operation.algorithms)
        assertEquals(SignatureFormat.CADES, operation.format)
        assertEquals(SignaturePackaging.DETACHED, operation.packaging)
        assertEquals(SignatureMode.EXPLICIT, operation.mode)
        assertTrue(operation.fixedExtraProperties.isEmpty())
        assertTrue(operation.allowedExtraProperties.isEmpty())

        assertNull(BuiltInSiteProfiles.releaseRegistry.profile(profileId))
        assertEquals(profile, BuiltInSiteProfiles.qaRegistry.profile(profileId))
        assertEquals(
            TrustMode.TRUSTED_SIGNING,
            BuiltInSiteProfiles.qaRegistry.resolve(profile.startUrl)?.trustMode,
        )
        assertNull(BuiltInSiteProfiles.releaseRegistry.resolve(profile.startUrl))
        assertNull(
            BuiltInSiteProfiles.qaRegistry.resolve(URI("https://sede.ugr.es.evil.example/")),
        )
    }

    @Test
    fun preservesTheExactJccmQaOnlyCertificateProbeContract() {
        val profileId = ProfileId("jccm-certificate-login-probe")
        val profile = BuiltInSiteProfiles.catalog.profiles.single { it.profileId == profileId }

        assertEquals(1, profile.profileVersion)
        assertEquals(CompatibilityStatus.VERIFIED_CONTRACT, profile.compatibilityStatus)
        assertEquals(ProfileActivation.QA_ONLY, profile.activation)
        assertEquals(
            URI(
                "https://ventanillaelectronica.jccm.es/administracion_electronica/" +
                    "formularios/identificacion.phtml",
            ),
            profile.startUrl,
        )
        assertEquals(
            setOf(ExactOrigin.parse("https://ventanillaelectronica.jccm.es")),
            profile.initiatorOrigins,
        )
        assertTrue(profile.redirectOrigins.isEmpty())
        assertTrue(profile.trustedBrowseOrigins.isEmpty())
        assertTrue(profile.endpoints.isEmpty())
        assertEquals(setOf(Capability.SIGN, Capability.LEGACY_SHA1), profile.capabilities)

        val operation = profile.operationPolicies.getValue(ProtocolOperation.SIGN)
        assertEquals(ProtocolInputAdapterId("miniapplet-autoscript-v1"), operation.inputAdapterId)
        assertEquals(CallbackContractId("miniapplet-sign-callback-v1"), operation.callbackContractId)
        assertEquals(setOf(SignatureAlgorithm.SHA1_WITH_RSA), operation.algorithms)
        assertEquals(SignatureFormat.CADES, operation.format)
        assertEquals(SignaturePackaging.DETACHED, operation.packaging)
        assertEquals(SignatureMode.EXPLICIT, operation.mode)
        assertTrue(operation.fixedExtraProperties.isEmpty())
        assertTrue(operation.allowedExtraProperties.isEmpty())

        assertNull(BuiltInSiteProfiles.releaseRegistry.profile(profileId))
        assertEquals(profile, BuiltInSiteProfiles.qaRegistry.profile(profileId))
        assertEquals(
            TrustMode.TRUSTED_SIGNING,
            BuiltInSiteProfiles.qaRegistry.resolve(profile.startUrl)?.trustMode,
        )
        assertNull(BuiltInSiteProfiles.releaseRegistry.resolve(profile.startUrl))
        assertNull(
            BuiltInSiteProfiles.qaRegistry.resolve(
                URI("https://ventanillaelectronica.jccm.es.evil.example/"),
            ),
        )
    }


    @Test
    fun preservesTheExactJccmRegistroGenericoQaOnlyContractSeparatelyFromTheOldProbe() {
        val profileId = ProfileId("jccm-registro-generico")
        val profile = BuiltInSiteProfiles.catalog.profiles.single { it.profileId == profileId }

        assertEquals(2, profile.profileVersion)
        assertEquals("JCCM — Registro Electrónico / Solicitud Genérica", profile.displayName)
        assertEquals(CompatibilityStatus.VERIFIED_CONTRACT, profile.compatibilityStatus)
        assertEquals(ProfileActivation.QA_ONLY, profile.activation)
        assertEquals(
            URI("https://registrounicociudadanos.jccm.es/registrounicociudadanos/acceso.do?id=SJLZ"),
            profile.startUrl,
        )
        assertEquals(
            setOf(ExactOrigin.parse("https://registrounicociudadanos.jccm.es")),
            profile.initiatorOrigins,
        )
        assertEquals(
            setOf(
                ExactOrigin.parse("https://sso.jccm.es"),
                ExactOrigin.parse("https://pasarela.clave.gob.es"),
            ),
            profile.redirectOrigins,
        )
        assertTrue(profile.trustedBrowseOrigins.isEmpty())
        assertTrue(profile.endpoints.isEmpty())
        assertEquals(setOf(Capability.SIGN, Capability.CLIENT_TLS_AUTH), profile.capabilities)
        assertEquals(setOf("RSA"), profile.certificateRules.allowedKeyAlgorithms)
        assertFalse(profile.certificateRules.requireDigitalSignatureKeyUsage)

        val operation = profile.operationPolicies.getValue(ProtocolOperation.SIGN)
        assertEquals(ProtocolInputAdapterId("miniapplet-autoscript-v1"), operation.inputAdapterId)
        assertEquals(CallbackContractId("miniapplet-sign-callback-v1"), operation.callbackContractId)
        assertEquals(setOf(SignatureAlgorithm.SHA512_WITH_RSA), operation.algorithms)
        assertEquals(SignatureFormat.XADES, operation.format)
        assertEquals(SignaturePackaging.DETACHED, operation.packaging)
        assertEquals(SignatureMode.IMPLICIT, operation.mode)
        assertEquals(
            linkedMapOf("format" to "XAdES Detached", "mode" to "implicit"),
            operation.fixedExtraProperties,
        )
        assertTrue(operation.allowedExtraProperties.isEmpty())

        val clientAuth = requireNotNull(profile.clientAuthPolicy)
        assertEquals(ClientAuthTransitionMode.IN_PLACE_FROM_SOURCE, clientAuth.transitionMode)
        assertEquals(HttpMethod.POST, clientAuth.requestMethod)
        assertEquals(
            setOf(ExactOrigin.parse("https://pasarela-ident.clave.gob.es")),
            clientAuth.requestOrigins,
        )
        assertEquals(
            setOf(URI("https://pasarela.clave.gob.es/Proxy2/ServiceRedirect")),
            clientAuth.sourceUrls,
        )
        assertEquals("/IdP2/AuthenticateCitizen", clientAuth.requestPath)
        assertTrue(clientAuth.fixedQueryParameters.isEmpty())
        assertTrue(clientAuth.requiredEphemeralQueryParameters.isEmpty())
        assertTrue(clientAuth.allowEmptyIssuerList)
        assertEquals(15, clientAuth.grantTtlSeconds)
        assertEquals(443, clientAuth.requestPort)

        assertNull(BuiltInSiteProfiles.releaseRegistry.profile(profileId))
        assertEquals(profile, BuiltInSiteProfiles.qaRegistry.profile(profileId))
        assertEquals(
            TrustMode.TRUSTED_SIGNING,
            BuiltInSiteProfiles.qaRegistry.resolve(profile.startUrl)?.trustMode,
        )
        assertEquals(
            TrustMode.BROWSE_ONLY,
            BuiltInSiteProfiles.qaRegistry.resolveForProfile(
                profileId,
                URI("https://sso.jccm.es/cas-jccm-clave/login"),
            )?.trustMode,
        )
        assertNull(BuiltInSiteProfiles.releaseRegistry.resolve(profile.startUrl))
        assertNotEquals(
            profile.profileId,
            ProfileId("jccm-certificate-login-probe"),
        )
    }

    @Test
    fun preservesTheExactSevillaAtseQaOnlyCertificateLoginContract() {
        val profileId = ProfileId("sevilla-atse-certificate-login")
        val profile = BuiltInSiteProfiles.catalog.profiles.single { it.profileId == profileId }

        assertEquals(1, profile.profileVersion)
        assertEquals("Agencia Tributaria de Sevilla — Acceso con certificado", profile.displayName)
        assertEquals(CompatibilityStatus.VERIFIED_CONTRACT, profile.compatibilityStatus)
        assertEquals(ProfileActivation.QA_ONLY, profile.activation)
        assertEquals(
            URI("https://www.sevilla.org/ovweb/ov-web-certificado/index.xhtml?modo=Contribuyente"),
            profile.startUrl,
        )
        assertEquals(
            setOf(ExactOrigin.parse("https://www.sevilla.org")),
            profile.initiatorOrigins,
        )
        assertTrue(profile.redirectOrigins.isEmpty())
        assertTrue(profile.trustedBrowseOrigins.isEmpty())
        assertTrue(profile.endpoints.isEmpty())
        assertEquals(setOf(Capability.SIGN, Capability.LEGACY_SHA1), profile.capabilities)
        assertNull(profile.clientAuthPolicy)
        assertEquals(setOf("RSA"), profile.certificateRules.allowedKeyAlgorithms)
        assertTrue(profile.certificateRules.requireDigitalSignatureKeyUsage)
        assertTrue(profile.evidence.isNotEmpty())

        val operation = profile.operationPolicies.getValue(ProtocolOperation.SIGN)
        assertEquals(
            ProtocolInputAdapterId("miniapplet-autoscript-v1"),
            operation.inputAdapterId,
        )
        assertEquals(
            CallbackContractId("autoscript-sign-callback-v1"),
            operation.callbackContractId,
        )
        assertEquals(setOf(Capability.SIGN, Capability.LEGACY_SHA1), operation.capabilities)
        assertNull(operation.endpointId)
        assertEquals(setOf(SignatureAlgorithm.SHA1_WITH_RSA), operation.algorithms)
        assertEquals(SignatureFormat.XADES, operation.format)
        assertEquals(SignaturePackaging.ATTACHED, operation.packaging)
        assertNull(operation.mode)
        assertTrue(operation.fixedExtraProperties.isEmpty())
        assertTrue(operation.allowedExtraProperties.isEmpty())

        assertNull(BuiltInSiteProfiles.releaseRegistry.profile(profileId))
        assertEquals(profile, BuiltInSiteProfiles.qaRegistry.profile(profileId))
        assertEquals(
            TrustMode.TRUSTED_SIGNING,
            BuiltInSiteProfiles.qaRegistry.resolve(profile.startUrl)?.trustMode,
        )
        assertNull(BuiltInSiteProfiles.releaseRegistry.resolve(profile.startUrl))
        assertNull(
            BuiltInSiteProfiles.qaRegistry.resolve(URI("https://www.sevilla.org.evil.example/")),
        )
    }

    @Test
    fun rejectsAnyExpansionOfTheCanariasCertificateLoginProfileContract() {
        val mutations = listOf(
            "\"profileVersion\": 2" to "\"profileVersion\": 1",
            "\"profileVersion\": 2" to "\"profileVersion\": 3",
            "\"reviewedOn\":\"2026-11-08\"" to "\"reviewedOn\":\"2026-08-17\"",
            "\"SHA1_WITH_RSA\"" to "\"SHA256_WITH_RSA\"",
            "\"format\":\"CAdES Detached\"" to "\"format\":\"CAdES\"",
            "referencesDigestMethod\":\"http://www.w3.org/2001/04/xmlenc#sha512" to
                "referencesDigestMethod\":\"http://www.w3.org/2001/04/xmlenc#sha256",
            "signingCert:true;issuer.rfc2254" to "signingCert:false;issuer.rfc2254",
        )

        mutations.forEach { (expected, replacement) ->
            val canariasStart = BuiltInSiteProfiles.JSON.indexOf("\"profileId\": \"canarias-sede\"")
            assertTrue(canariasStart >= 0)
            val nextProfile = BuiltInSiteProfiles.JSON.indexOf("\"profileId\":", canariasStart + 1)
            val end = if (nextProfile >= 0) nextProfile else BuiltInSiteProfiles.JSON.length
            val block = BuiltInSiteProfiles.JSON.substring(canariasStart, end)
            assertTrue("missing Canarias contract fragment: $expected", block.contains(expected))
            val changedBlock = block.replaceFirst(expected, replacement)
            val mutated = BuiltInSiteProfiles.JSON.substring(0, canariasStart) + changedBlock +
                BuiltInSiteProfiles.JSON.substring(end)
            assertThrows(IllegalArgumentException::class.java) {
                SiteProfileCatalogParser.parse(mutated)
            }
        }
    }

    @Test
    fun preservesTheExactCantabriaRecQaOnlyCertificateContract() {
        val profileId = ProfileId("cantabria-rec-cert-login")
        val profile = BuiltInSiteProfiles.catalog.profiles.single { it.profileId == profileId }

        assertEquals(1, profile.profileVersion)
        assertEquals(CompatibilityStatus.VERIFIED_CONTRACT, profile.compatibilityStatus)
        assertEquals(ProfileActivation.QA_ONLY, profile.activation)
        assertEquals(
            URI("https://rec.cantabria.es/rec/bienvenida.htm"),
            profile.startUrl,
        )
        assertEquals(
            setOf(ExactOrigin.parse("https://rec.cantabria.es")),
            profile.initiatorOrigins,
        )
        assertTrue(profile.redirectOrigins.isEmpty())
        assertTrue(profile.trustedBrowseOrigins.isEmpty())
        assertTrue(profile.endpoints.isEmpty())
        assertEquals(setOf(Capability.SIGN), profile.capabilities)
        assertEquals(setOf("RSA"), profile.certificateRules.allowedKeyAlgorithms)
        assertTrue(profile.certificateRules.requireDigitalSignatureKeyUsage)

        val operation = profile.operationPolicies.getValue(ProtocolOperation.SIGN)
        assertEquals(
            ProtocolInputAdapterId("miniapplet-autoscript-v1"),
            operation.inputAdapterId,
        )
        assertEquals(
            CallbackContractId("miniapplet-sign-callback-v1"),
            operation.callbackContractId,
        )
        assertEquals(setOf(Capability.SIGN), operation.capabilities)
        assertEquals(setOf(SignatureAlgorithm.SHA512_WITH_RSA), operation.algorithms)
        assertEquals(SignatureFormat.CADES, operation.format)
        assertEquals(SignaturePackaging.DETACHED, operation.packaging)
        assertEquals(SignatureMode.IMPLICIT, operation.mode)
        assertNull(operation.endpointId)
        assertEquals(
            mapOf("filters" to "", "mode" to "implicit"),
            operation.fixedExtraProperties,
        )
        assertTrue(operation.allowedExtraProperties.isEmpty())

        assertNull(BuiltInSiteProfiles.releaseRegistry.profile(profileId))
        assertNull(BuiltInSiteProfiles.releaseRegistry.resolve(profile.startUrl))
        assertEquals(profile, BuiltInSiteProfiles.qaRegistry.profile(profileId))
        assertEquals(
            TrustMode.TRUSTED_SIGNING,
            BuiltInSiteProfiles.qaRegistry.resolve(profile.startUrl)?.trustMode,
        )
        assertNull(
            BuiltInSiteProfiles.qaRegistry.resolve(
                URI("https://rec.cantabria.es.evil.example/"),
            ),
        )
    }

    @Test
    fun preservesTheExactUnizarAuthenticationContract() {
        val profile = BuiltInSiteProfiles.catalog.profiles.single {
            it.profileId == ProfileId("unizar-tramitador")
        }
        assertEquals(2, profile.profileVersion)
        assertEquals(CompatibilityStatus.VERIFIED_E2E, profile.compatibilityStatus)
        assertEquals(ProfileActivation.ENABLED, profile.activation)
        assertEquals(setOf("https://tramita.unizar.es"), profile.initiatorOrigins.mapTo(linkedSetOf()) { it.serialized })
        assertTrue(profile.redirectOrigins.isEmpty())
        assertTrue(profile.trustedBrowseOrigins.isEmpty())
        val operation = profile.operationPolicies.getValue(ProtocolOperation.SIGN)
        assertEquals(setOf(SignatureAlgorithm.SHA1_WITH_RSA), operation.algorithms)
        assertEquals(SignatureFormat.CADES, operation.format)
        assertEquals(SignaturePackaging.DETACHED, operation.packaging)
        assertNull(operation.mode)
        assertEquals(EndpointId("unizar-triphase"), operation.endpointId)
        assertEquals(
            mapOf(
                "precalculatedHashAlgorithm" to "SHA1",
                "serverUrl" to "https://tramita.unizar.es/afirma-server-triphase-signer-2.9/SignatureService",
            ),
            operation.fixedExtraProperties,
        )
        assertEquals(CallbackContractId("autoscript-sign-callback-v1"), operation.callbackContractId)
    }

    @Test
    fun preservesTheExactAragonSirawContract() {
        val profile = BuiltInSiteProfiles.catalog.profiles.single {
            it.profileId == ProfileId("aragon-siraw")
        }
        assertEquals(ProfileId("aragon-siraw"), profile.profileId)
        assertEquals(2, profile.profileVersion)
        assertEquals(LocalCadesDetachedAdapter.PROFILE_VERSION, profile.profileVersion)
        assertEquals(CompatibilityStatus.VERIFIED_E2E, profile.compatibilityStatus)
        assertEquals(ProfileActivation.ENABLED, profile.activation)
        assertEquals(
            "https://aplicaciones.aragon.es/siraw/pages/login.xhtml?origen=siefw",
            profile.startUrl.toString(),
        )
        assertEquals(
            setOf(ExactOrigin.parse("https://aplicaciones.aragon.es")),
            profile.initiatorOrigins,
        )
        assertTrue(profile.redirectOrigins.isEmpty())
        assertTrue(profile.trustedBrowseOrigins.isEmpty())
        assertTrue(profile.endpoints.isEmpty())
        assertEquals(setOf(Capability.SIGN, Capability.LEGACY_SHA1), profile.capabilities)
        val policy = profile.operationPolicies.getValue(ProtocolOperation.SIGN)
        assertEquals("Acceso con certificado a SIRAW", policy.safeDescription)
        assertEquals(setOf(SignatureAlgorithm.SHA1_WITH_RSA), policy.algorithms)
        assertEquals(SignatureFormat.CADES, policy.format)
        assertEquals(SignaturePackaging.DETACHED, policy.packaging)
        assertEquals(SignatureMode.EXPLICIT, policy.mode)
        assertEquals(ProtocolInputAdapterId("miniapplet-autoscript-v1"), policy.inputAdapterId)
        assertEquals(CallbackContractId("miniapplet-sign-callback-v1"), policy.callbackContractId)
        assertNull(policy.endpointId)
        assertEquals(
            mapOf("mode" to "explicit", "filter" to "nonexpired"),
            policy.fixedExtraProperties,
        )
        assertTrue(policy.allowedExtraProperties.isEmpty())
    }

    @Test
    fun preservesTheExactJuntaOfvirtualContract() {
        val profile = BuiltInSiteProfiles.catalog.profiles.single {
            it.profileId == ProfileId("junta-ofvirtual")
        }
        assertEquals(ProfileId("junta-ofvirtual"), profile.profileId)
        assertEquals(2, profile.profileVersion)
        assertEquals(CompatibilityStatus.VERIFIED_E2E, profile.compatibilityStatus)
        assertEquals(ProfileActivation.ENABLED, profile.activation)
        assertEquals(
            "https://ws072.juntadeandalucia.es/ofvirtual/auth/signInAutcertjs",
            profile.startUrl.toString(),
        )
        assertEquals(
            setOf("https://ws072.juntadeandalucia.es"),
            profile.initiatorOrigins.mapTo(linkedSetOf()) { it.serialized },
        )
        assertTrue(profile.redirectOrigins.isEmpty())
        assertTrue(profile.trustedBrowseOrigins.isEmpty())
        val operation = profile.operationPolicies.getValue(ProtocolOperation.SIGN)
        assertEquals("Acceso con certificado a la Oficina Virtual", operation.safeDescription)
        assertEquals(setOf(SignatureAlgorithm.SHA1_WITH_RSA), operation.algorithms)
        assertEquals(SignatureFormat.CADES, operation.format)
        assertEquals(SignaturePackaging.DETACHED, operation.packaging)
        assertEquals(SignatureMode.EXPLICIT, operation.mode)
        assertEquals(ProtocolInputAdapterId("miniapplet-autoscript-v1"), operation.inputAdapterId)
        assertEquals(CallbackContractId("miniapplet-sign-callback-v1"), operation.callbackContractId)
        assertEquals(
            mapOf(
                "serverUrl" to "https://ws024.juntadeandalucia.es/afirma-validator-miniapplet-1_5/sign/TriPhaseSignatureService",
                "filters" to "keyusage.digitalsignature:true;nonexpired:",
            ),
            operation.fixedExtraProperties,
        )
        val endpoint = profile.endpoints.getValue(EndpointId("junta-ofvirtual-triphase"))
        assertEquals("ws024.juntadeandalucia.es", endpoint.url.host)
        assertTrue(endpoint.url.originForTest() !in profile.navigationOriginsForTest())
    }

    @Test
    fun rejectsCrossProfileExactEndpointCollision() {
        val existingEndpoint =
            "https://ws024.juntadeandalucia.es/afirma-validator-miniapplet-1_4/sign/TriPhaseSignatureService"

        assertThrows(IllegalArgumentException::class.java) {
            SiteProfileCatalogParser.parse(
                BuiltInSiteProfiles.JSON.replace(
                    "https://ws024.juntadeandalucia.es/afirma-validator-miniapplet-1_5/sign/TriPhaseSignatureService",
                    existingEndpoint,
                ),
            )
        }
    }

    @Test
    fun clientAuthPolicyRequiresExplicitTransitionMode() {
        assertThrows(IllegalArgumentException::class.java) {
            SiteProfileCatalogParser.parse(
                BuiltInSiteProfiles.JSON.replaceFirst(
                    "\"transitionMode\": \"REDIRECT_AFTER_SOURCE\",",
                    "",
                ),
            )
        }
    }

    @Test
    fun rejectsAnyExpansionOfTheSanidadSameOriginClientTlsContract() {
        val mutations = listOf(
            "\"tramiteId\":\"TRAM_TEMPRANAATENCION\"" to
                "\"tramiteId\":\"TRAM_OTHER\"",
            "\"sourceUrls\":[\"https://sede.mscbs.gob.es/registroElectronico/formularios.htm\"]" to
                "\"sourceUrls\":[\"https://sede.mscbs.gob.es/registroElectronico/home.htm\"]",
            "\"COUNTRY\":\"ES\"},\"requiredEphemeralQueryParameters\":[]" to
                "\"COUNTRY\":\"ES\"},\"requiredEphemeralQueryParameters\":[\"session\"]",
        )

        mutations.forEach { (expected, replacement) ->
            assertTrue("missing Sanidad contract fragment: $expected", BuiltInSiteProfiles.JSON.contains(expected))
            assertThrows(IllegalArgumentException::class.java) {
                SiteProfileCatalogParser.parse(BuiltInSiteProfiles.JSON.replaceFirst(expected, replacement))
            }
        }
    }

    @Test
    fun rejectsDirectFixedQueryOutsideThePinnedSanidadContract() {
        val aeatPathAndQuery =
            "\"requestPath\":\"/wlpl/OVCT-CXEW/DialogoRepresentacion\"," +
                "\"fixedQueryParameters\":{\"ref\":\"/wlpl/BUGC-JDIT/MdcAcceso\"}"
        val expanded =
            "\"requestPath\":\"/wlpl/OVCT-CXEW/DialogoRepresentacion\"," +
                "\"fixedQueryParameters\":{\"ref\":\"/wlpl/BUGC-JDIT/MdcAcceso\",\"unexpected\":\"1\"}"

        // PATTEX is a DIRECT profile outside the fixed-query allowlist.
        val pattexPathAndQuery =
            "\"requestPath\":\"/PATTEX/accesoCertificadoSEDE.jsf\",\"fixedQueryParameters\":{}"
        val pattexWithQuery =
            "\"requestPath\":\"/PATTEX/accesoCertificadoSEDE.jsf\",\"fixedQueryParameters\":{\"unexpected\":\"1\"}"

        assertTrue(BuiltInSiteProfiles.JSON.contains(aeatPathAndQuery))
        assertTrue(BuiltInSiteProfiles.JSON.contains(pattexPathAndQuery))
        listOf(aeatPathAndQuery to expanded, pattexPathAndQuery to pattexWithQuery).forEach { (original, mutated) ->
            assertThrows(IllegalArgumentException::class.java) {
                SiteProfileCatalogParser.parse(BuiltInSiteProfiles.JSON.replaceFirst(original, mutated))
            }
        }
    }

    @Test
    fun directClientAuthCannotUseRedirectOriginAsSourceOutsideTheReviewedNavarraProfile() {
        val navarraId = "\"profileId\": \"navarra-sede-registro-general\""
        val unreviewedId = "\"profileId\": \"navarra-unreviewed-direct-client-auth\""
        assertTrue(BuiltInSiteProfiles.JSON.contains(navarraId))

        assertThrows(IllegalArgumentException::class.java) {
            SiteProfileCatalogParser.parse(BuiltInSiteProfiles.JSON.replaceFirst(navarraId, unreviewedId))
        }
    }

    @Test
    fun navarraClientAuthMappingFailsClosedOnUnboundOrAmbiguousParameterNames() {
        val mutations = listOf(
            "\"linkedEphemeralQueryParameterMappings\": {\"ReturnUrl\":\"returnUrl\"}" to
                "\"linkedEphemeralQueryParameterMappings\":{\"ReturnUrl\":\"wrongTarget\"}",
            "\"sourceRequiredEphemeralQueryParameters\": [\"ReturnUrl\"]" to
                "\"sourceRequiredEphemeralQueryParameters\":[\"OtherSource\"]",
            "\"requiredEphemeralQueryParameters\": [\"returnUrl\"]" to
                "\"requiredEphemeralQueryParameters\":[\"OtherTarget\"]",
            "\"linkedEphemeralQueryParameterMappings\": {\"ReturnUrl\":\"returnUrl\"}" to
                "\"linkedEphemeralQueryParameters\":[\"ReturnUrl\"]," +
                    "\"linkedEphemeralQueryParameterMappings\":{\"ReturnUrl\":\"returnUrl\"}",
        )

        mutations.forEach { (expected, replacement) ->
            assertTrue("missing Navarra contract fragment: $expected", BuiltInSiteProfiles.JSON.contains(expected))
            assertThrows(IllegalArgumentException::class.java) {
                SiteProfileCatalogParser.parse(BuiltInSiteProfiles.JSON.replaceFirst(expected, replacement))
            }
        }
    }

    @Test
    fun preservesTheExactAeatClientTlsQaContract() {
        val profileId = ProfileId("aeat-mis-datos-censales")
        val profile = BuiltInSiteProfiles.catalog.profiles.single { it.profileId == profileId }

        assertEquals(CompatibilityStatus.VERIFIED_CONTRACT, profile.compatibilityStatus)
        assertEquals(ProfileActivation.QA_ONLY, profile.activation)
        assertEquals(
            URI("https://sede.agenciatributaria.gob.es/Sede/mi-area-personal.html"),
            profile.startUrl,
        )
        assertEquals(setOf(Capability.CLIENT_TLS_AUTH), profile.capabilities)
        assertTrue(profile.operationPolicies.isEmpty())
        assertTrue(profile.endpoints.isEmpty())
        assertTrue(profile.redirectOrigins.isEmpty())
        assertTrue(profile.trustedBrowseOrigins.isEmpty())
        val policy = checkNotNull(profile.clientAuthPolicy)
        assertEquals(ClientAuthTransitionMode.DIRECT_FROM_SOURCE, policy.transitionMode)
        assertEquals(
            setOf(ExactOrigin.parse("https://www1.agenciatributaria.gob.es")),
            policy.requestOrigins,
        )
        assertEquals(
            setOf(
                URI(
                    "https://sede.agenciatributaria.gob.es/static_files/common/html/selector_acceso/" +
                        "SelectorAccesos.html?rep=S&ref=%2Fwlpl%2FBUGC-JDIT%2FMdcAcceso&aut=CPE",
                ),
            ),
            policy.sourceUrls,
        )
        assertEquals("/wlpl/OVCT-CXEW/DialogoRepresentacion", policy.requestPath)
        assertEquals(mapOf("ref" to "/wlpl/BUGC-JDIT/MdcAcceso"), policy.fixedQueryParameters)
        assertTrue(policy.requiredEphemeralQueryParameters.isEmpty())
        assertEquals(false, policy.allowEmptyIssuerList)
        assertEquals(15, policy.grantTtlSeconds)
        assertEquals(443, policy.requestPort)
        assertEquals(setOf("RSA", "EC"), profile.certificateRules.allowedKeyAlgorithms)
        assertTrue(profile.certificateRules.requireDigitalSignatureKeyUsage)
        assertNull(BuiltInSiteProfiles.releaseRegistry.profile(profileId))
        assertEquals(profile, BuiltInSiteProfiles.qaRegistry.profile(profileId))
        assertEquals(
            TrustMode.TRUSTED_CLIENT_AUTH,
            BuiltInSiteProfiles.qaRegistry.resolve(profile.startUrl)?.trustMode,
        )
        assertEquals(
            TrustMode.BROWSE_ONLY,
            BuiltInSiteProfiles.qaRegistry.resolve(
                URI("https://www1.agenciatributaria.gob.es/wlpl/BUGC-JDIT/MdcAcceso"),
            )?.trustMode,
        )
        assertNull(BuiltInSiteProfiles.releaseRegistry.resolve(profile.startUrl))
        assertNull(
            BuiltInSiteProfiles.releaseRegistry.resolve(
                URI("https://www1.agenciatributaria.gob.es/wlpl/BUGC-JDIT/MdcAcceso"),
            ),
        )
    }

    @Test
    fun rejectsMenorcaClientTlsContractWithoutLinkedUrlParameter() {
        val linkedUrl = "\"linkedEphemeralQueryParameters\":[\"URL\"]"

        assertTrue(BuiltInSiteProfiles.JSON.contains(linkedUrl))
        assertThrows(IllegalArgumentException::class.java) {
            SiteProfileCatalogParser.parse(
                BuiltInSiteProfiles.JSON.replaceFirst(
                    linkedUrl,
                    "\"linkedEphemeralQueryParameters\":[]",
                ),
            )
        }
    }

    @Test
    fun preservesTheExactCarneJovenClientTlsContract() {
        val profile = BuiltInSiteProfiles.catalog.profiles.single {
            it.profileId == ProfileId("carne-joven-andalucia")
        }
        assertEquals(CompatibilityStatus.VERIFIED_E2E, profile.compatibilityStatus)
        assertEquals(ProfileActivation.ENABLED, profile.activation)
        assertEquals(setOf(Capability.CLIENT_TLS_AUTH), profile.capabilities)
        assertTrue(profile.operationPolicies.isEmpty())
        assertTrue(profile.endpoints.isEmpty())
        val policy = checkNotNull(profile.clientAuthPolicy)
        assertEquals(ClientAuthTransitionMode.REDIRECT_AFTER_SOURCE, policy.transitionMode)
        assertEquals(
            setOf(ExactOrigin.parse("https://ws235.juntadeandalucia.es")),
            policy.requestOrigins,
        )
        assertEquals(
            setOf(URI("https://ws104.juntadeandalucia.es/carneJoven/servlet/CallAuthenticationServlet")),
            policy.sourceUrls,
        )
        assertEquals("/authenticationFacade", policy.requestPath)
        assertEquals(
            mapOf(
                "action" to "validateCert",
                "appId" to "IAJ.CARNETJOVEN",
                "comeBackURL" to
                    "aHR0cHM6Ly93czEwNC5qdW50YWRlYW5kYWx1Y2lhLmVzL2Nhcm5lSm92ZW4v" +
                    "c2VydmxldC9SZXR1cm5BdXRoZW50aWNhdGlvblNlcnZsZXQ=",
            ),
            policy.fixedQueryParameters,
        )
        assertEquals(setOf("ticketId", "webSessionId"), policy.requiredEphemeralQueryParameters)
        assertTrue(policy.allowEmptyIssuerList)
        assertEquals(15, policy.grantTtlSeconds)
        assertEquals(443, policy.requestPort)
        assertNull(
            BuiltInSiteProfiles.qaRegistry.resolve(
                URI("https://ws235.juntadeandalucia.es/authenticationFacade"),
            ),
        )
        assertEquals(
            TrustMode.TRUSTED_CLIENT_AUTH,
            BuiltInSiteProfiles.qaRegistry.resolve(profile.startUrl)?.trustMode,
        )
        assertEquals(
            TrustMode.TRUSTED_CLIENT_AUTH,
            BuiltInSiteProfiles.releaseRegistry.resolve(profile.startUrl)?.trustMode,
        )
        assertEquals(
            TrustMode.BROWSE_ONLY,
            BuiltInSiteProfiles.releaseRegistry.resolve(
                URI("https://ws235.juntadeandalucia.es/authenticationFacade"),
            )?.trustMode,
        )
        assertThrows(IllegalArgumentException::class.java) {
            SiteProfileCatalogParser.parse(
                BuiltInSiteProfiles.JSON.replace(
                    "[\"https://ws235.juntadeandalucia.es\"]",
                    "[\"https://ws235.juntadeandalucia.es\", \"https://ws236.juntadeandalucia.es\"]",
                ),
            )
        }
    }

    @Test
    fun ourenseClaveClientAuthProfilePinsReviewedSharedOriginsAndRejectsUnknownOwner() {
        val profileId = ProfileId("diputacion-ourense-sede")
        val profile = BuiltInSiteProfiles.catalog.profiles.single { it.profileId == profileId }
        val policy = checkNotNull(profile.clientAuthPolicy)

        assertEquals(CompatibilityStatus.VERIFIED_CONTRACT, profile.compatibilityStatus)
        assertEquals(ProfileActivation.QA_ONLY, profile.activation)
        assertEquals(
            URI(
                "https://sede.depourense.es/sta/CarpetaPublic/doEvent?APP_CODE=STA&" +
                    "PAGE_CODE=CATALOGO&DETALLE=6269000946476474507610&lang=ES",
            ),
            profile.startUrl,
        )
        assertEquals(setOf(ExactOrigin.parse("https://sede.depourense.es")), profile.initiatorOrigins)
        assertEquals(setOf(ExactOrigin.parse("https://pasarela.clave.gob.es")), profile.redirectOrigins)
        assertEquals(setOf(Capability.CLIENT_TLS_AUTH), profile.capabilities)
        assertEquals(ClientAuthTransitionMode.IN_PLACE_FROM_SOURCE, policy.transitionMode)
        assertEquals(
            setOf(URI("https://pasarela.clave.gob.es/Proxy2/ServiceRedirect")),
            policy.sourceUrls,
        )
        assertEquals(
            setOf(ExactOrigin.parse("https://pasarela-ident.clave.gob.es")),
            policy.requestOrigins,
        )
        assertEquals("/IdP2/AuthenticateCitizen", policy.requestPath)
        assertEquals(setOf("RSA", "EC"), profile.certificateRules.allowedKeyAlgorithms)
        assertTrue(profile.certificateRules.requireDigitalSignatureKeyUsage)
        assertNull(BuiltInSiteProfiles.releaseRegistry.profile(profileId))
        assertEquals(profile, BuiltInSiteProfiles.qaRegistry.profile(profileId))

        assertThrows(IllegalArgumentException::class.java) {
            SiteProfileCatalogParser.parse(
                BuiltInSiteProfiles.JSON.replace(
                    "\"profileId\": \"diputacion-ourense-sede\"",
                    "\"profileId\": \"unreviewed-clave-owner\"",
                ),
            )
        }
    }

    @Test
    fun diputacionSevillaClaveOwnershipStaysClosedToTheReviewedProfileId() {
        val reviewedId = "\"profileId\": \"diputacion-sevilla-sede\""
        val unreviewedId = "\"profileId\": \"unreviewed-sevilla-clave-owner\""
        assertTrue(BuiltInSiteProfiles.JSON.contains(reviewedId))

        assertThrows(IllegalArgumentException::class.java) {
            SiteProfileCatalogParser.parse(BuiltInSiteProfiles.JSON.replaceFirst(reviewedId, unreviewedId))
        }
    }

    @Test
    fun diputacionACorunaClaveOwnershipStaysClosedToTheReviewedProfileId() {
        val reviewedId = "\"profileId\": \"diputacion-a-coruna-solicitud-general\""
        val unreviewedId = "\"profileId\": \"unreviewed-coruna-clave-owner\""
        assertTrue(BuiltInSiteProfiles.JSON.contains(reviewedId))

        assertThrows(IllegalArgumentException::class.java) {
            SiteProfileCatalogParser.parse(BuiltInSiteProfiles.JSON.replaceFirst(reviewedId, unreviewedId))
        }
    }

    @Test
    fun preservesExactAragonSolicitudGeneralClientTlsContract() {
        val profileId = ProfileId("aragon-solicitud-general-client-auth")
        val start = URI("https://aplicaciones.aragon.es/tramitar/solicitud-general/identificacion")
        val profile = BuiltInSiteProfiles.catalog.profiles.single { it.profileId == profileId }
        val policy = checkNotNull(profile.clientAuthPolicy)
        val consume =
            "https://aplicaciones.aragon.es/mfe_core/rest/identification/TTO/" +
                "aHR0cHM6Ly9hcGxpY2FjaW9uZXMuYXJhZ29uLmVzL3RyYW1pdGFyL3NvbGljaXR1ZC1nZW5lcmFsL2lkZW50aWZpY2FjaW9uL2lkZW50aWZpY2Fkbw==/" +
                "SSLOGIN/consumeResponse"

        assertEquals(CompatibilityStatus.VERIFIED_CONTRACT, profile.compatibilityStatus)
        assertEquals(ProfileActivation.QA_ONLY, profile.activation)
        assertEquals(start, profile.startUrl)
        assertEquals(setOf(Capability.CLIENT_TLS_AUTH), profile.capabilities)
        assertTrue(profile.operationPolicies.isEmpty())
        assertTrue(profile.endpoints.isEmpty())
        assertEquals(ClientAuthTransitionMode.REDIRECT_AFTER_SOURCE, policy.transitionMode)
        assertEquals(
            setOf(ExactOrigin.parse("https://login1.loginssl.aragon.es"), ExactOrigin.parse("https://login2.loginssl.aragon.es")),
            policy.requestOrigins,
        )
        assertEquals(setOf(URI("https://login.loginssl.aragon.es/sife_login/SSLOGIN")), policy.sourceUrls)
        assertEquals(mapOf("redirect.url" to consume), policy.sourceFixedQueryParameters)
        assertEquals("/sife_login/SSLOGIN/idByCert", policy.requestPath)
        assertEquals(mapOf("redirect.url" to consume), policy.fixedQueryParameters)
        assertTrue(policy.requiredEphemeralQueryParameters.isEmpty())
        assertTrue(policy.allowEmptyIssuerList)
        assertEquals(443, policy.requestPort)
        assertEquals(setOf("RSA", "EC"), profile.certificateRules.allowedKeyAlgorithms)
        assertTrue(profile.certificateRules.requireDigitalSignatureKeyUsage)
        assertNull(BuiltInSiteProfiles.releaseRegistry.profile(profileId))
        assertNull(BuiltInSiteProfiles.qaRegistry.resolve(start))
        assertEquals(
            TrustMode.TRUSTED_CLIENT_AUTH,
            BuiltInSiteProfiles.qaRegistry.resolveForProfile(profileId, start)?.trustMode,
        )
    }

    @Test
    fun releaseEnablesVerifiedProfilesWhileQaKeepsExperimentalPortalsAvailable() {
        val junta = ProfileId("junta-andalucia")
        val carne = ProfileId("carne-joven-andalucia")
        val education = ProfileId("educacion-convocatoria")
        val ceuta = ProfileId("ceuta-sede")
        val acceda = ProfileId("age-acceda")
        val lleida = ProfileId("diputacion-lleida-sede")
        val badajoz = ProfileId("diputacion-badajoz-portal")
        val aragon = ProfileId("aragon-siraw")
        val ofvirtual = ProfileId("junta-ofvirtual")
        val unizar = ProfileId("unizar-tramitador")
        val releaseProfiles = setOf(carne, aragon, ofvirtual, unizar)
        val qaOnly = setOf(
            junta,
            education,
            ProfileId("formentera-sede-electronica"),
            ProfileId("iac-sede-public-navigation"),
            ProfileId("icac-sede-public-navigation"),
            ProfileId("itj-sede-public-navigation"),
            ProfileId("red-es-sede-public-navigation"),
            ProfileId("formentera-portal-institucional-navigation"),
            ProfileId("ico-sede-public-navigation"),
            ProfileId("el-hierro-sede-public-navigation"),
            ProfileId("imserso-sede-public-navigation"),
            ProfileId("ine-sede-public-navigation"),
            ProfileId("isfas-sede-public-navigation"),
            ProfileId("comunidad-madrid-gestiona2-public-navigation"),
            ProfileId("diputacion-soria-sede-client-auth"),
            ProfileId("la-gomera-sede-public-navigation"),
            ProfileId("lanzarote-sede-public-navigation"),
            ProfileId("zamora-sede-public-navigation"),
            ProfileId("zaragoza-sede-public-navigation"),
            ProfileId("reg-age-redsara"),
            ProfileId("aeat-mis-datos-censales"),
            ProfileId("dgt-verificacion-equipo"),
            ProfileId("junta-andalucia-vea-peg"),
            ProfileId("mjusticia-fundaciones-idp75"),
            ProfileId("dgoj-public-navigation"),
            ProfileId("diputacion-alava-registro-comun"),
            ceuta,
            acceda,
            ProfileId("aragon-solicitud-general-client-auth"),
            lleida,
            badajoz,
        )

        assertEquals(releaseProfiles, BuiltInSiteProfiles.catalog.profiles
            .mapNotNull { profile ->
                profile.profileId.takeIf { BuiltInSiteProfiles.releaseRegistry.profile(it) != null }
            }
            .toSet())
        qaOnly.forEach { profileId ->
            assertNull(BuiltInSiteProfiles.releaseRegistry.profile(profileId))
            assertTrue(BuiltInSiteProfiles.qaRegistry.profile(profileId) != null)
        }
        setOf(carne, aragon, ofvirtual, unizar).forEach { profileId ->
            assertEquals(
                CompatibilityStatus.VERIFIED_E2E,
                BuiltInSiteProfiles.releaseRegistry.profile(profileId)?.compatibilityStatus,
            )
        }
        assertEquals(
            CompatibilityStatus.EXPERIMENTAL,
            BuiltInSiteProfiles.qaRegistry.profile(junta)?.compatibilityStatus,
        )
        assertEquals(
            CompatibilityStatus.VERIFIED_CONTRACT,
            BuiltInSiteProfiles.qaRegistry.profile(education)?.compatibilityStatus,
        )
        assertEquals(
            CompatibilityStatus.VERIFIED_CONTRACT,
            BuiltInSiteProfiles.qaRegistry.profile(ceuta)?.compatibilityStatus,
        )
        assertEquals(
            CompatibilityStatus.VERIFIED_CONTRACT,
            BuiltInSiteProfiles.qaRegistry.profile(acceda)?.compatibilityStatus,
        )
    }

    @Test
    fun releaseRejectsEverySensitiveEnabledProfileWithoutVerifiedE2eEvidence() {
        val sensitiveCapabilities = setOf(
            Capability.SIGN,
            Capability.SELECT_CERTIFICATE,
            Capability.CLIENT_TLS_AUTH,
        )
        val sensitiveProfiles = BuiltInSiteProfiles.catalog.profiles.filter { profile ->
            profile.capabilities.any(sensitiveCapabilities::contains)
        }
        val nonE2eSensitiveProfiles = sensitiveProfiles.filter { profile ->
            profile.compatibilityStatus != CompatibilityStatus.VERIFIED_E2E
        }
        val verifiedSensitiveProfiles = sensitiveProfiles.filter { profile ->
            profile.activation == ProfileActivation.ENABLED &&
                profile.compatibilityStatus == CompatibilityStatus.VERIFIED_E2E
        }

        assertTrue(nonE2eSensitiveProfiles.isNotEmpty())
        nonE2eSensitiveProfiles.forEach { profile ->
            assertNull(profile.profileId.value, BuiltInSiteProfiles.releaseRegistry.profile(profile.profileId))
        }
        assertTrue(verifiedSensitiveProfiles.isNotEmpty())
        verifiedSensitiveProfiles.forEach { verifiedProfile ->
            val downgradedCatalog = BuiltInSiteProfiles.catalog.copy(
                profiles = BuiltInSiteProfiles.catalog.profiles.map { profile ->
                    if (profile.profileId == verifiedProfile.profileId) {
                        profile.copy(compatibilityStatus = CompatibilityStatus.VERIFIED_CONTRACT)
                    } else {
                        profile
                    }
                },
            )
            val release = SiteProfileRegistry(downgradedCatalog, BuildTrustPolicy.RELEASE)
            val qa = SiteProfileRegistry(downgradedCatalog, BuildTrustPolicy.QA)

            assertNull(verifiedProfile.profileId.value, release.profile(verifiedProfile.profileId))
            assertEquals(
                CompatibilityStatus.VERIFIED_CONTRACT,
                qa.profile(verifiedProfile.profileId)?.compatibilityStatus,
            )
        }
    }

    @Test
    fun `OEPM ProtegeO public launch profile is QA only and exposes no sensitive capability`() {
        val profileId = ProfileId("oepm-protegeo-general")
        val start = URI(
            "https://sede.oepm.gob.es/ProtegeOWeb/inicio.html?tipoTramite=SOLIC_PROP_GEN_OEPM",
        )
        val profile = BuiltInSiteProfiles.catalog.profiles.single { it.profileId == profileId }

        assertEquals(1, profile.profileVersion)
        assertEquals(CompatibilityStatus.VERIFIED_CONTRACT, profile.compatibilityStatus)
        assertEquals(ProfileActivation.QA_ONLY, profile.activation)
        assertEquals(start, profile.startUrl)
        assertEquals(setOf(ExactOrigin.parse("https://sede.oepm.gob.es")), profile.initiatorOrigins)
        assertTrue(profile.redirectOrigins.isEmpty())
        assertTrue(profile.trustedBrowseOrigins.isEmpty())
        assertTrue(profile.endpoints.isEmpty())
        assertTrue(profile.operationPolicies.isEmpty())
        assertTrue(profile.capabilities.isEmpty())
        assertNull(profile.clientAuthPolicy)
        assertEquals(setOf("RSA", "EC"), profile.certificateRules.allowedKeyAlgorithms)
        assertEquals(false, profile.certificateRules.requireDigitalSignatureKeyUsage)
        assertEquals(2, profile.evidence.size)

        assertNull(BuiltInSiteProfiles.releaseRegistry.profile(profileId))
        assertNull(BuiltInSiteProfiles.releaseRegistry.resolve(start))
        assertEquals(profile, BuiltInSiteProfiles.qaRegistry.profile(profileId))
        assertEquals(TrustMode.TRUSTED_BROWSE, BuiltInSiteProfiles.qaRegistry.resolve(start)?.trustMode)
        assertNull(BuiltInSiteProfiles.qaRegistry.resolve(URI("https://sede.oepm.gob.es.evil.example/")))
        assertNull(BuiltInSiteProfiles.qaRegistry.resolve(URI("https://sede.oepm.gob.es:444/")))
    }

    @Test
    fun rejectsDuplicateUnknownAndUnsupportedSchemaKeys() {
        val json = BuiltInSiteProfiles.JSON
        assertThrows(IllegalArgumentException::class.java) {
            SiteProfileCatalogParser.parse(json.replaceFirst("\"schemaVersion\": 1", "\"schemaVersion\": 1, \"schemaVersion\": 1"))
        }
        val catalogVersionToken = "\"catalogVersion\": ${BuiltInSiteProfiles.catalog.catalogVersion}"
        assertTrue(json.contains(catalogVersionToken))
        assertThrows(IllegalArgumentException::class.java) {
            SiteProfileCatalogParser.parse(
                json.replaceFirst(
                    catalogVersionToken,
                    "\"unknown\": true, $catalogVersionToken",
                ),
            )
        }
        assertThrows(IllegalArgumentException::class.java) {
            SiteProfileCatalogParser.parse(json.replaceFirst("\"schemaVersion\": 1", "\"schemaVersion\": 2"))
        }
    }

    @Test
    fun rejectsBlankFixedExtraPropertyOutsideCantabriaProfile() {
        val exact = "\"filter\": \"nonexpired:\""
        assertTrue(BuiltInSiteProfiles.JSON.contains(exact))
        assertThrows(IllegalArgumentException::class.java) {
            SiteProfileCatalogParser.parse(
                BuiltInSiteProfiles.JSON.replaceFirst(
                    exact,
                    "\"filter\": \"\"",
                ),
            )
        }
    }

    @Test
    fun rejectsUnregisteredProtocolAndSha1WithoutLegacyCapability() {
        assertThrows(IllegalArgumentException::class.java) {
            SiteProfileCatalogParser.parse(
                BuiltInSiteProfiles.JSON.replace("miniapplet-autoscript-v1", "unregistered-adapter"),
            )
        }
        assertThrows(IllegalArgumentException::class.java) {
            SiteProfileCatalogParser.parse(
                BuiltInSiteProfiles.JSON.replace("[\"SIGN\", \"LEGACY_SHA1\"]", "[\"SIGN\"]"),
            )
        }
    }

    @Test
    fun rejectsOriginConfusionAndEndpointMetadata() {
        val attacks = listOf(
            "http://www.juntadeandalucia.es",
            "https://user@www.juntadeandalucia.es",
            "https://www.juntadeandalucia.es:8443",
            "https://www.juntadeandalucia.es./",
            "https://127.0.0.1",
            "https://*.juntadeandalucia.es",
        )
        attacks.forEach { attack ->
            assertThrows(attack, IllegalArgumentException::class.java) {
                SiteProfileCatalogParser.parse(
                    BuiltInSiteProfiles.JSON.replaceFirst("https://www.juntadeandalucia.es\"", "$attack\""),
                )
            }
        }
        assertThrows(IllegalArgumentException::class.java) {
            SiteProfileCatalogParser.parse(
                BuiltInSiteProfiles.JSON.replace("TriPhaseSignatureService\"", "TriPhaseSignatureService?override=1\""),
            )
        }
    }

    @Test
    fun releaseDoesNotActivateQaOnlyProfileAndResolutionIsExact() {
        val qaCatalog = SiteProfileCatalogParser.parse(
            BuiltInSiteProfiles.JSON.replace("\"activation\": \"ENABLED\"", "\"activation\": \"QA_ONLY\""),
        )
        val release = SiteProfileRegistry(qaCatalog, BuildTrustPolicy.RELEASE)
        val qa = SiteProfileRegistry(qaCatalog, BuildTrustPolicy.QA)

        assertNull(release.resolve(URI("https://www.juntadeandalucia.es/path")))
        assertEquals(
            TrustMode.TRUSTED_SIGNING,
            qa.resolve(URI("https://www.juntadeandalucia.es/path"))?.trustMode,
        )
        assertNull(qa.resolve(URI("https://www.juntadeandalucia.es.evil.example/")))
        assertNull(qa.resolve(URI("https://www.juntadeandalucia.es:8443/")))
        assertEquals(
            TrustMode.BROWSE_ONLY,
            qa.resolve(URI("https://sede.juntadeandalucia.es/path"))?.trustMode,
        )
        assertEquals(
            TrustMode.TRUSTED_BROWSE,
            qa.resolveRedirect(
                ProfileId("junta-andalucia"),
                URI("https://sede.juntadeandalucia.es/path"),
            )?.trustMode,
        )
        assertNull(release.profile(ProfileId("junta-andalucia")))
        assertTrue(release.profileMetadata(ProfileId("junta-andalucia")) != null)
        assertTrue(qa.profile(ProfileId("junta-andalucia")) != null)
    }

    @Test
    fun `Alava Registro Comun profile exposes only exact QA navigation`() {
        val profileId = ProfileId("diputacion-alava-registro-comun")
        val start = URI("https://egoitza.araba.eus/izapidetu/at/01/es/0000301")
        val profile = BuiltInSiteProfiles.catalog.profiles.single { it.profileId == profileId }

        assertEquals(CompatibilityStatus.VERIFIED_CONTRACT, profile.compatibilityStatus)
        assertEquals(ProfileActivation.QA_ONLY, profile.activation)
        assertEquals(start, profile.startUrl)
        assertEquals(setOf(ExactOrigin.parse("https://egoitza.araba.eus")), profile.initiatorOrigins)
        assertTrue(profile.redirectOrigins.isEmpty())
        assertTrue(profile.trustedBrowseOrigins.isEmpty())
        assertTrue(profile.endpoints.isEmpty())
        assertTrue(profile.operationPolicies.isEmpty())
        assertTrue(profile.capabilities.isEmpty())
        assertNull(profile.clientAuthPolicy)
        assertEquals(setOf("RSA", "EC"), profile.certificateRules.allowedKeyAlgorithms)
        assertFalse(profile.certificateRules.requireDigitalSignatureKeyUsage)
        assertEquals(2, profile.evidence.size)

        assertNull(BuiltInSiteProfiles.releaseRegistry.profile(profileId))
        assertNull(BuiltInSiteProfiles.releaseRegistry.resolve(start))
        assertEquals(profile, BuiltInSiteProfiles.qaRegistry.profile(profileId))
        assertEquals(TrustMode.TRUSTED_BROWSE, BuiltInSiteProfiles.qaRegistry.resolve(start)?.trustMode)
        assertNull(BuiltInSiteProfiles.qaRegistry.resolve(URI("https://egoitza.araba.eus.evil.example/")))
        assertNull(BuiltInSiteProfiles.qaRegistry.resolve(URI("https://egoitza.araba.eus:444/")))
    }

    @Test
    fun `Castilla Leon QUJU public form profile is QA only and exposes no sensitive capability`() {
        val profileId = ProfileId("castilla-leon-quju-public")
        val start = URI("https://presidencia.jcyl.es/QUJU?O=1")
        val profile = BuiltInSiteProfiles.catalog.profiles.single { it.profileId == profileId }

        assertEquals(1, profile.profileVersion)
        assertEquals(CompatibilityStatus.VERIFIED_CONTRACT, profile.compatibilityStatus)
        assertEquals(ProfileActivation.QA_ONLY, profile.activation)
        assertEquals(start, profile.startUrl)
        assertEquals(setOf(ExactOrigin.parse("https://presidencia.jcyl.es")), profile.initiatorOrigins)
        assertTrue(profile.redirectOrigins.isEmpty())
        assertTrue(profile.trustedBrowseOrigins.isEmpty())
        assertTrue(profile.endpoints.isEmpty())
        assertTrue(profile.operationPolicies.isEmpty())
        assertTrue(profile.capabilities.isEmpty())
        assertNull(profile.clientAuthPolicy)
        assertEquals(setOf("RSA", "EC"), profile.certificateRules.allowedKeyAlgorithms)
        assertEquals(false, profile.certificateRules.requireDigitalSignatureKeyUsage)
        assertEquals(3, profile.evidence.size)

        assertNull(BuiltInSiteProfiles.releaseRegistry.profile(profileId))
        assertNull(BuiltInSiteProfiles.releaseRegistry.resolve(start))
        assertEquals(profile, BuiltInSiteProfiles.qaRegistry.profile(profileId))
        assertEquals(TrustMode.TRUSTED_BROWSE, BuiltInSiteProfiles.qaRegistry.resolve(start)?.trustMode)
        assertNull(BuiltInSiteProfiles.qaRegistry.resolve(URI("https://presidencia.jcyl.es.evil.example/")))
        assertNull(BuiltInSiteProfiles.qaRegistry.resolve(URI("https://presidencia.jcyl.es:444/")))
    }

    @Test
    fun `SEPES Sisgest consultation profile permits only QA client TLS`() {
        val profileId = ProfileId("sepes-transportes-public-complaints")
        val start = URI("https://sede.transportes.gob.es/MFOM.Sisgest/autenticacion.aspx")
        val profile = BuiltInSiteProfiles.catalog.profiles.single { it.profileId == profileId }

        assertEquals(2, profile.profileVersion)
        assertEquals(CompatibilityStatus.VERIFIED_CONTRACT, profile.compatibilityStatus)
        assertEquals(ProfileActivation.QA_ONLY, profile.activation)
        assertEquals(start, profile.startUrl)
        assertEquals(setOf(ExactOrigin.parse("https://sede.transportes.gob.es")), profile.initiatorOrigins)
        assertEquals(setOf(ExactOrigin.parse("https://pasarela.clave.gob.es")), profile.redirectOrigins)
        assertTrue(profile.trustedBrowseOrigins.isEmpty())
        assertTrue(profile.endpoints.isEmpty())
        assertTrue(profile.operationPolicies.isEmpty())
        assertEquals(setOf(Capability.CLIENT_TLS_AUTH), profile.capabilities)
        assertNotNull(profile.clientAuthPolicy)
        assertEquals(setOf("RSA", "EC"), profile.certificateRules.allowedKeyAlgorithms)
        assertTrue(profile.certificateRules.requireDigitalSignatureKeyUsage)
        assertEquals(7, profile.evidence.size)

        assertNull(BuiltInSiteProfiles.releaseRegistry.profile(profileId))
        assertNull(BuiltInSiteProfiles.releaseRegistry.resolve(start))
        assertEquals(profile, BuiltInSiteProfiles.qaRegistry.profile(profileId))
        assertNull(BuiltInSiteProfiles.qaRegistry.resolve(start))
        assertEquals(profileId, BuiltInSiteProfiles.qaRegistry.resolveForProfile(profileId, start)?.profile?.profileId)
        assertEquals(
            TrustMode.TRUSTED_CLIENT_AUTH,
            BuiltInSiteProfiles.qaRegistry.resolveForProfile(profileId, start)?.trustMode,
        )
        assertNull(BuiltInSiteProfiles.qaRegistry.resolveForProfile(profileId, URI("https://sede.transportes.gob.es.evil.example/")))
        assertNull(BuiltInSiteProfiles.qaRegistry.resolveForProfile(profileId, URI("https://sede.transportes.gob.es:444/")))
    }

    @Test
    fun `shared Transportes origin is reviewed only for the exact SEPES pair`() {
        val parsed = SiteProfileCatalogParser.parse(BuiltInSiteProfiles.JSON)
        assertTrue(parsed.profiles.any { it.profileId == ProfileId("transportes-qys-cert-login") })
        assertTrue(parsed.profiles.any { it.profileId == ProfileId("sepes-transportes-public-complaints") })

        val exactId = "\"profileId\": \"sepes-transportes-public-complaints\""
        val unreviewedId = "\"profileId\": \"sepes-transportes-public-complaints-copy\""
        assertTrue(BuiltInSiteProfiles.JSON.contains(exactId))
        assertThrows(IllegalArgumentException::class.java) {
            SiteProfileCatalogParser.parse(BuiltInSiteProfiles.JSON.replaceFirst(exactId, unreviewedId))
        }
    }

    @Test
    fun `BOE certificate login pins its QA source and TLS target`() {
        val profileId = ProfileId("boe-sede-public-home")
        val start = URI("https://extranet.boe.es/anuncios/")
        val profile = BuiltInSiteProfiles.catalog.profiles.single { it.profileId == profileId }

        assertEquals(2, profile.profileVersion)
        assertEquals(CompatibilityStatus.VERIFIED_CONTRACT, profile.compatibilityStatus)
        assertEquals(ProfileActivation.QA_ONLY, profile.activation)
        assertEquals(start, profile.startUrl)
        assertEquals(setOf(ExactOrigin.parse("https://extranet.boe.es")), profile.initiatorOrigins)
        assertTrue(profile.redirectOrigins.isEmpty())
        assertTrue(profile.trustedBrowseOrigins.isEmpty())
        assertTrue(profile.endpoints.isEmpty())
        assertTrue(profile.operationPolicies.isEmpty())
        assertEquals(setOf(Capability.CLIENT_TLS_AUTH), profile.capabilities)
        assertNotNull(profile.clientAuthPolicy)
        assertEquals(setOf("RSA", "EC"), profile.certificateRules.allowedKeyAlgorithms)
        assertTrue(profile.certificateRules.requireDigitalSignatureKeyUsage)
        assertEquals(5, profile.evidence.size)

        assertNull(BuiltInSiteProfiles.releaseRegistry.profile(profileId))
        assertNull(BuiltInSiteProfiles.releaseRegistry.resolve(start))
        assertEquals(profile, BuiltInSiteProfiles.qaRegistry.profile(profileId))
        assertEquals(TrustMode.TRUSTED_CLIENT_AUTH, BuiltInSiteProfiles.qaRegistry.resolve(start)?.trustMode)
        assertNull(BuiltInSiteProfiles.qaRegistry.resolve(URI("https://www.boe.es/informacion/index.php")))
        assertNull(BuiltInSiteProfiles.qaRegistry.resolve(URI("https://www.boe.es.evil.example/")))
        assertNull(BuiltInSiteProfiles.qaRegistry.resolve(URI("https://www.boe.es:444/")))
    }

    @Test
    fun `Fondos SIRIOWS profile permits only QA client TLS`() {
        val profileId = ProfileId("fondos-europeos-sede-public-home")
        val start = URI("https://webpub2.igae.hacienda.gob.es/accesoremoto/frmExecAPP.aspx?code=SIRIOWS&ta=ANO")
        val profile = BuiltInSiteProfiles.catalog.profiles.single { it.profileId == profileId }

        assertEquals(2, profile.profileVersion)
        assertEquals(CompatibilityStatus.VERIFIED_CONTRACT, profile.compatibilityStatus)
        assertEquals(ProfileActivation.QA_ONLY, profile.activation)
        assertEquals(start, profile.startUrl)
        assertEquals(setOf(ExactOrigin.parse("https://webpub2.igae.hacienda.gob.es")), profile.initiatorOrigins)
        assertEquals(setOf(ExactOrigin.parse("https://pasarela.clave.gob.es")), profile.redirectOrigins)
        assertTrue(profile.trustedBrowseOrigins.isEmpty())
        assertTrue(profile.endpoints.isEmpty())
        assertTrue(profile.operationPolicies.isEmpty())
        assertEquals(setOf(Capability.CLIENT_TLS_AUTH), profile.capabilities)
        assertNotNull(profile.clientAuthPolicy)
        assertEquals(setOf("RSA", "EC"), profile.certificateRules.allowedKeyAlgorithms)
        assertTrue(profile.certificateRules.requireDigitalSignatureKeyUsage)
        assertEquals(7, profile.evidence.size)

        assertNull(BuiltInSiteProfiles.releaseRegistry.profile(profileId))
        assertNull(BuiltInSiteProfiles.releaseRegistry.resolve(start))
        assertEquals(profile, BuiltInSiteProfiles.qaRegistry.profile(profileId))
        assertEquals(TrustMode.TRUSTED_CLIENT_AUTH, BuiltInSiteProfiles.qaRegistry.resolve(start)?.trustMode)
        assertNull(BuiltInSiteProfiles.qaRegistry.resolve(URI("https://sedefondoscomunitarios.gob.es.evil.example/")))
        assertNull(BuiltInSiteProfiles.qaRegistry.resolve(URI("https://sedefondoscomunitarios.gob.es:444/")))
        assertNull(BuiltInSiteProfiles.qaRegistry.resolve(URI("https://tramitesfondoseuropeos.hacienda.gob.es/dossier")))
    }

    @Test
    fun `Portal Funciona public home profile is QA only and exposes no sensitive capability`() {
        val profileId = ProfileId("portal-funciona-public-home")
        val start = URI("https://sede.funciona.gob.es/es/home")
        val profile = BuiltInSiteProfiles.catalog.profiles.single { it.profileId == profileId }

        assertEquals(1, profile.profileVersion)
        assertEquals(CompatibilityStatus.VERIFIED_CONTRACT, profile.compatibilityStatus)
        assertEquals(ProfileActivation.QA_ONLY, profile.activation)
        assertEquals(start, profile.startUrl)
        assertEquals(setOf(ExactOrigin.parse("https://sede.funciona.gob.es")), profile.initiatorOrigins)
        assertTrue(profile.redirectOrigins.isEmpty())
        assertTrue(profile.trustedBrowseOrigins.isEmpty())
        assertTrue(profile.endpoints.isEmpty())
        assertTrue(profile.operationPolicies.isEmpty())
        assertTrue(profile.capabilities.isEmpty())
        assertNull(profile.clientAuthPolicy)
        assertEquals(setOf("RSA", "EC"), profile.certificateRules.allowedKeyAlgorithms)
        assertEquals(false, profile.certificateRules.requireDigitalSignatureKeyUsage)
        assertEquals(2, profile.evidence.size)

        assertNull(BuiltInSiteProfiles.releaseRegistry.profile(profileId))
        assertNull(BuiltInSiteProfiles.releaseRegistry.resolve(start))
        assertEquals(profile, BuiltInSiteProfiles.qaRegistry.profile(profileId))
        assertEquals(TrustMode.TRUSTED_BROWSE, BuiltInSiteProfiles.qaRegistry.resolve(start)?.trustMode)
        assertNull(BuiltInSiteProfiles.qaRegistry.resolve(URI("https://auth-api.redsara.es/")))
        assertNull(BuiltInSiteProfiles.qaRegistry.resolve(URI("https://autentica.redsara.es/")))
        assertNull(BuiltInSiteProfiles.qaRegistry.resolve(URI("https://sede.funciona.gob.es.evil.example/")))
        assertNull(BuiltInSiteProfiles.qaRegistry.resolve(URI("https://sede.funciona.gob.es:444/")))
    }

    @Test
    fun `DGSFP public Sede profile pins the QA only Clave certificate login`() {
        val profileId = ProfileId("dgsfp-sede-public-home")
        val start = URI("https://www.sededgsfp.gob.es/")
        val profile = BuiltInSiteProfiles.catalog.profiles.single { it.profileId == profileId }

        assertEquals(2, profile.profileVersion)
        assertEquals(CompatibilityStatus.VERIFIED_CONTRACT, profile.compatibilityStatus)
        assertEquals(ProfileActivation.QA_ONLY, profile.activation)
        assertEquals(start, profile.startUrl)
        assertEquals(setOf(ExactOrigin.parse("https://www.sededgsfp.gob.es")), profile.initiatorOrigins)
        assertEquals(setOf(ExactOrigin.parse("https://pasarela.clave.gob.es")), profile.redirectOrigins)
        assertTrue(profile.trustedBrowseOrigins.isEmpty())
        assertTrue(profile.endpoints.isEmpty())
        assertTrue(profile.operationPolicies.isEmpty())
        assertEquals(setOf(Capability.CLIENT_TLS_AUTH), profile.capabilities)
        assertEquals(HttpMethod.POST, profile.clientAuthPolicy?.requestMethod)
        assertEquals(setOf("RSA", "EC"), profile.certificateRules.allowedKeyAlgorithms)
        assertTrue(profile.certificateRules.requireDigitalSignatureKeyUsage)
        assertEquals(9, profile.evidence.size)

        assertNull(BuiltInSiteProfiles.releaseRegistry.profile(profileId))
        assertNull(BuiltInSiteProfiles.releaseRegistry.resolve(start))
        assertEquals(profile, BuiltInSiteProfiles.qaRegistry.profile(profileId))
        assertEquals(TrustMode.TRUSTED_CLIENT_AUTH, BuiltInSiteProfiles.qaRegistry.resolve(start)?.trustMode)
        assertNull(BuiltInSiteProfiles.qaRegistry.resolveForProfile(profileId, URI("https://www.sededgsfp.gob.es.evil.example/")))
        assertNull(BuiltInSiteProfiles.qaRegistry.resolveForProfile(profileId, URI("https://www.sededgsfp.gob.es:444/")))
    }

    @Test
    fun `DGOJ login profile is QA only and pins the exact Clave v2 certificate route`() {
        val profileId = ProfileId("dgoj-public-navigation")
        val start = URI("https://sede.ordenacionjuego.gob.es/tramite/login/inicio.jjsp?iA=no&limpiarBusqueda=S")
        val profile = BuiltInSiteProfiles.catalog.profiles.single { it.profileId == profileId }

        assertEquals(2, profile.profileVersion)
        assertEquals(CompatibilityStatus.VERIFIED_CONTRACT, profile.compatibilityStatus)
        assertEquals(ProfileActivation.QA_ONLY, profile.activation)
        assertEquals(start, profile.startUrl)
        assertEquals(setOf(ExactOrigin.parse("https://sede.ordenacionjuego.gob.es")), profile.initiatorOrigins)
        assertEquals(setOf(ExactOrigin.parse("https://pasarela.clave.gob.es")), profile.redirectOrigins)
        assertTrue(profile.trustedBrowseOrigins.isEmpty())
        assertTrue(profile.endpoints.isEmpty())
        assertTrue(profile.operationPolicies.isEmpty())
        assertEquals(setOf(Capability.CLIENT_TLS_AUTH), profile.capabilities)
        assertEquals(
            ClientAuthPolicy(
                transitionMode = ClientAuthTransitionMode.IN_PLACE_FROM_SOURCE,
                requestOrigins = setOf(ExactOrigin.parse("https://pasarela-ident.clave.gob.es")),
                sourceUrls = setOf(URI("https://pasarela.clave.gob.es/Proxy2/ServiceRedirect")),
                requestPath = "/IdP2/AuthenticateCitizen",
                fixedQueryParameters = emptyMap(), requiredEphemeralQueryParameters = emptySet(),
                allowEmptyIssuerList = true, grantTtlSeconds = 15, requestMethod = HttpMethod.POST,
                requestPort = 443, requireOfferedKeyTypeMatch = true, requireTlsClientAuthExtendedKeyUsage = true,
            ),
            profile.clientAuthPolicy,
        )
        assertEquals(setOf("RSA", "EC"), profile.certificateRules.allowedKeyAlgorithms)
        assertTrue(profile.certificateRules.requireDigitalSignatureKeyUsage)
        assertEquals(8, profile.evidence.size)
        assertEquals(
            mapOf(
                "https://sede.ordenacionjuego.gob.es/" to "2026-08-24",
                "https://sede.ordenacionjuego.gob.es/es/firma" to "2026-08-24",
                "https://sede.ordenacionjuego.gob.es/aviso-navegador-chrome" to "2026-08-24",
                start.toASCIIString() to "2026-11-08",
                "https://sede.ordenacionjuego.gob.es/tramite/clave/lanzarPeticionClave.jjsp" to "2026-11-08",
                "https://pasarela.clave.gob.es/Proxy2/ServiceProvider" to "2026-11-08",
                "https://pasarela.clave.gob.es/Proxy2/ServiceRedirect" to "2026-11-08",
                "https://pasarela-ident.clave.gob.es/IdP2/AuthenticateCitizen" to "2026-11-08",
            ),
            profile.evidence.associate { it.url.toASCIIString() to it.reviewedOn.toString() },
        )

        assertNull(BuiltInSiteProfiles.releaseRegistry.profile(profileId))
        assertNull(BuiltInSiteProfiles.releaseRegistry.resolve(start))
        assertEquals(profile, BuiltInSiteProfiles.qaRegistry.profile(profileId))
        assertEquals(TrustMode.TRUSTED_CLIENT_AUTH, BuiltInSiteProfiles.qaRegistry.resolve(start)?.trustMode)
        assertNull(BuiltInSiteProfiles.qaRegistry.resolve(URI("https://sede.ordenacionjuego.gob.es.evil.example/")))
        assertNull(BuiltInSiteProfiles.qaRegistry.resolve(URI("https://sede.ordenacionjuego.gob.es:444/")))
    }

    @Test
    fun `CNMV public Sede profile is QA only and exposes no sensitive capability`() {
        val profileId = ProfileId("cnmv-sede-public-home")
        val start = URI("https://sede.cnmv.gob.es/sedecnmv/sedeelectronica.aspx")
        val profile = BuiltInSiteProfiles.catalog.profiles.single { it.profileId == profileId }

        assertEquals(2, profile.profileVersion)
        assertEquals(CompatibilityStatus.VERIFIED_CONTRACT, profile.compatibilityStatus)
        assertEquals(ProfileActivation.QA_ONLY, profile.activation)
        assertEquals(start, profile.startUrl)
        assertEquals(setOf(ExactOrigin.parse("https://sede.cnmv.gob.es")), profile.initiatorOrigins)
        assertEquals(
            setOf(ExactOrigin.parse("https://pasarela.clave.gob.es")),
            profile.redirectOrigins,
        )
        assertTrue(profile.trustedBrowseOrigins.isEmpty())
        assertTrue(profile.endpoints.isEmpty())
        assertTrue(profile.operationPolicies.isEmpty())
        assertEquals(setOf(Capability.CLIENT_TLS_AUTH), profile.capabilities)
        assertEquals(ClientAuthTransitionMode.IN_PLACE_FROM_SOURCE, profile.clientAuthPolicy?.transitionMode)
        assertEquals(HttpMethod.POST, profile.clientAuthPolicy?.requestMethod)
        assertEquals(setOf("RSA", "EC"), profile.certificateRules.allowedKeyAlgorithms)
        assertTrue(profile.certificateRules.requireDigitalSignatureKeyUsage)
        assertEquals(5, profile.evidence.size)

        assertNull(BuiltInSiteProfiles.releaseRegistry.profile(profileId))
        assertNull(BuiltInSiteProfiles.releaseRegistry.resolve(start))
        assertEquals(profile, BuiltInSiteProfiles.qaRegistry.profile(profileId))
        assertEquals(TrustMode.TRUSTED_CLIENT_AUTH, BuiltInSiteProfiles.qaRegistry.resolve(start)?.trustMode)
        assertNull(BuiltInSiteProfiles.qaRegistry.resolve(URI("https://sede.cnmv.gob.es.evil.example/")))
        assertNull(BuiltInSiteProfiles.qaRegistry.resolve(URI("https://sede.cnmv.gob.es:444/")))
    }

    @Test
    fun `Comunidad Madrid Registro General profile exposes only exact public QA navigation`() {
        val profileId = ProfileId("comunidad-madrid-registro-general")
        val start = URI("https://gestiona.comunidad.madrid/ereg_virtual_presenta/run/j/InicioDistribuidor.icm")
        val profile = BuiltInSiteProfiles.catalog.profiles.single { it.profileId == profileId }

        assertEquals(CompatibilityStatus.VERIFIED_CONTRACT, profile.compatibilityStatus)
        assertEquals(ProfileActivation.QA_ONLY, profile.activation)
        assertEquals(start, profile.startUrl)
        assertEquals(setOf(ExactOrigin.parse("https://gestiona.comunidad.madrid")), profile.initiatorOrigins)
        assertTrue(profile.redirectOrigins.isEmpty())
        assertTrue(profile.trustedBrowseOrigins.isEmpty())
        assertTrue(profile.endpoints.isEmpty())
        assertTrue(profile.operationPolicies.isEmpty())
        assertTrue(profile.capabilities.isEmpty())
        assertNull(profile.clientAuthPolicy)
        assertEquals(setOf("RSA", "EC"), profile.certificateRules.allowedKeyAlgorithms)
        assertFalse(profile.certificateRules.requireDigitalSignatureKeyUsage)
        assertEquals(2, profile.evidence.size)
        assertNull(BuiltInSiteProfiles.releaseRegistry.profile(profileId))
        assertNull(BuiltInSiteProfiles.releaseRegistry.resolve(start))
        assertEquals(profile, BuiltInSiteProfiles.qaRegistry.profile(profileId))
        assertEquals(TrustMode.TRUSTED_BROWSE, BuiltInSiteProfiles.qaRegistry.resolve(start)?.trustMode)
        assertNull(BuiltInSiteProfiles.qaRegistry.resolve(URI("https://gestiona.comunidad.madrid.evil.example/")))
        assertNull(BuiltInSiteProfiles.qaRegistry.resolve(URI("https://gestiona.comunidad.madrid:444/")))
    }

    @Test
    fun `Junta VEA PEG profile exposes only exact public QA navigation`() {
        val profileId = ProfileId("junta-andalucia-vea-peg")
        val start = URI("https://veaja.cloud.juntadeandalucia.es/inicio/procedimiento-detalle/PEG_VEA")
        val profile = BuiltInSiteProfiles.catalog.profiles.single { it.profileId == profileId }

        assertEquals(CompatibilityStatus.VERIFIED_CONTRACT, profile.compatibilityStatus)
        assertEquals(ProfileActivation.QA_ONLY, profile.activation)
        assertEquals(start, profile.startUrl)
        assertEquals(setOf(ExactOrigin.parse("https://veaja.cloud.juntadeandalucia.es")), profile.initiatorOrigins)
        assertTrue(profile.redirectOrigins.isEmpty())
        assertTrue(profile.trustedBrowseOrigins.isEmpty())
        assertTrue(profile.endpoints.isEmpty())
        assertTrue(profile.operationPolicies.isEmpty())
        assertEquals(setOf(Capability.CLIENT_TLS_AUTH), profile.capabilities)
        checkNotNull(profile.clientAuthPolicy)
        assertEquals(setOf("RSA", "EC"), profile.certificateRules.allowedKeyAlgorithms)
        assertFalse(profile.certificateRules.requireDigitalSignatureKeyUsage)
        assertNull(BuiltInSiteProfiles.releaseRegistry.profile(profileId))
        assertEquals(TrustMode.TRUSTED_CLIENT_AUTH, BuiltInSiteProfiles.qaRegistry.resolve(start)?.trustMode)
    }

    @Test
    fun `Junta VEA request continuation cannot weaken the confirmed TLS tuple`() {
        val exactContinuation =
            "{\"origin\":\"https://ws235-3.juntadeandalucia.es\"," +
                "\"path\":\"/authenticationFacade/4\"," +
                "\"fixedQueryParameters\":{\"action\":\"validateCert\",\"appId\":\"CHIE.VEA\"," +
                "\"comeBackURL\":\"aHR0cHM6Ly9hcGktdmVhamEuY2xvdWQuanVudGFkZWFuZGFsdWNpYS5lcy9hdXRoL3JldHVybkxvZ2lu\"}," +
                "\"requiredEphemeralQueryParameters\":[\"ticketId\",\"webSessionId\"]}"
        assertTrue(BuiltInSiteProfiles.JSON.contains(exactContinuation))

        listOf(
            exactContinuation.replace("CHIE.VEA", "OTHER.APP"),
            exactContinuation.replace(
                "[\"ticketId\",\"webSessionId\"]",
                "[\"ticketId\"]",
            ),
        ).forEach { weakenedContinuation ->
            val mutated = BuiltInSiteProfiles.JSON.replaceFirst(exactContinuation, weakenedContinuation)
            assertThrows(IllegalArgumentException::class.java) {
                SiteProfileCatalogParser.parse(mutated)
            }
        }
    }

    @Test
    fun `El Hierro Solicitud general pins only the reviewed QA Clave certificate login`() {
        val profileId = ProfileId("el-hierro-solicitud-general")
        val start = URI("https://elhierro.sedelectronica.es/catalog/tw/7944e884-3b98-48fc-abcd-d6db6ef8bd71")
        val profile = BuiltInSiteProfiles.catalog.profiles.single { it.profileId == profileId }
        assertEquals(CompatibilityStatus.VERIFIED_CONTRACT, profile.compatibilityStatus)
        assertEquals(ProfileActivation.QA_ONLY, profile.activation)
        assertEquals(start, profile.startUrl)
        assertEquals(setOf(ExactOrigin.parse("https://elhierro.sedelectronica.es")), profile.initiatorOrigins)
        assertEquals(setOf(ExactOrigin.parse("https://pasarela.clave.gob.es")), profile.redirectOrigins)
        assertTrue(profile.trustedBrowseOrigins.isEmpty())
        assertTrue(profile.endpoints.isEmpty())
        assertTrue(profile.operationPolicies.isEmpty())
        assertEquals(setOf(Capability.CLIENT_TLS_AUTH), profile.capabilities)
        assertEquals(HttpMethod.POST, profile.clientAuthPolicy?.requestMethod)
        assertEquals(setOf("RSA", "EC"), profile.certificateRules.allowedKeyAlgorithms)
        assertTrue(profile.certificateRules.requireDigitalSignatureKeyUsage)
        assertEquals(7, profile.evidence.size)
        assertEquals(
            TrustMode.TRUSTED_CLIENT_AUTH,
            BuiltInSiteProfiles.qaRegistry.resolveForProfile(profileId, start)?.trustMode,
        )
        assertNull(BuiltInSiteProfiles.releaseRegistry.profile(profileId))
        assertNull(BuiltInSiteProfiles.releaseRegistry.resolve(start))
    }

    @Test
    fun `Diputacion Avila Instancia General arms client TLS only on the reviewed Clave POST`() {
        val profileId = ProfileId("diputacion-avila-instancia-general")
        val start = URI("https://diputacionavila.sedelectronica.es/catalog/tw/5161fa8d-970e-4b48-a506-b2ac34ceafe5")
        val profile = BuiltInSiteProfiles.catalog.profiles.single { it.profileId == profileId }
        val policy = checkNotNull(profile.clientAuthPolicy)
        assertEquals(CompatibilityStatus.VERIFIED_CONTRACT, profile.compatibilityStatus)
        assertEquals(ProfileActivation.QA_ONLY, profile.activation)
        assertEquals(start, profile.startUrl)
        assertEquals(setOf(ExactOrigin.parse("https://diputacionavila.sedelectronica.es")), profile.initiatorOrigins)
        assertEquals(
            setOf(
                ExactOrigin.parse("https://pasarela.clave.gob.es"),
                ExactOrigin.parse("https://pasarela-ident-sistemas.clave.gob.es"),
            ),
            profile.redirectOrigins,
        )
        assertTrue(profile.endpoints.isEmpty())
        assertTrue(profile.operationPolicies.isEmpty())
        assertEquals(setOf(Capability.CLIENT_TLS_AUTH), profile.capabilities)
        assertEquals(ClientAuthTransitionMode.IN_PLACE_FROM_SOURCE, policy.transitionMode)
        assertEquals(setOf(URI("https://pasarela.clave.gob.es/Proxy2/ServiceRedirect")), policy.sourceUrls)
        assertEquals(setOf(ExactOrigin.parse("https://pasarela-ident.clave.gob.es")), policy.requestOrigins)
        assertEquals("/IdP2/AuthenticateCitizen", policy.requestPath)
        assertEquals(setOf("RSA", "EC"), profile.certificateRules.allowedKeyAlgorithms)
        assertTrue(profile.certificateRules.requireDigitalSignatureKeyUsage)
        assertEquals(5, profile.evidence.size)
        assertEquals(TrustMode.TRUSTED_CLIENT_AUTH, BuiltInSiteProfiles.qaRegistry.resolve(start)?.trustMode)
        assertNull(BuiltInSiteProfiles.releaseRegistry.profile(profileId))
        assertNull(BuiltInSiteProfiles.releaseRegistry.resolve(start))
    }

    @Test
    fun `Diputacion Cadiz solicitud generica arms client TLS only on the reviewed Clave POST`() {
        val profileId = ProfileId("diputacion-cadiz-solicitud-generica")
        val start = URI("https://sede.dipucadiz.es/group/sede/detalle-tramite?tramite=761")
        val profile = BuiltInSiteProfiles.catalog.profiles.single { it.profileId == profileId }

        assertEquals(2, profile.profileVersion)
        assertEquals("Diputación Provincial de Cádiz — Solicitud, escrito o comunicación genérica", profile.displayName)
        assertEquals(CompatibilityStatus.VERIFIED_CONTRACT, profile.compatibilityStatus)
        assertEquals(ProfileActivation.QA_ONLY, profile.activation)
        assertEquals(start, profile.startUrl)
        assertEquals(setOf(ExactOrigin.parse("https://sede.dipucadiz.es")), profile.initiatorOrigins)
        assertEquals(
            setOf(
                ExactOrigin.parse("https://sso.dipucadiz.es"),
                ExactOrigin.parse("https://pasarela.clave.gob.es"),
            ),
            profile.redirectOrigins,
        )
        assertTrue(profile.trustedBrowseOrigins.isEmpty())
        assertTrue(profile.endpoints.isEmpty())
        assertTrue(profile.operationPolicies.isEmpty())
        assertEquals(setOf(Capability.CLIENT_TLS_AUTH), profile.capabilities)
        val policy = checkNotNull(profile.clientAuthPolicy)
        assertEquals(ClientAuthTransitionMode.IN_PLACE_FROM_SOURCE, policy.transitionMode)
        assertEquals(setOf(URI("https://pasarela.clave.gob.es/Proxy2/ServiceRedirect")), policy.sourceUrls)
        assertEquals(setOf(ExactOrigin.parse("https://pasarela-ident.clave.gob.es")), policy.requestOrigins)
        assertEquals("/IdP2/AuthenticateCitizen", policy.requestPath)
        assertEquals(setOf("RSA", "EC"), profile.certificateRules.allowedKeyAlgorithms)
        assertTrue(profile.certificateRules.requireDigitalSignatureKeyUsage)
        assertEquals(6, profile.evidence.size)
        assertTrue(profile.evidence.all { it.reviewedOn.toString() in setOf("2026-08-21", "2026-11-07") })
        assertEquals(TrustMode.TRUSTED_CLIENT_AUTH, BuiltInSiteProfiles.qaRegistry.resolve(start)?.trustMode)
        assertEquals(
            TrustMode.BROWSE_ONLY,
            BuiltInSiteProfiles.qaRegistry.resolveForProfile(
                profileId,
                URI("https://pasarela.clave.gob.es/Proxy2/ServiceProvider"),
            )?.trustMode,
        )
        assertNull(BuiltInSiteProfiles.releaseRegistry.profile(profileId))
        assertNull(BuiltInSiteProfiles.releaseRegistry.resolve(start))
        assertNull(BuiltInSiteProfiles.qaRegistry.resolve(URI("https://sede.dipucadiz.es.evil.example/")))
    }

    @Test
    fun `Diputacion Caceres Instancia General arms client TLS only on the reviewed Clave POST`() {
        val profileId = ProfileId("diputacion-caceres-instancia-general")
        val start = URI("https://sede.dip-caceres.es/carpetaCiudadano/fichaprocedimiento.do?idproc=341")
        val profile = BuiltInSiteProfiles.catalog.profiles.single { it.profileId == profileId }

        assertEquals(2, profile.profileVersion)
        assertEquals("Diputación Provincial de Cáceres — Instancia General Normalizada", profile.displayName)
        assertEquals(CompatibilityStatus.VERIFIED_CONTRACT, profile.compatibilityStatus)
        assertEquals(ProfileActivation.QA_ONLY, profile.activation)
        assertEquals(start, profile.startUrl)
        assertEquals(setOf(ExactOrigin.parse("https://sede.dip-caceres.es")), profile.initiatorOrigins)
        assertEquals(setOf(ExactOrigin.parse("https://pasarela.clave.gob.es")), profile.redirectOrigins)
        assertTrue(profile.trustedBrowseOrigins.isEmpty())
        assertTrue(profile.endpoints.isEmpty())
        assertTrue(profile.operationPolicies.isEmpty())
        assertEquals(setOf(Capability.CLIENT_TLS_AUTH), profile.capabilities)
        val policy = checkNotNull(profile.clientAuthPolicy)
        assertEquals(ClientAuthTransitionMode.IN_PLACE_FROM_SOURCE, policy.transitionMode)
        assertEquals(setOf(URI("https://pasarela.clave.gob.es/Proxy2/ServiceRedirect")), policy.sourceUrls)
        assertEquals(setOf(ExactOrigin.parse("https://pasarela-ident.clave.gob.es")), policy.requestOrigins)
        assertEquals("/IdP2/AuthenticateCitizen", policy.requestPath)
        assertEquals(setOf("RSA", "EC"), profile.certificateRules.allowedKeyAlgorithms)
        assertTrue(profile.certificateRules.requireDigitalSignatureKeyUsage)
        assertEquals(8, profile.evidence.size)
        assertTrue(profile.evidence.all { it.reviewedOn.toString() in setOf("2026-08-21", "2026-11-07") })
        assertEquals(TrustMode.TRUSTED_CLIENT_AUTH, BuiltInSiteProfiles.qaRegistry.resolve(start)?.trustMode)
        assertEquals(
            TrustMode.BROWSE_ONLY,
            BuiltInSiteProfiles.qaRegistry.resolveForProfile(
                profileId,
                URI("https://pasarela.clave.gob.es/Proxy2/ServiceProvider"),
            )?.trustMode,
        )
        assertNull(BuiltInSiteProfiles.releaseRegistry.profile(profileId))
        assertNull(BuiltInSiteProfiles.releaseRegistry.resolve(start))
        assertNull(BuiltInSiteProfiles.qaRegistry.resolve(URI("https://sede.dip-caceres.es.evil.example/")))
    }


    @Test
    fun enairePinsTheExactReviewedClaveV2Contract() {
        val profileId = ProfileId("enaire-sede-public")
        val start = URI("https://enaire.sede.gob.es/")
        val profile = BuiltInSiteProfiles.catalog.profiles.single { it.profileId == profileId }

        assertEquals(2, profile.profileVersion)
        assertEquals("ENAIRE — Sede electrónica pública", profile.displayName)
        assertEquals(CompatibilityStatus.VERIFIED_CONTRACT, profile.compatibilityStatus)
        assertEquals(ProfileActivation.QA_ONLY, profile.activation)
        assertEquals(start, profile.startUrl)
        assertEquals(setOf(ExactOrigin.parse("https://enaire.sede.gob.es")), profile.initiatorOrigins)
        assertEquals(setOf(ExactOrigin.parse("https://pasarela.clave.gob.es")), profile.redirectOrigins)
        assertTrue(profile.trustedBrowseOrigins.isEmpty())
        assertTrue(profile.endpoints.isEmpty())
        assertTrue(profile.operationPolicies.isEmpty())
        assertEquals(setOf(Capability.CLIENT_TLS_AUTH), profile.capabilities)
        assertEquals(
            ClientAuthPolicy(
                transitionMode = ClientAuthTransitionMode.IN_PLACE_FROM_SOURCE,
                requestOrigins = setOf(ExactOrigin.parse("https://pasarela-ident.clave.gob.es")),
                sourceUrls = setOf(URI("https://pasarela.clave.gob.es/Proxy2/ServiceRedirect")),
                requestPath = "/IdP2/AuthenticateCitizen",
                fixedQueryParameters = emptyMap(),
                requiredEphemeralQueryParameters = emptySet(),
                allowEmptyIssuerList = true,
                grantTtlSeconds = 15,
                requireOfferedKeyTypeMatch = true,
                requireTlsClientAuthExtendedKeyUsage = true,
                requestPort = 443,
                requestMethod = HttpMethod.POST,
            ),
            profile.clientAuthPolicy,
        )
        assertEquals(setOf("RSA", "EC"), profile.certificateRules.allowedKeyAlgorithms)
        assertTrue(profile.certificateRules.requireDigitalSignatureKeyUsage)
        assertEquals(
            listOf(
                "https://enaire.sede.gob.es/",
                "https://enaire.sede.gob.es/procedimientos",
                "https://enaire.sede.gob.es/Requisitos",
                "https://enaire.sede.gob.es/servicio?id=Validacion-de-certificados-y-firma",
            ).map { EvidenceReference(URI(it), LocalDate.parse("2026-08-24")) } + listOf(
                "https://enaire.sede.gob.es/claveproxy/clave/authenticate" +
                    "?returnUrl=https%3A%2F%2Fenaire.sede.gob.es%2F",
                "https://pasarela.clave.gob.es/Proxy2/ServiceProvider",
                "https://pasarela.clave.gob.es/Proxy2/ServiceRedirect",
                "https://pasarela-ident.clave.gob.es/IdP2/AuthenticateCitizen",
            ).map { EvidenceReference(URI(it), LocalDate.parse("2026-11-07")) },
            profile.evidence,
        )
    }

    @Test
    fun claveV2ProfilesResolveReviewedQaUrlsAndRejectForeignOrigins() {
        val profileIds = listOf(
            "enaire-sede-public", "iac-sede-public-navigation", "icac-sede-public-navigation",
            "diputacion-granada-sede-public", "ico-sede-public-navigation",
        ).map(::ProfileId)
        val qa = BuiltInSiteProfiles.qaRegistry
        val release = BuiltInSiteProfiles.releaseRegistry
        val source = URI("https://pasarela.clave.gob.es/Proxy2/ServiceRedirect")
        val provider = URI("https://pasarela.clave.gob.es/Proxy2/ServiceProvider")
        val request = URI("https://pasarela-ident.clave.gob.es/IdP2/AuthenticateCitizen")

        assertNull(qa.resolve(source))
        assertNull(qa.resolve(provider))
        assertNull(qa.resolve(request))
        profileIds.forEach { profileId ->
            val profile = BuiltInSiteProfiles.catalog.profiles.single { it.profileId == profileId }
            val start = profile.startUrl
            val host = start.host
            assertEquals(profile, qa.profile(profileId))
            assertNull(release.profile(profileId))
            listOf(
                start to TrustMode.TRUSTED_CLIENT_AUTH,
                URI("https://$host/procedimientos") to TrustMode.TRUSTED_CLIENT_AUTH,
                source to TrustMode.TRUSTED_BROWSE,
                provider to TrustMode.BROWSE_ONLY,
                request to TrustMode.TRUSTED_CLIENT_AUTH,
            ).forEach { (url, trustMode) ->
                assertEquals(
                    "$profileId: $url",
                    ResolvedSiteProfile(profile, ExactOrigin.parse("https://${url.host}"), trustMode),
                    qa.resolveForProfile(profileId, url),
                )
                assertNull("$profileId: $url", release.resolveForProfile(profileId, url))
            }
            assertEquals(TrustMode.TRUSTED_CLIENT_AUTH, qa.resolve(start)?.trustMode)
            assertNull(release.resolve(start))
            listOf(
                "http://$host/",
                "https://$host:8443/",
                "https://user@$host/",
                "https://$host.evil.example/",
                "https://evil.$host/",
                "https://pasarela.clave.gob.es.evil.example/Proxy2/ServiceRedirect",
                "https://pasarela-ident.clave.gob.es.evil.example/IdP2/AuthenticateCitizen",
                "https://unreviewed.example/",
            ).forEach { url ->
                assertNull("$profileId: $url", qa.resolveForProfile(profileId, URI(url)))
            }
            profileIds.filter { it != profileId }.forEach { otherId ->
                val otherStart = checkNotNull(qa.profile(otherId)).startUrl
                assertNull("$profileId: $otherStart", qa.resolveForProfile(profileId, otherStart))
            }
        }
    }

    @Test
    fun `CSN arms client TLS only on the AFIRMA POST that ServiceProvider itself sends`() {
        val profileId = ProfileId("csn-sede-public")
        val start = URI("https://sede.csn.gob.es/")
        val profile = BuiltInSiteProfiles.catalog.profiles.single { it.profileId == profileId }

        assertEquals(2, profile.profileVersion)
        assertEquals(setOf(ExactOrigin.parse("https://sede.csn.gob.es")), profile.initiatorOrigins)
        assertEquals(setOf(ExactOrigin.parse("https://pasarela.clave.gob.es")), profile.redirectOrigins)
        assertEquals(setOf(Capability.CLIENT_TLS_AUTH), profile.capabilities)
        val policy = checkNotNull(profile.clientAuthPolicy)
        assertEquals(ClientAuthTransitionMode.IN_PLACE_FROM_SOURCE, policy.transitionMode)
        // CSN preselects AFIRMA, so there is no ServiceRedirect hop.
        assertEquals(setOf(URI("https://pasarela.clave.gob.es/Proxy2/ServiceProvider")), policy.sourceUrls)
        assertEquals(setOf(ExactOrigin.parse("https://pasarela-ident.clave.gob.es")), policy.requestOrigins)
        assertEquals("/IdP2/AuthenticateCitizen", policy.requestPath)
        assertTrue(profile.certificateRules.requireDigitalSignatureKeyUsage)
        assertEquals(TrustMode.TRUSTED_CLIENT_AUTH, BuiltInSiteProfiles.qaRegistry.resolve(start)?.trustMode)
        assertNull(BuiltInSiteProfiles.releaseRegistry.profile(profileId))
    }

    @Test
    fun `MJusticia fundaciones pins only the reviewed QA Clave login`() {
        val profileId = ProfileId("mjusticia-fundaciones-idp75")
        val start = URI("https://sede2.mjusticia.gob.es/procedimientos/choose-ambit/idp/75")
        val profile = BuiltInSiteProfiles.catalog.profiles.single { it.profileId == profileId }

        assertEquals(2, profile.profileVersion)
        assertEquals(CompatibilityStatus.VERIFIED_CONTRACT, profile.compatibilityStatus)
        assertEquals(ProfileActivation.QA_ONLY, profile.activation)
        assertEquals(start, profile.startUrl)
        assertEquals(setOf(ExactOrigin.parse("https://sede2.mjusticia.gob.es")), profile.initiatorOrigins)
        assertEquals(
            setOf(ExactOrigin.parse("https://pasarela.clave.gob.es")),
            profile.redirectOrigins,
        )
        assertTrue(profile.trustedBrowseOrigins.isEmpty())
        assertTrue(profile.endpoints.isEmpty())
        assertTrue(profile.operationPolicies.isEmpty())
        assertEquals(setOf(Capability.CLIENT_TLS_AUTH), profile.capabilities)
        assertEquals(ClientAuthTransitionMode.IN_PLACE_FROM_SOURCE, profile.clientAuthPolicy?.transitionMode)
        assertEquals(HttpMethod.POST, profile.clientAuthPolicy?.requestMethod)
        assertEquals(setOf("RSA", "EC"), profile.certificateRules.allowedKeyAlgorithms)
        assertTrue(profile.certificateRules.requireDigitalSignatureKeyUsage)
        assertEquals(7, profile.evidence.size)
        assertEquals(
            setOf("2026-08-19", "2026-11-07"),
            profile.evidence.map { it.reviewedOn.toString() }.toSet(),
        )

        assertNull(BuiltInSiteProfiles.releaseRegistry.profile(profileId))
        assertNull(BuiltInSiteProfiles.releaseRegistry.resolve(start))
        assertEquals(profile, BuiltInSiteProfiles.qaRegistry.profile(profileId))
        assertEquals(TrustMode.TRUSTED_CLIENT_AUTH, BuiltInSiteProfiles.qaRegistry.resolve(start)?.trustMode)
        assertNull(BuiltInSiteProfiles.qaRegistry.resolve(URI("https://sede2.mjusticia.gob.es.evil.example/")))
        assertNull(BuiltInSiteProfiles.qaRegistry.resolve(URI("https://sede2.mjusticia.gob.es:444/")))
    }

    @Test
    fun `CTBG Solicitud de Informacion arms client TLS only on the reviewed Clave POST`() {
        val profileId = ProfileId("ctbg-solicitud-informacion")
        val start = URI("https://sede.consejodetransparencia.gob.es/catalog/tw/01b4b72b-7f21-4d7c-9576-e1d7871624a6")
        val profile = BuiltInSiteProfiles.catalog.profiles.single { it.profileId == profileId }

        assertEquals(2, profile.profileVersion)
        assertEquals("Consejo de Transparencia y Buen Gobierno (CTBG) — Solicitud de Información", profile.displayName)
        assertEquals(CompatibilityStatus.VERIFIED_CONTRACT, profile.compatibilityStatus)
        assertEquals(ProfileActivation.QA_ONLY, profile.activation)
        assertEquals(start, profile.startUrl)
        assertEquals(setOf(ExactOrigin.parse("https://sede.consejodetransparencia.gob.es")), profile.initiatorOrigins)
        assertEquals(setOf(ExactOrigin.parse("https://pasarela.clave.gob.es")), profile.redirectOrigins)
        assertTrue(profile.trustedBrowseOrigins.isEmpty())
        assertTrue(profile.endpoints.isEmpty())
        assertTrue(profile.operationPolicies.isEmpty())
        assertEquals(setOf(Capability.CLIENT_TLS_AUTH), profile.capabilities)
        val policy = checkNotNull(profile.clientAuthPolicy)
        assertEquals(ClientAuthTransitionMode.IN_PLACE_FROM_SOURCE, policy.transitionMode)
        assertEquals(setOf(URI("https://pasarela.clave.gob.es/Proxy2/ServiceRedirect")), policy.sourceUrls)
        assertEquals(setOf(ExactOrigin.parse("https://pasarela-ident.clave.gob.es")), policy.requestOrigins)
        assertEquals("/IdP2/AuthenticateCitizen", policy.requestPath)
        assertEquals(setOf("RSA", "EC"), profile.certificateRules.allowedKeyAlgorithms)
        assertTrue(profile.certificateRules.requireDigitalSignatureKeyUsage)
        assertEquals(6, profile.evidence.size)
        assertTrue(profile.evidence.all { it.reviewedOn.toString() in setOf("2026-08-23", "2026-11-07") })
        assertEquals(TrustMode.TRUSTED_CLIENT_AUTH, BuiltInSiteProfiles.qaRegistry.resolve(start)?.trustMode)
        assertEquals(
            TrustMode.BROWSE_ONLY,
            BuiltInSiteProfiles.qaRegistry.resolveForProfile(
                profileId,
                URI("https://pasarela.clave.gob.es/Proxy2/ServiceProvider"),
            )?.trustMode,
        )
        assertNull(BuiltInSiteProfiles.releaseRegistry.profile(profileId))
        assertNull(BuiltInSiteProfiles.releaseRegistry.resolve(start))
        assertNull(BuiltInSiteProfiles.qaRegistry.resolve(URI("https://sede.consejodetransparencia.gob.es.evil.example/")))
    }

    @Test
    fun `Catastro generic submissions pins the reviewed QA Clave login`() {
        val profileId = ProfileId("catastro-solicitudes-genericas")
        val start = URI("https://www.sedecatastro.gob.es/Accesos/SECAccProcedimientos.aspx?Dest=22")
        val profile = BuiltInSiteProfiles.catalog.profiles.single { it.profileId == profileId }

        assertEquals(2, profile.profileVersion)
        assertEquals("Dirección General del Catastro — Otras solicitudes y escritos genéricos", profile.displayName)
        assertEquals(CompatibilityStatus.VERIFIED_CONTRACT, profile.compatibilityStatus)
        assertEquals(ProfileActivation.QA_ONLY, profile.activation)
        assertEquals(start, profile.startUrl)
        assertEquals(setOf(ExactOrigin.parse("https://www.sedecatastro.gob.es")), profile.initiatorOrigins)
        assertEquals(setOf(ExactOrigin.parse("https://pasarela.clave.gob.es")), profile.redirectOrigins)
        assertTrue(profile.trustedBrowseOrigins.isEmpty())
        assertTrue(profile.endpoints.isEmpty())
        assertTrue(profile.operationPolicies.isEmpty())
        assertEquals(setOf(Capability.CLIENT_TLS_AUTH), profile.capabilities)
        assertEquals(ClientAuthTransitionMode.IN_PLACE_FROM_SOURCE, profile.clientAuthPolicy?.transitionMode)
        assertEquals(HttpMethod.POST, profile.clientAuthPolicy?.requestMethod)
        assertEquals(setOf("RSA", "EC"), profile.certificateRules.allowedKeyAlgorithms)
        assertTrue(profile.certificateRules.requireDigitalSignatureKeyUsage)
        assertEquals(10, profile.evidence.size)
        assertEquals(
            setOf("2026-08-24", "2026-11-07"),
            profile.evidence.map { it.reviewedOn.toString() }.toSet(),
        )
        assertEquals(TrustMode.TRUSTED_CLIENT_AUTH, BuiltInSiteProfiles.qaRegistry.resolve(start)?.trustMode)
        assertEquals(
            TrustMode.BROWSE_ONLY,
            BuiltInSiteProfiles.qaRegistry.resolveForProfile(
                profileId,
                URI("https://pasarela.clave.gob.es/Proxy2/ResponseRedirect"),
            )?.trustMode,
        )
        assertNull(BuiltInSiteProfiles.releaseRegistry.profile(profileId))
        assertNull(BuiltInSiteProfiles.releaseRegistry.resolve(start))
        assertNull(BuiltInSiteProfiles.qaRegistry.resolve(URI("https://www.sedecatastro.gob.es.evil.example/")))
    }

    @Test
    fun `Asturias Sede may share only the reviewed miPrincipado redirect with the existing Asturias profile`() {
        val existing = BuiltInSiteProfiles.catalog.profiles.single {
            it.profileId == ProfileId("asturias-miprincipado")
        }
        val navigation = BuiltInSiteProfiles.catalog.profiles.single {
            it.profileId == ProfileId("asturias-sede-tramite-navigation")
        }
        val shared = ExactOrigin.parse("https://miprincipado.asturias.es")

        assertTrue(shared in existing.initiatorOrigins)
        assertTrue(shared in navigation.redirectOrigins)
        assertTrue(shared !in navigation.initiatorOrigins)
        assertTrue(navigation.capabilities.isEmpty())
        assertNull(navigation.clientAuthPolicy)
    }

    @Test
    fun `Asturias Sede shared origin exception fails closed for another origin or owner`() {
        val reviewedOrigins =
            "\"initiatorOrigins\": [\"https://sede.asturias.es\"],\n" +
                "      \"redirectOrigins\": [\"https://miprincipado.asturias.es\"]"
        val unreviewedOrigins =
            "\"initiatorOrigins\": [\"https://sede.asturias.es\"],\n" +
                "      \"redirectOrigins\": [\"https://tramita.asturias.es\"]"
        val reviewedOwner = "\"profileId\": \"asturias-sede-tramite-navigation\""
        val unreviewedOwner = "\"profileId\": \"asturias-sede-unreviewed-navigation\""

        assertTrue(BuiltInSiteProfiles.JSON.contains(reviewedOrigins))
        assertTrue(BuiltInSiteProfiles.JSON.contains(reviewedOwner))
        assertThrows(IllegalArgumentException::class.java) {
            SiteProfileCatalogParser.parse(
                BuiltInSiteProfiles.JSON.replaceFirst(reviewedOrigins, unreviewedOrigins),
            )
        }
        assertThrows(IllegalArgumentException::class.java) {
            SiteProfileCatalogParser.parse(
                BuiltInSiteProfiles.JSON.replaceFirst(reviewedOwner, unreviewedOwner),
            )
        }
    }

    @Test
    fun `Asturias Sede profile exposes only current redirect navigation in QA`() {
        val profileId = ProfileId("asturias-sede-tramite-navigation")
        val start = URI("https://sede.asturias.es/ast/-/dboid-6269000011903512107573")
        val profile = BuiltInSiteProfiles.catalog.profiles.single { it.profileId == profileId }

        assertEquals(CompatibilityStatus.VERIFIED_CONTRACT, profile.compatibilityStatus)
        assertEquals(ProfileActivation.QA_ONLY, profile.activation)
        assertEquals(start, profile.startUrl)
        assertEquals(setOf(ExactOrigin.parse("https://sede.asturias.es")), profile.initiatorOrigins)
        assertEquals(setOf(ExactOrigin.parse("https://miprincipado.asturias.es")), profile.redirectOrigins)
        assertTrue(profile.trustedBrowseOrigins.isEmpty())
        assertTrue(profile.endpoints.isEmpty())
        assertTrue(profile.operationPolicies.isEmpty())
        assertTrue(profile.capabilities.isEmpty())
        assertNull(profile.clientAuthPolicy)
        assertEquals(setOf("RSA", "EC"), profile.certificateRules.allowedKeyAlgorithms)
        assertFalse(profile.certificateRules.requireDigitalSignatureKeyUsage)
        assertNull(BuiltInSiteProfiles.releaseRegistry.profile(profileId))
    }

    @Test
    fun `FEGA Solicitud al FEGA is QA navigation only to observed Clave boundary`() {
        val profileId = ProfileId("fega-solicitud-general-ofvsg02")
        val start = URI("https://www3.sede.fega.gob.es/ConRegExt/regmantenimientos/inicioAsientos.action?tramite=OFVSG02")
        val profile = BuiltInSiteProfiles.catalog.profiles.single { it.profileId == profileId }

        assertEquals(1, profile.profileVersion)
        assertEquals("FEGA — Solicitud al FEGA", profile.displayName)
        assertEquals(CompatibilityStatus.VERIFIED_CONTRACT, profile.compatibilityStatus)
        assertEquals(ProfileActivation.QA_ONLY, profile.activation)
        assertEquals(start, profile.startUrl)
        assertEquals(setOf(ExactOrigin.parse("https://www3.sede.fega.gob.es")), profile.initiatorOrigins)
        assertEquals(setOf(ExactOrigin.parse("https://pasarela.clave.gob.es")), profile.redirectOrigins)
        assertTrue(profile.trustedBrowseOrigins.isEmpty())
        assertTrue(profile.endpoints.isEmpty())
        assertTrue(profile.operationPolicies.isEmpty())
        assertTrue(profile.capabilities.isEmpty())
        assertNull(profile.clientAuthPolicy)
        assertEquals(setOf("RSA", "EC"), profile.certificateRules.allowedKeyAlgorithms)
        assertFalse(profile.certificateRules.requireDigitalSignatureKeyUsage)
        assertEquals(6, profile.evidence.size)
        assertTrue(profile.evidence.all { it.reviewedOn.toString() == "2026-08-24" })
        assertEquals(TrustMode.TRUSTED_BROWSE, BuiltInSiteProfiles.qaRegistry.resolve(start)?.trustMode)
        assertEquals(
            TrustMode.BROWSE_ONLY,
            BuiltInSiteProfiles.qaRegistry.resolveForProfile(profileId, URI("https://pasarela.clave.gob.es/Proxy2/ServiceProvider"))?.trustMode,
        )
        assertNull(BuiltInSiteProfiles.releaseRegistry.profile(profileId))
        assertNull(BuiltInSiteProfiles.releaseRegistry.resolve(start))
        assertNull(BuiltInSiteProfiles.qaRegistry.resolve(URI("https://www3.sede.fega.gob.es.evil.example/")))
    }


    @Test
    fun `Diputacion Cordoba Solicitud Generica pins the reviewed QA Clave login`() {
        val profileId = ProfileId("diputacion-cordoba-solicitud-generica")
        val start = URI("https://sede.dipucordoba.es/diputacion/tramites/procedimiento/8876/solicitud-generica")
        val profile = BuiltInSiteProfiles.catalog.profiles.single { it.profileId == profileId }

        assertEquals(2, profile.profileVersion)
        assertEquals("Diputación Provincial de Córdoba — Solicitud Genérica", profile.displayName)
        assertEquals(CompatibilityStatus.VERIFIED_CONTRACT, profile.compatibilityStatus)
        assertEquals(ProfileActivation.QA_ONLY, profile.activation)
        assertEquals(start, profile.startUrl)
        assertEquals(setOf(ExactOrigin.parse("https://sede.dipucordoba.es")), profile.initiatorOrigins)
        assertEquals(
            setOf(ExactOrigin.parse("https://apis.dipucordoba.es"), ExactOrigin.parse("https://pasarela.clave.gob.es")),
            profile.redirectOrigins,
        )
        assertTrue(profile.trustedBrowseOrigins.isEmpty())
        assertTrue(profile.endpoints.isEmpty())
        assertTrue(profile.operationPolicies.isEmpty())
        assertEquals(setOf(Capability.CLIENT_TLS_AUTH), profile.capabilities)
        assertEquals(ClientAuthTransitionMode.IN_PLACE_FROM_SOURCE, profile.clientAuthPolicy?.transitionMode)
        assertEquals(HttpMethod.POST, profile.clientAuthPolicy?.requestMethod)
        assertEquals(setOf("RSA", "EC"), profile.certificateRules.allowedKeyAlgorithms)
        assertTrue(profile.certificateRules.requireDigitalSignatureKeyUsage)
        assertEquals(8, profile.evidence.size)
        assertEquals(
            setOf("2026-08-21", "2026-11-07"),
            profile.evidence.map { it.reviewedOn.toString() }.toSet(),
        )
        assertEquals(TrustMode.TRUSTED_CLIENT_AUTH, BuiltInSiteProfiles.qaRegistry.resolve(start)?.trustMode)
        assertNull(BuiltInSiteProfiles.releaseRegistry.profile(profileId))
        assertNull(BuiltInSiteProfiles.releaseRegistry.resolve(start))
        assertNull(BuiltInSiteProfiles.qaRegistry.resolve(URI("https://sede.dipucordoba.es.evil.example/")))
    }

    @Test
    fun `Gipuzkoa Registro profile is QA public navigation and keeps Izenpe auth fail closed`() {
        val profileId = ProfileId("diputacion-gipuzkoa-registro-public")
        val start = URI(
            "https://egoitza.gipuzkoa.eus/WAS/CORP/WATTramiteakWEB/inicio.do?idioma=C&app=00001",
        )
        val profile = BuiltInSiteProfiles.catalog.profiles.single { it.profileId == profileId }

        assertEquals(1, profile.profileVersion)
        assertEquals(CompatibilityStatus.VERIFIED_CONTRACT, profile.compatibilityStatus)
        assertEquals(ProfileActivation.QA_ONLY, profile.activation)
        assertEquals(start, profile.startUrl)
        assertEquals(setOf(ExactOrigin.parse("https://egoitza.gipuzkoa.eus")), profile.initiatorOrigins)
        assertTrue(profile.redirectOrigins.isEmpty())
        assertTrue(profile.trustedBrowseOrigins.isEmpty())
        assertTrue(profile.endpoints.isEmpty())
        assertTrue(profile.operationPolicies.isEmpty())
        assertTrue(profile.capabilities.isEmpty())
        assertNull(profile.clientAuthPolicy)
        assertEquals(setOf("RSA", "EC"), profile.certificateRules.allowedKeyAlgorithms)
        assertFalse(profile.certificateRules.requireDigitalSignatureKeyUsage)
        assertEquals(3, profile.evidence.size)
        assertTrue(profile.evidence.all { it.reviewedOn.toString() == "2026-08-19" })

        assertNull(BuiltInSiteProfiles.releaseRegistry.profile(profileId))
        assertNull(BuiltInSiteProfiles.releaseRegistry.resolve(start))
        assertEquals(profile, BuiltInSiteProfiles.qaRegistry.profile(profileId))
        assertEquals(TrustMode.TRUSTED_BROWSE, BuiltInSiteProfiles.qaRegistry.resolve(start)?.trustMode)
        assertNull(BuiltInSiteProfiles.qaRegistry.resolveForProfile(profileId, URI("https://eidas.izenpe.com/")))
        assertNull(BuiltInSiteProfiles.qaRegistry.resolveForProfile(profileId, URI("https://eidas2.izenpe.com/")))
        assertNull(BuiltInSiteProfiles.qaRegistry.resolve(URI("https://egoitza.gipuzkoa.eus.evil.example/")))
        assertNull(BuiltInSiteProfiles.qaRegistry.resolve(URI("https://egoitza.gipuzkoa.eus:444/")))
    }

    @Test
    fun `Segovia Registro profile stays bounded to the reviewed QA Clave certificate login`() {
        val profileId = ProfileId("diputacion-segovia-registro")
        val start = URI("https://sede.dipsegovia.es/registro")
        val profile = BuiltInSiteProfiles.catalog.profiles.single { it.profileId == profileId }

        assertEquals(2, profile.profileVersion)
        assertEquals("Diputación de Segovia — Registro electrónico", profile.displayName)
        assertEquals(CompatibilityStatus.VERIFIED_CONTRACT, profile.compatibilityStatus)
        assertEquals(ProfileActivation.QA_ONLY, profile.activation)
        assertEquals(start, profile.startUrl)
        assertEquals(setOf(ExactOrigin.parse("https://sede.dipsegovia.es")), profile.initiatorOrigins)
        assertEquals(setOf(ExactOrigin.parse("https://pasarela.clave.gob.es")), profile.redirectOrigins)
        assertTrue(profile.trustedBrowseOrigins.isEmpty())
        assertTrue(profile.endpoints.isEmpty())
        assertTrue(profile.operationPolicies.isEmpty())
        assertEquals(setOf(Capability.CLIENT_TLS_AUTH), profile.capabilities)
        assertEquals(HttpMethod.POST, profile.clientAuthPolicy?.requestMethod)
        assertEquals(setOf("RSA", "EC"), profile.certificateRules.allowedKeyAlgorithms)
        assertTrue(profile.certificateRules.requireDigitalSignatureKeyUsage)
        assertEquals(7, profile.evidence.size)
        assertTrue(profile.evidence.take(5).all { it.reviewedOn.toString() == "2026-08-21" })

        assertEquals(profile, BuiltInSiteProfiles.qaRegistry.profile(profileId))
        assertEquals(TrustMode.TRUSTED_CLIENT_AUTH, BuiltInSiteProfiles.qaRegistry.resolve(start)?.trustMode)
        assertNull(BuiltInSiteProfiles.releaseRegistry.profile(profileId))
        assertNull(BuiltInSiteProfiles.releaseRegistry.resolve(start))
        assertNull(BuiltInSiteProfiles.qaRegistry.resolve(URI("https://sede.dipsegovia.es.evil.example/registro")))
    }

    @Test
    fun `Madrid Cuenta Digital 53F1 profile pins only exact QA certificate login`() {
        val profileId = ProfileId("comunidad-madrid-cuenta-digital-53f1")
        val start = URI("https://digital.comunidad.madrid/ext/53F1")
        val profile = BuiltInSiteProfiles.catalog.profiles.single { it.profileId == profileId }

        assertEquals(1, profile.profileVersion)
        assertEquals(CompatibilityStatus.VERIFIED_CONTRACT, profile.compatibilityStatus)
        assertEquals(ProfileActivation.QA_ONLY, profile.activation)
        assertEquals(start, profile.startUrl)
        assertEquals(setOf(ExactOrigin.parse("https://digital.comunidad.madrid")), profile.initiatorOrigins)
        assertEquals(setOf(ExactOrigin.parse("https://gestiona.comunidad.madrid")), profile.redirectOrigins)
        assertTrue(profile.trustedBrowseOrigins.isEmpty())
        assertTrue(profile.endpoints.isEmpty())
        assertTrue(profile.operationPolicies.isEmpty())
        assertEquals(setOf(Capability.CLIENT_TLS_AUTH), profile.capabilities)
        val policy = checkNotNull(profile.clientAuthPolicy)
        assertEquals(HttpMethod.POST, policy.requestMethod)
        assertEquals(ClientAuthTransitionMode.IN_PLACE_FROM_SOURCE, policy.transitionMode)
        assertEquals(setOf("id"), policy.sourceRequiredEphemeralQueryParameters)
        assertEquals(setOf("RSA", "EC"), profile.certificateRules.allowedKeyAlgorithms)
        assertTrue(profile.certificateRules.requireDigitalSignatureKeyUsage)
        assertEquals(4, profile.evidence.size)
        assertEquals(1, profile.evidence.count { it.reviewedOn.toString() == "2026-11-07" })
    }
}

private fun URI.originForTest() = ExactOrigin.parse("https://$host")

private fun SiteProfile.navigationOriginsForTest() =
    initiatorOrigins + redirectOrigins + trustedBrowseOrigins +
        (clientAuthPolicy?.requestOrigins ?: emptySet())

package dev.junta.firmamobile.profile

import java.net.URI
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

class SharedOriginCharacterizationTest {
    private data class Family(
        val origin: String,
        val initiators: List<String> = emptyList(),
        val redirects: List<String> = emptyList(),
        val browse: List<String> = emptyList(),
        val requests: List<String> = emptyList(),
    ) {
        val owners get() = initiators + redirects + browse + requests
    }

    // Independent expectations, including owners admitted only by comparison with the first owner.
    private val families = listOf(
        Family(
            "https://pasarela.clave.gob.es",
            redirects = listOf(
                "educacion-convocatoria", "jccm-registro-generico", "airef-instancia-general",
                "mugeju-remision-documentacion-client-auth", "asturias-miprincipado",
                "diputacion-cadiz-solicitud-generica", "diputacion-avila-instancia-general",
                "ctbg-solicitud-informacion", "catastro-solicitudes-genericas",
                "diputacion-malaga-instancia-general", "fega-solicitud-general-ofvsg02",
                "el-hierro-solicitud-general", "catalunya-peticio-generica-client-auth",
                "diputacion-caceres-instancia-general", "diputacion-guadalajara-instancia-general",
                "diputacion-segovia-registro", "diputacion-palencia-solicitud-general",
                "diputacion-ourense-sede", "diputacion-sevilla-sede",
                "diputacion-a-coruna-solicitud-general",
            ),
            browse = listOf("ministerio-economia-instancia-generica"),
        ),
        Family(
            "https://pasarela-ident.clave.gob.es",
            redirects = listOf("diputacion-avila-instancia-general"),
            browse = listOf("ministerio-economia-instancia-generica"),
            requests = listOf(
                "educacion-convocatoria", "jccm-registro-generico", "airef-instancia-general",
                "mugeju-remision-documentacion-client-auth", "asturias-miprincipado",
                "diputacion-malaga-instancia-general", "catalunya-peticio-generica-client-auth",
                "diputacion-ourense-sede", "diputacion-sevilla-sede",
                "diputacion-a-coruna-solicitud-general",
            ),
        ),
        Family(
            "https://valid.aoc.cat",
            redirects = listOf(
                "diputacion-barcelona-solicitud-generica-2057", "catalunya-peticio-generica-client-auth",
                "catalunya-seu-registre-client-auth",
            ),
            browse = listOf("diputacion-tarragona-sede"),
        ),
        Family(
            "https://cert.valid.aoc.cat",
            redirects = listOf("diputacion-barcelona-solicitud-generica-2057"),
            requests = listOf("diputacion-tarragona-sede", "catalunya-seu-registre-client-auth"),
        ),
        Family(
            "https://tramits.gencat.cat",
            initiators = listOf("catalunya-peticio-generica-client-auth"),
            redirects = listOf("catalunya-seu-registre-client-auth"),
        ),
        Family(
            "https://ovt.gencat.cat",
            redirects = listOf("catalunya-peticio-generica-client-auth", "catalunya-seu-registre-client-auth"),
        ),
        Family(
            "https://identificacionssl.sedipualba.es",
            requests = listOf(
                "diputacion-leon-sede", "diputacion-albacete-portal",
                "consell-mallorca-sede", "diputacion-cuenca-portal",
            ),
        ),
        Family(
            "https://miprincipado.asturias.es",
            initiators = listOf("asturias-miprincipado"),
            redirects = listOf("asturias-sede-tramite-navigation"),
        ),
        Family(
            "https://gestiona.comunidad.madrid",
            initiators = listOf("comunidad-madrid-registro-general"),
            redirects = listOf("comunidad-madrid-cuenta-digital-53f1"),
        ),
        Family(
            "https://sede.transportes.gob.es",
            initiators = listOf("transportes-qys-cert-login", "sepes-transportes-public-complaints"),
        ),
        Family(
            "https://ws235.juntadeandalucia.es",
            requests = listOf("carne-joven-andalucia", "junta-andalucia-vea-peg"),
        ),
        Family(
            "https://aplicaciones.aragon.es",
            initiators = listOf("aragon-siraw", "aragon-solicitud-general-client-auth"),
        ),
        Family(
            "https://elhierro.sedelectronica.es",
            initiators = listOf("el-hierro-solicitud-general", "el-hierro-sede-public-navigation"),
        ),
        Family(
            "https://lagomera.sedelectronica.es",
            initiators = listOf("la-gomera-instancia-general", "la-gomera-sede-public-navigation"),
        ),
        Family(
            "https://lanzaroteylagraciosa.sedelectronica.es",
            initiators = listOf("lanzarote-instancia-general"),
            redirects = listOf("lanzarote-sede-public-navigation"),
        ),
    )

    @Test
    fun canonicalCatalogPinsEverySharedOriginOwnerAndRole() {
        val catalog = SiteProfileCatalogParser.parse(BuiltInSiteProfiles.JSON)
        val claims = catalog.profiles.flatMap { profile ->
            listOf(
                "initiator" to profile.initiatorOrigins,
                "redirect" to profile.redirectOrigins,
                "browse" to profile.trustedBrowseOrigins,
                "request" to (profile.clientAuthPolicy?.requestOrigins ?: emptySet()),
            ).flatMap { (role, origins) ->
                origins.map { it.serialized to (profile.profileId.value to role) }
            }
        }.groupBy({ it.first }, { it.second })
            .filterValues { owners -> owners.map { it.first }.distinct().size > 1 }
            .mapValues { it.value.toSet() }
        val expected = families.associate { family ->
            family.origin to (
                family.initiators.map { it to "initiator" } + family.redirects.map { it to "redirect" } +
                    family.browse.map { it to "browse" } + family.requests.map { it to "request" }
                ).toSet()
        }
        assertEquals(expected, claims)
    }

    @Test
    fun everyFamilyRemainsGloballyAmbiguousButSelectedProfilesStillResolve() {
        families.forEach { family ->
            val origin = ExactOrigin.parse(family.origin)
            assertNull(family.origin, BuiltInSiteProfiles.qaRegistry.resolve(origin.toTrustedOrigin()))
            family.owners.forEach { owner ->
                val id = ProfileId(owner)
                val profile = BuiltInSiteProfiles.qaRegistry.profile(id)!!
                val url = if (owner in family.initiators) profile.startUrl else URI("${family.origin}/")
                val resolved = BuiltInSiteProfiles.qaRegistry.resolveForProfile(id, url)
                assertNotNull("${family.origin}: $owner", resolved)
                assertEquals(id, resolved?.profile?.profileId)
                assertEquals(origin, resolved?.origin)
            }
            assertNull(
                BuiltInSiteProfiles.qaRegistry.resolveForProfile(ProfileId("reg-age-redsara"), URI(family.origin)),
            )
        }
    }

    @Test
    fun reviewedPairsParseInBothOrdersExceptTheDirectionalAocCertificatePair() {
        families.forEach { family ->
            family.owners.forEachIndexed { index, first ->
                family.owners.drop(index + 1).forEach { second ->
                    listOf(listOf(first, second), listOf(second, first)).forEach { order ->
                        val sedipualbaSiblings = family.origin == "https://identificacionssl.sedipualba.es" &&
                            "diputacion-leon-sede" !in order
                        // These profiles also collide at cert.valid.aoc.cat when testing valid.aoc.cat.
                        val reversedAoc = order == listOf(
                            "catalunya-seu-registre-client-auth", "diputacion-barcelona-solicitud-generica-2057",
                        )
                        val blocks = order.map(::profileJson)
                        blocks.forEach { parse(listOf(it)) }
                        if (sedipualbaSiblings || reversedAoc) {
                            rejects("${family.origin}: $order", blocks)
                        } else {
                            assertEquals(order, parse(blocks).profiles.map { it.profileId.value })
                        }
                    }
                }
            }
        }
    }

    @Test
    fun firstOwnerAllowsSedipualbaSiblingsWithoutMakingThemAnAllPairsClique() {
        val leon = profileJson("diputacion-leon-sede")
        val albacete = profileJson("diputacion-albacete-portal")
        val mallorca = profileJson("consell-mallorca-sede")
        val cuenca = profileJson("diputacion-cuenca-portal")
        assertEquals(4, parse(listOf(leon, albacete, mallorca, cuenca)).profiles.size)
        assertEquals(4, parse(listOf(leon, cuenca, mallorca, albacete)).profiles.size)
        rejects("Albacete owns first", listOf(albacete, leon, mallorca, cuenca))
        rejects("Mallorca owns first", listOf(mallorca, leon, albacete, cuenca))
        rejects("Cuenca owns first", listOf(cuenca, leon, albacete, mallorca))
    }

    @Test
    fun tarragonaFirstMasksTheDirectionalBarcelonaCatalunyaSeuCertificateConflict() {
        val tarragona = profileJson("diputacion-tarragona-sede")
        val barcelona = profileJson("diputacion-barcelona-solicitud-generica-2057")
        val seu = profileJson("catalunya-seu-registre-client-auth")
        assertEquals(3, parse(listOf(tarragona, seu, barcelona)).profiles.size)
        assertEquals(3, parse(listOf(barcelona, seu, tarragona)).profiles.size)
        rejects("Catalunya Seu owns first", listOf(seu, tarragona, barcelona))
    }

    @Test
    fun unrelatedThirdOwnersRejectEveryFamilyInEitherPosition() {
        families.forEach { family ->
            val stranger = navigationProfile("unreviewed-third-owner", family.origin, false)
            parse(listOf(stranger))
            val reviewed = family.owners.map(::profileJson)
            // Use the canonical order so a pre-existing directional conflict cannot explain rejection.
            val canonical = profileBlocks.filter { block -> reviewed.contains(block) }
            parse(canonical)
            rejects("${family.origin}: stranger last", canonical + stranger)
            rejects("${family.origin}: stranger first", listOf(stranger) + canonical)
        }
    }

    @Test
    fun navigationRoleMatrixKeepsMixedAndNonRedirectFamiliesDistinct() {
        val mixed = setOf(false to true, true to false)
        val nonRedirect = setOf(false to false)
        val pairs = listOf(
            Triple(
                "https://miprincipado.asturias.es",
                "asturias-miprincipado",
                "asturias-sede-tramite-navigation",
            ) to mixed,
            Triple(
                "https://gestiona.comunidad.madrid",
                "comunidad-madrid-registro-general",
                "comunidad-madrid-cuenta-digital-53f1",
            ) to mixed,
            Triple(
                "https://lanzaroteylagraciosa.sedelectronica.es",
                "lanzarote-instancia-general",
                "lanzarote-sede-public-navigation",
            ) to mixed,
            Triple(
                "https://elhierro.sedelectronica.es",
                "el-hierro-solicitud-general",
                "el-hierro-sede-public-navigation",
            ) to nonRedirect,
            Triple(
                "https://lagomera.sedelectronica.es",
                "la-gomera-instancia-general",
                "la-gomera-sede-public-navigation",
            ) to nonRedirect,
            Triple(
                "https://ws235.juntadeandalucia.es",
                "carne-joven-andalucia",
                "junta-andalucia-vea-peg",
            ) to nonRedirect,
        )
        val roles = listOf(false, true).flatMap { first -> listOf(false, true).map { first to it } }
        for ((pair, acceptedRoles) in pairs) for ((firstRedirect, secondRedirect) in roles) {
            val (origin, first, second) = pair
            val blocks = listOf(
                navigationProfile(first, origin, firstRedirect),
                navigationProfile(second, origin, secondRedirect),
            )
            blocks.forEach { parse(listOf(it)) }
            assertBothOrders(
                "$origin: $firstRedirect/$secondRedirect",
                blocks,
                accepted = (firstRedirect to secondRedirect) in acceptedRoles,
            )
        }
    }

    private fun assertBothOrders(message: String, blocks: List<String>, accepted: Boolean) {
        listOf(blocks, blocks.reversed()).forEach { order ->
            if (accepted) assertEquals(2, parse(order).profiles.size) else rejects(message, order)
        }
    }

    @Test
    fun claveOwnerSetsDifferAndGuadalajaraDoesNotAuthorizeAnIdentSibling() {
        val mineco = profileJson("ministerio-economia-instancia-generica")
        val onlyPublicGateway = listOf(
            "diputacion-guadalajara-instancia-general", "diputacion-cadiz-solicitud-generica",
            "diputacion-segovia-registro", "diputacion-palencia-solicitud-general", "el-hierro-solicitud-general",
        )
        val bothGateways = listOf(
            "ctbg-solicitud-informacion", "catastro-solicitudes-genericas",
            "fega-solicitud-general-ofvsg02", "diputacion-caceres-instancia-general",
        )
        val origins = listOf("https://pasarela.clave.gob.es", "https://pasarela-ident.clave.gob.es")
        for (owner in onlyPublicGateway + bothGateways) for (origin in origins) for (redirect in listOf(false, true)) {
            val peer = navigationProfile(owner, origin, redirect)
            parse(listOf(peer))
            assertBothOrders(
                "$origin: $owner/$redirect",
                listOf(mineco, peer),
                accepted = owner !in onlyPublicGateway || origin != "https://pasarela-ident.clave.gob.es",
            )
        }
    }

    @Test
    fun aocRedirectRolesAndTarragonaSharingHaveDifferentConstraints() {
        val barcelona = "diputacion-barcelona-solicitud-generica-2057"
        listOf("https://valid.aoc.cat", "https://cert.valid.aoc.cat").forEach { origin ->
            listOf(false, true).forEach { redirect ->
                val peer = navigationProfile(barcelona, origin, redirect)
                parse(listOf(peer))
                val seu = profileJson("catalunya-seu-registre-client-auth")
                if (redirect) assertEquals(2, parse(listOf(peer, seu)).profiles.size)
                else rejects("$origin: Barcelona must redirect", listOf(peer, seu))
                val tarragona = profileJson("diputacion-tarragona-sede")
                assertEquals(2, parse(listOf(peer, tarragona)).profiles.size)
                assertEquals(2, parse(listOf(tarragona, peer)).profiles.size)
            }
        }
    }

    @Test
    fun sameOriginTlsExceptionsRejectUnreviewedOwnersTransitionsAndSources() {
        val ids = listOf(
            "ministerio-sanidad-certificado", "navarra-sede-registro-general",
            "menorca-carpeta-ciutadana", "extremadura-pattex-client-auth",
            "diputacion-soria-sede-client-auth", "la-rioja-oficina-electronica",
        )
        ids.forEach { id ->
            val block = profileJson(id)
            val profile = parse(listOf(block)).profiles.single()
            val policy = profile.clientAuthPolicy!!
            assertEquals(443, policy.requestPort)
            assertTrue(policy.requestOrigins.any { it in profile.initiatorOrigins || it in profile.redirectOrigins })
            assertEquals(
                if (id == "la-rioja-oficina-electronica") ClientAuthTransitionMode.REDIRECT_AFTER_SOURCE
                else ClientAuthTransitionMode.DIRECT_FROM_SOURCE,
                policy.transitionMode,
            )
            rejects("$id: unreviewed owner", listOf(replace(block, id, "unreviewed-same-origin")))
            val wrongMode = if (id == "la-rioja-oficina-electronica") "DIRECT_FROM_SOURCE" else "REDIRECT_AFTER_SOURCE"
            rejects("$id: transition", listOf(replace(block, policy.transitionMode.name, wrongMode)))
            rejects("$id: request path", listOf(replace(block, policy.requestPath, "/unreviewed-client-auth")))
            val source = policy.sourceUrls.single().toASCIIString()
            rejects("$id: source", listOf(replace(block, source, "https://unreviewed.example/source")))
        }
    }

    @Test
    fun sameOriginTlsRequestOriginsAndPathsAreIndependentGoldenValues() {
        val expected = listOf(
            Triple(
                "ministerio-sanidad-certificado",
                "https://sede.mscbs.gob.es",
                "/SIGEM_AutenticacionWeb/validacionCertificado.do",
            ),
            Triple("navarra-sede-registro-general", "https://ateka.navarra.es", "/ateka/Certificate/login"),
            Triple("menorca-carpeta-ciutadana", "https://www.carpetaciutadana.org", "/cime/Login/LoginCert.aspx"),
            Triple("extremadura-pattex-client-auth", "https://pattex.juntaex.es", "/PATTEX/accesoCertificadoSEDE.jsf"),
            Triple(
                "diputacion-soria-sede-client-auth",
                "https://portaltramitador.dipsoria.es",
                "/web/inicioWebcCert.do",
            ),
            Triple("la-rioja-oficina-electronica", "https://ias1.larioja.org", "/clientcertSSL/login"),
        )
        expected.forEach { (id, origin, path) ->
            val policy = parse(listOf(profileJson(id))).profiles.single().clientAuthPolicy!!
            assertEquals(setOf(ExactOrigin.parse(origin)), policy.requestOrigins)
            assertEquals(path, policy.requestPath)
            assertEquals(HttpMethod.GET, policy.requestMethod)
            assertEquals(443, policy.requestPort)
        }
        val rioja = parse(listOf(profileJson("la-rioja-oficina-electronica"))).profiles.single().clientAuthPolicy!!
        assertEquals(setOf(URI("https://ias1.larioja.org/casLR/login")), rioja.sourceUrls)
        assertEquals(mapOf("inst" to "G", "apli" to "OFIVIR", "nodo" to "CIUDANO"), rioja.sourceFixedQueryParameters)
        assertEquals(setOf("param", "TARGET"), rioja.sourceRequiredEphemeralQueryParameters)
        assertTrue(rioja.fixedQueryParameters.isEmpty())
        assertTrue(rioja.requiredEphemeralQueryParameters.isEmpty())
        val pattex = parse(listOf(profileJson("extremadura-pattex-client-auth"))).profiles.single().clientAuthPolicy!!
        assertTrue(pattex.fixedQueryParameters.isEmpty())
        assertTrue(pattex.requiredEphemeralQueryParameters.isEmpty())
    }

    @Test
    fun tlsQueryAndPortMutationsAreRejectedForEverySameOriginProfile() {
        val cases = listOf(
            Triple("ministerio-sanidad-certificado", "TRAM_TARDESCONPLAN", "TRAM_OTHER"),
            Triple("navarra-sede-registro-general", "\"returnUrl\"", "\"otherUrl\""),
            Triple("menorca-carpeta-ciutadana", "\"URL\"", "\"OTHER\""),
            Triple("la-rioja-oficina-electronica", "\"OFIVIR\"", "\"OTHER\""),
            Triple(
                "extremadura-pattex-client-auth",
                "\"fixedQueryParameters\":{}",
                "\"fixedQueryParameters\":{\"extra\":\"value\"}",
            ),
            Triple("diputacion-soria-sede-client-auth", "\"ssl\"", "\"other\""),
        )
        cases.forEach { (id, old, new) ->
            val block = profileJson(id)
            parse(listOf(block))
            rejects("$id: query or port", listOf(replace(block, old, new)))
            rejects("$id: non-default port", listOf(withRequestPort(block, 444)))
        }
    }

    @Test
    fun malagaUnparameterizedPostExceptionRejectsAnyChangedEnvelope() {
        val block = profileJson("diputacion-malaga-instancia-general")
        val policy = parse(listOf(block)).profiles.single().clientAuthPolicy!!
        assertEquals(ClientAuthTransitionMode.IN_PLACE_FROM_SOURCE, policy.transitionMode)
        assertEquals(HttpMethod.POST, policy.requestMethod)
        assertEquals(443, policy.requestPort)
        assertEquals(setOf(ExactOrigin.parse("https://pasarela-ident.clave.gob.es")), policy.requestOrigins)
        assertEquals(setOf(URI("https://pasarela.clave.gob.es/Proxy2/ServiceRedirect")), policy.sourceUrls)
        assertEquals("/IdP2/AuthenticateCitizen", policy.requestPath)
        assertTrue(policy.fixedQueryParameters.isEmpty())
        assertTrue(policy.requiredEphemeralQueryParameters.isEmpty())
        listOf(
            "diputacion-malaga-instancia-general" to "unreviewed-post-owner",
            "IN_PLACE_FROM_SOURCE" to "REDIRECT_AFTER_SOURCE",
            "\"requestMethod\": \"POST\"" to "\"requestMethod\": \"GET\"",
            "\"requestPort\": 443" to "\"requestPort\": 444",
            "https://pasarela-ident.clave.gob.es" to "https://unreviewed.example",
            "https://pasarela.clave.gob.es/Proxy2/ServiceRedirect" to "https://pasarela.clave.gob.es/Proxy2/Other",
            "/IdP2/AuthenticateCitizen" to "/IdP2/Other",
            "\"fixedQueryParameters\": {}" to "\"fixedQueryParameters\": {\"extra\":\"value\"}",
        ).forEach { (old, new) -> rejects("Malaga: $old", listOf(replace(block, old, new))) }
    }

    private val profileBlocks by lazy {
        // Slice complete objects without Android's JSONObject or assumptions about JSON formatting.
        val blocks = mutableListOf<String>()
        var depth = 0
        var quoted = false
        var escaped = false
        var start = -1
        BuiltInSiteProfiles.JSON.substringBefore(",\n  \"reviewedOriginShares\"").forEachIndexed { index, char ->
            if (quoted) {
                when {
                    escaped -> escaped = false
                    char == '\\' -> escaped = true
                    char == '"' -> quoted = false
                }
            } else {
                when (char) {
                    '"' -> quoted = true
                    '{' -> { if (++depth == 2) start = index }
                    '}' -> { if (depth-- == 2) blocks += BuiltInSiteProfiles.JSON.substring(start, index + 1) }
                }
            }
        }
        assertEquals(BuiltInSiteProfiles.catalog.profiles.size, blocks.size)
        blocks
    }

    private fun profileJson(id: String): String =
        profileBlocks.single { it.contains("\"profileId\": \"$id\"") }

    private fun parse(blocks: List<String>): SiteProfileCatalog = SiteProfileCatalogParser.parse(
        """{"schemaVersion":1,"catalogVersion":1,"profiles":[${blocks.joinToString(",")}],
            "reviewedOriginShares":[${declarations(blocks)}]}""",
    )

    // Independent gate-0 fixtures supply the new schema; assertions above retain the legacy decisions.
    private fun declarations(blocks: List<String>): String {
        val profiles = blocks.map { block ->
            SiteProfileCatalogParser.parse(
                """{"schemaVersion":1,"catalogVersion":1,"profiles":[$block]}""",
            ).profiles.single()
        }
        return profiles.flatMap { first ->
            allOrigins(first).flatMap { origin -> fixtureDeclarations(first, origin, profiles) }
        }.joinToString(",")
    }

    private fun fixtureDeclarations(
        first: SiteProfile,
        origin: ExactOrigin,
        profiles: List<SiteProfile>,
    ): List<String> =
        profiles.filter { it.profileId != first.profileId && origin in allOrigins(it) }
            .groupBy { fixtureRoles(origin.serialized, first.profileId.value, it.profileId.value) }
            .filterKeys { it.isNotEmpty() }
            .map { (roles, later) ->
                val evidence = first.evidence.first()
                val peers = later.joinToString(",") { "\"${it.profileId.value}\"" }
                """{"origin":"${origin.serialized}","firstOwner":"${first.profileId.value}",
                    "laterOwners":[$peers],"redirectRoles":[$roles],
                    "evidence":{"url":"${evidence.url}","reviewedOn":"${evidence.reviewedOn}"}}"""
            }

    private fun allOrigins(profile: SiteProfile) = profile.initiatorOrigins + profile.redirectOrigins +
        profile.trustedBrowseOrigins + (profile.clientAuthPolicy?.requestOrigins ?: emptySet())

    private fun fixtureRoles(origin: String, first: String, later: String): String {
        val family = families.singleOrNull { it.origin == origin } ?: return ""
        val additionalIdentOwners = if (origin == "https://pasarela-ident.clave.gob.es") {
            listOf("ctbg-solicitud-informacion", "catastro-solicitudes-genericas",
                "fega-solicitud-general-ofvsg02", "diputacion-caceres-instancia-general")
        } else emptyList()
        val owners = family.owners + additionalIdentOwners
        return if (first !in owners || later !in owners) "" else roleCombinations(origin, first, later)
    }

    private fun roleCombinations(origin: String, first: String, later: String): String = when (origin) {
            "https://valid.aoc.cat" ->
                if ("diputacion-tarragona-sede" in listOf(first, later)) ALL_ROLES else "\"BOTH\""
            "https://cert.valid.aoc.cat" ->
                if ("diputacion-tarragona-sede" in listOf(first, later)) ALL_ROLES else "\"FIRST\""
            "https://tramits.gencat.cat", "https://miprincipado.asturias.es", "https://gestiona.comunidad.madrid",
            "https://lanzaroteylagraciosa.sedelectronica.es" -> "\"FIRST\",\"LATER\""
            "https://ovt.gencat.cat" -> "\"BOTH\""
            "https://identificacionssl.sedipualba.es" ->
                if ("diputacion-leon-sede" in listOf(first, later)) ALL_ROLES else ""
            "https://sede.transportes.gob.es", "https://ws235.juntadeandalucia.es",
            "https://elhierro.sedelectronica.es", "https://lagomera.sedelectronica.es" -> "\"NEITHER\""
            else -> ALL_ROLES
        }

    private companion object {
        const val ALL_ROLES = "\"NEITHER\",\"LATER\",\"FIRST\",\"BOTH\""
    }

    private fun rejects(label: String, blocks: List<String>) {
        assertThrows(label, IllegalArgumentException::class.java) { parse(blocks) }
    }

    private fun replace(block: String, old: String, new: String): String {
        assertTrue("missing mutation target: $old", old in block)
        return block.replace(old, new)
    }

    private fun withRequestPort(block: String, port: Int): String {
        val field = Regex("\"requestPort\"\\s*:\\s*\\d+")
        return if (field.containsMatchIn(block)) field.replace(block, "\"requestPort\":$port")
        else replace(block, "\"transitionMode\":", "\"requestPort\":$port,\"transitionMode\":")
    }

    private fun navigationProfile(id: String, origin: String, redirect: Boolean): String = """
        {
          "profileId":"$id", "profileVersion":1, "displayName":"Synthetic navigation owner",
          "compatibilityStatus":"EXPERIMENTAL", "activation":"QA_ONLY",
          "startUrl":"https://$id.example/start", "initiatorOrigins":["https://$id.example"],
          "redirectOrigins":[${if (redirect) "\"$origin\"" else ""}],
          "trustedBrowseOrigins":[${if (redirect) "" else "\"$origin\""}],
          "endpoints":[], "operationPolicies":[], "capabilities":[], "clientAuthPolicy":null,
          "certificateRules":{"allowedKeyAlgorithms":["RSA"],"requireDigitalSignatureKeyUsage":true},
          "evidence":[{"url":"https://$id.example/start","reviewedOn":"2026-10-24"}]
        }
    """.trimIndent()
}

package dev.junta.firmamobile.profile

import java.net.URI
import java.time.LocalDate
import java.time.ZoneOffset

object SiteProfileCatalogParser {
    const val MAX_CATALOG_CHARS = 262_144
    private const val MAX_REVIEWED_ORIGIN_SHARES = 128
    private const val MAX_LATER_ORIGIN_OWNERS = 32
    private const val MAX_DISPLAY_NAME_CHARS = 128
    private const val MAX_CONTENT_TYPE_CHARS = 128
    private const val MAX_SAFE_DESCRIPTION_CHARS = 160
    private const val MAX_PORT = 65_535
    private const val HTTPS_PORT = 443
    private const val MAX_CLIENT_AUTH_GRANT_TTL_SECONDS = 60
    private const val REVIEWED_CLIENT_AUTH_GRANT_TTL_SECONDS = 15
    private const val MAX_CLIENT_AUTH_QUERY_VALUE_CHARS = 2_048
    private const val MAX_HTTPS_URL_CHARS = 2_048

    fun parse(json: String): SiteProfileCatalog {
        require(json.length <= MAX_CATALOG_CHARS)
        val root = StrictJson(json).parse().obj("catalog")
        val requiredKeys = setOf("schemaVersion", "catalogVersion", "profiles")
        require(root.values.keys == requiredKeys + listOf("reviewedOriginShares").filter { it in root.values })
        val schemaVersion = root.int("schemaVersion")
        require(schemaVersion == 1 || schemaVersion == 2)
        val catalog = SiteProfileCatalog(
            schemaVersion = schemaVersion,
            catalogVersion = root.int("catalogVersion").also { require(it >= 1) },
            profiles = root.array("profiles").map { if (schemaVersion == 1) profile(it) else profileV2(it) },
            reviewedOriginShares = if ("reviewedOriginShares" in root.values) {
                root.array("reviewedOriginShares").also { require(it.size <= MAX_REVIEWED_ORIGIN_SHARES) }
                    .map(::reviewedOriginShare)
            } else {
                emptyList()
            },
        )
        require(catalog.profiles.map { it.profileId }.toSet().size == catalog.profiles.size)
        validateReviewedOriginShares(catalog)
        validateCatalog(catalog)
        if (schemaVersion == 2) catalog.profiles.forEach(ShimFeatureResolver.Companion::validate)
        return catalog
    }

    private fun reviewedOriginShare(value: JValue): ReviewedOriginShare {
        val o = value.obj("reviewedOriginShare")
        o.exact("origin", "firstOwner", "laterOwners", "redirectRoles", "evidence")
        return ReviewedOriginShare(
            origin = ExactOrigin.parse(o.string("origin")).also { require(it.serialized == o.string("origin")) },
            firstOwner = ProfileId(o.string("firstOwner")),
            laterOwners = strings(o.array("laterOwners")).also {
                require(it.size in 1..MAX_LATER_ORIGIN_OWNERS)
            }.map(::ProfileId).toSet(),
            redirectRoles = enums<RedirectOriginRoles>(o.array("redirectRoles")).also { require(it.isNotEmpty()) },
            evidence = evidence(o.objValue("evidence")),
        )
    }

    private fun validateReviewedOriginShares(catalog: SiteProfileCatalog) {
        val declared = mutableMapOf<Triple<ExactOrigin, ProfileId, ProfileId>, Set<RedirectOriginRoles>>()
        val profiles = catalog.profiles.associateBy { it.profileId }
        catalog.reviewedOriginShares.forEach { share ->
            require(share.evidence in requireNotNull(profiles[share.firstOwner]).evidence)
            share.laterOwners.forEach { later ->
                require(declared.put(Triple(share.origin, share.firstOwner, later), share.redirectRoles) == null)
            }
        }
        val expected = catalog.profiles.flatMap { profile -> profile.allOrigins().map { it to profile.profileId } }
            .groupBy({ it.first }, { it.second })
            .also { claims -> require(claims.values.all { it.size <= MAX_LATER_ORIGIN_OWNERS + 1 }) }
            .flatMap { (origin, owners) -> reviewedOriginRelations(origin, owners) }.toMap()
        require(declared == expected)
    }

    private fun reviewedOriginRelations(origin: ExactOrigin, owners: List<ProfileId>) = owners.flatMap { first ->
        owners.mapNotNull { later ->
            val roles = RedirectOriginRoles.entries.filter { role ->
                isReviewedSharedNavigationOrigin(origin, first, later, role.first, role.later)
            }.toSet()
            if (roles.isEmpty()) null else Triple(origin, first, later) to roles
        }
    }

    private fun SiteProfileCatalog.declaresSharedOrigin(
        origin: ExactOrigin,
        first: NavigationOriginOwner,
        later: NavigationOriginOwner,
    ) = reviewedOriginShares.any { share ->
        share.origin == origin && share.firstOwner == first.profileId && later.profileId in share.laterOwners &&
            share.redirectRoles.any { it.first == first.isRedirectOrigin && it.later == later.isRedirectOrigin }
    }

    private fun profile(value: JValue): SiteProfile {
        val o = value.obj("profile")
        val keys = arrayOf(
            "profileId", "profileVersion", "displayName", "compatibilityStatus", "activation",
            "startUrl", "initiatorOrigins", "redirectOrigins", "trustedBrowseOrigins", "endpoints",
            "operationPolicies", "capabilities", "clientAuthPolicy", "certificateRules", "evidence",
        )
        require(o.values.keys == keys.toSet() + listOf("lastVerifiedOn").filter { it in o.values })
        val compatibilityStatus = enum<CompatibilityStatus>(o.string("compatibilityStatus"))
        val lastVerifiedOn = if ("lastVerifiedOn" in o.values) {
            require(compatibilityStatus == CompatibilityStatus.VERIFIED_E2E)
            val raw = o.string("lastVerifiedOn")
            require(Regex("[0-9]{4}-[0-9]{2}-[0-9]{2}").matches(raw))
            LocalDate.parse(raw).also { require(it.year >= 1 && !it.isAfter(LocalDate.now(ZoneOffset.UTC))) }
        } else {
            null
        }
        val profileId = ProfileId(o.string("profileId"))
        val endpoints = o.array("endpoints").map(::endpoint)
        require(endpoints.map { it.endpointId }.toSet().size == endpoints.size)
        val operations = o.array("operationPolicies").map { value ->
            operation(value, allowBlankFixedExtraPropertyValues = profileId.value == CANTABRIA_PROFILE_ID)
        }
        require(operations.map { it.operation }.toSet().size == operations.size)
        return SiteProfile(
            profileId = profileId,
            profileVersion = o.int("profileVersion").also { require(it >= 1) },
            displayName = o.string("displayName").also {
                require(it.isNotBlank() && it.length <= MAX_DISPLAY_NAME_CHARS)
            },
            compatibilityStatus = compatibilityStatus,
            lastVerifiedOn = lastVerifiedOn,
            activation = enum(o.string("activation")),
            startUrl = strictHttpsUrl(o.string("startUrl")),
            initiatorOrigins = origins(o.array("initiatorOrigins")),
            redirectOrigins = origins(o.array("redirectOrigins")),
            trustedBrowseOrigins = origins(o.array("trustedBrowseOrigins")),
            endpoints = endpoints.associateBy { it.endpointId },
            operationPolicies = operations.associateBy { it.operation },
            capabilities = enums(o.array("capabilities")),
            clientAuthPolicy = o.nullableObject("clientAuthPolicy")?.let { clientAuth(it, profileId) },
            certificateRules = certificateRules(o.objValue("certificateRules")),
            evidence = o.array("evidence").map(::evidence),
        )
    }

    private fun profileV2(value: JValue): SiteProfile {
        val o = value.obj("profile")
        val features = shimFeatures(o.array("shimFeatures"))
        val baseValues = LinkedHashMap(o.values)
        baseValues.remove("shimFeatures")
        return profile(JObject(baseValues)).copy(shimFeatures = features)
    }

    private fun shimFeatures(values: List<JValue>): Set<ShimFeature> =
        values.map { ShimFeature.fromModuleId(it.string()) }.toSet().also { require(it.size == values.size) }

    private fun endpoint(value: JValue): ProfileEndpoint {
        val o = value.obj("endpoint")
        o.exact(
            "endpointId", "purpose", "url", "method", "requestContentTypes",
            "responseContentTypes", "maxRequestBytes", "maxResponseBytes", "redirects",
        )
        return ProfileEndpoint(
            EndpointId(o.string("endpointId")), enum(o.string("purpose")),
            strictHttpsUrl(o.string("url")).also { require(it.rawQuery == null) }, enum(o.string("method")),
            strings(o.array("requestContentTypes")).also { require(it.isNotEmpty() && it.all(::validContentType)) },
            strings(o.array("responseContentTypes")).also { require(it.isNotEmpty() && it.all(::validContentType)) },
            o.int("maxRequestBytes").also { require(it in 1..MAX_BODY_BYTES) },
            o.int("maxResponseBytes").also { require(it in 1..MAX_BODY_BYTES) },
            enum(o.string("redirects")),
        )
    }

    private fun operation(
        value: JValue,
        allowBlankFixedExtraPropertyValues: Boolean,
    ): OperationPolicy {
        val o = value.obj("operationPolicy")
        o.exact(
            "operation", "safeDescription", "inputAdapterId", "callbackContractId", "capabilities", "endpointId",
            "algorithms", "format", "packaging", "mode", "fixedExtraProperties",
            "allowedExtraProperties",
        )
        return OperationPolicy(
            operation = enum(o.string("operation")),
            safeDescription = o.string("safeDescription").also {
                require(
                    it.isNotBlank() && it.length <= MAX_SAFE_DESCRIPTION_CHARS &&
                        it.all { character -> !character.isISOControl() }
                )
            },
            inputAdapterId = ProtocolInputAdapterId(o.string("inputAdapterId")).also {
                require(it.value in REGISTERED_ADAPTERS)
            },
            callbackContractId = CallbackContractId(o.string("callbackContractId")).also {
                require(it.value in REGISTERED_CALLBACKS)
            },
            capabilities = enums(o.array("capabilities")),
            endpointId = o.nullableString("endpointId")?.let(::EndpointId),
            algorithms = enums(o.array("algorithms")),
            format = o.nullableString("format")?.let { enum(it) },
            packaging = o.nullableString("packaging")?.let { enum(it) },
            mode = o.nullableString("mode")?.let { enum(it) },
            fixedExtraProperties = extraProperties(
                o.objValue("fixedExtraProperties"),
                allowBlankValues = allowBlankFixedExtraPropertyValues,
            ),
            allowedExtraProperties = strings(o.array("allowedExtraProperties")),
        )
    }

    private fun clientAuth(o: JObject, profileId: ProfileId): ClientAuthPolicy {
        val baseKeys = arrayOf(
            "transitionMode", "requestOrigins", "sourceUrls", "requestPath", "fixedQueryParameters",
            "requiredEphemeralQueryParameters", "allowEmptyIssuerList", "grantTtlSeconds",
        )
        val optionalKeys = listOf(
            "requestPort",
            "requestMethod",
            "requireOfferedKeyTypeMatch",
            "requireTlsClientAuthExtendedKeyUsage",
            "sourceFixedQueryParameters",
            "sourceRequiredEphemeralQueryParameters",
            "linkedEphemeralQueryParameters",
            "linkedEphemeralQueryParameterMappings",
            "sourceBase64UrlConstraints",
            "requestContinuationUrlConstraints",
            "returnUrlConstraints",
        ).filter { it in o.values }
        o.exact(*(baseKeys.toList() + optionalKeys).toTypedArray())
        val requestPort = if ("requestPort" in o.values) {
            o.int("requestPort").also { require(it in 1..MAX_PORT) }
        } else {
            HTTPS_PORT
        }
        val requestMethod = if ("requestMethod" in o.values) {
            enum<HttpMethod>(o.string("requestMethod"))
        } else {
            HttpMethod.GET
        }
        val requireOfferedKeyTypeMatch = if ("requireOfferedKeyTypeMatch" in o.values) {
            o.boolean("requireOfferedKeyTypeMatch")
        } else {
            true
        }
        val requireTlsClientAuthExtendedKeyUsage = if ("requireTlsClientAuthExtendedKeyUsage" in o.values) {
            o.boolean("requireTlsClientAuthExtendedKeyUsage")
        } else {
            true
        }
        val transitionMode = enum<ClientAuthTransitionMode>(o.string("transitionMode"))
        val requestOrigins = origins(o.array("requestOrigins")).also { require(it.size == 1) }
        val sourceUrls = o.array("sourceUrls").map { strictHttpsUrl(it.string()) }.toSet()
            .also { require(it.isNotEmpty() && it.size == o.array("sourceUrls").size) }
        val requestPath = o.string("requestPath").also {
            require(it.startsWith('/') && URI(null, null, it, null).rawPath == it)
        }
        val fixed = stringMap(o.objValue("fixedQueryParameters"))
        val ephemeral = strings(o.array("requiredEphemeralQueryParameters"))
        val sourceFixed = if ("sourceFixedQueryParameters" in o.values) {
            stringMap(o.objValue("sourceFixedQueryParameters"))
        } else {
            emptyMap()
        }
        val sourceEphemeral = if ("sourceRequiredEphemeralQueryParameters" in o.values) {
            strings(o.array("sourceRequiredEphemeralQueryParameters"))
        } else {
            emptySet()
        }
        val linkedEphemeral = if ("linkedEphemeralQueryParameters" in o.values) {
            strings(o.array("linkedEphemeralQueryParameters"))
        } else {
            emptySet()
        }
        val linkedEphemeralMappings = if ("linkedEphemeralQueryParameterMappings" in o.values) {
            stringMap(o.objValue("linkedEphemeralQueryParameterMappings"))
        } else {
            emptyMap()
        }
        val sourceBase64UrlConstraints = if ("sourceBase64UrlConstraints" in o.values) {
            val constraints = o.objValue("sourceBase64UrlConstraints").obj("sourceBase64UrlConstraints")
            constraints.values.mapValues { (_, value) -> clientAuthUrlConstraint(value) }
        } else {
            emptyMap()
        }
        val requestContinuationUrlConstraints = if ("requestContinuationUrlConstraints" in o.values) {
            o.array("requestContinuationUrlConstraints").map(::clientAuthUrlConstraint).toSet()
                .also { require(it.size == o.array("requestContinuationUrlConstraints").size) }
        } else {
            emptySet()
        }
        val returnUrlConstraints = if ("returnUrlConstraints" in o.values) {
            o.array("returnUrlConstraints").map(::clientAuthUrlConstraint).toSet()
                .also { require(it.size == o.array("returnUrlConstraints").size) }
        } else {
            emptySet()
        }
        require((fixed.keys intersect ephemeral).isEmpty())
        require((sourceFixed.keys intersect sourceEphemeral).isEmpty())
        require(linkedEphemeral.all { it in ephemeral && it in sourceEphemeral })
        require(linkedEphemeralMappings.keys.all { it in sourceEphemeral })
        require(linkedEphemeralMappings.values.all { it in ephemeral })
        require(linkedEphemeralMappings.values.toSet().size == linkedEphemeralMappings.size)
        require((linkedEphemeral intersect linkedEphemeralMappings.keys).isEmpty())
        require((linkedEphemeral intersect linkedEphemeralMappings.values.toSet()).isEmpty())
        require(sourceBase64UrlConstraints.keys.all { it in sourceEphemeral })
        require(sourceBase64UrlConstraints.keys.all(CLIENT_AUTH_PARAMETER_NAME::matches))
        require(requestContinuationUrlConstraints.all { continuation ->
            continuation.fixedQueryParameters == fixed &&
                continuation.requiredEphemeralQueryParameters == ephemeral
        })
        when (transitionMode) {
            ClientAuthTransitionMode.REDIRECT_AFTER_SOURCE -> {
                require(requestMethod == HttpMethod.GET)
                require(linkedEphemeral.isEmpty() && linkedEphemeralMappings.isEmpty())
                require(
                    sourceBase64UrlConstraints.isEmpty() &&
                        requestContinuationUrlConstraints.isEmpty() &&
                        returnUrlConstraints.isEmpty()
                )
                require(
                    fixed.isNotEmpty() || ephemeral.isNotEmpty() || requestPort != HTTPS_PORT ||
                        sourceFixed.isNotEmpty() || sourceEphemeral.isNotEmpty()
                )
            }
            ClientAuthTransitionMode.DIRECT_FROM_SOURCE -> {
                require(requestMethod == HttpMethod.GET)
                require(
                    sourceBase64UrlConstraints.isEmpty() &&
                        requestContinuationUrlConstraints.isEmpty() &&
                        returnUrlConstraints.isEmpty()
                )
                val boundSourceParameters = linkedEphemeral + linkedEphemeralMappings.keys
                val boundTargetParameters = linkedEphemeral + linkedEphemeralMappings.values
                require(sourceEphemeral == boundSourceParameters)
                require(ephemeral == boundTargetParameters)
                if (sourceFixed.isNotEmpty() || sourceEphemeral.isNotEmpty()) {
                    require(boundSourceParameters.isNotEmpty())
                } else {
                    require(ephemeral.isEmpty())
                }
            }
            ClientAuthTransitionMode.IN_PLACE_FROM_SOURCE -> {
                require(requestPort == HTTPS_PORT)
                require(linkedEphemeral.isEmpty() && linkedEphemeralMappings.isEmpty())
                val reviewedUnparameterizedPostSource =
                    profileId.value == MALAGA_PROFILE_ID &&
                        requestMethod == HttpMethod.POST &&
                        requestPort == HTTPS_PORT &&
                        requestOrigins == setOf(ExactOrigin.parse(AIREF_CLIENT_AUTH_ORIGIN)) &&
                        sourceUrls == setOf(URI(MALAGA_CLIENT_AUTH_SOURCE_URL)) &&
                        requestPath == MALAGA_CLIENT_AUTH_REQUEST_PATH &&
                        fixed.isEmpty() && ephemeral.isEmpty()
                require(
                    sourceFixed.isNotEmpty() || sourceEphemeral.isNotEmpty() ||
                        reviewedUnparameterizedPostSource
                )
                when (requestMethod) {
                    HttpMethod.POST -> {
                        require(fixed.isEmpty() && ephemeral.isEmpty())
                        require(
                            sourceBase64UrlConstraints.isEmpty() &&
                                requestContinuationUrlConstraints.isEmpty() &&
                                returnUrlConstraints.isEmpty()
                        )
                    }
                    HttpMethod.GET -> {
                        require(sourceBase64UrlConstraints.isNotEmpty())
                        require(returnUrlConstraints.isNotEmpty())
                        require(fixed.isNotEmpty() || ephemeral.isNotEmpty())
                    }
                }
            }
        }
        return ClientAuthPolicy(
            transitionMode = transitionMode,
            requestOrigins = requestOrigins,
            sourceUrls = sourceUrls,
            requestPath = requestPath,
            fixedQueryParameters = fixed,
            requiredEphemeralQueryParameters = ephemeral,
            allowEmptyIssuerList = o.boolean("allowEmptyIssuerList"),
            grantTtlSeconds = o.int("grantTtlSeconds").also { require(it in 1..MAX_CLIENT_AUTH_GRANT_TTL_SECONDS) },
            requireOfferedKeyTypeMatch = requireOfferedKeyTypeMatch,
            requireTlsClientAuthExtendedKeyUsage = requireTlsClientAuthExtendedKeyUsage,
            requestPort = requestPort,
            requestMethod = requestMethod,
            sourceFixedQueryParameters = sourceFixed,
            sourceRequiredEphemeralQueryParameters = sourceEphemeral,
            linkedEphemeralQueryParameters = linkedEphemeral,
            linkedEphemeralQueryParameterMappings = linkedEphemeralMappings,
            sourceBase64UrlConstraints = sourceBase64UrlConstraints,
            requestContinuationUrlConstraints = requestContinuationUrlConstraints,
            returnUrlConstraints = returnUrlConstraints,
        )
    }

    private fun clientAuthUrlConstraint(value: JValue): ClientAuthUrlConstraint {
        val o = value.obj("clientAuthUrlConstraint")
        o.exact("origin", "path", "fixedQueryParameters", "requiredEphemeralQueryParameters")
        val fixed = stringMap(o.objValue("fixedQueryParameters"))
        val ephemeral = strings(o.array("requiredEphemeralQueryParameters"))
        require((fixed.keys intersect ephemeral).isEmpty())
        require(fixed.keys.all(CLIENT_AUTH_PARAMETER_NAME::matches))
        require(ephemeral.all(CLIENT_AUTH_PARAMETER_NAME::matches))
        require(fixed.values.all { it.length <= MAX_CLIENT_AUTH_QUERY_VALUE_CHARS && it.none(Char::isISOControl) })
        return ClientAuthUrlConstraint(
            origin = ExactOrigin.parse(o.string("origin")),
            path = o.string("path").also {
                require(it.startsWith('/') && URI(null, null, it, null).rawPath == it)
            },
            fixedQueryParameters = fixed,
            requiredEphemeralQueryParameters = ephemeral,
        )
    }

    private fun validateSeguridadSocialAutoFirmaProfile(profile: SiteProfile) {
        require(profile.compatibilityStatus == CompatibilityStatus.VERIFIED_CONTRACT)
        require(profile.activation == ProfileActivation.QA_ONLY)
        require(profile.startUrl == URI(SEGURIDAD_SOCIAL_AUTOFIRMA_START_URL))
        require(profile.initiatorOrigins == setOf(ExactOrigin.parse(SEGURIDAD_SOCIAL_ORIGIN)))
        require(profile.redirectOrigins.isEmpty())
        require(profile.trustedBrowseOrigins.isEmpty())
        require(profile.endpoints.isEmpty())
        require(profile.operationPolicies.isEmpty())
        require(profile.capabilities.isEmpty())
        require(profile.clientAuthPolicy == null)
    }

    private fun certificateRules(value: JValue): CertificateFilterRules {
        val o = value.obj("certificateRules")
        o.exact("allowedKeyAlgorithms", "requireDigitalSignatureKeyUsage")
        val algorithms = strings(o.array("allowedKeyAlgorithms"))
        require(algorithms.isNotEmpty() && algorithms.all { it == "RSA" || it == "EC" })
        return CertificateFilterRules(algorithms, o.boolean("requireDigitalSignatureKeyUsage"))
    }

    private fun evidence(value: JValue): EvidenceReference {
        val o = value.obj("evidence")
        o.exact("url", "reviewedOn")
        return EvidenceReference(strictHttpsUrl(o.string("url")), LocalDate.parse(o.string("reviewedOn")))
    }

    private data class NavigationOriginOwner(
        val profileId: ProfileId,
        val isRedirectOrigin: Boolean,
    )

    private fun validateCatalog(catalog: SiteProfileCatalog) {
        val navigationOriginOwners = mutableMapOf<ExactOrigin, NavigationOriginOwner>()
        val endpointUrlOwners = mutableMapOf<URI, ProfileId>()
        val endpointOwners = mutableMapOf<EndpointId, ProfileId>()
        catalog.profiles.forEach { p ->
            if (p.profileId.value == UGR_PROFILE_ID) {
                validateUgrProfile(p)
            }
            if (p.profileId.value == CANTABRIA_PROFILE_ID) {
                validateCantabriaProfile(p)
            }
            if (p.profileId.value == JCCM_PROFILE_ID) {
                validateJccmProfile(p)
            }
            if (p.profileId.value == JCCM_REGISTRO_PROFILE_ID) {
                validateJccmRegistroProfile(p)
            }
            if (p.profileId.value == MITES_PROFILE_ID) {
                validateMitesProfile(p)
            }
            if (p.profileId.value == SEVILLA_ATSE_PROFILE_ID) {
                validateSevillaAtseProfile(p)
            }
            if (p.profileId.value == CDTI_PROFILE_ID) {
                validateCdtiProfile(p)
            }
            if (p.profileId.value == TRANSPORTES_PROFILE_ID) {
                validateTransportesProfile(p)
            }
            if (p.profileId.value == MELILLA_PROFILE_ID) {
                validateMelillaProfile(p)
            }
            if (p.profileId.value == EXTREMADURA_PROFILE_ID) {
                validateExtremaduraProfile(p)
            }
            if (p.profileId.value == PATTEX_PROFILE_ID) {
                validatePattexProfile(p)
            }
            if (p.profileId.value == LA_PALMA_PROFILE_ID) {
                validateLaPalmaProfile(p)
            }
            if (p.profileId.value == BURGOS_PROFILE_ID) {
                validateBurgosProfile(p)
            }
            if (p.profileId.value == HUESCA_PROFILE_ID) {
                validateHuescaProfile(p)
            }
            if (p.profileId.value == LUGO_PROFILE_ID) {
                validateLugoProfile(p)
            }
            if (p.profileId.value == CAIB_PROFILE_ID) {
                validateCaibProfile(p)
            }
            if (p.profileId.value == LEON_PROFILE_ID) {
                validateLeonProfile(p)
            }
            if (p.profileId.value == ALBACETE_PROFILE_ID) {
                validateAlbaceteProfile(p)
            }
            if (p.profileId.value == MALLORCA_PROFILE_ID) {
                validateMallorcaProfile(p)
            }
            if (p.profileId.value == CUENCA_PROFILE_ID) {
                validateCuencaProfile(p)
            }
            if (p.profileId.value == LA_RIOJA_PROFILE_ID) {
                validateLaRiojaProfile(p)
            }
            if (p.profileId.value == NAVARRA_PROFILE_ID) {
                validateNavarraProfile(p)
            }
            if (p.profileId.value == SORIA_PROFILE_ID) {
                validateSoriaProfile(p)
            }
            if (p.profileId.value == GVA_PROFILE_ID) {
                validateGvaProfile(p)
            }
            if (p.profileId.value == EUSKADI_PROFILE_ID) {
                validateEuskadiProfile(p)
            }
            if (p.profileId.value == JAEN_PROFILE_ID) {
                validateJaenProfile(p)
            }
            if (p.profileId.value == SANIDAD_PROFILE_ID) {
                validateSanidadProfile(p)
            }
            CLAVE_DIRECT_PINS[p.profileId.value]?.takeIf { p.hasClientTls() }?.let { validateClaveDirectProfile(p, it) }
            if (p.profileId.value == VEA_PEG_PROFILE_ID && p.hasClientTls()) {
                validateVeaPegProfile(p)
            }
            if (p.profileId.value == CARNE_JOVEN_PROFILE_ID && p.hasClientTls()) {
                validateCarneJovenProfile(p)
            }
            if (p.profileId.value == AEAT_MIS_DATOS_PROFILE_ID) {
                validateAeatMisDatosProfile(p)
            }
            if (p.profileId.value == IMPORTASS_PROFILE_ID) {
                validateImportassProfile(p)
            }
            if (p.profileId.value == TOLEDO_PROFILE_ID) {
                validateToledoProfile(p)
            }
            if (p.profileId.value == VALLADOLID_PROFILE_ID) {
                validateValladolidProfile(p)
            }
            if (p.profileId.value == MENORCA_PROFILE_ID) {
                validateMenorcaProfile(p)
            }
            if (p.profileId.value == TEA_PROFILE_ID) {
                validateTeaProfile(p)
            }
            if (p.profileId.value == TARRAGONA_PROFILE_ID) {
                validateTarragonaProfile(p)
            }
            if (p.profileId.value == TENERIFE_PROFILE_ID) {
                validateTenerifeProfile(p)
            }
            if (p.profileId.value == FUERTEVENTURA_PROFILE_ID) {
                validateFuerteventuraProfile(p)
            }
            if (p.profileId.value == TRANSPARENCIA_PROFILE_ID) {
                validateTransparenciaProfile(p)
            }
            if (p.profileId.value == EIVISSA_PROFILE_ID) {
                validateEivissaProfile(p)
            }
            if (p.profileId.value == GRAN_CANARIA_PROFILE_ID) {
                validateGranCanariaProfile(p)
            }
            if (p.profileId.value == CANARIAS_PROFILE_ID) {
                validateCanariasProfile(p)
            }
            if (p.profileId.value == MINECO_PROFILE_ID) {
                validateMinecoProfile(p)
            }
            if (p.profileId.value == ISCIII_PROFILE_ID) {
                validateIsciiiProfile(p)
            }
            if (p.profileId.value == VALENCIA_PROFILE_ID) {
                validateValenciaProfile(p)
            }
            if (p.profileId.value == POLICIA_PROFILE_ID) {
                validatePoliciaProfile(p)
            }
            if (p.profileId.value == AIREF_PROFILE_ID) {
                validateAirefProfile(p)
            }
            if (p.profileId.value == EDUCATION_PROFILE_ID) {
                validateEducationProfile(p)
            }
            if (p.profileId.value == CATALUNYA_PROFILE_ID) {
                validateCatalunyaProfile(p)
            }
            if (p.profileId.value == CATALUNYA_SEU_PROFILE_ID) {
                validateCatalunyaSeuProfile(p)
            }
            if (p.profileId.value == MUGEJU_PROFILE_ID) {
                validateMugejuProfile(p)
            }
            if (p.profileId.value == MALAGA_PROFILE_ID) {
                validateMalagaProfile(p)
            }
            if (p.profileId.value == XUNTA_PROFILE_ID) {
                validateXuntaProfile(p)
            }
            if (p.profileId.value == ARAGON_TRAMITES_PROFILE_ID) {
                validateAragonTramitesProfile(p)
            }
            if (p.profileId.value == ACCEDA_PROFILE_ID) {
                validateAccedaProfile(p)
            }
            if (p.profileId.value == SEGURIDAD_SOCIAL_AUTOFIRMA_PROFILE_ID) {
                validateSeguridadSocialAutoFirmaProfile(p)
            }
            if (p.profileId.value == LLEIDA_PROFILE_ID) {
                validateLleidaProfile(p)
            }
            if (p.profileId.value == BADAJOZ_PROFILE_ID) {
                validateBadajozProfile(p)
            }
            require(p.initiatorOrigins.isNotEmpty())
            require(p.startUrl.origin() in p.initiatorOrigins)
            require((p.initiatorOrigins intersect p.redirectOrigins).isEmpty())
            require((p.initiatorOrigins intersect p.trustedBrowseOrigins).isEmpty())
            require((p.redirectOrigins intersect p.trustedBrowseOrigins).isEmpty())
            val clientAuthPolicy = p.clientAuthPolicy
            val clientAuthOrigins = clientAuthPolicy?.requestOrigins ?: emptySet()
            val sameOriginDirectClientAuth = SameOriginDirectClientAuthContract.allowsSameOriginDirect(p)
            val sameOriginRedirectClientAuth = LaRiojaClientAuthContract.allowsSameOriginRedirect(p)
            if (clientAuthPolicy?.requestPort == HTTPS_PORT) {
                require(
                    (clientAuthOrigins intersect p.initiatorOrigins).isEmpty() ||
                        sameOriginDirectClientAuth || sameOriginRedirectClientAuth
                )
                require((clientAuthOrigins intersect p.redirectOrigins).isEmpty() || sameOriginDirectClientAuth)
                require((clientAuthOrigins intersect p.trustedBrowseOrigins).isEmpty())
            }
            if (p.compatibilityStatus == CompatibilityStatus.BROWSE_ONLY ||
                p.compatibilityStatus == CompatibilityStatus.UNSUPPORTED
            ) {
                require(p.operationPolicies.isEmpty() && p.endpoints.isEmpty())
                require(p.capabilities.none {
                    it == Capability.SIGN || it == Capability.SELECT_CERTIFICATE ||
                        it == Capability.CLIENT_TLS_AUTH || it == Capability.AFIRMA_URI
                })
            }
            require(
                p.compatibilityStatus != CompatibilityStatus.UNSUPPORTED || p.activation == ProfileActivation.DISABLED,
            )
            require(
                p.activation != ProfileActivation.ENABLED || p.compatibilityStatus != CompatibilityStatus.UNSUPPORTED,
            )
            require(Capability.CLIENT_TLS_AUTH in p.capabilities == (p.clientAuthPolicy != null))
            p.clientAuthPolicy?.let { policy ->
                if (p.profileId.value == AIREF_PROFILE_ID) {
                    require(p.endpoints.isEmpty())
                    require(p.operationPolicies.keys == setOf(ProtocolOperation.SIGN))
                    require(
                        p.capabilities ==
                            setOf(Capability.SIGN, Capability.LEGACY_SHA1, Capability.CLIENT_TLS_AUTH),
                    )
                } else if (p.profileId.value == JCCM_REGISTRO_PROFILE_ID) {
                    require(p.endpoints.isEmpty())
                    require(p.operationPolicies.keys == setOf(ProtocolOperation.SIGN))
                    require(p.capabilities == setOf(Capability.SIGN, Capability.CLIENT_TLS_AUTH))
                } else {
                    require(p.operationPolicies.isEmpty() && p.endpoints.isEmpty())
                    require(p.capabilities == setOf(Capability.CLIENT_TLS_AUTH))
                }
                if (policy.transitionMode == ClientAuthTransitionMode.DIRECT_FROM_SOURCE &&
                    policy.fixedQueryParameters.isNotEmpty()
                ) {
                    require(
                        p.profileId.value == SANIDAD_PROFILE_ID ||
                            p.profileId.value == TEA_PROFILE_ID ||
                            p.profileId.value == LEON_PROFILE_ID ||
                            p.profileId.value == ALBACETE_PROFILE_ID ||
                            p.profileId.value == CUENCA_PROFILE_ID ||
                            p.profileId.value == MALLORCA_PROFILE_ID ||
                            p.profileId.value == GVA_PROFILE_ID ||
                            p.profileId.value == SORIA_PROFILE_ID
                    )
                }
                require(policy.sourceUrls.all { source ->
                    val allowedSourceOrigins = when {
                        p.profileId.value in setOf(
                            AIREF_PROFILE_ID,
                            ASTURIAS_PROFILE_ID,
                            EDUCATION_PROFILE_ID,
                            CATALUNYA_PROFILE_ID,
                            CATALUNYA_SEU_PROFILE_ID,
                            EUSKADI_PROFILE_ID,
                            MUGEJU_PROFILE_ID,
                            JCCM_REGISTRO_PROFILE_ID,
                            OURENSE_PROFILE_ID,
                            MALAGA_PROFILE_ID,
                            SEVILLA_DIPUTACION_PROFILE_ID,
                            CORUNA_PROFILE_ID,
                        ) -> p.initiatorOrigins + p.redirectOrigins
                        p.profileId.value == TARRAGONA_PROFILE_ID &&
                            policy.transitionMode == ClientAuthTransitionMode.IN_PLACE_FROM_SOURCE ->
                            p.trustedBrowseOrigins
                        else -> p.initiatorOrigins
                    }
                    (source.origin() in allowedSourceOrigins ||
                        policy.transitionMode == ClientAuthTransitionMode.IN_PLACE_FROM_SOURCE &&
                            policy.requestMethod == HttpMethod.GET &&
                            policy.sourceBase64UrlConstraints.isNotEmpty() ||
                        p.profileId.value == NAVARRA_PROFILE_ID &&
                            policy.transitionMode == ClientAuthTransitionMode.DIRECT_FROM_SOURCE &&
                            source.origin() in p.redirectOrigins ||
                        p.profileId.value == ARAGON_TRAMITES_PROFILE_ID &&
                            policy.transitionMode == ClientAuthTransitionMode.REDIRECT_AFTER_SOURCE &&
                            source.origin() in p.redirectOrigins) &&
                        (policy.sourceFixedQueryParameters.isEmpty() &&
                            policy.sourceRequiredEphemeralQueryParameters.isEmpty() ||
                            source.rawQuery == null)
                })
                require(policy.fixedQueryParameters.keys.all(CLIENT_AUTH_PARAMETER_NAME::matches))
                require(policy.fixedQueryParameters.values.all { value ->
                    value.length <= MAX_CLIENT_AUTH_QUERY_VALUE_CHARS && value.none(Char::isISOControl)
                })
                require(policy.requiredEphemeralQueryParameters.all(CLIENT_AUTH_PARAMETER_NAME::matches))
                require(policy.sourceFixedQueryParameters.keys.all(CLIENT_AUTH_PARAMETER_NAME::matches))
                require(policy.sourceFixedQueryParameters.values.all { value ->
                    value.length <= MAX_CLIENT_AUTH_QUERY_VALUE_CHARS && value.none(Char::isISOControl)
                })
                require(policy.sourceRequiredEphemeralQueryParameters.all(CLIENT_AUTH_PARAMETER_NAME::matches))
                require(policy.linkedEphemeralQueryParameters.all(CLIENT_AUTH_PARAMETER_NAME::matches))
                require(policy.linkedEphemeralQueryParameterMappings.keys.all(CLIENT_AUTH_PARAMETER_NAME::matches))
                require(policy.linkedEphemeralQueryParameterMappings.values.all(CLIENT_AUTH_PARAMETER_NAME::matches))
            }
            p.operationPolicies.values.forEach { op ->
                require(op.capabilities.all { it in p.capabilities })
                require(op.endpointId == null || op.endpointId in p.endpoints)
                require((op.fixedExtraProperties.keys intersect op.allowedExtraProperties).isEmpty())
                if (SignatureAlgorithm.SHA1_WITH_RSA in op.algorithms) {
                    require(Capability.LEGACY_SHA1 in p.capabilities && Capability.LEGACY_SHA1 in op.capabilities)
                }
                if (op.operation == ProtocolOperation.SIGN) {
                    require(op.algorithms.isNotEmpty() && op.format != null)
                    require(op.packaging != null && Capability.SIGN in op.capabilities)
                }
                if (op.inputAdapterId.value == ISCIII_INPUT_ADAPTER_ID) {
                    require(
                        p.profileId.value == ISCIII_PROFILE_ID ||
                            p.profileId.value == VALENCIA_PROFILE_ID ||
                            p.profileId.value == XUNTA_PROFILE_ID
                    )
                    require(op.operation == ProtocolOperation.SELECT_CERTIFICATE)
                    require(op.capabilities == setOf(Capability.SELECT_CERTIFICATE))
                    require(op.endpointId == null)
                    require(op.algorithms.isEmpty())
                    require(op.format == null && op.packaging == null && op.mode == null)
                    when (p.profileId.value) {
                        ISCIII_PROFILE_ID -> require(op.fixedExtraProperties == ISCIII_FIXED_EXTRA_PROPERTIES)
                        VALENCIA_PROFILE_ID -> require(op.fixedExtraProperties == VALENCIA_FIXED_EXTRA_PROPERTIES)
                        XUNTA_PROFILE_ID -> require(op.fixedExtraProperties == XUNTA_SELECT_FIXED_EXTRA_PROPERTIES)
                    }
                    require(op.allowedExtraProperties.isEmpty())
                }
                if (op.inputAdapterId.value == "miniapplet-autoscript-v1") {
                    require(op.operation == ProtocolOperation.SIGN)
                    require(
                        op.packaging == if (
                            p.profileId.value == SEVILLA_ATSE_PROFILE_ID ||
                            p.profileId.value == AIREF_PROFILE_ID ||
                            p.profileId.value == GRAN_CANARIA_PROFILE_ID ||
                            p.profileId.value == TRANSPARENCIA_PROFILE_ID ||
                            p.profileId.value == FUERTEVENTURA_PROFILE_ID ||
                            p.profileId.value == MINECO_PROFILE_ID ||
                            p.profileId.value == CDTI_PROFILE_ID ||
                            p.profileId.value == TRANSPORTES_PROFILE_ID ||
                            p.profileId.value == XUNTA_PROFILE_ID
                        ) {
                            SignaturePackaging.ATTACHED
                        } else {
                            SignaturePackaging.DETACHED
                        },
                    )
                    when (p.profileId.value) {
                        XUNTA_PROFILE_ID -> require(op.allowedExtraProperties == XUNTA_ALLOWED_EXTRA_PROPERTIES)
                        EIVISSA_PROFILE_ID -> require(op.allowedExtraProperties == setOf("filter", "mimeType"))
                        else -> require(op.allowedExtraProperties.isEmpty())
                    }
                    when (op.format) {
                        SignatureFormat.CADES -> {
                            if (op.endpointId == null) {
                                if (p.profileId.value == CANTABRIA_PROFILE_ID) {
                                    require(op.mode == SignatureMode.IMPLICIT)
                                    require(op.algorithms == setOf(SignatureAlgorithm.SHA512_WITH_RSA))
                                    require(op.fixedExtraProperties == CANTABRIA_EXTRA_PROPERTIES)
                                } else if (p.profileId.value == MITES_PROFILE_ID) {
                                    require(op.mode == SignatureMode.IMPLICIT)
                                    require(op.algorithms == setOf(SignatureAlgorithm.SHA512_WITH_RSA))
                                    require(op.fixedExtraProperties == MITES_EXTRA_PROPERTIES)
                                } else if (p.profileId.value == TENERIFE_PROFILE_ID) {
                                    require(op.mode == SignatureMode.EXPLICIT)
                                    require(op.algorithms == setOf(SignatureAlgorithm.SHA512_WITH_RSA))
                                    require(op.fixedExtraProperties == TENERIFE_EXTRA_PROPERTIES)
                                } else if (p.profileId.value == EIVISSA_PROFILE_ID) {
                                    require(op.mode == SignatureMode.IMPLICIT)
                                    require(op.algorithms == setOf(SignatureAlgorithm.SHA256_WITH_RSA))
                                    require(op.fixedExtraProperties == EIVISSA_FIXED_EXTRA_PROPERTIES)
                                    require(op.allowedExtraProperties == setOf("filter", "mimeType"))
                                } else if (
                                    p.profileId.value == LLEIDA_PROFILE_ID ||
                                    p.profileId.value == BADAJOZ_PROFILE_ID
                                ) {
                                    require(op.mode == SignatureMode.EXPLICIT)
                                    require(op.algorithms == setOf(SignatureAlgorithm.SHA256_WITH_RSA))
                                    require(op.fixedExtraProperties == LLEIDA_EXTRA_PROPERTIES)
                                } else {
                                    require(op.mode == SignatureMode.EXPLICIT)
                                    require(op.algorithms == setOf(SignatureAlgorithm.SHA1_WITH_RSA))
                                    val expectedLocalCadesProperties = when (p.profileId.value) {
                                        ARAGON_LOCAL_CADES_PROFILE_ID -> mapOf(
                                            "mode" to "explicit",
                                            "filter" to "nonexpired",
                                        )
                                        DGT_LOCAL_CADES_PROFILE_ID -> mapOf(
                                            "filter" to "nonexpired:",
                                        )
                                        UGR_PROFILE_ID -> emptyMap()
                                        JCCM_PROFILE_ID -> emptyMap()
                                        CANARIAS_PROFILE_ID -> CANARIAS_EXTRA_PROPERTIES
                                        else -> null
                                    }
                                    require(
                                        expectedLocalCadesProperties != null &&
                                            op.fixedExtraProperties == expectedLocalCadesProperties,
                                    )
                                }
                            } else {
                                require(op.fixedExtraProperties["serverUrl"] ==
                                    op.endpointId.let(p.endpoints::get)?.url?.toString())
                                when (op.mode) {
                                    SignatureMode.EXPLICIT -> {
                                        val exactKeys = op.fixedExtraProperties.keys
                                        val explicitMode = exactKeys == setOf("serverUrl", "mode") &&
                                            op.fixedExtraProperties["mode"] == "explicit"
                                        val fixedCertificateFilter =
                                            exactKeys == setOf("serverUrl", "filters") &&
                                                !op.fixedExtraProperties["filters"].isNullOrBlank()
                                        require(explicitMode || fixedCertificateFilter)
                                    }
                                    null -> {
                                        require(op.algorithms == setOf(SignatureAlgorithm.SHA1_WITH_RSA))
                                        require(op.fixedExtraProperties.keys ==
                                            setOf("serverUrl", "precalculatedHashAlgorithm"))
                                        require(op.fixedExtraProperties["precalculatedHashAlgorithm"] == "SHA1")
                                    }
                                    SignatureMode.IMPLICIT -> error("implicit direct-data profile is unsupported")
                                }
                            }
                        }
                        SignatureFormat.PADES -> {
                            if (p.profileId.value == ACCEDA_PROFILE_ID) {
                                require(op.endpointId == null && op.mode == null)
                                require(op.packaging == SignaturePackaging.DETACHED)
                                require(op.algorithms == setOf(SignatureAlgorithm.SHA1_WITH_RSA))
                                require(op.fixedExtraProperties == ACCEDA_FIXED_EXTRA_PROPERTIES)
                            } else if (p.profileId.value == XUNTA_PROFILE_ID) {
                                require(op.endpointId == EndpointId(XUNTA_ENDPOINT_ID) && op.mode == null)
                                require(op.algorithms == setOf(SignatureAlgorithm.SHA1_WITH_RSA))
                                require(op.fixedExtraProperties == XUNTA_FIXED_EXTRA_PROPERTIES)
                            } else {
                                require(
                                    p.profileId.value == GRAN_CANARIA_PROFILE_ID ||
                                        p.profileId.value == FUERTEVENTURA_PROFILE_ID ||
                                        p.profileId.value == TRANSPARENCIA_PROFILE_ID ||
                                        p.profileId.value == MINECO_PROFILE_ID,
                                )
                                require(op.endpointId == null && op.mode == null)
                                require(
                                    op.algorithms == if (p.profileId.value == FUERTEVENTURA_PROFILE_ID) {
                                        setOf(SignatureAlgorithm.SHA256_WITH_RSA)
                                    } else {
                                        setOf(SignatureAlgorithm.SHA512_WITH_RSA)
                                    },
                                )
                                val expectedPadesProperties = when (p.profileId.value) {
                                    MINECO_PROFILE_ID -> MINECO_EXTRA_PROPERTIES
                                    TRANSPARENCIA_PROFILE_ID -> TRANSPARENCIA_EXTRA_PROPERTIES
                                    FUERTEVENTURA_PROFILE_ID -> FUERTEVENTURA_EXTRA_PROPERTIES
                                    else -> GRAN_CANARIA_EXTRA_PROPERTIES
                                }
                                require(op.fixedExtraProperties == expectedPadesProperties)
                            }
                        }
                        SignatureFormat.XADES -> {
                            require(op.endpointId == null)
                            if (p.profileId.value == JCCM_REGISTRO_PROFILE_ID) {
                                require(op.mode == SignatureMode.IMPLICIT)
                            } else {
                                require(op.mode == null)
                            }
                            require(
                                op.algorithms == if (
                                    p.profileId.value == SEVILLA_ATSE_PROFILE_ID ||
                                    p.profileId.value == AIREF_PROFILE_ID ||
                                    p.profileId.value == POLICIA_PROFILE_ID ||
                                    p.profileId.value == TRANSPORTES_PROFILE_ID
                                ) {
                                    setOf(SignatureAlgorithm.SHA1_WITH_RSA)
                                } else {
                                    setOf(SignatureAlgorithm.SHA512_WITH_RSA)
                                },
                            )
                            val expectedXadesProperties = when (p.profileId.value) {
                                POLICIA_PROFILE_ID -> POLICIA_FIXED_EXTRA_PROPERTIES
                                CDTI_PROFILE_ID -> CDTI_FIXED_EXTRA_PROPERTIES
                                TRANSPORTES_PROFILE_ID -> TRANSPORTES_FIXED_EXTRA_PROPERTIES
                                JCCM_REGISTRO_PROFILE_ID -> JCCM_REGISTRO_FIXED_EXTRA_PROPERTIES
                                else -> emptyMap()
                            }
                            require(op.fixedExtraProperties == expectedXadesProperties)
                        }
                        SignatureFormat.FACTURAE -> error("unsupported adapter format")
                        null -> error("signing format required")
                    }
                }
            }
            p.endpoints.values.forEach { endpoint ->
                require(endpoint.redirects == RedirectPolicy.DENY)
                require(endpointOwners.put(endpoint.endpointId, p.profileId) == null)
                require(endpointUrlOwners.put(endpoint.url, p.profileId) == null)
            }
            p.allOrigins().forEach { origin ->
                val previousOwner = navigationOriginOwners.putIfAbsent(
                    origin,
                    NavigationOriginOwner(p.profileId, origin in p.redirectOrigins),
                )
                require(
                    previousOwner == null ||
                        (catalog.declaresSharedOrigin(
                            origin, previousOwner, NavigationOriginOwner(p.profileId, origin in p.redirectOrigins),
                        ) && isReviewedSharedNavigationOrigin(
                            origin,
                            previousOwner.profileId,
                            p.profileId,
                            previousOwner.isRedirectOrigin,
                            origin in p.redirectOrigins,
                        )),
                )
            }
        }
    }
    private fun validateIsciiiProfile(profile: SiteProfile) {
        require(profile.profileVersion == ISCIII_PROFILE_VERSION)
        require(profile.displayName == ISCIII_DISPLAY_NAME)
        require(profile.compatibilityStatus == CompatibilityStatus.VERIFIED_CONTRACT)
        require(profile.activation == ProfileActivation.QA_ONLY)
        require(profile.startUrl.toASCIIString() == ISCIII_START_URL)
        require(profile.initiatorOrigins == setOf(ExactOrigin.parse(ISCIII_ORIGIN)))
        require(profile.redirectOrigins.isEmpty())
        require(profile.trustedBrowseOrigins.isEmpty())
        require(profile.endpoints.isEmpty())
        require(profile.capabilities == setOf(Capability.SELECT_CERTIFICATE))
        require(profile.clientAuthPolicy == null)
        require(profile.certificateRules == CertificateFilterRules(setOf("RSA", "EC"), false))
        require(profile.operationPolicies.keys == setOf(ProtocolOperation.SELECT_CERTIFICATE))
        require(
            profile.operationPolicies.getValue(ProtocolOperation.SELECT_CERTIFICATE) == OperationPolicy(
                operation = ProtocolOperation.SELECT_CERTIFICATE,
                safeDescription = ISCIII_SAFE_DESCRIPTION,
                inputAdapterId = ProtocolInputAdapterId(ISCIII_INPUT_ADAPTER_ID),
                callbackContractId = CallbackContractId(ISCIII_CALLBACK_CONTRACT_ID),
                capabilities = setOf(Capability.SELECT_CERTIFICATE),
                endpointId = null,
                algorithms = emptySet(),
                format = null,
                packaging = null,
                mode = null,
                fixedExtraProperties = ISCIII_FIXED_EXTRA_PROPERTIES,
                allowedExtraProperties = emptySet(),
            ),
        )
        require(profile.evidence.map { it.url.toASCIIString() }.toSet() == ISCIII_EVIDENCE_URLS)
        require(profile.evidence.all { it.reviewedOn == LocalDate.parse("2026-08-15") })
    }

    private fun validateValenciaProfile(profile: SiteProfile) {
        require(profile.profileVersion == VALENCIA_PROFILE_VERSION)
        require(profile.displayName == VALENCIA_DISPLAY_NAME)
        require(profile.compatibilityStatus == CompatibilityStatus.VERIFIED_CONTRACT)
        require(profile.activation == ProfileActivation.QA_ONLY)
        require(profile.startUrl.toASCIIString() == VALENCIA_START_URL)
        require(profile.initiatorOrigins == setOf(ExactOrigin.parse(VALENCIA_ORIGIN)))
        require(profile.redirectOrigins.isEmpty())
        require(profile.trustedBrowseOrigins.isEmpty())
        require(profile.endpoints.isEmpty())
        require(profile.capabilities == setOf(Capability.SELECT_CERTIFICATE))
        require(profile.clientAuthPolicy == null)
        require(profile.certificateRules == CertificateFilterRules(setOf("RSA"), false))
        require(profile.operationPolicies.keys == setOf(ProtocolOperation.SELECT_CERTIFICATE))
        require(
            profile.operationPolicies.getValue(ProtocolOperation.SELECT_CERTIFICATE) == OperationPolicy(
                operation = ProtocolOperation.SELECT_CERTIFICATE,
                safeDescription = VALENCIA_SAFE_DESCRIPTION,
                inputAdapterId = ProtocolInputAdapterId(VALENCIA_INPUT_ADAPTER_ID),
                callbackContractId = CallbackContractId(VALENCIA_CALLBACK_CONTRACT_ID),
                capabilities = setOf(Capability.SELECT_CERTIFICATE),
                endpointId = null,
                algorithms = emptySet(),
                format = null,
                packaging = null,
                mode = null,
                fixedExtraProperties = VALENCIA_FIXED_EXTRA_PROPERTIES,
                allowedExtraProperties = emptySet(),
            ),
        )
        require(profile.evidence.map { it.url.toASCIIString() }.toSet() == VALENCIA_EVIDENCE_URLS)
        require(profile.evidence.all { it.reviewedOn == LocalDate.parse("2026-08-15") })
    }

    private fun validateNavarraProfile(profile: SiteProfile) {
        require(profile.profileVersion == NAVARRA_PROFILE_VERSION)
        require(profile.displayName == NAVARRA_DISPLAY_NAME)
        require(profile.compatibilityStatus == CompatibilityStatus.VERIFIED_CONTRACT)
        require(profile.activation == ProfileActivation.QA_ONLY)
        require(profile.startUrl.toASCIIString() == NAVARRA_START_URL)
        require(profile.initiatorOrigins == setOf(ExactOrigin.parse(NAVARRA_ENTRY_ORIGIN)))
        require(
            profile.redirectOrigins == setOf(
                ExactOrigin.parse(NAVARRA_RGE_ORIGIN),
                ExactOrigin.parse(NAVARRA_ATEKA_ORIGIN),
            ),
        )
        require(profile.trustedBrowseOrigins.isEmpty())
        require(profile.endpoints.isEmpty())
        require(profile.operationPolicies.isEmpty())
        require(profile.capabilities == setOf(Capability.CLIENT_TLS_AUTH))
        require(profile.certificateRules == CertificateFilterRules(setOf("RSA"), true))
        require(
            profile.clientAuthPolicy == ClientAuthPolicy(
                transitionMode = ClientAuthTransitionMode.DIRECT_FROM_SOURCE,
                requestOrigins = setOf(ExactOrigin.parse(NAVARRA_ATEKA_ORIGIN)),
                sourceUrls = setOf(URI(NAVARRA_SOURCE_URL)),
                requestPath = NAVARRA_REQUEST_PATH,
                fixedQueryParameters = emptyMap(),
                requiredEphemeralQueryParameters = setOf(NAVARRA_TARGET_TOKEN_PARAMETER),
                allowEmptyIssuerList = true,
                grantTtlSeconds = REVIEWED_CLIENT_AUTH_GRANT_TTL_SECONDS,
                requestPort = HTTPS_PORT,
                sourceFixedQueryParameters = emptyMap(),
                sourceRequiredEphemeralQueryParameters = setOf(NAVARRA_SOURCE_TOKEN_PARAMETER),
                linkedEphemeralQueryParameters = emptySet(),
                linkedEphemeralQueryParameterMappings = mapOf(
                    NAVARRA_SOURCE_TOKEN_PARAMETER to NAVARRA_TARGET_TOKEN_PARAMETER,
                ),
            ),
        )
        require(profile.evidence.map { it.url.toASCIIString() }.toSet() == NAVARRA_EVIDENCE_URLS)
        require(profile.evidence.all { it.reviewedOn == LocalDate.parse("2026-08-18") })
    }

    private fun validateSanidadProfile(profile: SiteProfile) {
        require(profile.profileVersion == SANIDAD_PROFILE_VERSION)
        require(profile.displayName == SANIDAD_DISPLAY_NAME)
        require(profile.compatibilityStatus == CompatibilityStatus.VERIFIED_CONTRACT)
        require(profile.activation == ProfileActivation.QA_ONLY)
        require(profile.startUrl.toASCIIString() == SANIDAD_START_URL)
        require(profile.initiatorOrigins == setOf(ExactOrigin.parse(SANIDAD_ORIGIN)))
        require(profile.redirectOrigins.isEmpty())
        require(profile.trustedBrowseOrigins.isEmpty())
        require(profile.endpoints.isEmpty())
        require(profile.operationPolicies.isEmpty())
        require(profile.capabilities == setOf(Capability.CLIENT_TLS_AUTH))
        require(profile.certificateRules == CertificateFilterRules(setOf("RSA", "EC"), true))
        require(
            profile.clientAuthPolicy == ClientAuthPolicy(
                transitionMode = ClientAuthTransitionMode.DIRECT_FROM_SOURCE,
                requestOrigins = setOf(ExactOrigin.parse(SANIDAD_ORIGIN)),
                sourceUrls = setOf(URI(SANIDAD_SOURCE_URL)),
                requestPath = SANIDAD_REQUEST_PATH,
                fixedQueryParameters = SANIDAD_FIXED_QUERY,
                requiredEphemeralQueryParameters = emptySet(),
                allowEmptyIssuerList = false,
                grantTtlSeconds = REVIEWED_CLIENT_AUTH_GRANT_TTL_SECONDS,
                requestPort = HTTPS_PORT,
            ),
        )
        require(profile.evidence.map { it.url.toASCIIString() }.toSet() == SANIDAD_EVIDENCE_URLS)
        require(profile.evidence.all { it.reviewedOn == LocalDate.parse("2026-08-14") })
    }

    // A profile id without a TLS contract (navigation-only owner) grants nothing the pins would guard.
    private fun SiteProfile.hasClientTls() = clientAuthPolicy != null || Capability.CLIENT_TLS_AUTH in capabilities

    private fun claveEvidence(reviewedOn: String, vararg urls: String) = urls.associateWith { reviewedOn }

    private class ClaveOriginPins(
        val initiator: String,
        val redirect: Set<String>,
        val trustedBrowse: Set<String>,
    )

    private class ClaveDirectPin(
        val displayName: String,
        val startUrl: String,
        val origins: ClaveOriginPins,
        val sourceUrl: String,
        val keyAlgorithms: Set<String>,
        val evidence: Map<String, String>,
    )

    private fun validateClaveDirectProfile(profile: SiteProfile, pin: ClaveDirectPin) {
        require(profile.profileVersion == 1)
        require(profile.displayName == pin.displayName)
        require(profile.compatibilityStatus == CompatibilityStatus.VERIFIED_CONTRACT)
        require(profile.activation == ProfileActivation.QA_ONLY)
        require(profile.startUrl.toASCIIString() == pin.startUrl)
        require(profile.initiatorOrigins == setOf(ExactOrigin.parse(pin.origins.initiator)))
        require(profile.redirectOrigins == pin.origins.redirect.map { ExactOrigin.parse(it) }.toSet())
        require(profile.trustedBrowseOrigins == pin.origins.trustedBrowse.map { ExactOrigin.parse(it) }.toSet())
        require(profile.endpoints.isEmpty())
        require(profile.operationPolicies.isEmpty())
        require(profile.capabilities == setOf(Capability.CLIENT_TLS_AUTH))
        require(profile.certificateRules == CertificateFilterRules(pin.keyAlgorithms, true))
        require(
            profile.clientAuthPolicy == ClientAuthPolicy(
                transitionMode = ClientAuthTransitionMode.DIRECT_FROM_SOURCE,
                requestOrigins = setOf(ExactOrigin.parse(CLAVE_IDENT_ORIGIN)),
                sourceUrls = setOf(URI(pin.sourceUrl)),
                requestPath = CLAVE_AUTHENTICATE_PATH,
                fixedQueryParameters = emptyMap(),
                requiredEphemeralQueryParameters = emptySet(),
                allowEmptyIssuerList = true,
                grantTtlSeconds = REVIEWED_CLIENT_AUTH_GRANT_TTL_SECONDS,
                requireOfferedKeyTypeMatch = true,
                requireTlsClientAuthExtendedKeyUsage = true,
                requestPort = HTTPS_PORT,
                requestMethod = HttpMethod.GET,
                sourceFixedQueryParameters = emptyMap(),
                sourceRequiredEphemeralQueryParameters = emptySet(),
                linkedEphemeralQueryParameters = emptySet(),
                linkedEphemeralQueryParameterMappings = emptyMap(),
                sourceBase64UrlConstraints = emptyMap(),
                requestContinuationUrlConstraints = emptySet(),
                returnUrlConstraints = emptySet(),
            ),
        )
        require(profile.evidence.associate { it.url.toASCIIString() to it.reviewedOn.toString() } == pin.evidence)
    }

    private fun validateVeaPegProfile(profile: SiteProfile) {
        require(profile.profileVersion == VEA_PEG_PROFILE_VERSION)
        require(profile.displayName == "Junta de Andalucía — Presentación electrónica general")
        require(profile.compatibilityStatus == CompatibilityStatus.VERIFIED_CONTRACT)
        require(profile.activation == ProfileActivation.QA_ONLY)
        require(profile.startUrl.toASCIIString() == VEA_PEG_START_URL)
        require(profile.initiatorOrigins == setOf(ExactOrigin.parse(VEA_PEG_PORTAL_ORIGIN)))
        require(profile.redirectOrigins.isEmpty())
        require(profile.trustedBrowseOrigins.isEmpty())
        require(profile.endpoints.isEmpty())
        require(profile.operationPolicies.isEmpty())
        require(profile.capabilities == setOf(Capability.CLIENT_TLS_AUTH))
        require(profile.certificateRules == CertificateFilterRules(setOf("RSA", "EC"), false))
        require(profile.clientAuthPolicy == veaPegPolicy())
        require(profile.evidence.associate { it.url.toASCIIString() to it.reviewedOn.toString() } == VEA_PEG_EVIDENCE)
    }

    private fun veaPegPolicy() = ClientAuthPolicy(
        transitionMode = ClientAuthTransitionMode.IN_PLACE_FROM_SOURCE,
        requestOrigins = setOf(ExactOrigin.parse(VEA_PEG_REQUEST_ORIGIN)),
        sourceUrls = setOf(URI(VEA_PEG_SOURCE_URL)),
        requestPath = VEA_PEG_REQUEST_PATH,
        fixedQueryParameters = veaPegFacadeQuery(),
        requiredEphemeralQueryParameters = VEA_PEG_TICKET_PARAMETERS,
        allowEmptyIssuerList = true,
        grantTtlSeconds = VEA_PEG_GRANT_TTL_SECONDS,
        requireOfferedKeyTypeMatch = false,
        requireTlsClientAuthExtendedKeyUsage = false,
        requestPort = HTTPS_PORT,
        requestMethod = HttpMethod.GET,
        sourceFixedQueryParameters = mapOf(
            "modoAcceso" to "afirma",
            "codigoProcedimiento" to "PEG_VEA",
            "comeBackUrl" to "aHR0cHM6Ly92ZWFqYS5jbG91ZC5qdW50YWRlYW5kYWx1Y2lhLmVzL2F1dGhGYWNhZGU=",
        ),
        sourceRequiredEphemeralQueryParameters = setOf("redirectUrl"),
        linkedEphemeralQueryParameters = emptySet(),
        linkedEphemeralQueryParameterMappings = emptyMap(),
        sourceBase64UrlConstraints = mapOf(
            "redirectUrl" to ClientAuthUrlConstraint(
                origin = ExactOrigin.parse(VEA_PEG_PORTAL_ORIGIN),
                path = "/inicio/procedimiento-detalle/PEG_VEA",
                fixedQueryParameters = mapOf("iniciarSolicitud" to "true"),
                requiredEphemeralQueryParameters = setOf("procedureId", "versionId"),
            ),
        ),
        requestContinuationUrlConstraints = (1..VEA_PEG_CONTINUATION_HOPS).map { hop ->
            ClientAuthUrlConstraint(
                origin = ExactOrigin.parse("https://ws235-$hop.juntadeandalucia.es"),
                path = "/authenticationFacade/${hop + 1}",
                fixedQueryParameters = veaPegFacadeQuery(),
                requiredEphemeralQueryParameters = VEA_PEG_TICKET_PARAMETERS,
            )
        }.toSet(),
        returnUrlConstraints = veaPegReturnConstraints(),
    )

    private fun veaPegReturnConstraints() = setOf(
        ClientAuthUrlConstraint(
            origin = ExactOrigin.parse(VEA_PEG_API_ORIGIN),
            path = "/auth/returnLogin",
            requiredEphemeralQueryParameters = setOf("resCode"),
        ),
        ClientAuthUrlConstraint(
            origin = ExactOrigin.parse(VEA_PEG_API_ORIGIN),
            path = "/auth/returnLogin",
            fixedQueryParameters = mapOf("appId" to "CHIE.VEA"),
            requiredEphemeralQueryParameters = setOf("resCode", "ticketId", "webSessionId"),
        ),
        ClientAuthUrlConstraint(origin = ExactOrigin.parse(VEA_PEG_API_ORIGIN), path = "/auth/endLogin"),
        ClientAuthUrlConstraint(
            origin = ExactOrigin.parse(VEA_PEG_PORTAL_ORIGIN),
            path = "/authFacade",
            requiredEphemeralQueryParameters = setOf("error", "redirectUrl"),
        ),
        ClientAuthUrlConstraint(
            origin = ExactOrigin.parse(VEA_PEG_PORTAL_ORIGIN),
            path = "/authFacade",
            requiredEphemeralQueryParameters = setOf("token", "redirectUrl"),
        ),
    )

    private fun veaPegFacadeQuery() = mapOf(
        "action" to "validateCert",
        "appId" to "CHIE.VEA",
        "comeBackURL" to "aHR0cHM6Ly9hcGktdmVhamEuY2xvdWQuanVudGFkZWFuZGFsdWNpYS5lcy9hdXRoL3JldHVybkxvZ2lu",
    )

    private fun validateCarneJovenProfile(profile: SiteProfile) {
        require(profile.profileVersion == 1)
        require(profile.displayName == CARNE_JOVEN_DISPLAY_NAME)
        require(profile.compatibilityStatus == CompatibilityStatus.VERIFIED_E2E)
        // QA_ONLY only narrows the profile, so the catalogue may downgrade it but never widen it.
        require(profile.activation == ProfileActivation.ENABLED || profile.activation == ProfileActivation.QA_ONLY)
        require(profile.startUrl.toASCIIString() == CARNE_JOVEN_START_URL)
        require(profile.initiatorOrigins == setOf(ExactOrigin.parse(CARNE_JOVEN_INITIATOR_ORIGIN)))
        require(profile.redirectOrigins.isEmpty())
        require(profile.trustedBrowseOrigins.isEmpty())
        require(profile.endpoints.isEmpty())
        require(profile.operationPolicies.isEmpty())
        require(profile.capabilities == setOf(Capability.CLIENT_TLS_AUTH))
        require(profile.certificateRules == CertificateFilterRules(setOf("RSA"), true))
        require(
            profile.clientAuthPolicy == ClientAuthPolicy(
                transitionMode = ClientAuthTransitionMode.REDIRECT_AFTER_SOURCE,
                requestOrigins = setOf(ExactOrigin.parse(CARNE_JOVEN_REQUEST_ORIGIN)),
                sourceUrls = setOf(URI(CARNE_JOVEN_SOURCE_URL)),
                requestPath = CARNE_JOVEN_REQUEST_PATH,
                fixedQueryParameters = mapOf(
                    "action" to "validateCert",
                    "appId" to "IAJ.CARNETJOVEN",
                    "comeBackURL" to CARNE_JOVEN_COME_BACK_URL,
                ),
                requiredEphemeralQueryParameters = setOf("ticketId", "webSessionId"),
                allowEmptyIssuerList = true,
                grantTtlSeconds = REVIEWED_CLIENT_AUTH_GRANT_TTL_SECONDS,
                requireOfferedKeyTypeMatch = true,
                requireTlsClientAuthExtendedKeyUsage = true,
                requestPort = HTTPS_PORT,
                requestMethod = HttpMethod.GET,
                sourceFixedQueryParameters = emptyMap(),
                sourceRequiredEphemeralQueryParameters = emptySet(),
                linkedEphemeralQueryParameters = emptySet(),
                linkedEphemeralQueryParameterMappings = emptyMap(),
                sourceBase64UrlConstraints = emptyMap(),
                requestContinuationUrlConstraints = emptySet(),
                returnUrlConstraints = emptySet(),
            ),
        )
        require(profile.evidence.map { it.url.toASCIIString() }.toSet() == CARNE_JOVEN_EVIDENCE_URLS)
        require(profile.evidence.all { it.reviewedOn == LocalDate.parse("2026-07-18") })
    }

    private fun validateAeatMisDatosProfile(profile: SiteProfile) {
        require(profile.profileVersion == AEAT_PROFILE_VERSION)
        require(profile.displayName == AEAT_DISPLAY_NAME)
        require(profile.compatibilityStatus == CompatibilityStatus.VERIFIED_CONTRACT)
        require(profile.activation == ProfileActivation.QA_ONLY)
        require(profile.startUrl.toASCIIString() == AEAT_START_URL)
        require(profile.initiatorOrigins == setOf(ExactOrigin.parse(AEAT_INITIATOR_ORIGIN)))
        require(profile.redirectOrigins.isEmpty())
        require(profile.trustedBrowseOrigins.isEmpty())
        require(profile.endpoints.isEmpty())
        require(profile.operationPolicies.isEmpty())
        require(profile.capabilities == setOf(Capability.CLIENT_TLS_AUTH))
        require(profile.certificateRules == CertificateFilterRules(setOf("RSA", "EC"), true))
        require(
            profile.clientAuthPolicy == ClientAuthPolicy(
                transitionMode = ClientAuthTransitionMode.DIRECT_FROM_SOURCE,
                requestOrigins = setOf(ExactOrigin.parse(AEAT_REQUEST_ORIGIN)),
                sourceUrls = setOf(URI(AEAT_SOURCE_URL)),
                requestPath = AEAT_REQUEST_PATH,
                fixedQueryParameters = emptyMap(),
                requiredEphemeralQueryParameters = emptySet(),
                allowEmptyIssuerList = false,
                grantTtlSeconds = REVIEWED_CLIENT_AUTH_GRANT_TTL_SECONDS,
                requireOfferedKeyTypeMatch = true,
                requireTlsClientAuthExtendedKeyUsage = true,
                requestPort = HTTPS_PORT,
                requestMethod = HttpMethod.GET,
                sourceFixedQueryParameters = emptyMap(),
                sourceRequiredEphemeralQueryParameters = emptySet(),
                linkedEphemeralQueryParameters = emptySet(),
                linkedEphemeralQueryParameterMappings = emptyMap(),
                sourceBase64UrlConstraints = emptyMap(),
                requestContinuationUrlConstraints = emptySet(),
                returnUrlConstraints = emptySet(),
            ),
        )
        require(profile.evidence.map { it.url.toASCIIString() }.toSet() == AEAT_EVIDENCE_URLS)
        require(profile.evidence.all { it.reviewedOn == LocalDate.parse("2026-07-31") })
    }

    private fun validateImportassProfile(profile: SiteProfile) {
        require(profile.profileVersion == IMPORTASS_PROFILE_VERSION)
        require(profile.displayName == IMPORTASS_DISPLAY_NAME)
        require(profile.compatibilityStatus == CompatibilityStatus.VERIFIED_CONTRACT)
        require(profile.activation == ProfileActivation.QA_ONLY)
        require(profile.startUrl.toASCIIString() == IMPORTASS_START_URL)
        require(
            profile.initiatorOrigins ==
                setOf(ExactOrigin.parse(IMPORTASS_PORTAL_ORIGIN), ExactOrigin.parse(IMPORTASS_IDP_ORIGIN)),
        )
        require(profile.redirectOrigins.isEmpty())
        require(profile.trustedBrowseOrigins.isEmpty())
        require(profile.endpoints.isEmpty())
        require(profile.operationPolicies.isEmpty())
        require(profile.capabilities == setOf(Capability.CLIENT_TLS_AUTH))
        require(profile.certificateRules == CertificateFilterRules(setOf("RSA", "EC"), true))
        require(
            profile.clientAuthPolicy == ClientAuthPolicy(
                transitionMode = ClientAuthTransitionMode.DIRECT_FROM_SOURCE,
                requestOrigins = setOf(ExactOrigin.parse(IMPORTASS_REQUEST_ORIGIN)),
                sourceUrls = setOf(URI(IMPORTASS_SOURCE_URL)),
                requestPath = IMPORTASS_REQUEST_PATH,
                fixedQueryParameters = emptyMap(),
                requiredEphemeralQueryParameters = emptySet(),
                allowEmptyIssuerList = false,
                grantTtlSeconds = REVIEWED_CLIENT_AUTH_GRANT_TTL_SECONDS,
                requireOfferedKeyTypeMatch = true,
                requireTlsClientAuthExtendedKeyUsage = true,
                requestPort = HTTPS_PORT,
                requestMethod = HttpMethod.GET,
                sourceFixedQueryParameters = emptyMap(),
                sourceRequiredEphemeralQueryParameters = emptySet(),
                linkedEphemeralQueryParameters = emptySet(),
                linkedEphemeralQueryParameterMappings = emptyMap(),
                sourceBase64UrlConstraints = emptyMap(),
                requestContinuationUrlConstraints = emptySet(),
                returnUrlConstraints = emptySet(),
            ),
        )
        require(profile.evidence.map { it.url.toASCIIString() }.toSet() == IMPORTASS_EVIDENCE_URLS)
        require(profile.evidence.all { it.reviewedOn == LocalDate.parse("2026-08-21") })
    }

    private fun validateToledoProfile(profile: SiteProfile) {
        require(profile.profileVersion == TOLEDO_PROFILE_VERSION)
        require(profile.displayName == TOLEDO_DISPLAY_NAME)
        require(profile.compatibilityStatus == CompatibilityStatus.VERIFIED_CONTRACT)
        require(profile.activation == ProfileActivation.QA_ONLY)
        require(profile.startUrl.toASCIIString() == TOLEDO_START_URL)
        require(profile.initiatorOrigins == setOf(ExactOrigin.parse(TOLEDO_ORIGIN)))
        require(profile.redirectOrigins.isEmpty())
        require(profile.trustedBrowseOrigins.isEmpty())
        require(profile.endpoints.isEmpty())
        require(profile.operationPolicies.isEmpty())
        require(profile.capabilities == setOf(Capability.CLIENT_TLS_AUTH))
        require(profile.certificateRules == CertificateFilterRules(setOf("RSA", "EC"), true))
        require(
            profile.clientAuthPolicy == ClientAuthPolicy(
                transitionMode = ClientAuthTransitionMode.DIRECT_FROM_SOURCE,
                requestOrigins = setOf(ExactOrigin.parse(TOLEDO_ORIGIN)),
                sourceUrls = setOf(URI(TOLEDO_SOURCE_URL)),
                requestPath = TOLEDO_REQUEST_PATH,
                fixedQueryParameters = emptyMap(),
                requiredEphemeralQueryParameters = emptySet(),
                allowEmptyIssuerList = false,
                grantTtlSeconds = REVIEWED_CLIENT_AUTH_GRANT_TTL_SECONDS,
                requireOfferedKeyTypeMatch = true,
                requireTlsClientAuthExtendedKeyUsage = true,
                requestPort = TOLEDO_REQUEST_PORT,
                requestMethod = HttpMethod.GET,
                sourceFixedQueryParameters = emptyMap(),
                sourceRequiredEphemeralQueryParameters = emptySet(),
                linkedEphemeralQueryParameters = emptySet(),
                linkedEphemeralQueryParameterMappings = emptyMap(),
                sourceBase64UrlConstraints = emptyMap(),
                requestContinuationUrlConstraints = emptySet(),
                returnUrlConstraints = emptySet(),
            ),
        )
        require(profile.evidence.map { it.url.toASCIIString() }.toSet() == TOLEDO_EVIDENCE_URLS)
        require(profile.evidence.all { it.reviewedOn == LocalDate.parse("2026-08-13") })
    }

    private fun validateValladolidProfile(profile: SiteProfile) {
        require(profile.profileVersion == VALLADOLID_PROFILE_VERSION)
        require(profile.displayName == VALLADOLID_DISPLAY_NAME)
        require(profile.compatibilityStatus == CompatibilityStatus.VERIFIED_CONTRACT)
        require(profile.activation == ProfileActivation.QA_ONLY)
        require(profile.startUrl.toASCIIString() == VALLADOLID_START_URL)
        require(profile.initiatorOrigins == setOf(ExactOrigin.parse(VALLADOLID_ORIGIN)))
        require(profile.redirectOrigins.isEmpty())
        require(profile.trustedBrowseOrigins.isEmpty())
        require(profile.endpoints.isEmpty())
        require(profile.operationPolicies.isEmpty())
        require(profile.capabilities == setOf(Capability.CLIENT_TLS_AUTH))
        require(profile.certificateRules == CertificateFilterRules(setOf("RSA", "EC"), true))
        require(
            profile.clientAuthPolicy == ClientAuthPolicy(
                transitionMode = ClientAuthTransitionMode.REDIRECT_AFTER_SOURCE,
                requestOrigins = setOf(ExactOrigin.parse(VALLADOLID_ORIGIN)),
                sourceUrls = setOf(URI(VALLADOLID_SOURCE_URL)),
                requestPath = VALLADOLID_REQUEST_PATH,
                fixedQueryParameters = emptyMap(),
                requiredEphemeralQueryParameters = emptySet(),
                allowEmptyIssuerList = true,
                grantTtlSeconds = REVIEWED_CLIENT_AUTH_GRANT_TTL_SECONDS,
                requireOfferedKeyTypeMatch = true,
                requireTlsClientAuthExtendedKeyUsage = true,
                requestPort = VALLADOLID_REQUEST_PORT,
                requestMethod = HttpMethod.GET,
                sourceFixedQueryParameters = emptyMap(),
                sourceRequiredEphemeralQueryParameters = emptySet(),
                linkedEphemeralQueryParameters = emptySet(),
                linkedEphemeralQueryParameterMappings = emptyMap(),
                sourceBase64UrlConstraints = emptyMap(),
                requestContinuationUrlConstraints = emptySet(),
                returnUrlConstraints = emptySet(),
            ),
        )
        require(profile.evidence.map { it.url.toASCIIString() }.toSet() == VALLADOLID_EVIDENCE_URLS)
        require(profile.evidence.all { it.reviewedOn == LocalDate.parse("2026-08-13") })
    }

    private fun validateSoriaProfile(profile: SiteProfile) {
        require(profile.profileVersion == 1)
        require(profile.compatibilityStatus == CompatibilityStatus.VERIFIED_CONTRACT)
        require(profile.activation == ProfileActivation.QA_ONLY)
        require(profile.startUrl.toASCIIString() == SORIA_START_URL)
        require(profile.initiatorOrigins == setOf(ExactOrigin.parse(SORIA_ORIGIN)))
        require(profile.redirectOrigins.isEmpty())
        require(profile.trustedBrowseOrigins.isEmpty())
        require(
            profile.clientAuthPolicy == ClientAuthPolicy(
                transitionMode = ClientAuthTransitionMode.DIRECT_FROM_SOURCE,
                requestOrigins = setOf(ExactOrigin.parse(SORIA_ORIGIN)),
                sourceUrls = setOf(URI(SORIA_START_URL)),
                requestPath = SORIA_REQUEST_PATH,
                fixedQueryParameters = SORIA_FIXED_QUERY,
                requiredEphemeralQueryParameters = emptySet(),
                allowEmptyIssuerList = true,
                grantTtlSeconds = 15,
                requestPort = 443,
                sourceFixedQueryParameters = emptyMap(),
                sourceRequiredEphemeralQueryParameters = emptySet(),
                linkedEphemeralQueryParameters = emptySet(),
            ),
        )
    }

    private fun validateMenorcaProfile(profile: SiteProfile) {
        require(profile.profileVersion == MENORCA_PROFILE_VERSION)
        require(profile.displayName == MENORCA_DISPLAY_NAME)
        require(profile.compatibilityStatus == CompatibilityStatus.VERIFIED_CONTRACT)
        require(profile.activation == ProfileActivation.QA_ONLY)
        require(profile.startUrl.toASCIIString() == MENORCA_START_URL)
        require(profile.initiatorOrigins == setOf(ExactOrigin.parse(MENORCA_ORIGIN)))
        require(profile.redirectOrigins.isEmpty())
        require(profile.trustedBrowseOrigins.isEmpty())
        require(profile.endpoints.isEmpty())
        require(profile.operationPolicies.isEmpty())
        require(profile.capabilities == setOf(Capability.CLIENT_TLS_AUTH))
        require(profile.certificateRules == CertificateFilterRules(setOf("RSA"), true))
        require(
            profile.clientAuthPolicy == ClientAuthPolicy(
                transitionMode = ClientAuthTransitionMode.DIRECT_FROM_SOURCE,
                requestOrigins = setOf(ExactOrigin.parse(MENORCA_ORIGIN)),
                sourceUrls = setOf(URI(MENORCA_SOURCE_URL)),
                requestPath = MENORCA_REQUEST_PATH,
                fixedQueryParameters = emptyMap(),
                requiredEphemeralQueryParameters = setOf(MENORCA_URL_PARAMETER),
                allowEmptyIssuerList = true,
                grantTtlSeconds = REVIEWED_CLIENT_AUTH_GRANT_TTL_SECONDS,
                requestPort = HTTPS_PORT,
                sourceFixedQueryParameters = emptyMap(),
                sourceRequiredEphemeralQueryParameters = setOf(MENORCA_URL_PARAMETER),
                linkedEphemeralQueryParameters = setOf(MENORCA_URL_PARAMETER),
            ),
        )
        require(profile.evidence.map { it.url.toASCIIString() }.toSet() == MENORCA_EVIDENCE_URLS)
        require(profile.evidence.all { it.reviewedOn == LocalDate.parse("2026-08-18") })
    }

    private fun validateTarragonaProfile(profile: SiteProfile) {
        require(profile.profileVersion == TARRAGONA_PROFILE_VERSION)
        require(profile.displayName == TARRAGONA_DISPLAY_NAME)
        require(profile.compatibilityStatus == CompatibilityStatus.VERIFIED_CONTRACT)
        require(profile.activation == ProfileActivation.QA_ONLY)
        require(profile.startUrl.toASCIIString() == TARRAGONA_START_URL)
        require(profile.initiatorOrigins == setOf(ExactOrigin.parse(TARRAGONA_INITIATOR_ORIGIN)))
        require(profile.redirectOrigins.isEmpty())
        require(
            profile.trustedBrowseOrigins == setOf(
                ExactOrigin.parse(TARRAGONA_INTEGRATOR_ORIGIN),
                ExactOrigin.parse(TARRAGONA_VALID_ORIGIN),
            ),
        )
        require(profile.endpoints.isEmpty())
        require(profile.operationPolicies.isEmpty())
        require(profile.capabilities == setOf(Capability.CLIENT_TLS_AUTH))
        require(profile.certificateRules == CertificateFilterRules(setOf("RSA", "EC"), true))
        require(
            profile.clientAuthPolicy == ClientAuthPolicy(
                transitionMode = ClientAuthTransitionMode.IN_PLACE_FROM_SOURCE,
                requestOrigins = setOf(ExactOrigin.parse(TARRAGONA_CERT_ORIGIN)),
                sourceUrls = setOf(URI(TARRAGONA_VALID_SOURCE)),
                requestPath = TARRAGONA_CERT_PATH,
                fixedQueryParameters = emptyMap(),
                requiredEphemeralQueryParameters = emptySet(),
                allowEmptyIssuerList = true,
                grantTtlSeconds = REVIEWED_CLIENT_AUTH_GRANT_TTL_SECONDS,
                requestPort = HTTPS_PORT,
                requestMethod = HttpMethod.POST,
                sourceFixedQueryParameters = TARRAGONA_SOURCE_FIXED_QUERY,
                sourceRequiredEphemeralQueryParameters = setOf("state"),
            ),
        )
        require(profile.evidence.map { it.url.toASCIIString() }.toSet() == TARRAGONA_EVIDENCE_URLS)
        require(profile.evidence.all { it.reviewedOn == LocalDate.parse("2026-08-21") })
    }

    private fun validateTeaProfile(profile: SiteProfile) {
        require(profile.profileVersion == TEA_PROFILE_VERSION)
        require(profile.displayName == TEA_DISPLAY_NAME)
        require(profile.compatibilityStatus == CompatibilityStatus.VERIFIED_CONTRACT)
        require(profile.activation == ProfileActivation.QA_ONLY)
        require(profile.startUrl.toASCIIString() == TEA_SOURCE_URL)
        require(profile.initiatorOrigins == setOf(ExactOrigin.parse(TEA_SOURCE_ORIGIN)))
        require(profile.redirectOrigins.isEmpty())
        require(profile.trustedBrowseOrigins.isEmpty())
        require(profile.endpoints.isEmpty())
        require(profile.operationPolicies.isEmpty())
        require(profile.capabilities == setOf(Capability.CLIENT_TLS_AUTH))
        require(profile.certificateRules == CertificateFilterRules(setOf("RSA", "EC"), true))
        require(
            profile.clientAuthPolicy == ClientAuthPolicy(
                transitionMode = ClientAuthTransitionMode.DIRECT_FROM_SOURCE,
                requestOrigins = setOf(ExactOrigin.parse(TEA_REQUEST_ORIGIN)),
                sourceUrls = setOf(URI(TEA_SOURCE_URL)),
                requestPath = TEA_REQUEST_PATH,
                fixedQueryParameters = linkedMapOf("tram" to "0"),
                requiredEphemeralQueryParameters = emptySet(),
                allowEmptyIssuerList = false,
                grantTtlSeconds = REVIEWED_CLIENT_AUTH_GRANT_TTL_SECONDS,
                requestPort = HTTPS_PORT,
            ),
        )
        require(profile.evidence.map { it.url.toASCIIString() }.toSet() == TEA_EVIDENCE_URLS)
        require(profile.evidence.all { it.reviewedOn == LocalDate.parse("2026-08-14") })
    }

    private fun validateCanariasProfile(profile: SiteProfile) {
        require(profile.profileVersion == CANARIAS_PROFILE_VERSION)
        require(profile.displayName == CANARIAS_DISPLAY_NAME)
        require(profile.compatibilityStatus == CompatibilityStatus.VERIFIED_CONTRACT)
        require(profile.activation == ProfileActivation.QA_ONLY)
        require(profile.startUrl.toASCIIString() == CANARIAS_START_URL)
        require(profile.initiatorOrigins == setOf(ExactOrigin.parse(CANARIAS_ORIGIN)))
        require(profile.redirectOrigins.isEmpty())
        require(profile.trustedBrowseOrigins.isEmpty())
        require(profile.endpoints.isEmpty())
        require(profile.capabilities == setOf(Capability.SIGN, Capability.LEGACY_SHA1))
        require(profile.clientAuthPolicy == null)
        require(profile.certificateRules == CertificateFilterRules(setOf("RSA"), true))
        require(profile.operationPolicies.keys == setOf(ProtocolOperation.SIGN))
        require(
            profile.operationPolicies.getValue(ProtocolOperation.SIGN) == OperationPolicy(
                operation = ProtocolOperation.SIGN,
                safeDescription = CANARIAS_SAFE_DESCRIPTION,
                inputAdapterId = ProtocolInputAdapterId("miniapplet-autoscript-v1"),
                callbackContractId = CallbackContractId("miniapplet-sign-callback-v1"),
                capabilities = setOf(Capability.SIGN, Capability.LEGACY_SHA1),
                endpointId = null,
                algorithms = setOf(SignatureAlgorithm.SHA1_WITH_RSA),
                format = SignatureFormat.CADES,
                packaging = SignaturePackaging.DETACHED,
                mode = SignatureMode.EXPLICIT,
                fixedExtraProperties = CANARIAS_EXTRA_PROPERTIES,
                allowedExtraProperties = emptySet(),
            ),
        )
        require(profile.evidence.map { it.url.toASCIIString() }.toSet() == CANARIAS_EVIDENCE_URLS)
        require(profile.evidence.all { it.reviewedOn == LocalDate.parse("2026-08-17") })
    }

    private fun validateTransparenciaProfile(profile: SiteProfile) {
        require(profile.profileVersion == TRANSPARENCIA_PROFILE_VERSION)
        require(profile.displayName == TRANSPARENCIA_DISPLAY_NAME)
        require(profile.compatibilityStatus == CompatibilityStatus.VERIFIED_CONTRACT)
        require(profile.activation == ProfileActivation.QA_ONLY)
        require(profile.startUrl.toASCIIString() == TRANSPARENCIA_START_URL)
        require(profile.initiatorOrigins == setOf(ExactOrigin.parse(TRANSPARENCIA_ORIGIN)))
        require(profile.redirectOrigins.isEmpty())
        require(profile.trustedBrowseOrigins.isEmpty())
        require(profile.endpoints.isEmpty())
        require(profile.capabilities == setOf(Capability.SIGN))
        require(profile.clientAuthPolicy == null)
        require(profile.certificateRules == CertificateFilterRules(setOf("RSA"), false))
        require(
            profile.operationPolicies == mapOf(
                ProtocolOperation.SIGN to OperationPolicy(
                    operation = ProtocolOperation.SIGN,
                    safeDescription = TRANSPARENCIA_SAFE_DESCRIPTION,
                    inputAdapterId = ProtocolInputAdapterId("miniapplet-autoscript-v1"),
                    callbackContractId = CallbackContractId("miniapplet-sign-callback-v1"),
                    capabilities = setOf(Capability.SIGN),
                    endpointId = null,
                    algorithms = setOf(SignatureAlgorithm.SHA512_WITH_RSA),
                    format = SignatureFormat.PADES,
                    packaging = SignaturePackaging.ATTACHED,
                    mode = null,
                    fixedExtraProperties = TRANSPARENCIA_EXTRA_PROPERTIES,
                    allowedExtraProperties = emptySet(),
                ),
            ),
        )
        require(profile.evidence.map { it.url.toASCIIString() }.toSet() == TRANSPARENCIA_EVIDENCE_URLS)
        require(profile.evidence.all { it.reviewedOn == LocalDate.parse("2026-08-18") })
    }

    private fun validateFuerteventuraProfile(profile: SiteProfile) {
        require(profile.profileVersion == FUERTEVENTURA_PROFILE_VERSION)
        require(profile.displayName == FUERTEVENTURA_DISPLAY_NAME)
        require(profile.compatibilityStatus == CompatibilityStatus.VERIFIED_CONTRACT)
        require(profile.activation == ProfileActivation.QA_ONLY)
        require(profile.startUrl.toASCIIString() == FUERTEVENTURA_START_URL)
        require(profile.initiatorOrigins == setOf(ExactOrigin.parse(FUERTEVENTURA_ORIGIN)))
        require(profile.redirectOrigins.isEmpty())
        require(profile.trustedBrowseOrigins.isEmpty())
        require(profile.endpoints.isEmpty())
        require(profile.capabilities == setOf(Capability.SIGN))
        require(profile.clientAuthPolicy == null)
        require(profile.certificateRules == CertificateFilterRules(setOf("RSA"), false))
        require(
            profile.operationPolicies == mapOf(
                ProtocolOperation.SIGN to OperationPolicy(
                    operation = ProtocolOperation.SIGN,
                    safeDescription = FUERTEVENTURA_SAFE_DESCRIPTION,
                    inputAdapterId = ProtocolInputAdapterId("miniapplet-autoscript-v1"),
                    callbackContractId = CallbackContractId("miniapplet-sign-callback-v1"),
                    capabilities = setOf(Capability.SIGN),
                    endpointId = null,
                    algorithms = setOf(SignatureAlgorithm.SHA256_WITH_RSA),
                    format = SignatureFormat.PADES,
                    packaging = SignaturePackaging.ATTACHED,
                    mode = null,
                    fixedExtraProperties = FUERTEVENTURA_EXTRA_PROPERTIES,
                    allowedExtraProperties = emptySet(),
                ),
            ),
        )
        require(profile.evidence.map { it.url.toASCIIString() }.toSet() == FUERTEVENTURA_EVIDENCE_URLS)
        require(profile.evidence.all { it.reviewedOn == LocalDate.parse("2026-08-18") })
    }

    private fun validateGranCanariaProfile(profile: SiteProfile) {
        require(profile.profileVersion == GRAN_CANARIA_PROFILE_VERSION)
        require(profile.displayName == GRAN_CANARIA_DISPLAY_NAME)
        require(profile.compatibilityStatus == CompatibilityStatus.VERIFIED_CONTRACT)
        require(profile.activation == ProfileActivation.QA_ONLY)
        require(profile.startUrl.toASCIIString() == GRAN_CANARIA_START_URL)
        require(profile.initiatorOrigins == setOf(ExactOrigin.parse(GRAN_CANARIA_ORIGIN)))
        require(profile.redirectOrigins.isEmpty())
        require(profile.trustedBrowseOrigins.isEmpty())
        require(profile.endpoints.isEmpty())
        require(profile.capabilities == setOf(Capability.SIGN))
        require(profile.clientAuthPolicy == null)
        require(profile.certificateRules == CertificateFilterRules(setOf("RSA"), false))
        require(
            profile.operationPolicies == mapOf(
                ProtocolOperation.SIGN to OperationPolicy(
                    operation = ProtocolOperation.SIGN,
                    safeDescription = GRAN_CANARIA_SAFE_DESCRIPTION,
                    inputAdapterId = ProtocolInputAdapterId("miniapplet-autoscript-v1"),
                    callbackContractId = CallbackContractId("miniapplet-sign-callback-v1"),
                    capabilities = setOf(Capability.SIGN),
                    endpointId = null,
                    algorithms = setOf(SignatureAlgorithm.SHA512_WITH_RSA),
                    format = SignatureFormat.PADES,
                    packaging = SignaturePackaging.ATTACHED,
                    mode = null,
                    fixedExtraProperties = GRAN_CANARIA_EXTRA_PROPERTIES,
                    allowedExtraProperties = emptySet(),
                ),
            ),
        )
        require(profile.evidence.map { it.url.toASCIIString() }.toSet() == GRAN_CANARIA_EVIDENCE_URLS)
        require(profile.evidence.all { it.reviewedOn == LocalDate.parse("2026-08-17") })
    }

    private fun validateMinecoProfile(profile: SiteProfile) {
        require(profile.profileVersion == MINECO_PROFILE_VERSION)
        require(profile.displayName == MINECO_DISPLAY_NAME)
        require(profile.compatibilityStatus == CompatibilityStatus.VERIFIED_CONTRACT)
        require(profile.activation == ProfileActivation.QA_ONLY)
        require(profile.startUrl.toASCIIString() == MINECO_START_URL)
        require(profile.initiatorOrigins == setOf(ExactOrigin.parse(MINECO_ORIGIN)))
        require(profile.redirectOrigins.isEmpty())
        require(profile.trustedBrowseOrigins == MINECO_BROWSE_ORIGINS)
        require(profile.endpoints.isEmpty())
        require(profile.capabilities == setOf(Capability.SIGN))
        require(profile.clientAuthPolicy == null)
        require(profile.certificateRules == CertificateFilterRules(setOf("RSA"), false))
        require(
            profile.operationPolicies == mapOf(
                ProtocolOperation.SIGN to OperationPolicy(
                    operation = ProtocolOperation.SIGN,
                    safeDescription = MINECO_SAFE_DESCRIPTION,
                    inputAdapterId = ProtocolInputAdapterId("miniapplet-autoscript-v1"),
                    callbackContractId = CallbackContractId("miniapplet-sign-callback-v1"),
                    capabilities = setOf(Capability.SIGN),
                    endpointId = null,
                    algorithms = setOf(SignatureAlgorithm.SHA512_WITH_RSA),
                    format = SignatureFormat.PADES,
                    packaging = SignaturePackaging.ATTACHED,
                    mode = null,
                    fixedExtraProperties = MINECO_EXTRA_PROPERTIES,
                    allowedExtraProperties = emptySet(),
                ),
            ),
        )
        require(profile.evidence.map { it.url.toASCIIString() }.toSet() == MINECO_EVIDENCE_URLS)
        require(profile.evidence.all { it.reviewedOn == LocalDate.parse("2026-08-17") })
    }

    private fun validateXuntaProfile(profile: SiteProfile) {
        require(profile.profileVersion == XUNTA_PROFILE_VERSION)
        require(profile.displayName == XUNTA_DISPLAY_NAME)
        require(profile.compatibilityStatus == CompatibilityStatus.VERIFIED_CONTRACT)
        require(profile.activation == ProfileActivation.QA_ONLY)
        require(profile.startUrl.toASCIIString() == XUNTA_START_URL)
        require(profile.initiatorOrigins == setOf(ExactOrigin.parse(XUNTA_ORIGIN)))
        require(profile.redirectOrigins.isEmpty())
        require(profile.trustedBrowseOrigins.isEmpty())
        require(profile.capabilities == setOf(Capability.SIGN, Capability.SELECT_CERTIFICATE, Capability.LEGACY_SHA1))
        require(profile.clientAuthPolicy == null)
        require(profile.certificateRules == CertificateFilterRules(setOf("RSA"), false))
        require(profile.endpoints.keys == setOf(EndpointId(XUNTA_ENDPOINT_ID)))
        require(
            profile.endpoints.getValue(EndpointId(XUNTA_ENDPOINT_ID)) == ProfileEndpoint(
                endpointId = EndpointId(XUNTA_ENDPOINT_ID),
                purpose = EndpointPurpose.TRIPHASE,
                url = URI(XUNTA_ENDPOINT_URL),
                method = HttpMethod.POST,
                requestContentTypes = setOf("application/x-www-form-urlencoded; charset=UTF-8"),
                responseContentTypes = setOf("text/plain"),
                maxRequestBytes = 2_097_152,
                maxResponseBytes = 2_097_152,
                redirects = RedirectPolicy.DENY,
            ),
        )
        require(profile.operationPolicies.keys == setOf(ProtocolOperation.SIGN, ProtocolOperation.SELECT_CERTIFICATE))
        require(
            profile.operationPolicies.getValue(ProtocolOperation.SIGN) == OperationPolicy(
                operation = ProtocolOperation.SIGN,
                safeDescription = XUNTA_SIGN_SAFE_DESCRIPTION,
                inputAdapterId = ProtocolInputAdapterId("miniapplet-autoscript-v1"),
                callbackContractId = CallbackContractId("miniapplet-sign-callback-v1"),
                capabilities = setOf(Capability.SIGN, Capability.LEGACY_SHA1),
                endpointId = EndpointId(XUNTA_ENDPOINT_ID),
                algorithms = setOf(SignatureAlgorithm.SHA1_WITH_RSA),
                format = SignatureFormat.PADES,
                packaging = SignaturePackaging.ATTACHED,
                mode = null,
                fixedExtraProperties = XUNTA_FIXED_EXTRA_PROPERTIES,
                allowedExtraProperties = XUNTA_ALLOWED_EXTRA_PROPERTIES,
            ),
        )
        require(
            profile.operationPolicies.getValue(ProtocolOperation.SELECT_CERTIFICATE) == OperationPolicy(
                operation = ProtocolOperation.SELECT_CERTIFICATE,
                safeDescription = XUNTA_SELECT_SAFE_DESCRIPTION,
                inputAdapterId = ProtocolInputAdapterId(ISCIII_INPUT_ADAPTER_ID),
                callbackContractId = CallbackContractId(ISCIII_CALLBACK_CONTRACT_ID),
                capabilities = setOf(Capability.SELECT_CERTIFICATE),
                endpointId = null,
                algorithms = emptySet(),
                format = null,
                packaging = null,
                mode = null,
                fixedExtraProperties = XUNTA_SELECT_FIXED_EXTRA_PROPERTIES,
                allowedExtraProperties = emptySet(),
            ),
        )
        require(profile.evidence.map { it.url.toASCIIString() }.toSet() == XUNTA_EVIDENCE_URLS)
        require(profile.evidence.all { it.reviewedOn == LocalDate.parse("2026-08-18") })
    }

    private fun validateEivissaProfile(profile: SiteProfile) {
        require(profile.profileVersion == EIVISSA_PROFILE_VERSION)
        require(profile.displayName == EIVISSA_DISPLAY_NAME)
        require(profile.compatibilityStatus == CompatibilityStatus.VERIFIED_CONTRACT)
        require(profile.activation == ProfileActivation.QA_ONLY)
        require(profile.startUrl.toASCIIString() == EIVISSA_START_URL)
        require(profile.initiatorOrigins == setOf(ExactOrigin.parse(EIVISSA_ORIGIN)))
        require(
            profile.redirectOrigins.isEmpty() && profile.trustedBrowseOrigins.isEmpty() && profile.endpoints.isEmpty(),
        )
        require(profile.capabilities == setOf(Capability.SIGN) && profile.clientAuthPolicy == null)
        require(profile.certificateRules == CertificateFilterRules(setOf("RSA"), true))
        require(profile.operationPolicies.keys == setOf(ProtocolOperation.SIGN))
        val op = profile.operationPolicies.getValue(ProtocolOperation.SIGN)
        require(op.safeDescription == EIVISSA_SAFE_DESCRIPTION)
        require(op.inputAdapterId == ProtocolInputAdapterId("miniapplet-autoscript-v1"))
        require(op.callbackContractId == CallbackContractId("autoscript-sign-callback-v1"))
        require(op.capabilities == setOf(Capability.SIGN) && op.endpointId == null)
        require(op.algorithms == setOf(SignatureAlgorithm.SHA256_WITH_RSA))
        require(op.format == SignatureFormat.CADES && op.packaging == SignaturePackaging.DETACHED)
        require(op.mode == SignatureMode.IMPLICIT)
        require(op.fixedExtraProperties == EIVISSA_FIXED_EXTRA_PROPERTIES)
        require(op.allowedExtraProperties == setOf("filter", "mimeType"))
        require(profile.evidence.map { it.url.toASCIIString() }.toSet() == EIVISSA_EVIDENCE_URLS)
        require(profile.evidence.all { it.reviewedOn == LocalDate.parse("2026-08-18") })
    }

    private fun validateLleidaProfile(profile: SiteProfile) {
        require(profile.startUrl.toASCIIString() == LLEIDA_START_URL)
        require(profile.initiatorOrigins == setOf(ExactOrigin.parse(LLEIDA_ORIGIN)))
        require(profile.redirectOrigins.isEmpty() && profile.trustedBrowseOrigins.isEmpty())
    }

    private fun validateBadajozProfile(profile: SiteProfile) {
        require(profile.startUrl.toASCIIString() == BADAJOZ_START_URL)
        require(profile.initiatorOrigins == setOf(ExactOrigin.parse(BADAJOZ_ORIGIN)))
        require(profile.redirectOrigins.isEmpty() && profile.trustedBrowseOrigins.isEmpty())
    }

    private fun validateTenerifeProfile(profile: SiteProfile) {
        require(profile.profileVersion == TENERIFE_PROFILE_VERSION)
        require(profile.displayName == TENERIFE_DISPLAY_NAME)
        require(profile.compatibilityStatus == CompatibilityStatus.VERIFIED_CONTRACT)
        require(profile.activation == ProfileActivation.QA_ONLY)
        require(profile.startUrl.toASCIIString() == TENERIFE_START_URL)
        require(profile.initiatorOrigins == setOf(ExactOrigin.parse(TENERIFE_ORIGIN)))
        require(profile.redirectOrigins.isEmpty())
        require(profile.trustedBrowseOrigins.isEmpty())
        require(profile.endpoints.isEmpty())
        require(profile.capabilities == setOf(Capability.SIGN))
        require(profile.clientAuthPolicy == null)
        require(profile.certificateRules == CertificateFilterRules(setOf("RSA"), true))
        require(
            profile.operationPolicies.getValue(ProtocolOperation.SIGN) == OperationPolicy(
                operation = ProtocolOperation.SIGN,
                safeDescription = TENERIFE_SAFE_DESCRIPTION,
                inputAdapterId = ProtocolInputAdapterId("miniapplet-autoscript-v1"),
                callbackContractId = CallbackContractId("miniapplet-sign-callback-v1"),
                capabilities = setOf(Capability.SIGN),
                endpointId = null,
                algorithms = setOf(SignatureAlgorithm.SHA512_WITH_RSA),
                format = SignatureFormat.CADES,
                packaging = SignaturePackaging.DETACHED,
                mode = SignatureMode.EXPLICIT,
                fixedExtraProperties = TENERIFE_EXTRA_PROPERTIES,
                allowedExtraProperties = emptySet(),
            ),
        )
        require(profile.operationPolicies.keys == setOf(ProtocolOperation.SIGN))
        require(profile.evidence.map { it.url.toASCIIString() }.toSet() == TENERIFE_EVIDENCE_URLS)
        require(profile.evidence.all { it.reviewedOn == LocalDate.parse("2026-08-14") })
    }

    private fun validateMelillaProfile(profile: SiteProfile) {
        require(profile.profileVersion == MELILLA_PROFILE_VERSION)
        require(profile.displayName == MELILLA_DISPLAY_NAME)
        require(profile.compatibilityStatus == CompatibilityStatus.VERIFIED_CONTRACT)
        require(profile.activation == ProfileActivation.QA_ONLY)
        require(profile.startUrl.toASCIIString() == MELILLA_START_URL)
        require(profile.initiatorOrigins == setOf(ExactOrigin.parse(MELILLA_ORIGIN)))
        require(profile.redirectOrigins.isEmpty())
        require(profile.trustedBrowseOrigins.isEmpty())
        require(profile.endpoints.isEmpty())
        require(profile.capabilities == setOf(Capability.SIGN))
        require(profile.clientAuthPolicy == null)
        require(profile.certificateRules == CertificateFilterRules(setOf("RSA"), true))
        require(profile.evidence.isNotEmpty())
        require(profile.operationPolicies.keys == setOf(ProtocolOperation.SIGN))
        require(
            profile.operationPolicies.getValue(ProtocolOperation.SIGN) == OperationPolicy(
                operation = ProtocolOperation.SIGN,
                safeDescription = MELILLA_SAFE_DESCRIPTION,
                inputAdapterId = ProtocolInputAdapterId("melilla-batch-autoscript-v1"),
                callbackContractId = CallbackContractId("melilla-batch-result-v1"),
                capabilities = setOf(Capability.SIGN),
                endpointId = null,
                algorithms = setOf(SignatureAlgorithm.SHA256_WITH_RSA),
                format = SignatureFormat.CADES,
                packaging = SignaturePackaging.DETACHED,
                mode = null,
                fixedExtraProperties = emptyMap(),
                allowedExtraProperties = emptySet(),
            ),
        )
    }

    private fun validateExtremaduraProfile(profile: SiteProfile) {
        require(profile.profileVersion == EXTREMADURA_PROFILE_VERSION)
        require(profile.displayName == EXTREMADURA_DISPLAY_NAME)
        require(profile.compatibilityStatus == CompatibilityStatus.VERIFIED_CONTRACT)
        require(profile.activation == ProfileActivation.QA_ONLY)
        require(profile.startUrl.toASCIIString() == EXTREMADURA_START_URL)
        require(profile.initiatorOrigins == setOf(ExactOrigin.parse(EXTREMADURA_ORIGIN)))
        require(profile.redirectOrigins.isEmpty())
        require(profile.trustedBrowseOrigins.isEmpty())
        require(profile.endpoints.isEmpty())
        require(profile.capabilities == setOf(Capability.SIGN))
        require(profile.clientAuthPolicy == null)
        require(profile.certificateRules == CertificateFilterRules(setOf("RSA"), true))
        require(profile.evidence.isNotEmpty())
        require(profile.operationPolicies.keys == setOf(ProtocolOperation.SIGN))
        require(
            profile.operationPolicies.getValue(ProtocolOperation.SIGN) == OperationPolicy(
                operation = ProtocolOperation.SIGN,
                safeDescription = EXTREMADURA_SAFE_DESCRIPTION,
                inputAdapterId = ProtocolInputAdapterId("extremadura-batch-autoscript-v1"),
                callbackContractId = CallbackContractId("extremadura-batch-result-v1"),
                capabilities = setOf(Capability.SIGN),
                endpointId = null,
                algorithms = setOf(SignatureAlgorithm.SHA256_WITH_RSA),
                format = SignatureFormat.CADES,
                packaging = SignaturePackaging.DETACHED,
                mode = null,
                fixedExtraProperties = emptyMap(),
                allowedExtraProperties = emptySet(),
            ),
        )
    }

    private fun validatePattexProfile(profile: SiteProfile) {
        require(profile.profileVersion == PATTEX_PROFILE_VERSION)
        require(profile.displayName == PATTEX_DISPLAY_NAME)
        require(profile.compatibilityStatus == CompatibilityStatus.VERIFIED_CONTRACT)
        require(profile.activation == ProfileActivation.QA_ONLY)
        require(profile.startUrl.toASCIIString() == PATTEX_START_URL)
        require(profile.initiatorOrigins == setOf(ExactOrigin.parse(PATTEX_ORIGIN)))
        require(profile.redirectOrigins.isEmpty())
        require(profile.trustedBrowseOrigins.isEmpty())
        require(profile.endpoints.isEmpty())
        require(profile.operationPolicies.isEmpty())
        require(profile.capabilities == setOf(Capability.CLIENT_TLS_AUTH))
        require(profile.certificateRules == CertificateFilterRules(setOf("RSA"), true))
        require(
            profile.clientAuthPolicy == ClientAuthPolicy(
                transitionMode = ClientAuthTransitionMode.DIRECT_FROM_SOURCE,
                requestOrigins = setOf(ExactOrigin.parse(PATTEX_ORIGIN)),
                sourceUrls = setOf(URI(PATTEX_START_URL)),
                requestPath = PATTEX_CLIENT_AUTH_PATH,
                fixedQueryParameters = emptyMap(),
                requiredEphemeralQueryParameters = emptySet(),
                allowEmptyIssuerList = true,
                grantTtlSeconds = REVIEWED_CLIENT_AUTH_GRANT_TTL_SECONDS,
                requestPort = HTTPS_PORT,
            ),
        )
        require(profile.evidence.map { it.url.toASCIIString() }.toSet() == PATTEX_EVIDENCE_URLS)
        require(profile.evidence.all { it.reviewedOn == LocalDate.parse("2026-08-19") })
    }

    private fun validateMugejuProfile(profile: SiteProfile) {
        require(profile.profileVersion == MUGEJU_PROFILE_VERSION)
        require(profile.displayName == MUGEJU_DISPLAY_NAME)
        require(profile.compatibilityStatus == CompatibilityStatus.VERIFIED_CONTRACT)
        require(profile.activation == ProfileActivation.QA_ONLY)
        require(profile.startUrl.toASCIIString() == MUGEJU_START_URL)
        require(profile.initiatorOrigins == setOf(ExactOrigin.parse(MUGEJU_ORIGIN)))
        require(profile.redirectOrigins == setOf(ExactOrigin.parse(MUGEJU_CLAVE_ORIGIN)))
        require(profile.trustedBrowseOrigins.isEmpty())
        require(profile.endpoints.isEmpty())
        require(profile.operationPolicies.isEmpty())
        require(profile.capabilities == setOf(Capability.CLIENT_TLS_AUTH))
        require(profile.certificateRules == CertificateFilterRules(setOf("RSA"), true))
        require(
            profile.clientAuthPolicy == ClientAuthPolicy(
                transitionMode = ClientAuthTransitionMode.DIRECT_FROM_SOURCE,
                requestOrigins = setOf(ExactOrigin.parse(MUGEJU_CLIENT_AUTH_ORIGIN)),
                sourceUrls = setOf(URI(MUGEJU_CLIENT_AUTH_SOURCE_URL)),
                requestPath = MUGEJU_CLIENT_AUTH_REQUEST_PATH,
                fixedQueryParameters = emptyMap(),
                requiredEphemeralQueryParameters = emptySet(),
                allowEmptyIssuerList = true,
                grantTtlSeconds = REVIEWED_CLIENT_AUTH_GRANT_TTL_SECONDS,
                requestPort = HTTPS_PORT,
            ),
        )
        require(
            profile.evidence.associate { it.url.toASCIIString() to it.reviewedOn } ==
                MUGEJU_EVIDENCE_REVIEWS,
        )
    }

    private fun validateMalagaProfile(profile: SiteProfile) {
        require(profile.profileVersion == MALAGA_PROFILE_VERSION)
        require(profile.displayName == MALAGA_DISPLAY_NAME)
        require(profile.compatibilityStatus == CompatibilityStatus.VERIFIED_CONTRACT)
        require(profile.activation == ProfileActivation.QA_ONLY)
        require(profile.startUrl.toASCIIString() == MALAGA_START_URL)
        require(profile.initiatorOrigins == setOf(ExactOrigin.parse(MALAGA_ORIGIN)))
        require(
            profile.redirectOrigins == setOf(
                ExactOrigin.parse(MALAGA_LOCAL_CLAVE_ORIGIN),
                ExactOrigin.parse(AIREF_CLAVE_ORIGIN),
            ),
        )
        require(profile.trustedBrowseOrigins.isEmpty())
        require(profile.endpoints.isEmpty())
        require(profile.operationPolicies.isEmpty())
        require(profile.capabilities == setOf(Capability.CLIENT_TLS_AUTH))
        require(profile.certificateRules == CertificateFilterRules(setOf("RSA", "EC"), true))
        require(
            profile.clientAuthPolicy == ClientAuthPolicy(
                transitionMode = ClientAuthTransitionMode.IN_PLACE_FROM_SOURCE,
                requestOrigins = setOf(ExactOrigin.parse(AIREF_CLIENT_AUTH_ORIGIN)),
                sourceUrls = setOf(URI(MALAGA_CLIENT_AUTH_SOURCE_URL)),
                requestPath = MALAGA_CLIENT_AUTH_REQUEST_PATH,
                requestMethod = HttpMethod.POST,
                fixedQueryParameters = emptyMap(),
                requiredEphemeralQueryParameters = emptySet(),
                allowEmptyIssuerList = true,
                grantTtlSeconds = REVIEWED_CLIENT_AUTH_GRANT_TTL_SECONDS,
                requestPort = HTTPS_PORT,
            ),
        )
        require(
            profile.evidence.associate { it.url.toASCIIString() to it.reviewedOn } ==
                MALAGA_EVIDENCE_REVIEWS,
        )
    }

    private fun validateAirefProfile(profile: SiteProfile) {
        require(profile.profileVersion == AIREF_PROFILE_VERSION)
        require(profile.displayName == AIREF_DISPLAY_NAME)
        require(profile.compatibilityStatus == CompatibilityStatus.VERIFIED_CONTRACT)
        require(profile.activation == ProfileActivation.QA_ONLY)
        require(profile.startUrl.toASCIIString() == AIREF_START_URL)
        require(profile.initiatorOrigins == setOf(ExactOrigin.parse(AIREF_ORIGIN)))
        require(profile.redirectOrigins == setOf(ExactOrigin.parse(AIREF_CLAVE_ORIGIN)))
        require(profile.trustedBrowseOrigins.isEmpty())
        require(profile.endpoints.isEmpty())
        require(profile.capabilities == setOf(Capability.SIGN, Capability.LEGACY_SHA1, Capability.CLIENT_TLS_AUTH))
        require(profile.certificateRules == CertificateFilterRules(setOf("RSA"), true))
        require(
            profile.clientAuthPolicy == ClientAuthPolicy(
                transitionMode = ClientAuthTransitionMode.DIRECT_FROM_SOURCE,
                requestOrigins = setOf(ExactOrigin.parse(AIREF_CLIENT_AUTH_ORIGIN)),
                sourceUrls = setOf(URI(AIREF_CLIENT_AUTH_SOURCE_URL)),
                requestPath = AIREF_CLIENT_AUTH_REQUEST_PATH,
                fixedQueryParameters = emptyMap(),
                requiredEphemeralQueryParameters = emptySet(),
                allowEmptyIssuerList = true,
                grantTtlSeconds = REVIEWED_CLIENT_AUTH_GRANT_TTL_SECONDS,
                requestPort = HTTPS_PORT,
            ),
        )
        require(profile.evidence.map { it.url.toASCIIString() }.toSet() == AIREF_EVIDENCE_URLS)
        require(profile.evidence.all { it.reviewedOn == LocalDate.parse("2026-08-18") })
        require(profile.operationPolicies.keys == setOf(ProtocolOperation.SIGN))
        require(
            profile.operationPolicies.getValue(ProtocolOperation.SIGN) == OperationPolicy(
                operation = ProtocolOperation.SIGN,
                safeDescription = AIREF_SAFE_DESCRIPTION,
                inputAdapterId = ProtocolInputAdapterId("miniapplet-autoscript-v1"),
                callbackContractId = CallbackContractId("autoscript-sign-callback-v1"),
                capabilities = setOf(Capability.SIGN, Capability.LEGACY_SHA1),
                endpointId = null,
                algorithms = setOf(SignatureAlgorithm.SHA1_WITH_RSA),
                format = SignatureFormat.XADES,
                packaging = SignaturePackaging.ATTACHED,
                mode = null,
                fixedExtraProperties = emptyMap(),
                allowedExtraProperties = emptySet(),
            ),
        )
    }

    private fun validateCatalunyaProfile(profile: SiteProfile) {
        require(profile.profileVersion == CATALUNYA_PROFILE_VERSION)
        require(profile.displayName == CATALUNYA_DISPLAY_NAME)
        require(profile.compatibilityStatus == CompatibilityStatus.VERIFIED_CONTRACT)
        require(profile.activation == ProfileActivation.QA_ONLY)
        require(profile.startUrl.toASCIIString() == CATALUNYA_START_URL)
        require(profile.initiatorOrigins == setOf(ExactOrigin.parse(CATALUNYA_PUBLIC_ORIGIN)))
        require(
            profile.redirectOrigins == setOf(
                ExactOrigin.parse(CATALUNYA_OVT_ORIGIN),
                ExactOrigin.parse(CATALUNYA_VALID_ORIGIN),
                ExactOrigin.parse(CATALUNYA_CLAVE_ORIGIN),
            ),
        )
        require(profile.trustedBrowseOrigins.isEmpty())
        require(profile.endpoints.isEmpty())
        require(profile.operationPolicies.isEmpty())
        require(profile.capabilities == setOf(Capability.CLIENT_TLS_AUTH))
        require(profile.certificateRules == CertificateFilterRules(setOf("RSA"), true))
        require(
            profile.clientAuthPolicy == ClientAuthPolicy(
                transitionMode = ClientAuthTransitionMode.DIRECT_FROM_SOURCE,
                requestOrigins = setOf(ExactOrigin.parse(CATALUNYA_CLIENT_AUTH_ORIGIN)),
                sourceUrls = setOf(URI(CATALUNYA_CLIENT_AUTH_SOURCE_URL)),
                requestPath = CATALUNYA_CLIENT_AUTH_REQUEST_PATH,
                fixedQueryParameters = emptyMap(),
                requiredEphemeralQueryParameters = emptySet(),
                allowEmptyIssuerList = true,
                grantTtlSeconds = REVIEWED_CLIENT_AUTH_GRANT_TTL_SECONDS,
                requestPort = HTTPS_PORT,
            ),
        )
        require(profile.evidence.map { it.url.toASCIIString() }.toSet() == CATALUNYA_EVIDENCE_URLS)
        require(profile.evidence.all { it.reviewedOn == LocalDate.parse("2026-08-19") })
    }

    private fun validateCatalunyaSeuProfile(profile: SiteProfile) {
        require(profile.profileVersion == CATALUNYA_SEU_PROFILE_VERSION)
        require(profile.displayName == CATALUNYA_SEU_DISPLAY_NAME)
        require(profile.compatibilityStatus == CompatibilityStatus.VERIFIED_CONTRACT)
        require(profile.activation == ProfileActivation.QA_ONLY)
        require(profile.startUrl.toASCIIString() == CATALUNYA_SEU_START_URL)
        require(profile.initiatorOrigins == setOf(ExactOrigin.parse(CATALUNYA_SEU_ORIGIN)))
        require(
            profile.redirectOrigins == setOf(
                ExactOrigin.parse(CATALUNYA_TRAMITS_ORIGIN),
                ExactOrigin.parse(CATALUNYA_OVT_ORIGIN),
                ExactOrigin.parse(CATALUNYA_VALID_ORIGIN),
            ),
        )
        require(profile.trustedBrowseOrigins.isEmpty())
        require(profile.endpoints.isEmpty())
        require(profile.operationPolicies.isEmpty())
        require(profile.capabilities == setOf(Capability.CLIENT_TLS_AUTH))
        require(profile.certificateRules == CertificateFilterRules(setOf("RSA", "EC"), false))
        require(
            profile.clientAuthPolicy == ClientAuthPolicy(
                transitionMode = ClientAuthTransitionMode.DIRECT_FROM_SOURCE,
                requestOrigins = setOf(ExactOrigin.parse(CATALUNYA_CERT_ORIGIN)),
                sourceUrls = setOf(URI(CATALUNYA_VALID_SOURCE_URL)),
                requestPath = CATALUNYA_CERT_REQUEST_PATH,
                fixedQueryParameters = emptyMap(),
                requiredEphemeralQueryParameters = emptySet(),
                allowEmptyIssuerList = true,
                grantTtlSeconds = REVIEWED_CLIENT_AUTH_GRANT_TTL_SECONDS,
                requestPort = HTTPS_PORT,
            ),
        )
        require(profile.evidence.map { it.url.toASCIIString() }.toSet() == CATALUNYA_SEU_EVIDENCE_URLS)
        require(profile.evidence.all { it.reviewedOn == LocalDate.parse("2026-08-19") })
    }

    private fun validateEducationProfile(profile: SiteProfile) {
        require(profile.profileVersion == EDUCATION_PROFILE_VERSION)
        require(profile.displayName == EDUCATION_DISPLAY_NAME)
        require(profile.compatibilityStatus == CompatibilityStatus.VERIFIED_CONTRACT)
        require(profile.activation == ProfileActivation.QA_ONLY)
        require(profile.startUrl.toASCIIString() == EDUCATION_START_URL)
        require(profile.initiatorOrigins == setOf(ExactOrigin.parse(EDUCATION_ORIGIN)))
        require(
            profile.redirectOrigins == setOf(
                ExactOrigin.parse(EDUCATION_CLAVE_EDU_ORIGIN),
                ExactOrigin.parse(AIREF_CLAVE_ORIGIN),
            ),
        )
        require(profile.trustedBrowseOrigins.isEmpty())
        require(profile.endpoints.isEmpty())
        require(profile.operationPolicies.isEmpty())
        require(profile.capabilities == setOf(Capability.CLIENT_TLS_AUTH))
        require(profile.certificateRules == CertificateFilterRules(setOf("RSA"), true))
        require(
            profile.clientAuthPolicy == ClientAuthPolicy(
                transitionMode = ClientAuthTransitionMode.DIRECT_FROM_SOURCE,
                requestOrigins = setOf(ExactOrigin.parse(AIREF_CLIENT_AUTH_ORIGIN)),
                sourceUrls = setOf(URI(EDUCATION_CLIENT_AUTH_SOURCE_URL)),
                requestPath = AIREF_CLIENT_AUTH_REQUEST_PATH,
                fixedQueryParameters = emptyMap(),
                requiredEphemeralQueryParameters = emptySet(),
                allowEmptyIssuerList = true,
                grantTtlSeconds = REVIEWED_CLIENT_AUTH_GRANT_TTL_SECONDS,
                requestPort = HTTPS_PORT,
            ),
        )
        require(profile.evidence.map { it.url.toASCIIString() }.toSet() == EDUCATION_EVIDENCE_URLS)
        require(profile.evidence.all { it.reviewedOn == LocalDate.parse("2026-08-19") })
    }

    private fun validatePoliciaProfile(profile: SiteProfile) {
        require(profile.profileVersion == POLICIA_PROFILE_VERSION)
        require(profile.displayName == POLICIA_DISPLAY_NAME)
        require(profile.compatibilityStatus == CompatibilityStatus.VERIFIED_CONTRACT)
        require(profile.activation == ProfileActivation.QA_ONLY)
        require(profile.startUrl.toASCIIString() == POLICIA_START_URL)
        require(profile.initiatorOrigins == setOf(ExactOrigin.parse(POLICIA_ORIGIN)))
        require(profile.redirectOrigins.isEmpty())
        require(profile.trustedBrowseOrigins.isEmpty())
        require(profile.endpoints.isEmpty())
        require(profile.capabilities == setOf(Capability.SIGN, Capability.LEGACY_SHA1))
        require(profile.clientAuthPolicy == null)
        require(profile.certificateRules == CertificateFilterRules(setOf("RSA"), false))
        require(profile.evidence.isNotEmpty())
        require(profile.operationPolicies.keys == setOf(ProtocolOperation.SIGN))
        require(
            profile.operationPolicies.getValue(ProtocolOperation.SIGN) == OperationPolicy(
                operation = ProtocolOperation.SIGN,
                safeDescription = POLICIA_SAFE_DESCRIPTION,
                inputAdapterId = ProtocolInputAdapterId("miniapplet-autoscript-v1"),
                callbackContractId = CallbackContractId("autoscript-sign-callback-v1"),
                capabilities = setOf(Capability.SIGN, Capability.LEGACY_SHA1),
                endpointId = null,
                algorithms = setOf(SignatureAlgorithm.SHA1_WITH_RSA),
                format = SignatureFormat.XADES,
                packaging = SignaturePackaging.DETACHED,
                mode = null,
                fixedExtraProperties = POLICIA_FIXED_EXTRA_PROPERTIES,
                allowedExtraProperties = emptySet(),
            ),
        )
    }

    private fun validateLaPalmaProfile(profile: SiteProfile) {
        require(profile.profileVersion == LA_PALMA_PROFILE_VERSION)
        require(profile.displayName == LA_PALMA_DISPLAY_NAME)
        require(profile.compatibilityStatus == CompatibilityStatus.VERIFIED_CONTRACT)
        require(profile.activation == ProfileActivation.QA_ONLY)
        require(profile.startUrl.toASCIIString() == LA_PALMA_START_URL)
        require(profile.initiatorOrigins == setOf(ExactOrigin.parse(LA_PALMA_ORIGIN)))
        require(profile.redirectOrigins.isEmpty())
        require(profile.trustedBrowseOrigins.isEmpty())
        require(profile.endpoints.isEmpty())
        require(profile.capabilities == setOf(Capability.SIGN))
        require(profile.clientAuthPolicy == null)
        require(profile.certificateRules == CertificateFilterRules(setOf("RSA"), true))
        require(profile.evidence.isNotEmpty())
        require(profile.operationPolicies.keys == setOf(ProtocolOperation.SIGN))
        require(
            profile.operationPolicies.getValue(ProtocolOperation.SIGN) == OperationPolicy(
                operation = ProtocolOperation.SIGN,
                safeDescription = LA_PALMA_SAFE_DESCRIPTION,
                inputAdapterId = ProtocolInputAdapterId("la-palma-batch-autoscript-v1"),
                callbackContractId = CallbackContractId("la-palma-batch-result-v1"),
                capabilities = setOf(Capability.SIGN),
                endpointId = null,
                algorithms = setOf(SignatureAlgorithm.SHA256_WITH_RSA),
                format = SignatureFormat.CADES,
                packaging = SignaturePackaging.DETACHED,
                mode = null,
                fixedExtraProperties = emptyMap(),
                allowedExtraProperties = emptySet(),
            ),
        )
    }

    private fun validateBurgosProfile(profile: SiteProfile) {
        require(profile.profileVersion == BURGOS_PROFILE_VERSION)
        require(profile.displayName == BURGOS_DISPLAY_NAME)
        require(profile.compatibilityStatus == CompatibilityStatus.VERIFIED_CONTRACT)
        require(profile.activation == ProfileActivation.QA_ONLY)
        require(profile.startUrl.toASCIIString() == BURGOS_START_URL)
        require(profile.initiatorOrigins == setOf(ExactOrigin.parse(BURGOS_ORIGIN)))
        require(profile.redirectOrigins.isEmpty())
        require(profile.trustedBrowseOrigins.isEmpty())
        require(profile.endpoints.isEmpty())
        require(profile.capabilities == setOf(Capability.SIGN))
        require(profile.clientAuthPolicy == null)
        require(profile.certificateRules == CertificateFilterRules(setOf("RSA"), true))
        require(profile.evidence.isNotEmpty())
        require(profile.operationPolicies.keys == setOf(ProtocolOperation.SIGN))
        require(
            profile.operationPolicies.getValue(ProtocolOperation.SIGN) == OperationPolicy(
                operation = ProtocolOperation.SIGN,
                safeDescription = BURGOS_SAFE_DESCRIPTION,
                inputAdapterId = ProtocolInputAdapterId("burgos-batch-autoscript-v1"),
                callbackContractId = CallbackContractId("burgos-batch-result-v1"),
                capabilities = setOf(Capability.SIGN),
                endpointId = null,
                algorithms = setOf(SignatureAlgorithm.SHA256_WITH_RSA),
                format = SignatureFormat.CADES,
                packaging = SignaturePackaging.DETACHED,
                mode = null,
                fixedExtraProperties = emptyMap(),
                allowedExtraProperties = emptySet(),
            ),
        )
    }

    private fun validateHuescaProfile(profile: SiteProfile) {
        require(profile.profileVersion == HUESCA_PROFILE_VERSION)
        require(profile.displayName == HUESCA_DISPLAY_NAME)
        require(profile.compatibilityStatus == CompatibilityStatus.VERIFIED_CONTRACT)
        require(profile.activation == ProfileActivation.QA_ONLY)
        require(profile.startUrl.toASCIIString() == HUESCA_START_URL)
        require(profile.initiatorOrigins == setOf(ExactOrigin.parse(HUESCA_ORIGIN)))
        require(profile.redirectOrigins.isEmpty())
        require(profile.trustedBrowseOrigins.isEmpty())
        require(profile.endpoints.isEmpty())
        require(profile.capabilities == setOf(Capability.SIGN))
        require(profile.clientAuthPolicy == null)
        require(profile.certificateRules == CertificateFilterRules(setOf("RSA"), true))
        require(profile.evidence.isNotEmpty())
        require(profile.operationPolicies.keys == setOf(ProtocolOperation.SIGN))
        require(
            profile.operationPolicies.getValue(ProtocolOperation.SIGN) == OperationPolicy(
                operation = ProtocolOperation.SIGN,
                safeDescription = HUESCA_SAFE_DESCRIPTION,
                inputAdapterId = ProtocolInputAdapterId("huesca-batch-autoscript-v1"),
                callbackContractId = CallbackContractId("huesca-batch-result-v1"),
                capabilities = setOf(Capability.SIGN),
                endpointId = null,
                algorithms = setOf(SignatureAlgorithm.SHA256_WITH_RSA),
                format = SignatureFormat.CADES,
                packaging = SignaturePackaging.DETACHED,
                mode = null,
                fixedExtraProperties = emptyMap(),
                allowedExtraProperties = emptySet(),
            ),
        )
    }

    private fun validateLugoProfile(profile: SiteProfile) {
        require(profile.profileVersion == LUGO_PROFILE_VERSION)
        require(profile.displayName == LUGO_DISPLAY_NAME)
        require(profile.compatibilityStatus == CompatibilityStatus.VERIFIED_CONTRACT)
        require(profile.activation == ProfileActivation.QA_ONLY)
        require(profile.startUrl.toASCIIString() == LUGO_START_URL)
        require(profile.initiatorOrigins == setOf(ExactOrigin.parse(LUGO_ORIGIN)))
        require(profile.redirectOrigins.isEmpty())
        require(profile.trustedBrowseOrigins.isEmpty())
        require(profile.endpoints.isEmpty())
        require(profile.capabilities == setOf(Capability.SIGN))
        require(profile.clientAuthPolicy == null)
        require(profile.certificateRules == CertificateFilterRules(setOf("RSA"), true))
        require(profile.evidence.isNotEmpty())
        require(profile.operationPolicies.keys == setOf(ProtocolOperation.SIGN))
        require(
            profile.operationPolicies.getValue(ProtocolOperation.SIGN) == OperationPolicy(
                operation = ProtocolOperation.SIGN,
                safeDescription = LUGO_SAFE_DESCRIPTION,
                inputAdapterId = ProtocolInputAdapterId("lugo-clientsigner-xml-batch-v1"),
                callbackContractId = CallbackContractId("lugo-clientsigner-batch-result-v1"),
                capabilities = setOf(Capability.SIGN),
                endpointId = null,
                algorithms = setOf(SignatureAlgorithm.SHA256_WITH_RSA),
                format = SignatureFormat.CADES,
                packaging = SignaturePackaging.DETACHED,
                mode = SignatureMode.EXPLICIT,
                fixedExtraProperties = mapOf("precalculatedHashAlgorithm" to "SHA-256"),
                allowedExtraProperties = emptySet(),
            ),
        )
    }


    private fun validateCaibProfile(profile: SiteProfile) {
        require(profile.profileVersion == CAIB_PROFILE_VERSION)
        require(profile.displayName == CAIB_DISPLAY_NAME)
        require(profile.compatibilityStatus == CompatibilityStatus.VERIFIED_CONTRACT)
        require(profile.activation == ProfileActivation.QA_ONLY)
        require(profile.startUrl.toASCIIString() == CAIB_START_URL)
        require(
            profile.initiatorOrigins == setOf(
                ExactOrigin.parse(CAIB_PUBLIC_ORIGIN), ExactOrigin.parse(CAIB_SIGNING_ORIGIN),
            ),
        )
        require(profile.redirectOrigins.isEmpty())
        require(profile.trustedBrowseOrigins.isEmpty())
        require(profile.endpoints.isEmpty())
        require(profile.capabilities == setOf(Capability.SIGN))
        require(profile.clientAuthPolicy == null)
        require(profile.certificateRules == CertificateFilterRules(setOf("RSA"), false))
        require(profile.evidence.map { it.url.toASCIIString() }.toSet() == CAIB_EVIDENCE_URLS)
        require(profile.evidence.all { it.reviewedOn == LocalDate.parse("2026-08-18") })
        require(profile.operationPolicies.keys == setOf(ProtocolOperation.SIGN))
        require(
            profile.operationPolicies.getValue(ProtocolOperation.SIGN) == OperationPolicy(
                operation = ProtocolOperation.SIGN,
                safeDescription = CAIB_SAFE_DESCRIPTION,
                inputAdapterId = ProtocolInputAdapterId("caib-portafib-batch-v1"),
                callbackContractId = CallbackContractId("caib-portafib-batch-result-v1"),
                capabilities = setOf(Capability.SIGN),
                endpointId = null,
                algorithms = setOf(SignatureAlgorithm.SHA256_WITH_RSA),
                format = SignatureFormat.PADES,
                packaging = SignaturePackaging.ATTACHED,
                mode = SignatureMode.IMPLICIT,
                fixedExtraProperties = emptyMap(),
                allowedExtraProperties = emptySet(),
            ),
        )
    }

    private fun validateLeonProfile(profile: SiteProfile) {
        require(profile.profileVersion == LEON_PROFILE_VERSION)
        require(profile.displayName == LEON_DISPLAY_NAME)
        require(profile.compatibilityStatus == CompatibilityStatus.VERIFIED_CONTRACT)
        require(profile.activation == ProfileActivation.QA_ONLY)
        require(profile.startUrl.toASCIIString() == LEON_START_URL)
        require(profile.initiatorOrigins == setOf(ExactOrigin.parse(LEON_ORIGIN)))
        require(profile.redirectOrigins.isEmpty())
        require(profile.trustedBrowseOrigins.isEmpty())
        require(profile.endpoints.isEmpty())
        require(profile.operationPolicies.isEmpty())
        require(profile.capabilities == setOf(Capability.CLIENT_TLS_AUTH))
        require(profile.certificateRules == CertificateFilterRules(setOf("RSA", "EC"), true))
        require(
            profile.clientAuthPolicy == ClientAuthPolicy(
                transitionMode = ClientAuthTransitionMode.DIRECT_FROM_SOURCE,
                requestOrigins = setOf(ExactOrigin.parse(LEON_CLIENT_AUTH_ORIGIN)),
                sourceUrls = setOf(URI(LEON_SOURCE_URL)),
                requestPath = "/",
                fixedQueryParameters = linkedMapOf("idioma" to "es", "entidad" to "24000"),
                requiredEphemeralQueryParameters = setOf("idtoken"),
                allowEmptyIssuerList = true,
                grantTtlSeconds = REVIEWED_CLIENT_AUTH_GRANT_TTL_SECONDS,
                requestPort = HTTPS_PORT,
                sourceFixedQueryParameters = linkedMapOf("idioma" to "es"),
                sourceRequiredEphemeralQueryParameters = setOf("idtoken"),
                linkedEphemeralQueryParameters = setOf("idtoken"),
            ),
        )
        require(profile.evidence.map { it.url.toASCIIString() }.toSet() == LEON_EVIDENCE_URLS)
        require(profile.evidence.all { it.reviewedOn == LocalDate.parse("2026-08-16") })
    }

    private fun validateAlbaceteProfile(profile: SiteProfile) {
        require(profile.profileVersion == ALBACETE_PROFILE_VERSION)
        require(profile.displayName == ALBACETE_DISPLAY_NAME)
        require(profile.compatibilityStatus == CompatibilityStatus.VERIFIED_CONTRACT)
        require(profile.activation == ProfileActivation.QA_ONLY)
        require(profile.startUrl.toASCIIString() == ALBACETE_START_URL)
        require(profile.initiatorOrigins == setOf(ExactOrigin.parse(ALBACETE_ORIGIN)))
        require(profile.redirectOrigins.isEmpty())
        require(profile.trustedBrowseOrigins.isEmpty())
        require(profile.endpoints.isEmpty())
        require(profile.operationPolicies.isEmpty())
        require(profile.capabilities == setOf(Capability.CLIENT_TLS_AUTH))
        require(profile.certificateRules == CertificateFilterRules(setOf("RSA", "EC"), true))
        require(
            profile.clientAuthPolicy == ClientAuthPolicy(
                transitionMode = ClientAuthTransitionMode.DIRECT_FROM_SOURCE,
                requestOrigins = setOf(ExactOrigin.parse(SEDIPUALBA_CLIENT_AUTH_ORIGIN)),
                sourceUrls = setOf(URI(ALBACETE_SOURCE_URL)),
                requestPath = "/",
                fixedQueryParameters = linkedMapOf("idioma" to "es", "entidad" to "02000"),
                requiredEphemeralQueryParameters = setOf("idtoken"),
                allowEmptyIssuerList = true,
                grantTtlSeconds = REVIEWED_CLIENT_AUTH_GRANT_TTL_SECONDS,
                requestPort = HTTPS_PORT,
                sourceFixedQueryParameters = linkedMapOf("idioma" to "es"),
                sourceRequiredEphemeralQueryParameters = setOf("idtoken"),
                linkedEphemeralQueryParameters = setOf("idtoken"),
            ),
        )
        require(profile.evidence.map { it.url.toASCIIString() }.toSet() == ALBACETE_EVIDENCE_URLS)
        require(profile.evidence.all { it.reviewedOn == LocalDate.parse("2026-08-18") })
    }

    private fun validateMallorcaProfile(profile: SiteProfile) {
        require(profile.profileVersion == MALLORCA_PROFILE_VERSION)
        require(profile.displayName == MALLORCA_DISPLAY_NAME)
        require(profile.compatibilityStatus == CompatibilityStatus.VERIFIED_CONTRACT)
        require(profile.activation == ProfileActivation.QA_ONLY)
        require(profile.startUrl.toASCIIString() == MALLORCA_START_URL)
        require(profile.initiatorOrigins == setOf(ExactOrigin.parse(MALLORCA_ORIGIN)))
        require(profile.redirectOrigins.isEmpty())
        require(profile.trustedBrowseOrigins.isEmpty())
        require(profile.endpoints.isEmpty())
        require(profile.operationPolicies.isEmpty())
        require(profile.capabilities == setOf(Capability.CLIENT_TLS_AUTH))
        require(profile.certificateRules == CertificateFilterRules(setOf("RSA", "EC"), true))
        require(
            profile.clientAuthPolicy == ClientAuthPolicy(
                transitionMode = ClientAuthTransitionMode.DIRECT_FROM_SOURCE,
                requestOrigins = setOf(ExactOrigin.parse(SEDIPUALBA_CLIENT_AUTH_ORIGIN)),
                sourceUrls = setOf(URI(MALLORCA_SOURCE_URL)),
                requestPath = "/",
                fixedQueryParameters = linkedMapOf("idioma" to "ca", "entidad" to "07700"),
                requiredEphemeralQueryParameters = setOf("idtoken"),
                allowEmptyIssuerList = true,
                grantTtlSeconds = REVIEWED_CLIENT_AUTH_GRANT_TTL_SECONDS,
                requestPort = HTTPS_PORT,
                sourceFixedQueryParameters = linkedMapOf("idioma" to "ca"),
                sourceRequiredEphemeralQueryParameters = setOf("idtoken"),
                linkedEphemeralQueryParameters = setOf("idtoken"),
            ),
        )
        require(profile.evidence.map { it.url.toASCIIString() }.toSet() == MALLORCA_EVIDENCE_URLS)
        require(profile.evidence.all { it.reviewedOn == LocalDate.parse("2026-08-18") })
    }

    private fun validateCuencaProfile(profile: SiteProfile) {
        require(profile.profileVersion == CUENCA_PROFILE_VERSION)
        require(profile.displayName == CUENCA_DISPLAY_NAME)
        require(profile.compatibilityStatus == CompatibilityStatus.VERIFIED_CONTRACT)
        require(profile.activation == ProfileActivation.QA_ONLY)
        require(profile.startUrl.toASCIIString() == CUENCA_START_URL)
        require(profile.initiatorOrigins == setOf(ExactOrigin.parse(CUENCA_ORIGIN)))
        require(profile.redirectOrigins.isEmpty())
        require(profile.trustedBrowseOrigins.isEmpty())
        require(profile.endpoints.isEmpty())
        require(profile.operationPolicies.isEmpty())
        require(profile.capabilities == setOf(Capability.CLIENT_TLS_AUTH))
        require(profile.certificateRules == CertificateFilterRules(setOf("RSA", "EC"), true))
        require(
            profile.clientAuthPolicy == ClientAuthPolicy(
                transitionMode = ClientAuthTransitionMode.DIRECT_FROM_SOURCE,
                requestOrigins = setOf(ExactOrigin.parse(SEDIPUALBA_CLIENT_AUTH_ORIGIN)),
                sourceUrls = setOf(URI(CUENCA_SOURCE_URL)),
                requestPath = "/",
                fixedQueryParameters = linkedMapOf("idioma" to "es", "entidad" to "16000"),
                requiredEphemeralQueryParameters = setOf("idtoken"),
                allowEmptyIssuerList = true,
                grantTtlSeconds = REVIEWED_CLIENT_AUTH_GRANT_TTL_SECONDS,
                requestPort = HTTPS_PORT,
                sourceFixedQueryParameters = linkedMapOf("idioma" to "es"),
                sourceRequiredEphemeralQueryParameters = setOf("idtoken"),
                linkedEphemeralQueryParameters = setOf("idtoken"),
            ),
        )
        require(profile.evidence.map { it.url.toASCIIString() }.toSet() == CUENCA_EVIDENCE_URLS)
        require(profile.evidence.all { it.reviewedOn == LocalDate.parse("2026-08-21") })
    }

    private fun validateLaRiojaProfile(profile: SiteProfile) {
        require(profile.profileVersion == LA_RIOJA_PROFILE_VERSION)
        require(profile.displayName == LA_RIOJA_DISPLAY_NAME)
        require(profile.compatibilityStatus == CompatibilityStatus.VERIFIED_CONTRACT)
        require(profile.activation == ProfileActivation.QA_ONLY)
        require(profile.startUrl.toASCIIString() == LA_RIOJA_START_URL)
        require(profile.initiatorOrigins == setOf(ExactOrigin.parse(LA_RIOJA_ORIGIN)))
        require(profile.redirectOrigins.isEmpty())
        require(profile.trustedBrowseOrigins.isEmpty())
        require(profile.endpoints.isEmpty())
        require(profile.operationPolicies.isEmpty())
        require(profile.capabilities == setOf(Capability.CLIENT_TLS_AUTH))
        require(profile.certificateRules == CertificateFilterRules(setOf("RSA", "EC"), false))
        require(profile.clientAuthPolicy == LaRiojaClientAuthContract.expectedPolicy())
        require(profile.evidence.map { it.url.toASCIIString() }.toSet() == LA_RIOJA_EVIDENCE_URLS)
        require(profile.evidence.all { it.reviewedOn == LocalDate.parse("2026-08-18") })
    }

    private fun validateGvaProfile(profile: SiteProfile) {
        require(profile.profileVersion == GVA_PROFILE_VERSION)
        require(profile.displayName == GVA_DISPLAY_NAME)
        require(profile.compatibilityStatus == CompatibilityStatus.VERIFIED_CONTRACT)
        require(profile.activation == ProfileActivation.QA_ONLY)
        require(profile.startUrl.toASCIIString() == GVA_START_URL)
        require(
            profile.initiatorOrigins == setOf(
                ExactOrigin.parse(GVA_TRAMITA_ORIGIN),
                ExactOrigin.parse(GVA_PTT_CLAVE_ORIGIN),
            ),
        )
        require(profile.redirectOrigins.isEmpty())
        require(profile.trustedBrowseOrigins.isEmpty())
        require(profile.endpoints.isEmpty())
        require(profile.operationPolicies.isEmpty())
        require(profile.capabilities == setOf(Capability.CLIENT_TLS_AUTH))
        require(profile.certificateRules == CertificateFilterRules(setOf("RSA", "EC"), true))
        require(
            profile.clientAuthPolicy == ClientAuthPolicy(
                transitionMode = ClientAuthTransitionMode.DIRECT_FROM_SOURCE,
                requestOrigins = setOf(ExactOrigin.parse(GVA_CLIENT_AUTH_ORIGIN)),
                sourceUrls = setOf(URI(GVA_SOURCE_URL)),
                requestPath = GVA_CLIENT_AUTH_PATH,
                fixedQueryParameters = linkedMapOf("idioma" to "es"),
                requiredEphemeralQueryParameters = setOf("idSesion"),
                allowEmptyIssuerList = true,
                grantTtlSeconds = REVIEWED_CLIENT_AUTH_GRANT_TTL_SECONDS,
                requestPort = HTTPS_PORT,
                sourceFixedQueryParameters = emptyMap(),
                sourceRequiredEphemeralQueryParameters = setOf("idSesion"),
                linkedEphemeralQueryParameters = setOf("idSesion"),
            ),
        )
        require(profile.evidence.map { it.url.toASCIIString() }.toSet() == GVA_EVIDENCE_URLS)
        require(profile.evidence.all { it.reviewedOn == LocalDate.parse("2026-08-18") })
    }

    private fun validateEuskadiProfile(profile: SiteProfile) {
        require(profile.profileVersion == EUSKADI_PROFILE_VERSION)
        require(profile.displayName == EUSKADI_DISPLAY_NAME)
        require(profile.compatibilityStatus == CompatibilityStatus.VERIFIED_CONTRACT)
        require(profile.activation == ProfileActivation.QA_ONLY)
        require(profile.startUrl.toASCIIString() == EUSKADI_START_URL)
        require(profile.initiatorOrigins == setOf(ExactOrigin.parse(EUSKADI_ORIGIN)))
        require(profile.redirectOrigins == setOf(ExactOrigin.parse(EUSKADI_IZENPE_ORIGIN)))
        require(profile.trustedBrowseOrigins.isEmpty())
        require(profile.endpoints.isEmpty())
        require(profile.operationPolicies.isEmpty())
        require(profile.capabilities == setOf(Capability.CLIENT_TLS_AUTH))
        require(profile.certificateRules == CertificateFilterRules(setOf("RSA", "EC"), true))
        require(
            profile.clientAuthPolicy == ClientAuthPolicy(
                transitionMode = ClientAuthTransitionMode.DIRECT_FROM_SOURCE,
                requestOrigins = setOf(ExactOrigin.parse(EUSKADI_CLIENT_AUTH_ORIGIN)),
                sourceUrls = setOf(URI(EUSKADI_SOURCE_URL)),
                requestPath = EUSKADI_CLIENT_AUTH_PATH,
                fixedQueryParameters = emptyMap(),
                requiredEphemeralQueryParameters = emptySet(),
                allowEmptyIssuerList = false,
                grantTtlSeconds = REVIEWED_CLIENT_AUTH_GRANT_TTL_SECONDS,
                requestPort = HTTPS_PORT,
                sourceFixedQueryParameters = emptyMap(),
                sourceRequiredEphemeralQueryParameters = emptySet(),
                linkedEphemeralQueryParameters = emptySet(),
            ),
        )
        require(profile.evidence.map { it.url.toASCIIString() }.toSet() == EUSKADI_EVIDENCE_URLS)
        require(profile.evidence.all { it.reviewedOn == LocalDate.parse("2026-08-19") })
    }

    private fun validateJaenProfile(profile: SiteProfile) {
        require(profile.profileVersion == JAEN_PROFILE_VERSION)
        require(profile.displayName == JAEN_DISPLAY_NAME)
        require(profile.compatibilityStatus == CompatibilityStatus.VERIFIED_CONTRACT)
        require(profile.activation == ProfileActivation.QA_ONLY)
        require(profile.startUrl.toASCIIString() == JAEN_START_URL)
        require(profile.initiatorOrigins == setOf(ExactOrigin.parse(JAEN_ORIGIN)))
        require(profile.redirectOrigins.isEmpty())
        require(profile.trustedBrowseOrigins.isEmpty())
        require(profile.endpoints.isEmpty())
        require(profile.operationPolicies.isEmpty())
        require(profile.capabilities == setOf(Capability.CLIENT_TLS_AUTH))
        require(profile.certificateRules == CertificateFilterRules(setOf("RSA", "EC"), true))
        require(
            profile.clientAuthPolicy == ClientAuthPolicy(
                transitionMode = ClientAuthTransitionMode.REDIRECT_AFTER_SOURCE,
                requestOrigins = setOf(ExactOrigin.parse(JAEN_CLIENT_AUTH_ORIGIN)),
                sourceUrls = setOf(URI(JAEN_SOURCE_URL)),
                requestPath = "/",
                fixedQueryParameters = linkedMapOf("back" to JAEN_SOURCE_URL),
                requiredEphemeralQueryParameters = setOf("key"),
                allowEmptyIssuerList = false,
                grantTtlSeconds = REVIEWED_CLIENT_AUTH_GRANT_TTL_SECONDS,
                requestPort = HTTPS_PORT,
                sourceFixedQueryParameters = emptyMap(),
                sourceRequiredEphemeralQueryParameters = emptySet(),
                linkedEphemeralQueryParameters = emptySet(),
            ),
        )
        require(profile.evidence.map { it.url.toASCIIString() }.toSet() == JAEN_EVIDENCE_URLS)
        require(profile.evidence.all { it.reviewedOn == LocalDate.parse("2026-08-19") })
    }

    private fun validateSevillaAtseProfile(profile: SiteProfile) {
        require(profile.profileVersion == SEVILLA_ATSE_PROFILE_VERSION)
        require(profile.displayName == SEVILLA_ATSE_DISPLAY_NAME)
        require(profile.compatibilityStatus == CompatibilityStatus.VERIFIED_CONTRACT)
        require(profile.activation == ProfileActivation.QA_ONLY)
        require(profile.startUrl.toASCIIString() == SEVILLA_ATSE_START_URL)
        require(profile.initiatorOrigins == setOf(ExactOrigin.parse(SEVILLA_ATSE_ORIGIN)))
        require(profile.redirectOrigins.isEmpty())
        require(profile.trustedBrowseOrigins.isEmpty())
        require(profile.endpoints.isEmpty())
        require(profile.capabilities == setOf(Capability.SIGN, Capability.LEGACY_SHA1))
        require(profile.clientAuthPolicy == null)
        require(profile.certificateRules == CertificateFilterRules(setOf("RSA"), true))
        require(profile.evidence.isNotEmpty())
        require(profile.operationPolicies.keys == setOf(ProtocolOperation.SIGN))
        require(
            profile.operationPolicies.getValue(ProtocolOperation.SIGN) == OperationPolicy(
                operation = ProtocolOperation.SIGN,
                safeDescription = SEVILLA_ATSE_SAFE_DESCRIPTION,
                inputAdapterId = ProtocolInputAdapterId("miniapplet-autoscript-v1"),
                callbackContractId = CallbackContractId("autoscript-sign-callback-v1"),
                capabilities = setOf(Capability.SIGN, Capability.LEGACY_SHA1),
                endpointId = null,
                algorithms = setOf(SignatureAlgorithm.SHA1_WITH_RSA),
                format = SignatureFormat.XADES,
                packaging = SignaturePackaging.ATTACHED,
                mode = null,
                fixedExtraProperties = emptyMap(),
                allowedExtraProperties = emptySet(),
            ),
        )
    }

    private fun validateTransportesProfile(profile: SiteProfile) {
        require(profile.profileVersion == TRANSPORTES_PROFILE_VERSION)
        require(profile.displayName == TRANSPORTES_DISPLAY_NAME)
        require(profile.compatibilityStatus == CompatibilityStatus.VERIFIED_CONTRACT)
        require(profile.activation == ProfileActivation.QA_ONLY)
        require(profile.startUrl.toASCIIString() == TRANSPORTES_START_URL)
        require(profile.initiatorOrigins == setOf(ExactOrigin.parse(TRANSPORTES_ORIGIN)))
        require(profile.redirectOrigins.isEmpty())
        require(profile.trustedBrowseOrigins.isEmpty())
        require(profile.endpoints.isEmpty())
        require(profile.capabilities == setOf(Capability.SIGN, Capability.LEGACY_SHA1))
        require(profile.clientAuthPolicy == null)
        require(profile.certificateRules == CertificateFilterRules(setOf("RSA"), true))
        require(profile.evidence.map { it.url.toASCIIString() }.toSet() == TRANSPORTES_EVIDENCE_URLS)
        require(profile.evidence.all { it.reviewedOn == LocalDate.parse("2026-08-17") })
        require(profile.operationPolicies.keys == setOf(ProtocolOperation.SIGN))
        require(
            profile.operationPolicies.getValue(ProtocolOperation.SIGN) == OperationPolicy(
                operation = ProtocolOperation.SIGN,
                safeDescription = TRANSPORTES_SAFE_DESCRIPTION,
                inputAdapterId = ProtocolInputAdapterId("miniapplet-autoscript-v1"),
                callbackContractId = CallbackContractId("autoscript-sign-callback-v1"),
                capabilities = setOf(Capability.SIGN, Capability.LEGACY_SHA1),
                endpointId = null,
                algorithms = setOf(SignatureAlgorithm.SHA1_WITH_RSA),
                format = SignatureFormat.XADES,
                packaging = SignaturePackaging.ATTACHED,
                mode = null,
                fixedExtraProperties = TRANSPORTES_FIXED_EXTRA_PROPERTIES,
                allowedExtraProperties = emptySet(),
            ),
        )
    }

    private fun validateCdtiProfile(profile: SiteProfile) {
        require(profile.profileVersion == CDTI_PROFILE_VERSION)
        require(profile.displayName == CDTI_DISPLAY_NAME)
        require(profile.compatibilityStatus == CompatibilityStatus.VERIFIED_CONTRACT)
        require(profile.activation == ProfileActivation.QA_ONLY)
        require(profile.startUrl.toASCIIString() == CDTI_START_URL)
        require(profile.initiatorOrigins == setOf(ExactOrigin.parse(CDTI_ORIGIN)))
        require(profile.redirectOrigins.isEmpty())
        require(profile.trustedBrowseOrigins.isEmpty())
        require(profile.endpoints.isEmpty())
        require(profile.capabilities == setOf(Capability.SIGN))
        require(profile.clientAuthPolicy == null)
        require(profile.certificateRules == CertificateFilterRules(setOf("RSA"), true))
        require(profile.evidence.isNotEmpty())
        require(profile.operationPolicies.keys == setOf(ProtocolOperation.SIGN))
        require(
            profile.operationPolicies.getValue(ProtocolOperation.SIGN) == OperationPolicy(
                operation = ProtocolOperation.SIGN,
                safeDescription = CDTI_SAFE_DESCRIPTION,
                inputAdapterId = ProtocolInputAdapterId("miniapplet-autoscript-v1"),
                callbackContractId = CallbackContractId("autoscript-sign-callback-v1"),
                capabilities = setOf(Capability.SIGN),
                endpointId = null,
                algorithms = setOf(SignatureAlgorithm.SHA512_WITH_RSA),
                format = SignatureFormat.XADES,
                packaging = SignaturePackaging.ATTACHED,
                mode = null,
                fixedExtraProperties = CDTI_FIXED_EXTRA_PROPERTIES,
                allowedExtraProperties = emptySet(),
            ),
        )
    }

    private fun validateJccmRegistroProfile(profile: SiteProfile) {
        require(profile.profileVersion == JCCM_REGISTRO_PROFILE_VERSION)
        require(profile.displayName == JCCM_REGISTRO_DISPLAY_NAME)
        require(profile.compatibilityStatus == CompatibilityStatus.VERIFIED_CONTRACT)
        require(profile.activation == ProfileActivation.QA_ONLY)
        require(profile.startUrl.toASCIIString() == JCCM_REGISTRO_START_URL)
        require(profile.initiatorOrigins == setOf(ExactOrigin.parse(JCCM_REGISTRO_ORIGIN)))
        require(profile.redirectOrigins == JCCM_REGISTRO_REDIRECT_ORIGINS)
        require(profile.trustedBrowseOrigins.isEmpty())
        require(profile.endpoints.isEmpty())
        require(profile.capabilities == setOf(Capability.SIGN, Capability.CLIENT_TLS_AUTH))
        require(profile.certificateRules == CertificateFilterRules(setOf("RSA"), false))
        require(profile.evidence.isNotEmpty())
        val clientAuth = requireNotNull(profile.clientAuthPolicy)
        require(clientAuth.transitionMode == ClientAuthTransitionMode.DIRECT_FROM_SOURCE)
        require(clientAuth.requestOrigins == setOf(ExactOrigin.parse(AIREF_CLIENT_AUTH_ORIGIN)))
        require(clientAuth.sourceUrls == setOf(URI(JCCM_REGISTRO_CLIENT_AUTH_SOURCE_URL)))
        require(clientAuth.requestPath == JCCM_REGISTRO_CLIENT_AUTH_PATH)
        require(clientAuth.fixedQueryParameters.isEmpty())
        require(clientAuth.requiredEphemeralQueryParameters.isEmpty())
        require(clientAuth.sourceFixedQueryParameters.isEmpty())
        require(clientAuth.sourceRequiredEphemeralQueryParameters.isEmpty())
        require(clientAuth.linkedEphemeralQueryParameters.isEmpty())
        require(clientAuth.linkedEphemeralQueryParameterMappings.isEmpty())
        require(clientAuth.allowEmptyIssuerList)
        require(clientAuth.grantTtlSeconds == REVIEWED_CLIENT_AUTH_GRANT_TTL_SECONDS)
        require(clientAuth.requestPort == HTTPS_PORT)
        require(profile.operationPolicies.keys == setOf(ProtocolOperation.SIGN))
        require(
            profile.operationPolicies.getValue(ProtocolOperation.SIGN) == OperationPolicy(
                operation = ProtocolOperation.SIGN,
                safeDescription = JCCM_REGISTRO_SAFE_DESCRIPTION,
                inputAdapterId = ProtocolInputAdapterId("miniapplet-autoscript-v1"),
                callbackContractId = CallbackContractId("miniapplet-sign-callback-v1"),
                capabilities = setOf(Capability.SIGN),
                endpointId = null,
                algorithms = setOf(SignatureAlgorithm.SHA512_WITH_RSA),
                format = SignatureFormat.XADES,
                packaging = SignaturePackaging.DETACHED,
                mode = SignatureMode.IMPLICIT,
                fixedExtraProperties = JCCM_REGISTRO_FIXED_EXTRA_PROPERTIES,
                allowedExtraProperties = emptySet(),
            ),
        )
    }

    private fun validateJccmProfile(profile: SiteProfile) {
        require(profile.profileVersion == JCCM_PROFILE_VERSION)
        require(profile.displayName == JCCM_DISPLAY_NAME)
        require(profile.compatibilityStatus == CompatibilityStatus.VERIFIED_CONTRACT)
        require(profile.activation == ProfileActivation.QA_ONLY)
        require(profile.startUrl.toASCIIString() == JCCM_START_URL)
        require(profile.initiatorOrigins == setOf(ExactOrigin.parse(JCCM_ORIGIN)))
        require(profile.redirectOrigins.isEmpty())
        require(profile.trustedBrowseOrigins.isEmpty())
        require(profile.endpoints.isEmpty())
        require(profile.capabilities == setOf(Capability.SIGN, Capability.LEGACY_SHA1))
        require(profile.clientAuthPolicy == null)
        require(profile.certificateRules == CertificateFilterRules(setOf("RSA"), true))
        require(profile.evidence.isNotEmpty())
        require(profile.operationPolicies.keys == setOf(ProtocolOperation.SIGN))
        require(
            profile.operationPolicies.getValue(ProtocolOperation.SIGN) == OperationPolicy(
                operation = ProtocolOperation.SIGN,
                safeDescription = JCCM_SAFE_DESCRIPTION,
                inputAdapterId = ProtocolInputAdapterId("miniapplet-autoscript-v1"),
                callbackContractId = CallbackContractId("miniapplet-sign-callback-v1"),
                capabilities = setOf(Capability.SIGN, Capability.LEGACY_SHA1),
                endpointId = null,
                algorithms = setOf(SignatureAlgorithm.SHA1_WITH_RSA),
                format = SignatureFormat.CADES,
                packaging = SignaturePackaging.DETACHED,
                mode = SignatureMode.EXPLICIT,
                fixedExtraProperties = emptyMap(),
                allowedExtraProperties = emptySet(),
            ),
        )
    }


    private fun validateMitesProfile(profile: SiteProfile) {
        require(profile.profileVersion == MITES_PROFILE_VERSION)
        require(profile.displayName == MITES_DISPLAY_NAME)
        require(profile.compatibilityStatus == CompatibilityStatus.VERIFIED_CONTRACT)
        require(profile.activation == ProfileActivation.QA_ONLY)
        require(profile.startUrl.toASCIIString() == MITES_START_URL)
        require(profile.initiatorOrigins == setOf(ExactOrigin.parse(MITES_ORIGIN)))
        require(profile.redirectOrigins.isEmpty())
        require(profile.trustedBrowseOrigins.isEmpty())
        require(profile.endpoints.isEmpty())
        require(profile.capabilities == setOf(Capability.SIGN))
        require(profile.clientAuthPolicy == null)
        require(profile.certificateRules == CertificateFilterRules(setOf("RSA"), true))
        require(profile.evidence.map { it.url.toASCIIString() }.toSet() == MITES_EVIDENCE_URLS)
        require(profile.operationPolicies.keys == setOf(ProtocolOperation.SIGN))
        require(
            profile.operationPolicies.getValue(ProtocolOperation.SIGN) == OperationPolicy(
                operation = ProtocolOperation.SIGN,
                safeDescription = MITES_SAFE_DESCRIPTION,
                inputAdapterId = ProtocolInputAdapterId("miniapplet-autoscript-v1"),
                callbackContractId = CallbackContractId("autoscript-sign-callback-v1"),
                capabilities = setOf(Capability.SIGN),
                endpointId = null,
                algorithms = setOf(SignatureAlgorithm.SHA512_WITH_RSA),
                format = SignatureFormat.CADES,
                packaging = SignaturePackaging.DETACHED,
                mode = SignatureMode.IMPLICIT,
                fixedExtraProperties = MITES_EXTRA_PROPERTIES,
                allowedExtraProperties = emptySet(),
            ),
        )
    }

    private fun validateUgrProfile(profile: SiteProfile) {
        require(profile.profileVersion == UGR_PROFILE_VERSION)
        require(profile.displayName == UGR_DISPLAY_NAME)
        require(profile.compatibilityStatus == CompatibilityStatus.VERIFIED_CONTRACT)
        require(profile.activation == ProfileActivation.QA_ONLY)
        require(profile.startUrl.toASCIIString() == UGR_START_URL)
        require(profile.initiatorOrigins == setOf(ExactOrigin.parse(UGR_ORIGIN)))
        require(profile.redirectOrigins.isEmpty())
        require(profile.trustedBrowseOrigins.isEmpty())
        require(profile.endpoints.isEmpty())
        require(profile.capabilities == setOf(Capability.SIGN, Capability.LEGACY_SHA1))
        require(profile.clientAuthPolicy == null)
        require(profile.certificateRules == CertificateFilterRules(setOf("RSA"), true))
        require(profile.evidence.isNotEmpty())
        require(profile.operationPolicies.keys == setOf(ProtocolOperation.SIGN))
        require(
            profile.operationPolicies.getValue(ProtocolOperation.SIGN) == OperationPolicy(
                operation = ProtocolOperation.SIGN,
                safeDescription = UGR_SAFE_DESCRIPTION,
                inputAdapterId = ProtocolInputAdapterId("miniapplet-autoscript-v1"),
                callbackContractId = CallbackContractId("miniapplet-sign-callback-v1"),
                capabilities = setOf(Capability.SIGN, Capability.LEGACY_SHA1),
                endpointId = null,
                algorithms = setOf(SignatureAlgorithm.SHA1_WITH_RSA),
                format = SignatureFormat.CADES,
                packaging = SignaturePackaging.DETACHED,
                mode = SignatureMode.EXPLICIT,
                fixedExtraProperties = emptyMap(),
                allowedExtraProperties = emptySet(),
            ),
        )
    }


    private fun validateAragonTramitesProfile(profile: SiteProfile) {
        require(profile.profileVersion == ARAGON_TRAMITES_PROFILE_VERSION)
        require(profile.displayName == ARAGON_TRAMITES_DISPLAY_NAME)
        require(profile.compatibilityStatus == CompatibilityStatus.VERIFIED_CONTRACT)
        require(profile.activation == ProfileActivation.QA_ONLY)
        require(profile.startUrl.toASCIIString() == ARAGON_TRAMITES_START_URL)
        require(profile.initiatorOrigins == setOf(ExactOrigin.parse(ARAGON_TRAMITES_ORIGIN)))
        require(profile.redirectOrigins == setOf(ExactOrigin.parse(ARAGON_SSLOGIN_BROKER_ORIGIN)))
        require(profile.trustedBrowseOrigins.isEmpty())
        require(profile.endpoints.isEmpty())
        require(profile.operationPolicies.isEmpty())
        require(profile.capabilities == setOf(Capability.CLIENT_TLS_AUTH))
        require(profile.certificateRules == CertificateFilterRules(setOf("RSA", "EC"), true))
        require(
            profile.clientAuthPolicy == ClientAuthPolicy(
                transitionMode = ClientAuthTransitionMode.REDIRECT_AFTER_SOURCE,
                requestOrigins = setOf(ExactOrigin.parse(ARAGON_SSLOGIN_CLIENT_AUTH_ORIGIN)),
                sourceUrls = setOf(URI(ARAGON_SSLOGIN_SOURCE_URL)),
                requestPath = ARAGON_SSLOGIN_REQUEST_PATH,
                fixedQueryParameters = ARAGON_SSLOGIN_FIXED_QUERY,
                requiredEphemeralQueryParameters = emptySet(),
                allowEmptyIssuerList = true,
                grantTtlSeconds = REVIEWED_CLIENT_AUTH_GRANT_TTL_SECONDS,
                requestPort = HTTPS_PORT,
                sourceFixedQueryParameters = ARAGON_SSLOGIN_FIXED_QUERY,
                sourceRequiredEphemeralQueryParameters = emptySet(),
                linkedEphemeralQueryParameters = emptySet(),
            ),
        )
        require(profile.evidence.map { it.url.toASCIIString() }.toSet() == ARAGON_TRAMITES_EVIDENCE_URLS)
        require(profile.evidence.all { it.reviewedOn == LocalDate.parse("2026-08-19") })
    }

    private fun validateCantabriaProfile(profile: SiteProfile) {
        require(profile.profileVersion == CANTABRIA_PROFILE_VERSION)
        require(profile.displayName == CANTABRIA_DISPLAY_NAME)
        require(profile.compatibilityStatus == CompatibilityStatus.VERIFIED_CONTRACT)
        require(profile.activation == ProfileActivation.QA_ONLY)
        require(profile.startUrl.toASCIIString() == CANTABRIA_START_URL)
        require(profile.initiatorOrigins == setOf(ExactOrigin.parse(CANTABRIA_ORIGIN)))
        require(profile.redirectOrigins.isEmpty())
        require(profile.trustedBrowseOrigins.isEmpty())
        require(profile.endpoints.isEmpty())
        require(profile.capabilities == setOf(Capability.SIGN))
        require(profile.clientAuthPolicy == null)
        require(profile.certificateRules == CertificateFilterRules(setOf("RSA"), true))
        require(profile.evidence.isNotEmpty())
        require(profile.operationPolicies.keys == setOf(ProtocolOperation.SIGN))
        require(
            profile.operationPolicies.getValue(ProtocolOperation.SIGN) == OperationPolicy(
                operation = ProtocolOperation.SIGN,
                safeDescription = CANTABRIA_SAFE_DESCRIPTION,
                inputAdapterId = ProtocolInputAdapterId("miniapplet-autoscript-v1"),
                callbackContractId = CallbackContractId("miniapplet-sign-callback-v1"),
                capabilities = setOf(Capability.SIGN),
                endpointId = null,
                algorithms = setOf(SignatureAlgorithm.SHA512_WITH_RSA),
                format = SignatureFormat.CADES,
                packaging = SignaturePackaging.DETACHED,
                mode = SignatureMode.IMPLICIT,
                fixedExtraProperties = CANTABRIA_EXTRA_PROPERTIES,
                allowedExtraProperties = emptySet(),
            ),
        )
    }

    private fun isReviewedSharedNavigationOrigin(
        origin: ExactOrigin,
        firstOwner: ProfileId,
        secondOwner: ProfileId,
        firstIsRedirectOrigin: Boolean,
        secondIsRedirectOrigin: Boolean,
    ): Boolean =
        (setOf(firstOwner.value, secondOwner.value).let { owners ->
            owners.size == 2 &&
                when (origin.serialized) {
                    AIREF_CLAVE_ORIGIN -> owners.all {
                        it in setOf(
                            MINECO_PROFILE_ID,
                            AIREF_PROFILE_ID,
                            ASTURIAS_PROFILE_ID,
                            EDUCATION_PROFILE_ID,
                            CATALUNYA_PROFILE_ID,
                            AVILA_PROFILE_ID,
                            CADIZ_PROFILE_ID,
                            GUADALAJARA_PROFILE_ID,
                            CTBG_PROFILE_ID,
                            CATASTRO_PROFILE_ID,
                            FEGA_PROFILE_ID,
                            CACERES_PROFILE_ID,
                            MUGEJU_PROFILE_ID,
                            JCCM_REGISTRO_PROFILE_ID,
                            SEGOVIA_PROFILE_ID,
                            PALENCIA_PROFILE_ID,
                            EL_HIERRO_PROFILE_ID,
                            OURENSE_PROFILE_ID,
                            MALAGA_PROFILE_ID,
                            SEVILLA_DIPUTACION_PROFILE_ID,
                            CORUNA_PROFILE_ID,
                        )
                    }
                    AIREF_CLIENT_AUTH_ORIGIN -> owners.all {
                        it in setOf(
                            MINECO_PROFILE_ID,
                            AIREF_PROFILE_ID,
                            ASTURIAS_PROFILE_ID,
                            EDUCATION_PROFILE_ID,
                            CATALUNYA_PROFILE_ID,
                            AVILA_PROFILE_ID,
                            CTBG_PROFILE_ID,
                            CATASTRO_PROFILE_ID,
                            FEGA_PROFILE_ID,
                            CACERES_PROFILE_ID,
                            MUGEJU_PROFILE_ID,
                            JCCM_REGISTRO_PROFILE_ID,
                            OURENSE_PROFILE_ID,
                            MALAGA_PROFILE_ID,
                            SEVILLA_DIPUTACION_PROFILE_ID,
                            CORUNA_PROFILE_ID,
                        )
                    }
                    else -> false
                }
        }) ||
            (setOf(firstOwner.value, secondOwner.value).let { owners ->
                owners.size == 2 &&
                    GUADALAJARA_PROFILE_ID in owners &&
                    owners.any { it in setOf(MINECO_PROFILE_ID, AIREF_PROFILE_ID, AVILA_PROFILE_ID) } &&
                    origin.serialized == AIREF_CLAVE_ORIGIN
            }) ||
            (setOf(firstOwner.value, secondOwner.value).let { owners ->
                firstIsRedirectOrigin && secondIsRedirectOrigin &&
                    (owners == setOf(DIPUTACION_BARCELONA_2057_PROFILE_ID, CATALUNYA_PROFILE_ID) ||
                        owners == setOf(DIPUTACION_BARCELONA_2057_PROFILE_ID, CATALUNYA_SEU_PROFILE_ID)) &&
                    origin.serialized == CATALUNYA_VALID_ORIGIN
            }) ||
            (setOf(firstOwner.value, secondOwner.value).let { owners ->
                firstIsRedirectOrigin && secondIsRedirectOrigin &&
                    owners == setOf(CATALUNYA_PROFILE_ID, CATALUNYA_SEU_PROFILE_ID) &&
                    origin.serialized == CATALUNYA_VALID_ORIGIN
            }) ||
            (setOf(firstOwner.value, secondOwner.value).let { owners ->
                owners == setOf(CATALUNYA_PROFILE_ID, CATALUNYA_SEU_PROFILE_ID) &&
                    ((firstIsRedirectOrigin != secondIsRedirectOrigin &&
                        origin.serialized == CATALUNYA_PUBLIC_ORIGIN) ||
                        (firstIsRedirectOrigin && secondIsRedirectOrigin &&
                            origin.serialized == CATALUNYA_OVT_ORIGIN))
            }) ||
            (setOf(firstOwner.value, secondOwner.value).let { owners ->
                firstIsRedirectOrigin && !secondIsRedirectOrigin &&
                    owners == setOf(DIPUTACION_BARCELONA_2057_PROFILE_ID, CATALUNYA_SEU_PROFILE_ID) &&
                    origin.serialized == CATALUNYA_CERT_ORIGIN
            }) ||
            (setOf(firstOwner.value, secondOwner.value).let { owners ->
                (owners == setOf(LEON_PROFILE_ID, MALLORCA_PROFILE_ID) ||
                    owners == setOf(LEON_PROFILE_ID, ALBACETE_PROFILE_ID) ||
                    owners == setOf(LEON_PROFILE_ID, CUENCA_PROFILE_ID)) &&
                    origin.serialized == SEDIPUALBA_CLIENT_AUTH_ORIGIN
            }) ||
            (setOf(firstOwner.value, secondOwner.value).let { owners ->
                (owners == setOf(ASTURIAS_PROFILE_ID, ASTURIAS_SEDE_NAVIGATION_PROFILE_ID) &&
                    firstIsRedirectOrigin != secondIsRedirectOrigin &&
                    origin.serialized == ASTURIAS_MIPRINCIPADO_ORIGIN) ||
                    (owners == setOf(MADRID_REGISTRO_PROFILE_ID, MADRID_CUENTA_DIGITAL_PROFILE_ID) &&
                        firstIsRedirectOrigin != secondIsRedirectOrigin &&
                        origin.serialized == MADRID_GESTIONA_ORIGIN) ||
                    (!firstIsRedirectOrigin && !secondIsRedirectOrigin &&
                        owners == setOf(TRANSPORTES_PROFILE_ID, SEPES_TRANSPORTES_PROFILE_ID) &&
                        origin.serialized == TRANSPORTES_SHARED_ORIGIN)
            }) ||
            (setOf(firstOwner.value, secondOwner.value) ==
                setOf("carne-joven-andalucia", "junta-andalucia-vea-peg") &&
                !firstIsRedirectOrigin && !secondIsRedirectOrigin &&
                origin.serialized == "https://ws235.juntadeandalucia.es") ||
            (setOf(firstOwner.value, secondOwner.value) ==
                setOf(ARAGON_LOCAL_CADES_PROFILE_ID, ARAGON_TRAMITES_PROFILE_ID) &&
                origin.serialized == ARAGON_TRAMITES_ORIGIN) ||
            (setOf(firstOwner.value, secondOwner.value) == setOf(DIBA_PROFILE_ID, TARRAGONA_PROFILE_ID) &&
                origin.serialized in setOf(TARRAGONA_VALID_ORIGIN, TARRAGONA_CERT_ORIGIN)) ||
            (setOf(firstOwner.value, secondOwner.value) ==
                setOf(CATALUNYA_PROFILE_ID, TARRAGONA_PROFILE_ID) &&
                origin.serialized == TARRAGONA_VALID_ORIGIN) ||
            (setOf(firstOwner.value, secondOwner.value) ==
                setOf(CATALUNYA_SEU_PROFILE_ID, TARRAGONA_PROFILE_ID) &&
                origin.serialized in setOf(TARRAGONA_VALID_ORIGIN, TARRAGONA_CERT_ORIGIN)) ||
            (setOf(firstOwner.value, secondOwner.value) ==
                setOf(EL_HIERRO_PROFILE_ID, EL_HIERRO_PUBLIC_NAVIGATION_PROFILE_ID) &&
                !firstIsRedirectOrigin && !secondIsRedirectOrigin &&
                origin.serialized == EL_HIERRO_PUBLIC_ORIGIN) ||
            (setOf(firstOwner.value, secondOwner.value) ==
                setOf(LA_GOMERA_PROFILE_ID, LA_GOMERA_PUBLIC_NAVIGATION_PROFILE_ID) &&
                !firstIsRedirectOrigin && !secondIsRedirectOrigin &&
                origin.serialized == LA_GOMERA_PUBLIC_ORIGIN) ||
            (setOf(firstOwner.value, secondOwner.value) ==
                setOf(LANZAROTE_PROFILE_ID, LANZAROTE_PUBLIC_NAVIGATION_PROFILE_ID) &&
                firstIsRedirectOrigin != secondIsRedirectOrigin &&
                origin.serialized == LANZAROTE_PUBLIC_REDIRECT_ORIGIN)

    private fun SiteProfile.allOrigins() = initiatorOrigins + redirectOrigins + trustedBrowseOrigins +
        (clientAuthPolicy?.requestOrigins ?: emptySet())
    private fun URI.origin() = ExactOrigin.parse("https://$host")
    private fun origins(values: List<JValue>) = values.map {
        val raw = it.string()
        ExactOrigin.parse(raw).also { origin -> require(raw == origin.serialized) }
    }.toSet().also { require(it.size == values.size) }
    private fun strings(values: List<JValue>) = values.map { it.string() }.toSet()
        .also { require(it.size == values.size && it.all(String::isNotBlank)) }
    private fun stringMap(value: JValue): Map<String, String> = value.obj("stringMap").values
        .mapValues { (_, entry) -> entry.string() }
        .also { map -> require(map.keys.all { it.isNotBlank() } && map.values.all { it.isNotBlank() }) }

    private fun extraProperties(
        value: JValue,
        allowBlankValues: Boolean,
    ): Map<String, String> = value.obj("stringMap").values
        .mapValues { (_, entry) -> entry.string() }
        .also { map ->
            require(map.keys.all { it.isNotBlank() })
            require(
                map.values.all { entry ->
                    entry.length <= MAX_EXTRA_PROPERTY_VALUE_CHARS &&
                        entry.none(Char::isISOControl) &&
                        (allowBlankValues || entry.isNotBlank())
                },
            )
        }
    private inline fun <reified T : Enum<T>> enums(values: List<JValue>) =
        values.map { enum<T>(it.string()) }.toSet().also { require(it.size == values.size) }
    private inline fun <reified T : Enum<T>> enum(value: String): T = enumValueOf(value)
    private fun strictHttpsUrl(raw: String): URI {
        require(raw.length <= MAX_HTTPS_URL_CHARS && !raw.any(Char::isISOControl))
        val uri = URI(raw)
        require(!uri.isOpaque && uri.scheme == "https" && uri.host != null && uri.userInfo == null)
        require(uri.port == -1 || uri.port == HTTPS_PORT)
        require(uri.rawFragment == null)
        val origin = ExactOrigin.parse("https://${uri.host}")
        require(uri.host == origin.host)
        return uri
    }

    private fun validContentType(value: String): Boolean =
        value.length <= MAX_CONTENT_TYPE_CHARS && CONTENT_TYPE.matches(value)

    private const val ARAGON_TRAMITES_PROFILE_ID = "aragon-solicitud-general-client-auth"
    private const val ARAGON_TRAMITES_PROFILE_VERSION = 1
    private const val ARAGON_TRAMITES_DISPLAY_NAME =
        "Gobierno de Aragón — Solicitud de carácter general"
    private const val ARAGON_TRAMITES_START_URL =
        "https://aplicaciones.aragon.es/tramitar/solicitud-general/identificacion"
    private const val ARAGON_TRAMITES_ORIGIN = "https://aplicaciones.aragon.es"
    private const val ARAGON_SSLOGIN_BROKER_ORIGIN = "https://login.loginssl.aragon.es"
    private const val ARAGON_SSLOGIN_CLIENT_AUTH_ORIGIN = "https://login1.loginssl.aragon.es"
    private const val ARAGON_SSLOGIN_SOURCE_URL =
        "https://login.loginssl.aragon.es/sife_login/SSLOGIN"
    private const val ARAGON_SSLOGIN_REQUEST_PATH = "/sife_login/SSLOGIN/idByCert"
    private const val ARAGON_SSLOGIN_CONSUME_RESPONSE =
        "https://aplicaciones.aragon.es/mfe_core/rest/identification/TTO/" +
            "aHR0cHM6Ly9hcGxpY2FjaW9uZXMuYXJhZ29uLmVzL3RyYW1pdGFyL3NvbGljaXR1ZC1nZW5lc" +
                "mFsL2lkZW50aWZpY2FjaW9uL2lkZW50aWZpY2Fkbw==/" +
            "SSLOGIN/consumeResponse"
    private val ARAGON_SSLOGIN_FIXED_QUERY = linkedMapOf(
        "redirect.url" to ARAGON_SSLOGIN_CONSUME_RESPONSE,
    )
    private val ARAGON_TRAMITES_EVIDENCE_URLS = setOf(
        "https://www.aragon.es/tramites/registro-electronico-general",
        ARAGON_TRAMITES_START_URL,
        "https://aplicaciones.aragon.es/mfe_core/rest/help/MFE_IDENTIFICACION_SSL_INICIO",
        ARAGON_SSLOGIN_SOURCE_URL,
        "https://login1.loginssl.aragon.es/sife_login/SSLOGIN/idByCert",
    )
    private const val ARAGON_LOCAL_CADES_PROFILE_ID = "aragon-siraw"
    private const val DGT_LOCAL_CADES_PROFILE_ID = "dgt-verificacion-equipo"
    private val REGISTERED_ADAPTERS = setOf(
        "miniapplet-autoscript-v1",
        "melilla-batch-autoscript-v1",
        "extremadura-batch-autoscript-v1",
        "la-palma-batch-autoscript-v1",
        "huesca-batch-autoscript-v1",
        "lugo-clientsigner-xml-batch-v1",
        "caib-portafib-batch-v1",
        "burgos-batch-autoscript-v1",
        "autoscript-select-certificate-v1",
    )
    private val REGISTERED_CALLBACKS = setOf(
        "miniapplet-sign-callback-v1",
        "autoscript-sign-callback-v1",
        "melilla-batch-result-v1",
        "extremadura-batch-result-v1",
        "la-palma-batch-result-v1",
        "huesca-batch-result-v1",
        "lugo-clientsigner-batch-result-v1",
        "caib-portafib-batch-result-v1",
        "burgos-batch-result-v1",
        "autoscript-select-certificate-callback-v1",
    )
    private val CONTENT_TYPE = Regex("[A-Za-z0-9!#$&^_.+-]+/[A-Za-z0-9!#$&^_.+-]+(?:; charset=UTF-8)?")
    private const val MAX_BODY_BYTES = 8 * 1024 * 1024
    private const val MAX_EXTRA_PROPERTY_VALUE_CHARS = 2_048
    private const val ISCIII_PROFILE_ID = "isciii-certificate-selection"
    private const val ISCIII_PROFILE_VERSION = 1
    private const val ISCIII_DISPLAY_NAME = "ISCIII — selección de certificado"
    private const val ISCIII_START_URL =
        "https://sede.isciii.gob.es/cargaApplet.jsp?accion=generico&recurso.opcion=null"
    private const val ISCIII_ORIGIN = "https://sede.isciii.gob.es"
    private const val ISCIII_SAFE_DESCRIPTION =
        "Compartir certificado con la Sede electrónica del ISCIII"
    private const val ISCIII_INPUT_ADAPTER_ID = "autoscript-select-certificate-v1"
    private const val ISCIII_CALLBACK_CONTRACT_ID =
        "autoscript-select-certificate-callback-v1"
    private val ISCIII_FIXED_EXTRA_PROPERTIES = linkedMapOf(
        "serverUrl" to
            "http://dtomcat7.isciiides.es:8080/afirma-server-triphase-signer/SignatureService",
    )
    private val ISCIII_EVIDENCE_URLS = setOf(
        ISCIII_START_URL,
        "https://sede.isciii.gob.es/js/autoscript/autoscript.js",
        "https://sede.isciii.gob.es/js/autoscript/constantes.js",
    )
    private const val VALENCIA_PROFILE_ID = "diputacion-valencia-sede"
    private const val VALENCIA_PROFILE_VERSION = 1
    private const val VALENCIA_DISPLAY_NAME = "Diputació de València — selección de certificado"
    private const val VALENCIA_START_URL = "https://portafirmas.dival.es/signingpad/xhtml/login.xhtml"
    private const val VALENCIA_ORIGIN = "https://portafirmas.dival.es"
    private const val VALENCIA_SAFE_DESCRIPTION =
        "Compartir certificado con el Portafirmas de la Diputació de València"
    private const val VALENCIA_INPUT_ADAPTER_ID = "autoscript-select-certificate-v1"
    private const val VALENCIA_CALLBACK_CONTRACT_ID = "autoscript-select-certificate-callback-v1"
    private val VALENCIA_FIXED_EXTRA_PROPERTIES = linkedMapOf(
        "filters" to "keyusage.nonrepudiation:true;nonexpired:true",
        "headless" to "true",
    )
    private val VALENCIA_EVIDENCE_URLS = setOf(
        VALENCIA_START_URL,
        "https://portafirmas.dival.es/signingpad/js/autoscript.js",
        "https://portafirmas.dival.es/signingpad/js/filtros.js",
    )
    private const val LA_RIOJA_PROFILE_ID = "la-rioja-oficina-electronica"
    private const val LA_RIOJA_PROFILE_VERSION = 1
    private const val LA_RIOJA_DISPLAY_NAME = "Gobierno de La Rioja — Oficina electrónica"
    private const val LA_RIOJA_START_URL =
        "https://ias1.larioja.org/oficinavirtual/presentacion?act_codi=24697"
    private const val LA_RIOJA_ORIGIN = "https://ias1.larioja.org"
    private const val LA_RIOJA_SOURCE_URL = "https://ias1.larioja.org/casLR/login"
    private val LA_RIOJA_EVIDENCE_URLS = setOf(
        "https://web.larioja.org/oficina-electronica/tramite?n=24697",
        LA_RIOJA_START_URL,
        LA_RIOJA_SOURCE_URL,
        "https://ias1.larioja.org/clientcertSSL/login",
    )
    private const val NAVARRA_PROFILE_ID = "navarra-sede-registro-general"
    private const val NAVARRA_PROFILE_VERSION = 1
    private const val NAVARRA_DISPLAY_NAME = "Gobierno de Navarra — Registro General con certificado"
    private const val NAVARRA_START_URL =
        "https://www.navarra.es/es/tramites/on/-/line/registro-general-electronico"
    private const val NAVARRA_ENTRY_ORIGIN = "https://www.navarra.es"
    private const val NAVARRA_RGE_ORIGIN = "https://administracionelectronica.navarra.es"
    private const val NAVARRA_ATEKA_ORIGIN = "https://ateka.navarra.es"
    private const val NAVARRA_SOURCE_URL = "https://ateka.navarra.es/ateka/router"
    private const val NAVARRA_REQUEST_PATH = "/ateka/Certificate/login"
    private const val NAVARRA_SOURCE_TOKEN_PARAMETER = "ReturnUrl"
    private const val NAVARRA_TARGET_TOKEN_PARAMETER = "returnUrl"
    private val NAVARRA_EVIDENCE_URLS = setOf(
        NAVARRA_START_URL,
        "https://administracionelectronica.navarra.es/RGE2/Default.aspx?idioma=es",
        NAVARRA_SOURCE_URL,
        "https://ateka.navarra.es/ateka/Certificate/login",
    )
    private const val SANIDAD_PROFILE_ID = "ministerio-sanidad-certificado"
    private const val SANIDAD_PROFILE_VERSION = 1
    private const val SANIDAD_DISPLAY_NAME = "Ministerio de Sanidad — acceso con certificado"
    private const val SANIDAD_START_URL = "https://sede.mscbs.gob.es/"
    private const val SANIDAD_ORIGIN = "https://sede.mscbs.gob.es"
    private const val SANIDAD_SOURCE_URL =
        "https://sede.mscbs.gob.es/registroElectronico/formularios.htm"
    private const val SANIDAD_REQUEST_PATH =
        "/SIGEM_AutenticacionWeb/validacionCertificado.do"
    private val SANIDAD_FIXED_QUERY = linkedMapOf(
        "REDIRECCION" to "RegistroTelematico",
        "tramiteId" to "TRAM_TARDESCONPLAN",
        "ENTIDAD_ID" to "000",
        "LANG" to "es",
        "COUNTRY" to "ES",
    )
    private val SANIDAD_EVIDENCE_URLS = setOf(
        SANIDAD_SOURCE_URL,
        "https://sede.mscbs.gob.es/SIGEM_RegistroTelematicoWeb/indiceForm",
        "https://sede.mscbs.gob.es/diseno/js/form_gen.js",
        "https://sede.mscbs.gob.es/SIGEM_AutenticacionWeb/validacionCertificado.do?" +
            "REDIRECCION=RegistroTelematico&tramiteId=TRAM_TARDESCONPLAN&" +
            "ENTIDAD_ID=000&LANG=es&COUNTRY=ES",
    )
    private const val VEA_PEG_PROFILE_ID = "junta-andalucia-vea-peg"
    private const val VEA_PEG_PROFILE_VERSION = 3
    private const val VEA_PEG_START_URL =
        "https://veaja.cloud.juntadeandalucia.es/inicio/procedimiento-detalle/PEG_VEA"
    private const val VEA_PEG_PORTAL_ORIGIN = "https://veaja.cloud.juntadeandalucia.es"
    private const val VEA_PEG_API_ORIGIN = "https://api-veaja.cloud.juntadeandalucia.es"
    private const val VEA_PEG_REQUEST_ORIGIN = "https://ws235.juntadeandalucia.es"
    private const val VEA_PEG_SOURCE_URL = "https://api-veaja.cloud.juntadeandalucia.es/auth/login"
    private const val VEA_PEG_REQUEST_PATH = "/authenticationFacade"
    private const val VEA_PEG_GRANT_TTL_SECONDS = 60
    private const val VEA_PEG_CONTINUATION_HOPS = 6
    private val VEA_PEG_TICKET_PARAMETERS = setOf("ticketId", "webSessionId")
    private val VEA_PEG_EVIDENCE = listOf(
        "https://www.juntadeandalucia.es/servicios/sede/tramites.html",
        VEA_PEG_START_URL,
        VEA_PEG_SOURCE_URL,
        "https://ws235.juntadeandalucia.es/authenticationFacade",
        "https://ws235-2.juntadeandalucia.es/authenticationFacade/3",
        "https://ws235-3.juntadeandalucia.es/authenticationFacade/4",
        "https://ws235-4.juntadeandalucia.es/authenticationFacade/5",
    ).associateWith { "2026-08-28" }
    private const val CLAVE_IDENT_ORIGIN = "https://pasarela-ident.clave.gob.es"
    private const val CLAVE_AUTHENTICATE_PATH = "/IdP2/AuthenticateCitizen"
    private const val CLAVE_IDENT_EVIDENCE_URL = "https://pasarela-ident.clave.gob.es/IdP2/AuthenticateCitizen"
    private const val CLAVE_PROVIDER_URL = "https://pasarela.clave.gob.es/Proxy2/ServiceProvider"
    private const val CLAVE_REDIRECT_URL = "https://pasarela.clave.gob.es/Proxy2/ServiceRedirect"
    private const val CLAVE_PASARELA_ORIGIN = "https://pasarela.clave.gob.es"
    private val CLAVE_DIRECT_PINS = mapOf(
        ASTURIAS_PROFILE_ID to ClaveDirectPin(
            displayName = "Principado de Asturias — MiPrincipado / Solicitud Genérica",
            startUrl = "https://miprincipado.asturias.es/-/dboid-6269000102616541907573" +
                "?redirect=%2Fweb%2Fsede%2Ftodos-los-servicios-y-tramites",
            origins = ClaveOriginPins(
                initiator = "https://miprincipado.asturias.es",
                redirect = setOf(
                    "https://tramita.asturias.es",
                    "https://rhsso.asturias.es",
                    CLAVE_PASARELA_ORIGIN,
                ),
                trustedBrowse = emptySet(),
            ),
            sourceUrl = CLAVE_PROVIDER_URL,
            keyAlgorithms = setOf("RSA"),
            evidence = claveEvidence(
                "2026-08-19",
                "https://miprincipado.asturias.es/-/dboid-6269000102616541907573" +
                    "?redirect=%2Fweb%2Fsede%2Ftodos-los-servicios-y-tramites",
                "https://tramita.asturias.es/sta/Relec/STARhssoManager",
                CLAVE_PROVIDER_URL,
                CLAVE_IDENT_EVIDENCE_URL,
            ),
        ),
        OURENSE_PROFILE_ID to ClaveDirectPin(
            displayName = "Deputación de Ourense — Solicitud de propósito general",
            startUrl = "https://sede.depourense.es/sta/CarpetaPublic/doEvent" +
                "?APP_CODE=STA&PAGE_CODE=CATALOGO&DETALLE=6269000946476474507610&lang=ES",
            origins = ClaveOriginPins(
                initiator = "https://sede.depourense.es",
                redirect = setOf(CLAVE_PASARELA_ORIGIN),
                trustedBrowse = emptySet(),
            ),
            sourceUrl = CLAVE_REDIRECT_URL,
            keyAlgorithms = setOf("RSA", "EC"),
            evidence = claveEvidence(
                "2026-08-20",
                "https://sede.depourense.es/sta/CarpetaPublic/doEvent" +
                    "?APP_CODE=STA&PAGE_CODE=CATALOGO&DETALLE=6269000946476474507610&lang=ES",
                "https://sede.depourense.es/sta/pages/webapps/js/components/catserv.js?ver=2602.0.4",
                CLAVE_REDIRECT_URL,
                CLAVE_IDENT_EVIDENCE_URL,
            ),
        ),
        SEVILLA_DIPUTACION_PROFILE_ID to ClaveDirectPin(
            displayName = "Diputación de Sevilla — Sede electrónica",
            startUrl = "https://sedeelectronicadipusevilla.es",
            origins = ClaveOriginPins(
                initiator = "https://sedeelectronicadipusevilla.es",
                redirect = setOf(CLAVE_PASARELA_ORIGIN),
                trustedBrowse = emptySet(),
            ),
            sourceUrl = CLAVE_REDIRECT_URL,
            keyAlgorithms = setOf("RSA", "EC"),
            evidence = claveEvidence(
                "2026-08-21",
                "https://sedeelectronicadipusevilla.es",
                "https://sedeelectronicadipusevilla.es/opencms/system/modules/gsede/elements/secciones/" +
                    "autenticacion/autenticacion.jsp",
                "https://sedeelectronicadipusevilla.es/opencms/common-js/clientSigner.js",
                CLAVE_REDIRECT_URL,
                CLAVE_IDENT_EVIDENCE_URL,
            ),
        ),
        CORUNA_PROFILE_ID to ClaveDirectPin(
            displayName = "Deputación da Coruña — Solicitude Xeral",
            startUrl = "https://www.dacoruna.gal/portada",
            origins = ClaveOriginPins(
                initiator = "https://www.dacoruna.gal",
                redirect = setOf(CLAVE_PASARELA_ORIGIN),
                trustedBrowse = setOf("https://sede.dacoruna.gal"),
            ),
            sourceUrl = CLAVE_REDIRECT_URL,
            keyAlgorithms = setOf("RSA", "EC"),
            evidence = claveEvidence(
                "2026-08-21",
                "https://www.dacoruna.gal/portada",
                "https://sede.dacoruna.gal/sxc/gl/procedimientosytramites/tramites/SolicitudGeneral_N",
                "https://sede.dacoruna.gal/tramitador/entrada" +
                    "?idLogica=accesoDirecto&entrada=ciudadano&idEntidad=diputacion&idExpediente=X004&fkIdioma=GL",
                CLAVE_REDIRECT_URL,
                CLAVE_IDENT_EVIDENCE_URL,
            ),
        ),
    )
    private const val CARNE_JOVEN_PROFILE_ID = "carne-joven-andalucia"
    private const val CARNE_JOVEN_DISPLAY_NAME = "Carné Joven Europeo de Andalucía"
    private const val CARNE_JOVEN_START_URL =
        "https://ws104.juntadeandalucia.es/carneJoven/cjservlet/portal/index.jsp"
    private const val CARNE_JOVEN_INITIATOR_ORIGIN = "https://ws104.juntadeandalucia.es"
    private const val CARNE_JOVEN_REQUEST_ORIGIN = "https://ws235.juntadeandalucia.es"
    private const val CARNE_JOVEN_SOURCE_URL =
        "https://ws104.juntadeandalucia.es/carneJoven/servlet/CallAuthenticationServlet"
    private const val CARNE_JOVEN_REQUEST_PATH = "/authenticationFacade"
    private const val CARNE_JOVEN_COME_BACK_URL =
        "aHR0cHM6Ly93czEwNC5qdW50YWRlYW5kYWx1Y2lhLmVzL2Nhcm5lSm92ZW4vc2VydmxldC9SZXR1cm5BdXRoZW50aWNhdGlvblNlcnZsZXQ="
    private val CARNE_JOVEN_EVIDENCE_URLS = setOf(
        "https://www.juntadeandalucia.es/servicios/sede/tramites/procedimientos/detalle/24721.html",
        CARNE_JOVEN_SOURCE_URL,
    )
    private const val AEAT_MIS_DATOS_PROFILE_ID = "aeat-mis-datos-censales"
    private const val AEAT_PROFILE_VERSION = 1
    private const val AEAT_DISPLAY_NAME = "Agencia Tributaria — Mis datos censales"
    private const val AEAT_START_URL = "https://sede.agenciatributaria.gob.es/Sede/mi-area-personal.html"
    private const val AEAT_INITIATOR_ORIGIN = "https://sede.agenciatributaria.gob.es"
    private const val AEAT_REQUEST_ORIGIN = "https://www1.agenciatributaria.gob.es"
    private const val AEAT_SOURCE_URL = AEAT_START_URL
    private const val AEAT_REQUEST_PATH = "/wlpl/BUGC-JDIT/MdcAcceso"
    private val AEAT_EVIDENCE_URLS = setOf(
        AEAT_START_URL,
        "https://www1.agenciatributaria.gob.es/wlpl/BUGC-JDIT/MdcAcceso",
    )
    private const val IMPORTASS_PROFILE_ID = "tgss-importass-client-auth"
    private const val IMPORTASS_PROFILE_VERSION = 1
    private const val IMPORTASS_DISPLAY_NAME = "TGSS Import@ss — acceso con certificado"
    private const val IMPORTASS_START_URL =
        "https://portal.seg-social.gob.es/wps/myportal/importass/importass/personal/"
    private const val IMPORTASS_PORTAL_ORIGIN = "https://portal.seg-social.gob.es"
    private const val IMPORTASS_IDP_ORIGIN = "https://idp.seg-social.es"
    private const val IMPORTASS_REQUEST_ORIGIN = "https://ipce.seg-social.es"
    private const val IMPORTASS_SOURCE_URL = "https://idp.seg-social.es/PGIS/Login?seleccion=IPCE"
    private const val IMPORTASS_REQUEST_PATH = "/IPCE/Login"
    private val IMPORTASS_EVIDENCE_URLS = setOf(
        "https://portal.seg-social.gob.es/wps/portal/importass/importass/ayuda",
        IMPORTASS_START_URL,
        IMPORTASS_SOURCE_URL,
        "https://ipce.seg-social.es/IPCE/Login",
    )
    private const val TOLEDO_PROFILE_ID = "diputacion-toledo-sede"
    private const val TOLEDO_PROFILE_VERSION = 1
    private const val TOLEDO_DISPLAY_NAME = "Diputación de Toledo — Registro Telemático con certificado"
    private const val TOLEDO_START_URL =
        "https://diputacion.toledo.gob.es/SIGEM_RegistroTelematicoWeb/realizarSolicitudRegistro.do?tramiteId=TRAM_31"
    private const val TOLEDO_ORIGIN = "https://diputacion.toledo.gob.es"
    private const val TOLEDO_SOURCE_URL =
        "https://diputacion.toledo.gob.es/SIGEM_AutenticacionWeb/seleccionEntidad.do" +
            "?REDIRECCION=RegistroTelematico&tramiteId=TRAM_31&SESION_ID=&ENTIDAD_ID=&LANG=&COUNTRY="
    private const val TOLEDO_REQUEST_PATH = "/SIGEM_AutenticacionWeb/validacionCertificado.do"
    private const val TOLEDO_REQUEST_PORT = 843
    private val TOLEDO_EVIDENCE_URLS = setOf(
        "https://diputacion.toledo.gob.es/procedimientos/1",
        TOLEDO_START_URL,
        TOLEDO_SOURCE_URL,
    )
    private const val VALLADOLID_PROFILE_ID = "diputacion-valladolid-sede"
    private const val VALLADOLID_PROFILE_VERSION = 1
    private const val VALLADOLID_DISPLAY_NAME = "Diputación de Valladolid — acceso con certificado"
    private const val VALLADOLID_START_URL = "https://www.sede.diputaciondevalladolid.es/tgauth/login"
    private const val VALLADOLID_ORIGIN = "https://www.sede.diputaciondevalladolid.es"
    private const val VALLADOLID_SOURCE_URL = "https://www.sede.diputaciondevalladolid.es/c/portal/cert-login"
    private const val VALLADOLID_REQUEST_PATH = "/c/portal/cert-login"
    private const val VALLADOLID_REQUEST_PORT = 21460
    private val VALLADOLID_EVIDENCE_URLS = setOf(
        "https://www.sede.diputaciondevalladolid.es/tramites-disponibles/12S203/",
        VALLADOLID_START_URL,
        VALLADOLID_SOURCE_URL,
    )
    private const val SORIA_PROFILE_ID = "diputacion-soria-sede-client-auth"
    private const val SORIA_ORIGIN = "https://portaltramitador.dipsoria.es"
    private const val SORIA_START_URL = "$SORIA_ORIGIN/web/inicioWebc.do?opcion=cargar" +
        "&redirige=L2NhcmdhTWVudVdlYi5kbz9vcGNpb249bm9yZWc%3D&entidad=SORIA&idioma=1"
    private const val SORIA_REQUEST_PATH = "/web/inicioWebcCert.do"
    private val SORIA_FIXED_QUERY = mapOf(
        "opcion" to "ssl",
        "entidad" to "SORIA",
        "redirige" to "L2NhcmdhTWVudVdlYi5kbz9vcGNpb249bm9yZWc%3D",
        "idioma" to "1",
    )
    private const val MENORCA_PROFILE_ID = "menorca-carpeta-ciutadana"
    private const val MENORCA_PROFILE_VERSION = 1
    private const val MENORCA_DISPLAY_NAME = "Consell Insular de Menorca — Sol·licitud genèrica"
    private const val MENORCA_START_URL =
        "https://www.carpetaciutadana.org/cime/gesserveis/Gestion.aspx?IDGESTION=990100262"
    private const val MENORCA_ORIGIN = "https://www.carpetaciutadana.org"
    private const val MENORCA_SOURCE_URL = "https://www.carpetaciutadana.org/cime/Login/Login.aspx"
    private const val MENORCA_REQUEST_PATH = "/cime/Login/LoginCert.aspx"
    private const val MENORCA_URL_PARAMETER = "URL"
    private val MENORCA_EVIDENCE_URLS = setOf(
        MENORCA_START_URL,
        "https://www.carpetaciutadana.org/cime/solicituds/iniciartramit.aspx?TIPO=REGE&IDIOMA=1",
        MENORCA_SOURCE_URL,
        "https://www.carpetaciutadana.org/cime/Login/LoginCert.aspx",
    )
    private const val DIBA_PROFILE_ID = "diputacion-barcelona-solicitud-generica-2057"
    private const val TARRAGONA_PROFILE_ID = "diputacion-tarragona-sede"
    private const val TARRAGONA_PROFILE_VERSION = 1
    private const val TARRAGONA_DISPLAY_NAME =
        "Diputació de Tarragona — Sol·licitud genèrica amb certificat"
    private const val TARRAGONA_START_URL =
        "https://seuelectronica.dipta.cat/tramits-online/fr/administracions/8004330008/" +
            "procediments/DIP80_EGIST_00001/crearInstancia"
    private const val TARRAGONA_INITIATOR_ORIGIN = "https://seuelectronica.dipta.cat"
    private const val TARRAGONA_INTEGRATOR_ORIGIN = "https://egovern.altanet.org"
    private const val TARRAGONA_VALID_ORIGIN = "https://valid.aoc.cat"
    private const val SEGURIDAD_SOCIAL_AUTOFIRMA_PROFILE_ID = "seguridad-social-sede-autofirma"
    private const val SEGURIDAD_SOCIAL_ORIGIN = "https://sede.seg-social.gob.es"
    private const val SEGURIDAD_SOCIAL_AUTOFIRMA_START_URL =
        "https://sede.seg-social.gob.es/wps/portal/sede/sede/Inicio/RegistroElectronicoApod/NREASS_3?changeLanguage=es"
    private const val TARRAGONA_VALID_SOURCE = "https://valid.aoc.cat/o/oauth2/auth"
    private const val TARRAGONA_CERT_ORIGIN = "https://cert.valid.aoc.cat"
    private const val TARRAGONA_CERT_PATH = "/o/oauth2/cert"
    private val TARRAGONA_SOURCE_FIXED_QUERY = linkedMapOf(
        "response_type" to "code",
        "client_id" to "valid.dipta.cat",
        "redirect_uri" to "https://egovern.altanet.org/valid/code",
        "scope" to "autenticacio_usuari",
        "access_type" to "online",
        "approval_prompt" to "auto",
    )
    private val TARRAGONA_EVIDENCE_URLS = setOf(
        "https://seuelectronica.dipta.cat/instancia-generica",
        "https://valid.aoc.cat/o/oauth2/js/login.js",
        "https://cert.valid.aoc.cat/o/oauth2/cert",
    )
    private const val TEA_PROFILE_ID = "tea-alegaciones-certificado"
    private const val TEA_PROFILE_VERSION = 1
    private const val TEA_DISPLAY_NAME = "TEA — Alegaciones con certificado"
    private const val TEA_SOURCE_URL = "https://sede.tea.hacienda.gob.es/TEA/alegaciones.html"
    private const val TEA_SOURCE_ORIGIN = "https://sede.tea.hacienda.gob.es"
    private const val TEA_REQUEST_ORIGIN = "https://www1.tea.hacienda.gob.es"
    private const val TEA_REQUEST_PATH = "/wlpl/TEAC-TRAM/SedeTRAM"
    private val TEA_EVIDENCE_URLS = setOf(
        TEA_SOURCE_URL,
        "https://www1.tea.hacienda.gob.es/wlpl/TEAC-TRAM/SedeTRAM?tram=0",
    )
    private const val LLEIDA_PROFILE_ID = "diputacion-lleida-sede"
    private const val BADAJOZ_PROFILE_ID = "diputacion-badajoz-portal"
    private const val LLEIDA_START_URL = "https://seu.diputaciolleida.cat"
    private const val LLEIDA_ORIGIN = "https://seu.diputaciolleida.cat"
    private const val BADAJOZ_START_URL = "https://sede.dip-badajoz.es"
    private const val BADAJOZ_ORIGIN = "https://sede.dip-badajoz.es"
    private val LLEIDA_EXTRA_PROPERTIES = linkedMapOf(
        "policy" to "FirmaAGE",
        "headless" to "true",
        "filters" to "nonexpired:true;authCert:true",
    )
    private const val CANARIAS_PROFILE_ID = "canarias-sede"
    private const val CANARIAS_PROFILE_VERSION = 1
    private const val CANARIAS_DISPLAY_NAME = "Gobierno de Canarias — Sede electrónica"
    private const val CANARIAS_START_URL = "https://sede.gobiernodecanarias.org/sede/la_sede"
    private const val CANARIAS_ORIGIN = "https://sede.gobiernodecanarias.org"
    private const val CANARIAS_SAFE_DESCRIPTION =
        "Acceso con certificado a la Sede electrónica del Gobierno de Canarias"
    private val CANARIAS_EXTRA_PROPERTIES = linkedMapOf(
        "format" to "CAdES Detached",
        "serverUrl" to "https://sede.gobiernodecanarias.org/platino/servlet_afirma/SignatureService",
        "referencesDigestMethod" to "http://www.w3.org/2001/04/xmlenc#sha512",
        "filters" to "nonexpired:true;signingCert:true;issuer.rfc2254:" +
            "(&(!(CN=CiberCentro*))(!(CN=GobCanCA))(!(O=Gobierno de Canarias))" +
            "(!(O=PKI))(!(O=DO_NOT_TRUST*)))",
    )
    private val CANARIAS_EVIDENCE_URLS = setOf(
        CANARIAS_START_URL,
        "https://sede.gobiernodecanarias.org/sede/tramites/6861",
        "https://sede.gobiernodecanarias.org/sede/identificacion",
        "https://sede.gobiernodecanarias.org/platino/cliente_afirma/mini/js/miniapplet.js",
        "https://sede.gobiernodecanarias.org/platino/cliente_afirma/mini/js/sfest.base.js",
    )
    private const val TRANSPARENCIA_PROFILE_ID = "age-portal-de-la-transparencia"
    private const val TRANSPARENCIA_PROFILE_VERSION = 1
    private const val TRANSPARENCIA_DISPLAY_NAME = "Portal de la Transparencia — Derecho de acceso"
    private const val TRANSPARENCIA_START_URL =
        "https://transparencia.sede.gob.es/procedimiento/portada?idProc=133628&idAmb=101524"
    private const val TRANSPARENCIA_ORIGIN = "https://transparencia.sede.gob.es"
    private const val TRANSPARENCIA_SAFE_DESCRIPTION =
        "Firma PAdES con certificado en el Portal de la Transparencia"
    private val TRANSPARENCIA_EXTRA_PROPERTIES = linkedMapOf(
        "filters" to "nonexpired:true;",
        "headless" to "true",
    )
    private val TRANSPARENCIA_EVIDENCE_URLS = setOf(
        TRANSPARENCIA_START_URL,
        "https://transparencia.sede.gob.es/.resources/ac2-front/webresources/js/ac2-formularios.js",
        "https://transparencia.sede.gob.es/.resources/ac2-front/webresources/js/autofirma/ac2-autofirmaFunctions.js",
        "https://transparencia.sede.gob.es/.resources/ac2-front/webresources/js/autofirma/autoscript.js",
    )
    private const val FUERTEVENTURA_PROFILE_ID = "fuerteventura-sede-electronica"
    private const val FUERTEVENTURA_PROFILE_VERSION = 1
    private const val FUERTEVENTURA_DISPLAY_NAME =
        "Cabildo Insular de Fuerteventura — Sede electrónica"
    private const val FUERTEVENTURA_START_URL =
        "https://sede.cabildofuer.es/eAdmin/Registrar.do?action=comenzar&tipoReg=1"
    private const val FUERTEVENTURA_ORIGIN = "https://sede.cabildofuer.es"
    private const val FUERTEVENTURA_SAFE_DESCRIPTION =
        "Firma PAdES de solicitud en la Sede electrónica del Cabildo Insular de Fuerteventura"
    private val FUERTEVENTURA_EXTRA_PROPERTIES = linkedMapOf(
        "signaturePositionOnPageLowerLeftX" to "50",
        "signaturePositionOnPageLowerLeftY" to "15",
        "signaturePositionOnPageUpperRightX" to "150",
        "signaturePositionOnPageUpperRightY" to "50",
        "signaturePages" to "all",
        "layer2Text" to "Firmado por \$\$SUBJECTCN\$\$ el día \$\$SIGNDATE=dd/MM/yyyy\$\$ \$\$ORGANIZATION\$\$",
        "layer2FontSize" to "6",
        "layer2FontFamily" to "0",
        "layer2FontStyle" to "0",
        "signatureRotation" to "0",
        "includeQuestionMark" to "false",
        "obfuscateCertText" to "true",
    )
    private val FUERTEVENTURA_EVIDENCE_URLS = setOf(
        FUERTEVENTURA_START_URL,
        "https://sede.cabildofuer.es/eAdmin/Registrar.do?action=verYfirmar&modo=cert",
        "https://sede.cabildofuer.es/eAdmin/js/miniapplet.js",
    )
    private const val GRAN_CANARIA_PROFILE_ID = "gran-canaria-sede-electronica"
    private const val GRAN_CANARIA_PROFILE_VERSION = 1
    private const val GRAN_CANARIA_DISPLAY_NAME = "Cabildo Insular de Gran Canaria — Sede electrónica"
    private const val GRAN_CANARIA_START_URL = "https://sede.grancanaria.com/sede-privado/instancia-general?inicio"
    private const val GRAN_CANARIA_ORIGIN = "https://sede.grancanaria.com"
    private const val GRAN_CANARIA_SAFE_DESCRIPTION =
        "Firma PAdES de solicitud en la Sede del Cabildo de Gran Canaria"
    private val GRAN_CANARIA_EXTRA_PROPERTIES = linkedMapOf(
        "headless" to "true",
        "filters" to "nonexpired:",
    )
    private val GRAN_CANARIA_EVIDENCE_URLS = setOf(
        "https://sede.grancanaria.com/informacion-instancia",
        "https://sede.grancanaria.com/informacion-instancia?" +
            "p_p_id=Configuracion_WAR_SedeElectronicaportlet_INSTANCE_sede_tramites&" +
            "p_p_lifecycle=2&p_p_state=normal&p_p_mode=view&" +
            "p_p_cacheability=cacheLevelPage&p_p_col_id=&p_p_col_count=0&" +
            "_Configuracion_WAR_SedeElectronicaportlet_INSTANCE_sede_tramites_" +
            "javax.faces.resource=AFIRMA%2Foperaciones.js&" +
            "_Configuracion_WAR_SedeElectronicaportlet_INSTANCE_sede_tramites_ln=js",
    )
    private const val MINECO_PROFILE_ID = "ministerio-economia-instancia-generica"
    private const val MINECO_PROFILE_VERSION = 1
    private const val MINECO_DISPLAY_NAME =
        "Ministerio de Economía, Comercio y Empresa — Instancia Genérica"
    private const val MINECO_START_URL =
        "https://serviciosede.mineco.gob.es/FB/Home.aspx?control=161_IG"
    private const val MINECO_ORIGIN = "https://serviciosede.mineco.gob.es"
    private const val MINECO_SAFE_DESCRIPTION =
        "Firma PAdES de Instancia Genérica del Ministerio de Economía, Comercio y Empresa"
    private val MINECO_BROWSE_ORIGINS = setOf(
        ExactOrigin.parse("https://pasarela.clave.gob.es"),
        ExactOrigin.parse("https://pasarela-ident.clave.gob.es"),
    )
    private val MINECO_EXTRA_PROPERTIES = linkedMapOf(
        "filters" to "signingCert:;nonexpired:",
        "expPolicy" to "FirmaAGE",
        "signatureSubFilter" to "ETSI.CAdES.detached",
    )
    private val MINECO_EVIDENCE_URLS = setOf(
        MINECO_START_URL,
        "https://serviciosede.mineco.gob.es/FB/solicitud/firma.aspx",
        "https://serviciosede.mineco.gob.es/FB/@miniFirma/js/autoscript.js",
        "https://sede.mineco.gob.es/stfls/sede/Ficheros/manuales/Manual_IG.pdf",
    )
    private const val XUNTA_PROFILE_ID = "xunta-galicia-solicitude-xenerica"
    private const val XUNTA_PROFILE_VERSION = 1
    private const val XUNTA_DISPLAY_NAME = "Xunta de Galicia — Solicitude xenérica"
    private const val XUNTA_START_URL = "https://sede.xunta.gal/tramites-e-servizos/solicitude-xenerica"
    private const val XUNTA_ORIGIN = "https://sede.xunta.gal"
    private const val XUNTA_ENDPOINT_ID = "xunta-galicia-triphase"
    private const val XUNTA_ENDPOINT_URL = "https://sede.xunta.gal/presenta/sinatura/SignatureService"
    private const val XUNTA_SIGN_SAFE_DESCRIPTION =
        "Firma PAdES de solicitud genérica en la Sede de la Xunta de Galicia"
    private const val XUNTA_SELECT_SAFE_DESCRIPTION =
        "Seleccionar certificado para la solicitud genérica de la Xunta de Galicia"
    private val XUNTA_FIXED_EXTRA_PROPERTIES = linkedMapOf(
        "format" to "PAdES",
        "signatureSubFilter" to "ETSI.CAdES.detached",
        "serverUrl" to XUNTA_ENDPOINT_URL,
        "referencesDigestMethod" to "http://www.w3.org/2000/09/xmldsig#sha1",
        "mimeType" to "hash/sha256",
        "headless" to "true",
    )
    private val XUNTA_ALLOWED_EXTRA_PROPERTIES = linkedSetOf(
        "filters", "locale", "nif", "id", "codigoSeguridad", "marcaFirmaCustom", "dataUser", "idBorrador",
    )
    private val XUNTA_SELECT_FIXED_EXTRA_PROPERTIES = linkedMapOf("filters" to "nonexpired")
    private val XUNTA_EVIDENCE_URLS = setOf(
        XUNTA_START_URL,
        "https://sede.xunta.gal/presenta/novo/PR004A_2025_1",
        "https://sede.xunta.gal/presenta/assets/js/miniapplet.js?nocache=1.7.0",
        "https://sede.xunta.gal/presenta/main.293423417603b2d37c80.js",
    )
    private const val EIVISSA_PROFILE_ID = "eivissa-sede-electronica"
    private const val EIVISSA_PROFILE_VERSION = 1
    private const val EIVISSA_DISPLAY_NAME = "Consell Insular d’Eivissa — Sede electrónica"
    private const val EIVISSA_START_URL = "https://seu.conselldeivissa.es/"
    private const val EIVISSA_ORIGIN = "https://seu.conselldeivissa.es"
    private const val EIVISSA_SAFE_DESCRIPTION =
        "Firma de Instancia General en la Sede electrónica del Consell Insular d’Eivissa"
    private val EIVISSA_FIXED_EXTRA_PROPERTIES = linkedMapOf("headless" to "true", "mode" to "implicit")
    private val EIVISSA_EVIDENCE_URLS = setOf(
        EIVISSA_START_URL,
        "https://seu.conselldeivissa.es/sta/reg/autofirma.js",
        "https://seu.conselldeivissa.es/sta/CarpetaPublic/Public?APP_CODE=STA&PAGE_COD" +
            "E=CATALOGO&DETALLE=6269002703260065905043",
    )
    private const val TENERIFE_PROFILE_ID = "tenerife-sede-electronica"
    private const val TENERIFE_PROFILE_VERSION = 1
    private const val TENERIFE_DISPLAY_NAME = "Cabildo Insular de Tenerife — Sede electrónica"
    private const val TENERIFE_START_URL = "https://sede.tenerife.es/"
    private const val TENERIFE_ORIGIN = "https://sede.tenerife.es"
    private const val TENERIFE_SAFE_DESCRIPTION =
        "Firma de solicitud en la Sede electrónica del Cabildo Insular de Tenerife"
    private val TENERIFE_EXTRA_PROPERTIES = linkedMapOf("mode" to "explicit")
    private const val ASTURIAS_PROFILE_ID = "asturias-miprincipado"
    private const val ASTURIAS_SEDE_NAVIGATION_PROFILE_ID = "asturias-sede-tramite-navigation"
    private const val ASTURIAS_MIPRINCIPADO_ORIGIN = "https://miprincipado.asturias.es"
    private const val MADRID_REGISTRO_PROFILE_ID = "comunidad-madrid-registro-general"
    private const val MADRID_CUENTA_DIGITAL_PROFILE_ID = "comunidad-madrid-cuenta-digital-53f1"
    private const val MADRID_GESTIONA_ORIGIN = "https://gestiona.comunidad.madrid"
    private const val MUGEJU_PROFILE_ID = "mugeju-remision-documentacion-client-auth"
    private const val MUGEJU_PROFILE_VERSION = 1
    private const val MUGEJU_DISPLAY_NAME = "MUGEJU — Remisión de documentación con certificado"
    private const val MUGEJU_START_URL = "https://sedemugeju.gob.es/remisiondocumentacion"
    private const val MUGEJU_ORIGIN = "https://sedemugeju.gob.es"
    private const val MUGEJU_CLAVE_ORIGIN = "https://pasarela.clave.gob.es"
    private const val MUGEJU_CLIENT_AUTH_ORIGIN = "https://pasarela-ident.clave.gob.es"
    private const val MUGEJU_CLIENT_AUTH_SOURCE_URL = "https://pasarela.clave.gob.es/Proxy2/ServiceProvider"
    private const val MUGEJU_CLIENT_AUTH_REQUEST_PATH = "/IdP2/AuthenticateCitizen"
    private val MUGEJU_EVIDENCE_REVIEWS = mapOf(
        MUGEJU_START_URL to LocalDate.parse("2026-08-19"),
        "https://sedemugeju.gob.es/mutualnet3/clave/ControladorClaveCiudadanoServlet?operation=REM" to
            LocalDate.parse("2026-08-19"),
        MUGEJU_CLIENT_AUTH_SOURCE_URL to LocalDate.parse("2026-08-18"),
        "https://pasarela-ident.clave.gob.es/IdP2/AuthenticateCitizen" to LocalDate.parse("2026-08-18"),
    )
    private const val EDUCATION_PROFILE_ID = "educacion-convocatoria"
    private const val EDUCATION_PROFILE_VERSION = 2
    private const val EDUCATION_DISPLAY_NAME =
        "Ministerio de Educación — Convocatoria 46 — acceso con certificado"
    private const val EDUCATION_START_URL =
        "https://sede.educacion.gob.es/sede/login/loginConv.jjsp?iA=no&idConvocatoria=46"
    private const val EDUCATION_ORIGIN = "https://sede.educacion.gob.es"
    private const val EDUCATION_CLAVE_EDU_ORIGIN = "https://www.educacion.gob.es"
    private const val EDUCATION_CLIENT_AUTH_SOURCE_URL =
        "https://pasarela.clave.gob.es/Proxy2/ServiceRedirect"
    private val EDUCATION_EVIDENCE_URLS = setOf(
        EDUCATION_START_URL,
        "https://www.educacion.gob.es/claveedu/claveEduPeticion.form",
        "https://pasarela.clave.gob.es/Proxy2/ServiceProvider",
        EDUCATION_CLIENT_AUTH_SOURCE_URL,
        "https://pasarela-ident.clave.gob.es/IdP2/AuthenticateCitizen",
    )
    private const val AIREF_PROFILE_ID = "airef-instancia-general"
    private const val OURENSE_PROFILE_ID = "diputacion-ourense-sede"
    private const val MALAGA_PROFILE_ID = "diputacion-malaga-instancia-general"
    private const val MALAGA_PROFILE_VERSION = 3
    private const val MALAGA_DISPLAY_NAME =
        "Diputación de Málaga — Instancia general — acceso con certificado"
    private const val MALAGA_START_URL =
        "https://sede.malaga.es/instancia-general/nueva-instancia-general/"
    private const val MALAGA_ORIGIN = "https://sede.malaga.es"
    private const val MALAGA_LOCAL_CLAVE_ORIGIN = "https://clave.malaga.es"
    private const val MALAGA_CLIENT_AUTH_SOURCE_URL =
        "https://pasarela.clave.gob.es/Proxy2/ServiceRedirect"
    private const val MALAGA_CLIENT_AUTH_REQUEST_PATH = "/IdP2/AuthenticateCitizen"
    private val MALAGA_EVIDENCE_REVIEWS = mapOf(
        MALAGA_START_URL to LocalDate.parse("2026-09-03"),
        "$MALAGA_LOCAL_CLAVE_ORIGIN/clave.php" to LocalDate.parse("2026-09-03"),
        "$AIREF_CLAVE_ORIGIN/Proxy2/ServiceProvider" to LocalDate.parse("2026-09-03"),
        MALAGA_CLIENT_AUTH_SOURCE_URL to LocalDate.parse("2026-09-03"),
        "$AIREF_CLIENT_AUTH_ORIGIN$MALAGA_CLIENT_AUTH_REQUEST_PATH" to LocalDate.parse("2026-09-03"),
    )
    private const val SEVILLA_DIPUTACION_PROFILE_ID = "diputacion-sevilla-sede"
    private const val CORUNA_PROFILE_ID = "diputacion-a-coruna-solicitud-general"
    private const val AVILA_PROFILE_ID = "diputacion-avila-instancia-general"
    private const val CADIZ_PROFILE_ID = "diputacion-cadiz-solicitud-generica"
    private const val GUADALAJARA_PROFILE_ID = "diputacion-guadalajara-instancia-general"
    private const val SEGOVIA_PROFILE_ID = "diputacion-segovia-registro"
    private const val PALENCIA_PROFILE_ID = "diputacion-palencia-solicitud-general"
    private const val EL_HIERRO_PROFILE_ID = "el-hierro-solicitud-general"
    private const val EL_HIERRO_PUBLIC_NAVIGATION_PROFILE_ID = "el-hierro-sede-public-navigation"
    private const val EL_HIERRO_PUBLIC_ORIGIN = "https://elhierro.sedelectronica.es"
    private const val LA_GOMERA_PROFILE_ID = "la-gomera-instancia-general"
    private const val LA_GOMERA_PUBLIC_NAVIGATION_PROFILE_ID = "la-gomera-sede-public-navigation"
    private const val LA_GOMERA_PUBLIC_ORIGIN = "https://lagomera.sedelectronica.es"
    private const val LANZAROTE_PROFILE_ID = "lanzarote-instancia-general"
    private const val LANZAROTE_PUBLIC_NAVIGATION_PROFILE_ID = "lanzarote-sede-public-navigation"
    private const val LANZAROTE_PUBLIC_REDIRECT_ORIGIN = "https://lanzaroteylagraciosa.sedelectronica.es"
    private const val CTBG_PROFILE_ID = "ctbg-solicitud-informacion"
    private const val CATASTRO_PROFILE_ID = "catastro-solicitudes-genericas"
    private const val FEGA_PROFILE_ID = "fega-solicitud-general-ofvsg02"
    private const val CACERES_PROFILE_ID = "diputacion-caceres-instancia-general"
    private const val AIREF_PROFILE_VERSION = 1
    private const val AIREF_DISPLAY_NAME = "AIReF — Instancia General"
    private const val AIREF_START_URL =
        "https://sede.airef.es/invesiteRE/action/inicio?authMethod=Clave&organismo=AIREF&tramite=AF-01"
    private const val AIREF_ORIGIN = "https://sede.airef.es"
    private const val AIREF_CLAVE_ORIGIN = "https://pasarela.clave.gob.es"
    private const val AIREF_CLIENT_AUTH_ORIGIN = "https://pasarela-ident.clave.gob.es"
    private const val AIREF_CLIENT_AUTH_SOURCE_URL = "https://pasarela.clave.gob.es/Proxy2/ServiceProvider"
    private const val AIREF_CLIENT_AUTH_REQUEST_PATH = "/IdP2/AuthenticateCitizen"
    private const val AIREF_SAFE_DESCRIPTION = "Firma de la solicitud de Instancia General de la AIReF"
    private val AIREF_EVIDENCE_URLS = setOf(
        "https://sede.airef.es/catalogo-de-tramites-es/instancia-general-es/",
        AIREF_START_URL,
        "https://sede.airef.es/invesiteRE/scripts/afirma/miniapplet.js",
    )
    private const val DIPUTACION_BARCELONA_2057_PROFILE_ID = "diputacion-barcelona-solicitud-generica-2057"
    private const val CATALUNYA_PROFILE_ID = "catalunya-peticio-generica-client-auth"
    private const val CATALUNYA_PROFILE_VERSION = 1
    private const val CATALUNYA_DISPLAY_NAME =
        "Generalitat de Catalunya — Petició genèrica — acceso con certificado"
    private const val CATALUNYA_START_URL =
        "https://tramits.gencat.cat/ca/tramits/tramits-temes/Peticio-generica?" +
            "category=72461610-a82c-11e3-a972-000c29052e2c"
    private const val CATALUNYA_PROTECTED_URL =
        "https://ovt.gencat.cat/gsitgf/AppJava/traint/renderitzar.do?" +
            "reqCode=inicial&set-locale=ca_ES&idioma=ca_ES&idServei=ING001HTM2&" +
            "urlRetorn=https%3A%2F%2Ftramits.gencat.cat%2Fca%2Ftramits%2Ftramits-temes%2F" +
            "Peticio-generica%3Fcategory%3D72461610-a82c-11e3-a972-000c29052e2c"
    private const val CATALUNYA_PUBLIC_ORIGIN = "https://tramits.gencat.cat"
    private const val CATALUNYA_OVT_ORIGIN = "https://ovt.gencat.cat"
    private const val CATALUNYA_VALID_ORIGIN = "https://valid.aoc.cat"
    private const val CATALUNYA_CLAVE_ORIGIN = "https://pasarela.clave.gob.es"
    private const val CATALUNYA_CLIENT_AUTH_ORIGIN = "https://pasarela-ident.clave.gob.es"
    private const val CATALUNYA_CLIENT_AUTH_SOURCE_URL = "https://pasarela.clave.gob.es/Proxy2/ServiceProvider"
    private const val CATALUNYA_CLIENT_AUTH_REQUEST_PATH = "/IdP2/AuthenticateCitizen"
    private val CATALUNYA_EVIDENCE_URLS = setOf(
        "https://tramits.gencat.cat/ca/tramits/tramits-temes/Peticio-generica?" +
            "category=72461610-a82c-11e3-a972-000c29052e2c",
        CATALUNYA_START_URL,
        CATALUNYA_PROTECTED_URL,
        CATALUNYA_CLIENT_AUTH_SOURCE_URL,
        "$CATALUNYA_CLIENT_AUTH_ORIGIN$CATALUNYA_CLIENT_AUTH_REQUEST_PATH",
    )
    private const val CATALUNYA_SEU_PROFILE_ID = "catalunya-seu-registre-client-auth"
    private const val CATALUNYA_SEU_PROFILE_VERSION = 1
    private const val CATALUNYA_SEU_DISPLAY_NAME =
        "Generalitat de Catalunya — Registre electrònic — acceso con certificado"
    private const val CATALUNYA_SEU_START_URL =
        "https://web.gencat.cat/ca/seu-electronica/serveis-de-la-seu/registre-electronic/"
    private const val CATALUNYA_SEU_ORIGIN = "https://web.gencat.cat"
    private const val CATALUNYA_TRAMITS_ORIGIN = "https://tramits.gencat.cat"
    private const val CATALUNYA_CERT_ORIGIN = "https://cert.valid.aoc.cat"
    private const val CATALUNYA_CERT_REQUEST_PATH = "/o/oauth2/cert"
    private const val CATALUNYA_VALID_SOURCE_URL =
        "https://valid.aoc.cat/o/oauth2/auth?lang=ca&scope=autenticacio_usuari&state=state&" +
            "redirect_uri=https%3A%2F%2Fovt.gencat.cat%2Fgsitfc%2FAppJava%2Fredirectservlet&" +
            "response_type=code&client_id=gsit.gencat.cat&approval_prompt=auto"
    private const val CATALUNYA_PETICIO_URL =
        "https://tramits.gencat.cat/ca/tramits/tramits-temes/Peticio-generica?" +
            "category=72461610-a82c-11e3-a972-000c29052e2c"
    private const val CATALUNYA_OVT_SIGNED_URL =
        "https://ovt.gencat.cat/gsitgf/AppJava/traint/renderitzar.do?" +
            "reqCode=inicial&set-locale=ca_ES&idioma=ca_ES&idServei=ING001HTM2&" +
            "urlRetorn=https%3A%2F%2Ftramits.gencat.cat%2Fca%2Ftramits%2Ftramits-temes%2F" +
            "Peticio-generica%3Fcategory%3D72461610-a82c-11e3-a972-000c29052e2c"
    private val CATALUNYA_SEU_EVIDENCE_URLS = setOf(
        CATALUNYA_SEU_START_URL,
        CATALUNYA_PETICIO_URL,
        CATALUNYA_OVT_SIGNED_URL,
        "https://valid.aoc.cat/o/oauth2/auth",
        "https://valid.aoc.cat/o/oauth2/js/login.js",
        "$CATALUNYA_CERT_ORIGIN$CATALUNYA_CERT_REQUEST_PATH",
    )
    private const val POLICIA_PROFILE_ID = "policia-solicitud-generica"
    private const val POLICIA_PROFILE_VERSION = 1
    private const val POLICIA_DISPLAY_NAME = "Policía Nacional — Solicitud genérica"
    private const val POLICIA_START_URL = "https://sede.policia.gob.es/"
    private const val POLICIA_ORIGIN = "https://sede.policia.gob.es"
    private const val POLICIA_SAFE_DESCRIPTION =
        "Firma de solicitud en la Sede de la Policía Nacional"
    private val POLICIA_FIXED_EXTRA_PROPERTIES = linkedMapOf(
        "format" to "XAdES Detached",
        "filters.1" to "dnie:;nonexpired:",
        "filters.2" to "keyusage.nonrepudiation:true;nonexpired:",
    )
    private val TENERIFE_EVIDENCE_URLS = setOf(
        TENERIFE_START_URL,
        "https://sede.tenerife.es/76.81426d6ba0b90ca6.js",
    )
    private const val MELILLA_PROFILE_ID = "melilla-sede"
    private const val MELILLA_PROFILE_VERSION = 1
    private const val MELILLA_DISPLAY_NAME = "Ciudad Autónoma de Melilla — Sede Electrónica"
    private const val MELILLA_START_URL =
        "https://sede.melilla.es/sta/CarpetaPublic/doEvent?" +
            "APP_CODE=STA&PAGE_CODE=CATALOGO&DETALLE=6269000018479610199999"
    private const val MELILLA_ORIGIN = "https://sede.melilla.es"
    private const val MELILLA_SAFE_DESCRIPTION = "Firma por lotes en la Sede Electrónica de Melilla"
    private const val EXTREMADURA_PROFILE_ID = "extremadura-tramites"
    private const val EXTREMADURA_PROFILE_VERSION = 1
    private const val EXTREMADURA_DISPLAY_NAME = "Junta de Extremadura — Trámites"
    private const val EXTREMADURA_START_URL = "https://tramites.juntaex.es/"
    private const val EXTREMADURA_ORIGIN = "https://tramites.juntaex.es"
    private const val EXTREMADURA_SAFE_DESCRIPTION =
        "Firma por lotes en Trámites de la Junta de Extremadura"
    private const val PATTEX_PROFILE_ID = "extremadura-pattex-client-auth"
    private const val PATTEX_PROFILE_VERSION = 1
    private const val PATTEX_DISPLAY_NAME = "Junta de Extremadura — PATTEX acceso con certificado"
    private const val PATTEX_START_URL =
        "https://pattex.juntaex.es/PATTEX/externos.jsf?" +
            "info=060~user~pass~SEDE_ALTA~https://pattex.juntaex.es~codigo"
    private const val PATTEX_ORIGIN = "https://pattex.juntaex.es"
    private const val PATTEX_CLIENT_AUTH_PATH = "/PATTEX/accesoCertificadoSEDE.jsf"
    private val PATTEX_EVIDENCE_URLS = setOf(
        "https://portaltributario.juntaex.es/PortalTributario/web/guest/requisitos-tecnicos",
        PATTEX_START_URL,
        "https://pattex.juntaex.es/PATTEX/accesoCertificadoSEDE.jsf",
    )
    private const val LA_PALMA_PROFILE_ID = "la-palma-sede-electronica"
    private const val LA_PALMA_PROFILE_VERSION = 1
    private const val LA_PALMA_DISPLAY_NAME = "Cabildo Insular de La Palma — Sede electrónica"
    private const val LA_PALMA_START_URL = "https://sedeelectronica.cabildodelapalma.es/"
    private const val LA_PALMA_ORIGIN = "https://sedeelectronica.cabildodelapalma.es"
    private const val LA_PALMA_SAFE_DESCRIPTION =
        "Firma por lotes en la Sede electrónica del Cabildo Insular de La Palma"
    private const val BURGOS_PROFILE_ID = "diputacion-burgos-portal"
    private const val BURGOS_PROFILE_VERSION = 1
    private const val BURGOS_DISPLAY_NAME = "Diputación Provincial de Burgos — Registro electrónico"
    private const val BURGOS_START_URL =
        "https://registro.diputaciondeburgos.es/sta/CarpetaPublic/doEvent?APP_CODE=STA" +
            "&DETALLE=6269000968832920507194&PAGE_CODE=CATALOGO"
    private const val BURGOS_ORIGIN = "https://registro.diputaciondeburgos.es"
    private const val BURGOS_SAFE_DESCRIPTION =
        "Firma por lotes de Instancia Genérica en el Registro electrónico de la Diputación Provincial de Burgos"
    private const val HUESCA_PROFILE_ID = "diputacion-huesca-portal"
    private const val HUESCA_PROFILE_VERSION = 1
    private const val HUESCA_DISPLAY_NAME = "Diputación Provincial de Huesca — Oficina Virtual"
    private const val HUESCA_START_URL =
        "https://ovc24.dphuesca.es/sta/CarpetaPublic/doEvent?APP_CODE=STA&PAGE_CODE=OVC_HOME"
    private const val HUESCA_ORIGIN = "https://ovc24.dphuesca.es"
    private const val HUESCA_SAFE_DESCRIPTION =
        "Firma por lotes en la Oficina Virtual de la Diputación Provincial de Huesca"
    private const val LUGO_PROFILE_ID = "diputacion-lugo-sede"
    private const val LUGO_PROFILE_VERSION = 1
    private const val LUGO_DISPLAY_NAME = "Deputación de Lugo — Sede electrónica"
    private const val LUGO_START_URL =
        "https://sede.deputacionlugo.org/opencms/system/modules/gsede/elements/seccion" +
            "es/autenticacion/autenticacion.jsp"
    private const val LUGO_ORIGIN = "https://sede.deputacionlugo.org"
    private const val LUGO_SAFE_DESCRIPTION =
        "Acceso con certificado mediante lote XML clientSigner de la Sede de la Deputación de Lugo"

    private const val CAIB_PROFILE_ID = "caib-portafib"
    private const val CAIB_PROFILE_VERSION = 1
    private const val CAIB_DISPLAY_NAME = "Govern de les Illes Balears — Instància genèrica"
    private const val CAIB_START_URL =
        "https://www.caib.es/sistramitfront/asistente/iniciarTramite.html?tramite=CAIB" +
            ".SIMPL_DOC.INSTANCIA_GENERICA_SR&version=1&idioma=es&servicioCatalogo=fal" +
            "se&idTramiteCatalogo=4213963&parametros="
    private const val CAIB_PUBLIC_ORIGIN = "https://www.caib.es"
    private const val CAIB_SIGNING_ORIGIN = "https://intranet.caib.es"
    private const val CAIB_SAFE_DESCRIPTION = "Firma PAdES de la Instància genèrica mediante PortaFIB"
    private val CAIB_EVIDENCE_URLS = setOf(CAIB_START_URL, "https://intranet.caib.es/portafibback/")

    private const val LEON_PROFILE_ID = "diputacion-leon-sede"
    private const val LEON_PROFILE_VERSION = 1
    private const val LEON_DISPLAY_NAME = "Diputación Provincial de León — acceso con certificado"
    private const val LEON_START_URL =
        "https://sede.dipuleon.es/carpetaciudadana/tramite.aspx?idtramite=20270"
    private const val LEON_ORIGIN = "https://sede.dipuleon.es"
    private const val LEON_SOURCE_URL =
        "https://sede.dipuleon.es/segex/identificacion_opciones.aspx"
    private const val LEON_CLIENT_AUTH_ORIGIN = "https://identificacionssl.sedipualba.es"
    private val LEON_EVIDENCE_URLS = setOf(
        LEON_START_URL,
        "https://sede.dipuleon.es/carpetaciudadana/login.aspx",
        "https://identificacionssl.sedipualba.es/",
    )
    private const val SEDIPUALBA_CLIENT_AUTH_ORIGIN = "https://identificacionssl.sedipualba.es"
    private const val ALBACETE_PROFILE_ID = "diputacion-albacete-portal"
    private const val ALBACETE_PROFILE_VERSION = 1
    private const val ALBACETE_DISPLAY_NAME = "Diputación Provincial de Albacete — acceso con certificado"
    private const val ALBACETE_START_URL =
        "https://sede.dipualba.es/carpetaciudadana/tramite.aspx?idtramite=567"
    private const val ALBACETE_ORIGIN = "https://sede.dipualba.es"
    private const val ALBACETE_SOURCE_URL =
        "https://sede.dipualba.es/segex/identificacion_opciones.aspx"
    private val ALBACETE_EVIDENCE_URLS = setOf(
        ALBACETE_START_URL,
        "https://sede.dipualba.es/carpetaciudadana/login.aspx",
        ALBACETE_SOURCE_URL,
        "https://identificacionssl.sedipualba.es/",
    )
    private const val MALLORCA_PROFILE_ID = "consell-mallorca-sede"
    private const val MALLORCA_PROFILE_VERSION = 1
    private const val MALLORCA_DISPLAY_NAME = "Consell de Mallorca — acceso con certificado"
    private const val MALLORCA_START_URL =
        "https://cim.secimallorca.net/segex/tramite.aspx?idtramite=12082"
    private const val MALLORCA_ORIGIN = "https://cim.secimallorca.net"
    private const val MALLORCA_SOURCE_URL =
        "https://cim.secimallorca.net/segex/identificacion_opciones.aspx"
    private val MALLORCA_EVIDENCE_URLS = setOf(
        MALLORCA_START_URL,
        "https://cim.secimallorca.net/carpetaciudadana/login.aspx",
        "https://identificacionssl.sedipualba.es/",
    )
    private const val CUENCA_PROFILE_ID = "diputacion-cuenca-portal"
    private const val CUENCA_PROFILE_VERSION = 1
    private const val CUENCA_DISPLAY_NAME = "Diputación Provincial de Cuenca — acceso con certificado"
    private const val CUENCA_START_URL =
        "https://sede.dipucuenca.es/carpetaciudadana/tramite.aspx?idtramite=12074"
    private const val CUENCA_ORIGIN = "https://sede.dipucuenca.es"
    private const val CUENCA_SOURCE_URL =
        "https://sede.dipucuenca.es/segex/identificacion_opciones.aspx"
    private val CUENCA_EVIDENCE_URLS = setOf(
        CUENCA_START_URL,
        "https://sede.dipucuenca.es/carpetaciudadana/login.aspx",
        CUENCA_SOURCE_URL,
        "https://identificacionssl.sedipualba.es/",
    )
    private const val GVA_PROFILE_ID = "generalitat-valenciana-client-auth"
    private const val GVA_PROFILE_VERSION = 1
    private const val GVA_DISPLAY_NAME = "Generalitat Valenciana — acceso con certificado"
    private const val GVA_START_URL =
        "https://www.tramita.gva.es/ctt-att-atr/asistente/iniciarTramite.html?" +
            "tramite=DGM_GEN&version=4&idioma=es&idProcGuc=15602&" +
            "idSubfaseGuc=SOLICITUD&idCatGuc=PR"
    private const val GVA_TRAMITA_ORIGIN = "https://www.tramita.gva.es"
    private const val GVA_PTT_CLAVE_ORIGIN = "https://ptt-clave.gva.es"
    private const val GVA_SOURCE_URL = "https://ptt-clave.gva.es/pttclave/redirigirClave.html"
    private const val GVA_CLIENT_AUTH_ORIGIN = "https://ptt-clave-clientcert.gva.es"
    private const val GVA_CLIENT_AUTH_PATH = "/pttclave/retornoClientCert.html"
    private val GVA_EVIDENCE_URLS = setOf(
        "https://sede.gva.es/es/detall-tramit?id_proc=15602",
        GVA_START_URL,
        GVA_SOURCE_URL,
        "https://ptt-clave-clientcert.gva.es/pttclave/retornoClientCert.html",
    )
    private const val EUSKADI_PROFILE_ID = "euskadi-sede-electronica"
    private const val EUSKADI_PROFILE_VERSION = 1
    private const val EUSKADI_DISPLAY_NAME = "Gobierno Vasco — Registro Electrónico General"
    private const val EUSKADI_ORIGIN = "https://www.euskadi.eus"
    private const val EUSKADI_IZENPE_ORIGIN = "https://eidas.izenpe.com"
    private const val EUSKADI_CLIENT_AUTH_ORIGIN = "https://eidas2.izenpe.com"
    private const val EUSKADI_START_URL =
        "https://www.euskadi.eus/web01-sedeform/es/x43kToolkitWar/form/fdp?" +
            "procedureId=1017701&tipoPresentacion=19&language=es"
    private const val EUSKADI_SOURCE_URL =
        "https://eidas.izenpe.com/trustedx-authserver/izenpe/authentication"
    private const val EUSKADI_CLIENT_AUTH_PATH =
        "/cert-authn-external-validation/authenticate"
    private val EUSKADI_EVIDENCE_URLS = setOf(
        "https://www.euskadi.eus/registro/registro-electronico-general/web01-tramite/es/",
        EUSKADI_START_URL,
        EUSKADI_SOURCE_URL,
        "$EUSKADI_CLIENT_AUTH_ORIGIN$EUSKADI_CLIENT_AUTH_PATH",
    )

    private const val JAEN_PROFILE_ID = "diputacion-jaen-sede"
    private const val JAEN_PROFILE_VERSION = 1
    private const val JAEN_DISPLAY_NAME = "Diputación Provincial de Jaén — acceso con certificado"
    private const val JAEN_START_URL = "https://sede.dipujaen.es/SolicitudGenerica"
    private const val JAEN_ORIGIN = "https://sede.dipujaen.es"
    private const val JAEN_SOURCE_URL = "https://sede.dipujaen.es/IniciarSesion/Certificado"
    private const val JAEN_CLIENT_AUTH_ORIGIN = "https://cert2.dipujaen.es"
    private val JAEN_EVIDENCE_URLS = setOf(
        JAEN_START_URL,
        "https://sede.dipujaen.es/IniciarSesion",
        JAEN_SOURCE_URL,
        "$JAEN_CLIENT_AUTH_ORIGIN/",
    )
    private const val TRANSPORTES_PROFILE_ID = "transportes-qys-cert-login"
    private const val SEPES_TRANSPORTES_PROFILE_ID = "sepes-transportes-public-complaints"
    private const val TRANSPORTES_SHARED_ORIGIN = "https://sede.transportes.gob.es"
    private const val TRANSPORTES_PROFILE_VERSION = 1
    private const val TRANSPORTES_DISPLAY_NAME =
        "Ministerio de Transportes y Movilidad Sostenible — Quejas y Sugerencias"
    private const val TRANSPORTES_START_URL =
        "https://sede.transportes.gob.es/MFOM.genericprocedure.web/?id=7002"
    private const val TRANSPORTES_ORIGIN = "https://sede.transportes.gob.es"
    private const val TRANSPORTES_SAFE_DESCRIPTION =
        "Acceso con certificado a Quejas y Sugerencias del Ministerio de Transportes"
    private val TRANSPORTES_FIXED_EXTRA_PROPERTIES = linkedMapOf(
        "format" to "XAdES Enveloped",
        "includeOnlySigningCertificate" to "true",
        "nodeToSign" to "tag1",
        "applySystemDate" to "false",
        "filters.1" to "keyusage.digitalsignature:true;nonexpired:",
        "sticky" to "true",
    )
    private val TRANSPORTES_EVIDENCE_URLS = setOf(
        "https://sede.transportes.gob.es/proc-servicios-comunes/presentacion-quejas-su" +
            "gerencias-ambito-ministerio-transportes-movilidad-sostenible",
        "https://sede.transportes.gob.es/MFOM.genericprocedure.web/Autenticacion.aspx",
        "https://sede.transportes.gob.es/CIM/js/CIM_Functions.js",
        "https://sede.transportes.gob.es/CIM/js/CIM_Classes.js",
        "https://sede.transportes.gob.es/CIM/js/CIM_Constants.js",
    )
    private const val CDTI_PROFILE_ID = "cdti-certificate-validation"
    private const val CDTI_PROFILE_VERSION = 1
    private const val CDTI_DISPLAY_NAME = "CDTI — Validación de certificado digital"
    private const val CDTI_START_URL =
        "https://sede.cdti.gob.es/AreaPrivada/Expedientes/Common/Certificados/ValidarCertificado.aspx"
    private const val CDTI_ORIGIN = "https://sede.cdti.gob.es"
    private const val CDTI_SAFE_DESCRIPTION = "Validación de certificado digital en CDTI"
    private val CDTI_FIXED_EXTRA_PROPERTIES = linkedMapOf("filters" to "nonexpired")
    private const val SEVILLA_ATSE_PROFILE_ID = "sevilla-atse-certificate-login"
    private const val SEVILLA_ATSE_PROFILE_VERSION = 1
    private const val SEVILLA_ATSE_DISPLAY_NAME =
        "Agencia Tributaria de Sevilla — Acceso con certificado"
    private const val SEVILLA_ATSE_START_URL =
        "https://www.sevilla.org/ovweb/ov-web-certificado/index.xhtml?modo=Contribuyente"
    private const val SEVILLA_ATSE_ORIGIN = "https://www.sevilla.org"
    private const val SEVILLA_ATSE_SAFE_DESCRIPTION =
        "Acceso con certificado a la Agencia Tributaria de Sevilla"
    private const val CANTABRIA_PROFILE_ID = "cantabria-rec-cert-login"
    private const val CANTABRIA_PROFILE_VERSION = 1
    private const val CANTABRIA_DISPLAY_NAME =
        "Registro Electrónico Común de Cantabria — Acceso con certificado"
    private const val CANTABRIA_START_URL = "https://rec.cantabria.es/rec/bienvenida.htm"
    private const val CANTABRIA_ORIGIN = "https://rec.cantabria.es"
    private const val CANTABRIA_SAFE_DESCRIPTION =
        "Acceso con certificado al Registro Electrónico Común de Cantabria"
    private val CANTABRIA_EXTRA_PROPERTIES = linkedMapOf(
        "filters" to "",
        "mode" to "implicit",
    )
    private const val MITES_PROFILE_ID = "mites-certificate-login"
    private const val MITES_PROFILE_VERSION = 1
    private const val MITES_DISPLAY_NAME = "Ministerio de Trabajo y Economía Social — Acceso con certificado"
    private const val MITES_START_URL = "https://sede.mites.gob.es/"
    private const val MITES_ORIGIN = "https://sede.mites.gob.es"
    private const val MITES_SAFE_DESCRIPTION = "Acceso con certificado a la Sede del Ministerio de Trabajo"
    private val MITES_EXTRA_PROPERTIES = linkedMapOf(
        "mode" to "implicit",
        "filters.1" to "signingCert:;keyusage.nonrepudiation:true;nonexpired:",
    )
    private val MITES_EVIDENCE_URLS = setOf(
        "https://sede.mites.gob.es/inicio/detalleProcedimiento/38",
        "https://sede.mites.gob.es/nuevasede-ciudadano/api/public/procedimientos/38",
        "https://sede.mites.gob.es/auth.component-3JUEHJQO.js",
        "https://sede.mites.gob.es/chunk-MX4YJU4O.js",
    )
    private const val UGR_PROFILE_ID = "ugr-certificado-login"
    private const val JCCM_REGISTRO_PROFILE_ID = "jccm-registro-generico"
    private const val JCCM_REGISTRO_PROFILE_VERSION = 1
    private const val JCCM_REGISTRO_DISPLAY_NAME = "JCCM — Registro Electrónico / Solicitud Genérica"
    private const val JCCM_REGISTRO_START_URL =
        "https://registrounicociudadanos.jccm.es/registrounicociudadanos/acceso.do?id=SJLZ"
    private const val JCCM_REGISTRO_ORIGIN = "https://registrounicociudadanos.jccm.es"
    private const val JCCM_REGISTRO_SAFE_DESCRIPTION =
        "Firma del resumen XML de la Solicitud Genérica de JCCM"
    private const val JCCM_REGISTRO_CLIENT_AUTH_SOURCE_URL =
        "https://pasarela.clave.gob.es/Proxy2/ServiceRedirect"
    private const val JCCM_REGISTRO_CLIENT_AUTH_PATH = "/IdP2/AuthenticateCitizen"
    private val JCCM_REGISTRO_REDIRECT_ORIGINS = setOf(
        ExactOrigin.parse("https://sso.jccm.es"),
        ExactOrigin.parse(AIREF_CLAVE_ORIGIN),
    )
    private val JCCM_REGISTRO_FIXED_EXTRA_PROPERTIES = linkedMapOf(
        "format" to "XAdES Detached",
        "mode" to "implicit",
    )
    private const val JCCM_PROFILE_ID = "jccm-certificate-login-probe"
    private const val JCCM_PROFILE_VERSION = 1
    private const val JCCM_DISPLAY_NAME =
        "Junta de Comunidades de Castilla-La Mancha — Probe de acceso con certificado"
    private const val JCCM_START_URL =
        "https://ventanillaelectronica.jccm.es/administracion_electronica/formularios/identificacion.phtml"
    private const val JCCM_ORIGIN = "https://ventanillaelectronica.jccm.es"
    private const val JCCM_SAFE_DESCRIPTION =
        "Validación pública de acceso con certificado de Castilla-La Mancha"
    private const val UGR_PROFILE_VERSION = 1
    private const val UGR_DISPLAY_NAME = "Universidad de Granada — Acceso con certificado"
    private const val UGR_START_URL = "https://sede.ugr.es/Hades/jsp/pantallacertificado.jsp"
    private const val UGR_ORIGIN = "https://sede.ugr.es"
    private const val UGR_SAFE_DESCRIPTION = "Acceso con certificado a la Universidad de Granada"
    private const val ACCEDA_PROFILE_ID = "age-acceda"
    private const val ACCEDA_PROFILE_VERSION = 1
    private const val ACCEDA_DISPLAY_NAME = "Plataforma ACCEDA — Sede electrónica"
    private const val ACCEDA_START_URL =
        "https://sede.administracionespublicas.gob.es/certificado/info/idp/82/ida/0/language/es_ES"
    private const val ACCEDA_ORIGIN = "https://sede.administracionespublicas.gob.es"
    private const val ACCEDA_SAFE_DESCRIPTION = "Firma de solicitud en Plataforma ACCEDA"
    private val ACCEDA_FIXED_EXTRA_PROPERTIES = mapOf(
        "format" to "PAdES Detached",
        "expPolicy" to "FirmaAGE",
        "nonexpired" to "true",
    )

    private fun validateAccedaProfile(profile: SiteProfile) {
        require(profile.profileVersion == ACCEDA_PROFILE_VERSION)
        require(profile.displayName == ACCEDA_DISPLAY_NAME)
        require(profile.compatibilityStatus == CompatibilityStatus.VERIFIED_CONTRACT)
        require(profile.activation == ProfileActivation.QA_ONLY)
        require(profile.startUrl.toASCIIString() == ACCEDA_START_URL)
        require(profile.initiatorOrigins == setOf(ExactOrigin.parse(ACCEDA_ORIGIN)))
        require(profile.redirectOrigins.isEmpty())
        require(profile.trustedBrowseOrigins.isEmpty())
        require(profile.endpoints.isEmpty())
        require(profile.capabilities == setOf(Capability.SIGN, Capability.LEGACY_SHA1))
        require(profile.clientAuthPolicy == null)
        require(profile.certificateRules == CertificateFilterRules(setOf("RSA"), true))
        require(profile.evidence.isNotEmpty())
        require(profile.operationPolicies.keys == setOf(ProtocolOperation.SIGN))
        require(
            profile.operationPolicies.getValue(ProtocolOperation.SIGN) == OperationPolicy(
                operation = ProtocolOperation.SIGN,
                safeDescription = ACCEDA_SAFE_DESCRIPTION,
                inputAdapterId = ProtocolInputAdapterId("miniapplet-autoscript-v1"),
                callbackContractId = CallbackContractId("miniapplet-sign-callback-v1"),
                capabilities = setOf(Capability.SIGN, Capability.LEGACY_SHA1),
                endpointId = null,
                algorithms = setOf(SignatureAlgorithm.SHA1_WITH_RSA),
                format = SignatureFormat.PADES,
                packaging = SignaturePackaging.DETACHED,
                mode = null,
                fixedExtraProperties = ACCEDA_FIXED_EXTRA_PROPERTIES,
                allowedExtraProperties = emptySet(),
            ),
        )
    }
}

private sealed interface JValue {
    fun obj(label: String) = this as? JObject ?: error("$label must be object")
    fun string() = (this as? JString)?.value ?: error("string required")
}
private data class JObject(val values: LinkedHashMap<String, JValue>) : JValue {
    fun exact(vararg keys: String) { require(values.keys == keys.toSet()) }
    fun string(key: String) = required(key).string()
    fun nullableString(key: String) = when (val v = required(key)) { JNull -> null; else -> v.string() }
    fun int(key: String): Int = (required(key) as? JNumber)?.value?.toIntExact() ?: error("integer required")
    fun boolean(key: String) = (required(key) as? JBoolean)?.value ?: error("boolean required")
    fun array(key: String) = (required(key) as? JArray)?.values ?: error("array required")
    fun objValue(key: String) = required(key)
    fun nullableObject(key: String) = when (val v = required(key)) {
        JNull -> null
        is JObject -> v
        else -> error("object required")
    }
    private fun required(key: String) = requireNotNull(values[key])
}
private data class JArray(val values: List<JValue>) : JValue
private data class JString(val value: String) : JValue
private data class JNumber(val value: String) : JValue
private data class JBoolean(val value: Boolean) : JValue
private data object JNull : JValue
private fun String.toIntExact(): Int? = toIntOrNull()?.takeIf { it.toString() == this }

private const val MAX_JSON_DEPTH = 32
private const val JSON_CONTROL_CHAR_LIMIT = 0x20
private const val JSON_UNICODE_ESCAPE_DIGITS = 4
private const val JSON_HEX_RADIX = 16

private class StrictJson(private val source: String) {
    private var index = 0
    fun parse(): JValue = value(0).also { whitespace(); require(index == source.length) }
    private fun value(depth: Int): JValue {
        require(depth <= MAX_JSON_DEPTH); whitespace(); require(index < source.length)
        return when (source[index]) {
            '{' -> obj(depth + 1); '[' -> array(depth + 1); '"' -> JString(string())
            't' -> literal("true", JBoolean(true)); 'f' -> literal("false", JBoolean(false))
            'n' -> literal("null", JNull); '-', in '0'..'9' -> number()
            else -> error("invalid JSON")
        }
    }
    private fun obj(depth: Int): JObject {
        index++; whitespace(); val map = linkedMapOf<String, JValue>()
        if (take('}')) return JObject(map)
        while (true) {
            whitespace(); require(peek() == '"'); val key = string(); require(map[key] == null)
            whitespace(); require(take(':')); map[key] = value(depth); whitespace()
            if (take('}')) return JObject(map)
            require(take(','))
        }
    }
    private fun array(depth: Int): JArray {
        index++; whitespace(); val list = mutableListOf<JValue>()
        if (take(']')) return JArray(list)
        while (true) {
            list += value(depth); whitespace()
            if (take(']')) return JArray(list)
            require(take(','))
        }
    }
    private fun string(): String {
        require(take('"')); val out = StringBuilder()
        while (index < source.length) {
            val c = source[index++]; when {
                c == '"' -> return out.toString()
                c == '\\' -> { require(index < source.length); when (val e = source[index++]) {
                    '"', '\\', '/' -> out.append(e); 'b' -> out.append('\b'); 'f' -> out.append('\u000c')
                    'n' -> out.append('\n'); 'r' -> out.append('\r'); 't' -> out.append('\t')
                    'u' -> {
                        require(index + JSON_UNICODE_ESCAPE_DIGITS <= source.length)
                        val hex = source.substring(index, index + JSON_UNICODE_ESCAPE_DIGITS)
                        require(hex.all { it.isDigit() || it.lowercaseChar() in 'a'..'f' })
                        out.append(hex.toInt(JSON_HEX_RADIX).toChar())
                        index += JSON_UNICODE_ESCAPE_DIGITS
                    }
                    else -> error("invalid escape")
                } }
                c.code < JSON_CONTROL_CHAR_LIMIT -> error("control in string")
                else -> out.append(c)
            }
        }; error("unterminated string")
    }
    private fun number(): JNumber {
        val start = index; if (take('-')) require(index < source.length)
        if (take('0')) require(index == source.length || source[index] !in '0'..'9')
        else {
            require(index < source.length && source[index] in '1'..'9')
            while (index < source.length && source[index] in '0'..'9') index++
        }
        require(index == source.length || source[index] !in charArrayOf('.', 'e', 'E'))
        return JNumber(source.substring(start, index))
    }
    private fun <T : JValue> literal(text: String, value: T): T {
        require(source.startsWith(text, index)); index += text.length
        return value
    }
    private fun whitespace() {
        while (index < source.length && source[index] in charArrayOf(' ', '\n', '\r', '\t')) index++
    }
    private fun take(c: Char) = if (index < source.length && source[index] == c) { index++; true } else false
    private fun peek() = source[index]
}

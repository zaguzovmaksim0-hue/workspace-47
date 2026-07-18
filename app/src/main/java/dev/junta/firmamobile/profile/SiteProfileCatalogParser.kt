package dev.junta.firmamobile.profile

import java.net.URI
import java.time.LocalDate

object SiteProfileCatalogParser {
    const val MAX_CATALOG_CHARS = 262_144

    fun parse(json: String): SiteProfileCatalog {
        require(json.length <= MAX_CATALOG_CHARS)
        val root = StrictJson(json).parse().obj("catalog")
        root.exact("schemaVersion", "catalogVersion", "profiles")
        val schemaVersion = root.int("schemaVersion")
        require(schemaVersion == 1)
        val catalog = SiteProfileCatalog(
            schemaVersion = schemaVersion,
            catalogVersion = root.int("catalogVersion").also { require(it >= 1) },
            profiles = root.array("profiles").map(::profile),
        )
        require(catalog.profiles.map { it.profileId }.toSet().size == catalog.profiles.size)
        validateCatalog(catalog)
        return catalog
    }

    private fun profile(value: JValue): SiteProfile {
        val o = value.obj("profile")
        o.exact(
            "profileId", "profileVersion", "displayName", "compatibilityStatus", "activation",
            "startUrl", "initiatorOrigins", "redirectOrigins", "trustedBrowseOrigins", "endpoints",
            "operationPolicies", "capabilities", "clientAuthPolicy", "certificateRules", "evidence",
        )
        val endpoints = o.array("endpoints").map(::endpoint)
        require(endpoints.map { it.endpointId }.toSet().size == endpoints.size)
        val operations = o.array("operationPolicies").map(::operation)
        require(operations.map { it.operation }.toSet().size == operations.size)
        return SiteProfile(
            profileId = ProfileId(o.string("profileId")),
            profileVersion = o.int("profileVersion").also { require(it >= 1) },
            displayName = o.string("displayName").also { require(it.isNotBlank() && it.length <= 128) },
            compatibilityStatus = enum(o.string("compatibilityStatus")),
            activation = enum(o.string("activation")),
            startUrl = strictHttpsUrl(o.string("startUrl")),
            initiatorOrigins = origins(o.array("initiatorOrigins")),
            redirectOrigins = origins(o.array("redirectOrigins")),
            trustedBrowseOrigins = origins(o.array("trustedBrowseOrigins")),
            endpoints = endpoints.associateBy { it.endpointId },
            operationPolicies = operations.associateBy { it.operation },
            capabilities = enums(o.array("capabilities")),
            clientAuthPolicy = o.nullableObject("clientAuthPolicy")?.let(::clientAuth),
            certificateRules = certificateRules(o.objValue("certificateRules")),
            evidence = o.array("evidence").map(::evidence),
        )
    }

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

    private fun operation(value: JValue): OperationPolicy {
        val o = value.obj("operationPolicy")
        o.exact(
            "operation", "safeDescription", "inputAdapterId", "callbackContractId", "capabilities", "endpointId",
            "algorithms", "format", "packaging", "mode", "fixedExtraProperties",
            "allowedExtraProperties",
        )
        return OperationPolicy(
            operation = enum(o.string("operation")),
            safeDescription = o.string("safeDescription").also {
                require(it.isNotBlank() && it.length <= 160 && it.all { character -> !character.isISOControl() })
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
            fixedExtraProperties = stringMap(o.objValue("fixedExtraProperties")),
            allowedExtraProperties = strings(o.array("allowedExtraProperties")),
        )
    }

    private fun clientAuth(o: JObject): ClientAuthPolicy {
        o.exact(
            "requestOrigins", "sourceUrls", "requestPath", "fixedQueryParameters",
            "requiredEphemeralQueryParameters", "allowEmptyIssuerList", "grantTtlSeconds",
        )
        val fixed = stringMap(o.objValue("fixedQueryParameters"))
        val ephemeral = strings(o.array("requiredEphemeralQueryParameters"))
        require((fixed.keys intersect ephemeral).isEmpty())
        return ClientAuthPolicy(
            requestOrigins = origins(o.array("requestOrigins")).also { require(it.size == 1) },
            sourceUrls = o.array("sourceUrls").map { strictHttpsUrl(it.string()) }.toSet()
                .also { require(it.isNotEmpty() && it.size == o.array("sourceUrls").size) },
            requestPath = o.string("requestPath").also {
                require(it.startsWith('/') && URI(null, null, it, null).rawPath == it)
            },
            fixedQueryParameters = fixed,
            requiredEphemeralQueryParameters = ephemeral.also { require(it.isNotEmpty()) },
            allowEmptyIssuerList = o.boolean("allowEmptyIssuerList"),
            grantTtlSeconds = o.int("grantTtlSeconds").also { require(it in 1..60) },
        )
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

    private fun validateCatalog(catalog: SiteProfileCatalog) {
        val originOwners = mutableMapOf<ExactOrigin, ProfileId>()
        val endpointOwners = mutableMapOf<EndpointId, ProfileId>()
        catalog.profiles.forEach { p ->
            require(p.initiatorOrigins.isNotEmpty())
            require(p.startUrl.origin() in p.initiatorOrigins)
            require((p.initiatorOrigins intersect p.redirectOrigins).isEmpty())
            require((p.initiatorOrigins intersect p.trustedBrowseOrigins).isEmpty())
            require((p.redirectOrigins intersect p.trustedBrowseOrigins).isEmpty())
            val clientAuthOrigins = p.clientAuthPolicy?.requestOrigins ?: emptySet()
            require((clientAuthOrigins intersect p.initiatorOrigins).isEmpty())
            require((clientAuthOrigins intersect p.redirectOrigins).isEmpty())
            require((clientAuthOrigins intersect p.trustedBrowseOrigins).isEmpty())
            if (p.compatibilityStatus == CompatibilityStatus.BROWSE_ONLY ||
                p.compatibilityStatus == CompatibilityStatus.UNSUPPORTED
            ) {
                require(p.operationPolicies.isEmpty() && p.endpoints.isEmpty())
                require(p.capabilities.none {
                    it == Capability.SIGN || it == Capability.SELECT_CERTIFICATE ||
                        it == Capability.CLIENT_TLS_AUTH || it == Capability.AFIRMA_URI
                })
            }
            require(p.compatibilityStatus != CompatibilityStatus.UNSUPPORTED || p.activation == ProfileActivation.DISABLED)
            require(p.activation != ProfileActivation.ENABLED || p.compatibilityStatus != CompatibilityStatus.UNSUPPORTED)
            require(Capability.CLIENT_TLS_AUTH in p.capabilities == (p.clientAuthPolicy != null))
            p.clientAuthPolicy?.let { policy ->
                require(p.operationPolicies.isEmpty() && p.endpoints.isEmpty())
                require(p.capabilities == setOf(Capability.CLIENT_TLS_AUTH))
                require(policy.sourceUrls.all { it.origin() in p.initiatorOrigins })
                require(policy.fixedQueryParameters.keys.all(PARAMETER_NAME::matches))
                require(policy.fixedQueryParameters.values.all { value ->
                    value.length <= 2_048 && value.none(Char::isISOControl)
                })
                require(policy.requiredEphemeralQueryParameters.all(PARAMETER_NAME::matches))
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
                if (op.inputAdapterId.value == "miniapplet-autoscript-v1") {
                    require(op.operation == ProtocolOperation.SIGN)
                    require(op.packaging == SignaturePackaging.DETACHED)
                    require(op.allowedExtraProperties.isEmpty())
                    when (op.format) {
                        SignatureFormat.CADES -> {
                            require(op.endpointId != null)
                            require(op.fixedExtraProperties["serverUrl"] ==
                                op.endpointId.let(p.endpoints::get)?.url?.toString())
                            when (op.mode) {
                                SignatureMode.EXPLICIT -> {
                                    require(op.fixedExtraProperties.keys == setOf("serverUrl", "mode"))
                                    require(op.fixedExtraProperties["mode"] == "explicit")
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
                        SignatureFormat.XADES -> {
                            require(op.endpointId == null && op.mode == null)
                            require(op.algorithms == setOf(SignatureAlgorithm.SHA512_WITH_RSA))
                            require(op.fixedExtraProperties.isEmpty())
                        }
                        SignatureFormat.PADES, SignatureFormat.FACTURAE -> error("unsupported adapter format")
                        null -> error("signing format required")
                    }
                }
            }
            p.endpoints.values.forEach { endpoint ->
                require(endpoint.url.origin() in p.allOrigins())
                require(endpoint.redirects == RedirectPolicy.DENY)
                require(endpointOwners.put(endpoint.endpointId, p.profileId) == null)
            }
            p.allOrigins().forEach { origin -> require(originOwners.put(origin, p.profileId) == null) }
        }
    }

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
    private inline fun <reified T : Enum<T>> enums(values: List<JValue>) =
        values.map { enum<T>(it.string()) }.toSet().also { require(it.size == values.size) }
    private inline fun <reified T : Enum<T>> enum(value: String): T = enumValueOf(value)
    private fun strictHttpsUrl(raw: String): URI {
        require(raw.length <= 2048 && !raw.any(Char::isISOControl))
        val uri = URI(raw)
        require(!uri.isOpaque && uri.scheme == "https" && uri.host != null && uri.userInfo == null)
        require(uri.port == -1 || uri.port == 443)
        require(uri.rawFragment == null)
        val origin = ExactOrigin.parse("https://${uri.host}")
        require(uri.host == origin.host)
        return uri
    }

    private fun validContentType(value: String): Boolean =
        value.length <= 128 && CONTENT_TYPE.matches(value)

    private val REGISTERED_ADAPTERS = setOf("miniapplet-autoscript-v1")
    private val REGISTERED_CALLBACKS = setOf(
        "miniapplet-sign-callback-v1",
        "autoscript-sign-callback-v1",
    )
    private val CONTENT_TYPE = Regex("[A-Za-z0-9!#$&^_.+-]+/[A-Za-z0-9!#$&^_.+-]+(?:; charset=UTF-8)?")
    private val PARAMETER_NAME = Regex("[A-Za-z][A-Za-z0-9_]{0,63}")
    private const val MAX_BODY_BYTES = 8 * 1024 * 1024
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
    fun nullableObject(key: String) = when (val v = required(key)) { JNull -> null; is JObject -> v; else -> error("object required") }
    private fun required(key: String) = requireNotNull(values[key])
}
private data class JArray(val values: List<JValue>) : JValue
private data class JString(val value: String) : JValue
private data class JNumber(val value: String) : JValue
private data class JBoolean(val value: Boolean) : JValue
private data object JNull : JValue
private fun String.toIntExact(): Int? = toIntOrNull()?.takeIf { it.toString() == this }

private class StrictJson(private val source: String) {
    private var index = 0
    fun parse(): JValue = value(0).also { whitespace(); require(index == source.length) }
    private fun value(depth: Int): JValue {
        require(depth <= 32); whitespace(); require(index < source.length)
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
            if (take('}')) return JObject(map); require(take(','))
        }
    }
    private fun array(depth: Int): JArray {
        index++; whitespace(); val list = mutableListOf<JValue>()
        if (take(']')) return JArray(list)
        while (true) { list += value(depth); whitespace(); if (take(']')) return JArray(list); require(take(',')) }
    }
    private fun string(): String {
        require(take('"')); val out = StringBuilder()
        while (index < source.length) {
            val c = source[index++]; when {
                c == '"' -> return out.toString()
                c == '\\' -> { require(index < source.length); when (val e = source[index++]) {
                    '"', '\\', '/' -> out.append(e); 'b' -> out.append('\b'); 'f' -> out.append('\u000c')
                    'n' -> out.append('\n'); 'r' -> out.append('\r'); 't' -> out.append('\t')
                    'u' -> { require(index + 4 <= source.length); val hex = source.substring(index, index + 4); require(hex.all { it.isDigit() || it.lowercaseChar() in 'a'..'f' }); out.append(hex.toInt(16).toChar()); index += 4 }
                    else -> error("invalid escape")
                } }
                c.code < 0x20 -> error("control in string")
                else -> out.append(c)
            }
        }; error("unterminated string")
    }
    private fun number(): JNumber {
        val start = index; if (take('-')) require(index < source.length)
        if (take('0')) require(index == source.length || source[index] !in '0'..'9')
        else { require(index < source.length && source[index] in '1'..'9'); while (index < source.length && source[index] in '0'..'9') index++ }
        require(index == source.length || source[index] !in charArrayOf('.', 'e', 'E'))
        return JNumber(source.substring(start, index))
    }
    private fun <T : JValue> literal(text: String, value: T): T { require(source.startsWith(text, index)); index += text.length; return value }
    private fun whitespace() { while (index < source.length && source[index] in charArrayOf(' ', '\n', '\r', '\t')) index++ }
    private fun take(c: Char) = if (index < source.length && source[index] == c) { index++; true } else false
    private fun peek() = source[index]
}

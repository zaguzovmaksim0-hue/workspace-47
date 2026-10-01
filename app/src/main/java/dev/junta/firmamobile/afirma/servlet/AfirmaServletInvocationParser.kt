package dev.junta.firmamobile.afirma.servlet

import dev.junta.firmamobile.signing.SigningAlgorithm
import java.io.ByteArrayOutputStream
import java.net.URI
import java.nio.ByteBuffer
import java.nio.charset.CodingErrorAction
import java.util.Base64
import java.util.Locale

/** Pure syntax normalization, never origin authorization or network access.
 * Reference: clienteafirma 0d7f3cf01fb65d2be5b245622d2c8f490f36e718,
 * autoscript/UrlParameters and CAdESParameters. Default mode is EXPLICIT.
 */
internal object AfirmaServletInvocationParser {
    fun parse(rawUri: String, pageUrl: String): AfirmaServletParseResult {
        var payload: ByteArray? = null
        var advancedCipher: AfirmaAesParameters? = null
        return try {
            if (rawUri.length > MAX_URI || rawUri.any(Char::isISOControl)) invalid("invalid_uri_size_or_control")
            val page = URI(pageUrl)
            if (!AfirmaEndpointPolicy.accepts(URI(page.scheme, page.userInfo, page.host, page.port, page.path, page.query, null)) || page.rawUserInfo != null) {
                invalid("invalid_source_page")
            }
            val source = URI("https", null, page.host.lowercase(Locale.ROOT), page.port, null, null, null).toASCIIString()
            val uri = URI(rawUri)
            if (!uri.scheme.equals("afirma", true) || uri.isOpaque || uri.rawUserInfo != null || uri.port != -1 ||
                uri.rawFragment != null || uri.rawPath !in setOf("", "/")) invalid("invalid_protocol_uri")
            val op = when (uri.host?.lowercase(Locale.ROOT)) {
                "sign" -> AfirmaServletOperation.SIGN
                "selectcert" -> AfirmaServletOperation.SELECT_CERTIFICATE
                else -> unsupported("operation_not_implemented")
            }
            val values = parameters(uri.rawQuery ?: invalid("missing_parameters"))
            if (values.keys.any { it !in KNOWN_PARAMETERS }) unsupported("unknown_parameter")
            if (values.keys.any { it in setOf("fileid", "rid", "rtservlet", "serverurl", "ksb64", "keystore", "defaultkeystore") }) {
                unsupported("indirect_data_or_cipher_or_keystore_variant")
            }
            values["op"]?.let {
                val expected = if (op == AfirmaServletOperation.SIGN) "sign" else "selectcert"
                if (!it.equals(expected, true)) invalid("conflicting_operation")
            }
            values["cop"]?.let { if (!it.equals("sign", true) || op != AfirmaServletOperation.SIGN) unsupported("cosign_or_countersign") }
            for (name in listOf("ver", "v")) values[name]?.let { if (it !in setOf("1", "2", "3", "4")) unsupported("protocol_version") }
            if (values["ver"] != null && values["v"] != null && values["ver"] != values["v"]) invalid("conflicting_protocol_versions")
            if (!values["mcv"].isNullOrBlank() || !values["minkeysize"].isNullOrBlank()) unsupported("minimum_client_or_key_constraint")
            for (name in listOf("sticky", "resetsticky", "stickycert")) {
                values[name]?.let { if (!it.equals("false", true)) unsupported("sticky_certificate_constraint") }
            }
            for (name in listOf("aw", "dlgload")) values[name]?.let {
                if (!it.equals("true", true) && !it.equals("false", true)) invalid("invalid_boolean_metadata")
            }
            val sessionId = values["id"] ?: invalid("missing_session_id")
            if (!SESSION_ID.matches(sessionId)) invalid("invalid_session_id")
            val endpoint = URI(values["stservlet"] ?: invalid("missing_storage_url"))
            if (!AfirmaEndpointPolicy.accepts(endpoint)) invalid("invalid_storage_url")
            endpoint.rawQuery?.let { query ->
                val names = query.split('&').map { decodeComponent(it.substringBefore('=')).lowercase(Locale.ROOT) }
                if (names.any { it in setOf("op", "v", "id", "dat") }) invalid("ambiguous_storage_parameters")
            }
            val key = values["key"]
            if (key != null && (key.length != 8 || key.any { it.code !in 0x20..0x7e })) invalid("invalid_legacy_key")
            values["cipher"]?.let { config ->
                val bytes = strictBase64(config, 4096)
                val fields = try { AfirmaCipherJson.parse(decodeUtf8(bytes)) } finally { bytes.fill(0) }
                if (fields.keys.any { it !in setOf("algo", "key", "iv", "legDes", "legacydes") }) unsupported("cipher_parameter")
                if (!fields["algo"].equals("AES", true)) unsupported("cipher_algorithm")
                if (fields["legDes"] != null && fields["legacydes"] != null && fields["legDes"] != fields["legacydes"]) {
                    invalid("conflicting_legacy_keys")
                }
                (fields["legDes"] ?: fields["legacydes"])?.let { legacy ->
                    if (legacy.length != 8 || legacy.any { it.code !in 0x20..0x7e } || key != null && key != legacy) invalid("conflicting_legacy_keys")
                }
                val aesKey = strictBase64(fields["key"] ?: invalid("missing_cipher_key"), 32)
                try {
                    val iv = strictBase64(fields["iv"] ?: invalid("missing_cipher_iv"), 16)
                    try { advancedCipher = AfirmaAesParameters(aesKey, iv) } finally { iv.fill(0) }
                } finally { aesKey.fill(0) }
            }
            val properties = values["properties"]?.takeIf(String::isNotEmpty)?.let(::properties).orEmpty()
            val algorithm: SigningAlgorithm?
            val detached: Boolean
            if (op == AfirmaServletOperation.SIGN) {
                if (!values["format"].equals("cades", true)) {
                    if (values["format"].isNullOrBlank()) invalid("missing_format")
                    unsupported("signature_format")
                }
                algorithm = when (values["algorithm"]?.lowercase(Locale.ROOT)) {
                    "sha1withrsa" -> SigningAlgorithm.SHA1_WITH_RSA
                    "sha256withrsa" -> SigningAlgorithm.SHA256_WITH_RSA
                    "sha512withrsa" -> SigningAlgorithm.SHA512_WITH_RSA
                    null, "" -> invalid("missing_algorithm")
                    else -> unsupported("signature_algorithm")
                }
                if (properties.keys.any { it != "mode" }) unsupported("signature_property_not_implemented")
                detached = when (properties["mode"]?.lowercase(Locale.ROOT) ?: "explicit") {
                    "explicit" -> true
                    "implicit" -> false
                    else -> unsupported("signature_mode")
                }
                val encoded = values["dat"] ?: unsupported("interactive_file_selection_required")
                if (encoded.startsWith("http:", true) || encoded.startsWith("https:", true)) unsupported("remote_data")
                payload = strictBase64(encoded, MAX_PAYLOAD)
            } else {
                if (values.keys.any { it in setOf("dat", "format", "algorithm", "cop") }) invalid("unexpected_signing_parameters")
                if (properties.isNotEmpty()) unsupported("certificate_filter_not_implemented")
                algorithm = null
                detached = true
                payload = ByteArray(0)
            }
            AfirmaServletParseResult.Accepted(AfirmaServletInvocation(op, source, endpoint, sessionId, key,
                algorithm, detached, checkNotNull(payload), advancedCipher))
        } catch (error: UnsupportedInput) {
            AfirmaServletParseResult.Unsupported(error.code)
        } catch (error: InvalidInput) {
            AfirmaServletParseResult.Invalid(error.code)
        } catch (_: Exception) {
            AfirmaServletParseResult.Invalid("malformed_input")
        } finally { payload?.fill(0); advancedCipher?.close() }
    }

    private fun parameters(query: String): Map<String, String> {
        val segments = query.split('&')
        if (segments.size > 64) invalid("too_many_parameters")
        val values = linkedMapOf<String, String>()
        for (part in segments) {
            if (part.isEmpty()) invalid("empty_parameter")
            val key = decodeComponent(part.substringBefore('='))
            if (!NAME.matches(key)) invalid("invalid_parameter_name")
            val value = decodeComponent(part.substringAfter('=', ""))
            if (values.put(key, value) != null) invalid("duplicate_parameter")
        }
        return values
    }

    private fun properties(encoded: String): Map<String, String> {
        val bytes = strictBase64(encoded, 16_384)
        val text = try { decodeUtf8(bytes) } finally { bytes.fill(0) }
        if (text.any { it == '\\' || (it.isISOControl() && it !in "\r\n\t") }) unsupported("escaped_or_binary_properties")
        val result = linkedMapOf<String, String>()
        for (line in text.replace("\r\n", "\n").replace('\r', '\n').split('\n')) {
            val clean = line.trim()
            if (clean.isEmpty() || clean.startsWith('#') || clean.startsWith('!')) continue
            val match = PROPERTY.matchEntire(clean) ?: unsupported("property_syntax")
            val key = match.groupValues[1]
            if (result.put(key, match.groupValues[2].trim()) != null) invalid("duplicate_property")
        }
        return result
    }

    private fun decodeComponent(value: String): String {
        val bytes = ByteArrayOutputStream(value.length)
        var index = 0
        while (index < value.length) {
            if (value[index] == '%') {
                if (index + 2 >= value.length) invalid("malformed_percent")
                val first = value[index + 1].digitToIntOrNull(16) ?: invalid("malformed_percent")
                val second = value[index + 2].digitToIntOrNull(16) ?: invalid("malformed_percent")
                bytes.write(first * 16 + second); index += 3
            } else {
                val end = value.indexOf('%', index).let { if (it < 0) value.length else it }
                bytes.write(value.substring(index, end).toByteArray(Charsets.UTF_8)); index = end
            }
        }
        val content = bytes.toByteArray()
        return try { decodeUtf8(content).also { if (it.any(Char::isISOControl)) invalid("parameter_control") } }
        finally { content.fill(0) }
    }

    internal fun strictBase64(value: String, maxBytes: Int): ByteArray {
        if (value.length > ((maxBytes + 2L) / 3L * 4L) || !BASE64.matches(value) || value.length % 4 == 1 ||
            (value.contains('=') && value.length % 4 != 0) ||
            ((value.contains('+') || value.contains('/')) && (value.contains('-') || value.contains('_')))
        ) invalid("invalid_base64")
        val canonical = value.replace('-', '+').replace('_', '/')
        val bytes = Base64.getDecoder().decode(canonical)
        if (bytes.size > maxBytes || Base64.getEncoder().withoutPadding().encodeToString(bytes) != canonical.trimEnd('=')) {
            bytes.fill(0); invalid("invalid_base64")
        }
        return bytes
    }

    private fun decodeUtf8(bytes: ByteArray): String = Charsets.UTF_8.newDecoder()
        .onMalformedInput(CodingErrorAction.REPORT).onUnmappableCharacter(CodingErrorAction.REPORT)
        .decode(ByteBuffer.wrap(bytes)).toString()

    private class InvalidInput(val code: String) : RuntimeException()
    private class UnsupportedInput(val code: String) : RuntimeException()
    private fun invalid(code: String): Nothing = throw InvalidInput(code)
    private fun unsupported(code: String): Nothing = throw UnsupportedInput(code)
    private const val MAX_URI = 1_048_576
    private const val MAX_PAYLOAD = 524_288
    private val NAME = Regex("[A-Za-z][A-Za-z0-9_-]{0,63}")
    private val SESSION_ID = Regex("[A-Za-z0-9_-]{1,128}")
    private val BASE64 = Regex("[A-Za-z0-9+/_-]*={0,2}")
    private val PROPERTY = Regex("([^\\s=:]+)\\s*(?:=|:|\\s)\\s*(.*)")
    private val KNOWN_PARAMETERS = setOf("id", "key", "dat", "properties", "algorithm", "format", "stservlet",
        "fileid", "rid", "cipher", "rtservlet", "serverurl", "op", "cop", "jvc", "ver", "v", "appname", "dlgload", "aw",
        "sticky", "resetsticky", "stickycert", "keystore", "defaultkeystore", "ksb64", "mcv", "minkeysize")
}

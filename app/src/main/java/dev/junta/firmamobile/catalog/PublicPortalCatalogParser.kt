package dev.junta.firmamobile.catalog

import dev.junta.firmamobile.profile.ExactOrigin
import dev.junta.firmamobile.profile.ProfileId
import dev.junta.firmamobile.profile.SignatureFormat
import java.net.URI
import java.time.LocalDate

object PublicPortalCatalogParser {
    const val MAX_CATALOG_CHARS = 1_048_576

    fun parse(json: String): PublicPortalCatalog {
        require(json.length <= MAX_CATALOG_CHARS)
        val root = PublicStrictJson(json).parse().obj("catalog")
        root.exact("schemaVersion", "catalogVersion", "sourceRevision", "entries")
        val entries = root.array("entries").map(::entry)
        val catalog = PublicPortalCatalog(
            schemaVersion = root.int("schemaVersion").also { require(it == 1) },
            catalogVersion = root.int("catalogVersion").also { require(it >= 1) },
            sourceRevision = root.string("sourceRevision").also { require(SHA256.matches(it)) },
            entries = entries,
        )
        require(entries.size >= 150)
        require(entries.map { it.portalId }.toSet().size == entries.size)
        require(entries.mapNotNull { it.inventoryId }.toSet().size == entries.count { it.inventoryId != null })
        require(entries.map { it.entryUrl }.toSet().size == entries.size)
        require(
            entries.filter { it.profileId != null }
                .groupBy { it.profileId }
                .values
                .all { boundEntries ->
                    boundEntries.size == 1 || boundEntries.count { it.launchUrl == null } <= 1
                },
        )
        return catalog
    }

    private fun entry(value: PublicJsonValue): PublicPortalEntry {
        val o = value.obj("entry")
        o.exactWithOptional(
            setOf("launchUrl"),
            "portalId", "inventoryId", "profileId", "displayName", "organization",
            "governmentLevel", "territory", "purpose", "entryUrl", "observedMechanisms",
            "observedSignatureFormats", "protocolFamily", "catalogStatus", "inventoryStatus",
            "discoveryState", "evidenceIds", "reviewedOn", "limitations",
        )
        val profileId = o.nullableString("profileId")?.let(::ProfileId)
        val launchUrl = o.optionalNullableString("launchUrl")?.let(::strictHttpsUrl)
        val catalogStatus = enum<PublicCatalogStatus>(o.string("catalogStatus"))
        if (profileId == null) {
            require(
                launchUrl == null &&
                    (catalogStatus == PublicCatalogStatus.DISCOVERED ||
                        catalogStatus == PublicCatalogStatus.CATALOGED ||
                        catalogStatus == PublicCatalogStatus.BLOCKED ||
                        catalogStatus == PublicCatalogStatus.DEPRECATED),
            )
        }
        return PublicPortalEntry(
            portalId = PortalId(o.string("portalId")),
            inventoryId = o.nullableString("inventoryId")?.also { require(INVENTORY_ID.matches(it)) },
            profileId = profileId,
            displayName = bounded(o.string("displayName"), 160),
            organization = bounded(o.string("organization"), 180),
            governmentLevel = enum(o.string("governmentLevel")),
            territory = bounded(o.string("territory"), 120),
            purpose = bounded(o.string("purpose"), 500),
            entryUrl = strictHttpsUrl(o.string("entryUrl")),
            launchUrl = launchUrl,
            observedMechanisms = enums(o.array("observedMechanisms")),
            observedSignatureFormats = enums<SignatureFormat>(o.array("observedSignatureFormats")),
            protocolFamily = bounded(o.string("protocolFamily"), 200),
            catalogStatus = catalogStatus,
            inventoryStatus = enum(o.string("inventoryStatus")),
            discoveryState = enum(o.string("discoveryState")),
            evidenceIds = strings(o.array("evidenceIds")).also {
                require(it.isNotEmpty() && it.all(EVIDENCE_ID::matches))
            },
            reviewedOn = o.nullableString("reviewedOn")?.let(LocalDate::parse),
            limitations = bounded(o.string("limitations"), 800),
        )
    }

    private fun strictHttpsUrl(raw: String): URI {
        require(raw.length <= 2_048 && !raw.any(Char::isISOControl))
        val uri = URI(raw)
        require(!uri.isOpaque && uri.scheme == "https" && uri.host != null && uri.userInfo == null)
        require(uri.port == -1)
        require(uri.rawFragment == null)
        require(uri.normalize() == uri)
        val origin = ExactOrigin.parse("https://${uri.host}")
        require(uri.host == origin.host)
        return uri
    }

    private fun bounded(value: String, max: Int): String = value.also {
        require(it.isNotBlank() && it.length <= max && !it.any(Char::isISOControl))
    }

    private fun strings(values: List<PublicJsonValue>) = values.map { it.string() }.toSet()
        .also { require(it.size == values.size && it.all(String::isNotBlank)) }
    private inline fun <reified T : Enum<T>> enums(values: List<PublicJsonValue>) =
        values.map { enum<T>(it.string()) }.toSet().also { require(it.size == values.size) }
    private inline fun <reified T : Enum<T>> enum(value: String): T = enumValueOf(value)

    private val SHA256 = Regex("[0-9a-f]{64}")
    private val INVENTORY_ID = Regex("ES-PUB-[0-9]{4}")
    private val EVIDENCE_ID = Regex("[A-Za-z0-9][A-Za-z0-9._:-]{0,127}")
}

private sealed interface PublicJsonValue {
    fun obj(label: String) = this as? PublicJsonObject ?: error("$label must be object")
    fun string() = (this as? PublicJsonString)?.value ?: error("string required")
}

private data class PublicJsonObject(val values: LinkedHashMap<String, PublicJsonValue>) : PublicJsonValue {
    fun exact(vararg keys: String) { require(values.keys == keys.toSet()) }
    fun exactWithOptional(optionalKeys: Set<String>, vararg keys: String) {
        require(values.keys == keys.toSet() || values.keys == keys.toSet() + optionalKeys)
    }
    fun string(key: String) = required(key).string()
    fun nullableString(key: String) = when (val value = required(key)) {
        PublicJsonNull -> null
        else -> value.string()
    }
    fun optionalNullableString(key: String) = values[key]?.let { value ->
        when (value) {
            PublicJsonNull -> null
            else -> value.string()
        }
    }
    fun int(key: String): Int = (required(key) as? PublicJsonNumber)?.value?.toIntExact()
        ?: error("integer required")
    fun array(key: String) = (required(key) as? PublicJsonArray)?.values ?: error("array required")
    private fun required(key: String) = requireNotNull(values[key])
}

private data class PublicJsonArray(val values: List<PublicJsonValue>) : PublicJsonValue
private data class PublicJsonString(val value: String) : PublicJsonValue
private data class PublicJsonNumber(val value: String) : PublicJsonValue
private data class PublicJsonBoolean(val value: Boolean) : PublicJsonValue
private data object PublicJsonNull : PublicJsonValue
private fun String.toIntExact(): Int? = toIntOrNull()?.takeIf { it.toString() == this }

private class PublicStrictJson(private val source: String) {
    private var index = 0

    fun parse(): PublicJsonValue = value(0).also {
        whitespace()
        require(index == source.length)
    }

    private fun value(depth: Int): PublicJsonValue {
        require(depth <= 32)
        whitespace()
        require(index < source.length)
        return when (source[index]) {
            '{' -> obj(depth + 1)
            '[' -> array(depth + 1)
            '"' -> PublicJsonString(string())
            't' -> literal("true", PublicJsonBoolean(true))
            'f' -> literal("false", PublicJsonBoolean(false))
            'n' -> literal("null", PublicJsonNull)
            '-', in '0'..'9' -> number()
            else -> error("invalid JSON")
        }
    }

    private fun obj(depth: Int): PublicJsonObject {
        index++
        whitespace()
        val map = linkedMapOf<String, PublicJsonValue>()
        if (take('}')) return PublicJsonObject(map)
        while (true) {
            whitespace()
            require(peek() == '"')
            val key = string()
            require(map[key] == null)
            whitespace()
            require(take(':'))
            map[key] = value(depth)
            whitespace()
            if (take('}')) return PublicJsonObject(map)
            require(take(','))
        }
    }

    private fun array(depth: Int): PublicJsonArray {
        index++
        whitespace()
        val list = mutableListOf<PublicJsonValue>()
        if (take(']')) return PublicJsonArray(list)
        while (true) {
            list += value(depth)
            whitespace()
            if (take(']')) return PublicJsonArray(list)
            require(take(','))
        }
    }

    private fun string(): String {
        require(take('"'))
        val out = StringBuilder()
        while (index < source.length) {
            val character = source[index++]
            when {
                character == '"' -> return out.toString()
                character == '\\' -> {
                    require(index < source.length)
                    when (val escaped = source[index++]) {
                        '"', '\\', '/' -> out.append(escaped)
                        'b' -> out.append('\b')
                        'f' -> out.append('\u000c')
                        'n' -> out.append('\n')
                        'r' -> out.append('\r')
                        't' -> out.append('\t')
                        'u' -> {
                            require(index + 4 <= source.length)
                            val hex = source.substring(index, index + 4)
                            require(hex.all { it.isDigit() || it.lowercaseChar() in 'a'..'f' })
                            out.append(hex.toInt(16).toChar())
                            index += 4
                        }
                        else -> error("invalid escape")
                    }
                }
                character.code < 0x20 -> error("control in string")
                else -> out.append(character)
            }
        }
        error("unterminated string")
    }

    private fun number(): PublicJsonNumber {
        val start = index
        if (take('-')) require(index < source.length)
        if (take('0')) require(index == source.length || source[index] !in '0'..'9') else {
            require(index < source.length && source[index] in '1'..'9')
            while (index < source.length && source[index] in '0'..'9') index++
        }
        require(index == source.length || source[index] !in charArrayOf('.', 'e', 'E'))
        return PublicJsonNumber(source.substring(start, index))
    }

    private fun <T : PublicJsonValue> literal(text: String, value: T): T {
        require(source.startsWith(text, index))
        index += text.length
        return value
    }

    private fun whitespace() {
        while (index < source.length && source[index] in charArrayOf(' ', '\n', '\r', '\t')) index++
    }

    private fun take(character: Char) = if (index < source.length && source[index] == character) {
        index++
        true
    } else {
        false
    }

    private fun peek() = source[index]
}

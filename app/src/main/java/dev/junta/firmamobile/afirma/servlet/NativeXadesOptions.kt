package dev.junta.firmamobile.afirma.servlet

import java.util.Locale

enum class NativeXadesPackaging {
    DETACHED, ENVELOPING, ENVELOPED, EXTERNALLY_DETACHED
}

internal data class NativeXadesOptions(
    val packaging: NativeXadesPackaging,
    val mimeType: String,
    val contentDescription: String?
) {
    companion object {
        private val formats = mapOf(
            "xades detached" to NativeXadesPackaging.DETACHED,
            "xades enveloping" to NativeXadesPackaging.ENVELOPING,
            "xades enveloped" to NativeXadesPackaging.ENVELOPED,
            "xades externally detached" to NativeXadesPackaging.EXTERNALLY_DETACHED
        )
        private val allowedProperties = setOf(
            "format", "mode", "mimeType", "contentDescription", "headless", "validatePkcs1"
        )
        private val booleanProperties = listOf("headless", "validatePkcs1")
        private val mimePattern = Regex(
            "[A-Za-z0-9][A-Za-z0-9!#$&^_.+-]*/[A-Za-z0-9][A-Za-z0-9!#$&^_.+-]*"
        )

        fun acceptsFormat(format: String): Boolean {
            val name = format.lowercase(Locale.ROOT)
            return name == "xades" || name in formats
        }

        fun parse(format: String, properties: Map<String, String>): NativeXadesOptions? {
            val name = format.lowercase(Locale.ROOT)
            val explicitPackaging = formats[name]
            if (name != "xades" && explicitPackaging == null) return null
            if (properties.keys.any { it !in allowedProperties }) return null

            // Property selectors accept full format names or packaging labels.
            val selectedPackaging = properties["format"]?.let {
                val selector = it.lowercase(Locale.ROOT)
                formats[selector] ?: formats["xades $selector"] ?: return null
            }
            if (explicitPackaging != null && selectedPackaging != null &&
                explicitPackaging != selectedPackaging
            ) return null

            val mode = properties["mode"]
            if (mode != null && mode != "explicit" && mode != "implicit") return null
            // Validate literal booleans without granting authority or changing UI.
            for (key in booleanProperties) {
                val value = properties[key]
                if (value != null && value != "true" && value != "false") return null
            }

            val mimeType = properties["mimeType"] ?: "application/octet-stream"
            if (mimeType.length > 128 || !mimePattern.matches(mimeType)) return null
            val description = properties["contentDescription"]
            if (description != null &&
                (description.length > 256 || description.any { forbiddenDescriptionChar(it) })
            ) return null

            return NativeXadesOptions(
                packaging = selectedPackaging ?: explicitPackaging ?: NativeXadesPackaging.ENVELOPING,
                mimeType = mimeType,
                contentDescription = description
            )
        }

        private fun forbiddenDescriptionChar(c: Char): Boolean =
            c in '\u0000'..'\u001F' || c in '\u007F'..'\u009F' ||
                c == '\u061C' || c in '\u200E'..'\u200F' ||
                c in '\u202A'..'\u202E' || c in '\u2066'..'\u2069'
    }
}

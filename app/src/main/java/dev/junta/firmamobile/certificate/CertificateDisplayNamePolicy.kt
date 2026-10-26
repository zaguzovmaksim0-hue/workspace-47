package dev.junta.firmamobile.certificate

internal object CertificateDisplayNamePolicy {
    const val DEFAULT_DISPLAY_NAME = STORED_DEFAULT_CERTIFICATE_NAME

    fun sanitize(displayName: String?): String {
        val sanitized = displayName
            ?.filter { character ->
                character >= ' ' &&
                    character != '\u007f' &&
                    !isUnicodeBidiControl(character)
            }
            ?.take(MAX_DISPLAY_NAME_LENGTH)
            ?.trim()
        return sanitized.orEmpty().ifBlank { DEFAULT_DISPLAY_NAME }
    }

    fun presentation(displayName: String): CertificateDisplayName =
        if (displayName == DEFAULT_DISPLAY_NAME) CertificateDisplayName.Default
        else CertificateDisplayName.Named(displayName)

    private fun isUnicodeBidiControl(character: Char): Boolean =
        character == '\u061c' ||
            character in '\u200e'..'\u200f' ||
            character in '\u202a'..'\u202e' ||
            character in '\u2066'..'\u2069'

    private const val MAX_DISPLAY_NAME_LENGTH = 256
}

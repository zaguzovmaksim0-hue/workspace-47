package dev.junta.firmamobile.certificate

internal sealed interface CertificateDisplayName {
    data object Default : CertificateDisplayName
    data class Named(val value: String) : CertificateDisplayName
}

/** Legacy on-disk token, not a localized UI label. */
internal const val STORED_DEFAULT_CERTIFICATE_NAME = "Certificado seleccionado"

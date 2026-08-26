package dev.junta.firmamobile.certificate

import android.content.Context

internal object BuildVariantCertificateDocumentAccessFactory {
    fun create(context: Context): CertificateDocumentAccess =
        ContentResolverCertificateDocumentAccess(context.contentResolver)
}

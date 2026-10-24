package dev.junta.firmamobile.signing

import java.time.Clock
import java.time.Instant
import java.time.ZoneOffset
import java.util.Base64

// Test-owned semantic fixtures for all fourteen legacy contracts in plan #426.
// Identities are generated at runtime; no key/certificate/container is persisted.
data class AdapterGoldenCase(
    val name: String,
    val adapter: SigningProtocolAdapter,
    val profile: String,
    val host: String,
    val algorithm: SigningAlgorithm,
    val description: String,
    val data: ByteArray,
    val properties: String = "",
    val page: String? = null,
    val format: SigningFormat = SigningFormat.CADES,
    val maxBytes: Int = data.size,
    val revalidationError: SigningErrorCode? = SigningErrorCode.PROTOCOL_FAILED,
    val descriptionGuard: Boolean = true,
) {
    override fun toString(): String = name
}

internal val goldenInstant: Instant = Instant.parse("2030-01-02T03:04:05Z")
private val goldenClock = Clock.fixed(goldenInstant, ZoneOffset.UTC)

internal fun adapterGoldenCases(): List<AdapterGoldenCase> = listOf(
    AdapterGoldenCase(
        "Aragon", LocalCadesDetachedAdapter(goldenClock), "aragon-siraw", "aplicaciones.aragon.es",
        SigningAlgorithm.SHA1_WITH_RSA, "synthetic Aragon challenge", ByteArray(20) { it.toByte() },
        "mode=explicit\nfilter=nonexpired", revalidationError = null, descriptionGuard = false,
    ),
    AdapterGoldenCase(
        "DGT", DgtVerificationCadesAdapter(goldenClock), "dgt-verificacion-equipo", "sede.dgt.gob.es",
        SigningAlgorithm.SHA1_WITH_RSA, "synthetic DGT challenge", "Cadena a firmar".encodeToByteArray(),
        "filter=nonexpired:", revalidationError = null, descriptionGuard = false,
    ),
    AdapterGoldenCase(
        "Lleida", DiputacionLleidaCadesAdapter(goldenClock), "diputacion-lleida-sede", "seu.diputaciolleida.cat",
        SigningAlgorithm.SHA256_WITH_RSA, "Acceso con certificado a la Diputació de Lleida",
        "synthetic printable challenge".encodeToByteArray(),
        "policy=FirmaAGE\nheadless=true\nfilters=nonexpired:true;authCert:true", maxBytes = 512,
    ),
    AdapterGoldenCase(
        "Badajoz", DiputacionBadajozCadesAdapter(goldenClock), "diputacion-badajoz-portal", "sede.dip-badajoz.es",
        SigningAlgorithm.SHA256_WITH_RSA, "Acceso con certificado a la Diputación de Badajoz",
        "synthetic printable challenge".encodeToByteArray(),
        "policy=FirmaAGE\nheadless=true\nfilters=nonexpired:true;authCert:true", maxBytes = 512,
    ),
    AdapterGoldenCase(
        "Canarias", CanariasCertificateLoginCadesAdapter(goldenClock), "canarias-sede",
        "sede.gobiernodecanarias.org", SigningAlgorithm.SHA1_WITH_RSA,
        "Acceso con certificado a la Sede electrónica del Gobierno de Canarias",
        "Wed, 02 Jan 2030 03:04:05 GMT".encodeToByteArray(),
        "format=CAdES Detached\n" +
            "serverUrl=https://sede.gobiernodecanarias.org/platino/servlet_afirma/SignatureService\n" +
            "referencesDigestMethod=http://www.w3.org/2001/04/xmlenc#sha512\n" +
            "filters=nonexpired:true;signingCert:true;issuer.rfc2254:" +
            "(&(!(CN=CiberCentro*))(!(CN=GobCanCA))(!(O=Gobierno de Canarias))" +
            "(!(O=PKI))(!(O=DO_NOT_TRUST*)))",
        page = "https://sede.gobiernodecanarias.org/sede/identificacion",
    ),
    AdapterGoldenCase(
        "UGR", UgrCadesDetachedAdapter(goldenClock), "ugr-certificado-login", "sede.ugr.es",
        SigningAlgorithm.SHA1_WITH_RSA, "Acceso con certificado a la Universidad de Granada",
        "Universidad de Granada".encodeToByteArray(),
    ),
    AdapterGoldenCase(
        "JCCM", JccmCertificateLoginProbeCadesAdapter(goldenClock), "jccm-certificate-login-probe",
        "ventanillaelectronica.jccm.es", SigningAlgorithm.SHA1_WITH_RSA,
        JccmCertificateLoginProbeCadesAdapter.SAFE_DESCRIPTION, "ABCDE".encodeToByteArray(),
    ),
    AdapterGoldenCase(
        "MITES", MitesCertificateLoginCadesAdapter(goldenClock), "mites-certificate-login", "sede.mites.gob.es",
        SigningAlgorithm.SHA512_WITH_RSA, "Acceso con certificado a la Sede del Ministerio de Trabajo",
        "abcdefghij".encodeToByteArray(),
        "mode=implicit\nfilters.1=signingCert:;keyusage.nonrepudiation:true;nonexpired:",
        revalidationError = SigningErrorCode.UNSUPPORTED_PROTOCOL,
    ),
    AdapterGoldenCase(
        "Cantabria", CantabriaRecCertificateLoginCadesAdapter(goldenClock), "cantabria-rec-cert-login",
        "rec.cantabria.es", SigningAlgorithm.SHA512_WITH_RSA,
        "Acceso con certificado al Registro Electrónico Común de Cantabria",
        "0123456789abcdef0123456789abcdef01234567".encodeToByteArray(), "filters=\nmode=implicit",
        revalidationError = SigningErrorCode.UNSUPPORTED_PROTOCOL,
    ),
    AdapterGoldenCase(
        "Tenerife", TenerifeCadesDetachedAdapter(goldenClock), "tenerife-sede-electronica", "sede.tenerife.es",
        SigningAlgorithm.SHA512_WITH_RSA, TenerifeCadesDetachedAdapter.SAFE_DESCRIPTION,
        "synthetic Tenerife application document".encodeToByteArray(), "mode=explicit", maxBytes = 524_288,
    ),
    AdapterGoldenCase(
        "Eivissa", EivissaCadesDetachedAdapter(goldenClock), "eivissa-sede-electronica", "seu.conselldeivissa.es",
        SigningAlgorithm.SHA256_WITH_RSA, EivissaCadesDetachedAdapter.SAFE_DESCRIPTION,
        "synthetic Eivissa xsig".encodeToByteArray(), maxBytes = 524_288, revalidationError = null,
    ),
    AdapterGoldenCase(
        "Sevilla", SevillaAtseXadesEnvelopingAdapter(goldenClock), "sevilla-atse-certificate-login",
        "www.sevilla.org", SigningAlgorithm.SHA1_WITH_RSA, SevillaAtseXadesEnvelopingAdapter.SAFE_DESCRIPTION,
        "0123456789abcdef0123456789abcdefABCDEFGH".encodeToByteArray(),
        format = SigningFormat.XADES, revalidationError = null,
    ),
    AdapterGoldenCase(
        "AIReF", AirefXadesEnvelopingAdapter(goldenClock), "airef-instancia-general", "sede.airef.es",
        SigningAlgorithm.SHA1_WITH_RSA, AirefXadesEnvelopingAdapter.SAFE_DESCRIPTION,
        ByteArray(32) { (it * 7).toByte() }, format = SigningFormat.XADES, revalidationError = null,
    ),
    AdapterGoldenCase(
        "CDTI", CdtiXadesEnvelopingAdapter(goldenClock), "cdti-certificate-validation", "sede.cdti.gob.es",
        SigningAlgorithm.SHA512_WITH_RSA, CdtiXadesEnvelopingAdapter.SAFE_DESCRIPTION,
        Base64.getDecoder().decode("CertExp4780e003d3164fc3ac515844b4eb30f8s15utgmbrvl0kdvkk01oaz55"),
        "filters=nonexpired",
        page = "https://sede.cdti.gob.es/AreaPrivada/Expedientes/Common/Certificados/ValidarCertificado.aspx",
        format = SigningFormat.XADES, revalidationError = SigningErrorCode.UNSUPPORTED_PROTOCOL,
    ),
)

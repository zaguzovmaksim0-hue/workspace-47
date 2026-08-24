# Ministerio de Educación — Convocatoria 46 — bounded client TLS evidence

Reviewed: 2026-08-19.

The current official entry `https://sede.educacion.gob.es/sede/login/loginConv.jjsp?iA=no&idConvocatoria=46` returns HTTP 200 and exposes a first-party POST to `https://www.educacion.gob.es/claveedu/claveEduPeticion.form`. That bridge produces a transient SAML POST to `https://pasarela.clave.gob.es/Proxy2/ServiceProvider`. No SAML or cookie values are retained here.

The current Cl@ve chooser exposes the certificate option as provider `AFIRMA`. Selecting it POSTs through `https://pasarela.clave.gob.es/Proxy2/ServiceRedirect`; the resulting first-party form has the exact target `https://pasarela-ident.clave.gob.es/IdP2/AuthenticateCitizen`. A direct TLS 1.2 probe of that hostname observed a server `CertificateRequest`, RSA-sign among the advertised client-certificate types, and an empty client-certificate CA-name list.

This evidence proves only the bounded certificate client-TLS transition. It does not prove authentication success, document signing, signature format or algorithm, callback semantics, or final filing acceptance. The implementation therefore remains QA-only and exposes only `CLIENT_TLS_AUTH`. No private-key document signature, final filing/registration/submission, or payment was performed.

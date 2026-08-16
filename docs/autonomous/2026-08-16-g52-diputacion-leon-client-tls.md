# Diputación Provincial de León — bounded certificate-login contract

Reviewed: 2026-08-16

## Public first-party evidence

- The public `Instancia General` procedure is `https://sede.dipuleon.es/carpetaciudadana/tramite.aspx?idtramite=20270`; it states that the generated request PDF must be digitally signed.
- The public login path reaches `segex/identificacion_opciones.aspx?idtoken=...&idioma=es` without storing or replaying cookies during research.
- That page exposes `Identificarse con certificado digital a través de nuestro servidor` and constructs exactly `https://identificacionssl.sedipualba.es/?idtoken=...&idioma=es&entidad=24000`. The page uses the same `idtoken` in source and target.
- A TLS-verified GET to the exact certificate host with a synthetic token triggers a TLS 1.2 renegotiation. The server sends `CertificateRequest` before returning an HTTP 302 to a León error page when no client certificate is supplied. No real certificate or private key was presented.

## Implementation boundary

The QA profile implements only this observed browser client-certificate transition. The authorizer requires:

- exact active profile and source host/path;
- exact source query names `idtoken` and `idioma=es`;
- bounded observed `idtoken` shape;
- exact target host `identificacionssl.sedipualba.es`, HTTPS port 443 and root path;
- exact target fixed values `idioma=es` and `entidad=24000`;
- the target `idtoken` to equal the source `idtoken` exactly;
- no extra query keys or fragment;
- one bounded 15-second grant and explicit certificate confirmation through the existing client-auth flow.

## Limitations

- QA-only and not E2E verified.
- No real certificate, private key, authenticated account, OTP, cookie replay, signature, upload or administrative submission was used.
- The later PDF signing algorithm, signature format, callback and submission contract remain intentionally unimplemented.

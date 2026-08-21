# Diputación Provincial de Cuenca — current client-auth evidence

Reviewed: 2026-08-21

## Decision

Implement the smallest current capability as a dedicated QA-only
`CLIENT_TLS_AUTH` profile for the official Cuenca electronic procedure. Do not
implement document signing, a signing endpoint, a signature format or
administrative filing.

## Decisive current evidence

- The official electronic-procedure catalog is
  `https://sede.dipucuenca.es/catalogoservicios.aspx`; its current public list
  links “Registro electrónico. Presentación de instancia general” to
  `https://sede.dipucuenca.es/carpetaciudadana/tramite.aspx?idtramite=12074`.
- The procedure page describes a web request that becomes a PDF, says the
  resulting instance must be digitally signed, and exposes the current
  unauthenticated “Iniciar sesión” boundary.
- The current login modal reaches
  `https://sede.dipucuenca.es/segex/identificacion_opciones.aspx` and its
  first-party JavaScript builds the exact client-certificate launch
  `https://identificacionssl.sedipualba.es/?idtoken=<ephemeral>&idioma=es&entidad=16000`.
  The same ephemeral parameter is required on the source and target, while
  `idioma=es` and `entidad=16000` are fixed target parameters.
- The same current page labels the option “Identificarse con certificado
  digital a través de nuestro servidor” and documents the accepted certificate
  classes. A request without a client certificate returns to the official
  Cuenca fallback page stating that no valid certificate was selected; no
  certificate, credential, document, signature or filing was supplied.
- The current official sede also identifies the institution as Diputación
  Provincial de Cuenca, entity `P1600000B`, and links the Sedipualb@ platform.

The institutional origin `https://www.dipucuenca.es` returned HTTP 403 during
this pass and is therefore not promoted into the trusted profile. The exact
implemented start surface is the independently reachable official sede
procedure above.

## Boundary

The profile is `QA_ONLY` and exposes only `CLIENT_TLS_AUTH`. It does not claim
that the portal accepted a real authenticated request, and it does not model
AutoFirma, CAdES/PAdES/XAdES, algorithms, callbacks, Storage/Retrieve, or the
final registration POST. The status is `IMPLEMENTED_NOT_E2E` with generated
catalog status `E2E_PENDING`.

## Evidence URLs

- `https://www.dipucuenca.es`
- `https://sede.dipucuenca.es/`
- `https://sede.dipucuenca.es/catalogoservicios.aspx`
- `https://sede.dipucuenca.es/carpetaciudadana/tramite.aspx?idtramite=12074`
- `https://sede.dipucuenca.es/carpetaciudadana/login.aspx`
- `https://sede.dipucuenca.es/segex/identificacion_opciones.aspx`
- `https://identificacionssl.sedipualba.es/`

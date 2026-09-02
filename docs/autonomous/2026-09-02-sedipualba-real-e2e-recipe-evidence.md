# SEDIPUALB@ shared CLIENT_TLS_AUTH REAL E2E recipe evidence — 2026-09-02

Scope: fresh public unauthenticated navigation evidence only. No client certificate was sent, no authenticated area was entered, and no administrative form was submitted while collecting this evidence.

## Covered catalog cards

The same reviewed SEDIPUALB@ authentication family is currently exposed by:

- `diputacion-albacete-portal` — `https://sede.dipualba.es/carpetaciudadana/tramite.aspx?idtramite=567`
- `diputacion-leon-sede` — `https://sede.dipuleon.es/carpetaciudadana/tramite.aspx?idtramite=20270`
- `mallorca-sede-electronica` — `https://cim.secimallorca.net/segex/tramite.aspx?idtramite=12082`
- `mallorca-portal-institucional` — catalog alias whose exact `launchUrl` is the same Mallorca procedure URL above

All four already bind to QA-only profiles whose `CLIENT_TLS_AUTH` policies pin the SEDIPUALB@ certificate endpoint and link the dynamic `idtoken` from the source page to the TLS target.

## Current public navigation contract

Fresh GETs on 2026-09-02 showed the same sequence for Albacete, León and Mallorca:

1. The procedure page exposes one exact login link.
   - Albacete/León label: `Iniciar sesión`.
   - Mallorca label: `Iniciar sessió`.
   - Each href is a same-origin `carpetaciudadana/login.aspx` URL with an exact encoded return to that procedure.
2. The login page creates the authentication surface through same-origin `carpeta_ciudadana_cliente_autenticacion.aspx`, preserving the exact login URL in its `returnUrl` parameter.
3. That public authentication request redirects to the same-origin source page:
   - path exactly `/segex/identificacion_opciones.aspx`;
   - query contains exactly `idtoken` plus fixed `idioma`;
   - `idtoken` is ephemeral and must be consumed from the live page, never copied into code or durable evidence.
4. The source page contains `tbody#optSsl` and exactly one certificate image at `imgs/identificacion/certificado.svg`.
5. The page's current first-party JavaScript binds `#optSsl` to `https://identificacionssl.sedipualba.es/` with the same live `idtoken`, the same language and the profile-specific entity code:
   - Albacete: `idioma=es`, `entidad=02000`;
   - León: `idioma=es`, `entidad=24000`;
   - Mallorca: `idioma=ca`, `entidad=07700`.

No ephemeral token value is preserved in this document.

## Fail-closed implementation

The REAL E2E recipe:

- matches the exact reviewed procedure URL, login label and login href;
- accepts only one same-origin auth iframe with the exact authentication path and exact `returnUrl` back to the reviewed login URL;
- stays on the exact reviewed login page and inspects only same-origin auth iframes already opened by the portal;
- accepts exactly one iframe only when its live location has the reviewed origin/path, exactly `idtoken` + `idioma`, the fixed language matches, and the token has a bounded URL-safe shape;
- clicks only that iframe’s `tbody#optSsl` after validating its exact certificate image and localized alt text; the portal’s own reviewed handler performs the top-level transition to the TLS endpoint;
- relies on the existing profile-scoped `ClientAuthNavigationAuthorizer` to independently verify the subsequent TLS host, entity, language and linked `idtoken` before any certificate is offered.

The recipe does not interact with the later document-signing or submission flow.

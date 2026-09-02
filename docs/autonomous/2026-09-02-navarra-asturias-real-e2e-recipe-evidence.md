# Navarra and Asturias CLIENT_TLS_AUTH REAL E2E recipe evidence — 2026-09-02

Scope: current public unauthenticated navigation and DOM only. No client certificate was sent and no administrative form was filed or submitted.

## Gobierno de Navarra — Registro General

Catalog/profile: `navarra-sede-registro-general`.

Fresh public navigation showed:

1. The catalog entry page contains the exact anchor `Tramitar` to `https://administracionelectronica.navarra.es/RGE2/Default.aspx?idioma=es`.
2. That endpoint performs two public redirects and lands at `https://ateka.navarra.es/ateka/router?ReturnUrl=...`.
3. The live `ReturnUrl` is an OIDC callback for client `rge`, with redirect URI exactly `https://administracionelectronica.navarra.es/RGE2/Default.aspx`, `response_type=code id_token`, `response_mode=form_post`, `code_challenge_method=S256` and `ui_locales=es`.
4. The router page exposes exactly the certificate-authentication anchor `Certificado Digital o DNIe` to `/ateka/Certificate/login?returnUrl=...`.
5. The certificate link's dynamic `returnUrl` is byte-for-byte the decoded live `ReturnUrl` from the source router URL. The existing `ClientAuthNavigationAuthorizer` independently enforces the same source-to-target parameter mapping before permitting CLIENT_TLS_AUTH.

The recipe therefore clicks only `Tramitar` and the exact certificate-auth link after validating the live OIDC state linkage. It does not interact with the registry form after authentication.

## Principado de Asturias — MiPrincipado / Solicitud Genérica

Catalog/profile: `asturias-miprincipado-sede` / `asturias-miprincipado`.

The public procedure page currently contains a dedicated authentication form `form#sytInitForm`:

- action exactly `https://tramita.asturias.es/sta/Relec/STARhssoManager`;
- method `post`, target `_blank`;
- fixed `APP_CODE=STA`, `PAGE_CODE=CATALOGO`, `ROOTID=2`;
- `dboidSolicitud=6269000102616541907573`;
- `autoFirma=false`;
- `url=Relec/STARhssoManager`, `fire=true`;
- fixed return path to the reviewed public procedure.

Inside that form the only reviewed online authentication button is `Con sistema Clave`, type `button`, with exact site-owned handler `javascript:sendFormCustom(false);`. The current handler merely submits `sytInitForm`; on the reviewed page no application payload fields are present in that auth form. The existing profile then constrains navigation through the reviewed Asturias/Cl@ve origins and permits client TLS only from the pinned Cl@ve source.

The recipe validates the form/action/fixed hidden values and clicks only that authentication button. It does not click or submit a final administrative application control.

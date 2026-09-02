# Menorca shared CLIENT_TLS_AUTH REAL E2E recipe evidence — 2026-09-02

Scope: fresh unauthenticated public navigation only. No client certificate was sent and no administrative submission was performed.

## Covered cards

Both catalog cards bind to QA profile `menorca-carpeta-ciutadana` and launch the same reviewed procedure URL:

- `menorca-portal-institucional`
- `menorca-sede-electronica`
- launch/profile start: `https://www.carpetaciutadana.org/cime/gesserveis/Gestion.aspx?IDGESTION=990100262`

The sede currently also exposes `Tràmits en línia - Carpeta Ciutadana` to the current Carpeta service, consistent with the catalog alias.

## Current public contract

With normal ASP.NET session cookies preserved, fresh requests on 2026-09-02 showed:

1. The profile start page returns HTTP 200 and has exactly reviewed action link:
   - id `ctl00_Content1_HyperLink1`;
   - label `Tramitar`;
   - href `https://www.carpetaciutadana.org/cime/solicituds/iniciartramit.aspx?TIPO=REGE&IDIOMA=1`.
2. Following that action redirects once to `https://www.carpetaciutadana.org/cime/Login/Login.aspx` with exactly one query parameter `URL`.
3. The current service serializes the linked procedure URL with an unusual inverted-question-mark separator (`¿`, UTF-8 `%C2%BF`) in live traffic; historical/current profile evidence uses the normal `?` form. The recipe therefore accepts only those two exact linked values and nothing else.
4. The login page exposes a single reviewed authentication control:
   - id `ctl00_Content1_Button1`;
   - name `ctl00$Content1$Button1`;
   - type `submit`;
   - value `Certificat electrònic`;
   - class `boton`.
5. The existing profile authorizer independently constrains the next authentication transition to same-origin `/cime/Login/LoginCert.aspx`, requires exactly the linked `URL` parameter, and checks that its value is identical to the source value before allowing CLIENT_TLS_AUTH.

The recipe deliberately clicks only the certificate authentication selector. It does not interact with later AutoFirma, document signing, filing or submission controls.

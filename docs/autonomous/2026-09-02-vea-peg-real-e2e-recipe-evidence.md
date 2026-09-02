# Junta de Andalucía VEA PEG CLIENT_TLS_AUTH REAL E2E evidence — 2026-09-02

Scope: fresh public SPA shell and first-party JavaScript bundle analysis only. No client certificate was sent, no draft was created by this inspection, and no administrative application was filled, signed, or submitted.

Catalog/profile: `junta-andalucia-sede` / `junta-andalucia-vea-peg`.

## Current first-party application contract

The current `https://veaja.cloud.juntadeandalucia.es/inicio/procedimiento-detalle/PEG_VEA` Angular bundle shows the following unauthenticated flow:

1. The procedure page exposes a button whose visible label is exactly `INICIAR SOLICITUD`; its click handler calls `goToIniciarSolicitud()`.
2. In `goToIniciarSolicitud()`, when there is no authenticated session, the application constructs a live return URL for the same PEG procedure with `iniciarSolicitud=true`, `procedureId=<live procedure id>`, and `versionId=<live selected version id>`, configures the authentication modal, and opens it. The draft-creation branch is only reached after `checkIfLogged()` succeeds.
3. The current authentication modal contains a distinct button labelled exactly `Acceder con certificado electrónico`; its handler calls `loginCertificate()`.
4. `loginCertificate()` base64-encodes the configured live redirect URL, retains procedure code `PEG_VEA`, and routes to `/authFacade` with `modoAcceso=afirma`.
5. The auth facade then invokes the existing authentication service, whose current first-party code builds the API authentication URL with `modoAcceso=afirma`, live `redirectUrl`, and `codigoProcedimiento=PEG_VEA`.
6. The existing profile independently constrains that source to `https://api-veaja.cloud.juntadeandalucia.es/auth/login`, requires the live base64 redirect to decode back to `/inicio/procedimiento-detalle/PEG_VEA?iniciarSolicitud=true&procedureId=...&versionId=...`, and pins the subsequent client-TLS request to the reviewed `ws235*.juntadeandalucia.es/authenticationFacade*` continuation family.

## Fail-closed recipe

The REAL E2E recipe performs exactly two UI actions on the reviewed PEG page:

- first, it finds exactly one enabled `button` with normalized text `INICIAR SOLICITUD` and rejects it if it is already inside an authentication modal;
- second, after the application opens its own authentication modal, it finds exactly one enabled `button` with normalized text `Acceder con certificado electrónico` and requires that button to be inside `app-modal-1` before clicking it.

The recipe does not interact with `CONTINUAR BORRADOR`, NIF/NIE, Cl@ve, any application field, file upload, signature control, or final presentation/submission control.

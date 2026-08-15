# Diputación de Toledo — bounded SIGEM client-TLS evidence — 2026-08-13

## Scope

Public, unauthenticated evidence only. No client certificate, credential, authenticated session, signature, upload, or administrative submission was used.

## Public observations

1. The official procedures page `https://diputacion.toledo.gob.es/procedimientos/1` exposes `SOLICITUD DE PROPÓSITO GENERAL` and an explicit `Con Certificado electrónico` entry.
2. That entry is `https://diputacion.toledo.gob.es/SIGEM_RegistroTelematicoWeb/realizarSolicitudRegistro.do?tramiteId=TRAM_31`, which redirects to the public SIGEM authentication application.
3. The unauthenticated authentication shell meta-refreshes to the exact 443 source `https://diputacion.toledo.gob.es/SIGEM_AutenticacionWeb/seleccionEntidad.do?REDIRECCION=RegistroTelematico&tramiteId=TRAM_31&SESION_ID=&ENTIDAD_ID=&LANG=&COUNTRY=`.
4. The source page contains an on-load JavaScript redirect to the exact target `https://diputacion.toledo.gob.es:843/SIGEM_AutenticacionWeb/validacionCertificado.do`.
5. A public TLS handshake to that `:843` target sends a client-certificate request, advertises accepted client-certificate CA names and RSA/DSA/ECDSA signing certificate types. Without a client certificate, the unauthenticated response reports an error obtaining the user certificate.

## Implementation boundary

The supported contract is only one QA-only `CLIENT_TLS_AUTH` transition from the exact observed 443 source URI to the exact same-host `:843` path. The grant is one-shot and port-pinned. The application supports RSA/EC certificate identities; DSA is not added. Document signing, signature formats/algorithms, uploads, form submission, and legal registration remain unimplemented and must not be inferred from this authentication contract.

## E2E status

Not verified E2E. Release activation remains disabled until a separately authorized physical-device check verifies only the certificate-login boundary without signing or submitting an administrative request.

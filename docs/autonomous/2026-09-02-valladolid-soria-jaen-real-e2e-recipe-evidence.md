# Valladolid, Soria and Jaén CLIENT_TLS_AUTH REAL E2E recipe evidence — 2026-09-02

Scope: fresh public unauthenticated DOM evidence. No client certificate was sent and no administrative form was filed or submitted.

All three profiles are already QA-only `CLIENT_TLS_AUTH` contracts. The additions below only drive exact reviewed authentication controls until the existing profile-scoped client-auth authorizer takes over.

## Diputación de Valladolid

Entry: `https://www.sede.diputaciondevalladolid.es/tgauth/login`.

Fresh HTTP 200 DOM exposes the exact anchor:

- label `ACCESO CON CERTIFICADO DIGITAL`;
- href `/c/portal/cert-login`.

That href is exactly the profile's reviewed source URL. The existing policy then constrains the certificate request to the reviewed same host/path and port 21460.

## Diputación de Soria

Entry/profile source:

`https://portaltramitador.dipsoria.es/web/inicioWebc.do?opcion=cargar&redirige=L2NhcmdhTWVudVdlYi5kbz9vcGNpb249bm9yZWc%3D&entidad=SORIA&idioma=1`

Fresh HTTP 200 DOM exposes the certificate control:

- `button#b_certificado`;
- visible label `Acceder`;
- type `button`;
- exact onclick `pulsarCertificado();`.

The page also has a different Cl@ve button reusing the same invalid duplicate DOM id, so the recipe deliberately matches id + label + type + exact onclick together. The existing profile authorizer constrains the resulting request to `/web/inicioWebcCert.do` with the reviewed fixed query tuple.

## Diputación de Jaén

Entry: `https://sede.dipujaen.es/SolicitudGenerica`.

Fresh public DOM shows:

1. exact anchor label `Acceder`, href `/IniciarSesion`;
2. on HTTP 200 `https://sede.dipujaen.es/IniciarSesion`, exact anchor label `Acceder con certificado digital`, href `/IniciarSesion/Certificado`.

The second href is exactly the profile's reviewed source URL. From there the existing client-auth policy constrains the redirect to `https://cert2.dipujaen.es/`, requires the fixed `back=https://sede.dipujaen.es/IniciarSesion/Certificado`, and requires the ephemeral `key` parameter before any certificate request is accepted.

No recipe here interacts with the later generic-request submission flow.

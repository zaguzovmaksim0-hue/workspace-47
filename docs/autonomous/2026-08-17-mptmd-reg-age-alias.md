# Ministerio de Política Territorial y Memoria Democrática → REG-AGE delegation evidence — 2026-08-17

## Public unauthenticated evidence

- `https://mptmd.sede.gob.es/` returned HTTP 200 in an unauthenticated Chromium session and identified itself as the official **Sede Electrónica del Ministerio de Política Territorial y Memoria Democrática**. Direct command-line HTTP to the same origin returned HTTP 403, so the decisive institutional evidence was read through a public unauthenticated browser context.
- The public home page exposes **Registro Electrónico General** / **Acceso al Registro Electrónico General** and links to `https://mptmd.sede.gob.es/servicio?id=Registro-Electr%C3%B3nico-General`.
- That official service page returned HTTP 200 and explicitly describes the **Registro Electrónico General de la AGE** as the general point for solicitudes, escritos y comunicaciones not covered by an existing specific procedure.
- The same page publishes **ACCESO DIRECTO AL REGISTRO** to `https://reg.redsara.es/`. In a fresh unauthenticated Chromium context configured with locale `es-ES`, the observed navigation chain was `https://reg.redsara.es/` HTTP 302 → `https://reg.redsara.es/es/` HTTP 200. The final URL is exactly the immutable `startUrl` of the already implemented `reg-age-redsara` profile.
- The institutional page also states that access/sending requires DNIe or a digital certificate via Cl@ve and that, when sent, the request is signed with the digital certificate. This supports the inventory-level `certificate_required: SI` and `signature_required: SI` statements only; it does not establish a separate MPTMD signing implementation.
- No login, Cl@ve authentication, credential, certificate selection/use, private key, form submission, upload, signature operation, payment, or administrative presentation was attempted.

## Bounded implementation

`ES-PUB-0072` is represented only as an `ALIAS_ONLY` QA catalog binding to the existing `reg-age-redsara` launch. The official MPTMD REG service page remains the `entry_url`/`procedure_page`; the catalog launch is only the exact existing profile start URL.

No MPTMD signing origin, signing endpoint, algorithm, callback contract, signature format/packaging, certificate-selection rule, client-TLS rule, initiator origin, or other cryptographic capability is inferred for `mptmd.sede.gob.es`. Existing REG-AGE trust boundaries remain unchanged. Release remains fail-closed. Status is `IMPLEMENTED_NOT_E2E` / generated `E2E_PENDING`; no `VERIFIED_E2E` claim is made.

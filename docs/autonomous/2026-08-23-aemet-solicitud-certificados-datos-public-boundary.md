# ES-PUB-0024 — AEMET — bounded public navigation and certificate-login evidence

Reviewed: 2026-08-23

## Current public chain

- The official AEMET institutional Sede page `https://www.aemet.es/es/sede_electronica` links to the AEMET electronic-services surface.
- `https://sede.aemet.gob.es/AEMET/es/GestionPeticiones/home` is the stable public Sede entry and exposes **Solicitud certificados y datos**.
- `https://sede.aemet.gob.es/AEMET/es/GestionPeticiones/solicitudes` describes the current certificates/data request service and requires registration or identification for web requests.
- `https://sede.aemet.gob.es/AEMET/es/GestionPeticiones/nuevaSolicitud` exposes **Usuarios en general** and a research-organization variant. In an established public Sede session the general-user branch targets `https://sede.aemet.gob.es/AEMET/es/GestionPeticiones/formularioSolicitud?tipoSolicitud=L1`.
- The L1 branch requires identification and offers username/password or **DNI-e / certificado digital**; the page states that AutoFirma is necessary for certificate access.
- `https://sede.aemet.gob.es/AEMET/es/GestionPeticiones/sso` is the public certificate-login boundary. Its HTML loads `miniapplet.js` and `firma.js` and contains a same-origin POST form for `ssoLogin` with `signature` and `errorMessage` fields.
- Fresh direct deep-link requests can fall back to the generic Sede shell, so the profile deliberately starts at the stable official Sede entry instead of depending on session-conditioned deep links.

## Implemented boundary

The Android profile is `QA_ONLY` and navigation-only. It trusts only the exact origin `https://sede.aemet.gob.es` and has no redirect origins, trusted-browse expansions, endpoints, operation policies, capabilities, or client-auth policy.

The inventory records `CERTIFICATE_ACCESS`, `ELECTRONIC_SIGNATURE`, `AUTOFIRMA`, and `MINIAPPLET` only as observations of the public login boundary. Those observations do **not** grant the runtime `SIGN`, `SELECT_CERTIFICATE`, or `CLIENT_TLS_AUTH` capabilities. Exact signing payload, algorithm, signature format, callback, server endpoint, authenticated form behavior, document upload, and final filing remain unverified.

No username/password, certificate, signature value, document, application form, final submission, registration, payment, or private-key operation was sent or executed during this evidence pass.

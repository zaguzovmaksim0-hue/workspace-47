# AGE PAG / REG-AGE exact delegation evidence — worker C — 2026-08-13

## Scope and safety boundary

This checkpoint used only unauthenticated first-party HTTPS GET requests. No credentials, session replay,
certificate selection, signing, form POST, upload, payment, administrative submission, APK launch, ADB,
or device-control workflow was used.

## Exact public delegation

The catalogued PAG URL
`https://sede.administracion.gob.es/PAG_Sede/ServiciosElectronicos/RegistroElectronicoGeneral.html?idioma=es&imprimir=1`
currently redirects with HTTP 301 to the official canonical REG-AGE information page at
`https://sede.administracion.gob.es/servicios-electronicos/registro-electronico-general-age`, which
returns HTTP 200.

A fresh bounded fetch on 2026-08-13 returned 84,462 bytes with SHA-256
`663e40e6c9f7cde3214c932121520c58c09dc0ba9a6b3321a824c00b8f52ce2d`. The public body contains the
exact external access URL `https://reg.redsara.es/es/` for "Acceso al Registro Electrónico General".
The page also describes certificate/DNIe and Cl@ve access. No protected action was entered.

Workspace-47 already has the QA-only `reg-age-redsara` profile whose exact start URL is
`https://reg.redsara.es/es/`. Therefore `age-pag-reg` can reuse the established alias pattern already
used by `us-sede`: retain the official PAG information URL as metadata, set the exact RedSARA URL only
as `launch_url`, and bind to the existing profile. This does not infer a PAG-specific cryptographic
contract and does not enable the alias in release builds. E2E remains pending.

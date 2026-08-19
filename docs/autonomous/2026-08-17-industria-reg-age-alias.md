# Ministerio de Industria y Turismo → REG-AGE delegation evidence — 2026-08-17

## Public evidence

- Public unauthenticated `GET https://sede.minetur.gob.es/` reached the current official `Sede electrónica del Ministerio de Industria y Turismo` after public redirects to `sede.serviciosmin.gob.es`.
- The first-party register page `https://sede.minetur.gob.es/es-es/procedimientoselectronicos/Paginas/consulta_registro.aspx` returned HTTP 200 and contains the section `Acceso al Registro Electrónico General de la Administración General del Estado`. It states that requests, writings, and communications without a specific supporting application can use the General Electronic Register service, and publishes `https://rec.redsara.es/registro/action/are/acceso.do`.
- A bounded unauthenticated redirect check of that exact published URL returned HTTP 301 with `Location: https://reg.redsara.es/`. No login, certificate, POST, upload, signature, payment, or administrative filing was performed.
- The current official Punto de Acceso General page `https://administracion.gob.es/pag_Home/atencionCiudadana/Registros-electronicos-AGE.html` identifies the service as `Registro Electrónico General (REG-AGE)` and publishes `Acceso al REG` on `https://reg.redsara.es/`.
- The existing QA-only `reg-age-redsara` profile canonical start `https://reg.redsara.es/es/` returned HTTP 200 with title `REG - Registro Electrónico General` on 2026-08-17.

## Bounded implementation

`ES-PUB-0069` keeps `https://sede.minetur.gob.es/` as institutional `entryUrl` and the Ministry register page as `procedure_page`. QA launches only the exact existing profile start `https://reg.redsara.es/es/` under `reg-age-redsara`.

The public redirects to `sede.serviciosmin.gob.es` and from legacy `rec.redsara.es` are evidence/navigation only. Neither origin is added to cryptographic trust. Generic AutoFirma/certificate guidance on the Ministry site is not used to infer an Industria-specific signing ABI, algorithm, format, callback, certificate rule, client-TLS contract, or endpoint.

Status remains `IMPLEMENTED_NOT_E2E` / `E2E_PENDING` pending a safe physical E2E transition.

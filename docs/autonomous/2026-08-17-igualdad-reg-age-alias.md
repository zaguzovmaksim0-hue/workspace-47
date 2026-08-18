# Ministerio de Igualdad → REG-AGE delegation evidence — 2026-08-17

## Public evidence

- Public unauthenticated GET of `https://igualdad.sede.gob.es/` returned HTTP 200 with title `Sede Electrónica del Ministerio de Igualdad - Inicio`.
- The Sede exposes a first-party service page at `https://igualdad.sede.gob.es/servicio?id=Registro-Electrónico-General` (HTTP 200). Its heading/description identify the `Registro Electrónico General` and `Acceso al Registro Electrónico General`; the page text explicitly names the `Registro Electrónico General de la Administración General del Estado (REG-AGE)` and its public access link targets `https://reg.redsara.es/`.
- The existing `reg-age-redsara` QA-only profile has immutable start URL `https://reg.redsara.es/es/`; a bounded unauthenticated GET of that exact canonical URL returned HTTP 200 on 2026-08-17.
- The generic AC2/AutoFirma assets visible on the Igualdad Sede are not used as implementation evidence here. Previous public research showed their pre-auth signer ABI is incomplete, so this change relies only on the explicit REG-AGE delegation.

## Bounded implementation

`ES-PUB-0067` remains institutional at `https://igualdad.sede.gob.es/` for `entryUrl` and records the first-party REG service page as `procedure_page`. QA launches only the exact existing `reg-age-redsara` start URL `https://reg.redsara.es/es/`. No Igualdad signing origin, signer ABI, endpoint, signature format/algorithm, callback, certificate-selection rule, or client-TLS rule is added or inferred.

Status is `IMPLEMENTED_NOT_E2E` / `E2E_PENDING`. No credentials, certificate selection, private key, login flow, POST, upload, signature, payment, or administrative submission was performed.

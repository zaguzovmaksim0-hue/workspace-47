# MIVAU → REG-AGE delegation evidence — 2026-08-17

## Current public surface

Public unauthenticated inspection of `https://mivau.sede.gob.es/` identified the current official Sede Electrónica del Ministerio de Vivienda y Agenda Urbana. A fresh Chromium 149 off-the-record context exposed, through the rendered DINTEL shadow-DOM menu, the first-party service page:

`https://mivau.sede.gob.es/servicio?id=Registro-Electr%C3%B3nico-General`

The same fresh browser session showed the public service description for the Registro Electrónico General de la Administración General del Estado. No credentials, certificate, login, signature, POST, upload, payment or administrative submission was used.

## Exact delegation proof

The public first-party service page contains exactly one REG anchor relevant to the service:

- text: `Registro Electrónico`
- href: `https://reg.redsara.es/`

The REG root is locale-negotiated. A fresh off-the-record Chromium navigation with Spanish `Accept-Language` produced this document chain:

`https://reg.redsara.es/` → HTTP 302 → `https://reg.redsara.es/es/`

An independent public HEAD request with `Accept-Language: es-ES,es;q=0.9` produced the same `Location: https://reg.redsara.es/es/`, and direct HEAD to `/es/` returned HTTP 200. The final Spanish URL is exactly the committed `startUrl` of existing QA-only profile `reg-age-redsara`.

The default headless browser locale independently demonstrated that the root can negotiate another language (`/en/`), which is why the institutional root link itself is not stored as the executable alias target. Workspace-47 binds the alias only to the existing exact Spanish profile start URL.

## Browser/network boundary

The root Sede was inspected in a new BrowserContext created through Chrome DevTools Protocol over a dedicated Chromium process and temporary profile; the BrowserContext was off-the-record and disposed after each probe. The root page loaded the first-party ACCEDA2 resources `ac2-commons.js`, `ac2-detalleExpediente.js`, `ac2-formularios.js`, and `ac2-usuariosLogin.js`, plus DINTEL UI 2.1.0 resources. No service-specific signing constants were needed or inferred because the decisive public operation is an explicit delegation to REG-AGE.

The browser network pass observed only public document/script resources plus the site's own Dynatrace telemetry. No protected XHR, signature service, Storage/Retrieve, certificate-selection endpoint or administrative submit endpoint was invoked.

## Bounded implementation

`ES-PUB-0076` is represented only as an `ALIAS_ONLY` QA catalog binding to existing profile `reg-age-redsara`. The official MIVAU REG service page remains the `entryUrl`; `launchUrl` is the exact existing canonical start URL `https://reg.redsara.es/es/`.

No MIVAU-specific signing origin, algorithm, signature format, callback, certificate rule, client-TLS rule or endpoint is inferred. In particular, `https://mivau.sede.gob.es` is not added to the REG-AGE cryptographic initiator/trust allowlist.

Status is strictly `IMPLEMENTED_NOT_E2E` / `E2E_PENDING`. Physical end-to-end acceptance remains unverified.

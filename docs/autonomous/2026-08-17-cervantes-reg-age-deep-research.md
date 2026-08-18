# Instituto Cervantes → REG-AGE deep public research — 2026-08-17

## Decision

`ES-PUB-0049` is an `ALIAS_ONLY` candidate to the existing QA-only `reg-age-redsara` profile. The decisive evidence is the current Instituto Cervantes Sede service page itself; no compatibility conclusion is derived merely from the `*.sede.gob.es` hostname, AC2 branding, or generic AutoFirma code.

## Static first-party map

Public unauthenticated GETs on 2026-08-17:

- `https://cervantes.sede.gob.es/` → HTTP 200, `Sede Electrónica del Instituto Cervantes - Inicio`, SHA-256 `244d2437bb44c2d06edd0618efea96af7b7cfb0f176e1d656a48bd7f0d42d684`.
- `https://cervantes.sede.gob.es/servicio?id=Registro-Electrónico-General` → HTTP 200, SHA-256 `698a41eed7e1650689f2d46e8a94dbde4aaae91b22da0870f154f2fb896dea3a`. It explicitly calls the service `Registro Electrónico General de la Administración General del Estado (REG-AGE)` and publishes `Acceso al Registro Electrónico` to `https://reg.redsara.es/`.
- `https://reg.redsara.es/es/` → HTTP 200, `REG - Registro Electrónico General`, SHA-256 `b183ad9ef7533adc1fc6746b7733e1ad63dc109c104c405f7ac37b525042c897`.

Directly loaded first-party Cervantes JavaScript was fetched and hashed:

- `ac2-commons.js` → `a0602be1828809d6d6e5705175c30646361662104427f8ff42745be9d7e70156`
- `ac2-detalleExpediente.js` → `a94e39ed8bf7301ac4383e845dfa9b86863ff06168adcd43419916fb39cbf962`
- `ac2-formularios.js` → `ac1983eb5ed614c9f446ebbfbea38160a4d28ea99080cbb2ed0adf8a62d1c7cc`
- `ac2-usuariosLogin.js` → `0dcb1bda71626e301181230c123c789454600430cb9e1cb7d0bbd4b0befc8a92`
- Dynatrace `ruxitagentjs_ICANVfgqru_10341260622154106.js` → `1fa6408a3db61afadafb7c02daecb70d50fc971e5aae50ed5bcd8bdcf9efaee9`

No form or iframe exists on the public REG-AGE service page. The loaded AC2 scripts contain generic authenticated-form/AutoFirma code and private-process REST paths, but those paths were not invoked and are not used as evidence for a Cervantes-specific signing contract.

## BROWSER_PUBLIC_RUNTIME pass

A real Playwright/Chromium public session loaded the first-party REG-AGE service page unauthenticated. The accessibility tree showed `Iniciar sesión` / `Área privada` separately from the public service and the service content explicitly named REG-AGE. The public `Registro Electrónico` link had exact href `https://reg.redsara.es/`.

Clicking only that public external link opened the current `REG - Registro Electrónico General` application without login or certificate use. The browser network recorded:

1. `GET https://reg.redsara.es/` → HTTP 302.
2. With the fresh browser's English locale, `Location: https://reg.redsara.es/en/` → HTTP 200.
3. A bounded public GET with `Accept-Language: es-ES,es;q=0.9` proved the same root negotiates exactly to `Location: https://reg.redsara.es/es/`.
4. Direct `GET https://reg.redsara.es/es/` → HTTP 200 and the same REG application title.

The browser loaded public static REG assets and `GET https://reg.redsara.es/config/config.json` successfully. No REG login/auth action was entered.

The Cervantes page itself emitted Dynatrace telemetry POSTs automatically as page instrumentation; these were not replayed, inspected for private content, or used as contract evidence. No administrative POST, form submit, upload, signing operation, certificate selection, payment, or authenticated/session replay was triggered by the worker.

## Runtime boundary

The public AC2 script graph exposes generic code paths for authenticated procedures, including AutoFirma-related functions and private-process REST paths such as `/.rest/formulario/...`. Those code paths require authenticated procedure state and/or later user actions; invoking them would cross the research boundary. They are therefore deliberately excluded from the alias contract.

No new profile is necessary. The exact public delegation is sufficient: preserve Cervantes as institutional `entryUrl`, record the Cervantes REG-AGE service as procedure evidence, and launch only the existing profile canonical start `https://reg.redsara.es/es/`. `cervantes.sede.gob.es` is not added to REG-AGE signing trust.

## Truthful status

The implementation is `IMPLEMENTED_NOT_E2E` / `E2E_PENDING`. A safe physical accepted-flow E2E has not been performed.

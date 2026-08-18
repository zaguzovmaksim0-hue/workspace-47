# Museo Nacional Centro de Arte Reina Sofía → REG-AGE deep public research — 2026-08-17

## Decision

`ES-PUB-0080` is an `ALIAS_ONLY` implementation candidate to the existing QA-only `reg-age-redsara` profile. The decisive current evidence is the Museo Reina Sofía Sede service page itself. No compatibility conclusion is derived merely from the `*.sede.gob.es` hostname, common AC2 assets, or generic AutoFirma code.

## Static first-party map

Public unauthenticated GETs on 2026-08-17:

- `https://museoreinasofia.sede.gob.es/` → HTTP 200, title `Sede Electrónica del Museo Nacional Centro de Arte Reina Sofía - Inicio`, SHA-256 `6de37d1985f3be0a0faf36b4de99c8f9ce4e76493682d25a813f11bf6b173fae`.
- `https://museoreinasofia.sede.gob.es/servicio?id=Registro-Electrónico-General` → HTTP 200, title `Sede Electrónica del Museo Nacional Centro de Arte Reina Sofía - Servicio`, SHA-256 `5d9b20c5ce6995fc90025a2a937105a87c59297aef4f4c8b58e8acc75411c8bc`. It explicitly calls the service `Registro Electrónico General de la Administración General del Estado (REG-AGE)` and publishes `Acceso al Registro Electrónico` to `https://reg.redsara.es/`.
- `https://museoreinasofia.sede.gob.es/categoria?idCat=100202` (`Otras solicitudes`) → HTTP 200, SHA-256 `abf171236ec6770ceea978dfab2b2a7a849c8e1a95058aa98061a2b255cdc5a4`.
- `https://museoreinasofia.sede.gob.es/categoria?idCat=100203` (`Registro electrónico`) → HTTP 200, SHA-256 `b35a2eda84efec3b9b6eb37c972827ad6a85f535eafebc374eb152c80e02b1bb`.
- `https://reg.redsara.es/es/` → HTTP 200, title `REG - Registro Electrónico General` during this pass.

Directly loaded first-party JavaScript on the decisive Sede surface was fetched and hashed:

- `ac2-commons.js` → `a0602be1828809d6d6e5705175c30646361662104427f8ff42745be9d7e70156`
- `ac2-detalleExpediente.js` → `a94e39ed8bf7301ac4383e845dfa9b86863ff06168adcd43419916fb39cbf962`
- `ac2-formularios.js` → `ac1983eb5ed614c9f446ebbfbea38160a4d28ea99080cbb2ed0adf8a62d1c7cc`
- `ac2-usuariosLogin.js` → `0dcb1bda71626e301181230c123c789454600430cb9e1cb7d0bbd4b0befc8a92`
- Dynatrace `ruxitagentjs_ICANVfgqru_10341260622154106.js` → `1fa6408a3db61afadafb7c02daecb70d50fc971e5aae50ed5bcd8bdcf9efaee9`

The public REG-AGE service page contains no HTML form and no iframe. Its loaded script graph includes generic AC2 code for authenticated procedure flows, including AutoFirma references and private-process `/.rest/...` paths. Those code paths were not invoked and are not evidence for a Museo-specific signing ABI.

## BROWSER_PUBLIC_RUNTIME pass

A real Playwright/Chromium unauthenticated session loaded the first-party REG-AGE service page. The accessibility tree showed `Iniciar sesión` / `Área privada` separately from the public service and exposed the exact `Registro Electrónico` link with href `https://reg.redsara.es/`.

Following only that public link opened the current `REG - Registro Electrónico General` application without login, credentials, certificate selection, or signing. A direct browser navigation to the published REG root recorded:

1. `GET https://reg.redsara.es/` → HTTP 302.
2. With the browser's English locale, `Location: https://reg.redsara.es/en/` → HTTP 200.
3. A bounded public GET with `Accept-Language: es-ES,es;q=0.9` proved the same root negotiates exactly to `Location: https://reg.redsara.es/es/`.
4. Direct `GET https://reg.redsara.es/es/` → HTTP 200 with title `REG - Registro Electrónico General`.

The REG runtime also loaded public static assets plus `GET https://reg.redsara.es/config/config.json` successfully.

## Runtime / handler boundary

A browser runtime inspection on the public Museo REG-AGE service page established:

- `forms: []`
- `iframes: []`
- exact public REG link: text `Registro Electrónico`, href `https://reg.redsara.es/`, target `_blank`, no inline `onclick`
- `window.AutoScript`, `window.MiniApplet`, `window.doSignAsPromise`, `window.SignParams`, `window.CLAVE_VALIDATE_URL`, `window.contextPath`, `window.CONTEXT_PATH`, and `window.linkPrefix` were all `undefined` on this public service runtime.

The complete loaded first-party AC2 script graph was separately searched and does contain generic authenticated-flow helpers such as `startAutofirma`, `doSignAsPromise` call sites, `signatureB64`, Cl@ve validation redirects, and `/.rest/formulario/...` endpoints. Invoking those paths would require later authenticated/procedure state and is outside the alias contract. No such endpoint was replayed or triggered.

The browser emitted Dynatrace telemetry POSTs automatically as page instrumentation. They were not replayed, used as contract evidence, or treated as administrative actions.

## Boundary proof and implementation scope

The bounded alias contract is fully public before authentication: the Museo itself names REG-AGE and delegates to the official REG root, whose Spanish locale negotiation resolves exactly to the existing `reg-age-redsara` canonical `startUrl`.

Therefore no new profile or adapter is needed. The implementation preserves `https://museoreinasofia.sede.gob.es/` as the institutional `entryUrl`, records the first-party REG-AGE service as procedure evidence, and launches only `https://reg.redsara.es/es/` through the existing profile. `museoreinasofia.sede.gob.es` is not added to REG-AGE cryptographic trust or initiator origins.

No credentials, Cl@ve login, certificate selection/use, signing, file upload, payment, administrative POST/submission, or private/session replay was performed.

## Truthful status

The implementation remains `IMPLEMENTED_NOT_E2E` / `E2E_PENDING`. A physical accepted-flow E2E has not been performed.

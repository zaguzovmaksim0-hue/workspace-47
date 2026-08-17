# OEPM ProtegeO general-purpose launch — deep public research — 2026-08-17

## Decision

`ES-PUB-0082` is a `NEW_PROFILE` candidate whose implemented contract is deliberately limited to public QA navigation into the exact current ProtegeO general-purpose launch. It is not a REG-AGE alias and it does not model certificate authentication or signing.

The current OEPM eSede card publishes an exact ProtegeO launch and marks it `Acceso permitido con clave` while also displaying `No requiere certificado electrónico`. The same page still contains older detailed prose describing Java Applet, legacy browser/Java requirements and certificate signing. Because current card/runtime evidence conflicts with that legacy prose, no signing or certificate constant is inferred from it.

## Static first-party map

Public unauthenticated GETs during this pass:

- `https://sede.oepm.gob.es/` → HTTP 302 to `https://sede.oepm.gob.es/eSede/es/index.html`, then HTTP 200; title `Sede electrónica OEPM`; final body SHA-256 `c36b58a031dd8e976341cb3dd42b71addd292669e17f55178559123bed531e93`.
- `https://sede.oepm.gob.es/eSede/es/tramites-comunes/solicitud-electronica-de-proposito-general-remitida-a-la-oepm-/` → HTTP 200; title `Sede Electrónica`; SHA-256 `1241a6414d701935e3b4d9c0f8eb199819f91e4e5c4de595d937684051cf902f`.
- `https://sede.oepm.gob.es/eSede/es/tramites-comunes/` → HTTP 200; SHA-256 `7f1f5ae282d4a2cce26a1343e74a048bbbc50b1f9ae7ba8e4dbd79a2f3807e28`.
- exact first-party application link: `https://sede.oepm.gob.es/ProtegeOWeb/inicio.html?tipoTramite=SOLIC_PROP_GEN_OEPM` → HTTP 200, title `ProtegeO`, footer `ProtegeO v.1.71.1`.

The current procedure card's exact markup exposes the ProtegeO link plus icons titled `Acceso permitido con clave`, `No requiere certificado electrónico`, `No Requiere pago` and `No dispone de formulario`.

Current public ProtegeO static resources were fetched without session path parameters and hashed:

- `https://sede.oepm.gob.es/ProtegeOWeb/static/resources/js/scripts.js` → SHA-256 `947be1e6fa15662d649290e962fcb5339b679ff87bc4ccfa797844a90c597b1c`.
- `https://sede.oepm.gob.es/ProtegeOWeb/static/resources/js/protegeo.js` → `92e336b77a665f9adef52508aeb9ec1883b3fd11f8a4335f3f3f26fbe49711e3`.
- `https://sede.oepm.gob.es/ProtegeOWeb/static/resources/js/eventos.js` → `320bf643d9da5f067797dbc93f09cb2fd740f25bb04d31395d3c261d39cea58b`.
- `https://sede.oepm.gob.es/ProtegeOWeb/static/resources/js/fileinput.js` → `02eaa70a047e1f37ce282d5eb3b1d2fb8abe1d7a552a507aa322d3065ad9b925`.
- `https://sede.oepm.gob.es/ProtegeOWeb/static/resources/js/es.js` → `dbf6169f65773a285d4e16f68347d26ee0e0f70810d0451e8b9a086dd7e18082`.

The complete loaded application JS graph contained no `AutoScript`, `MiniApplet`, `ClienteFirma`, `AppletFirma`, AutoFirma marker, XAdES/CAdES/PAdES token, SHA signing algorithm constant, or current certificate/signing bridge on the public pre-POST page.

## BROWSER_PUBLIC_RUNTIME pass

A real public unauthenticated Playwright/Chromium session loaded the current OEPM procedure page and followed only its exact first-party `Solicitud de propósito general` link. This opened ProtegeO v1.71.1 with the matching procedure heading.

The first `Aceptar` only revealed the client-side `Nueva Presentación` panel. Before the second `Aceptar`, runtime inspection established:

- one form `#formulario`;
- action `https://sede.oepm.gob.es/ProtegeOWeb/inicio?tipoTramite=SOLIC_PROP_GEN_OEPM`;
- method `POST`;
- only three current hidden fields: `destino`, `continuar`, `borrador`, all empty;
- `AutoScript`, `MiniApplet`, `clienteFirma`, `ClienteFirma`, `SignParams`, `afirma` and `AppletFirma` were undefined globals.

The current inline handler is simply `iniciar()`, which displays a loading element and invokes `document.forms['formulario'].submit()`.

## Network and non-destructive boundary trace

Normal browser traffic before the protected transition consisted of public GETs for the ProtegeO document, CSS, JavaScript, fonts and images. Runtime-generated `;jsessionid=...` path parameters and cookies were transient and are intentionally absent from durable evidence.

For the second `Aceptar`, `form.submit()` was replaced in-memory with a recorder before activation. Clicking the real UI control then recorded the exact intended method/action/three hidden fields and did not issue the POST. A subsequent network inventory contained no such POST. This proves the first unknown seam is the server response to the protected `POST /ProtegeOWeb/inicio?tipoTramite=SOLIC_PROP_GEN_OEPM`, while respecting the read-only boundary.

No credentials, login, Cl@ve authentication, certificate selection/use, signing, upload, payment or administrative submission occurred.

## Implementation boundary

The exact public launch contract is sufficient for a navigation-only profile:

- profile start is the exact published ProtegeO URL;
- only `sede.oepm.gob.es` is the initiator origin;
- profile is `QA_ONLY` and `VERIFIED_CONTRACT`;
- `capabilities`, `operationPolicies`, `endpoints`, and `clientAuthPolicy` are empty/null;
- no signing adapter or certificate-auth bridge is added;
- schema-required `certificateRules` are inert metadata because no certificate capability exists, and therefore do not assert an OEPM certificate contract.

The later POST/authentication/signature behavior is intentionally outside the implementation. Any future promotion of those sensitive seams requires fresh current evidence and the applicable authorization.

## Truthful status

The catalog record is `IMPLEMENTED_NOT_E2E / E2E_PENDING`. No physical accepted-flow E2E was performed.

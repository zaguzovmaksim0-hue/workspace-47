# Ourense Cl@ve REAL E2E recipe evidence — 2026-09-02

Scope: fresh public unauthenticated navigation only. No client certificate was sent and no administrative submission was performed.

Catalog/profile: `diputacion-ourense-sede` / `diputacion-ourense-sede`.

The current reviewed STA procedure page exposes one exact top-level authentication link:

- label `Identificate`;
- href `https://sede.depourense.es/sta/CarpetaPrivate/Login?APP_CODE=STA&PAGE_CODE=HOME`.

Fresh GET of that login URL returns HTTP 200 and contains only an automatic Cl@ve redirect form at the authentication boundary:

- `<body onload="document.redirectForm.submit();">`;
- `form name="redirectForm" method="post"`;
- action `https://pasarela.clave.gob.es/Proxy2/ServiceProvider`.

The existing QA profile already trusts the reviewed Cl@ve redirect origin and pins the subsequent client-auth transition from `pasarela.clave.gob.es/Proxy2/ServiceRedirect` to `pasarela-ident.clave.gob.es/IdP2/AuthenticateCitizen`.

Therefore the REAL E2E recipe performs only the exact `Identificate` click. It deliberately does not choose a direct STA certificate link, Portafirmas, document presentation, or any later administrative action; all later authentication navigation is left to the portal and the existing profile-scoped authorizer.

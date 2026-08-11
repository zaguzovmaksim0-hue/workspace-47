# Generation 45 — Castilla y León public AutoFirma checker research — 2026-08-11

## Scope and safety boundary

This research used only unauthenticated HTTPS GET requests to current first-party Junta de Castilla y
León pages and static JavaScript. The public signature simulator itself was **not executed**: no
certificate selector was opened, no local signature was produced, and no POST, server validation,
credential, cookie, upload, or administrative action was performed. The embedded sample PDF is not
copied into tracked documentation.

## Official public launch and exact checker

The current `Tramita Castilla y León` technical-requirements page links the official checker at
`https://www.ae.jcyl.es/reqae2` as the tool for simulating an AutoFirma/Cl@ve Firma signature. The
checker returned HTTP 200 and loads current first-party AutoFirma integration resources including:

- `firmaScript/config/remotoAutoScript1_9.js` — SHA-256
  `d3d7f696bd7bfcf8206fe1668ffadc3a049e6112bb5789529846bbd804a2d346`;
- `javascript/rae_CheckFirma.js` — SHA-256
  `cf4bdff5035d6743effc4827cdaefba9e2ca81dc1e15e5f3081741c8f6491404`;
- `javascript/rae_SoftwareCliente.js` — SHA-256
  `ea81a443b5fa54d180c3b53c8d0342ac4fd6853da8796d06df7bf99a385768fa`;
- `firmaScript/autoscript1_9/JCYLfirma.js` — SHA-256
  `a25fb898baadb99532f21de6e3abd25394cb371093a6e3e859a7f716560b4d68`.

`JCYLfirma.js` directly proves the browser-local seam: its `sign` method calls
`AutoScript.sign(JCYLdata, JCYLsignatureAlgorithm, JCYLsignatureFormat, JCYLparams, ...)`.
`remotoAutoScript1_9.js` currently initializes the checker with `SHA512withRSA`, `PADES`, empty
parameters followed by `includeOnlySignningCertificate=true`, `expPolicy=FirmaAGE`,
`filters.1=nonexpired:;signingCert:;`, and `filters.2=dnie:;`. It configures current same-origin
Storage/Retrieve services, minimum AutoScript client version `1.9`, and loads AutoFirma before running
the simulation callback.

The checker UI defaults confirm the normal single-signature PAdES branch: `Simulacionxades`,
`Simulacionlotes`, `headless`, `sinData`, DNI-specific filtering, and forced intermediate-server mode
are all unchecked; only `excluirCaducados` is checked. The normal branch therefore supplies the
embedded public sample PDF as data. The optional legacy XAdES branch is separately explicit in source
and is not treated as the default contract.

## Callback and server boundary

After AutoFirma returns a signature, `firmaCorrectaCallback` stores it and invokes a first-party
server helper to extract the signing certificate before dispatching the page callback. The checker
then calls `validarFirma(signatureB64)`, which performs a POST to `validarFirmaServlet`. Neither POST
was executed. This means the public checker exposes a concrete browser-local signing ABI, but its full
successful simulation also depends on post-sign server actions outside the autonomous safety boundary.

## Product interpretation

This is strong exact evidence for a **technical compatibility checker**, not for an active citizen
administrative procedure. The existing inventory deliberately keeps the technical checker separate
from promotion of the broad `castilla-leon-tramita` surface (`ES-PUB-0102`) absent a current citizen
procedure binding. Accordingly this generation makes no profile, registry, inventory, catalog,
release, or E2E change. `ES-PUB-0102` remains `BROWSE_ONLY`; the checker remains a high-quality
research lead that can support future diagnostics or a separately justified exact-surface profile if
product scope explicitly includes technical simulators.

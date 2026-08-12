# Generation 45 — Euskadi Registro General / firmaGiltza public contract research — 2026-08-11

## Scope and safety boundary

This research used only unauthenticated HTTPS GET requests to current first-party Euskadi public pages,
the public Registro General form HTML, and static JavaScript. No form field was filled, no form action
was triggered, no certificate selector or signing component was launched, and no POST, upload,
authentication, credential/cookie use, signature, payment, or administrative submission occurred.

## Current citizen procedure binding

The current Euskadi Sede links the official `Registro Electrónico General` page. That page returned
HTTP 200 and identifies procedure `1017701`. Its live electronic-registration flow links both the
public assistant and the current form URL under `x43kToolkitWar`. The current form itself is publicly
GET-readable before user input and returned HTTP 200.

The form publishes `ISNUEVAFIRMAACTIVA="true"` and a `createSignIdazkiAction` behaviour pointing to
current first-party `firmaGiltza` HTML/JavaScript. The current behaviour/plugin code builds the signing
configuration with application `x43k`, iframe mode, `skip_server_id=true`, `is_hash=false`, and
`mecanismo_firma="auto"` when no explicit mechanism is supplied. For the active new-signing path,
files referenced by location or Base64 are classified as `xades-enveloping`; the older fallback paths
use XAdES detached/enveloped forms.

Key current public assets observed:

- public Registro form HTML — SHA-256
  `2285d9841ae99315d3c68bd6a16fedac02d21d9a8d67b80a21f8ece7a33c3545`;
- `firmaGiltza/statics/js/ventanaFirmaGiltza.js` — SHA-256
  `7ad429c07be0fad0a7bdf16c8bd4409a5415d3b2feb408c1e29ea6656422e607`;
- `firmaGiltza/statics/js/firmaGiltza.js` — SHA-256
  `3ecdc8155df399b88819fcc1c66ea21513a216f796e381315c626c0d54ccabe6`;
- `firmaGiltza/statics/js/firmaGiltzaUtils.js` — SHA-256
  `25f0e213ee967d3aecda9c8b1f2b60ad83138c633defa4f9adac003871155ab6`;
- `x43kToolkit/plugins/behaviours_plugin.js` — SHA-256
  `b1002e617983c297af0bc4d450f4c009f046bd14098aced67fcce9cff6b3c72e`.

## Server-mediated boundary

`ventanaFirmaGiltza.js` constructs a Base64 configuration containing the file references, origin,
signing mechanism, application, language, environment and mode, then hands that configuration to the
same-origin `x43faGiltzaWar` server flow. Public JavaScript exposes initialization/job/download
endpoints and returns signing results to the parent through the configured callback/message channel.
The form's result callback then calls its own `sign/move` server endpoint before associating returned
signatures with form objects.

This public code proves the current citizen procedure uses the Giltza integration and XAdES Enveloping
for the active new-signature path. It does **not** expose a complete autonomous local-signature ABI:
actual document references/configuration and the server-issued signing transition depend on form state,
and the public static code does not fix the cryptographic signature algorithm required for that live
operation. Those stateful/server calls were not executed.

## Result

`euskadi-sede-electronica` (`ES-PUB-0115`) remains `BROWSE_ONLY` / research-only. Evidence quality is
substantially stronger than the prior general-help baseline because a live citizen Registro procedure
is now bound to the exact Giltza/XAdES-Enveloping architecture. However, the missing stateful document
configuration/server transition and unresolved exact cryptographic algorithm prevent a truthful
Junta Firma Mobile profile or protocol adapter from being implemented from public unauthenticated
static evidence alone. No registry, catalog, release, or E2E status is promoted.

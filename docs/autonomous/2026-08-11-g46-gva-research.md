# Generalitat Valenciana procedure login-boundary research — generation 46 — 2026-08-11

## Scope and safety boundary

This slice used only official public, unauthenticated HTTPS GET requests against `sede.gva.es` and
`www.tramita.gva.es`, plus static JavaScript loaded by the public login page. No login option was
activated; no certificate identity was selected; no POST/form submission, credential, certificate,
cookie, signature, upload, payment, administrative submission, APK launch, ADB, or device-control
workflow was used. Temporary public response bodies were deleted after bounded inspection. Ephemeral
session identifiers emitted by the service were not retained in repository evidence.

## Exact current procedure and transition

Inventory surface `ES-PUB-0108` (`gva-sede`) remains `BROWSE_ONLY`. Its current inventory procedure
`https://sede.gva.es/es/detall-tramit?id_proc=15602` returned HTTP 200 with SHA-256
`d28abfa40747cc45a1d7f7d638ae9a1f3e20256841457aae8eb76018f710b8fb`. It is the live
`Solicitud de reconocimiento de la titularidad compartida de explotaciones agrarias en la Comunitat
Valenciana` procedure. The public page mentions electronic signature and publishes the exact
first-party tramitation start under `www.tramita.gva.es` for procedure 15602.

A GET to that published start URL returned one redirect to the assistant's `login.html` and then HTTP
200. The fetched public login page had SHA-256
`8c6fb3301cf24ae1efcfa16f35fcba6e8e7ac3e245256758caf9040802571ba8`. No session identifier
from the redirect target is retained here. The login HTML contains no AutoFirma, AutoScript,
MiniApplet, `SHA*withRSA`, XAdES, PAdES, or CAdES contract marker.

## Static login assets and accepted-systems guidance

The login page loads three same-origin JavaScript files that were fetched and inspected:

- `jquery-1.8.3.min.js`: HTTP 200, SHA-256
  `61c6caebd23921741fb5ffe6603f16634fca9840c2bf56ac8201e9264d6daccf`;
- `jquery.imc.comuns.js`: HTTP 200, SHA-256
  `0a2ed83250e0187443a1efa02071d830ad9a12a9df08ad90e042e42271fecd7e`;
- `jquery.imc.error.js`: HTTP 200, SHA-256
  `9da2070069af12df76a80673f5a0434de9a97c9f5c6ee84e1806e090d88798e7`.

None defines or references AutoFirma, AutoScript, MiniApplet, a `SHA*withRSA` signing algorithm,
XAdES, PAdES, or CAdES. A loose `firma` substring match inside `jquery.imc.comuns.js` came from the
Catalan UI word `confirmacio`; it is not an electronic-signature operation and was rejected as
contract evidence.

The current official accepted-identification/signature systems page
`https://sede.gva.es/es/sistemes-d-identificacio-i-signatura-acceptats` returned HTTP 200 with SHA-256
`602c5878365c3fa1e6efedf6d75329c738e289c3bff1b93ea98ca08b56a8a88a`. It confirms certificate,
Cl@ve, and electronic-signature support at policy level, but does not publish the exact browser-local
contract for procedure 15602.

## Contract conclusion

The current public evidence binds `ES-PUB-0108` to a live procedure and exact electronic-tramitation
entry, but the first transition is login and no local signing ABI is exposed before that boundary.
The algorithm, signature format/mode, payload, callback, result-delivery endpoint, and transport remain
unverified. No Client-TLS conclusion is drawn merely from certificate terminology.

`ES-PUB-0108` therefore remains `BROWSE_ONLY` / research-only. No `SiteProfile`, catalog binding,
origin allowlist, bridge capability, inventory status, or release state is changed. The next safe gate
is a first-party unauthenticated signing invocation reachable before login or form submission; no
missing value may be inferred from generic GVA or AutoFirma behavior.

## Queue impact

No new implementation-ready portal was promoted. The classified research buffer remains at least 16
surfaces. Exact implementation priority remains Sevilla ATSE after acceptable terminal Codex Cloud
evidence, preserved Melilla STA, then `extremadura-tramites` (`ES-PUB-0109`).

# Ceuta signed-procedure pre-auth research — generation 46 — 2026-08-11

## Scope and safety boundary

This slice used only official public, unauthenticated HTTPS GET requests against `sede.ceuta.es` and
its first-party static assets. No authentication control was activated; no POST/form submission,
credential, certificate, cookie, signature, upload, payment, administrative submission, APK launch,
ADB, or device-control workflow was used. Temporary public response bodies were deleted after bounded
inspection.

## Exact public procedure

Inventory surface `ES-PUB-0106` (`ceuta-sede`) remains `BROWSE_ONLY`. Its recorded next gate was a
concrete signed-procedure inspection because general requirements already named AutoFirma but did not
prove an invocation contract.

The current inventory procedure
`https://sede.ceuta.es/controlador/controlador?cmd=tramite&modulo=tramites&tramite=ANI` returned HTTP
200 with SHA-256 `9619d40b36f99a5bbc81271d988143410f50e2f0e454fe4e0d8b84f96b5504c1`. It is the live
`CERTIFICADO DE SERVICIOS PRESTADOS EN LA ADMINISTRACIÓN` procedure and explicitly offers
`Tramitar en línea`.

The page explains that online registration requires either a valid electronic certificate or Cl@ve.
The online button does not invoke a signer. Its JavaScript only opens the page's authentication modal.
The active modal path sends the user into the state identification service via Cl@ve/certificate. A
separate direct certificate/DNI-e branch is present only inside commented markup and is not an active
control. No authentication option was activated in this research slice.

## Current requirements and first-party JavaScript

The official technical-requirements page
`https://sede.ceuta.es/controlador/controlador?cmd=requisitos&modulo=info` returned HTTP 200 with
SHA-256 `c5e767a8021521024725d9d19e04a32a13d8af1f2d439cd5247a878687fca8bd`. It names a personal
or representative certificate or Cl@ve for access and explicitly names the AutoFirma desktop program
for electronic signature. This is product-level support evidence, not a procedure-local ABI.

The procedure loads first-party `https://sede.ceuta.es/lib/tsi/form.js?v=1.0`, which returned HTTP 200
with SHA-256 `b02ced30893e7d77516b76a9fd5066d6ca58ce29a578d05aa608947f2eececed`. Bounded inspection
found no AutoFirma, AutoScript, MiniApplet, `SHA*withRSA`, XAdES, PAdES, or CAdES call. A loose search
for `firmar` matched the generic UI helper name `msg_confirmar`; it is not a signing operation and was
not treated as contract evidence.

## Contract conclusion

The public procedure proves a live online-registration path and the sede-level requirement to use
AutoFirma for electronic signing, but the first procedure transition is authentication and no
browser-local signing ABI is exposed before it. The public evidence does not reveal the algorithm,
format/mode, payload, callback, result transport, or server endpoint needed for a safe profile.

`ES-PUB-0106` therefore remains `BROWSE_ONLY` / research-only. No `SiteProfile`, catalog binding,
origin allowlist, bridge capability, inventory status, or release state is changed. The next safe gate
is a first-party signing invocation reachable unauthenticated before a submission boundary; values
must not be inferred from generic AutoFirma defaults or from another administration.

## Queue impact

No new implementation-ready portal was promoted. The classified research buffer remains at least 16
surfaces. Exact implementation priority remains Sevilla ATSE after acceptable terminal Codex Cloud
evidence, preserved Melilla STA, then `extremadura-tramites` (`ES-PUB-0109`).

# Mallorca SEDIPUALB/SEGEX signing-boundary research — generation 46 — 2026-08-11

## Scope and safety boundary

This slice used only official public, unauthenticated HTTPS GET requests against the Consell de
Mallorca sede and the exact SEDIPUALB/SEGEX procedure linked by that sede. No POST form was submitted;
no identification method was activated; no credential, certificate, cookie jar, signing operation,
upload, payment, administrative submission, APK launch, ADB, or device-control workflow was used.
Temporary public response bodies were deleted after bounded inspection.

## Current sede and exact generic-register procedure

Inventory surface `ES-PUB-0120` (`mallorca-sede-electronica`) remains `BROWSE_ONLY`.
`https://seu.conselldemallorca.net/` returned HTTP 200 with SHA-256
`fbcf6609b446ef1e646b98421ec6949a1e652296204524e3f56d1983b30dec16`. The current sede publishes
its `registre electrònic` and links the Consell generic procedure through the SEDIPUALB/SEGEX service.

The public Consell link for generic procedure `12082` resolves to
`https://cim.secimallorca.net/segex/tramite.aspx?idtramite=12082`. That page returned HTTP 200; a
bounded fetch had SHA-256 `e524860ff594f0668d6be958ca2581dbdbe0ebbddeda361dcb7ae25455e89de9`.
It is explicitly branded as the Consell Insular de Mallorca electronic sede and presents an ASP.NET
POST form. The form was not submitted.

The public pre-submit procedure text is unusually specific about the product boundary:

- the user must identify with a digital certificate;
- the procedure explicitly says not to use Cl@ve because the instance could not then be signed;
- digital certificate is stated as the only valid mechanism for both identification and signing;
- the instance must be signed; and
- the page explicitly requires the Autofirm@ application for signing.

This is direct evidence for a live certificate-only + Autofirm@ signed procedure, but it is not yet an
exact browser-local signing ABI.

## First-party runtime JavaScript

The public procedure loads first-party SEGEX assets. Two implementation-relevant files were fetched:

- `https://cim.secimallorca.net/jscomun/formularios/formularios-2.10.js?v=03`: HTTP 200, SHA-256
  `3ac1768278f3a3bd26b4e7ffd9725c49ac95679e2a6a23656f64fc82369f8a9e`;
- `https://cim.secimallorca.net/js/sedeelectronica.js`: HTTP 200, SHA-256
  `364c2bdc4d03e4660da43b0f5e85862035b95b2c35c40c735ecbe5f993a83942`.

Neither contains AutoFirma/AutoScript/MiniApplet, `SHA*withRSA`, XAdES, PAdES, CAdES, a local signer
callback, or a signing transport. The signer appears to be reached only after the current public POST
boundary, which was not crossed.

## Contract conclusion

`ES-PUB-0120` now has strong first-party evidence for an exact live generic-register procedure that
requires certificate-only identification/signing and Autofirm@. However, the public pre-submit state
still does not expose algorithm, format/mode, payload semantics, callback, result-delivery endpoint,
or transport. Those values must not be inferred from Autofirma defaults, another SEDIPUALB tenant, or
another Spanish administration.

The surface therefore remains `BROWSE_ONLY` / research-only. No `SiteProfile`, origin allowlist,
catalog binding, bridge capability, inventory status, or release state is changed. Mallorca should be
kept as a high-priority research lead for any future first-party pre-auth signer asset or official
technical contract that closes the missing ABI without crossing the POST/authentication boundary.

## Queue impact

No new implementation-ready portal was promoted. The classified research buffer remains at least 16
surfaces. Exact implementation priority remains Sevilla ATSE after acceptable terminal Codex Cloud
evidence, preserved Melilla STA, then `extremadura-tramites` (`ES-PUB-0109`). Mallorca is an
additional strong research lead behind those implementation-ready slices.

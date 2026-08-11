# Menorca public tramitation boundary research — generation 46 — 2026-08-11

## Scope and safety boundary

This slice used only official public, unauthenticated HTTPS GET requests. No authentication,
credential, certificate, cookie jar, form submission, signing, upload, payment, administrative
submission, APK launch, ADB, or device-control workflow was used. Redirect inspection recorded only
HTTP status plus sanitized origin/path; redirect parameter values were not persisted.

## Current sede and public online-service boundary

Inventory surface `ES-PUB-0118` (`menorca-sede-electronica`) remains `BROWSE_ONLY`.
`https://seuelectronica.cime.es/` returned HTTP 200 with SHA-256
`7e37ca4afe8280a98770f462fdfbbbbac74c02c5e5f1cc84807dd7427ff3ed59`. The page is the current
Consell Insular de Menorca electronic-sede entry and does not expose AutoFirma, AutoScript,
MiniApplet, `STAAutofirmaLote`, `signBatchProcess`, `SHA*withRSA`, XAdES, PAdES, or CAdES markers.
Its loaded first-party scripts are ordinary portal assets (`jquery`, menu, text-size, and load helpers),
not a public local-signature client.

The sede's current `Tràmits en línia - Carpeta Ciutadana` control points to the separately hosted
public service `https://www.carpetaciutadana.org/web/gesserveis/gserveis.aspx`. A bounded GET without a
cookie jar did not reach stable public procedure content: the service returned HTTP 302 back to the
same origin/path on each of six bounded hops. No form or authentication transition was activated and
no redirect value was retained.

## Contract conclusion

The current official sede therefore proves the online-service handoff but does not expose a
procedure-specific signing ABI on `seuelectronica.cime.es`. The linked citizen-folder service also
did not yield a stable unauthenticated public procedure surface in the bounded no-cookie check. No
algorithm, format/mode, payload, callback, endpoint, batch contract, certificate requirement, or
Client-TLS behavior can be inferred safely from this evidence.

`ES-PUB-0118` remains `BROWSE_ONLY` / research-only. No `SiteProfile`, origin allowlist, catalog
binding, bridge capability, inventory status, or release state is changed. The next safe gate is a
stable official public procedure page or first-party contract surfaced without authentication or
submission.

## Queue impact

No new implementation-ready portal was promoted. The classified research buffer remains at least 16
surfaces. Exact implementation priority remains Sevilla ATSE after acceptable terminal Codex Cloud
evidence, preserved Melilla STA, then `extremadura-tramites` (`ES-PUB-0109`).

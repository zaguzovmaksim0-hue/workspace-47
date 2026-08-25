# ICO public sede navigation evidence — 2026-08-25

## Scope

This review covered only a public unauthenticated `GET` of the official ICO
electronic-sede entry:

- entry URL: `https://sedeico.gob.es/web/sedeico`
- transport: HTTPS, normal certificate validation, no TLS bypass
- observed result: same-origin redirect to `https://sedeico.gob.es/`, then HTTP 200 public HTML

No credentials, cookies, certificates, private keys, login, certificate
selection, signing, upload, payment, form submission, or administrative
transaction was used.

## Bounded implementation contract

Workspace-47 adds the QA-only profile `ico-sede-public-navigation` with the
exact published entry URL and origin `https://sedeico.gob.es`. Same-origin
browsing admits the observed transition to the root; no external redirect
origin is trusted. The profile declares no capabilities, operation policies,
endpoints, client TLS policy, signing adapter, or web-message bridge.

The release registry intentionally excludes this `QA_ONLY` profile. The public
catalog records `IMPLEMENTED_NOT_E2E` / `E2E_PENDING`; the implementation proves
the bounded navigation contract, not acceptance of any real administrative
result.

## Remaining gate

An authorized physical check may later confirm the exact entry launch and
same-origin transition in the app. It must remain limited to public navigation
and must not log in, select a certificate, sign, upload, or submit a real
request.

Primary source: <https://sedeico.gob.es/web/sedeico>.

# La Gomera public sede navigation evidence — 2026-08-25

## Scope

This review covered only a public unauthenticated `GET` of the official
electronic-sede entry:

- URL: `https://lagomera.sedelectronica.es/info.0`
- transport: HTTPS, normal certificate validation, no TLS bypass
- observed result: the first same-origin response establishes a temporary
  session cookie; the next GET returns HTTP 200 on the same public URL

The session cookie was used only transiently for the bounded public check; its
value was not retained in the repository, evidence, logs, or fixtures. No
credentials, certificates, private keys, login, certificate selection,
signing, upload, payment, form submission, or administrative transaction was
used.

## Bounded implementation contract

Workspace-47 adds the QA-only profile `la-gomera-sede-public-navigation` with
the exact entry URL and origin `https://lagomera.sedelectronica.es`. The
profile allows the exact public launch and the observed session-cookie
handshake only. It declares no capabilities, operation policies, endpoints,
redirect origins, trusted extra origins, client TLS policy, signing adapter,
or web-message bridge.

The release registry intentionally excludes this `QA_ONLY` profile. The public
catalog records `IMPLEMENTED_NOT_E2E` / `E2E_PENDING`; the implementation proves
the bounded navigation contract, not acceptance of any real administrative
result.

## Remaining gate

An authorized physical check may later confirm the exact entry launch and
temporary same-origin session transition in the app. It must remain limited to
public navigation and must not log in, select a certificate, sign, upload, or
submit a real request.

Primary source: <https://lagomera.sedelectronica.es/info.0>.

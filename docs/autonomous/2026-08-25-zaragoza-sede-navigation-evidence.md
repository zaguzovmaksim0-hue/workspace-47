# Zaragoza public sede navigation evidence — 2026-08-25

## Scope

This review covered only a public unauthenticated `GET` of the exact public
electronic-sede entry:

- URL: `https://dpz.sedelectronica.es/info.0`
- transport: HTTPS, normal certificate validation, no TLS bypass
- observed result: the first same-origin response establishes a temporary
  session cookie; the next GET returns HTTP 200 on the same public URL

The session cookie was used only transiently for the bounded public check; its
value was not retained in the repository, evidence, logs, or fixtures. No
credentials, certificates, private keys, login, certificate selection,
signing, upload, payment, form submission, or administrative transaction was
used.

The inventory retains the official documentary statement that some Zaragoza
procedures conditionally mention certificate and electronic signature. That
statement is not treated as proof of a mobile protocol contract.

## Bounded implementation contract

Workspace-47 adds the QA-only profile `zaragoza-sede-public-navigation` with
the exact entry URL and origin `https://dpz.sedelectronica.es`. The profile
allows only the observed public session-cookie handshake. It declares no
capabilities, operation policies, endpoints, redirect origins, trusted extra
origins, client TLS policy, signing adapter, or web-message bridge.

The release registry intentionally excludes this `QA_ONLY` profile. The public
catalog records `IMPLEMENTED_NOT_E2E` / `E2E_PENDING`; the implementation proves
the bounded navigation contract, not acceptance of any real administrative
result.

## Remaining gate

An authorized physical check may later confirm the exact entry launch and
temporary same-origin session transition in the app. It must remain limited to
public navigation and must not log in, select a certificate, sign, upload, or
submit a real request.

Primary source: <https://dpz.sedelectronica.es/info.0>.

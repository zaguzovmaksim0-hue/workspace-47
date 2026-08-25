# Lanzarote public sede navigation evidence — 2026-08-25

## Scope

This review covered only the public HTTPS launch of the official electronic
sede entry:

- entry URL: `https://cabildodelanzarote.sedelectronica.es/info.0`
- observed redirect: HTTP 301 to `https://lanzaroteylagraciosa.sedelectronica.es:443`
- current origin: `https://lanzaroteylagraciosa.sedelectronica.es`
- observed result on the current origin: the first same-origin response
  establishes a temporary session cookie; the next GET returns HTTP 200 on
  the same public URL
- transport: HTTPS, normal certificate validation, no TLS bypass

The session cookie was used only transiently for the bounded public check; its
value was not retained in the repository, evidence, logs, or fixtures. No
credentials, certificates, private keys, login, certificate selection,
signing, upload, payment, form submission, or administrative transaction was
used.

## Bounded implementation contract

Workspace-47 adds the QA-only profile `lanzarote-sede-public-navigation` with
the exact entry URL as its initiator origin and the observed
`lanzaroteylagraciosa.sedelectronica.es` host as an explicit redirect origin.
The profile allows the bounded public redirect and session-cookie handshake
only. It declares no capabilities, operation policies, endpoints, trusted
extra origins, client TLS policy, signing adapter, or web-message bridge.

The release registry intentionally excludes this `QA_ONLY` profile. The public
catalog records `IMPLEMENTED_NOT_E2E` / `E2E_PENDING`; the implementation proves
the bounded navigation contract, not acceptance of any real administrative
result.

## Remaining gate

An authorized physical check may later confirm the exact entry launch, the
explicit redirect origin, and the temporary same-origin session transition in
the app. It must remain limited to public navigation and must not log in,
select a certificate, sign, upload, or submit a real request.

Primary source: <https://cabildodelanzarote.sedelectronica.es/info.0>.

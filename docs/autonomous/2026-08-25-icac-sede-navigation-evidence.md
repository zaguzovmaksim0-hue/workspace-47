# ICAC public sede navigation evidence — 2026-08-25

## Scope

This review covered only a public unauthenticated `GET` of the official ICAC
electronic-sede root:

- URL: `https://icac.sede.gob.es/`
- transport: HTTPS, normal certificate validation, no TLS bypass
- observed result: HTTP 200, public HTML response, no redirect

No credentials, cookies, certificates, private keys, login, certificate
selection, signing, upload, payment, form submission, or administrative
transaction was used.

## Bounded implementation contract

Workspace-47 adds the QA-only profile `icac-sede-public-navigation` with the
exact root URL and exact origin `https://icac.sede.gob.es`. The profile exposes
ordinary same-origin browsing only. It declares no capabilities, operation
policies, endpoints, redirect origins, trusted extra origins, client TLS
policy, signing adapter, or web-message bridge.

The release registry intentionally excludes this `QA_ONLY` profile. The public
catalog records `IMPLEMENTED_NOT_E2E` / `E2E_PENDING`; the implementation proves
the bounded launch contract, not acceptance of any real administrative result.

## Remaining gate

An authorized physical check may later confirm that the exact root opens in the
app without crossing the declared origin boundary. It must remain limited to
public navigation and must not log in, select a certificate, sign, upload, or
submit a real request.

Primary source: <https://icac.sede.gob.es/>.

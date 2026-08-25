# IMSERSO public sede navigation evidence — 2026-08-25

## Scope

The bounded contract covers only the official public landing page:

- URL: `https://sede.imserso.gob.es/inicio`
- transport: HTTPS with normal certificate validation; no TLS bypass
- public surface: procedures and services, pending requests, Cl@ve, and
  electronic register links are exposed from the landing page

Official first-party search results identify `/inicio` as the current Spanish
landing page and expose the public navigation areas. No credentials,
certificates, private keys, login, certificate selection, signing, upload,
payment, form submission, or administrative transaction was used.

## Bounded implementation contract

Workspace-47 adds the QA-only profile `imserso-sede-public-navigation` with the
exact `/inicio` entry URL and origin `https://sede.imserso.gob.es`. It declares
no capabilities, operation policies, endpoints, redirect origins, trusted
extra origins, client TLS policy, signing adapter, or web-message bridge.

The release registry intentionally excludes this `QA_ONLY` profile. The public
catalog records `IMPLEMENTED_NOT_E2E` / `E2E_PENDING`; the implementation proves
only the bounded public-navigation contract.

## Verification boundary

The Termux host could not complete a normal direct TLS validation because the
server connection did not provide the intermediate issuer needed by the local
trust chain. No `--insecure` request was used and no certificate workaround was
added. A physical app launch remains required before any runtime availability
claim; it must stay limited to public navigation and stop before authentication
or submission.

Primary sources:

- <https://sede.imserso.gob.es/inicio>
- <https://sede.imserso.gob.es/es/procedimientos-servicios/centros>

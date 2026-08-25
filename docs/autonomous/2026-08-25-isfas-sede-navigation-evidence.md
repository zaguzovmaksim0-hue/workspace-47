# ISFAS public sede navigation evidence — 2026-08-25

## Scope

The bounded contract covers only the official public Sede root:

- URL: `https://sede.isfas.gob.es/`
- transport: HTTPS with normal certificate validation; no TLS bypass
- public surface: the Sede landing page and public navigation to its catalog

The first-party ISFAS material identifies the root as the current Sede and
warns that some procedures require identification or remain partially
operational. No credentials, certificates, private keys, login, certificate
selection, signing, upload, payment, form submission, or administrative
transaction was used.

## Bounded implementation contract

Workspace-47 adds the QA-only profile `isfas-sede-public-navigation` with the
exact root entry URL and origin `https://sede.isfas.gob.es`. It declares no
capabilities, operation policies, endpoints, redirect origins, trusted extra
origins, client TLS policy, signing adapter, or web-message bridge.

The release registry intentionally excludes this `QA_ONLY` profile. The public
catalog records `IMPLEMENTED_NOT_E2E` / `E2E_PENDING`; the implementation proves
only the bounded public-navigation contract and does not claim access to any
ISFAS procedure.

## Verification boundary

The Termux host could not complete a normal direct TLS validation because the
server connection did not provide the intermediate issuer needed by the local
trust chain. No `--insecure` request was used and no certificate workaround was
added. A physical app launch remains required before any runtime availability
claim; it must stay limited to public navigation and stop before identification
or submission.

Primary sources:

- <https://sede.isfas.gob.es/>
- <https://sede.isfas.gob.es/ispre/index.html>

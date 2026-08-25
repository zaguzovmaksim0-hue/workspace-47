# INE public sede navigation evidence — 2026-08-25

## Scope

The bounded contract covers only the official Spanish public landing page:

- URL: `https://sede.ine.gob.es/es/index.htm`
- transport: HTTPS with normal certificate validation; no TLS bypass
- public surface: padrón, censo electoral, sanctions/payments, grants, and
  electronic register navigation

The first-party INE page identifies the public procedures and direct links.
No credentials, certificates, private keys, login, certificate selection,
signing, upload, payment, form submission, or administrative transaction was
used.

## Bounded implementation contract

Workspace-47 adds the QA-only profile `ine-sede-public-navigation` with the
exact `/es/index.htm` entry URL and origin `https://sede.ine.gob.es`. It
declares no capabilities, operation policies, endpoints, redirect origins,
trusted extra origins, client TLS policy, signing adapter, or web-message
bridge.

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

Primary source: <https://sede.ine.gob.es/es/index.htm>.

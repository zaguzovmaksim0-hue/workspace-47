# 0011 — Reviewed certificate login boundaries for Wave D

**Date:** 2026-11-08
**Status:** Draft

## Context

The operator authorized certificate login only, with no administrative filing and no relaxation of the security model.
AESA and DGSFP use the existing Cl@ve v2 auto-POST contract. Canarias redirects Android to a second exact login page.
AEMET calls `MiniApplet.sign(nonceB64, "SHA1withRSA", "AUTO", null, success, error)` for a decimal nonce from 0 to 10000.
Its original mobile transport names an unrelated HTTP storage broker, which the app must never use.
The public HTML/JS and mobile Chrome probes reviewed on 2026-11-08 establish these contracts, but not accepted login.
The AUTO interpretation follows official clienteafirma commit `0d7f3cf01fb65d2be5b245622d2c8f490f36e718`:
[format selection](https://github.com/ctt-gob-es/clienteafirma/blob/0d7f3cf01fb65d2be5b245622d2c8f490f36e718/afirma-simple/src/main/java/es/gob/afirma/standalone/protocol/ProtocolInvocationLauncherUtil.java#L189),
[default explicit mode](https://github.com/ctt-gob-es/clienteafirma/blob/0d7f3cf01fb65d2be5b245622d2c8f490f36e718/afirma-core/src/main/java/es/gob/afirma/core/signers/AOSignConstants.java#L269),
[detached content](https://github.com/ctt-gob-es/clienteafirma/blob/0d7f3cf01fb65d2be5b245622d2c8f490f36e718/afirma-crypto-cades/src/main/java/es/gob/afirma/signers/cades/CAdESParameters.java#L112).

## Decision

Keep all profiles QA_ONLY and E2E_PENDING. Reuse the pinned Cl@ve transition for AESA and DGSFP.
Retain both exact Canarias login pages in the shim, native bridge and local adapter; reject neighboring URLs.
Use an AEMET-specific local adapter and feature: accept only the six-argument AUTO/null tuple, canonical Base64
and a canonical decimal nonce of at most five bytes. Normalize that tuple alone to explicit detached CAdES/SHA1.
Return the signature through the original in-memory callback. Add no HTTP endpoints, broker, generic AUTO support
or new network grants. AEMET native routing and signing preparation independently validate the contract and payload.
Use ordinary exact DOM clicks after readiness checks. Leave Funciona navigation-only: its broker-origin source
and multi-hop grant propagation require a separately reviewed authorization contract.
When a non-deep AEMET probe reaches `AwaitingConfirmation`, abandon the pending native reply and immediately close
the scenario: its error callback also submits the login form, so clicking Cancel would perform a negative login POST.
This protects only that reached boundary. An earlier native failure (for example `CERTIFICATE_LOCKED` or
`PROTOCOL_FAILED`) or a shim timeout still delivers the original error callback and can submit a negative login POST.
Keep production error delivery unchanged without evidence of a product bug; non-deep probes do not guarantee zero
login requests. This limitation does not authorize any administrative filing.

## Consequences

The existing native consent, origin, active-profile, main-frame and navigation/replay boundaries stay authoritative.
Parser pins, shim predicates, adapters and catalog metadata must agree; the small CAdES page constraint becomes an
exact set to represent Canarias desktop and mobile login. SHA1 is confined to the reviewed legacy login operations.
Synthetic tests can prove filtering and detached signatures; server acceptance and Android lifecycle ordering remain
unverified until authorized REAL E2E. This change does not promote compatibility status.
Synthetic characterization of the actual shim and an in-memory login-form sink confirms one error callback/submit
for native failures and request expiry, with no real network or credentials. It does not prove Android failure timing.
The existing release build enables unverified profiles and selects the QA registry; QA_ONLY is metadata and does
not hide these contracts from that runtime. The strict release registry remains available as a separate policy.

## Alternatives

1. Retain navigation-only profiles until authenticated device evidence exists. This preserves the old boundary but
   prevents the operator's QA recipe from reaching the already reviewed login consent.
2. Follow AEMET's native URI/storage path or introduce general AUTO inference. The observed HTTP placeholder broker
   cannot satisfy the app's HTTPS-only transport policy, and general inference would authorize unreviewed documents.

## Payoff trigger

Run operator-authorized Android REAL E2E on the integrated candidate to verify the five login boundaries and AEMET's
local callback acceptance before promoting compatibility claims or changing the exact reviewed contracts. Include
pre-consent failures when selecting a reviewed non-deep probe strategy; the confirmation stop alone cannot prevent
all negative login requests.

# UGR Certificate Contract Design

Status: implementation-ready QA-only design for candidate `ugr-sede` / `ES-PUB-0018`.

## Contract source and scope

The sole portal-contract evidence source is `/data/data/com.termux/files/home/workspace-47-autonomous-20260803/build/autonomous-evidence/g33-portal-research/ugr-sede/EVIDENCE_PACKET.md`. This change records a verified contract, not an end-to-end portal result. It must not navigate an authenticated area, use a real credential or private key, submit the real form, contact AutoFirma, or perform device/ADB actions.

## Profile and lifecycle

Add profile `ugr-certificado-login`, version `1`, with:

- compatibility `VERIFIED_CONTRACT` and activation `QA_ONLY`;
- start URL `https://sede.ugr.es/Hades/jsp/pantallacertificado.jsp`;
- only initiator origin `https://sede.ugr.es`, with no redirect or trusted-browse origins;
- `SIGN` and `LEGACY_SHA1` capabilities, RSA digital-signature certificate rules;
- no endpoint and no invented signing/server URL;
- MiniApplet/AutoScript input with the two-value MiniApplet callback;
- exact signing tuple `SHA1_WITH_RSA`, `CADES`, `DETACHED`, `EXPLICIT`;
- an exact empty-string `extraProperties` contract represented by a UGR-specific parser/bridge rule.

The runtime QA registry may resolve the profile as trusted signing. The release registry must not expose it because its activation is `QA_ONLY` and its compatibility is not `VERIFIED_E2E`. It must never be labeled `VERIFIED_E2E` or release-enabled.

## Data flow and boundaries

The document-start shim receives a profile-scoped UGR compatibility flag from the native bridge. Only when that flag is active and the page origin is exactly `https://sede.ugr.es` may the shim normalize the exact literal `Universidad de Granada` with exact arguments `SHA1withRSA`, `CAdES`, and `""`. The literal is textual data: the shim converts its 22 ASCII bytes to canonical Base64 solely for native transport. Every other non-Base64 data value remains rejected, including whitespace, case, punctuation, encoding, algorithm, format, origin, and filter variants.

The same UGR-only shim branch may intercept the observed setup calls (`setForceWSMode(true)`, `cargarAppAfirma()` with no arguments, and `setServlets` with the exact first-party Storage/Retrieve URLs) as local compatibility no-ops. A setup call with any other arguments, profile, or origin follows the existing behavior and remains fail-closed. No setup call may issue a storage/retrieve or signing request.

The native MiniApplet bridge remains the authority. It validates main-frame state, resolved trusted origin, selected active profile, profile version, operation binding, exact algorithm/format, and the exact UGR empty-string property. It accepts the normalized UGR bytes only through the UGR profile contract; the UGR detached CAdES adapter then rechecks the protocol id, profile/version, initiator origin, SHA1withRSA/CAdES tuple, empty properties, and exactly 22 payload bytes before producing a detached CAdES result. Existing generic Base64 handling and generic `null` property semantics are unchanged.

The registry binds UGR to the UGR detached CAdES adapter and the existing MiniApplet callback contract. The application resolver includes that adapter without adding an endpoint. The callback delivers both signature and certificate to the portal shim exactly as the current callback encoder does.

## Catalog binding

Update only the UGR source record in `docs/compatibility/all-spanish-public-portals-inventory.md` to describe the evidence-backed AutoScript/CAdES/SHA1 contract and `IMPLEMENTED_NOT_E2E`. Run `tools/generate_public_portal_catalog.py` with the canonical inventory and profile sources to regenerate `app/src/main/res/raw/public_portal_catalog_v1.json`. The generated entry must bind `ugr-sede` to `ugr-certificado-login`, expose `E2E_PENDING`, retain `IMPLEMENTED_NOT_E2E`, and keep release launch unavailable through the QA-only registry.

## Verification and non-goals

Regression tests are written and run RED before any production/config implementation. Focused GREEN coverage must include the exact UGR profile and release exclusion, shim literal normalization/rejection of variants and scoped setup interception, native bridge normalization and rejection of wrong tuples/origins/profiles, detached CAdES exact-payload enforcement, registry binding, generated catalog binding/status, and unchanged generic, Aragón, and REG behavior. Run the generator, its focused tests, the focused Android unit-test classes, `git diff --check`, and inspect the complete diff. Do not edit broad audit ledgers, test reports, or handoff documents.

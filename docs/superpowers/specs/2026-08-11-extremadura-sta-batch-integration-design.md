# Extremadura STA batch integration design — 2026-08-11

## Goal

Integrate `extremadura-tramites` (`ES-PUB-0109`) as a QA-only, non-E2E STA AutoFirma batch profile
without weakening the accepted Melilla contract. Reuse behavior only where current first-party public
evidence proves the same STA ABI; keep profile/origin/runtime-URL ownership exact and fail closed.

## Evidence boundary

Only public unauthenticated evidence is used. On 2026-08-11 the current Extremadura first-party
`autoscript.js`, `sta-autofirma-lote.js`, and `webAppsFwk.js?ver=2605.0.2` resources returned HTTP 200
with the already recorded G42/G45 hashes and were byte-for-byte identical to the Melilla copies. The
helper still exposes `STAAutofirmaLote.firmarLote`, SHA256withRSA/CAdES defaults, per-document
CAdES/PAdES/XAdES, backend-supplied pre/post/data URLs, and the `PRESENTAR_FIRMA` callback carrying
`validationResponse`.

A bounded unauthenticated GET-only servlet probe also confirmed the Extremadura endpoint family:
`/sta/AutofirmaLote/presign/<synthetic-id>` and `postsign/<synthetic-id>` reached the servlet and
reported the missing `json` parameter; `getdata/<synthetic-id>/<synthetic-doc-id>` reached operation
lookup. No authentication, certificate selection, form POST, real operation id, upload, signing,
payment, or submission was used.

These observations justify a shared STA batch execution seam. They do not justify cross-origin URLs,
release enablement, or `VERIFIED_E2E`.

## Design

### 1. Coordinator resolves the adapter per owned request

`BatchSigningCoordinator` currently owns exactly one `BatchSigningProtocolAdapter`. Extremadura needs a
second exact profile adapter while retaining one shared batch signing ownership/UI coordinator. Add a
resolver seam analogous to the ordinary signing coordinator. Resolve the adapter during `prepare`,
reject unknown protocol ids, and store the exact resolved adapter inside the owned operation so later
`confirm` cannot switch adapters. Existing single-adapter construction remains the default behavior.

This is the first TDD slice because it is a direct blocker to adding a second STA profile and changes no
portal contract by itself.

### 2. Shared STA URL grammar with profile-specific origin

Extract the already accepted `/sta/AutofirmaLote/{presign,postsign,getdata}` grammar into a shared
internal policy parameterized by one exact HTTPS host. Keep `MelillaBatchUrlPolicy` behavior identical
through its fixed Melilla host. Add an Extremadura policy fixed to `tramites.juntaex.es`. Never allow a
host list inside one policy instance, redirects, alternate ports beyond 443/default, query strings,
fragments, userinfo, path aliases, or cross-profile runtime URLs.

### 3. Shared protocol execution with exact profile contracts

Reuse the existing batch JSON/pre/post implementation through a profile contract that binds protocol
id, profile id/version, exact `TrustedOrigin`, and URL policy. Preserve the accepted Melilla adapter/id
as-is. Add a distinct Extremadura adapter/id over the same execution core so protocol registry and tests
can prove which profile owns a request.

### 4. Bridge and normalization remain profile-bound

Generalize only the current batch bridge plumbing needed to select one exact STA profile contract from
`profileId`. Document-ready, request, cancel, reply ownership, navigation epoch, document UUID, and
origin checks remain unchanged in strength. A request accepted for Melilla must never normalize or
execute as Extremadura and vice versa.

### 5. QA profile and truthful public catalog promotion last

Only after runtime focused gates pass, add `extremadura-tramites` to `config/site_profiles_v1.json` as
`VERIFIED_CONTRACT` + `QA_ONLY`, exact `https://tramites.juntaex.es` initiator origin, RSA digital-
signature certificate rule, SIGN only, SHA256withRSA, CAdES default/detached semantics, and the shared
public STA batch callback shape. Bind `ES-PUB-0109` to that profile and promote only to
`IMPLEMENTED_NOT_E2E` / `E2E_PENDING`. Release must remain disabled with no launch target.

## Non-goals

- No authenticated portal interaction or real administrative procedure.
- No invented runtime operation/document identifiers or fixed pre/post/data endpoints.
- No release activation or E2E claim.
- No widening of Melilla origin/path/TLS/retry/certificate rules.
- No new dependency or toolchain version.
- No phone-local Gradle/JVM/Kotlin execution.

## Acceptance

Each behavioral tracer bullet is committed and pushed before its focused Gradle RED/GREEN task in
Codex Cloud. Applicable Debug+QA tests, parser/catalog tests, lint/build gates, Python catalog
reproducibility, `git diff --check`, bounded unsafe-content scans, and direct Standards + Spec review
must pass before the portal is accepted.

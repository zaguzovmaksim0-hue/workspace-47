# US Sede → REG-AGE catalog alias implementation plan

## Evidence

Use `docs/autonomous/2026-08-09-g37-us-sede-reg-age-evidence.md` and the existing `reg-age-redsara` profile evidence. Do not perform authenticated or state-changing portal interaction.

## Exact files

Expected production changes:

- `app/src/main/java/dev/junta/firmamobile/catalog/PortalCatalogModels.kt`
- `app/src/main/java/dev/junta/firmamobile/catalog/PublicPortalCatalogParser.kt`
- `app/src/main/java/dev/junta/firmamobile/catalog/PortalCatalogRepository.kt`
- `app/src/main/res/raw/public_portal_catalog_v1.json`

Expected tests:

- `app/src/test/java/dev/junta/firmamobile/catalog/PublicPortalCatalogParserTest.kt`
- `app/src/test/java/dev/junta/firmamobile/catalog/PortalCatalogRepositoryTest.kt`
- optionally `app/src/test/java/dev/junta/firmamobile/smoke/CatalogSmokeControllerTest.kt` only if the public launch seam needs an explicit smoke assertion.

Generated/shared catalog files may change only through the repository's existing generator workflow if required; inspect before touching them.

## TDD sequence

1. RED: add one focused parser/repository behavioral test proving an exact alias can retain a distinct public `entryUrl` while resolving only to an identical profile `startUrl`; add negative cases for missing profile and mismatched launch URL without changing production behavior.
2. Commit the RED-only worker state. The orchestrator pushes the worker branch and runs the focused Gradle test only through Codex Cloud against that exact SHA. RED must fail for the intended missing alias behavior.
3. GREEN: add nullable `launchUrl`, strict parser support, exact binding validation, and the minimum repository launch resolution needed for the test.
4. Bind only `us-sede` to `reg-age-redsara` using the exact public US procedure evidence and exact REG-AGE target. Do not add new trusted origins or a duplicate signing profile.
5. Commit the worker GREEN state. The orchestrator pushes and runs focused and then applicable full Android gates through Codex Cloud against exact pushed SHAs.
6. Run lightweight local JSON/catalog generation/policy checks if applicable, `git diff --check`, changed-path review, sensitive-data scan, and Matt Pocock `codex/code-review`.
7. Integrate the verified worker commits sequentially into `agent/workspace-47-autonomous-20260803`, push, verify remote SHA, update ledger/handoff and truthful counts.

## Acceptance

- `us-sede` resolves/openable only in QA because `reg-age-redsara` is QA-only.
- US launch returns canonical `https://reg.redsara.es/es/` while catalog metadata retains the official US procedure URL.
- A wrong alias launch URL, unknown profile, metadata substitution, HTTP URL, userinfo, explicit port, fragment, or normalized-path trick fails closed.
- Existing non-alias entries behave identically.
- Existing release gating is unchanged.
- Inventory becomes `IMPLEMENTED_NOT_E2E` / catalog `E2E_PENDING`, never `VERIFIED_E2E`.

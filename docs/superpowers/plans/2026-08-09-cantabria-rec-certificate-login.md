# Cantabria REC certificate-login implementation plan

## Source of truth

- `docs/autonomous/2026-08-09-g37-cantabria-rec-certificate-login-evidence.md`
- `docs/superpowers/specs/2026-08-09-cantabria-rec-certificate-login-design.md`
- `docs/agents/matt-pocock-workflow.md`

## Task 1 — RED tracer

In an isolated worker branch/worktree, add focused tests only:

- `app/src/test/java/dev/junta/firmamobile/browser/AfirmaJavascriptShimTest.kt` — exact Cantabria compatibility flag/origin/algorithm/format/challenge/properties is required without broadening generic behavior;
- `app/src/test/java/dev/junta/firmamobile/browser/MiniAppletBridgeAdapterTest.kt` or a candidate-specific test — exact runtime 40-lowercase-hex challenge with SHA512withRSA+CAdES+canonical implicit properties is accepted under the exact candidate profile and rejected otherwise.

The first RED should be a compile-valid behavioral failure against current production. Worker commits but does not push; orchestrator pushes and runs focused Gradle only through Codex Cloud against the exact pushed SHA.

## Task 2 — GREEN profile-scoped shim/native route

Expected production files:

- `app/src/main/java/dev/junta/firmamobile/browser/AfirmaJavascriptShim.kt`;
- `app/src/main/res/raw/afirma_shim.js`;
- `app/src/main/java/dev/junta/firmamobile/browser/MiniAppletBridgeAdapter.kt`;
- `config/site_profiles_v1.json`;
- profile/parser/registry tests only where needed for the exact QA profile.

Implement the minimum exact compatibility contract. Do not create a new signing algorithm implementation if the existing local cryptographic engine already supports SHA512withRSA; verify that capability before changing production. If the lower signing stack does not support it, stop the GREEN at the validated input/profile seam and open the next TDD slice instead of silently falling back or downgrading the algorithm.

## Task 3 — Cloud verification

After each worker commit, orchestrator pushes the worker branch, records exact 40-hex SHA, and runs focused tests through `$HOME/bin/w47-cloud`. No local Gradle command is permitted.

After complete candidate implementation, run the canonical full Android Cloud gate against the exact pushed SHA.

## Task 4 — truthful catalog integration

Only after functional GREEN:

- update `ES-PUB-0101` in `docs/compatibility/all-spanish-public-portals-inventory.md` with exact public certificate-login evidence and `IMPLEMENTED_NOT_E2E`;
- map it to `cantabria-rec-cert-login` using the canonical generator/profile binding rules;
- regenerate `app/src/main/res/raw/public_portal_catalog_v1.json` with `tools/generate_public_portal_catalog.py`;
- update focused Python generator tests and catalog repository/parser tests;
- keep public catalog `E2E_PENDING`, never E2E verified.

The catalog entry launches only the exact configured QA profile start URL. No authenticated URL or form target is invented.

## Task 5 — review/integration

Run `git diff --check`, changed-path review, added-line secret/unsafe scan, Matt Pocock `codex/code-review`, focused/full Cloud gates, and lightweight Python generator/policy tests where applicable. Integrate sequentially into `agent/workspace-47-autonomous-20260803`, push, and verify exact remote SHA.

Manual acceptance remains: real certificate selection/login and end-to-end portal behavior on a physical environment. Autonomous work must not perform those actions.

# OSS publication status

**Branch:** `oss/publication-readiness-20260811`
**Project product cutoff:** `4bf6afb000dbab8f6f767d8ea05a1a00e2d563cb`
**Cutoff rationale:** this is the last autonomous product SHA with recorded Codex Cloud acceptance: focused Gradle PASS plus broader Debug 656/656 and QA 35/35 (691/691 total). Later autonomous commits were in-flight TDD RED work and are intentionally excluded from the publication candidate.
**Current publication candidate:** OSS documentation/policy and project-origin visual-resource remediation layered on top of the green product cutoff. Relative to `4bf6afb...`, no Kotlin/Java/Go/Python product-source file differs in the reviewed current tree.
**Branch hygiene:** `agent/workspace-47-autonomous-20260803` and `oss/autonomous-cutoff-20260812` were pinned back to the green cutoff after the maintainer stopped the autonomous worker. In-flight commits remain preserved in Git/PR history; the OSS publication branch was not history-rewritten.
**Execution boundary:** Termux may run Git/Gitleaks/Python policy checks and submit jobs, but Android Gradle/JVM/Kotlin executes only in Codex Cloud `workspace-47-android` through `$HOME/bin/w47-cloud`.
**Policy:** repository remains private until the final one-shot verification passes on one exact candidate SHA.

## Gate matrix

| Gate | Status | Evidence / required action |
| --- | --- | --- |
| Publication branch integrity | `PASS` | Publication work remains on a dedicated branch. Sync used normal PR/merge commits; the later RED test was removed by a normal follow-up commit rather than rewriting OSS history. |
| Product-code cutoff | `PASS_CLOUD_GREEN` | `4bf6afb...` is the last accepted product checkpoint. Recorded Cloud evidence reports focused Gradle exit 0 and broader Debug 656/656 + QA 35/35 = 691/691 with zero failures/errors/skips. |
| Current product-source delta vs green cutoff | `PASS_NONE` | GitHub comparison from `4bf6afb...` to the reviewed OSS candidate showed no changed Kotlin/Java/Go/Python product-source files. Build-affecting OSS delta is the project-origin Android visual-resource replacement; remaining changes are docs/configuration/verification tooling. |
| Interrupted autonomous RED | `PASS_EXCLUDED` | `db9bacd...` added an unimplemented `ExtremaduraProfileCatalogBindingTest` as TDD RED. It is absent from the current OSS tree and the autonomous/cutoff refs were pinned back to `4bf6afb...`. |
| Current-tree secret/credential inventory | `REVIEWED_WITH_FINAL_HISTORY_SCAN` | Release signing, relay configuration, synthetic credential fixture, sanitized evidence/logging and Gitleaks configuration were reviewed. No real current-tree signing/relay credential was identified. Full-history scanning remains mandatory. |
| Gitleaks scanner integrity | `PASS_CONFIGURED_PENDING_EXECUTION` | Publication gate pins Gitleaks 8.29.1, verifies the official release SHA-256, requires a runtime detector canary, and scans all reachable refs with `--log-opts="--all"`. Gitleaks 8.30.1 is excluded from the publication decision because of upstream regression evidence. |
| Full-history Gitleaks result | `BLOCKED_EXECUTION` | Must obtain exit `0` and no unresolved real-secret finding on the final exact candidate. One-shot command: `bash scripts/oss/run-termux-publication-gates.sh "$PWD"`. |
| GitHub Actions execution | `EXTERNAL_BLOCKER_BYPASSED_FOR_PREPUBLICATION` | Real CI/Security workflows are active and include `oss/**`, but events are still diverted to deleted historical `BuildFailed` workflow id `324591298` with pre-job `startup_failure`. The final private publication gate no longer depends on Actions: Termux + Codex Cloud is the approved execution path. |
| Synthetic PKCS#12 fixture | `PASS_TEST_ONLY` | `app/src/androidTest/assets/synthetic-identity.p12.b64` is an intentionally public synthetic fixture documented in `docs/test-fixtures.md`. |
| Release signing material | `PASS_CURRENT_TREE` | Real release signing material is supplied outside Git; no committed production-key fallback is accepted. |
| Git-history author/committer email privacy | `PASS_USER_ACCEPTED` | Maintainer explicitly accepted publication of existing author/committer Gmail metadata on 2026-08-12. |
| Release transport boundary | `PASS_REVIEWED` | Reviewed release transport remains direct-only; release tunnel policy is empty. |
| QA relay boundary | `PASS_REVIEWED` | QA capability is opt-in/debug-scoped; relay destination is fixed; credential storage is digest-only; operational secrets/captures stay outside Git. |
| Protocol/evidence privacy | `PASS_REVIEWED` | Reviewed evidence retains sanitized metadata and omits credentials/session/private identity artifacts. |
| Bebas Neue font | `PASS_THIRD_PARTY_LICENSE` | SIL OFL 1.1 notice retained in `docs/licenses/BebasNeue-OFL.txt` and `NOTICE`. |
| Gradle Wrapper | `PASS_THIRD_PARTY_LICENSE` | Upstream Gradle Wrapper material retains Apache-2.0/SPDX metadata. |
| Portal catalog/data provenance | `PASS_WITH_SOURCE_BOUNDARY` | Runtime catalog is generated by local project tooling from reviewed repository sources; upstream public sources are not silently relicensed. |
| Runtime/build dependency license review | `PASS_FOR_SOURCE_PUBLICATION` | Family-level runtime/PyYAML review found no project-source relicensing blocker. Exact APK/AAB notices remain a separate binary-release gate. |
| Binary dependency notices | `BINARY_RELEASE_GATE` | Exact packaged AAR/JAR license/NOTICE audit is required before binary distribution, not before source publication. |
| `afirma_shim.js` / project-source rights | `PASS_USER_ATTESTED` | Maintainer explicitly confirmed all five source-rights/no-unlicensed-copy statements on 2026-08-12. |
| Home visual provenance | `PASS_PROJECT_ORIGIN_REPLACEMENT_BUILD_PENDING` | Unresolved WebP removed; project-origin `jfm_home_background.xml` replaces it. Fresh final Cloud resource/build verification remains mandatory. |
| Launcher/custom visual provenance | `PASS_PROJECT_ORIGIN_REPLACEMENT_BUILD_PENDING` | All 20 unresolved launcher PNGs removed and replaced with project-origin vector/anydpi XML resources. Fresh final Cloud resource/build verification remains mandatory. |
| Publication visual-asset policy | `PASS_PATH_LEVEL_RECHECK_REQUIRED` | RED→GREEN was previously observed for `tools/test_publication_visual_assets.py`; the one-shot runner reruns it on the exact final SHA. |
| Android/Gradle execution | `BLOCKED_EXECUTION_READY` | Canonical full gate is `$HOME/bin/w47-cloud full --branch oss/publication-readiness-20260811 --sha <exact-sha>` in `workspace-47-android`. The Termux orchestrator invokes it automatically after local security/policy checks. No local Gradle fallback. |
| Unofficial-project disclosure | `PASS_REVIEWED` | App and public docs clearly identify the project as independent/unofficial and not endorsed by public administrations. |
| Public README | `PASS_CURRENT_BRANCH` | Scope, security model, maturity, provenance and no-affiliation language are present. |
| SECURITY policy | `PASS_CURRENT_BRANCH` | Defines private reporting, credential/evidence rules and third-party research authorization boundary. |
| CONTRIBUTING policy | `PASS_CURRENT_BRANCH` | Defines testing, privacy, research-boundary and provenance requirements. |
| NOTICE / third-party provenance | `PASS_FOR_SOURCE_PUBLICATION` | NOTICE/provenance/dependency audit distinguish project-origin source from separately licensed material. |
| Root project `LICENSE` | `INTENTIONALLY_ABSENT` | Apache-2.0 remains selected provisionally; add only after full-history Gitleaks and canonical Cloud Android verification pass on the same exact candidate. |
| Repository visibility | `PRIVATE_REQUIRED` | Do not switch public until the final orchestrated verification passes. |
| Autonomous synchronization | `PASS_FROZEN_GREEN_CUTOFF` | Autonomous work was stopped. Publication uses the last Cloud-green product cutoff `4bf6afb...`; working autonomous/cutoff refs are pinned to it. |
| Codex for OSS application | `NOT_READY` | Prepare/submit only after safe public visibility and verification of public repository metadata. |

## Remaining hard blockers

There are now only two evidence results left, both produced by the same one-shot Termux orchestration:

1. **Full-history/all-refs Gitleaks PASS** using verified Gitleaks 8.29.1 after its detector canary succeeds.
2. **Canonical Codex Cloud Android PASS** for the exact same candidate SHA through `$HOME/bin/w47-cloud full`.

Run:

```bash
bash scripts/oss/run-termux-publication-gates.sh "$PWD"
```

If it reaches the final `PASS: all mandatory source-publication execution gates completed ...`, the next repository mutation is the root Apache-2.0 `LICENSE` plus final approved-publication status. Until then, the repository remains private.

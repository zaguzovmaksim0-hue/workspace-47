# Site-profile catalog: BuildConfig to asset migration plan

## Decision and scope

Task 3, batch 2; inspected main `07b8232d1e3db006260fd11c6a40c52b80286a79` on 2026-10-24. Branch: `refactor/catalog-json-asset-20261024`; isolated worktree: `~/.cache/w47-agent2-catalog-json-asset`.

**Plan only.** There is a shared context-free facade, but no supported asset/raw loading seam behind it that can serve every existing consumer without new initialization or dependency plumbing. The conditional implementation gate is therefore not met. No partial asset copy, SHA field, loader, parser or test change is included. A facade is not the same thing as an Android-resource loader.

Read the complete batch, root `AGENTS.md`, `CONTEXT.md`, `CONTRIBUTING.md`, engineering/verification policies and project plan sections 3 and 6. The earlier investigation is [the 2026-10-18 handoff](2026-10-18-site-profile-catalog-assets-boundary.md); this document rechecks its boundary against current main rather than treating historical counts as current evidence. The current open-PR search also found #413 (catalog verification dates), #414 (behavioral test replacements), and #420 (origin-exception plan); do not copy their unmerged behavior or expand this task into their scope.

Only this handoff is changed. `profile/SiteProfileCatalogParser.kt` and its tests belong to task 8 and must remain untouched here. No adapter, protocol, security policy, dependency, verification metadata, lock file, baseline, UI, workflow or signing configuration is changed.

## Goal and invariants

Remove the large generated Java JSON expression, package the exact canonical bytes once as an Android asset, retain a small expected SHA-256 in BuildConfig, and reject corrupt/missing payloads before decoding/parsing. Keep `config/site_profiles_v1.json` as the **only committed catalog data source**; do not commit a second hand-maintained JSON file under assets/raw. The public portal catalog is a different artifact, not an integrity source for site profiles.

Current canonical source: 191,140 bytes, SHA-256 `4e03e8df32d63776cd96793abb7368da301b3cb160d157310f439a53d3aad885`. This is an observation of the inspected source, not an immutable version pin. Future approved data changes must regenerate both resource and expected digest from the same input.

Preserve strict parser validation, registry activation/eligibility, ambiguous-origin handling, QA/release and verified-only selection, consent, origin constraints and signing behavior. A SHA pin detects packaging drift; it is not a substitute for APK signing or protection against an attacker who can change both payload and compiled pin.

## Reproduced dependency map

Commands run from the isolated worktree:

```sh
grep -rn SITE_PROFILE_CATALOG_JSON --exclude-dir=.git .
git grep -n BuiltInSiteProfiles -- app/src/main
git grep -n BuiltInSiteProfiles -- app/src/main |
  grep -v -E 'import |object BuiltInSiteProfiles' | wc -l
```

The last command returns **43 lines**, not 43 direct BuildConfig references and not a semantic call-expression count. `BuiltInSiteProfiles` appears in 23 production files (65 matching lines including imports/declaration), 139 unit-test files (1,516 matching lines), four Android-test files (eight lines), and no release-only source file. Three UI files contribute import-only matches. Re-run these commands on the eventual implementation base.

| Direct literal dependency | Current role / migration consequence |
| --- | --- |
| `app/build.gradle.kts` (canonical provider and lines 202-206) | Reads canonical JSON via tracked provider and emits `SITE_PROFILE_CATALOG_JSON`; `chunkedBuildConfigText` avoids the Java constant-pool string limit. Replace only catalog generation after a supported reader exists. |
| `app/src/main/java/dev/junta/firmamobile/profile/SiteProfileRegistry.kt:154-173` | `BuiltInSiteProfiles.JSON` directly reads BuildConfig; synchronized lazy catalog and registries parse/derive from it. No Context, asset opener or resource-loader dependency exists. |
| `app/src/test/java/dev/junta/firmamobile/profile/SiteProfileCatalogParserTest.kt:25,28` | Compares BuildConfig/facade JSON and parsed models. Coordinate a later test-ownership handoff; no edits during task 8. |
| `tools/tests/test_generate_public_portal_catalog.py:2081` | Existing structural single-source assertion explicitly requires the BuildConfig reference. Updating it is outside task 3's allowed files; a follow-up needs permission and a behavioral replacement, not a weakened assertion. |

Other literal hits are documentation/history, including the previous handoff, security roadmap and F-15B design/plan. They are not additional runtime readers.

Production use groups (paths below relative to `app/src/main/java/dev/junta/firmamobile/`):

- `JuntaFirmaApplication.kt:26-27`: builds `PortalCatalogRepository` using facade registry/catalog. Its `CatalogRepositoryLoader` reads **public** `R.raw.public_portal_catalog_v1`, then consumes already-available site profiles. It is not the common site-profile loader.
- `MainActivity.kt:277-307,897-901`: profile lookup, adapter construction and runtime resolution.
- `afirma/AfirmaUriParser.kt:13`: resolves the origin before parsing a request.
- `browser/{EuskadiClientAuthPostBridgeAdapter,IsciiiCertificateSelectionBridgeAdapter,JuntaNavigationPolicy,ValenciaCertificateSelectionBridgeAdapter,XuntaCertificateSelectionBridgeAdapter}.kt`: default registry constructor arguments; some callers can initialize the facade independently of Application catalog loading.
- `browser/MiniAppletBridgeAdapter.kt:113`, `WebMessageBridge.kt:260,314,1006`, `WebMessageProtocol.kt:56`: protocol/security policy consumers; read-only investigation, never migrate portal behavior in this branch.
- `network/JuntaOriginPolicy.kt:34-91`: active-origin policy derived from catalog and runtime registry.
- `signing/{JuntaOfvirtualTriPhaseAdapter,JuntaTriPhaseAdapter,UnizarTriPhaseAdapter,XuntaPadesTriPhaseAdapter}.kt`: default profile catalog lookup; `SigningCoordinator.kt:100`: default registry.
- `ui/BrowserScreen.kt:181-248,870-874,1109`, `ui/BrowserSupport.kt:197,209`: browser/profile policy consumers. Import-only matches in other UI files are not loader calls.

## Why the first implementation stage is blocked

1. `BuiltInSiteProfiles` is context-free; many consumers use default arguments and plain JVM tests. `SiteProfileRegistryTest` is one concrete no-Robolectric example. Replacing the JSON expression with `Context.assets.open` cannot preserve that access contract by itself.
2. `JuntaFirmaApplication.onCreate` currently configures runtime scope and builds `AppContainer`; there is no site-profile resource-provider initialization. Adding one there or to AppContainer exceeds this task's `profile/`/catalog-generation boundary and needs explicit ownership and ordering design. The public catalog repository loader does not dominate all 43 access lines.
3. Explicit dependency migration reaches browser, network, signing and UI consumers and potentially exceeds the approximately 20-production-file stop threshold; portal adapters are separately prohibited. It must be broken into separately approved PRs, not attempted as a mass replacement.
4. Existing Python and parser-test compatibility checks need coordinated ownership changes. Leaving the obsolete JSON field merely to satisfy them would not accomplish the migration.
5. APK `assets/...` lookup using `ClassLoader.getResourceAsStream` is not established here. Plain-JVM success cannot establish Android variant/APK/minified behavior. No speculative classloader loader, reflective Application lookup, source-tree fallback, second JSON path or configurable global singleton is acceptable.

## Staged follow-up PRs (not implemented)

### PR A — decide and own resource access

Owner decision: approve either an explicitly composition-owned site-profile dependency (preferred for clear ownership), or prove a supported context-free packaged-resource strategy. Record the selected boundary and exact file ownership before implementation. Proposed areas for the explicit route: `profile/` loader, `JuntaFirmaApplication.kt`, `AppContainer.kt`, associated JVM/Robolectric tests. These additional files are **not** authorized by this task.

Specify who owns immutable catalog lifetime, initialization before all default-argument reads, behavior on early access and concurrent access, and independent test instances. Do not add a mutable singleton/reset hook to hide this decision. If explicit injection cannot retain existing consumer signatures, acknowledge it and obtain separate approval before changing them; do not represent this as the conditional first stage.

Exit: a supported reader contract with behavioral tests, a concrete remaining-caller list, and no new origin/security semantics. Do not remove the existing source until the end-to-end replacement can ship atomically.

### PR B — generated asset and verified loader, atomic cutover

Blocked on A and expanded ownership permission. Expected small core: `app/build.gradle.kts` catalog-generation block, new `profile/SiteProfileCatalogLoader.kt`, facade wiring in `profile/SiteProfileRegistry.kt`, loader tests, approved initialization point and compatibility-test updates. Keep parser production code unchanged. Do not bundle portal-adapter or unrelated Gradle refactoring.

- A deterministic Gradle task declares the canonical JSON input and generated asset output under `app/build/generated/`, copying **exact bytes** to `site_profiles_v1.json`. Register the generated asset directory with the Android variant source API; clean/incremental packaging must depend on it for debug, qa, optimized and release. No manually committed duplicate and no Python build dependency.
- Emit only `BuildConfig.SITE_PROFILE_CATALOG_SHA256` from the same canonical bytes with SHA-256 (64 lowercase hex characters). Do not use the public catalog `sourceRevision`, which hashes inventory Markdown. Remove `SITE_PROFILE_CATALOG_JSON` only as part of the supported cutover.
- Loader accepts an owned stream opener and immutable expected digest; Android adapter uses assets, JVM tests supply streams. Validate pin syntax, use bounded byte reads, close streams on all paths, compare digest **before** strict UTF-8 decoding and parsing. No trim, newline conversion, replacement decoding or JSON reserialization.
- Keep the parser's 262,144 UTF-16-character limit. A conservative independent byte ceiling may use four bytes per allowed character; check overflow and reject at limit + 1 without unbounded buffering. Recheck the parser's actual limit on the implementation base without editing it.
- Missing asset, I/O failure, oversize, malformed pin, digest mismatch, invalid UTF-8 and invalid catalog all fail closed: no usable catalog/registry, no empty or permissive replacement and no BuildConfig/source-tree/network fallback. Do not log catalog contents or private URLs. Ownership must define safe caller/UI error propagation outside this task if needed.
- Retain synchronized immutable catalog/registry caching and policy selection; do not cache partially verified data. Tests must use independent loader instances rather than mutate process-global state.

Exit: all required tests below pass on exact PR head in CI, packaged asset is unique and byte-identical, no large JSON BuildConfig field remains, no new detekt findings or suppression/baseline additions. If the agreed implementation exceeds the file/protocol/security boundary, stop and subdivide again.

### PR C — cleanup and documentation

After B, update active security-roadmap/test-report wording with explicit permission; retain historical plans as history. Audit remaining literal references, remove catalog-only obsolete generation helpers if owned, and verify no duplicate committed payload or secondary fallback returned. Run ordinary exact-SHA CI/security again. No automatic E2E, daily APK, installation, ready conversion or merge.

## Required behavioral acceptance matrix for B

| Test | Expected evidence |
| --- | --- |
| Successful load | Fixed synthetic UTF-8 bytes (including non-ASCII and trailing newline) plus independently computed expected digest yield the exact JSON/model; stream closes. |
| Wrong pin / changed bytes | A one-byte mutation with the original pin, or a wrong well-formed pin with original bytes, fails before parsing or publishing registries. Do not compute expected pin from the mutated input under test. |
| Missing asset | Opener throws file-not-found; load rejects, publishes nothing and never tries a fallback. |
| Other failures | I/O mid-read, truncation, over-limit stream, malformed digest and valid-digest invalid UTF-8/invalid JSON are rejected; both boundary lengths and closure on failure are tested. |
| Generation | Clean and incremental builds after canonical-source mutation update both packaged bytes and compiled pin. Canonical bytes, packaged bytes and independently hashed expected pin match; exactly one APK asset entry. |
| Cache/policy | Concurrent first load never publishes partial state; repeated reads preserve immutable catalog; verified-only/QA/release activation, ambiguous origins and parser mutation tests remain unchanged. |
| Environments | Pure JVM stream seam, Robolectric asset adapter and applicable APK variants in CI. Resource presence checks alone do not prove real Android runtime/minified lookup. |

Tests are JVM/Robolectric behavioral tests, not Python source-text/SHA assertions against Kotlin. Plan-only delivery adds no pretend loader tests. Missing/wrong-pin tests describe required future behavior, not checks that passed today. Any Android/minified startup acceptance remains unverified until separately authorized; do not run instrumentation/emulator/E2E under the current prohibition.

## Verification performed for this plan

- Fresh `origin/main` fetched and the named isolated branch/worktree created; no main-checkout edits.
- Literal dependency search, complete production facade-use search, counts, Application/facade/build generation inspection and current catalog-related open-PR listing completed.
- Four existing behavioral public-catalog tests passed with `python3 -B -m unittest` (3.621 s): `test_committed_resource_is_byte_for_byte_reproducible`, `test_missing_inventory_match_fails_closed`, `test_duplicate_profile_start_url_fails_closed`, `test_unexpected_site_profile_root_key_fails_closed`, all in `tools.tests.test_generate_public_portal_catalog.PublicPortalCatalogGeneratorTest`.
- These tests establish unchanged baseline generator behavior, **not** asset-loader correctness. No new Python test reads Kotlin or pins Kotlin source SHA. The existing structural assertion is documented as a future migration blocker, not modified or used as a substitute for behavioral coverage.
- Before publication: `git diff --check` and a changed-file audit must show only this handoff. After publication: wait for `gh pr checks` on the exact final head; record terminal CI/security results in the draft PR rather than claim success from an older SHA. Amend the single task commit for any task-owned correction and recheck the new head; never weaken checks.

No local Gradle, build, compilation, emulator, instrumentation, APK install, real portal interaction or manual workflow dispatch was run. No physical/E2E acceptance or actual asset migration is claimed. Suggested next workflow: `codex/ask-matt` to resolve the owned boundary, then `codex/implement`/`codex/tdd` only after approval; no subagents required.

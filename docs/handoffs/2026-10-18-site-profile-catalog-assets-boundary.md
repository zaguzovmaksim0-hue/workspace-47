# Site-profile catalog assets/raw migration boundary

Status: research-only; no runtime, build, catalog, test or workflow change implemented.
Related roadmap: #374.

## Fixed baseline

- Inspected commit: `b584a7d5b3a928994d092b559839c653b4a2e6e9`.
- Isolated branch: `refactor/catalog-assets-20261018`.
- The orchestrator subsequently reported main `5cb79e778aa8ffaf633d9123abb0607d8f97fe9a` after MITES #375. That revision was not inspected or rebased into this worktree; all observations below are baseline-specific.
- `config/site_profiles_v1.json`: 191,140 bytes; SHA-256 `4e03e8df32d63776cd96793abb7368da301b3cb160d157310f439a53d3aad885`.
- That digest is an observation for this refactor baseline, not a permanent catalog-version pin. A later authorized catalog change can legitimately change it.

## Complete active literal dependency map

`git grep -n -F SITE_PROFILE_CATALOG_JSON` identifies these active dependencies:

1. `app/build.gradle.kts`: reads the sole committed source `config/site_profiles_v1.json` through `providers.fileContents(...).asText.get()`, then emits the String BuildConfig field via `chunkedBuildConfigText`. The helper splits Java-source literals at 4,096 characters and joins them to avoid the class-file UTF-8 constant limit. This is generated Java, not a remaining handwritten Kotlin catalog body.
2. `app/src/main/java/dev/junta/firmamobile/profile/SiteProfileRegistry.kt`: `BuiltInSiteProfiles.JSON` reads that field. Its synchronized lazy catalog parses this JSON; release/QA registries are synchronized lazy derivatives. Runtime registry selection still depends on `RuntimeProfileScope`, `ALLOW_QA_PROFILES` and `ALLOW_UNVERIFIED_PROFILES`.
3. `app/src/test/java/dev/junta/firmamobile/profile/SiteProfileCatalogParserTest.kt`: `generatedBuildConfigSourceMatchesTheCompiledCatalog` compares the facade's JSON with BuildConfig and checks parsed catalog equality.
4. `tools/tests/test_generate_public_portal_catalog.py`: `test_site_profile_catalog_has_one_committed_source` checks that only `config/site_profiles_v1.json` is tracked, the old raw resource is absent, the facade references BuildConfig, and no embedded Kotlin JSON body returns.

Remaining literal matches are historical F-15B design/plan/test-report/security-roadmap descriptions, not generators or runtime readers.

## Related generators and validation

- There is no separate site-profile JSON generator in the inspected tree: `config/site_profiles_v1.json` is the canonical input. The Android build generates the BuildConfig representation from it.
- `tools/generate_public_portal_catalog.py` takes an explicit `--profiles` path. Its `_site_profiles` and `_profile_bindings` validate canonical IDs/URLs, uniqueness and exact inventory bindings; `generate` uses the reviewed inventory plus that source. `test_committed_resource_is_byte_for_byte_reproducible` verifies the committed public catalog against the generator.
- The public catalog's `sourceRevision` is SHA-256 of the inventory Markdown bytes, **not** of the site-profile JSON. It must not be reused as the expected site-profile resource digest.
- `SiteProfileCatalogParser.parse` checks the maximum character count, exact root/schema, duplicate keys/IDs and the existing detailed profile/origin/protocol constraints. `SiteProfileRegistry` retains activation and release-eligibility policy.
- No independent runtime SHA-256 comparison of `BuiltInSiteProfiles.JSON` was found in the inspected profile path. Current source consistency is enforced by build-time inclusion, policy/reproducibility tests and APK signing, not by a separate runtime catalog digest field. Dependency-verification metadata and release-signing policy are unrelated protections and must remain intact.

## Why no direct assets/raw patch was made

The context-free facade is an intentional boundary, not accidental missing plumbing. The historical F-15B design explicitly rejected a Context-backed raw-resource loader for this reason:

`docs/superpowers/specs/2026-07-31-f15b-catalog-generation-deduplication-design.md`, sections “Canonical site-profile source” and “Alternatives rejected”.

At this baseline, direct `BuiltInSiteProfiles` references occur in 23 production Kotlin files, 138 unit-test Kotlin files and four Android-test Kotlin files. Seventeen unit-test files have no `RobolectricTestRunner`; examples include `SiteProfileRegistryTest`, `RuntimeProfilePolicyTest`, `RuntimeProfileScopeTest`, `MitesProfileCatalogBindingTest` and `BatchSigningCoordinatorTest`. These counts describe files, not test-case counts.

A normal `Context.assets.open` or `Resources.openRawResource` replacement cannot preserve the existing no-Context API by itself. It needs one of:

- a new application initialization contract plus a global resource provider, with ordering and test-isolation implications;
- migration of callers to an explicitly owned catalog dependency, including areas concurrently owned by the consent worker;
- a different packaged-resource access strategy proven to work without Context.

A reconfigurable singleton, reflective `ActivityThread` application lookup, a test-only production fallback to the source tree, or retaining the large BuildConfig JSON as a fallback is not an acceptable shortcut. Keeping both payload paths would also fail to accomplish the transfer and would recreate drift risk.

`ClassLoader.getResourceAsStream` for an APK `assets/...` entry might provide a smaller boundary, but that is not an established resource seam in this repository. JVM classpath success alone would not prove Android APK lookup, variant packaging or minified-build behavior. No such Android evidence was produced, and no unsupported lookup was introduced under the no-startup/no-instrumentation task boundary.

## Concrete follow-up design, pending an owned loading boundary

1. Keep `config/site_profiles_v1.json` as the **only committed catalog data source**. Do not move or duplicate it into a manually maintained Android resource. Keep the public-catalog generator reading this same source.
2. Use a tracked, deterministic Gradle task to copy its exact bytes into one generated `assets/site_profiles_v1.json` (or generated `res/raw` after the loader decision). Declare input/output properties and connect generation through the Android variant source API so clean and incremental builds cannot silently package a stale copy. Do not add a Python dependency to Android builds.
3. Generate only a small expected SHA-256 value from the canonical bytes into BuildConfig. Remove the large JSON field only when all consumers have a supported reader. Verify packaged bytes against this expected digest **before** UTF-8 decoding and strict parsing. Missing, truncated, oversized or mismatched input must fail closed without fallback. The existing parser and activation/eligibility rules remain unchanged.
4. Bound byte reads; decode UTF-8 explicitly and strictly; preserve whitespace, newline and Unicode bytes. Do not normalize, trim, parse/re-serialize or infer trust from inventory metadata. Keep synchronized immutable catalog/registry caching and `BuiltInSiteProfiles.JSON` behavior for existing policy tests where a supported facade can be retained.
5. Choose ownership separately before implementation: either an explicit Android composition-owned catalog dependency, or an independently validated context-free packaged resource. Standard JVM resources could be evaluated as a different destination, but are not an implemented substitute for the requested Android assets/raw migration. Do not expand into a DI/ViewModel or consent refactor in this branch.

### Required regression matrix for an implementation

| Boundary | Independent assertion / required negative case |
| --- | --- |
| Canonical source -> generated resource | Exact byte equality, including trailing newline and non-ASCII content; source mutation forces resource regeneration on an incremental build. |
| Canonical source -> compiled expected integrity | Digest of canonical bytes equals the compiled expected digest; a stale/wrong expected value is rejected, not regenerated by the test from the already loaded payload. |
| Packaged resource -> runtime facade | Actual variant resource bytes equal canonical bytes and the facade JSON/canonical parsed model; prove the APK entry exists exactly once. |
| Runtime integrity failure | Mutated byte, truncation, missing resource, excessive length and digest mismatch all fail before catalog use; no BuildConfig/source-tree fallback. Include RED/GREEN evidence for each drift direction. |
| Public-catalog generator | Run the existing byte-for-byte reproducibility, single-source, missing binding, duplicate start URL and unexpected-root tests against the unchanged canonical input. |
| Policy | Retain strict parser mutation tests and QA/release registry policy tests, including plain-JVM callers; do not change trust origins, capabilities, activation or eligibility. |
| Variant/build lifecycle | Verify clean and incremental generation on applicable variants and exact-SHA CI before integration; do not infer Android resource or minified behavior from pure-JVM tests. |

These are acceptance requirements, **not tests claimed to exist or pass for an unimplemented migration**.

## Checks actually executed

Five existing focused Python tests passed (zero failures/errors) on the unchanged baseline code:

- `PublicPortalCatalogGeneratorTest.test_committed_resource_is_byte_for_byte_reproducible`
- `PublicPortalCatalogGeneratorTest.test_site_profile_catalog_has_one_committed_source`
- `PublicPortalCatalogGeneratorTest.test_missing_inventory_match_fails_closed`
- `PublicPortalCatalogGeneratorTest.test_duplicate_profile_start_url_fails_closed`
- `PublicPortalCatalogGeneratorTest.test_unexpected_site_profile_root_key_fails_closed`

Module: `tools.tests.test_generate_public_portal_catalog`.

These establish baseline generator/policy behavior only. They do not verify a resource migration. No Gradle task, Android build, lint, instrumentation, startup, portal request or installed-app action was run. The potential missing verified Termux aapt2 package was not retried or bootstrapped and is not presented as a newly reproduced build failure.

## Integration boundary

Only this research handoff is changed. No source/test/catalog, trusted origin, integrity rule, release/QA switch, consent/signing coordinator, UI, workflow or signing material is changed. No rebase, push, PR or merge was performed. The orchestrator owns any later implementation decision and serialized integration.

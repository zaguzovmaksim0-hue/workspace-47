# F-15B Catalog Generation Deduplication Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [x]`) syntax for tracking.

**Goal:** Remove all manually maintained duplicate site-profile/public-catalog sources while preserving the exact runtime trust model and public evidence claims.

**Architecture:** `config/site_profiles_v1.json` becomes the sole committed runtime-profile data source and is embedded by AGP into `BuildConfig`. The public catalog is generated only from the reviewed inventory, with profile bindings inferred by exact equality between profile `startUrl` and inventory `entry_url`.

**Tech Stack:** Gradle Kotlin DSL, Android BuildConfig, Kotlin/JUnit/Robolectric, Python 3/unittest/PyYAML, JSON/YAML inventory generation.

## Global Constraints

- Do not change any profile origin, endpoint, algorithm, adapter, capability, activation or evidence status.
- Do not add fuzzy, host-only, prefix or redirect matching; profile/public binding is exact URL equality only.
- Do not perform portal network access, authentication, signing or document submission.
- Do not store passwords, PKCS#12, private keys, certificates, signatures, cookies or personal identifiers.
- Do not push.

---

### Task 1: Add failing single-source and binding tests

**Files:**
- Modify: `tools/tests/test_generate_public_portal_catalog.py`

**Interfaces:**
- Consumes: current public-catalog generator and repository paths.
- Produces: regression expectations for `generate(inventory_source, profile_source)` and a single committed profile source.

- [x] **Step 1: Write failing tests**

Add tests that require:

```python
SITE_PROFILES = ROOT / "config" / "site_profiles_v1.json"
OLD_RAW_SITE_PROFILES = ROOT / "app" / "src" / "main" / "res" / "raw" / "site_profiles_v1.json"
REGISTRY_SOURCE = ROOT / "app" / "src" / "main" / "java" / "dev" / "junta" / "firmamobile" / "profile" / "SiteProfileRegistry.kt"
```

The tests must assert:

- `GENERATOR.generate(SOURCE, SITE_PROFILES)` is the supported API;
- every profile maps to one inventory record by exact `startUrl == entryUrl`;
- removing a matching inventory record fails;
- duplicate profile start URLs fail;
- `config/site_profiles_v1.json` exists;
- the old raw resource does not exist;
- `SiteProfileRegistry.kt` references `BuildConfig.SITE_PROFILE_CATALOG_JSON` and contains no embedded `const val JSON = """` body.

- [x] **Step 2: Run the focused tests and verify RED**

Run:

```bash
python -m unittest tools.tests.test_generate_public_portal_catalog -v
```

Expected: FAIL because the canonical config file, two-argument generator API and generated BuildConfig catalog do not exist yet.

### Task 2: Make the site-profile catalog single-source

**Files:**
- Move: `app/src/main/res/raw/site_profiles_v1.json` → `config/site_profiles_v1.json`
- Modify: `app/build.gradle.kts`
- Modify: `app/src/main/java/dev/junta/firmamobile/profile/SiteProfileRegistry.kt`
- Modify: `app/src/test/java/dev/junta/firmamobile/profile/SiteProfileCatalogParserTest.kt`

**Interfaces:**
- Produces: `BuildConfig.SITE_PROFILE_CATALOG_JSON: String` and unchanged `BuiltInSiteProfiles.JSON` test API.

- [x] **Step 1: Move the canonical JSON without content changes**

Use `git mv` and verify the file hash is unchanged.

- [x] **Step 2: Add safe multiline Java-string escaping in Gradle**

Add a dedicated helper that escapes backslash, quote, newline, carriage return, tab, backspace, form feed and remaining control characters as Java source escapes. Keep the existing strict relay-string helper unchanged.

Read `config/site_profiles_v1.json` with `providers.fileContents(...).asText.get()` and define:

```kotlin
buildConfigField(
    "String",
    "SITE_PROFILE_CATALOG_JSON",
    quotedBuildConfigText(siteProfileCatalogJson),
)
```

- [x] **Step 3: Remove the handwritten Kotlin catalog**

Replace the embedded body with:

```kotlin
val JSON: String = BuildConfig.SITE_PROFILE_CATALOG_JSON
```

Keep `catalog`, `releaseRegistry`, `qaRegistry` and `runtimeRegistry` behavior unchanged.

- [x] **Step 4: Update the resource-equivalence test**

Replace the deleted `R.raw.site_profiles_v1` check with assertions that `BuiltInSiteProfiles.JSON` equals `BuildConfig.SITE_PROFILE_CATALOG_JSON` and parses to `BuiltInSiteProfiles.catalog`.

### Task 3: Remove hardcoded public-catalog entries and bindings

**Files:**
- Modify: `docs/compatibility/all-spanish-public-portals-inventory.md`
- Modify: `tools/generate_public_portal_catalog.py`
- Modify: `tools/tests/test_generate_public_portal_catalog.py`
- Generate: `app/src/main/res/raw/public_portal_catalog_v1.json`

**Interfaces:**
- Consumes: inventory Markdown and `config/site_profiles_v1.json`.
- Produces: deterministic public catalog with all runtime profile IDs bound by exact URL.

- [x] **Step 1: Add the two missing reviewed inventory records**

Add stable records `ES-PUB-0181` (`junta-andalucia-ofvirtual`) and `ES-PUB-0182` (`educacion-convocatoria-46`) with the same reviewed dates, evidence scope, status and limitations previously emitted by `_supplemental_entries()`.

- [x] **Step 2: Implement strict profile loading and exact binding**

Delete `PROFILE_BINDINGS` and `_supplemental_entries()`.

Add profile loading that validates schema version 1, non-empty unique profile IDs, unique HTTPS start URLs and exact root/profile keys needed by the generator. Build a one-to-one `surface_key -> profileId` map by exact equality between profile `startUrl` and inventory `entry_url`. Fail when any profile is unmatched, multiply matched or collides.

- [x] **Step 3: Regenerate the public catalog**

Run:

```bash
python tools/generate_public_portal_catalog.py \
  --source docs/compatibility/all-spanish-public-portals-inventory.md \
  --profiles config/site_profiles_v1.json \
  --output app/src/main/res/raw/public_portal_catalog_v1.json
```

- [x] **Step 4: Run focused Python tests and verify GREEN**

Run:

```bash
python -m unittest tools.tests.test_generate_public_portal_catalog -v
```

Expected: all focused tests pass, current seven profiles are bound, and the committed output is byte-for-byte reproducible.

### Task 4: Run focused Android and repository verification

**Files:**
- Modify as needed only for failures directly caused by F-15B.

- [x] **Step 1: Run profile/catalog JVM tests**

```bash
./gradlew --no-daemon \
  testDebugUnitTest \
  --tests 'dev.junta.firmamobile.profile.*' \
  --tests 'dev.junta.firmamobile.catalog.*'
```

- [x] **Step 2: Run all Python tests**

```bash
python -m unittest discover -s tools/tests -p 'test_*.py' -v
```

- [x] **Step 3: Inspect diff and generated-data invariants**

Verify no second committed site-profile JSON exists, no hardcoded supplemental entries/bindings remain, and no trust/evidence values changed unintentionally.

### Task 5: Documentation, full gate and local commit

**Files:**
- Modify: `docs/security-roadmap.md`
- Modify: `docs/test-report.md`
- Modify: `docs/handoffs/NEXT_CHAT_HANDOFF.md`

- [x] **Step 1: Run the full fresh verification gate**

Run Debug/QA unit tests, lint, Debug/QA/QA-AndroidTest assemblies, all Python tests, Go test/vet/build, catalog reproducibility, APK artifact checks and forbidden-canary scans using the same commands/scripts as F-14.

- [x] **Step 2: Record exact results**

Document counts, failures/skips, hashes and environmental limitations. Do not claim physical-device or portal E2E testing.

- [x] **Step 3: Review staged diff**

Run `git diff --check`, inspect staged files, confirm no sensitive data and no unrelated changes.

- [x] **Step 4: Create one local commit**

```bash
git add config app tools docs
git commit -m "refactor(catalog): deduplicate generated sources"
```

Do not push.

## Completion note — 2026-07-31

All tasks completed. Final verification and residual DNS-test determinism note are
recorded in `docs/test-report.md`; no push or physical portal operation was
performed.

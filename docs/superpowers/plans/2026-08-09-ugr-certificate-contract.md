# UGR Certificate Contract Implementation Plan

> **Agent workflow:** follow `docs/agents/matt-pocock-workflow.md`; use Matt Pocock `codex/implement`/`codex/tdd` and `codex/code-review`. All Gradle verification follows `docs/agents/codex-cloud-gradle.md`. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Add the UGR certificate-login contract as a QA-only, fail-closed MiniApplet/AutoScript integration without changing generic, Aragón, or REG behavior.

**Architecture:** Bind one exact UGR profile to a local detached CAdES adapter. Scope textual-to-Base64 normalization and setup compatibility to the UGR profile/origin in the document-start shim, then revalidate the same contract in the native bridge and adapter. Bind the profile to the canonical public catalog through the inventory source and generator.

**Tech Stack:** Kotlin/JVM Android unit tests, Android WebView document-start JavaScript, Bouncy Castle detached CAdES, strict JSON profile/catalog parsers, Python inventory/catalog generator.

## Global Constraints

- Evidence source is only `/data/data/com.termux/files/home/workspace-47-autonomous-20260803/build/autonomous-evidence/g33-portal-research/ugr-sede/EVIDENCE_PACKET.md`.
- UGR start URL is `https://sede.ugr.es/Hades/jsp/pantallacertificado.jsp` and its only initiator origin is `https://sede.ugr.es`.
- The exact portal call is `AutoScript.sign(texto,'SHA1withRSA','CAdES',filtro,ok,error)` with `filtro=''`.
- `Universidad de Granada` is textual data and exactly 22 ASCII bytes; only the UGR path may canonicalize it to Base64 for native transport.
- UGR is `VERIFIED_CONTRACT` / `QA_ONLY`; inventory is `IMPLEMENTED_NOT_E2E`; catalog is `E2E_PENDING`; it is never release-enabled or `VERIFIED_E2E`.
- UGR has no signing endpoint; the observed Storage/Retrieve URLs are not signing endpoints and must not be called.
- Generic Base64 validation and generic empty-property parsing remain unchanged; all non-Base64 variants except the exact UGR literal are rejected.
- No real portal signing, authenticated navigation, credentials, certificate/private-key use, form submission, Android UI, ADB, or device control is allowed.
- Do not modify broad audit ledgers, test reports, or handoff files; do not hand-edit generated catalog JSON.

---

### Task 1: Establish the exact UGR RED regression suite

**Files:**
- Modify: `app/src/test/java/dev/junta/firmamobile/browser/AfirmaJavascriptShimTest.kt`
- Modify: `app/src/test/java/dev/junta/firmamobile/browser/MiniAppletBridgeAdapterTest.kt`
- Modify: `app/src/test/java/dev/junta/firmamobile/profile/SiteProfileCatalogParserTest.kt`
- Modify: `app/src/test/java/dev/junta/firmamobile/profile/SiteProfileRegistryTest.kt`
- Modify: `app/src/test/java/dev/junta/firmamobile/signing/LocalCadesDetachedAdapterTest.kt`
- Modify: `app/src/test/java/dev/junta/firmamobile/signing/ProtocolAdapterRegistryTest.kt`
- Modify: `app/src/test/java/dev/junta/firmamobile/catalog/PortalCatalogRepositoryTest.kt`
- Modify: `app/src/test/java/dev/junta/firmamobile/catalog/PublicPortalCatalogParserTest.kt`
- Modify: `tools/tests/test_generate_public_portal_catalog.py`

**Interfaces:** Tests must use the existing public profile, bridge, registry, adapter, catalog, and generator interfaces. New tests may name the exact UGR constants/API that the implementation will provide, but must not weaken existing assertions.

- [ ] **Step 1: Add one-behavior regression assertions for the UGR profile and lifecycle.** Assert the exact profile fields, QA resolution, release exclusion, no endpoint, no invented URL, exact callback binding, and catalog `ugr-sede`/`ES-PUB-0018` status.
- [ ] **Step 2: Add the shim contract assertions.** Assert the UGR-enabled shim contains the exact literal/tuple and scoped setup behavior, rejects literal variants, and that a non-UGR shim retains strict Base64 behavior and generic setup behavior.
- [ ] **Step 3: Add native bridge and adapter assertions.** Send the exact UGR JSON message with canonical Base64 and empty string, assert a decoded 22-byte payload, and reject wrong origin/profile/active profile/version/algorithm/format/property/data-length cases. Assert the detached adapter signs only the exact UGR contract and rejects Aragón/REG-like mutations while existing Aragón/REG tests remain green.
- [ ] **Step 4: Run the exact focused RED command before changing production/config.**

Run:

```bash
BRANCH="$(git branch --show-current)"
SHA="$(git rev-parse HEAD)"
git push -u origin "$BRANCH"
w47-cloud gradle --branch "$BRANCH" --sha "$SHA" :app:testDebugUnitTest --tests 'dev.junta.firmamobile.browser.AfirmaJavascriptShimTest' --tests 'dev.junta.firmamobile.browser.MiniAppletBridgeAdapterTest' --tests 'dev.junta.firmamobile.profile.SiteProfileCatalogParserTest' --tests 'dev.junta.firmamobile.profile.SiteProfileRegistryTest' --tests 'dev.junta.firmamobile.signing.LocalCadesDetachedAdapterTest' --tests 'dev.junta.firmamobile.signing.ProtocolAdapterRegistryTest' --tests 'dev.junta.firmamobile.catalog.PortalCatalogRepositoryTest' --tests 'dev.junta.firmamobile.catalog.PublicPortalCatalogParserTest'
```

Expected: FAIL because the exact UGR profile, shim flag/path, native contract, adapter binding, and generated catalog entry do not exist yet; record the real failure output without changing production/config first.

### Task 2: Add the fail-closed UGR profile and native contract

**Files:**
- Modify: `config/site_profiles_v1.json`
- Modify: `app/src/main/java/dev/junta/firmamobile/profile/SiteProfileCatalogParser.kt`
- Modify: `app/src/main/java/dev/junta/firmamobile/browser/MiniAppletBridgeAdapter.kt`
- Create or modify: `app/src/main/java/dev/junta/firmamobile/signing/UgrCadesDetachedAdapter.kt`
- Modify: `app/src/main/java/dev/junta/firmamobile/signing/ProtocolAdapterRegistry.kt`
- Modify: `app/src/main/java/dev/junta/firmamobile/MainActivity.kt`

**Interfaces:** The profile parser and bridge expose no generic relaxation. The UGR adapter implements `SigningProtocolAdapter`, uses the existing `CadesDetachedCodec`/payload ownership model, and returns the existing `ProtocolPrepareResult`/`ProtocolCompletionResult` types. The registry resolves `(ProfileId("ugr-certificado-login"), ProtocolOperation.SIGN)` to the UGR adapter id and `miniapplet-sign-callback-v1`.

- [ ] **Step 1: Add the exact profile JSON.** Use version 1, `VERIFIED_CONTRACT`, `QA_ONLY`, only `https://sede.ugr.es`, no endpoints, `SIGN`/`LEGACY_SHA1`, RSA digital-signature rules, and empty fixed/allowed properties for the exact UGR operation.
- [ ] **Step 2: Extend parser validation only for the exact UGR profile id.** Permit the empty fixed-property representation only when every UGR operation field is exact; keep the current endpoint/null-property rules for every other profile.
- [ ] **Step 3: Add UGR bridge normalization.** Accept only a string `extraProperties` equal to `""` for the exact UGR operation/profile; keep JSON `null` required for REG and canonical key/value parsing required elsewhere. Decode strict Base64 and pass the existing normalized payload path.
- [ ] **Step 4: Implement the UGR detached adapter.** Recheck protocol id, `ugr-certificado-login`, version 1, `https://sede.ugr.es`, SHA1withRSA, CAdES, empty properties, certificate presence, and exactly 22 payload bytes before using the detached CAdES codec. Do not alter Aragón constants or semantics.
- [ ] **Step 5: Bind and resolve the adapter.** Add the exact registry binding and MainActivity resolver branch; do not add an HTTP transport or endpoint.
- [ ] **Step 6: Run the focused Android GREEN command.**

Run:

```bash
BRANCH="$(git branch --show-current)"
SHA="$(git rev-parse HEAD)"
git push -u origin "$BRANCH"
w47-cloud gradle --branch "$BRANCH" --sha "$SHA" :app:testDebugUnitTest --tests 'dev.junta.firmamobile.browser.AfirmaJavascriptShimTest' --tests 'dev.junta.firmamobile.browser.MiniAppletBridgeAdapterTest' --tests 'dev.junta.firmamobile.profile.SiteProfileCatalogParserTest' --tests 'dev.junta.firmamobile.profile.SiteProfileRegistryTest' --tests 'dev.junta.firmamobile.signing.LocalCadesDetachedAdapterTest' --tests 'dev.junta.firmamobile.signing.ProtocolAdapterRegistryTest'
```

Expected: PASS, including unchanged generic, Aragón, and REG assertions.

### Task 3: Add the UGR-only JavaScript transport path

**Files:**
- Modify: `app/src/main/java/dev/junta/firmamobile/browser/AfirmaJavascriptShim.kt`
- Modify: `app/src/main/java/dev/junta/firmamobile/browser/WebMessageBridge.kt`
- Modify: `app/src/main/res/raw/afirma_shim.js`
- Modify: `app/src/test/java/dev/junta/firmamobile/browser/AfirmaJavascriptShimTest.kt`

**Interfaces:** `AfirmaJavascriptShim.load` receives a profile-scoped UGR compatibility value from `WebMessageBridge`; the raw shim continues to use document-start installation and the existing `JuntaFirmaMobile` bridge.

- [ ] **Step 1: Add one profile-scoped placeholder.** Configure it from the selected profile id, without accepting arbitrary page data as authority; native validation remains authoritative.
- [ ] **Step 2: Normalize the exact UGR literal.** For exact UGR flag + exact origin + exact `SHA1withRSA`/`CAdES`/`""` tuple, post canonical Base64 for the 22 ASCII bytes and preserve both success callback values. All other data still follows strict Base64 rejection.
- [ ] **Step 3: Intercept only exact UGR setup calls.** No-op only the exact `setForceWSMode(true)`, zero-argument `cargarAppAfirma`, and exact first-party Storage/Retrieve `setServlets` pair on the UGR page. Leave other calls untouched/fail-closed and never perform endpoint calls.
- [ ] **Step 4: Run the shim-focused tests and inspect the rendered script source.**

Run:

```bash
BRANCH="$(git branch --show-current)"
SHA="$(git rev-parse HEAD)"
git push -u origin "$BRANCH"
w47-cloud gradle --branch "$BRANCH" --sha "$SHA" :app:testDebugUnitTest --tests 'dev.junta.firmamobile.browser.AfirmaJavascriptShimTest'
```

Expected: PASS with the exact UGR branch and unchanged generic checks.

### Task 4: Bind inventory and regenerate the public catalog

**Files:**
- Modify: `docs/compatibility/all-spanish-public-portals-inventory.md`
- Regenerate: `app/src/main/res/raw/public_portal_catalog_v1.json`
- Modify: `tools/tests/test_generate_public_portal_catalog.py`
- Modify: `app/src/test/java/dev/junta/firmamobile/catalog/PublicPortalCatalogParserTest.kt`
- Modify: `app/src/test/java/dev/junta/firmamobile/catalog/PortalCatalogRepositoryTest.kt`

**Interfaces:** The generator consumes the inventory source and `config/site_profiles_v1.json`; no generated JSON is hand-edited.

- [ ] **Step 1: Update only the UGR inventory record.** Set the evidence-backed AutoScript/CAdES/SHA1 metadata, `IMPLEMENTED_NOT_E2E`, and a limitation stating that no portal E2E was performed; preserve its exact `ES-PUB-0018` and entry URL.
- [ ] **Step 2: Run the canonical generator.**

```bash
python3 tools/generate_public_portal_catalog.py --source docs/compatibility/all-spanish-public-portals-inventory.md --profiles config/site_profiles_v1.json --output app/src/main/res/raw/public_portal_catalog_v1.json
```

- [ ] **Step 3: Run generator and catalog tests.**

```bash
python3 tools/tests/test_generate_public_portal_catalog.py
BRANCH="$(git branch --show-current)"
SHA="$(git rev-parse HEAD)"
git push -u origin "$BRANCH"
w47-cloud gradle --branch "$BRANCH" --sha "$SHA" :app:testDebugUnitTest --tests 'dev.junta.firmamobile.catalog.PublicPortalCatalogParserTest' --tests 'dev.junta.firmamobile.catalog.PortalCatalogRepositoryTest'
```

Expected: UGR is `E2E_PENDING` / `IMPLEMENTED_NOT_E2E`, QA implementation is visible, release launch is unavailable, and all existing catalog invariants remain valid.

### Task 5: Final integration verification and atomic commit

**Files:** all files from Tasks 1–4 only.

- [ ] **Step 1: Run the complete focused Android test set and generator test.** Use the exact class filters from Tasks 1–4 plus `python3 tools/tests/test_generate_public_portal_catalog.py`.
- [ ] **Step 2: Run `git diff --check`.** It must exit zero.
- [ ] **Step 3: Inspect the complete diff.** Confirm no endpoint, credential, certificate/private-key, real portal navigation, Android UI/ADB operation, broad audit/test-report/handoff edit, or hand-edited generated catalog is present.
- [ ] **Step 4: Commit all intended files in one atomic commit.**

```bash
git add config/site_profiles_v1.json docs/compatibility/all-spanish-public-portals-inventory.md docs/superpowers/specs/2026-08-09-ugr-certificate-contract-design.md docs/superpowers/plans/2026-08-09-ugr-certificate-contract.md app/src/main/java/dev/junta/firmamobile/profile/SiteProfileCatalogParser.kt app/src/main/java/dev/junta/firmamobile/browser/MiniAppletBridgeAdapter.kt app/src/main/java/dev/junta/firmamobile/signing/UgrCadesDetachedAdapter.kt app/src/main/java/dev/junta/firmamobile/signing/ProtocolAdapterRegistry.kt app/src/main/java/dev/junta/firmamobile/MainActivity.kt app/src/main/java/dev/junta/firmamobile/browser/AfirmaJavascriptShim.kt app/src/main/java/dev/junta/firmamobile/browser/WebMessageBridge.kt app/src/main/res/raw/afirma_shim.js app/src/main/res/raw/public_portal_catalog_v1.json app/src/test tools/tests/test_generate_public_portal_catalog.py
git commit -m "feat: add QA-only UGR certificate contract"
```

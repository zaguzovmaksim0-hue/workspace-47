# Aragón SIRAW Profile Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Activate the reviewed Aragón SIRAW login-signature contract as a QA-only profile using the existing local detached CAdES adapter.

**Architecture:** Add an exact `aragon-siraw` profile to the bundled site-profile catalog, bind it to `LocalCadesDetachedAdapter`, instantiate the adapter in the application, and bind the existing public catalog entry through the deterministic generator. Keep Storage/Retrieve disabled and preserve `VERIFIED_CONTRACT / QA_ONLY` until physical-device E2E.

**Tech Stack:** Kotlin, Android WebView, AndroidX WebKit 1.16.0, Jetpack Compose, Bouncy Castle 1.84, Gradle 9.4.1, AGP 9.2.1, Python catalog generator, Context7 MCP 3.2.4.

## Global Constraints

- Base branch is `recovery/project-continuation-20260728` at or after `1570e34`.
- Exact origin is `https://aplicaciones.aragon.es`.
- Exact start URL is `https://aplicaciones.aragon.es/siraw/pages/login.xhtml?origen=siefw`.
- Status is `VERIFIED_CONTRACT`; activation is `QA_ONLY`.
- Only `SHA1_WITH_RSA`, detached `CADES`, `EXPLICIT`, and exact properties `mode=explicit` plus `filter=nonexpired` are allowed.
- Challenge length remains exactly 20 bytes.
- StorageService and RetrieveService are not runtime endpoints or capabilities in this milestone.
- No portal-acceptance or `VERIFIED_E2E` claim is permitted.

---

### Task 1: Define the exact SIRAW profile

**Files:**
- Modify: `app/src/main/res/raw/site_profiles_v1.json`
- Modify: `app/src/main/java/dev/junta/firmamobile/profile/SiteProfileRegistry.kt`
- Modify: `app/src/test/java/dev/junta/firmamobile/profile/SiteProfileCatalogParserTest.kt`
- Modify: `app/src/test/java/dev/junta/firmamobile/profile/SiteProfileRegistryTest.kt`

**Interfaces:**
- Produces: `ProfileId("aragon-siraw")` with one exact `ProtocolOperation.SIGN` policy.
- Consumes: Existing `SiteProfileCatalogParser`, `BuiltInSiteProfiles`, and `LocalCadesDetachedAdapter` constants.

- [ ] **Step 1: Add failing parser assertions**

Add assertions that the parsed profile has:

```kotlin
assertEquals(ProfileId("aragon-siraw"), profile.profileId)
assertEquals(CompatibilityStatus.VERIFIED_CONTRACT, profile.compatibilityStatus)
assertEquals(ProfileActivation.QA_ONLY, profile.activation)
assertEquals(setOf(ExactOrigin.parse("https://aplicaciones.aragon.es")), profile.initiatorOrigins)
assertEquals(setOf(Capability.SIGN, Capability.LEGACY_SHA1), profile.capabilities)
assertEquals(SignatureAlgorithm.SHA1_WITH_RSA, policy.algorithms.single())
assertEquals(SignatureFormat.CADES, policy.format)
assertEquals(SignaturePackaging.DETACHED, policy.packaging)
assertEquals(SignatureMode.EXPLICIT, policy.mode)
assertEquals(
    mapOf("mode" to "explicit", "filter" to "nonexpired"),
    policy.fixedExtraProperties,
)
assertTrue(profile.endpoints.isEmpty())
```

- [ ] **Step 2: Run parser and registry tests and confirm failure**

Run:

```bash
./gradlew testDebugUnitTest \
  --tests dev.junta.firmamobile.profile.SiteProfileCatalogParserTest \
  --tests dev.junta.firmamobile.profile.SiteProfileRegistryTest \
  --no-daemon
```

Expected: failure because `aragon-siraw` is absent.

- [ ] **Step 3: Add the profile to both bundled catalog sources**

Add the exact seventh profile to `site_profiles_v1.json` and the embedded fallback JSON in `SiteProfileRegistry.kt`. Use:

```json
{
  "profileId": "aragon-siraw",
  "profileVersion": 1,
  "displayName": "Gobierno de Aragón — SIRAW",
  "compatibilityStatus": "VERIFIED_CONTRACT",
  "activation": "QA_ONLY",
  "startUrl": "https://aplicaciones.aragon.es/siraw/pages/login.xhtml?origen=siefw",
  "initiatorOrigins": ["https://aplicaciones.aragon.es"],
  "redirectOrigins": [],
  "trustedBrowseOrigins": [],
  "endpoints": [],
  "operationPolicies": [{
    "operation": "SIGN",
    "safeDescription": "Acceso con certificado a SIRAW",
    "inputAdapterId": "miniapplet-autoscript-v1",
    "callbackContractId": "miniapplet-sign-callback-v1",
    "capabilities": ["SIGN", "LEGACY_SHA1"],
    "endpointId": null,
    "algorithms": ["SHA1_WITH_RSA"],
    "format": "CADES",
    "packaging": "DETACHED",
    "mode": "EXPLICIT",
    "fixedExtraProperties": {"mode": "explicit", "filter": "nonexpired"},
    "allowedExtraProperties": []
  }],
  "capabilities": ["SIGN", "LEGACY_SHA1"],
  "clientAuthPolicy": null,
  "certificateRules": {
    "allowedKeyAlgorithms": ["RSA"],
    "requireDigitalSignatureKeyUsage": true
  },
  "evidence": [
    {"url": "https://aplicaciones.aragon.es/siraw/pages/login.xhtml?origen=siefw", "reviewedOn": "2026-07-28"},
    {"url": "https://aplicaciones.aragon.es/siraw/javax.faces.resource/js/afirma.js.xhtml", "reviewedOn": "2026-07-28"}
  ]
}
```

Increment `catalogVersion` from `5` to `6` in both sources.

- [ ] **Step 4: Add release/QA resolution assertions**

In `SiteProfileRegistryTest`, assert:

```kotlin
assertNotNull(BuiltInSiteProfiles.qaRegistry.profile(ProfileId("aragon-siraw")))
assertNull(BuiltInSiteProfiles.releaseRegistry.profile(ProfileId("aragon-siraw")))
```

- [ ] **Step 5: Run parser and registry tests**

Run the Step 2 command. Expected: PASS.

- [ ] **Step 6: Commit**

```bash
git add app/src/main/res/raw/site_profiles_v1.json \
  app/src/main/java/dev/junta/firmamobile/profile/SiteProfileRegistry.kt \
  app/src/test/java/dev/junta/firmamobile/profile/SiteProfileCatalogParserTest.kt \
  app/src/test/java/dev/junta/firmamobile/profile/SiteProfileRegistryTest.kt
git commit -m "feat: add Aragón SIRAW site profile"
```

### Task 2: Wire the local CAdES adapter

**Files:**
- Modify: `app/src/main/java/dev/junta/firmamobile/signing/ProtocolAdapterRegistry.kt`
- Modify: `app/src/main/java/dev/junta/firmamobile/MainActivity.kt`
- Modify: `app/src/test/java/dev/junta/firmamobile/signing/ProtocolAdapterRegistryTest.kt`
- Modify: `app/src/test/java/dev/junta/firmamobile/browser/MiniAppletBridgeAdapterTest.kt`

**Interfaces:**
- Consumes: `LocalCadesDetachedAdapter.ID` and `ProfileId("aragon-siraw")`.
- Produces: exact runtime resolution of Aragón SIGN requests to `LocalCadesDetachedAdapter`.

- [ ] **Step 1: Add failing registry test**

Add:

```kotlin
val binding = BuiltInProtocolAdapterRegistry.registry.resolve(
    ProfileId("aragon-siraw"),
    ProtocolOperation.SIGN,
)
assertEquals(LocalCadesDetachedAdapter.ID, binding?.signingProtocolId)
assertEquals(CallbackContractId("miniapplet-sign-callback-v1"), binding?.callbackContractId)
```

- [ ] **Step 2: Add failing bridge-routing test**

Create an active Aragón profile request with exact origin, 20-byte payload, `SHA1withRSA`, `CAdES`, and `mode=explicit\nfilter=nonexpired`. Assert the bridge produces a pending request whose `protocolId` is `LocalCadesDetachedAdapter.ID`. Repeat with another active profile and assert rejection before signing.

- [ ] **Step 3: Run focused tests and confirm failure**

```bash
./gradlew testDebugUnitTest \
  --tests dev.junta.firmamobile.signing.ProtocolAdapterRegistryTest \
  --tests dev.junta.firmamobile.browser.MiniAppletBridgeAdapterTest \
  --no-daemon
```

Expected: registry resolution is null and the bridge cannot route Aragón.

- [ ] **Step 4: Add the protocol binding**

Add to `BuiltInProtocolAdapterRegistry`:

```kotlin
ProtocolAdapterBinding(
    profileId = ProfileId(LocalCadesDetachedAdapter.PROFILE_ID),
    operation = ProtocolOperation.SIGN,
    inputAdapterId = ProtocolInputAdapterId("miniapplet-autoscript-v1"),
    callbackContractId = CallbackContractId("miniapplet-sign-callback-v1"),
    signingProtocolId = LocalCadesDetachedAdapter.ID,
)
```

- [ ] **Step 5: Instantiate and resolve the adapter in MainActivity**

Add:

```kotlin
val aragonAdapter = LocalCadesDetachedAdapter()
```

and resolve `aragonAdapter.id -> aragonAdapter` in `adapterResolver`.

- [ ] **Step 6: Run focused tests**

Run the Step 3 command. Expected: PASS.

- [ ] **Step 7: Run cryptographic regression test**

```bash
./gradlew testDebugUnitTest \
  --tests dev.junta.firmamobile.signing.LocalCadesDetachedAdapterTest \
  --no-daemon
```

Expected: PASS, including detached CMS validation and tamper rejection.

- [ ] **Step 8: Commit**

```bash
git add app/src/main/java/dev/junta/firmamobile/signing/ProtocolAdapterRegistry.kt \
  app/src/main/java/dev/junta/firmamobile/MainActivity.kt \
  app/src/test/java/dev/junta/firmamobile/signing/ProtocolAdapterRegistryTest.kt \
  app/src/test/java/dev/junta/firmamobile/browser/MiniAppletBridgeAdapterTest.kt
git commit -m "feat: route Aragón SIRAW to local CAdES"
```

### Task 3: Bind the public catalog entry

**Files:**
- Modify: `tools/generate_public_portal_catalog.py`
- Modify: `tools/tests/test_generate_public_portal_catalog.py`
- Modify: `docs/compatibility/all-spanish-public-portals-inventory.md`
- Generate: `app/src/main/res/raw/public_portal_catalog_v1.json`
- Modify: `app/src/test/java/dev/junta/firmamobile/catalog/PortalCatalogRepositoryTest.kt`

**Interfaces:**
- Produces: `aragon-siraw` catalog entry with `profileId="aragon-siraw"`, `catalogStatus="E2E_PENDING"`, and `inventoryStatus="IMPLEMENTED_NOT_E2E"`.

- [ ] **Step 1: Add failing generator test**

Assert that generated Aragón has:

```python
assert aragon["profileId"] == "aragon-siraw"
assert aragon["catalogStatus"] == "E2E_PENDING"
assert aragon["inventoryStatus"] == "IMPLEMENTED_NOT_E2E"
```

- [ ] **Step 2: Add failing repository test**

Assert that the repository exposes Aragón as an openable compatible profile in QA and that its status text remains E2E pending.

- [ ] **Step 3: Run tests and confirm failure**

```bash
python -m unittest tools.tests.test_generate_public_portal_catalog
./gradlew testDebugUnitTest \
  --tests dev.junta.firmamobile.catalog.PortalCatalogRepositoryTest \
  --no-daemon
```

Expected: profile binding/status assertions fail.

- [ ] **Step 4: Add the generator binding**

Add:

```python
"aragon-siraw": "aragon-siraw",
```

to `PROFILE_BINDINGS`.

- [ ] **Step 5: Update the reviewed inventory record**

For `surface_key: "aragon-siraw"`, set:

```yaml
inventory_status: "IMPLEMENTED_NOT_E2E"
reason: "Contrato MiniApplet exacto y adaptador CAdES local implementados; aceptación E2E del portal pendiente."
reviewed_at: "2026-07-28"
```

Do not add Storage/Retrieve support claims.

- [ ] **Step 6: Regenerate the catalog**

```bash
python tools/generate_public_portal_catalog.py \
  --source docs/compatibility/all-spanish-public-portals-inventory.md \
  --output app/src/main/res/raw/public_portal_catalog_v1.json
```

- [ ] **Step 7: Run generator and repository tests**

Run the Step 3 commands. Expected: PASS.

- [ ] **Step 8: Verify deterministic generation**

```bash
cp app/src/main/res/raw/public_portal_catalog_v1.json "$HOME/.cache/public_portal_catalog_v1.first.json"
python tools/generate_public_portal_catalog.py \
  --source docs/compatibility/all-spanish-public-portals-inventory.md \
  --output app/src/main/res/raw/public_portal_catalog_v1.json
cmp "$HOME/.cache/public_portal_catalog_v1.first.json" app/src/main/res/raw/public_portal_catalog_v1.json
rm -f "$HOME/.cache/public_portal_catalog_v1.first.json"
```

Expected: `cmp` exits 0.

- [ ] **Step 9: Commit**

```bash
git add tools/generate_public_portal_catalog.py \
  tools/tests/test_generate_public_portal_catalog.py \
  docs/compatibility/all-spanish-public-portals-inventory.md \
  app/src/main/res/raw/public_portal_catalog_v1.json \
  app/src/test/java/dev/junta/firmamobile/catalog/PortalCatalogRepositoryTest.kt
git commit -m "feat: expose Aragón SIRAW in service catalog"
```

### Task 4: Full verification and documentation

**Files:**
- Modify: `docs/compatibility/spanish-government-signing-matrix.md`
- Modify: `docs/security-roadmap.md`
- Modify: `docs/test-report.md`

**Interfaces:**
- Produces: truthful milestone documentation with `VERIFIED_CONTRACT / IMPLEMENTED_NOT_E2E`.

- [ ] **Step 1: Update documentation**

Document the exact profile, local CAdES adapter, disabled Storage/Retrieve, QA-only activation, tests, and absence of portal E2E.

- [ ] **Step 2: Run full debug and QA unit suites**

```bash
./gradlew testDebugUnitTest testQaUnitTest --no-daemon
```

Expected: PASS.

- [ ] **Step 3: Run lint and assemble gates**

```bash
./gradlew lintDebug lintQa assembleDebug assembleQa --no-daemon
```

Expected: PASS.

- [ ] **Step 4: Validate Git state and generated catalog**

```bash
git diff --check
python -m unittest tools.tests.test_generate_public_portal_catalog
python tools/generate_public_portal_catalog.py \
  --source docs/compatibility/all-spanish-public-portals-inventory.md \
  --output "$HOME/.cache/public_portal_catalog_v1.verify.json"
cmp "$HOME/.cache/public_portal_catalog_v1.verify.json" app/src/main/res/raw/public_portal_catalog_v1.json
rm -f "$HOME/.cache/public_portal_catalog_v1.verify.json"
```

Expected: all commands exit 0.

- [ ] **Step 5: Commit documentation**

```bash
git add docs/compatibility/spanish-government-signing-matrix.md \
  docs/security-roadmap.md docs/test-report.md
git commit -m "docs: record Aragón SIRAW implementation"
```

- [ ] **Step 6: Record final evidence**

```bash
git status --short --branch
git log --oneline --decorate -6
```

Expected: clean worktree on `recovery/project-continuation-20260728`. Do not push without explicit user instruction.

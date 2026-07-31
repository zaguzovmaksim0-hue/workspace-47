# AEAT Client TLS F-03 Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Add an exact, QA-only AEAT Client TLS profile for the read-only `Mis datos censales` login path and verify it without broadening release trust.

**Architecture:** Extend the existing Client TLS policy with an explicit transition mode. Carné Joven retains its two-stage redirect contract; AEAT uses a one-step modern main-frame transition from one exact source URL to one exact queryless target. The existing preference barrier, native confirmation, dedicated WebView and request-handler validation remain the only certificate-delivery path.

**Tech Stack:** Kotlin, Android WebView, Compose, Gradle, JUnit/Robolectric, Python catalog generator, Shizuku/rish physical-device QA.

## Global Constraints

- AEAT starts as `VERIFIED_CONTRACT / QA_ONLY` and is unavailable in release.
- Exact source: `https://sede.agenciatributaria.gob.es/Sede/mi-area-personal.html`.
- Exact request target: `https://www1.agenciatributaria.gob.es/wlpl/BUGC-JDIT/MdcAcceso`.
- No query, fragment, non-443 port, subframe or legacy callback may authorize AEAT Client TLS.
- The only in-scope outcome is authenticated read-only access; no modification, signing, filing, payment or submission.
- No certificate, key, password, cookie, personal data, screenshot or response body may be persisted.
- Release remains unchanged until a separate explicit promotion after physical E2E.

---

## File map

- `app/src/main/java/dev/junta/firmamobile/profile/ProfileModels.kt`: transition-mode enum and policy field.
- `app/src/main/java/dev/junta/firmamobile/profile/SiteProfileCatalogParser.kt`: strict JSON parsing and mode-specific invariants.
- `app/src/main/java/dev/junta/firmamobile/browser/ClientAuthNavigationAuthorizer.kt`: exact direct-transition authorization.
- `config/site_profiles_v1.json`: Carné Joven mode plus AEAT QA profile.
- `app/src/main/res/raw/public_portal_catalog_v1.json`: generated public binding and honest status.
- `app/src/test/java/dev/junta/firmamobile/profile/SiteProfileCatalogParserTest.kt`: exact profile and release-isolation tests.
- `app/src/test/java/dev/junta/firmamobile/browser/ClientAuthNavigationAuthorizerTest.kt`: direct and hostile transition tests.
- `app/src/test/java/dev/junta/firmamobile/browser/ClientAuthRequestHandlerTest.kt`: key-type/issuer regression coverage.
- `tools/tests/test_generate_public_portal_catalog.py`: generated catalog binding/status tests if existing coverage is insufficient.
- `docs/compatibility/*`, `docs/security-roadmap.md`, `docs/test-plan.md`, `docs/test-report.md`, `docs/handoffs/NEXT_CHAT_HANDOFF.md`: contract and evidence boundaries.

---

### Task 1: Add transition-mode contract with parser RED/GREEN

**Files:**
- Modify: `app/src/test/java/dev/junta/firmamobile/profile/SiteProfileCatalogParserTest.kt`
- Modify: `app/src/main/java/dev/junta/firmamobile/profile/ProfileModels.kt`
- Modify: `app/src/main/java/dev/junta/firmamobile/profile/SiteProfileCatalogParser.kt`
- Modify: `config/site_profiles_v1.json`

**Interfaces:**
- Produces: `enum class ClientAuthTransitionMode { REDIRECT_AFTER_SOURCE, DIRECT_FROM_SOURCE }`
- Produces: `ClientAuthPolicy.transitionMode: ClientAuthTransitionMode`

- [x] **Step 1: Write parser tests that require an explicit mode**

Add assertions that Carné Joven parses as `REDIRECT_AFTER_SOURCE`, that removing
`transitionMode` is rejected, and that a direct mode accepts empty fixed and
ephemeral parameter sets only when the exact target is queryless.

- [x] **Step 2: Run the focused parser tests and verify RED**

Run:

```bash
./gradlew :app:testDebugUnitTest --tests '*SiteProfileCatalogParserTest*' --no-daemon
```

Expected: compilation/test failure because `ClientAuthTransitionMode` and the
required JSON field do not exist.

- [x] **Step 3: Implement the minimal model/parser change**

Add the enum and field. Parse `transitionMode` as a required exact key. Enforce:

```kotlin
when (transitionMode) {
    ClientAuthTransitionMode.REDIRECT_AFTER_SOURCE ->
        require(fixed.isNotEmpty() || ephemeral.isNotEmpty())
    ClientAuthTransitionMode.DIRECT_FROM_SOURCE ->
        require(fixed.isEmpty() && ephemeral.isEmpty())
}
```

Add `"transitionMode":"REDIRECT_AFTER_SOURCE"` to Carné Joven.

- [x] **Step 4: Re-run focused parser tests and verify GREEN**

Run the Step 2 command. Expected: PASS.

---

### Task 2: Implement exact direct-source authorization with hostile TDD

**Files:**
- Modify: `app/src/test/java/dev/junta/firmamobile/browser/ClientAuthNavigationAuthorizerTest.kt`
- Modify: `app/src/main/java/dev/junta/firmamobile/browser/ClientAuthNavigationAuthorizer.kt`

**Interfaces:**
- Consumes: `ClientAuthPolicy.transitionMode`
- Produces: immediate one-shot `AuthorizedClientAuthTarget` for a valid `DIRECT_FROM_SOURCE` transition.

- [x] **Step 1: Add direct-transition success test using a test catalog**

Create an exact AEAT test profile and assert one modern main-frame transition
from the exact source to the exact target returns one authorization and a repeat
returns no authorization.

- [x] **Step 2: Add hostile direct-transition tests**

Assert rejection for:

```text
legacy callback
subframe
null/wrong profile
wrong current URL
wrong source origin or source path
wrong target host or suffix host
wrong path or encoded path
non-443 port
fragment
any query
empty query marker
```

- [x] **Step 3: Run focused authorizer tests and verify RED**

Run:

```bash
./gradlew :app:testDebugUnitTest --tests '*ClientAuthNavigationAuthorizerTest*' --no-daemon
```

Expected: direct AEAT transition returns null because only pending redirect mode
exists.

- [x] **Step 4: Implement minimal mode dispatch**

For `DIRECT_FROM_SOURCE`, clear pending state, require `currentUrl` to equal an
exact policy source URL, require `target.matches(policy)`, and return the bounded
target immediately. Preserve the existing code path unchanged for
`REDIRECT_AFTER_SOURCE`.

Update target matching so an empty expected parameter set requires
`rawQuery == null`; an empty `?` is rejected.

- [x] **Step 5: Re-run focused authorizer tests and verify GREEN**

Run the Step 3 command. Expected: all direct and existing Carné Joven tests pass.

---

### Task 3: Add exact AEAT QA profile and release isolation

**Files:**
- Modify: `config/site_profiles_v1.json`
- Modify: `app/src/test/java/dev/junta/firmamobile/profile/SiteProfileCatalogParserTest.kt`
- Modify: `app/src/test/java/dev/junta/firmamobile/profile/SiteProfileRegistryTest.kt`
- Modify: `app/src/test/java/dev/junta/firmamobile/profile/RuntimeProfilePolicyTest.kt`

**Interfaces:**
- Produces: `ProfileId("aeat-mis-datos-censales")` in QA registry only.

- [x] **Step 1: Write exact profile and registry tests**

Assert:

```kotlin
compatibilityStatus == VERIFIED_CONTRACT
activation == QA_ONLY
transitionMode == DIRECT_FROM_SOURCE
requestOrigins == { https://www1.agenciatributaria.gob.es }
sourceUrls == { https://sede.agenciatributaria.gob.es/Sede/mi-area-personal.html }
requestPath == /wlpl/BUGC-JDIT/MdcAcceso
fixedQueryParameters.isEmpty()
requiredEphemeralQueryParameters.isEmpty()
allowEmptyIssuerList == false
allowedKeyAlgorithms == { RSA, EC }
```

Also assert QA resolves the source as `TRUSTED_CLIENT_AUTH`, QA resolves the
request origin only as `BROWSE_ONLY`, and release resolves neither profile nor
origins.

- [x] **Step 2: Run focused profile tests and verify RED**

Run:

```bash
./gradlew :app:testDebugUnitTest \
  --tests '*SiteProfileCatalogParserTest*' \
  --tests '*SiteProfileRegistryTest*' \
  --tests '*RuntimeProfilePolicyTest*' \
  --no-daemon
```

Expected: AEAT profile is absent.

- [x] **Step 3: Add the minimal QA profile**

Increment `catalogVersion` and add the exact profile described in Global
Constraints. Do not add redirect or trusted-browse origins, endpoints, signing
operations, or extra capabilities.

- [x] **Step 4: Re-run focused profile tests and verify GREEN**

Run Step 2. Expected: PASS.

---

### Task 4: Preserve request-handler fail-closed constraints

**Files:**
- Modify: `app/src/test/java/dev/junta/firmamobile/browser/ClientAuthRequestHandlerTest.kt`
- Modify only if tests expose a defect: `app/src/main/java/dev/junta/firmamobile/browser/ClientAuthWebViewClient.kt`

**Interfaces:**
- Consumes: AEAT `allowedKeyAlgorithms={RSA,EC}`, `allowEmptyIssuerList=false`.

- [x] **Step 1: Add tests for AEAT-style offers**

Verify a matching RSA identity can proceed when the request advertises RSA and
an acceptable issuer matches the chain. Verify an empty principal list is
ignored for AEAT policy, and EC/ECDSA normalization remains exact.

- [x] **Step 2: Run focused handler tests**

Run:

```bash
./gradlew :app:testDebugUnitTest --tests '*ClientAuthRequestHandlerTest*' --no-daemon
```

Expected: PASS if existing behavior already satisfies the contract; otherwise a
behavioral failure must precede the minimal production fix.

- [x] **Step 3: Make only an evidence-driven fix if RED occurs**

Do not relax issuer, EKU, key-usage, validity, epoch, TTL or host/port checks.

- [x] **Step 4: Re-run focused tests**

Expected: PASS.

---

### Task 5: Bind the public catalog without overstating evidence

**Files:**
- Modify source inventory/mapping consumed by `tools/generate_public_portal_catalog.py`
- Regenerate: `app/src/main/res/raw/public_portal_catalog_v1.json`
- Modify: `tools/tests/test_generate_public_portal_catalog.py`

**Interfaces:**
- Produces: `aeat-sede.profileId == "aeat-mis-datos-censales"` while inventory
  status remains contract-only/QA, not E2E.

- [x] **Step 1: Add catalog-generation assertions**

Assert the AEAT entry binds to the profile and that its limitations explicitly
state exact Client TLS contract observed, QA-only, physical E2E pending, and no
signing/submission claim.

- [x] **Step 2: Run Python generator tests and verify RED**

Run:

```bash
python -m unittest tools.tests.test_generate_public_portal_catalog -v
```

Expected: binding assertion fails because `profileId` is null.

- [x] **Step 3: Update the canonical inventory source and regenerate**

Use the repository generator rather than hand-editing generated JSON. Preserve
all unrelated entry IDs and ordering.

- [x] **Step 4: Re-run generator and policy tests**

Run:

```bash
python -m unittest tools.tests.test_generate_public_portal_catalog tools.tests.test_ci_policy -v
```

Expected: PASS and deterministic regenerated output.

---

### Task 6: Document contract and physical gate

**Files:**
- Modify: `docs/compatibility/spanish-government-signing-matrix.md`
- Modify: `docs/compatibility/all-spanish-public-portals-inventory.md`
- Modify: `docs/security-roadmap.md`
- Modify: `docs/threat-model.md`
- Modify: `docs/test-plan.md`
- Modify: `docs/test-report.md`
- Modify: `docs/handoffs/NEXT_CHAT_HANDOFF.md`

- [x] **Step 1: Record only verified facts**

Document exact source/target, TLS `CertificateRequest`, QA-only status, direct
transition mode and remaining Android callback/E2E gates. Do not claim WebView
or portal acceptance before physical evidence.

- [x] **Step 2: Run documentation/policy checks**

Run:

```bash
python -m unittest tools.tests.test_ci_policy -v
git diff --check
```

Expected: PASS.

---

### Task 7: Run complete local verification

**Files:** none unless failures reveal defects.

- [x] **Step 1: Run full Android verification**

```bash
./gradlew \
  :app:verifyRuntimeDependencyLocks \
  verifyResolvedCoreVersion \
  verifyPortableAapt2Configuration \
  :app:testDebugUnitTest \
  :app:testQaUnitTest \
  :app:lintDebug \
  :app:lintQa \
  :app:assembleDebug \
  :app:assembleQa \
  :app:assembleQaAndroidTest \
  --rerun-tasks --no-build-cache --no-daemon
```

Expected: BUILD SUCCESSFUL.

- [x] **Step 2: Run artifact and release gates**

```bash
scripts/ci/verify-android-artifacts.sh
scripts/ci/verify-release-fail-closed.sh
```

Expected: artifact checks pass and release remains fail-closed without signing
credentials.

- [x] **Step 3: Run full Python and unchanged Go regression gates**

```bash
python -m unittest discover -s tools/tests -v
(cd ws024-relay && go test ./... -count=1 && go vet ./... && go build ./cmd/ws024-relay)
rm -f ws024-relay/ws024-relay
```

Expected: PASS, allowing only the existing environmental hardlink skip.

---

### Task 8: Perform physical QA and decide status honestly

**Files:**
- Create only if successful or blocked evidence is sanitized: `docs/e2e/2026-07-31-aeat-client-tls-*.md`
- Modify status/docs only after observed result.

- [x] **Step 1: Install the exact QA APK through the existing Shizuku/rish path**

Verify installed package hash matches the built QA APK. Do not export app-private
certificate material or browser data.

- [ ] **Step 2: Open `aeat-sede` and perform only the exact source-to-target flow**

Use the app UI to open `Mi área personal`, select `Mis datos censales`, confirm
the native certificate prompt, and observe whether the authenticated read-only
landing page opens. Stop before every modification or submission control.

- [x] **Step 3: Record sanitized outcome**

Allowed evidence:

```text
exact build hash
callback observed: yes/no
request host and port
normalized offered key-type set
issuer count and digest only
native confirmation shown: yes/no
portal accepted authentication: yes/no
final origin/path category without query or personal data
```

- [x] **Step 4: Apply status rule**

If all physical gates pass, change only this profile to
`VERIFIED_E2E / ENABLED`, update catalog/docs/tests, rerun impacted gates, and
record the exact scope. Otherwise retain `VERIFIED_CONTRACT / QA_ONLY` and record
the blocker without weakening validation.

---

### Task 9: Commit, push and update PR

- [x] **Step 1: Review staged scope**

```bash
git status --short
git diff --check
git diff --stat
git diff --cached --check
```

- [ ] **Step 2: Commit implementation**

```bash
git add <reviewed files>
git commit -m "feat(client-tls): add exact AEAT QA profile"
```

- [ ] **Step 3: Push and verify PR #6**

```bash
git push origin feature/ws024-secure-tunnel-20260728
gh pr view 6 --json url,state,headRefOid,statusCheckRollup
```

Expected: PR remains open and contains the new commits.

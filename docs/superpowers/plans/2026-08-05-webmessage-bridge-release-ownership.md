# WebMessage Bridge Release Ownership Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Close every WebMessage bridge attachment at its exact WebView ownership boundary without allowing a stale release callback to close a replacement bridge.

**Architecture:** Add a pure Kotlin atomic owner/resource lease and use it as the sole bridge lifecycle holder in `BrowserScreen`. Attachment creation binds owner and resource; exact `AndroidView.onRelease`, renderer recovery, Client TLS transition, navigation invalidation and full disposal delegate through the lease.

**Tech Stack:** Kotlin, `AtomicReference`, Android WebView, Jetpack Compose `AndroidView`, JUnit/Robolectric source-policy regression, Gradle Debug/QA gates.

## Global Constraints

- Work only in `/data/data/com.termux/files/home/workspace-47-autonomous-20260803` on `agent/workspace-47-autonomous-20260803`.
- Preserve canonical branch `feature/ws024-secure-tunnel-20260728` at `9c99bbfb36e13f88231d56001ccef8c4cbbce128`.
- No APK installation/launch, ADB/device control, portal interaction, credentials, certificates, signatures, upload, payment or submission.
- Do not change WebMessage payloads, JavaScript, origin, TLS, signing, profile, catalog, release or dependency policy.
- Evidence documents change only after fresh final verification.

---

### Task 1: Owner-bound closeable lease

**Files:**
- Create: `app/src/test/java/dev/junta/firmamobile/ui/BrowserOwnedResourceLeaseTest.kt`
- Create: `app/src/main/java/dev/junta/firmamobile/ui/BrowserOwnedResourceLease.kt`

**Interfaces:**
- Produces: `BrowserOwnedResourceLease<Owner : Any, Resource : AutoCloseable>` with `bind(owner, resource)`, `current()`, `release(owner)` and `close()`.
- Guarantees: replacement closes superseded resource; stale owner cannot close current resource; exact release and full close are one-shot.

- [ ] **Step 1: Write the failing ownership test**

Create one test that binds owner A/resource A, binds owner B/resource B, verifies A was closed, verifies stale release A cannot close B, then releases B and verifies B closes once and the lease is empty.

- [ ] **Step 2: Run RED**

Run:

```bash
./gradlew --no-daemon --rerun-tasks testDebugUnitTest \
  --tests dev.junta.firmamobile.ui.BrowserOwnedResourceLeaseTest
```

Expected: Kotlin test compilation fails because `BrowserOwnedResourceLease` does not exist.

- [ ] **Step 3: Implement the minimum lease**

Use an `AtomicReference<Binding<Owner, Resource>?>`. `bind` installs the replacement before closing the superseded resource. `release` uses exact referential owner identity and a compare-and-set loop. `close` atomically clears and closes the current resource.

- [ ] **Step 4: Run focused GREEN**

Run the same Debug test and require zero failures.

### Task 2: Bind BrowserScreen bridge lifecycle

**Files:**
- Modify: `app/src/test/java/dev/junta/firmamobile/browser/BrowserSecurityRegressionTest.kt`
- Modify: `app/src/main/java/dev/junta/firmamobile/ui/BrowserScreen.kt`

**Interfaces:**
- Consumes: `BrowserOwnedResourceLease<WebView, WebMessageBridgeAttachment>`.
- Produces: exact owner-bound bridge installation/release and unchanged current-attachment abandonment/close behavior.

- [ ] **Step 1: Write the failing integration regression**

Require `BrowserScreen.kt` to instantiate the lease, bind with `(webView, attachment)`, release with the exact `webView` inside `AndroidView.onRelease`, and contain no raw `AtomicReference<WebMessageBridgeAttachment?>` or `bridgeRef.set(attachment)` lifecycle holder.

- [ ] **Step 2: Run RED**

Run:

```bash
./gradlew --no-daemon --rerun-tasks testDebugUnitTest \
  --tests dev.junta.firmamobile.browser.BrowserSecurityRegressionTest
```

Expected: FAIL because BrowserScreen still uses the unowned bridge reference and does not close it from `onRelease`.

- [ ] **Step 3: Implement the minimum integration**

Replace the bridge reference with the lease. Route current attachment lookup,
current close, attachment bind and exact `onRelease` through the lease. Release the
bridge before `stopLoading()` and `destroy()`.

- [ ] **Step 4: Run focused GREEN**

Run the lease and BrowserSecurityRegression tests in Debug, then the same selected
tests in Debug and QA. Require all selected tests to pass.

### Task 3: Full verification, evidence and remote integration

**Files:**
- Update after verification: `docs/autonomous/2026-08-04-audit-ledger.md`
- Update after verification: `docs/security-roadmap.md`
- Update after verification: `docs/test-report.md`
- Update after verification: `docs/handoffs/NEXT_CHAT_HANDOFF.md`

**Interfaces:**
- Consumes: final production/test diff from Tasks 1-2.
- Produces: fresh gate evidence and one pushed atomic milestone commit.

- [ ] **Step 1: Run full gates**

Run pin/AAPT2 checks, full Debug and QA JVM suites, Debug/QA/QA-AndroidTest assemblies,
lint, Python suite, Go test/vet/build, Android artifact checks and release-without-
private-signing fail-closed. Remove the generated relay binary and require zero release
APKs.

- [ ] **Step 2: Review exact scope**

Inspect the complete diff, run `git diff --check`, scan changed content for secrets,
PII, raw certificate/signature material, unsafe WebView/TLS patterns and unrelated
changes.

- [ ] **Step 3: Record observed evidence**

Update only the four evidence documents with exact RED/GREEN/full-gate jobs, counts,
artifact hashes, limitations and prohibited-action confirmation.

- [ ] **Step 4: Commit and push**

Stage only milestone files, rerun staged checks, create one atomic commit, push to
`origin/agent/workspace-47-autonomous-20260803`, fetch, and verify exact remote SHA,
divergence `0/0`, clean worktree and immutable canonical SHA.

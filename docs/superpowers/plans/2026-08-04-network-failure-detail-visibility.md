# Network Failure Detail Visibility Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Remove Kotlin exposed-type suppressions by making route-failure detail an internal implementation boundary without changing network behavior.

**Architecture:** Preserve the public failure code/constructor while changing only `ProfileHttpResult.Failure` from a data class with public internal-typed state to an ordinary class with internal detail construction/access. Leave fallback phase semantics and all transport code paths unchanged.

**Tech Stack:** Kotlin 2.3.10, Android/JVM tests, Python CI-policy unittest.

## Global Constraints

- Work only in `/data/data/com.termux/files/home/workspace-47-autonomous-20260803`.
- No retry, fallback, DNS, TLS, tunnel, timeout, origin or signing behavior change.
- No dependency/toolchain changes.
- No APK installation, launch, device control or portal interaction.

---

### Task 1: Close the failure-detail visibility boundary

**Files:**
- Modify: `tools/tests/test_ci_policy.py`
- Modify: `app/src/main/java/dev/junta/firmamobile/network/ProfileHttpTransport.kt`
- Update after GREEN: `docs/autonomous/2026-08-04-audit-ledger.md`
- Update after GREEN: `docs/security-roadmap.md`
- Update after GREEN: `docs/test-report.md`
- Update after GREEN: `docs/handoffs/NEXT_CHAT_HANDOFF.md`

**Interfaces:**
- Preserves: `ProfileHttpResult.Failure(ProfileHttpFailure)`.
- Preserves: `ProfileHttpResult.Failure.code: ProfileHttpFailure`.
- Internalizes: `ProfileHttpResult.Failure.detail: ProfileHttpFailureDetail` and its primary constructor.

- [ ] **Step 1: Write the failing source/API policy test**

Require `ProfileHttpResult.Failure` to be an ordinary class with an internal
primary constructor/internal detail, preserve the public code constructor/property,
and reject `EXPOSED_PARAMETER_TYPE`, `EXPOSED_PROPERTY_TYPE`, and `data class
Failure`.

- [ ] **Step 2: Observe RED**

Run the focused Python test and confirm failure on the current suppressed data
class shape.

- [ ] **Step 3: Implement the minimal visibility fix**

Remove the suppression; replace only `data class Failure(val detail: ...)` with
`class Failure internal constructor(internal val detail: ...)`. Preserve its body
and all routing/call sites.

- [ ] **Step 4: Focused GREEN**

Run the policy test, compile Debug/QA, then focused network transport/direct-first
and tri-phase execution tests.

- [ ] **Step 5: Full verification**

Run complete Debug/QA JVM, lint/build/APK, full Python, Go test/vet/build, Android
artifact verification and release fail-closed verification.

- [ ] **Step 6: Review and evidence docs**

Inspect complete diff and warning output, run `git diff --check`, sensitive-pattern
and unsafe-network scans, then record evidence in the ledger/roadmap/test report
and durable handoff.

- [ ] **Step 7: Commit, push and verify**

Create one atomic commit, push the autonomous branch, fetch and verify exact remote
SHA and zero divergence.

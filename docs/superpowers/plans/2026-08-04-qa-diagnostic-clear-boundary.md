# QA Diagnostic Journal Clear Boundary Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Make `SanitizedLogger.clear()` logically clear both its in-memory journal and the QA app-private persisted diagnostic journal without changing release or portal behavior.

**Architecture:** Extend the existing `SanitizedLogSink` lifecycle with a default no-op `clear()`. `SanitizedLogger` delegates clear best-effort to its sink; the QA composite propagates clear to `QaDiagnosticFileSink`, which truncates the app-private journal. Existing lambda sinks stay compatible because `emit()` remains the only abstract method.

**Tech Stack:** Kotlin/JVM, Android app-private files, JUnit 4, Gradle Android unit tests.

## Global Constraints

- Work only in `/data/data/com.termux/files/home/workspace-47-autonomous-20260803` on `agent/workspace-47-autonomous-20260803`.
- Do not change portal/profile, WebView, network, TLS, certificate, signing, or release eligibility behavior.
- Do not broaden logging fields or persist new data.
- `clear()` is logical journal clearing; do not claim physical secure erasure of flash storage or system Logcat.
- Diagnostic sink failures remain best-effort and must not fail application security flows.
- Follow RED -> minimal GREEN -> focused/full verification -> diff/security review -> atomic commit/push.

---

### Task 1: Clear the QA persisted diagnostic journal through the existing sink boundary

**Files:**
- Modify: `app/src/test/java/dev/junta/firmamobile/security/ApplicationSanitizedLoggerFactoryTest.kt`
- Modify: `app/src/main/java/dev/junta/firmamobile/security/SanitizedLogger.kt`
- Modify: `app/src/main/java/dev/junta/firmamobile/security/QaDiagnosticFileSink.kt`
- Modify: `app/src/main/java/dev/junta/firmamobile/security/ApplicationSanitizedLoggerFactory.kt`
- Update after evidence changes: `docs/autonomous/2026-08-04-audit-ledger.md`
- Update after evidence changes: `docs/security-roadmap.md`
- Update after evidence changes: `docs/test-report.md`
- Update after evidence changes: `docs/handoffs/NEXT_CHAT_HANDOFF.md`

**Interfaces:**
- Consumes: `SanitizedLogSink.emit(record: String)`, `SanitizedLogger.clear()`, `QaDiagnosticFileSink(FILE_NAME)`.
- Produces: `SanitizedLogSink.clear(): Unit` with a default no-op; `QaDiagnosticFileSink.clear(): Unit`; QA composite clear propagation.

- [ ] **Step 1: Write the failing integration test**

Add to `ApplicationSanitizedLoggerFactoryTest`:

```kotlin
@Test
fun qaClearRemovesInMemoryAndPersistedJournal() {
    val directory = Files.createTempDirectory("jfm-qa-logger-clear").toFile()
    val logger = ApplicationSanitizedLoggerFactory.create(
        filesDirectory = directory,
        qaEnabled = true,
        diagnosticMirror = SanitizedLogSink {},
    )
    val file = directory.resolve(QaDiagnosticFileSink.FILE_NAME)

    logger.recordBrowserEvent(
        DiagnosticEventCode.NETWORK_ERROR,
        "ws072.juntadeandalucia.es",
    )
    assertTrue(logger.exportText().contains("event=NETWORK_ERROR"))
    assertTrue(file.readText().contains("event=NETWORK_ERROR"))

    logger.clear()

    assertTrue(logger.exportText().isEmpty())
    assertTrue(file.isFile)
    assertTrue(file.readText().isEmpty())
}
```

- [ ] **Step 2: Run RED and record the expected failure**

Run:

```bash
./gradlew :app:testDebugUnitTest \
  --tests 'dev.junta.firmamobile.security.ApplicationSanitizedLoggerFactoryTest.qaClearRemovesInMemoryAndPersistedJournal' \
  --no-daemon
```

Expected: FAIL on the final persisted-file emptiness assertion because current
`SanitizedLogger.clear()` clears only the in-memory deque.

- [ ] **Step 3: Implement the minimum sink lifecycle extension**

In `SanitizedLogger.kt` keep the SAM shape and add a default method:

```kotlin
fun interface SanitizedLogSink {
    fun emit(record: String)

    fun clear() = Unit
}
```

Change logger clear to:

```kotlin
@Synchronized
fun clear() {
    records.clear()
    runCatching { sink.clear() }
}
```

In `QaDiagnosticFileSink.kt` add:

```kotlin
@Synchronized
override fun clear() {
    file.parentFile?.mkdirs()
    file.outputStream().use { }
}
```

In `ApplicationSanitizedLoggerFactory.kt`, use an object for the QA composite:

```kotlin
object : SanitizedLogSink {
    override fun emit(record: String) {
        runCatching { fileSink.emit(record) }
        runCatching { diagnosticMirror.emit(record) }
    }

    override fun clear() {
        runCatching { fileSink.clear() }
        runCatching { diagnosticMirror.clear() }
    }
}
```

Keep the non-QA sink as `SanitizedLogSink {}`.

- [ ] **Step 4: Run focused GREEN in Debug and QA**

Run:

```bash
./gradlew \
  :app:testDebugUnitTest \
  --tests 'dev.junta.firmamobile.security.ApplicationSanitizedLoggerFactoryTest' \
  :app:testQaUnitTest \
  --tests 'dev.junta.firmamobile.security.ApplicationSanitizedLoggerFactoryTest' \
  --no-daemon
```

Expected: PASS.

- [ ] **Step 5: Run full required gates**

Run the repository's current local equivalents of CI:

```bash
./gradlew verifyResolvedCoreVersion verifyPortableAapt2Configuration --no-daemon
./gradlew testDebugUnitTest testQaUnitTest --no-daemon
./gradlew lintDebug lintQa --no-daemon
./gradlew assembleDebug assembleQa assembleQaAndroidTest --no-daemon
scripts/ci/verify-android-artifacts.sh
scripts/ci/verify-release-fail-closed.sh
python -m unittest discover -s tools/tests -p 'test_*.py' -v
(
  cd ws024-relay
  go test ./... -count=1
  go vet ./...
  go build ./cmd/ws024-relay
  rm -f ws024-relay
)
```

Expected: all locally available gates PASS; record environmental skips separately.
Do not claim `go test -race` on unsupported Termux.

- [ ] **Step 6: Review and record evidence**

Run:

```bash
git diff --check
git diff --name-status
git diff
```

Verify no secrets, raw certificate/signature/password material, unsafe WebView/TLS
patterns, unrelated runtime changes, or generated relay binary are present. Update
only evidence documents whose claims changed.

- [ ] **Step 7: Commit, push, and verify exact remote SHA**

After a final `git fetch --prune origin` and divergence check, stage only the planned
files and commit atomically, for example:

```bash
git commit -m "security(logging): clear qa diagnostic journal"
git push origin HEAD:agent/workspace-47-autonomous-20260803
git fetch --prune origin
```

Require local HEAD, upstream, and
`origin/agent/workspace-47-autonomous-20260803` to be identical with divergence
`0/0` before marking the milestone complete.

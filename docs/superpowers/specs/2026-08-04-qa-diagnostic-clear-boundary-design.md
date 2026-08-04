# QA diagnostic journal clear boundary design

## Context

`SanitizedLogger` owns a bounded in-memory diagnostic journal. In QA,
`ApplicationSanitizedLoggerFactory` also mirrors the same sanitized records into
`filesDir/qa-navigation.log` through `QaDiagnosticFileSink`. The authoritative
`docs/test-plan.md` contract says `clear elimina el journal`.

The current `SanitizedLogger.clear()` removes only the in-memory deque. During the
same process, the QA file still contains the pre-clear records. There are currently
no production call sites for `sanitizedLogger.clear()`, so this is a dormant
privacy/API-contract defect rather than evidence of an observed user-data leak.

## Scope

The milestone makes one semantic change: clearing the application logger clears all
logger-owned journal layers that the application can control synchronously.

It does not change:

- which events are collected;
- sanitization, allowlists, hashes, capacities, or file location;
- QA/release build eligibility or portal behavior;
- Logcat retention, which is outside the logger-owned app-private journal;
- certificate, signing, network, WebView, or persistence policy elsewhere.

## Considered approaches

### 1. Add a separate `onClear` constructor callback

This is minimal mechanically, but it adds a second lifecycle channel beside
`SanitizedLogSink` and expands `SanitizedLogger` construction with an unrelated
callback. Emit and clear responsibility can then drift.

### 2. Introduce a new two-method journal/storage abstraction

This gives the strongest type distinction, but it duplicates the existing sink
boundary and forces unnecessary adapter churn for a one-method lifecycle extension.

### 3. Extend `SanitizedLogSink` with a default `clear()` method — selected

`SanitizedLogSink` remains a Kotlin `fun interface`: `emit(record)` is still its sole
abstract method, while `clear()` has a no-op default. Existing lambda/SAM call sites
remain source-compatible. Stateful sinks can override `clear()`.

This keeps emit/clear ownership on one interface and makes composition explicit.

## Detailed design

### `SanitizedLogSink`

Add:

```kotlin
fun clear() = Unit
```

`emit(record)` remains the only abstract method.

### `SanitizedLogger.clear()`

While holding the existing logger monitor:

1. clear the in-memory deque;
2. invoke `sink.clear()` as best-effort using `runCatching`.

Diagnostics must never make a security-sensitive application flow fail. This matches
the existing best-effort `sink.emit()` behavior. A sink I/O failure therefore does
not restore or retain the in-memory journal and does not throw through the logger API.

### `QaDiagnosticFileSink.clear()`

Override `clear()` with the same synchronization boundary as `emit()` and truncate
`qa-navigation.log` to zero bytes. Keep the file in the app-private `filesDir`; do
not claim physical secure erasure on flash storage. The operation is logical journal
clearing only.

### `ApplicationSanitizedLoggerFactory`

Replace the QA lambda composite with an object implementing `SanitizedLogSink`:

- `emit()` keeps best-effort fan-out to the file sink and diagnostic mirror;
- `clear()` best-effort clears the file sink and invokes the mirror's clear hook.

The current application mirror is a Logcat lambda, whose default `clear()` is a
no-op. The app does not claim to erase system Logcat history.

Non-QA mode remains a no-op sink, so release persistence behavior is unchanged.

## Concurrency and failure behavior

`SanitizedLogger` methods are synchronized. `QaDiagnosticFileSink.emit()` and
`clear()` are synchronized on the sink. The logger invokes the sink while holding its
existing monitor, exactly as it already invokes `emit()`, so clear cannot interleave
with a logger append from another thread.

No callback acquires the logger monitor, so this adds no lock cycle. File exceptions
remain contained as diagnostic failures.

## Test strategy

Add one integration regression to `ApplicationSanitizedLoggerFactoryTest`:

1. create a QA logger in a temporary directory;
2. record a safe event and prove it exists in memory and `qa-navigation.log`;
3. call `logger.clear()`;
4. assert the in-memory export is empty;
5. assert the QA file remains app-owned but has zero journal content.

On the current implementation this must fail at step 5, establishing RED. After the
minimal fix it must pass in both Debug and QA unit variants.

Then run the full relevant Android, Python, Go, artifact, release-fail-closed, diff,
and sensitive-content gates required by the autonomous master plan.

## Exact files

Production:

- `app/src/main/java/dev/junta/firmamobile/security/SanitizedLogger.kt`
- `app/src/main/java/dev/junta/firmamobile/security/QaDiagnosticFileSink.kt`
- `app/src/main/java/dev/junta/firmamobile/security/ApplicationSanitizedLoggerFactory.kt`

Test:

- `app/src/test/java/dev/junta/firmamobile/security/ApplicationSanitizedLoggerFactoryTest.kt`

Evidence after verification:

- `docs/autonomous/2026-08-04-audit-ledger.md`
- `docs/security-roadmap.md`
- `docs/test-report.md`
- `docs/handoffs/NEXT_CHAT_HANDOFF.md`

## Acceptance criteria

- Current QA integration test reproduces a RED because file content survives
  `logger.clear()`.
- Final QA logger clear leaves both the in-memory journal and app-private QA journal
  logically empty.
- Existing lambda `SanitizedLogSink` call sites continue to compile.
- Non-QA mode still creates no QA diagnostic file.
- No event schema, portal behavior, network/TLS/signing/certificate behavior, or
  release persistence policy changes.
- Full required automated gates pass before commit/push.

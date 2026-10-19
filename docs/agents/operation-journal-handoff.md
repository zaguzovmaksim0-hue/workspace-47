# Operation journal foundation (#374)

Base: `04d67ba69cf8f722be151a2716542f26124ebf72`.
Scope: new `history/` production/test files only; no existing production call sites changed.

## Contract and limits

- `InMemoryOperationJournal` is **ephemeral, process-local, not persisted**. End-to-end operation history is still TODO: no runtime/coordinator wiring or UI is included.
- Each immutable entry contains only a positive local `Long` sequence, an `Instant`, `OperationKind` and `LocalOutcome`. Append accepts only the last three; it allocates the sequence itself. No free-text/document/name/content/digest/certificate/alias/subject/origin/URL/request-ID/error/exception input exists.
- Kinds: `SIGN`, `COSIGN`, `COUNTERSIGN`, `BATCH`, `VERIFY`, `IDENTIFY`. Local outcomes: `COMPLETED`, `CANCELLED`, `FAILED`; these do not assert remote delivery/acceptance or signature validity. Event-to-enum mapping remains an integration task.
- Capacity is `1..64`, default `32`; append evicts only the oldest retained entry. Ordering follows append sequence, never timestamps. Equal, backward, pre-epoch and full-range `Instant` values are supported without epoch-millisecond arithmetic.
- Append/snapshot/clear share the instance monitor. Snapshots are detached unmodifiable copies, bounded to 64 entries. `clear()` forgets entries **and resets the sequence**; already-issued snapshots/export bytes cannot be revoked. This is not a security or replay ledger.
- Sequence exhaustion fails before eviction/mutation, never wraps. The internal initial-sequence constructor is a synthetic boundary-test seam; normal construction starts at zero.
- `OperationJournalJson.toUtf8(snapshot)` returns fresh bytes only, at most **16,384 bytes**. Schema 1 has fixed key/entry order, enum spellings, ISO-8601 UTC Instants, UTF-8 without BOM and one trailing newline. It exports every retained entry, with no truncation, generated-at timestamp or ambient locale/time-zone dependency. Schema changes require a version change.
- No database, filesystem, preferences, telemetry, clipboard, network, automatic export or new dependency was introduced. Metadata itself is still potentially sensitive; future UI/export delivery needs an explicit user-controlled privacy/lifecycle design.

## Actual focused checks — 2026-10-19

- RED: the new tests first failed to compile because the history API did not yet exist.
- GREEN: cached Kotlin compiler **2.2.21**, `-no-stdlib -no-reflect -Werror -jvm-target 17 -Xjdk-release=17`; only the four new production and two new test files were compiled. Cached JUnit **4.13.2** and Hamcrest **1.3**: **20 tests passed** (`InMemoryOperationJournalTest`, `OperationJournalJsonTest`).
- Tests cover capacity bounds/eviction/order/clear, immutable defensive snapshots, sequence exhaustion, negative/extreme time, simultaneous writers/readers/clear, exact/stable UTF-8 JSON, all enum combinations, export size, locale/time-zone independence and reflected allowlists of entry fields/append input types.
- JVM execution used Termux OpenJDK **21.0.12**, not a Java 17 runtime; the compilation target and JDK API surface were 17. The compiler emitted a non-fatal Jansi native-library diagnostic on Android; compilation and JUnit both exited successfully.
- Scoped production detekt **1.23.8** passed with `--input app/src/main/java/dev/junta/firmamobile/history --build-upon-default-config --baseline config/detekt/baseline.xml --max-issues 0`. Cached JAR SHA-256 verified: `2ce2ff952e150baf28a29cda70a363b0340b3e81a55f43e51ec5edffc3d066c1`. Baseline/config/suppressions were not changed.
- Reproduction runner and logs are outside the worktree at `~/.cache/w47-operation-journal-checks-20261019/` (`run-jvm.sh`, `red.log`, `green.log`, `detekt.log`, `detekt.xml`); they are not application persistence or committed files.

No Gradle/bootstrap/download/device/E2E/APK/portal/real-certificate/key checks were run. Broad integration/CI and any publication belong to the parent session. This foundation does not complete or close #374.

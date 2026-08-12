# DNS Executor Test Isolation Design

## Scope

`HttpsProfileHttpTransport` uses one process-wide bounded DNS executor with two workers, a `SynchronousQueue`, daemon threads, and `AbortPolicy`. That production policy is intentional: overload must fail closed instead of growing a queue or worker pool.

The original symptom appeared when the saturation unit test ran before the representative non-global-address test. The first diagnosis was correct but incomplete: caller/Future completion can precede a `ThreadPoolExecutor` worker returning to the synchronous handoff queue. Isolating only the saturation test removed one shared-state source, but a later fresh QA run proved that rapid ordinary DNS submissions inside the classification loop could hit the same process-wide transition and return `NETWORK_ERROR` instead of reaching `PRIVATE_ADDRESS`.

The defect is therefore test ownership, not the public-address classifier and not a portal regression. JVM tests must not share the process executor whose rejection behavior they are not testing.

## Chosen design

Add an internal constructor dependency to `HttpsProfileHttpTransport`:

```kotlin
private val dnsExecutor: ExecutorService = DNS_EXECUTOR
```

`resolveWithDeadline` submits through that dependency. Runtime callers continue to use the existing process-wide `DNS_EXECUTOR` unchanged.

Test execution is split by purpose:

1. Synchronous resolver tests use `DirectTestExecutorService`, which executes submitted `FutureTask` work inline and owns no thread.
2. DNS timeout and cancellation subcases each own a separate bounded `ThreadPoolExecutor`, then call `shutdown()` and require bounded `awaitTermination()` before continuing.
3. The saturation test owns a dedicated executor with the exact production bounds and rejection policy, releases its synthetic resolver tasks, joins callers, and terminates the executor before returning.
4. Every JVM-test construction of `HttpsProfileHttpTransport` supplies an explicit test-owned DNS executor. The process-wide runtime executor is not used as incidental test infrastructure.

A focused regression test proves that DNS resolution is submitted through the injected executor. An explicit source audit covers all 18 JVM-test transport constructions.

## Security and behavior invariants

- Production remains limited to two concurrent DNS tasks.
- Production retains 0 core workers, 2 maximum workers, 30-second keep-alive, `SynchronousQueue`, daemon threads, `AbortPolicy`, and core-thread timeout.
- No production queue, retry, sleep, fallback, or executor replacement is added.
- `RejectedExecutionException` still maps to fail-closed `NETWORK_ERROR`.
- DNS timeout, cancellation, URL policy, public-address filtering, peer pinning, and HTTP execution are unchanged.
- The dependency remains internal and is not exposed as public API.
- No portal access, authentication, certificate operation, signing, device installation, or physical instrumentation is performed.

## Alternatives rejected

1. **Add a fixed sleep.** Timing-based and device-load dependent.
2. **Poll or reflect the private shared executor.** Couples tests to private state and still permits cross-test mutation.
3. **Order JUnit methods.** Hides shared-state leakage rather than removing it.
4. **Change production to queue or retry submissions.** Alters the reviewed fail-closed policy and is outside this task.
5. **Use one asynchronous test executor for all subcases.** Reintroduces the same handoff transition between otherwise independent assertions.

## Testing

- RED 1: the focused injected-executor test failed to compile because the constructor seam did not exist.
- GREEN 1: the test passed after the minimal dependency and submit indirection.
- RED 2: a later fresh combined focused run failed in QA at `2001:10::1`, expected `PRIVATE_ADDRESS` but received `NETWORK_ERROR`; this disproved the saturation-only design.
- GREEN 2: all synchronous unit transports moved to the direct test executor, timeout/cancel paths received separate owned pools, and all 18 JVM-test transport constructions became explicit.
- Final focused evidence: the exact combined Debug/QA command passed, followed by five additional sequential runs per variant.
- Complete Debug and QA suites, lint, builds, artifact checks, release fail-closed, Python, and Go gates run before the local commit.

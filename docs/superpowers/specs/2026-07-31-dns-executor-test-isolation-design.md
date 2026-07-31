# DNS Executor Test Isolation Design

## Scope

The Android `HttpsProfileHttpTransport` uses one process-wide bounded DNS executor with two workers, a `SynchronousQueue`, and `AbortPolicy`. This is intentional production behavior: overload must fail closed instead of growing an unbounded queue or worker pool.

The saturation unit test currently exercises that shared executor. Releasing the two synthetic resolver tasks and joining their caller threads proves that each `Future` completed, but it does not prove that both `ThreadPoolExecutor` workers have already returned to the handoff queue. A following test can therefore observe a short-lived `RejectedExecutionException` and report `NETWORK_ERROR` instead of reaching its own DNS classification assertion.

This task removes the test-order dependency without changing production limits, rejection behavior, DNS policy, timeouts, or failure mapping.

## Chosen design

Add an internal constructor dependency to `HttpsProfileHttpTransport`:

```kotlin
private val dnsExecutor: ExecutorService = DNS_EXECUTOR
```

`resolveWithDeadline` submits to this dependency. Runtime callers continue to use the existing process-wide `DNS_EXECUTOR` unchanged.

The saturation test creates a dedicated `ThreadPoolExecutor` with the same bounded configuration as production, passes it to all transports participating in that test, and owns its lifecycle. Teardown follows this order:

1. release both synthetic resolver tasks;
2. join the caller threads and verify they stopped;
3. call `shutdown()` on the dedicated executor;
4. require `awaitTermination` to succeed within a bounded deadline;
5. cancel request cancellation objects as final cleanup.

A focused regression test proves that DNS resolution is submitted to the injected executor. This establishes that the test-scoped executor is real behavior, not an unused seam.

## Security and behavior invariants

- Production remains limited to two concurrent DNS tasks.
- Production retains `SynchronousQueue` and `AbortPolicy`; no queue, retry, sleep, or fallback is added.
- `RejectedExecutionException` still maps to fail-closed `NETWORK_ERROR`.
- DNS timeout, cancellation, public-address filtering, peer pinning, and HTTP execution are unchanged.
- The new dependency is `internal` through the existing internal constructor and is not exposed as a public API.
- No portal access, authentication, certificate operation, signing, or device installation is performed.

## Alternatives rejected

1. **Add a fixed sleep after the saturation test.** Timing-based and device-load dependent.
2. **Poll or reflect the private shared executor.** Couples tests to a private field and still lets one test mutate process-wide state.
3. **Change production to queue or retry DNS submissions.** Alters the approved fail-closed saturation policy and is outside this task.
4. **Order the JUnit methods.** Hides shared-state leakage rather than removing it.

## Testing

- RED: add a test requiring a supplied `ExecutorService` to receive the DNS submission; it fails before the constructor seam exists.
- GREEN: inject the executor and verify the focused test passes.
- Refactor the saturation test to use its own bounded executor and deterministic `shutdown/awaitTermination` teardown.
- Repeatedly run `ProfileHttpTransportTest` in one Gradle invocation strategy and run complete Debug and QA unit suites.
- Run lint/build/artifact and repository checks required by the current branch before the local commit.

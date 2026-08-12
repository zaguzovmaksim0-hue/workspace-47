# DNS Executor Test Isolation Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [x]`) syntax for tracking.

**Goal:** Remove every JVM-test dependency on process-wide DNS executor handoff timing while preserving the exact production fail-closed executor policy.

**Architecture:** `HttpsProfileHttpTransport` receives an internal `ExecutorService` dependency whose runtime default remains the existing process-wide bounded executor. Synchronous JVM tests use an inline test executor; timeout, cancellation and saturation tests own bounded executors and terminate them. Every JVM-test transport construction supplies an explicit executor.

**Tech Stack:** Kotlin 2.x, Java 17 concurrency (`ExecutorService`, `ThreadPoolExecutor`, `SynchronousQueue`), JUnit 4, Android Gradle Plugin/Gradle 9.4.1.

## Global Constraints

- Keep production maximum DNS workers at exactly `2`.
- Keep `SynchronousQueue`, `AbortPolicy`, 30-second keep-alive, daemon worker threads, and core-thread timeout.
- Keep rejected DNS submission mapped to `ProfileHttpFailure.NETWORK_ERROR`.
- Do not add production retries, sleeps, queues, fallback DNS, or policy relaxation.
- Do not perform portal access, authentication, signing, certificate operations, or device installation.
- Do not push.

---

### Task 1: Prove the missing executor seam with a failing test

**Files:**
- Modify: `app/src/test/java/dev/junta/firmamobile/network/ProfileHttpTransportTest.kt`

**Interfaces:**
- Consumes: `HttpsProfileHttpTransport.post` and the existing internal constructor.
- Produces: regression expectation that a supplied `ExecutorService` performs DNS work.

- [x] **Step 1: Add a test-owned executor that records submissions**

Add imports for `AbstractExecutorService`, `ExecutorService`, `RejectedExecutionException`, and `AtomicInteger`. Add a small test executor:

```kotlin
private class RecordingExecutorService : AbstractExecutorService() {
    val submissions = AtomicInteger(0)
    private val shutdown = AtomicBoolean(false)

    override fun execute(command: Runnable) {
        if (shutdown.get()) throw RejectedExecutionException("shutdown")
        submissions.incrementAndGet()
        command.run()
    }

    override fun shutdown() { shutdown.set(true) }
    override fun shutdownNow(): MutableList<Runnable> { shutdown.set(true); return mutableListOf() }
    override fun isShutdown(): Boolean = shutdown.get()
    override fun isTerminated(): Boolean = shutdown.get()
    override fun awaitTermination(timeout: Long, unit: TimeUnit): Boolean = shutdown.get()
}
```

- [x] **Step 2: Add the failing behavioral test**

Add:

```kotlin
@Test
fun dnsResolutionUsesTheInjectedExecutor() {
    val dnsExecutor = RecordingExecutorService()
    val transport = HttpsProfileHttpTransport(
        dnsResolver = DnsResolver { listOf(InetAddress.getByName("127.0.0.1")) },
        executor = ProfileHttpExecutor { _, _, _, _, _, _, _, _ -> error("HTTP must not start") },
        dnsExecutor = dnsExecutor,
    )

    assertEquals(ProfileHttpFailure.PRIVATE_ADDRESS, (post(transport) as ProfileHttpResult.Failure).code)
    assertEquals(1, dnsExecutor.submissions.get())
}
```

- [x] **Step 3: Run the focused test and verify RED**

Run:

```bash
./gradlew :app:testDebugUnitTest \
  --tests 'dev.junta.firmamobile.network.ProfileHttpTransportTest.dnsResolutionUsesTheInjectedExecutor' \
  --rerun-tasks --no-build-cache --console=plain
```

Expected: Kotlin test compilation fails because `dnsExecutor` is not yet a constructor parameter. This is the missing seam identified by the design; no production code has changed.

### Task 2: Add the minimal executor dependency

**Files:**
- Modify: `app/src/main/java/dev/junta/firmamobile/network/ProfileHttpTransport.kt`

**Interfaces:**
- Produces: internal constructor parameter `dnsExecutor: ExecutorService = DNS_EXECUTOR`.

- [x] **Step 1: Import and inject `ExecutorService`**

Add:

```kotlin
import java.util.concurrent.ExecutorService
```

Extend the internal constructor:

```kotlin
private val dnsTimeoutMillis: Long = DNS_TIMEOUT_MILLIS,
private val dnsExecutor: ExecutorService = DNS_EXECUTOR,
```

- [x] **Step 2: Submit DNS work through the dependency**

Change only:

```kotlin
DNS_EXECUTOR.submit(Callable { dnsResolver.resolve(host) })
```

to:

```kotlin
dnsExecutor.submit(Callable { dnsResolver.resolve(host) })
```

- [x] **Step 3: Run the focused test and verify GREEN**

Run the Task 1 focused Gradle command. Expected: PASS with one recorded submission and `PRIVATE_ADDRESS`.

### Task 3: Isolate and deterministically terminate the saturation test executor

**Files:**
- Modify: `app/src/test/java/dev/junta/firmamobile/network/ProfileHttpTransportTest.kt`

**Interfaces:**
- Consumes: constructor parameter `dnsExecutor: ExecutorService`.
- Produces: saturation coverage with no process-wide executor mutation.

- [x] **Step 1: Add a test factory matching production bounds**

Add imports for `SynchronousQueue` and `ThreadPoolExecutor`, then add:

```kotlin
private fun newBoundedDnsExecutor(): ThreadPoolExecutor = ThreadPoolExecutor(
    0,
    2,
    30,
    TimeUnit.SECONDS,
    SynchronousQueue(),
    { task -> Thread(task, "profile-dns-test").apply { isDaemon = true } },
    ThreadPoolExecutor.AbortPolicy(),
).apply { allowCoreThreadTimeOut(true) }
```

- [x] **Step 2: Pass one dedicated executor to every transport in the saturation test**

Create `val dnsExecutor = newBoundedDnsExecutor()` before the worker transports and pass `dnsExecutor = dnsExecutor` to both worker transports and the third saturated transport.

- [x] **Step 3: Make teardown condition-based and bounded**

In `finally`, perform:

```kotlin
release.countDown()
workers.forEach { it.join(1_000) }
dnsExecutor.shutdown()
assertTrue(dnsExecutor.awaitTermination(1, TimeUnit.SECONDS))
cancellations.forEach(ProfileHttpCancellation::cancel)
```

Keep `assertTrue(workers.none(Thread::isAlive))`. Do not add sleeps or touch the production shared executor.

- [x] **Step 4: Run the complete focused class**

```bash
./gradlew :app:testDebugUnitTest \
  --tests 'dev.junta.firmamobile.network.ProfileHttpTransportTest' \
  --rerun-tasks --no-build-cache --console=plain
```

Expected: all `ProfileHttpTransportTest` methods pass.

### Task 4: Verify order independence and branch regressions

**Files:**
- Modify only if a failure is directly caused by this task.

- [x] **Step 1: Run repeated focused Debug and QA test tasks**

Run the focused class three times per variant in one shell loop, without parallel duplicate Gradle jobs. Expected: six clean passes.

- [x] **Step 2: Run complete Debug and QA JVM suites**

```bash
./gradlew :app:testDebugUnitTest :app:testQaUnitTest --console=plain
```

Expected: both suites pass with the current branch test counts.

- [x] **Step 3: Run static/build gates**

```bash
./gradlew :app:lintDebug :app:lintQa :app:assembleDebug :app:assembleQa :app:assembleQaAndroidTest --console=plain
```

Run the repository's APK artifact, release fail-closed, Python, and Go checks documented in `docs/test-report.md` when their scripts are available.

### Task 4A: Correct the incomplete saturation-only diagnosis

**Files:**
- Add: `app/src/test/java/dev/junta/firmamobile/network/DirectTestExecutorService.kt`
- Modify: `app/src/test/java/dev/junta/firmamobile/network/ProfileHttpTransportTest.kt`
- Modify: `app/src/test/java/dev/junta/firmamobile/network/SecureTunnelExternalHarnessTest.kt`
- Modify: `app/src/test/java/dev/junta/firmamobile/network/SecureTunnelSocketFactoryTest.kt`

- [x] **Step 1: Treat the final fresh QA failure as a second RED**

The narrow saturation-only patch passed its initial repeats, but a later fresh
combined run failed `everyRepresentativeNonGlobalDnsRangeIsRejectedBeforeConnect`
at `2001:10::1`: expected `PRIVATE_ADDRESS`, received `NETWORK_ERROR`. This
proved ordinary rapid test submissions could still race the process executor.

- [x] **Step 2: Isolate every synchronous JVM resolver**

Add `DirectTestExecutorService` and pass it explicitly to ordinary transport
unit tests, including the external tunnel harnesses. It owns no worker thread
and executes the submitted `FutureTask` inline.

- [x] **Step 3: Give timeout and cancellation separate owned pools**

Do not share one asynchronous executor across those subcases. Each pool is
terminated with bounded `awaitTermination` before the next subcase begins.

- [x] **Step 4: Audit and repeat the corrected design**

Verify all 18 JVM-test `HttpsProfileHttpTransport` constructions specify
`dnsExecutor`. Run the exact combined focused command successfully, then five
additional sequential runs for Debug and five for QA, followed by full suites.

### Task 5: Document, review, and commit

**Files:**
- Modify: `docs/security-roadmap.md`
- Modify: `docs/test-report.md`
- Modify: `docs/handoffs/NEXT_CHAT_HANDOFF.md`
- Modify: `docs/superpowers/plans/2026-07-31-dns-executor-test-isolation.md`

- [x] **Step 1: Record the root cause and invariant preservation**

Document that caller completion preceded executor worker handoff readiness, that the test now owns a dedicated bounded executor, and that production limits and failure policy did not change.

- [x] **Step 2: Mark every completed plan checkbox**

Mark each checkbox complete only after the corresponding evidence exists.

- [x] **Step 3: Review the final diff**

Run:

```bash
git diff --check
git status --short
git diff --stat
git diff
```

Confirm no sensitive data, generated artifact noise, unrelated code, or production policy relaxation.

- [x] **Step 4: Create one local implementation commit**

```bash
git add app docs
git commit -m "test(network): isolate bounded dns executor"
```

Do not push.


## Completion note — 2026-07-31

The first saturation-only implementation was not accepted after its final fresh
QA run exposed a second handoff race in an ordinary classification loop. The
corrected implementation explicitly isolates all 18 JVM-test transport
constructions, then passed the exact combined focused command, five additional
Debug runs, five additional QA runs, complete 500-test suites, and all branch
gates. The implementation commit is created only after final staged review. No
push or physical portal operation is part of this task.

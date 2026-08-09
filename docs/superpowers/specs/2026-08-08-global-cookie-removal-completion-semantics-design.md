# G32-01 Global cookie-removal completion semantics — Design

## Problem

`SiteDataCleaner.clearAllConfirmed` forwards `CookieManager.removeAllCookies` through
`WebCookieStore.removeAllCookies`. Android's API contract says the asynchronous Boolean
indicates **whether any cookies were removed**, not whether the removal operation succeeded.
The current implementation names the value `cookiesCleared`, skips `flush()` when it is false,
and then requires that Boolean to be true for the overall completion callback.

Therefore the valid state "global WebStorage deletion completed and there were no cookies to
remove" is reported as failure. `BrowserScreen` then keeps `globalClearResult=false` and does not
perform the normal exact-owner reload, even though cookie removal completed and there was no
cookie state left to remove. This is a completion/status semantic defect adjacent to G31; it is
not evidence that `removeAllCookies(false)` left a cookie behind.

## Evidence and invariant

Android `CookieManager.removeAllCookies(ValueCallback<Boolean>)` invokes its callback when the
operation is complete; the Boolean says whether **any cookies were removed**. It is not a success
flag. `CookieManager.flush()` writes currently accessible cookie state to persistent storage and
may perform blocking I/O.

The global clear contract therefore becomes:

1. `WebStorage.deleteAllData()` must complete without throwing.
2. `removeAllCookies` must be invoked and reach its asynchronous callback.
3. If the callback reports `cookiesRemoved=true`, preserve the existing explicit `flush()` and
   require it to complete without throwing.
4. If it reports `cookiesRemoved=false`, treat that as a completed no-op cookie deletion and do
   not call `flush()` merely to manufacture a success signal.
5. Synchronous `removeAllCookies` exceptions, WebStorage exceptions, and a required `flush()`
   exception remain failure.

No current-site cookie deletion, cache erasure, WebView ownership, navigation epoch, Client TLS,
signing, origin/path allowlist, profile/release, dependency, or portal contract changes.

## Test seam

Use the existing public `SiteDataCleaner.clearAllConfirmed` seam and `FakeCookieStore` in
`SiteDataCleanerTest`.

RED case: `removeAllCookies` completes with `false` (zero cookies removed) while WebStorage
clears successfully. Expected overall callback is `true`, exactly one remove-all call, exactly one
WebStorage delete-all call, and zero flush calls. Current production must fail only the callback
expectation.

After GREEN, add/retain negative controls proving a WebStorage exception and a required flush
exception still fail closed.

## Exact production/test files

- `app/src/main/java/dev/junta/firmamobile/browser/SiteDataCleaner.kt`
- `app/src/test/java/dev/junta/firmamobile/browser/SiteDataCleanerTest.kt`

Evidence documents are updated only after focused/full verification.

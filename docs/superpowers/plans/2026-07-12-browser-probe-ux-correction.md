# Browser and Probe UX Correction Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:executing-plans to implement this plan task-by-task. Subagent execution is not authorized for this workspace.

**Goal:** Keep Junta WebView controls above Android system UI, present the current address in a stable one-line toolbar, and scope `FLAG_SECURE` to password input while preserving the current browser, observer, certificate, and signing behavior.

**Architecture:** Add one `BrowserWindowInsetsPolicy` with Compose and Android View adapters, one focused `BrowserAddressBar`, and one scoped `SensitiveWindowProtection` effect. `BrowserScreen` owns address presentation and consumes chrome padding; the debug probe reuses the native inset adapter and keeps URL details outside sanitized observations.

**Tech Stack:** Kotlin 2.3.10, Jetpack Compose BOM 2026.06.00, Material 3, AndroidX Core/Activity/WebKit, Robolectric, AndroidX Test, Gradle Wrapper on Termux/aarch64, Android 16 POCO F6 Pro.

## Global Constraints

- Keep package ID `dev.junta.firmamobile`, minSdk 26, compileSdk/targetSdk 36.
- Do not alter TLS handling, `JuntaOriginPolicy`, WebView hardening, cookies, bridge schema, protocol observer, certificate flow, or signing logic.
- Do not add a runtime dependency, `addJavascriptInterface`, mixed content, universal file access, external-intent expansion, or release WebView debugging.
- Do not log or persist URL query/fragment, cookies, P12 bytes, passwords, keys, or signing payload.
- Do not launch `MainActivity` for routine build gates. Runtime launches are limited to instrumentation and the named browser/probe QA gate.
- Use the real certificate only after the complete signing contour exists; this plan uses no real certificate.
- One implementation commit must contain only this UX correction. Existing observer work is checkpointed first in a separate commit.

---

### Task 0: Checkpoint the existing safe protocol probe without claiming a signing branch

**Files:**

- Modify with already observed safe evidence: `docs/protocol-observations.md`
- Existing Task 1 changes only:
  `app/src/main/java/dev/junta/firmamobile/security/SanitizedLogger.kt`,
  `app/src/main/res/raw/afirma_shim.js`,
  `app/src/debug/AndroidManifest.xml`,
  `app/src/debug/java/dev/junta/firmamobile/browser/ProtocolObservationRecorder.kt`,
  `app/src/debug/java/dev/junta/firmamobile/browser/ProtocolProbeActivity.kt`,
  `app/src/test/java/dev/junta/firmamobile/browser/AfirmaJavascriptShimTest.kt`,
  `app/src/test/java/dev/junta/firmamobile/browser/ProtocolObservationRecorderTest.kt`,
  `app/src/androidTest/java/dev/junta/firmamobile/browser/ProtocolProbeInstrumentedTest.kt`

**Interfaces:**

- Consumes the runtime evidence already captured from the POCO F6 Pro:
  trusted host, `MiniApplet.cargarMiniApplet`, one argument length, and
  `ObservedRuntimeBranch.NONE`.
- Produces a clean Git boundary for the UX correction.
- Does not mark Task 1 complete and does not infer `AFIRMA`, `INTENT`,
  `WEBSOCKET`, or `DIRECT_NETWORK`.

- [ ] **Step 1: Record only the bounded runtime evidence**

Append an observation entry with no raw URL, query, callback, cookie, or
payload. Use this shape:

```markdown
### 2026-07-12 — POCO F6 Pro debug probe

- top-level host: `www.juntadeandalucia.es`
- MiniApplet call: `LOAD`
- argument count: `1`
- argument lengths: `48`
- observed runtime branch: `NONE`
- conclusion: the public page loads MiniApplet, but the signing transport is
  not yet proven; Task 1 remains open.
```

- [ ] **Step 2: Re-run the observer close gate**

Run:

```bash
./gradlew testDebugUnitTest compileDebugAndroidTestKotlin lintDebug --console=plain
```

Expected: 94 or more tests, zero failures/errors/skips, androidTest Kotlin
compiles, lint succeeds.

Run:

```bash
rg -n 'addJavascriptInterface|handler\.proceed\(|HostnameVerifier\s*\{|X509TrustManager|allowUniversalAccessFromFileURLs\s*=\s*true|mixedContentMode\s*=\s*WebSettings\.MIXED_CONTENT_ALWAYS_ALLOW' app/src/main && exit 1 || true
rg -n 'PrivateKey\.encoded|privateKey\.encoded|evaluateJavascript\(' app/src/main app/src/test && exit 1 || true
git diff --check
```

Expected: searches print nothing and diff check succeeds.

- [ ] **Step 3: Commit only the observer checkpoint**

```bash
git add \
  app/src/main/java/dev/junta/firmamobile/security/SanitizedLogger.kt \
  app/src/main/res/raw/afirma_shim.js \
  app/src/debug \
  app/src/test/java/dev/junta/firmamobile/browser/AfirmaJavascriptShimTest.kt \
  app/src/test/java/dev/junta/firmamobile/browser/ProtocolObservationRecorderTest.kt \
  app/src/androidTest/java/dev/junta/firmamobile/browser/ProtocolProbeInstrumentedTest.kt \
  docs/protocol-observations.md
git diff --cached --check
git commit -m "feat: add safe Junta MiniApplet runtime probe"
```

Expected: UX files from later tasks are not staged; the observation explicitly
says the signing branch remains unknown.

---

### Task 1: Write the complete UX RED suite

**Files:**

- Modify: `app/src/test/java/dev/junta/firmamobile/ui/BrowserScreenTest.kt`
- Create: `app/src/test/java/dev/junta/firmamobile/ui/BrowserWindowInsetsTest.kt`
- Create: `app/src/test/java/dev/junta/firmamobile/ui/SensitiveWindowProtectionTest.kt`
- Modify: `app/src/test/java/dev/junta/firmamobile/browser/JuntaWebViewClientTest.kt`
- Modify: `app/src/androidTest/java/dev/junta/firmamobile/AppLaunchTest.kt`
- Modify: `app/src/androidTest/java/dev/junta/firmamobile/CertificateSetupFlowTest.kt`
- Modify: `app/src/androidTest/java/dev/junta/firmamobile/browser/ProtocolProbeInstrumentedTest.kt`

**Interfaces:**

- Tests request these not-yet-existing APIs:

```kotlin
object BrowserWindowInsetsPolicy {
    @Composable fun current(): WindowInsets
    fun install(window: Window, root: View)
}

object BrowserAddressPresentation {
    fun hostOf(url: String): String
}

@Composable
fun BrowserAddressBar(currentUrl: String, onSubmit: (String) -> Unit)

object WindowSecureFlagPolicy {
    fun apply(window: Window, sensitive: Boolean)
}

@Composable
fun SensitiveWindowProtection(enabled: Boolean, updateSecure: (Boolean) -> Unit)
```

- `BrowserNavigationCallbacks` gains a default no-op
  `fun onTopLevelUrlChanged(url: String)`.

- [ ] **Step 1: Add RED address and fixed-toolbar tests**

In `BrowserScreenTest`, render `BrowserLayout` with a long URL and injected
zero insets. Assert normal mode shows only the host, the full URL is absent,
the toolbar is 64 dp, clicking the address exposes the full URL, and the
toolbar stays 64 dp:

```kotlin
private const val LONG_URL =
    "https://www.juntadeandalucia.es/empleoformacionytrabajoautonomo/" +
        "ovorion/auth/signInAutcertjs?redacted=not-logged#fragment"

@Test
fun longAddressUsesOneLineHostAndFullUrlOnlyWhileEditing() {
    rule.setContent {
        JuntaFirmaTheme {
            BrowserLayout(
                currentUrl = LONG_URL,
                certificateOwner = "Persona de Prueba",
                browserInsets = WindowInsets(0, 0, 0, 0),
                onAddressSubmitted = {},
                onBack = {}, onHome = {}, onReload = {},
                onChangeCertificate = {}, onLockCertificate = {},
                onClearSession = {},
            ) { Text("contenido-web", Modifier.testTag(BROWSER_CONTENT_TAG)) }
        }
    }

    rule.onNodeWithText("www.juntadeandalucia.es").assertIsDisplayed()
    rule.onNodeWithText(LONG_URL).assertDoesNotExist()
    rule.onNodeWithTag(BROWSER_TOOLBAR_TAG).assertHeightIsEqualTo(64.dp)
    rule.onNodeWithTag(BROWSER_ADDRESS_LABEL_TAG).performClick()
    rule.onNodeWithText(LONG_URL).assertIsDisplayed()
    rule.onNodeWithTag(BROWSER_TOOLBAR_TAG).assertHeightIsEqualTo(64.dp)
}
```

- [ ] **Step 2: Add RED Compose/native inset tests**

The Compose test injects a 96 px bottom inset and verifies the tagged bottom
chrome includes it while the tagged content ends above it. The native test
dispatches navigation 96 px and IME 320 px twice and asserts bottom padding is
320 px both times, never 416 or 640:

```kotlin
@Test
fun nativePolicyUsesMaxImeAndNavigationInsetWithoutAccumulation() {
    val activity = Robolectric.buildActivity(ComponentActivity::class.java)
        .setup().get()
    val root = FrameLayout(activity)
    activity.setContentView(root)
    BrowserWindowInsetsPolicy.install(activity.window, root)
    val insets = WindowInsetsCompat.Builder()
        .setInsets(WindowInsetsCompat.Type.navigationBars(), Insets.of(0, 0, 0, 96))
        .setInsets(WindowInsetsCompat.Type.ime(), Insets.of(0, 0, 0, 320))
        .build()

    ViewCompat.dispatchApplyWindowInsets(root, insets)
    assertEquals(320, root.paddingBottom)
    ViewCompat.dispatchApplyWindowInsets(root, insets)
    assertEquals(320, root.paddingBottom)
}
```

- [ ] **Step 3: Add RED scoped secure-window tests**

Test the real Window flag mapping and the composable disposal contract:

```kotlin
@Test
fun realWindowFlagCanBeEnabledAndCleared() {
    val activity = Robolectric.buildActivity(ComponentActivity::class.java)
        .setup().get()
    WindowSecureFlagPolicy.apply(activity.window, true)
    assertTrue(activity.window.attributes.flags and FLAG_SECURE != 0)
    WindowSecureFlagPolicy.apply(activity.window, false)
    assertEquals(0, activity.window.attributes.flags and FLAG_SECURE)
}

@Test
fun sensitiveEffectClearsProtectionWhenItLeavesComposition() {
    val events = mutableListOf<Boolean>()
    var visible by mutableStateOf(true)
    rule.setContent {
        if (visible) SensitiveWindowProtection(true) { events += it }
    }
    rule.runOnIdle { visible = false }
    rule.runOnIdle { assertEquals(false, events.last()) }
}
```

Instrumentation assertions add:

```kotlin
scenario.onActivity { activity ->
    assertEquals(0, activity.window.attributes.flags and FLAG_SECURE)
}
```

for the non-sensitive home/browser state, assert non-zero during locked
password input, then zero after entering `Unlocking`/`Unlocked`. The probe test
adopts `android.permission.DUMP`, launches `ProtocolProbeActivity`, and asserts
the flag is zero before closing it.

- [ ] **Step 4: Add RED top-level URL callback test**

Extend `RecordingBrowserCallbacks` and call `onPageStarted`/`onPageFinished`:

```kotlin
@Test
fun topLevelPageLifecycleUpdatesAddressWithoutLoggingTheUrl() {
    val raw = "https://www.juntadeandalucia.es/path?secret-canary=value#fragment"
    client.onPageStarted(webView, raw, null)
    client.onPageFinished(webView, raw)
    assertEquals(listOf("url:$raw", "url:$raw"), callbacks.events)
    assertFalse(logger.exportText().contains("secret-canary"))
}
```

- [ ] **Step 5: Run RED and confirm the expected cause**

```bash
./gradlew testDebugUnitTest \
  --tests '*BrowserScreenTest' \
  --tests '*BrowserWindowInsetsTest' \
  --tests '*SensitiveWindowProtectionTest' \
  --tests '*JuntaWebViewClientTest' \
  --console=plain
```

Expected: compilation fails because the requested address, inset, and scoped
secure-window APIs do not yet exist. Fix test syntax only if needed; do not add
production code until the failure is for the missing behavior.

---

### Task 2: Implement the minimal browser/probe UX correction

**Files:**

- Create: `app/src/main/java/dev/junta/firmamobile/ui/BrowserWindowInsets.kt`
- Create: `app/src/main/java/dev/junta/firmamobile/ui/BrowserAddressBar.kt`
- Create: `app/src/main/java/dev/junta/firmamobile/ui/SensitiveWindowProtection.kt`
- Modify: `app/src/main/java/dev/junta/firmamobile/ui/BrowserScreen.kt`
- Modify: `app/src/main/java/dev/junta/firmamobile/browser/JuntaWebViewClient.kt`
- Modify: `app/src/main/java/dev/junta/firmamobile/MainActivity.kt`
- Modify: `app/src/main/res/values/strings.xml`
- Modify UI only:
  `app/src/debug/java/dev/junta/firmamobile/browser/ProtocolProbeActivity.kt`

**Interfaces:**

- Produces the exact APIs requested by Task 1.
- `ProtocolObservationRecorder`, `SafeProtocolObservation`, bridge names,
  message schema, and branch detection remain byte-for-byte unchanged.

- [ ] **Step 1: Implement the shared inset policy**

Create `BrowserWindowInsets.kt`:

```kotlin
object BrowserWindowInsetsPolicy {
    @Composable
    fun current(): WindowInsets = WindowInsets.safeDrawing.union(WindowInsets.ime)

    fun install(window: Window, root: View) {
        WindowCompat.setDecorFitsSystemWindows(window, false)
        val initial = Insets.of(
            root.paddingLeft, root.paddingTop, root.paddingRight, root.paddingBottom,
        )
        ViewCompat.setOnApplyWindowInsetsListener(root) { view, source ->
            val safe = source.getInsets(
                WindowInsetsCompat.Type.systemBars() or
                    WindowInsetsCompat.Type.displayCutout() or
                    WindowInsetsCompat.Type.ime(),
            )
            view.setPadding(
                initial.left + safe.left,
                initial.top + safe.top,
                initial.right + safe.right,
                initial.bottom + safe.bottom,
            )
            WindowInsetsCompat.CONSUMED
        }
        ViewCompat.requestApplyInsets(root)
    }
}
```

In `BrowserLayout`, pass an injectable
`browserInsets: WindowInsets = BrowserWindowInsetsPolicy.current()`. Set
`Scaffold(contentWindowInsets = WindowInsets(0, 0, 0, 0))`; apply top plus
horizontal insets to the top chrome, bottom plus horizontal insets to the
bottom chrome, and use the union with IME. Apply and consume Scaffold content
padding once.

- [ ] **Step 2: Implement the one-line address component**

Create `BrowserAddressBar.kt` with fixed test tags and height:

```kotlin
internal val BrowserToolbarHeight = 64.dp
internal const val BROWSER_TOOLBAR_TAG = "browser_toolbar"
internal const val BROWSER_ADDRESS_LABEL_TAG = "browser_address_label"
internal const val BROWSER_CONTENT_TAG = "browser_content"

object BrowserAddressPresentation {
    fun hostOf(url: String): String = runCatching {
        val uri = Uri.parse(url)
        require(uri.scheme.equals("https", ignoreCase = true))
        require(uri.encodedUserInfo == null)
        uri.host?.lowercase(Locale.ROOT)?.takeIf { it.isNotBlank() }
            ?: INVALID_ADDRESS
    }.getOrDefault(INVALID_ADDRESS)
}
```

Normal `Text` uses:

```kotlin
maxLines = 1,
softWrap = false,
overflow = TextOverflow.Ellipsis,
```

Edit mode uses `BasicTextField(singleLine = true, maxLines = 1)` and normal
Android text selection. `ImeAction.Go` calls `onSubmit`; Back/cancel restores
host-only display. Do not use `rememberSaveable` for the URL.

Wire `BrowserScreen` with `currentUrl` initialized to
`JuntaOriginPolicy.START_URL`. Address submission first rejects non-HTTPS or
userinfo; then it delegates to the existing `JuntaNavigationPolicy`. Only
`AllowInWebView` calls `webView.loadUrl`; `OpenExternal`, `HandleAfirma`, and
`Block` keep their existing safe outcomes.

- [ ] **Step 3: Send top-level URL lifecycle updates to UI**

Add to `BrowserNavigationCallbacks`:

```kotlin
fun onTopLevelUrlChanged(url: String) = Unit
```

Add to `JuntaWebViewClient`:

```kotlin
override fun onPageStarted(view: WebView, url: String, favicon: Bitmap?) {
    callbacks.onTopLevelUrlChanged(url)
}

override fun onPageFinished(view: WebView, url: String) {
    callbacks.onTopLevelUrlChanged(url)
}
```

`BrowserScreen` stores the callback value in non-saveable Compose state. It
does not call `SanitizedLogger` with the URL.

- [ ] **Step 4: Scope the secure window flag to password input**

Create `SensitiveWindowProtection.kt`:

```kotlin
object WindowSecureFlagPolicy {
    fun apply(window: Window, sensitive: Boolean) {
        if (sensitive) {
            window.addFlags(WindowManager.LayoutParams.FLAG_SECURE)
        } else {
            window.clearFlags(WindowManager.LayoutParams.FLAG_SECURE)
        }
    }
}

@Composable
internal fun SensitiveWindowProtection(
    enabled: Boolean,
    updateSecure: (Boolean) -> Unit,
) {
    DisposableEffect(enabled, updateSecure) {
        updateSecure(enabled)
        onDispose { updateSecure(false) }
    }
}
```

In `MainActivity`, remember a stable lambda bound to its Window, call the
effect with `certificateState.value is CertificateUiState.Locked`, and remove
flag mutation from the existing `LaunchedEffect`. The existing state rule that
closes BrowserScreen when the certificate is not unlocked remains unchanged.

- [ ] **Step 5: Apply the UI-only probe changes**

In `ProtocolProbeActivity`:

- remove `window.addFlags(FLAG_SECURE)` and its import;
- install `BrowserWindowInsetsPolicy` on the root `LinearLayout`;
- add one-line host text plus a `Detalles` button;
- keep the full URL TextView hidden until the user expands details;
- configure that TextView as selectable, single-line, and horizontally
  scrolling;
- update it from a new UI callback in `ProtocolProbeWebViewClient`;
- never pass it to `ProtocolObservationRecorder` or `SanitizedLogger`.

Do not change probe message parsing, observed enums, branch matching, or
document-start shim behavior.

- [ ] **Step 6: Run focused GREEN**

```bash
./gradlew testDebugUnitTest \
  --tests '*BrowserScreenTest' \
  --tests '*BrowserWindowInsetsTest' \
  --tests '*SensitiveWindowProtectionTest' \
  --tests '*JuntaWebViewClientTest' \
  compileDebugAndroidTestKotlin \
  --console=plain
```

Expected: all focused tests pass and all instrumentation sources compile.

---

### Task 3: Full verification, POCO visual gate, and dedicated UX commit

**Files:**

- All files from Tasks 1–2, and no protocol/certificate/signing files.
- Device evidence only under
  `/storage/emulated/0/Codex/Work/junta-firma-mobile/`; do not commit it.

**Interfaces:**

- Consumes the green UX implementation.
- Produces one reviewable UX commit and one real-device screenshot result.

- [ ] **Step 1: Run the complete local close sequence**

```bash
./gradlew testDebugUnitTest compileDebugAndroidTestKotlin lintDebug \
  assembleDebug assembleRelease --console=plain
```

Expected: all tasks succeed, all existing WebView/bridge/certificate tests
remain green, lint succeeds, and both APKs are produced.

```bash
rg -n 'addJavascriptInterface|handler\.proceed\(|HostnameVerifier\s*\{|X509TrustManager|allowUniversalAccessFromFileURLs\s*=\s*true|mixedContentMode\s*=\s*WebSettings\.MIXED_CONTENT_ALWAYS_ALLOW' app/src/main && exit 1 || true
rg -n 'PrivateKey\.encoded|privateKey\.encoded|evaluateJavascript\(' app/src/main app/src/test && exit 1 || true
rg -n 'FLAG_SECURE' app/src/debug/java/dev/junta/firmamobile/browser/ProtocolProbeActivity.kt && exit 1 || true
git diff --check
```

Expected: searches print nothing and diff check succeeds.

- [ ] **Step 2: Verify APK structure and release isolation**

```bash
APKSIGNER="$ANDROID_HOME/build-tools/36.0.0/apksigner"
ZIPALIGN="$ANDROID_HOME/build-tools/36.0.0/zipalign"
"$APKSIGNER" verify --verbose app/build/outputs/apk/debug/app-debug.apk
"$APKSIGNER" verify --verbose app/build/outputs/apk/release/app-release.apk
"$ZIPALIGN" -c -p 4 app/build/outputs/apk/debug/app-debug.apk
"$ZIPALIGN" -c -p 4 app/build/outputs/apk/release/app-release.apk
.gradle/termux-aapt2/bin/aapt2 dump badging \
  app/build/outputs/apk/release/app-release.apk | \
  rg 'application-debuggable' && exit 1 || true
unzip -l app/build/outputs/apk/release/app-release.apk | \
  rg 'ProtocolProbe|androidTest|synthetic-identity|\.p12|\.pfx' && exit 1 || true
sha256sum app/build/outputs/apk/debug/app-debug.apk \
  app/build/outputs/apk/release/app-release.apk
```

Expected: v2 verification and zipalign succeed, release is non-debuggable and
contains no probe/test/certificate fixture. Debug-key-signed release remains a
documented non-production limitation until the final release phase.

- [ ] **Step 3: Install through Android shell with authoritative exit codes**

Copy the verified debug APK to shared Work and then `/data/local/tmp` because
Android 16 `system_server` cannot read the FUSE `/sdcard` APK directly:

```bash
cp app/build/outputs/apk/debug/app-debug.apk \
  /storage/emulated/0/Codex/Work/junta-firma-mobile/app-debug.apk
rish -c "cp /sdcard/Codex/Work/junta-firma-mobile/app-debug.apk \
  /data/local/tmp/junta-firma-mobile-debug.apk; chmod 644 \
  /data/local/tmp/junta-firma-mobile-debug.apk; pm install -r -t \
  /data/local/tmp/junta-firma-mobile-debug.apk; result=\$?; \
  echo INSTALL_EXIT=\$result; exit \$result"
rish -c "pm path dev.junta.firmamobile"
```

Expected: `Success`, `INSTALL_EXIT=0`, and a package path.

- [ ] **Step 4: Run the one allowed visual/runtime gate**

Record the actual navigation mode with `settings get secure navigation_mode`.
Launch only the flow needed to show the browser/probe; use the debug probe when
certificate setup would otherwise block BrowserScreen. Capture UI tree and one
screenshot:

```bash
rish -c "am start -W -n \
  dev.junta.firmamobile/.browser.ProtocolProbeActivity; sleep 12; \
  uiautomator dump /data/local/tmp/junta-probe-ux.xml >/dev/null; \
  cp /data/local/tmp/junta-probe-ux.xml \
  /sdcard/Codex/Work/junta-firma-mobile/junta-probe-ux.xml; \
  screencap -p \
  /sdcard/Codex/Work/junta-firma-mobile/junta-probe-ux.png"
```

Inspect the real screenshot. Required observations:

- screenshot pixels are visible rather than black;
- host occupies one line and details are collapsed;
- WebView/cookie banner bottom is above the three-button or gestural navigation
  inset;
- lower controls can be scrolled into view and tapped;
- no Google Play or external AutoFirma activity appears.

If only one navigation mode is currently configured, verify that mode on the
device and rely on injected inset tests for the other mode; do not mutate the
user's system navigation preference.

- [ ] **Step 5: Force-stop and review the exact UX diff**

```bash
rish -c "am force-stop dev.junta.firmamobile"
git status --short
git diff --stat
git diff --check
```

Expected: app is not left open; only browser/probe UX and test files are dirty.

- [ ] **Step 6: Commit the UX correction separately**

```bash
git add \
  app/src/main/java/dev/junta/firmamobile/MainActivity.kt \
  app/src/main/java/dev/junta/firmamobile/browser/JuntaWebViewClient.kt \
  app/src/main/java/dev/junta/firmamobile/ui/BrowserAddressBar.kt \
  app/src/main/java/dev/junta/firmamobile/ui/BrowserScreen.kt \
  app/src/main/java/dev/junta/firmamobile/ui/BrowserWindowInsets.kt \
  app/src/main/java/dev/junta/firmamobile/ui/SensitiveWindowProtection.kt \
  app/src/main/res/values/strings.xml \
  app/src/debug/java/dev/junta/firmamobile/browser/ProtocolProbeActivity.kt \
  app/src/test/java/dev/junta/firmamobile/ui/BrowserScreenTest.kt \
  app/src/test/java/dev/junta/firmamobile/ui/BrowserWindowInsetsTest.kt \
  app/src/test/java/dev/junta/firmamobile/ui/SensitiveWindowProtectionTest.kt \
  app/src/test/java/dev/junta/firmamobile/browser/JuntaWebViewClientTest.kt \
  app/src/androidTest/java/dev/junta/firmamobile/AppLaunchTest.kt \
  app/src/androidTest/java/dev/junta/firmamobile/CertificateSetupFlowTest.kt \
  app/src/androidTest/java/dev/junta/firmamobile/browser/ProtocolProbeInstrumentedTest.kt
git diff --cached --check
git commit -m "fix: correct browser and probe system UI handling"
```

Expected: the commit contains no observer parser, certificate, signing,
origin-policy, cookie, or bridge changes.

---

## Self-review coverage

- Address host/full-URL/fixed-height requirements: Tasks 1.1 and 2.2–2.3.
- Three-button, gestural, safeDrawing, and IME insets without duplication:
  Tasks 1.2, 2.1, and 3.4.
- Browser and probe share the inset boundary: Task 2.1 and Task 2.5.
- Screenshot policy and temporary sensitive flag: Tasks 1.3 and 2.4–2.5.
- No observer/security regression: global constraints and Tasks 0, 3.1, 3.2,
  and 3.6.
- Device screenshot and app closure: Tasks 3.4–3.5.
- Wider Junta runtime observation remains open and resumes only after this
  plan completes.

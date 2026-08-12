package dev.junta.firmamobile.browser

import android.Manifest
import android.R as AndroidR
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.SystemClock
import android.view.ViewGroup
import android.view.WindowManager.LayoutParams.FLAG_SECURE
import android.view.WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE
import android.view.WindowManager.LayoutParams.SOFT_INPUT_MASK_ADJUST
import android.webkit.WebView
import android.widget.TextView
import androidx.core.graphics.Insets
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.test.core.app.ActivityScenario
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import androidx.lifecycle.Lifecycle
import dev.junta.firmamobile.R
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicReference
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class ProtocolProbeInstrumentedTest {
    @Suppress("DEPRECATION")
    @Test
    fun debugProbeIsExplicitlyProtectedByTheShellOnlyDumpPermission() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val info = context.packageManager.getActivityInfo(
            ComponentName(context, ProtocolProbeActivity::class.java),
            PackageManager.GET_META_DATA,
        )

        assertTrue(info.exported)
        assertEquals("android.permission.DUMP", info.permission)
        assertEquals(context.packageName, info.packageName)
    }

    @Test
    fun probeAllowsScreenshotsAndReservesTheCurrentNavigationInset() {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        instrumentation.uiAutomation.adoptShellPermissionIdentity(Manifest.permission.DUMP)
        try {
            launchProbeWithoutInitialLoad().use { scenario ->
                scenario.onActivity { activity ->
                    assertEquals(0, activity.window.attributes.flags and FLAG_SECURE)
                    assertEquals(
                        SOFT_INPUT_ADJUST_RESIZE,
                        activity.window.attributes.softInputMode and SOFT_INPUT_MASK_ADJUST,
                    )
                    val content = activity.findViewById<ViewGroup>(AndroidR.id.content)
                    val root = content.findViewById<ViewGroup>(R.id.protocol_probe_root)
                    val navigationReservation = maxOf(
                        root.paddingLeft,
                        root.paddingRight,
                        root.paddingBottom,
                    )
                    assertTrue(
                        "The current navigation mode must reserve a non-top system edge",
                        navigationReservation > 0,
                    )

                    val navigation = WindowInsetsCompat.Builder()
                        .setInsets(
                            WindowInsetsCompat.Type.navigationBars(),
                            Insets.of(0, 0, 0, 96),
                        )
                        .setVisible(WindowInsetsCompat.Type.navigationBars(), true)
                        .setVisible(WindowInsetsCompat.Type.ime(), false)
                        .build()
                    ViewCompat.dispatchApplyWindowInsets(root, navigation)
                    assertEquals(96, root.paddingBottom)

                    val keyboard = WindowInsetsCompat.Builder(navigation)
                        .setInsets(
                            WindowInsetsCompat.Type.ime(),
                            Insets.of(0, 0, 0, 320),
                        )
                        .setVisible(WindowInsetsCompat.Type.ime(), true)
                        .build()
                    ViewCompat.dispatchApplyWindowInsets(root, keyboard)
                    assertEquals(320, root.paddingBottom)
                    ViewCompat.dispatchApplyWindowInsets(root, navigation)
                    assertEquals(96, root.paddingBottom)
                }
            }
        } finally {
            instrumentation.uiAutomation.dropShellPermissionIdentity()
        }
    }

    @Test
    fun longProbeObservationKeepsWebContentVisible() {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        instrumentation.uiAutomation.adoptShellPermissionIdentity(Manifest.permission.DUMP)
        try {
            launchProbeWithoutInitialLoad().use { scenario ->
                scenario.onActivity { activity ->
                    activity.findViewById<TextView>(R.id.protocol_probe_status).text =
                        List(80) { index -> "safe observation $index" }.joinToString("\n")
                }
                instrumentation.waitForIdleSync()
                scenario.onActivity { activity ->
                    val root = activity.findViewById<ViewGroup>(R.id.protocol_probe_root)
                    val status = activity.findViewById<TextView>(R.id.protocol_probe_status)
                    val webView = activity.findViewById<WebView>(R.id.protocol_probe_webview)
                    assertTrue(root.height > 0)
                    assertTrue(status.height < root.height / 2)
                    assertTrue(webView.height > 0)
                }
            }
        } finally {
            instrumentation.uiAutomation.dropShellPermissionIdentity()
        }
    }

    @Test
    fun documentLocationUsesOnlyActiveWindowOrFailsClosedWithoutReversePairing() {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        instrumentation.uiAutomation.adoptShellPermissionIdentity(Manifest.permission.DUMP)
        try {
            launchProbeWithoutInitialLoad().use { scenario ->
                scenario.onActivity { activity ->
                    activity.findViewById<WebView>(R.id.protocol_probe_webview)
                        .loadDataWithBaseURL(
                            SYNTHETIC_TRUSTED_URL,
                            SYNTHETIC_SIGN_PAGE,
                            "text/html",
                            "UTF-8",
                            null,
                        )
                }

                awaitTitle(scenario, SYNTHETIC_DONE_TITLE)
                val observation = currentStatus(scenario)
                val correlated = observation.contains(CORRELATED_OBSERVATION)
                val recorderState = safeRecorderState(scenario)
                assertTrue(
                    "$observation probeState=${safeProbeState(scenario)} " +
                        "recorderState=$recorderState",
                    correlated || recorderState.failedClosed,
                )
                if (correlated) {
                    assertTrue(observation, observation.contains("correlation=ACTIVE_CALL_WINDOW"))
                    assertTrue(observation.contains("argument.0.length=37"))
                }
                assertFalse(observation.contains("BOUNDED_EVENT_PAIR"))
                assertFalse(observation.contains(RAW_INTENT_CANARY))
                assertHistoryExcludes(scenario, RAW_INTENT_CANARY)
                assertEquals(Lifecycle.State.RESUMED, scenario.state)
                scenario.onActivity { activity ->
                    assertEquals(
                        0,
                        activity.window.attributes.flags and FLAG_SECURE,
                    )
                }
            }
        } finally {
            instrumentation.uiAutomation.dropShellPermissionIdentity()
        }
    }

    @Test
    fun iframeIntentIsBlockedWithoutConsumingTheTopLevelSignWindow() {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        instrumentation.uiAutomation.adoptShellPermissionIdentity(Manifest.permission.DUMP)
        try {
            launchProbeWithoutInitialLoad().use { scenario ->
                loadSyntheticPage(scenario, SYNTHETIC_IFRAME_THEN_MAIN_FRAME_PAGE)

                val observation = awaitStatus(scenario, CORRELATED_OBSERVATION)
                val correlatedLines = observation.lineSequence()
                    .filter { it.contains(CORRELATED_OBSERVATION) }
                    .toList()
                assertTrue(observation, correlatedLines.isNotEmpty())
                assertTrue(correlatedLines.all { it.contains("argument.0.length=37") })
                assertTrue(correlatedLines.all { it.contains("correlation=REQUEST_ID") })
                assertFalse(observation.contains(IFRAME_INTENT_CANARY))
                assertFalse(observation.contains(MAIN_FRAME_INTENT_CANARY))
                assertHistoryExcludes(scenario, IFRAME_INTENT_CANARY, MAIN_FRAME_INTENT_CANARY)
                assertEquals(Lifecycle.State.RESUMED, scenario.state)
            }
        } finally {
            instrumentation.uiAutomation.dropShellPermissionIdentity()
        }
    }

    @Test
    fun standaloneIntentBeforeSignIsNeverAttachedRetroactively() {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        instrumentation.uiAutomation.adoptShellPermissionIdentity(Manifest.permission.DUMP)
        try {
            launchProbeWithoutInitialLoad().use { scenario ->
                loadSyntheticPage(scenario, SYNTHETIC_STANDALONE_THEN_SIGN_PAGE)
                awaitTitle(scenario, SYNTHETIC_DONE_TITLE)

                val observation = currentStatus(scenario)
                assertFalse(observation.contains(CORRELATED_OBSERVATION))
                assertFalse(observation.contains(STANDALONE_INTENT_CANARY))
                assertFalse(observation.contains(MAIN_FRAME_INTENT_CANARY))
                assertHistoryExcludes(
                    scenario,
                    STANDALONE_INTENT_CANARY,
                    MAIN_FRAME_INTENT_CANARY,
                )
                assertEquals(Lifecycle.State.RESUMED, scenario.state)
            }
        } finally {
            instrumentation.uiAutomation.dropShellPermissionIdentity()
        }
    }

    @Test
    fun windowOpenInsideSignUsesExactRequestIdCorrelation() {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        instrumentation.uiAutomation.adoptShellPermissionIdentity(Manifest.permission.DUMP)
        try {
            launchProbeWithoutInitialLoad().use { scenario ->
                loadSyntheticPage(scenario, SYNTHETIC_WINDOW_OPEN_PAGE)

                val observation = awaitStatus(scenario, "correlation=REQUEST_ID")
                assertTrue(observation, observation.contains(CORRELATED_OBSERVATION))
                assertTrue(observation, observation.contains("argument.0.length=23"))
                assertFalse(observation.contains(WINDOW_OPEN_CANARY))
                assertHistoryExcludes(scenario, WINDOW_OPEN_CANARY)
                assertEquals(Lifecycle.State.RESUMED, scenario.state)
            }
        } finally {
            instrumentation.uiAutomation.dropShellPermissionIdentity()
        }
    }

    @Test
    fun observationFailurePreservesOriginalPortalReturnAndException() {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        instrumentation.uiAutomation.adoptShellPermissionIdentity(Manifest.permission.DUMP)
        try {
            launchProbeWithoutInitialLoad().use { scenario ->
                loadSyntheticPage(scenario, SYNTHETIC_OBSERVATION_FAILURE_PAGE)
                awaitTitle(scenario, ORIGINAL_BEHAVIOR_PRESERVED_TITLE)

                assertEquals(Lifecycle.State.RESUMED, scenario.state)
                assertHistoryExcludes(scenario, OBSERVATION_FAILURE_CANARY)
            }
        } finally {
            instrumentation.uiAutomation.dropShellPermissionIdentity()
        }
    }

    @Test
    fun nonStringProbeMessagePoisonsTrustedMainFrameCorrelation() {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        instrumentation.uiAutomation.adoptShellPermissionIdentity(Manifest.permission.DUMP)
        try {
            launchProbeWithoutInitialLoad().use { scenario ->
                loadSyntheticPage(scenario, SYNTHETIC_NON_STRING_MESSAGE_PAGE)
                awaitTitle(scenario, SYNTHETIC_DONE_TITLE)

                val observation = awaitStatus(
                    scenario,
                    "event=PROTOCOL_CORRELATION_REJECTED",
                )
                assertTrue(observation.contains("event=PROTOCOL_CORRELATION_REJECTED"))
                assertFalse(observation.contains("call=SIGN branch=INTENT"))
                assertFalse(observation.contains(NON_STRING_MESSAGE_CANARY))
                assertHistoryExcludes(scenario, NON_STRING_MESSAGE_CANARY)
                assertEquals(Lifecycle.State.RESUMED, scenario.state)
            }
        } finally {
            instrumentation.uiAutomation.dropShellPermissionIdentity()
        }
    }

    private fun loadSyntheticPage(
        scenario: ActivityScenario<ProtocolProbeActivity>,
        page: String,
    ) {
        scenario.onActivity { activity ->
            activity.findViewById<WebView>(R.id.protocol_probe_webview)
                .loadDataWithBaseURL(
                    SYNTHETIC_TRUSTED_URL,
                    page,
                    "text/html",
                    "UTF-8",
                    null,
                )
        }
    }

    private fun awaitStatus(
        scenario: ActivityScenario<ProtocolProbeActivity>,
        expected: String,
    ): String {
        val deadline = SystemClock.uptimeMillis() + CORRELATION_TIMEOUT_MILLIS
        var observation = ""
        do {
            observation = currentStatus(scenario)
            if (observation.contains(expected)) return observation
            SystemClock.sleep(POLL_INTERVAL_MILLIS)
        } while (SystemClock.uptimeMillis() < deadline)
        return observation
    }

    private fun currentStatus(scenario: ActivityScenario<ProtocolProbeActivity>): String {
        var observation = ""
        scenario.onActivity { activity ->
            observation = activity
                .findViewById<TextView>(R.id.protocol_probe_status)
                .text
                .toString()
        }
        return observation
    }

    private fun awaitTitle(
        scenario: ActivityScenario<ProtocolProbeActivity>,
        expected: String,
    ) {
        val deadline = SystemClock.uptimeMillis() + CORRELATION_TIMEOUT_MILLIS
        var title: String? = null
        do {
            scenario.onActivity { activity ->
                title = activity.findViewById<WebView>(R.id.protocol_probe_webview).title
            }
            if (title == expected) return
            SystemClock.sleep(POLL_INTERVAL_MILLIS)
        } while (SystemClock.uptimeMillis() < deadline)
        assertEquals(expected, title)
    }

    private fun assertHistoryExcludes(
        scenario: ActivityScenario<ProtocolProbeActivity>,
        vararg canaries: String,
    ) {
        scenario.onActivity { activity ->
            val history = activity.findViewById<WebView>(R.id.protocol_probe_webview)
                .copyBackForwardList()
            repeat(history.size) { index ->
                val item = history.getItemAtIndex(index)
                val safeSurface = listOf(item.url, item.originalUrl, item.title)
                    .joinToString(separator = " ")
                canaries.forEach { canary ->
                    assertFalse(safeSurface.contains(canary))
                }
            }
        }
    }

    private fun safeProbeState(
        scenario: ActivityScenario<ProtocolProbeActivity>,
    ): String {
        val value = AtomicReference("unavailable")
        val completed = CountDownLatch(1)
        scenario.onActivity { activity ->
            activity.findViewById<WebView>(R.id.protocol_probe_webview)
                .evaluateJavascript(SAFE_PROBE_STATE_EXPRESSION) { encodedState ->
                    value.set(encodedState)
                    completed.countDown()
                }
        }
        if (!completed.await(5, TimeUnit.SECONDS)) return "timeout"
        return value.get()
    }

    private fun safeRecorderState(
        scenario: ActivityScenario<ProtocolProbeActivity>,
    ): SafeRecorderState {
        var state = SafeRecorderState(
            documentActive = false,
            failedClosed = true,
            bufferedMessageCount = -1,
            pendingCallCount = -1,
            activationResult = DocumentActivationResult.NONE,
        )
        scenario.onActivity { activity ->
            state = activity.protocolRecorderForTesting.safeStateForTesting()
        }
        return state
    }

    private fun launchProbeWithoutInitialLoad(): ActivityScenario<ProtocolProbeActivity> {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val launchIntent = Intent(context, ProtocolProbeActivity::class.java).putExtra(
            ProtocolProbeActivity.EXTRA_SKIP_INITIAL_LOAD,
            true,
        )
        return ActivityScenario.launch(launchIntent)
    }

    private companion object {
        const val SYNTHETIC_TRUSTED_URL =
            "https://www.juntadeandalucia.es/empleoformacionytrabajoautonomo/" +
                "ovorion/auth/signInAutcertjs"
        const val RAW_INTENT_CANARY = "jfm-correlation-canary"
        const val IFRAME_INTENT_CANARY = "jfm-iframe-intent-canary"
        const val MAIN_FRAME_INTENT_CANARY = "jfm-main-frame-intent-canary"
        const val STANDALONE_INTENT_CANARY = "jfm-standalone-intent-canary"
        const val WINDOW_OPEN_CANARY = "jfm-window-open-canary"
        const val OBSERVATION_FAILURE_CANARY = "jfm-observation-failure-canary"
        const val NON_STRING_MESSAGE_CANARY = "jfm-non-string-message-canary"
        const val SYNTHETIC_DONE_TITLE = "probe-flow-done"
        const val ORIGINAL_BEHAVIOR_PRESERVED_TITLE = "probe-original-behavior-preserved"
        const val SAFE_PROBE_STATE_EXPRESSION =
            "JSON.stringify({" +
                "shim:window.__jfmAfirmaShimInstalled===true," +
                "documentId:/^[0-9a-f-]{36}$/.test(window.__jfmProbeDocumentId||'')," +
                "probe:typeof window.JuntaFirmaProbe==='object'," +
                "sign:!!(window.MiniApplet&&typeof window.MiniApplet.sign==='function')" +
                "})"
        const val CORRELATED_OBSERVATION = "call=SIGN branch=INTENT"
        const val CORRELATION_TIMEOUT_MILLIS = 10_000L
        const val POLL_INTERVAL_MILLIS = 100L
        val SYNTHETIC_SIGN_PAGE = """
            <!doctype html>
            <script>
              window.MiniApplet = {
                sign: function() {
                  document.location =
                    "intent://$RAW_INTENT_CANARY/#Intent;scheme=https;end";
                }
              };
              window.addEventListener("load", () => setTimeout(() => {
                window.MiniApplet.sign(
                  "xxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxx",
                  "SHA256withRSA",
                  "CAdES",
                  "",
                  "",
                  ""
                );
                document.title = "$SYNTHETIC_DONE_TITLE";
              }, 500));
            </script>
        """.trimIndent()
        val SYNTHETIC_IFRAME_THEN_MAIN_FRAME_PAGE = """
            <!doctype html>
            <iframe id="probe-frame"></iframe>
            <script>
              window.MiniApplet = {
                sign: function(dat) {
                  if (dat.length === 17) {
                    document.getElementById("probe-frame").src =
                      "intent://$IFRAME_INTENT_CANARY/#Intent;scheme=https;end";
                  } else {
                    window.open(
                      "intent://$MAIN_FRAME_INTENT_CANARY/#Intent;scheme=https;end"
                    );
                  }
                }
              };
              window.addEventListener("load", () => setTimeout(() => {
                window.MiniApplet.sign(
                  "xxxxxxxxxxxxxxxxx", "SHA256withRSA", "CAdES", "", "", ""
                );
                setTimeout(() => window.MiniApplet.sign(
                  "xxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxx",
                  "SHA256withRSA", "CAdES", "", "", ""
                ), 200);
              }, 500));
            </script>
        """.trimIndent()
        val SYNTHETIC_STANDALONE_THEN_SIGN_PAGE = """
            <!doctype html>
            <script>
              window.MiniApplet = {
                sign: function() {
                  document.location =
                    "intent://$MAIN_FRAME_INTENT_CANARY/#Intent;scheme=https;end";
                }
              };
              window.addEventListener("load", () => setTimeout(() => {
                document.location =
                  "intent://$STANDALONE_INTENT_CANARY/#Intent;scheme=https;end";
                setTimeout(() => {
                  window.MiniApplet.sign(
                    "xxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxx",
                    "SHA256withRSA", "CAdES", "", "", ""
                  );
                  document.title = "$SYNTHETIC_DONE_TITLE";
                }, 200);
              }, 500));
            </script>
        """.trimIndent()
        val SYNTHETIC_WINDOW_OPEN_PAGE = """
            <!doctype html>
            <script>
              window.MiniApplet = {
                sign: function() {
                  window.open(
                    "intent://$WINDOW_OPEN_CANARY/#Intent;scheme=https;end"
                  );
                }
              };
              window.addEventListener("load", () => setTimeout(() => {
                window.MiniApplet.sign(
                  "xxxxxxxxxxxxxxxxxxxxxxx",
                  "SHA256withRSA", "CAdES", "", "", ""
                );
              }, 500));
            </script>
        """.trimIndent()
        val SYNTHETIC_OBSERVATION_FAILURE_PAGE = """
            <!doctype html>
            <script>
              let invocationCount = 0;
              window.MiniApplet = {
                sign: function() {
                  invocationCount += 1;
                  return "$OBSERVATION_FAILURE_CANARY-return";
                }
              };
              window.addEventListener("load", () => setTimeout(() => {
                const first = Proxy.revocable({}, {});
                first.revoke();
                const returned = window.MiniApplet.sign(
                  first.proxy, "SHA256withRSA", "CAdES", "", "", ""
                );
                window.MiniApplet.sign = function() {
                  invocationCount += 1;
                  throw new Error("$OBSERVATION_FAILURE_CANARY-error");
                };
                const second = Proxy.revocable({}, {});
                second.revoke();
                let originalExceptionPreserved = false;
                try {
                  window.MiniApplet.sign(
                    second.proxy, "SHA256withRSA", "CAdES", "", "", ""
                  );
                } catch (error) {
                  originalExceptionPreserved =
                    error.message === "$OBSERVATION_FAILURE_CANARY-error";
                }
                if (returned === "$OBSERVATION_FAILURE_CANARY-return" &&
                    originalExceptionPreserved && invocationCount === 2) {
                  document.title = "$ORIGINAL_BEHAVIOR_PRESERVED_TITLE";
                } else {
                  document.title = "probe-original-behavior-replaced";
                }
              }, 500));
            </script>
        """.trimIndent()
        val SYNTHETIC_NON_STRING_MESSAGE_PAGE = """
            <!doctype html>
            <script>
              window.MiniApplet = {
                sign: function() {
                  window.open(
                    "intent://$NON_STRING_MESSAGE_CANARY/#Intent;scheme=https;end"
                  );
                }
              };
              window.addEventListener("load", () => setTimeout(() => {
                window.JuntaFirmaProbe.postMessage(new ArrayBuffer(8));
                setTimeout(() => {
                  window.MiniApplet.sign(
                    "xxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxx",
                    "SHA256withRSA", "CAdES", "", "", ""
                  );
                  document.title = "$SYNTHETIC_DONE_TITLE";
                }, 200);
              }, 500));
            </script>
        """.trimIndent()
    }
}

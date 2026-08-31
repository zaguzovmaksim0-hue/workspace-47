package dev.junta.firmamobile

import android.app.Activity
import android.app.Instrumentation.ActivityResult
import android.content.Intent
import androidx.compose.ui.test.junit4.v2.createEmptyComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.core.app.ActivityScenario
import androidx.test.espresso.intent.Intents
import androidx.test.espresso.intent.Intents.intending
import androidx.test.espresso.intent.matcher.IntentMatchers.hasAction
import androidx.test.espresso.intent.matcher.IntentMatchers.hasCategories
import androidx.test.espresso.intent.matcher.IntentMatchers.hasExtra
import androidx.test.espresso.intent.matcher.IntentMatchers.hasType
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.hamcrest.Matchers.allOf
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class CertificatePickerIntentTest {
    @get:Rule
    val rule = createEmptyComposeRule()

    @Test
    fun selectCertificateLaunchesOpenDocumentWithPkcs12MimeAllowlist() {
        val matcher = allOf(
            hasAction(Intent.ACTION_OPEN_DOCUMENT),
            hasCategories(setOf(Intent.CATEGORY_OPENABLE)),
            hasType("*/*"),
            hasExtra(
                Intent.EXTRA_MIME_TYPES,
                arrayOf(
                    "application/x-pkcs12",
                    "application/pkcs12",
                    "application/octet-stream",
                ),
            ),
        )

        TestCertificateDependencies.install().use {
            Intents.init()
            try {
                intending(matcher).respondWith(ActivityResult(Activity.RESULT_CANCELED, null))
                ActivityScenario.launch(MainActivity::class.java).use {
                    rule.onNodeWithText("Seleccionar certificado").performClick()
                    assertTrue(
                        "Expected ACTION_OPEN_DOCUMENT with the PKCS#12 MIME allowlist",
                        Intents.getIntents().any(matcher::matches),
                    )
                }
            } finally {
                Intents.release()
            }
        }
    }
}

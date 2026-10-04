package dev.junta.firmamobile.testing

import android.app.Application
import android.content.ComponentName
import android.content.Intent
import android.content.IntentFilter
import androidx.activity.ComponentActivity
import androidx.compose.ui.test.junit4.ComposeContentTestRule
import androidx.compose.ui.test.junit4.v2.createComposeRule as platformComposeRule
import org.junit.runner.Description
import org.junit.runners.model.Statement
import org.robolectric.RuntimeEnvironment
import org.robolectric.Shadows.shadowOf

/** Register only the synthetic Compose host in Robolectric's in-memory package
 * manager. The production manifest/APK remains exactly as shipped. All actual
 * UI assertions and production components still run through the normal rule. */
fun createComposeRule(): ComposeContentTestRule {
    val delegate = platformComposeRule()
    return object : ComposeContentTestRule by delegate {
        override fun apply(base: Statement, description: Description): Statement = object : Statement() {
            override fun evaluate() {
                val app = RuntimeEnvironment.getApplication<Application>()
                val component = ComponentName(app.packageName, ComponentActivity::class.java.name)
                val packages = shadowOf(app.packageManager)
                packages.addActivityIfNotPresent(component)
                packages.addIntentFilterForActivity(component, IntentFilter(Intent.ACTION_MAIN).apply {
                    addCategory(Intent.CATEGORY_LAUNCHER)
                })
                delegate.apply(base, description).evaluate()
            }
        }
    }
}

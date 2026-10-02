package dev.junta.firmamobile

import androidx.activity.compose.setContent
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.State
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.junit4.v2.createEmptyComposeRule
import androidx.compose.ui.test.onChildren
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import dev.junta.firmamobile.afirma.servlet.NativeBatchReceipt
import dev.junta.firmamobile.afirma.servlet.NativeBatchReceipt.Entry
import dev.junta.firmamobile.afirma.servlet.NativeBatchReceipt.Origin
import dev.junta.firmamobile.ui.NativeBatchResultDetails
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class NativeBatchResultDetailsInstrumentedTest {
    @get:Rule
    val composeRule = createEmptyComposeRule()

    private val toggle get() = composeRule.onNodeWithTag("native-batch-details-toggle")
    private val details get() = composeRule.onNodeWithTag("native-batch-details")

    @Test
    fun startsCollapsedAndOpeningShowsAllRowsWithOnlyTheToggleAction() {
        val receipt = serviceReceipt()
        withDetails(mutableStateOf(receipt)) { activity ->
            toggle.assertTextEquals(activity.getString(R.string.native_batch_details_show))
            details.assertDoesNotExist()
            receipt.entries.indices.forEach { index ->
                composeRule.onNodeWithTag("native-batch-result-$index").assertDoesNotExist()
            }
            composeRule.onAllNodes(hasClickAction()).assertCountEquals(1)

            toggle.performClick()

            toggle.assertTextEquals(activity.getString(R.string.native_batch_details_hide))
            details.assertExists()
            assertText(activity, R.string.native_batch_origin_service)
            receipt.entries.forEachIndexed { index, entry ->
                composeRule.onNodeWithTag("native-batch-result-$index").assertExists()
                assertText(activity, R.string.native_batch_id, index + 1, entry.documentLabel)
                assertText(activity, R.string.native_batch_status, entry.statusCode)
                entry.description?.let { description ->
                    assertText(activity, R.string.native_batch_description, description)
                }
            }
            composeRule.onNodeWithTag("native-batch-result-1").onChildren().assertCountEquals(3)
            composeRule.onAllNodes(hasClickAction()).assertCountEquals(1)
        }
    }

    @Test
    fun detailsCanBeCollapsedAgain() {
        val receipt = serviceReceipt()
        withDetails(mutableStateOf(receipt)) { activity ->
            toggle.performClick()
            details.assertExists()

            toggle.performClick()

            toggle.assertTextEquals(activity.getString(R.string.native_batch_details_show))
            details.assertDoesNotExist()
            receipt.entries.indices.forEach { index ->
                composeRule.onNodeWithTag("native-batch-result-$index").assertDoesNotExist()
            }
            composeRule.onNodeWithText(
                activity.getString(R.string.native_batch_origin_service),
            ).assertDoesNotExist()
        }
    }

    @Test
    fun changingReceiptResetsExpansionAndShowsTheNewReceiptWhenReopened() {
        val original = serviceReceipt()
        val localEntry = Entry("Local document.pdf", "CUSTOM_STATUS", "Local receipt detail.")
        val current = mutableStateOf(original)
        val replacement = NativeBatchReceipt(Origin.LOCAL, listOf(localEntry))
        withDetails(current) { activity ->
            toggle.performClick()
            details.assertExists()

            composeRule.runOnIdle { current.value = replacement }

            toggle.assertTextEquals(activity.getString(R.string.native_batch_details_show))
            details.assertDoesNotExist()
            original.entries.indices.forEach { index ->
                composeRule.onNodeWithTag("native-batch-result-$index").assertDoesNotExist()
            }

            toggle.performClick()

            details.assertExists()
            assertText(activity, R.string.native_batch_origin_local)
            composeRule.onNodeWithTag("native-batch-result-0").assertExists()
            assertText(activity, R.string.native_batch_id, 1, localEntry.documentLabel)
            assertText(activity, R.string.native_batch_status, localEntry.statusCode)
            assertText(activity, R.string.native_batch_description, localEntry.description!!)
            composeRule.onNodeWithTag("native-batch-result-1").assertDoesNotExist()
            composeRule.onNodeWithTag("native-batch-result-2").assertDoesNotExist()
            composeRule.onNodeWithText(
                activity.getString(R.string.native_batch_origin_service),
            ).assertDoesNotExist()
            composeRule.onNodeWithText(
                activity.getString(R.string.native_batch_id, 1, original.entries[0].documentLabel),
            ).assertDoesNotExist()
        }
    }

    private fun serviceReceipt() = NativeBatchReceipt(
        Origin.SERVICE,
        listOf(
            Entry("same <b>document</b>.pdf", "DONE_AND_SAVED", "Saved <b>as reported</b>."),
            Entry("same <b>document</b>.pdf", "ERROR_PRE", null),
            Entry("https://example.invalid/document.pdf", "SKIPPED", "Not attempted."),
        ),
    )

    private fun assertText(activity: MainActivity, resource: Int, vararg args: Any) {
        val expected = activity.getString(resource, *args)
        composeRule.onNodeWithText(expected).assertTextEquals(expected)
    }

    private fun withDetails(receipt: State<NativeBatchReceipt>, check: (MainActivity) -> Unit) {
        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            lateinit var activity: MainActivity
            scenario.onActivity {
                activity = it
                it.setContent {
                    MaterialTheme { NativeBatchResultDetails(receipt.value) }
                }
            }
            check(activity)
        }
    }
}

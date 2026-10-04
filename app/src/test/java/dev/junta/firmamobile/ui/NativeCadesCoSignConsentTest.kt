package dev.junta.firmamobile.ui

import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import dev.junta.firmamobile.afirma.servlet.AfirmaConsentDetails
import dev.junta.firmamobile.afirma.servlet.AfirmaConsentPhase
import dev.junta.firmamobile.afirma.servlet.AfirmaConsentProblem
import dev.junta.firmamobile.afirma.servlet.AfirmaConsentPrompt
import dev.junta.firmamobile.ui.theme.JuntaFirmaTheme
import java.util.UUID
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.ConscryptMode
import org.robolectric.annotation.GraphicsMode
import org.robolectric.annotation.SQLiteMode

/** Isolated consent component, not a browser/device/signing E2E. */
@RunWith(RobolectricTestRunner::class)
@ConscryptMode(ConscryptMode.Mode.OFF)
@GraphicsMode(GraphicsMode.Mode.LEGACY)
@SQLiteMode(SQLiteMode.Mode.LEGACY)
@Config(qualifiers="w400dp-h1200dp")
class NativeCadesCoSignConsentTest {
    @get:Rule val rule=createComposeRule()
    private var confirmed=0
    private var unlocked=0
    @Test fun localCosignExplainsTheAbsentOriginalAndDoesNotSignWhenLocked() {
        val value=prompt("cosign","CAdES · co-sign · detached",locked=true)
        rule.setContent { JuntaFirmaTheme { NativeAfirmaConsentDialog(value,{confirmed++},{unlocked++},{},{}) } }
        rule.onNodeWithTag("native-cades-cosign-notice").performScrollTo().assertIsDisplayed()
        rule.onNodeWithText("Añadir mi firma al documento ya firmado").performScrollTo().assertIsDisplayed()
        rule.onNodeWithTag("native-afirma-confirm").assertDoesNotExist()
        rule.runOnIdle { assertEquals(0,confirmed);assertEquals(0,unlocked) }
    }
    @Test fun matchingUnlockedIdentityStillRequiresTheExistingConfirmButton() {
        val value=prompt("cosign","CAdES · co-sign · attached",locked=false)
        rule.setContent { JuntaFirmaTheme { NativeAfirmaConsentDialog(value,{confirmed++},{unlocked++},{},{}) } }
        rule.runOnIdle { assertEquals(0,confirmed) }
        rule.onNodeWithTag("native-afirma-confirm").performClick()
        rule.runOnIdle { assertEquals(1,confirmed);assertEquals(0,unlocked) }
    }
    @Test fun localBatchDisclosureDoesNotClaimNativeHistoryChecksForDelegatedSigning() {
        val value=mutableStateOf(prompt("batch","Batch · local · CAdES co-sign",locked=true))
        rule.setContent { JuntaFirmaTheme { NativeAfirmaConsentDialog(value.value,{confirmed++},{unlocked++},{},{}) } }
        rule.onNodeWithTag("native-cades-cosign-notice").performScrollTo().assertIsDisplayed()
        rule.runOnIdle {
            value.value=prompt("cosign","CAdES · trifásico",locked=true).let { it.copy(details=it.details.copy(delegatedSigning=true)) }
        }
        rule.onNodeWithTag("native-cades-cosign-notice").assertDoesNotExist()
        rule.runOnIdle { assertEquals(0,confirmed);assertEquals(0,unlocked) }
    }
    private fun prompt(operation:String,format:String,locked:Boolean)=AfirmaConsentPrompt(
        UUID.randomUUID(),AfirmaConsentDetails("https://portal.synthetic.example","https://storage.synthetic.example/put",operation,format,"SHA256withRSA",4096,"a".repeat(64)),
        AfirmaConsentPhase.REVIEW,if(locked)null else "Synthetic fixture",if(locked)AfirmaConsentProblem.LOCKED else null,
    )
}

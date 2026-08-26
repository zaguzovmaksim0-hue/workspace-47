package dev.junta.firmamobile.control

import android.content.Intent
import android.net.Uri
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.ConscryptMode
import org.robolectric.annotation.GraphicsMode
import org.robolectric.annotation.SQLiteMode

@RunWith(RobolectricTestRunner::class)
@ConscryptMode(ConscryptMode.Mode.OFF)
@GraphicsMode(GraphicsMode.Mode.LEGACY)
@SQLiteMode(SQLiteMode.Mode.LEGACY)
class E2eControlIntentParserTest {
    @Test
    fun `raw password and unknown payload extras are rejected`() {
        val passwordIntent = Intent(E2eControlHook.ACTION)
            .putExtra(E2eControlHook.EXTRA_RUN_ID, "run-1")
            .putExtra(E2eControlHook.EXTRA_COMMAND, "CERT_UNLOCK")
            .putExtra(E2eControlHook.EXTRA_SECRET_HANDLE, "0123456789abcdef0123456789abcdef")
            .putExtra("password", "must-never-be-accepted")
        assertNull(E2eControlIntentParser.parse(passwordIntent))

        val payloadIntent = Intent(E2eControlHook.ACTION)
            .putExtra(E2eControlHook.EXTRA_RUN_ID, "run-2")
            .putExtra(E2eControlHook.EXTRA_COMMAND, "PORTAL_OPEN")
            .putExtra(E2eControlHook.EXTRA_PORTAL_ID, "age-reg-redsara")
            .putExtra("payload", "opaque")
        assertNull(E2eControlIntentParser.parse(payloadIntent))
    }

    @Test
    fun `certificate URI is data not an arbitrary string extra`() {
        val uri = Uri.parse(
            "content://com.android.externalstorage.documents/document/primary%3ADownload%2Ffixture.p12",
        )
        val intent = Intent(E2eControlHook.ACTION)
            .setData(uri)
            .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            .addFlags(Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION)
            .putExtra(E2eControlHook.EXTRA_RUN_ID, "run-cert")
            .putExtra(E2eControlHook.EXTRA_COMMAND, "CERT_SELECT")

        val parsed = requireNotNull(E2eControlIntentParser.parse(intent))
        assertEquals(uri, parsed.certificateUri)
        assertTrue(parsed.intentFlags and Intent.FLAG_GRANT_READ_URI_PERMISSION != 0)
        assertTrue(parsed.intentFlags and Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION != 0)
        assertFalse(intent.extras?.keySet().orEmpty().any { it.contains("password", ignoreCase = true) })
    }
}

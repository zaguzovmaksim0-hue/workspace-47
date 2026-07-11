package dev.junta.firmamobile

import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.ConscryptMode
import org.robolectric.annotation.GraphicsMode
import org.robolectric.annotation.SQLiteMode

@RunWith(RobolectricTestRunner::class)
@ConscryptMode(ConscryptMode.Mode.OFF)
@GraphicsMode(GraphicsMode.Mode.LEGACY)
@SQLiteMode(SQLiteMode.Mode.LEGACY)
class FirstRunCopyTest {
    @Test
    fun certificateCopyMatchesRequiredFirstRunText() {
        val context = RuntimeEnvironment.getApplication()

        assertEquals(
            "Selecciona tu archivo .p12 o .pfx.",
            context.getString(R.string.certificate_copy),
        )
    }

    @Test
    fun privacyCopyMatchesRequiredFirstRunText() {
        val context = RuntimeEnvironment.getApplication()

        assertEquals(
            "El archivo y la contraseña no se enviarán a terceros.",
            context.getString(R.string.privacy_copy),
        )
    }
}

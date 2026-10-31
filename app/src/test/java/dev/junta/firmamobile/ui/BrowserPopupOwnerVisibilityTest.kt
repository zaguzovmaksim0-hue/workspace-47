package dev.junta.firmamobile.ui

import android.content.Context
import android.view.View
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.assertEquals
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
class BrowserPopupOwnerVisibilityTest {
    @Test fun entryUrlResetWithoutCloseRestoresOwnerAfterPopupHidesIt() {
        val owner = View(ApplicationProvider.getApplicationContext<Context>())
        setBrowserPopupOwnerVisibility(owner, popupOpen = true)
        assertEquals(View.INVISIBLE, owner.visibility)
        setBrowserPopupOwnerVisibility(owner, popupOpen = false)
        assertEquals(View.VISIBLE, owner.visibility)
    }

    @Test fun absenceOfOwnerIsSafeAcrossPopupStateChanges() {
        setBrowserPopupOwnerVisibility(null, popupOpen = true)
        setBrowserPopupOwnerVisibility(null, popupOpen = false)
    }
}

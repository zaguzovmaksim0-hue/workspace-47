package dev.junta.firmamobile.ui

import android.view.View

/** Keep the owner view visible whenever the popup state is absent, even after a state-key reset. */
internal fun setBrowserPopupOwnerVisibility(owner: View?, popupOpen: Boolean) {
    owner?.visibility = if (popupOpen) View.INVISIBLE else View.VISIBLE
}

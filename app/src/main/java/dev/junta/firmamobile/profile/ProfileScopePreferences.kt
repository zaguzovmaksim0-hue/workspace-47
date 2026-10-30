package dev.junta.firmamobile.profile

import android.content.Context

class ProfileScopePreferences(context: Context) {
    private val preferences = context.applicationContext.getSharedPreferences(
        PREFERENCES_NAME,
        Context.MODE_PRIVATE,
    )

    fun verifiedOnly(): Boolean = preferences.getBoolean(VERIFIED_ONLY_KEY, false)

    fun setVerifiedOnly(verifiedOnly: Boolean): Boolean =
        preferences.edit().putBoolean(VERIFIED_ONLY_KEY, verifiedOnly).commit()

    /** One-time startup migration: the catalog no longer exposes this setting. */
    fun resetVerifiedOnly(): Boolean =
        if (verifiedOnly()) setVerifiedOnly(false) else false

    private companion object {
        const val PREFERENCES_NAME = "profile_scope_v1"
        const val VERIFIED_ONLY_KEY = "verified_only"
    }
}

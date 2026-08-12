package dev.junta.firmamobile.catalog

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import dev.junta.firmamobile.R

internal fun loadBundledPublicPortalCatalog(): PublicPortalCatalog {
    val context = ApplicationProvider.getApplicationContext<Context>()
    val json = context.resources.openRawResource(R.raw.public_portal_catalog_v1)
        .bufferedReader()
        .use { it.readText() }
    return PublicPortalCatalogParser.parse(json)
}

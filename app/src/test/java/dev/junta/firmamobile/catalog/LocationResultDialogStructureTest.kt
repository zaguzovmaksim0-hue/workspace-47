package dev.junta.firmamobile.catalog

import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** Source-only extraction guards, not Android/Compose behavior acceptance. */
class LocationResultDialogStructureTest {
    private val source = listOf(
        File("src/main/java/dev/junta/firmamobile/catalog/PortalCatalogScreen.kt"),
        File("app/src/main/java/dev/junta/firmamobile/catalog/PortalCatalogScreen.kt"),
    ).first { it.isFile }.readText()

    @Test
    fun dialogKeepsTheVisibilityGatePresentationAndSlotOrder() {
        assertBody("LocationResultDialog", """
            if (state == CatalogLocationState.IDLE || state == CatalogLocationState.LOADING) return
            val title = locationDialogTitle(state) ?: return
            val copy = locationDialogCopy(state) ?: return
            AlertDialog(
                onDismissRequest = onDismiss,
                shape = CatalogShape,
                containerColor = JuntaPaperElevated,
                titleContentColor = JuntaTealDark,
                textContentColor = JuntaInk,
                title = { Text(stringResource(title)) },
                text = { Text(stringResource(copy)) },
                confirmButton = {
                    locationDialogConfirmButton(state, onChooseManually, onOpenSettings, onOpenAppSettings)
                },
                dismissButton = {
                    locationDialogDismissButton(state, onChooseManually, onDismiss)
                },
            )
        """)
    }

    @Test
    fun resourceMappingsKeepAllSixErrorsAndBothHiddenStates() {
        val resources = linkedMapOf(
            "PERMISSION_DENIED" to "catalog_location_permission",
            "LOCATION_DISABLED" to "catalog_location_disabled",
            "UNAVAILABLE" to "catalog_location_unavailable",
            "GEOCODING_FAILED" to "catalog_geocoding_failed",
            "LOCATION_TIMEOUT" to "catalog_location_timeout",
            "OUTSIDE_SPAIN" to "catalog_location_outside",
        )
        for ((helper, suffix) in listOf("locationDialogTitle" to "title", "locationDialogCopy" to "copy")) {
            assertTrue(source.contains("@StringRes\nprivate fun $helper(state: CatalogLocationState): Int?"))
            val expected = resources.entries.joinToString("\n") { (state, resource) ->
                "CatalogLocationState.$state -> R.string.${resource}_$suffix"
            } + "\nCatalogLocationState.IDLE, CatalogLocationState.LOADING -> null"
            assertBody(helper, expected)
        }
    }

    @Test
    fun buttonsKeepExactCallbackRolesLabelsShapesAndDefaultEnabledState() {
        assertBody("locationDialogConfirmButton", """
            when (state) {
                CatalogLocationState.LOCATION_DISABLED -> {
                    Button(onClick = onOpenSettings, shape = CatalogShape) {
                        Text(stringResource(R.string.catalog_open_location_settings))
                    }
                }
                CatalogLocationState.PERMISSION_DENIED -> {
                    Button(onClick = onOpenAppSettings, shape = CatalogShape) {
                        Text(stringResource(R.string.catalog_open_permissions))
                    }
                }
                else -> {
                    Button(onClick = onChooseManually, shape = CatalogShape) {
                        Text(stringResource(R.string.catalog_choose_manually))
                    }
                }
            }
        """)
        assertBody("locationDialogDismissButton", """
            if (
                state == CatalogLocationState.LOCATION_DISABLED ||
                state == CatalogLocationState.PERMISSION_DENIED
            ) {
                OutlinedButton(onClick = onChooseManually, shape = CatalogShape) {
                    Text(stringResource(R.string.catalog_choose_manually))
                }
            } else {
                OutlinedButton(onClick = onDismiss, shape = CatalogShape) {
                    Text(stringResource(R.string.close))
                }
            }
        """)
    }

    @Test
    fun helpersStayPrivateLowerCamelAndOwnNoStateOrEffects() {
        for (name in listOf("locationDialogTitle", "locationDialogCopy",
            "locationDialogConfirmButton", "locationDialogDismissButton")) {
            assertTrue(name, source.contains("private fun $name("))
            assertFalse(name, Regex("\\b(?:remember\\w*|mutableStateOf|produceState|LaunchedEffect|DisposableEffect|SideEffect|LocalContext|launch)\\b")
                .containsMatchIn(body(name)))
        }
        for (name in listOf("locationDialogConfirmButton", "locationDialogDismissButton")) {
            assertTrue(name, source.contains("@Composable\nprivate fun $name("))
        }
        val caller = source.substringAfter("    LocationResultDialog(").substringBefore("\n    )")
        assertEquals(compact("""
            state = state.locationState,
            onDismiss = onDismissLocationMessage,
            onChooseManually = {
                onDismissLocationMessage()
                regionPickerVisible = true
            },
            onOpenSettings = {
                onDismissLocationMessage()
                onOpenLocationSettings()
            },
            onOpenAppSettings = {
                onDismissLocationMessage()
                onOpenAppSettings()
            },
        """), compact(caller))
    }

    private fun assertBody(name: String, expected: String) {
        assertEquals(name, compact(expected), compact(body(name)))
    }

    private fun body(name: String): String {
        val signature = "private fun $name("
        val declaration = source.indexOf(signature)
        assertTrue("Missing declaration: $signature", declaration >= 0)
        val opening = source.indexOf('{', declaration + signature.length)
        assertTrue("Missing body: $signature", opening >= 0)
        var depth = 1
        var index = opening + 1
        while (index < source.length && depth > 0) {
            when (source[index]) {
                '{' -> depth++
                '}' -> depth--
            }
            index++
        }
        assertEquals("Unbalanced body: $signature", 0, depth)
        return source.substring(opening + 1, index - 1)
    }

    private fun compact(text: String): String = text.filterNot(Char::isWhitespace)
}

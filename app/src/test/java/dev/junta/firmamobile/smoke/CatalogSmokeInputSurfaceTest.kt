package dev.junta.firmamobile.smoke

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test

class CatalogSmokeInputSurfaceTest {
    @Test
    fun `command model has identifiers and operation only`() {
        val inputFields = CatalogSmokeRequest::class.java.declaredFields
            .map { it.name }
            .filterNot { it.startsWith("$") }
            .toSet()

        assertEquals(setOf("runId", "portalId", "operation", "profileId"), inputFields)
        inputFields.forEach { name ->
            val lower = name.lowercase()
            assertFalse(lower.contains("url"))
            assertFalse(lower.contains("script"))
            assertFalse(lower.contains("selector"))
            assertFalse(lower.contains("payload"))
            assertFalse(lower.contains("certificate"))
            assertFalse(lower.contains("password"))
        }
    }
}

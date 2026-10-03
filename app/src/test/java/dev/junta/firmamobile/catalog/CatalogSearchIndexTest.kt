package dev.junta.firmamobile.catalog

import java.util.concurrent.Callable
import java.util.concurrent.Executors
import org.junit.Assert.*
import org.junit.Test

class CatalogSearchIndexTest {
    private fun row(id: String, region: Int = 0, name: String = id, vararg fields: String) =
        CatalogSearchIndex.Row(id, region, name, listOf(name) + fields, id)

    @Test fun searchNormalizesPublicFieldsOnceAndTheQueryOnlyOncePerLookup() {
        var normalizations = 0; var comparisons = 0
        val index = CatalogSearchIndex((1..183).map { row("id-$it", it % 20, "Árbol $it", "Cádiz", "Firma") },
            normalize = { normalizations++; normalizeCatalogSearch(it) },
            compareNames = Comparator { a, b -> comparisons++; a.compareTo(b) })
        val precomputed = normalizations; val sorted = comparisons
        assertEquals(183 * 3, precomputed); assertTrue(sorted > 0)
        repeat(100) { assertEquals(183, index.find("  CADIZ  ", it % 20).size) }
        assertEquals(precomputed + 100, normalizations)
        assertEquals("No collation sort while typing or changing region", sorted, comparisons)
    }

    @Test fun regionPriorityNameAndTieBreakOrderMatchTheExistingContract() {
        val index = CatalogSearchIndex(listOf(row("b", 2, "Árbol"), row("a", 2, "arbol"), row("x", 1, "Zorro"), row("national", 0, "Final")))
        assertEquals(listOf("national", "x", "a", "b"), index.find("", null))
        assertEquals(listOf("a", "b", "national", "x"), index.find("", 2))
        assertEquals(listOf("national", "x", "a", "b"), index.find("", 99))
        assertEquals(listOf("a", "b"), index.find("ÁRBOL", 0))
    }

    @Test fun aliasAndMetadataFieldsAreSearchableButFieldsDoNotMergeIntoOneMatch() {
        val index = CatalogSearchIndex(listOf(row("one", 0, "Perfil interno", "Sede pública", "Universidad Cádiz")))
        assertEquals(listOf("one"), index.find("SEDE PUBLICA", null))
        assertEquals(listOf("one"), index.find("cadiz", null))
        assertTrue(index.find("interno sede", null).isEmpty())
    }

    @Test fun publicIndexDoesNotCacheQueryResultsOrMutableFilterState() {
        val index = CatalogSearchIndex(listOf(row("one"), row("two")))
        var allowed = setOf("one")
        assertEquals(listOf("one"), index.find("", null) { it in allowed })
        allowed = setOf("two")
        assertEquals(listOf("two"), index.find("", null) { it in allowed })
        assertEquals(listOf("one", "two"), index.find("", null))
    }

    @Test fun constructionSnapshotsSearchFieldsWithoutGrantingMutableCallerChanges() {
        val fields = mutableListOf("Original")
        val rows = mutableListOf(CatalogSearchIndex.Row("one", 0, "one", fields, "one"))
        val index = CatalogSearchIndex(rows)
        fields[0] = "Modified"; rows.clear()
        assertEquals(listOf("one"), index.find("original", null))
        assertTrue(index.find("modified", null).isEmpty())
    }

    @Test fun duplicateIdsAreRejectedAndEmptyIndexesStayEmpty() {
        assertThrows(IllegalArgumentException::class.java) { CatalogSearchIndex(listOf(row("same"), row("same", 1))) }
        val empty = CatalogSearchIndex<String>(emptyList())
        assertTrue(empty.find("", null).isEmpty()); assertTrue(empty.find("query", 12).isEmpty())
    }

    @Test fun repeatedConcurrentLookupsNeverShareAMutableCollatorOrResultList() {
        val index = CatalogSearchIndex((0 until 183).map { row("id-$it", it % 20, "Nombre $it", "Firma") })
        val expected = (0..19).associateWith { index.find("firma", it) }
        val pool = Executors.newFixedThreadPool(4)
        try {
            val tasks = (0 until 160).map { n -> Callable { index.find("firma", n % 20) == expected[n % 20] } }
            assertTrue(pool.invokeAll(tasks).all { it.get() })
        } finally { pool.shutdownNow() }
    }

    @Test fun combiningMarksAndLocaleIndependentCaseArePreserved() {
        val index = CatalogSearchIndex(listOf(row("es", 0, "Renovación Cádiz"), row("composed", 0, "Cata\u0301logo")))
        assertEquals(listOf("es"), index.find("RENOVACION CADIZ", null))
        assertEquals(listOf("composed"), index.find("catalogo", null))
    }
}

package dev.junta.firmamobile.catalog

import java.text.Collator
import java.text.Normalizer
import java.util.Collections
import java.util.Locale
import java.util.concurrent.ConcurrentHashMap

/** Immutable public presentation index. It never grants navigation/signing
 * authority and never caches a user's query string or browsing addresses. */
internal class CatalogSearchIndex<T>(
    rows: List<Row<T>>,
    private val normalize: (String) -> String = ::normalizeCatalogSearch,
    compareNames: Comparator<String> = spanishNameComparator(),
) {
    class Row<T>(val id: String, val region: Int, val name: String, val fields: List<String>, val value: T)
    private class Indexed<T>(val id: String, val region: Int, val name: String, val fields: List<String>, val value: T)

    private val alphabetical = rows.map { row ->
        Indexed(row.id, row.region, row.name, row.fields.map(normalize), row.value)
    }.also { require(it.map { row -> row.id }.toSet().size == it.size) }
        .sortedWith { left, right ->
            val name = compareNames.compare(left.name, right.name)
            if (name != 0) name else left.id.compareTo(right.id)
        }
    private val groups = alphabetical.groupBy { it.region }
    private val regions = groups.keys.sorted()
    // Only a bounded set of public catalog region IDs can be retained here.
    private val orders = ConcurrentHashMap<Int, List<Indexed<T>>>()

    fun find(search: String, selectedRegion: Int?, accepts: (T) -> Boolean = { true }): List<T> {
        val needle = normalize(search)
        val selected = selectedRegion?.takeIf { it != 0 && it in groups } ?: 0
        val ordered = orders.getOrPut(selected) {
            val regionOrder = if (selected == 0) regions else listOf(selected) + regions.filterNot { it == selected }
            Collections.unmodifiableList(regionOrder.flatMap { groups.getValue(it) })
        }
        return ordered.asSequence()
            .filter { accepts(it.value) && (needle.isEmpty() || it.fields.any { field -> needle in field }) }
            .map { it.value }.toList()
    }

    companion object {
        private fun spanishNameComparator(): Comparator<String> {
            val collator = Collator.getInstance(Locale.forLanguageTag("es-ES")).apply { strength = Collator.PRIMARY }
            // Used only during construction; never shared by concurrent lookups.
            return Comparator(collator::compare)
        }
    }
}

private val catalogCombiningMarks = Regex("\\p{M}+")
internal fun normalizeCatalogSearch(value: String): String = Normalizer.normalize(value, Normalizer.Form.NFD)
    .replace(catalogCombiningMarks, "").lowercase(Locale.ROOT).trim()

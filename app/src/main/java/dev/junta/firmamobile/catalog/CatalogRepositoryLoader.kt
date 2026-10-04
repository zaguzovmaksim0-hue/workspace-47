package dev.junta.firmamobile.catalog

import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/** Process-local cache of immutable bundled public metadata. Failed loads can retry. */
internal class CatalogRepositoryLoader<T : Any>(private val load: suspend () -> T) {
    private val mutex = Mutex()
    private var cached: T? = null

    suspend fun get(): T = mutex.withLock {
        cached ?: load().also { cached = it }
    }
}

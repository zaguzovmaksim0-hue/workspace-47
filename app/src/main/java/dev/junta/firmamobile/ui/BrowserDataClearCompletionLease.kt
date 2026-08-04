package dev.junta.firmamobile.ui

import java.util.concurrent.atomic.AtomicReference

internal class BrowserDataClearCompletionLease<T> {
    internal class Request<T> internal constructor(
        val owner: T,
    )

    private val activeRequest = AtomicReference<Request<T>?>(null)

    fun begin(owner: T): Request<T> {
        val request = Request(owner)
        activeRequest.set(request)
        return request
    }

    fun consume(request: Request<T>): Boolean =
        activeRequest.compareAndSet(request, null)

    fun invalidate() {
        activeRequest.set(null)
    }
}

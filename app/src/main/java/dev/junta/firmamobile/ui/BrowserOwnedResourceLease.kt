package dev.junta.firmamobile.ui

import java.util.concurrent.atomic.AtomicReference

internal class BrowserOwnedResourceLease<Owner : Any, Resource : AutoCloseable> : AutoCloseable {
    private val active = AtomicReference<Binding<Owner, Resource>?>(null)

    fun bind(owner: Owner, resource: Resource) {
        val previous = active.getAndSet(Binding(owner, resource))
        if (previous?.resource !== resource) previous?.resource?.close()
    }

    fun current(): Resource? = active.get()?.resource

    fun release(owner: Owner): Boolean {
        while (true) {
            val binding = active.get() ?: return false
            if (binding.owner !== owner) return false
            if (active.compareAndSet(binding, null)) {
                binding.resource.close()
                return true
            }
        }
    }

    override fun close() {
        active.getAndSet(null)?.resource?.close()
    }

    private class Binding<Owner : Any, Resource : AutoCloseable>(
        val owner: Owner,
        val resource: Resource,
    )
}

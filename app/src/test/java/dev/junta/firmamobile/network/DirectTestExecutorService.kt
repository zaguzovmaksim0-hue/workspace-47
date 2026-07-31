package dev.junta.firmamobile.network

import java.util.concurrent.AbstractExecutorService
import java.util.concurrent.RejectedExecutionException
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger

/** Executes submitted DNS work inline so JVM tests never share process executor state. */
internal class DirectTestExecutorService : AbstractExecutorService() {
    val submissions = AtomicInteger(0)
    private val stopped = AtomicBoolean(false)

    override fun execute(command: Runnable) {
        if (stopped.get()) throw RejectedExecutionException("executor stopped")
        submissions.incrementAndGet()
        command.run()
    }

    override fun shutdown() {
        stopped.set(true)
    }

    override fun shutdownNow(): MutableList<Runnable> {
        stopped.set(true)
        return mutableListOf()
    }

    override fun isShutdown(): Boolean = stopped.get()

    override fun isTerminated(): Boolean = stopped.get()

    override fun awaitTermination(timeout: Long, unit: TimeUnit): Boolean = stopped.get()
}

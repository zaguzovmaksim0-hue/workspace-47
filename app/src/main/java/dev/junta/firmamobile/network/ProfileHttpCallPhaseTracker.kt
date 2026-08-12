package dev.junta.firmamobile.network

import java.net.InetSocketAddress
import java.net.Proxy
import java.util.concurrent.atomic.AtomicReference
import okhttp3.Call
import okhttp3.EventListener

internal class ProfileHttpCallPhaseTracker : EventListener() {
    private val phase = AtomicReference(ProfileHttpFailurePhase.UNKNOWN)

    override fun connectStart(call: Call, address: InetSocketAddress, proxy: Proxy) {
        advanceTo(ProfileHttpFailurePhase.TCP_BEFORE_HTTP_BYTES)
    }

    override fun secureConnectStart(call: Call) {
        advanceTo(ProfileHttpFailurePhase.TLS_BEFORE_HTTP_BYTES)
    }

    override fun requestHeadersStart(call: Call) {
        advanceTo(ProfileHttpFailurePhase.HTTP_WRITE_STARTED)
    }

    override fun responseHeadersStart(call: Call) {
        advanceTo(ProfileHttpFailurePhase.READ_AFTER_HTTP_WRITE)
    }

    fun dnsFailure(code: ProfileHttpFailure): ProfileHttpFailureDetail = detailFor(code)

    fun failure(code: ProfileHttpFailure): ProfileHttpFailureDetail = detailFor(code)

    internal fun beginDns() {
        advanceTo(ProfileHttpFailurePhase.DNS_BEFORE_CONNECT)
    }

    internal fun responseHeadersObserved() {
        advanceTo(ProfileHttpFailurePhase.READ_AFTER_HTTP_WRITE)
    }

    private fun detailFor(code: ProfileHttpFailure): ProfileHttpFailureDetail {
        val observed = phase.get()
        return ProfileHttpFailureDetail(
            code = code,
            phase = observed,
            httpWriteStarted = observed !in SAFE_PRE_WRITE_PHASES,
        )
    }

    private fun advanceTo(next: ProfileHttpFailurePhase) {
        while (true) {
            val current = phase.get()
            if (current == ProfileHttpFailurePhase.READ_AFTER_HTTP_WRITE || current == next) return
            if (current != ProfileHttpFailurePhase.UNKNOWN && current.ordinal > next.ordinal) return
            if (phase.compareAndSet(current, next)) return
        }
    }

    private companion object {
        val SAFE_PRE_WRITE_PHASES = setOf(
            ProfileHttpFailurePhase.DNS_BEFORE_CONNECT,
            ProfileHttpFailurePhase.TCP_BEFORE_HTTP_BYTES,
            ProfileHttpFailurePhase.TLS_BEFORE_HTTP_BYTES,
        )
    }
}

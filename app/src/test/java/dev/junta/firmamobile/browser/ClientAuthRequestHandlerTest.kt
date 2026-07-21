package dev.junta.firmamobile.browser

import android.webkit.ClientCertRequest
import dev.junta.firmamobile.profile.BuiltInSiteProfiles
import dev.junta.firmamobile.profile.ProfileId
import dev.junta.firmamobile.signing.nonExportableSyntheticIdentity
import java.security.Principal
import java.security.PrivateKey
import java.security.cert.X509Certificate
import java.time.Clock
import java.time.Instant
import java.time.ZoneOffset
import java.util.concurrent.atomic.AtomicInteger
import javax.security.auth.x500.X500Principal
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Test

class ClientAuthRequestHandlerTest {
    private val synthetic = nonExportableSyntheticIdentity()
    private val identity = synthetic.identity
    private val now = identity.summary.validFrom.plusSeconds(60)
    private val clock = Clock.fixed(now, ZoneOffset.UTC)

    @Test
    fun exactEmptyIssuerRequestProceedsOnceWithoutExportingThePrivateKey() {
        val clears = AtomicInteger()
        val handler = handler(epoch = 9, clears = clears)
        val first = RecordingRequest()

        handler.handle(first)

        assertEquals(1, first.proceeds)
        assertEquals(0, first.ignores)
        assertNotNull(first.privateKey)
        assertEquals(0, synthetic.encodedReads.get())
        assertEquals(identity.chain.size, first.chain?.size)
        assertEquals(0, clears.get())

        handler.abandon()
        assertEquals(1, clears.get())

        handler.abandon()
        assertEquals(1, clears.get())

        val replay = RecordingRequest()
        handler.handle(replay)
        assertEquals(0, replay.proceeds)
        assertEquals(1, replay.ignores)
        assertEquals(1, clears.get())
    }

    @Test
    fun wrongHostPortKeyTypeIssuerEpochAndExpiryAlwaysIgnore() {
        val cases = listOf(
            RecordingRequest(host = "ws235.juntadeandalucia.es.evil.example"),
            RecordingRequest(port = 8443),
            RecordingRequest(keyTypes = arrayOf("EC")),
            RecordingRequest(principals = arrayOf(X500Principal("CN=Unexpected CA"))),
        )
        cases.forEach { request ->
            val clears = AtomicInteger()
            handler(epoch = 9, clears = clears).handle(request)
            assertEquals(0, request.proceeds)
            assertEquals(1, request.ignores)
            assertEquals(1, clears.get())
        }

        val wrongEpoch = RecordingRequest()
        val wrongEpochClears = AtomicInteger()
        handler(epoch = 10, currentEpoch = { 11 }, clears = wrongEpochClears).handle(wrongEpoch)
        assertEquals(1, wrongEpoch.ignores)
        assertEquals(1, wrongEpochClears.get())

        val expired = RecordingRequest()
        val expiredClears = AtomicInteger()
        val expiredClock = Clock.fixed(now.plusSeconds(16), ZoneOffset.UTC)
        handler(epoch = 12, clock = expiredClock, clears = expiredClears).handle(expired)
        assertEquals(1, expired.ignores)
        assertEquals(1, expiredClears.get())
    }

    @Test
    fun proceedExceptionDoesNotIssueASecondTerminalDecisionAndCleanupRunsOnce() {
        val clears = AtomicInteger()
        val handler = handler(epoch = 14, clears = clears)
        val request = RecordingRequest(throwAfterProceed = true)

        handler.handle(request)
        assertEquals(1, clears.get())

        handler.abandon()

        assertEquals(1, request.proceeds)
        assertEquals(0, request.ignores)
        assertEquals(1, clears.get())
    }

    @Test
    fun abandonAndMissingIdentityAreTerminalAndClearPreferencesExactlyOnce() {
        val abandonedClears = AtomicInteger()
        val abandoned = handler(epoch = 20, clears = abandonedClears)
        abandoned.abandon()
        abandoned.abandon()
        val afterAbandon = RecordingRequest()
        abandoned.handle(afterAbandon)
        assertEquals(1, afterAbandon.ignores)
        assertEquals(1, abandonedClears.get())

        val missingClears = AtomicInteger()
        val missing = ClientAuthRequestHandler(
            grant = ClientAuthGrant(authorized(), 21),
            identityProvider = { null },
            currentNavigationEpoch = { 21 },
            clearClientCertPreferences = { missingClears.incrementAndGet() },
            clock = clock,
        )
        val missingRequest = RecordingRequest()
        missing.handle(missingRequest)
        missing.abandon()
        assertEquals(1, missingRequest.ignores)
        assertEquals(1, missingClears.get())
    }

    private fun handler(
        epoch: Long,
        currentEpoch: () -> Long = { epoch },
        clears: AtomicInteger = AtomicInteger(),
        clock: Clock = this.clock,
    ) = ClientAuthRequestHandler(
        grant = ClientAuthGrant(authorized(), epoch),
        identityProvider = { identity },
        currentNavigationEpoch = currentEpoch,
        clearClientCertPreferences = { clears.incrementAndGet() },
        clock = clock,
    )

    private fun authorized(): AuthorizedClientAuthTarget {
        val authorizer = ClientAuthNavigationAuthorizer(BuiltInSiteProfiles.qaRegistry, clock)
        authorizer.observeTopLevelNavigation(PROFILE, INDEX, SOURCE, 4, true)
        authorizer.onTopLevelPageStarted(SOURCE, 5)
        return checkNotNull(
            authorizer.observeTopLevelNavigation(PROFILE, SOURCE, TARGET, 5, true),
        )
    }

    private class RecordingRequest(
        private val host: String = "ws235.juntadeandalucia.es",
        private val port: Int = 443,
        private val keyTypes: Array<String> = arrayOf("RSA", "EC"),
        private val principals: Array<Principal> = emptyArray(),
        private val throwAfterProceed: Boolean = false,
    ) : ClientCertRequest() {
        var proceeds = 0
        var ignores = 0
        var cancels = 0
        var privateKey: PrivateKey? = null
        var chain: Array<X509Certificate>? = null

        override fun getHost(): String = host
        override fun getPort(): Int = port
        override fun getKeyTypes(): Array<String> = keyTypes
        override fun getPrincipals(): Array<Principal> = principals
        override fun proceed(privateKey: PrivateKey, chain: Array<X509Certificate>) {
            proceeds++
            this.privateKey = privateKey
            this.chain = chain
            if (throwAfterProceed) error("synthetic proceed failure")
        }
        override fun ignore() {
            ignores++
        }
        override fun cancel() {
            cancels++
        }
    }

    private companion object {
        val PROFILE = ProfileId("carne-joven-andalucia")
        const val INDEX = "https://ws104.juntadeandalucia.es/carneJoven/cjservlet/portal/index.jsp"
        const val SOURCE =
            "https://ws104.juntadeandalucia.es/carneJoven/servlet/CallAuthenticationServlet"
        const val TARGET =
            "https://ws235.juntadeandalucia.es/authenticationFacade?action=validateCert&ticketId=synthetic-ticket&appId=IAJ.CARNETJOVEN&webSessionId=synthetic-session&comeBackURL=aHR0cHM6Ly93czEwNC5qdW50YWRlYW5kYWx1Y2lhLmVzL2Nhcm5lSm92ZW4vc2VydmxldC9SZXR1cm5BdXRoZW50aWNhdGlvblNlcnZsZXQ%3D"
    }
}

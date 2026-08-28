package dev.junta.firmamobile.browser

import android.webkit.ClientCertRequest
import dev.junta.firmamobile.profile.BuiltInSiteProfiles
import dev.junta.firmamobile.profile.BuildTrustPolicy
import dev.junta.firmamobile.profile.ProfileId
import dev.junta.firmamobile.certificate.UnlockedIdentity
import dev.junta.firmamobile.signing.issuedSyntheticIdentity
import dev.junta.firmamobile.signing.nonExportableSyntheticIdentity
import java.security.Principal
import java.security.PrivateKey
import java.security.cert.X509Certificate
import java.time.Clock
import java.time.Duration
import java.time.Instant
import java.time.ZoneId
import java.time.ZoneOffset
import java.util.concurrent.atomic.AtomicInteger
import javax.security.auth.x500.X500Principal
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNotNull
import org.junit.Test

class ClientAuthRequestHandlerTest {
    private val synthetic = nonExportableSyntheticIdentity()
    private val identity = synthetic.identity
    private val now = identity.summary.validFrom.plusSeconds(60)
    private val clock = Clock.fixed(now, ZoneOffset.UTC)
    private val monotonic = MutableMonotonicClock(1_000_000_000L)

    @Test
    fun aeatMatchingRsaIdentityAndIssuerProceedOnce() {
        val clears = AtomicInteger()
        val handler = aeatHandler(epoch = 30, clears = clears)
        val issuer = identity.chain.firstOrNull()?.issuerX500Principal
            ?: identity.certificate.issuerX500Principal
        val request = RecordingRequest(
            host = "www1.agenciatributaria.gob.es",
            keyTypes = arrayOf("rsa", "ECDSA"),
            principals = arrayOf(issuer),
        )

        handler.handle(request)

        assertEquals(1, request.proceeds)
        assertEquals(0, request.ignores)
        assertEquals(0, clears.get())
        assertEquals(0, synthetic.encodedReads.get())
    }

    @Test
    fun valladolidClientCertificateRequestProceedsOnlyOnTheObservedPort() {
        val exact = RecordingRequest(
            host = "www.sede.diputaciondevalladolid.es",
            port = 21460,
            keyTypes = arrayOf("RSA"),
            principals = emptyArray(),
        )
        valladolidHandler(epoch = 33).handle(exact)
        assertEquals(1, exact.proceeds)
        assertEquals(0, exact.ignores)

        listOf(443, 21461).forEach { wrongPort ->
            val rejected = RecordingRequest(
                host = "www.sede.diputaciondevalladolid.es",
                port = wrongPort,
                keyTypes = arrayOf("RSA"),
                principals = emptyArray(),
            )
            valladolidHandler(epoch = 34).handle(rejected)
            assertEquals(0, rejected.proceeds)
            assertEquals(1, rejected.ignores)
        }
    }

    @Test
    fun aeatLeafSubjectIsNotAcceptedAsAnIssuer() {
        val issuedIdentity = issuedSyntheticIdentity()
        assertNotEquals(
            issuedIdentity.certificate.subjectX500Principal,
            issuedIdentity.certificate.issuerX500Principal,
        )
        val clears = AtomicInteger()
        val handler = aeatHandler(epoch = 32, clears = clears, identity = issuedIdentity)
        val request = RecordingRequest(
            host = "www1.agenciatributaria.gob.es",
            keyTypes = arrayOf("RSA"),
            principals = arrayOf(issuedIdentity.certificate.subjectX500Principal),
        )

        handler.handle(request)

        assertEquals(0, request.proceeds)
        assertEquals(1, request.ignores)
        assertEquals(1, clears.get())
    }

    @Test
    fun aeatEmptyIssuerListFailsClosedAndClearsPreferences() {
        val clears = AtomicInteger()
        val handler = aeatHandler(epoch = 31, clears = clears)
        val request = RecordingRequest(
            host = "www1.agenciatributaria.gob.es",
            keyTypes = arrayOf("RSA"),
            principals = emptyArray(),
        )

        handler.handle(request)

        assertEquals(0, request.proceeds)
        assertEquals(1, request.ignores)
        assertEquals(1, clears.get())
    }

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
        val expiredMonotonic = MutableMonotonicClock(2_000_000_000L)
        val expiredHandler = handler(
            epoch = 12,
            clears = expiredClears,
            monotonic = expiredMonotonic,
        )
        expiredMonotonic.advance(Duration.ofSeconds(16))
        expiredHandler.handle(expired)
        assertEquals(1, expired.ignores)
        assertEquals(1, expiredClears.get())
    }

    @Test
    fun veaUsesServerTlsValidationForOfferedKeyTypeCompatibility() {
        val profile = BuiltInSiteProfiles.catalog.profiles.single {
            it.profileId == ProfileId("junta-andalucia-vea-peg")
        }
        val policy = checkNotNull(profile.clientAuthPolicy)
        assertEquals(false, policy.requireOfferedKeyTypeMatch)
        assertEquals(false, policy.requireTlsClientAuthExtendedKeyUsage)
        val authorized = AuthorizedClientAuthTarget(
            profileId = profile.profileId,
            target = java.net.URI(
                "https://ws235.juntadeandalucia.es/authenticationFacade?action=validateCert&appId=CHIE.VEA&comeBackURL=aHR0cHM6Ly9hcGktdmVhamEuY2xvdWQuanVudGFkZWFuZGFsdWNpYS5lcy9hdXRoL3JldHVybkxvZ2lu&ticketId=synthetic-ticket&webSessionId=synthetic-session",
            ),
            policy = policy,
            certificateRules = profile.certificateRules,
            observedAtMonotonicNanos = monotonic.nowNanos(),
            lifetimeNanos = Duration.ofSeconds(policy.grantTtlSeconds.toLong()).toNanos(),
        )
        val diagnostics = mutableListOf<ClientAuthRequestDiagnostic>()
        val request = RecordingRequest(keyTypes = arrayOf("UNRECOGNIZED_BY_LOCAL_POLICY"))
        ClientAuthRequestHandler(
            grant = ClientAuthGrant(authorized, 43),
            identityProvider = { identity },
            currentNavigationEpoch = { 43 },
            clearClientCertPreferences = {},
            onDiagnostic = diagnostics::add,
            clock = clock,
            monotonicNanos = monotonic::nowNanos,
        ).handle(request)

        assertEquals(1, request.proceeds)
        assertEquals(0, request.ignores)
        assertEquals(ClientAuthRequestDiagnostic.PROCEEDED, diagnostics.last())
    }

    @Test
    fun explicitUserConfirmationRefreshesOnlyTheShortTlsWindow() {
        val localMonotonic = MutableMonotonicClock(4_000_000_000L)
        val old = shortTtlAuthorized(monotonic = localMonotonic, ttlSeconds = 1)
        localMonotonic.advance(Duration.ofSeconds(10))
        val refreshed = old.refreshedAfterUserConfirmation(localMonotonic.nowNanos())
        val diagnostics = mutableListOf<ClientAuthRequestDiagnostic>()
        val handler = ClientAuthRequestHandler(
            grant = ClientAuthGrant(refreshed, 44),
            identityProvider = { identity },
            currentNavigationEpoch = { 44 },
            clearClientCertPreferences = {},
            onDiagnostic = diagnostics::add,
            clock = clock,
            monotonicNanos = localMonotonic::nowNanos,
        )

        val request = RecordingRequest()
        handler.handle(request)

        assertEquals(1, request.proceeds)
        assertEquals(
            listOf(
                ClientAuthRequestDiagnostic.CHALLENGE_RECEIVED,
                ClientAuthRequestDiagnostic.PROCEEDED,
            ),
            diagnostics,
        )
    }

    @Test
    fun civilClockRollbackCannotExtendAcceptedClientAuthGrantTtl() {
        val mutableClock = MutableClock(now)
        val localMonotonic = MutableMonotonicClock(3_000_000_000L)
        val grant = ClientAuthGrant(
            shortTtlAuthorized(monotonic = localMonotonic, ttlSeconds = 1),
            13,
        )
        val clears = AtomicInteger()
        val handler = ClientAuthRequestHandler(
            grant = grant,
            identityProvider = { identity },
            currentNavigationEpoch = { 13 },
            clearClientCertPreferences = { clears.incrementAndGet() },
            clock = mutableClock,
            monotonicNanos = localMonotonic::nowNanos,
        )

        mutableClock.rewind(Duration.ofSeconds(30))
        localMonotonic.advance(Duration.ofSeconds(1))

        val request = RecordingRequest()
        handler.handle(request)

        assertEquals(0, request.proceeds)
        assertEquals(1, request.ignores)
        assertEquals(1, clears.get())
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
            grant = ClientAuthGrant(authorized(monotonic), 21),
            identityProvider = { null },
            currentNavigationEpoch = { 21 },
            clearClientCertPreferences = { missingClears.incrementAndGet() },
            clock = clock,
            monotonicNanos = monotonic::nowNanos,
        )
        val missingRequest = RecordingRequest()
        missing.handle(missingRequest)
        missing.abandon()
        assertEquals(1, missingRequest.ignores)
        assertEquals(1, missingClears.get())
    }

    private fun shortTtlAuthorized(
        monotonic: MutableMonotonicClock,
        ttlSeconds: Int,
    ): AuthorizedClientAuthTarget {
        val base = BuiltInSiteProfiles.catalog.profiles.single { it.profileId == PROFILE }
        val profile = base.copy(
            clientAuthPolicy = checkNotNull(base.clientAuthPolicy).copy(
                grantTtlSeconds = ttlSeconds,
            ),
        )
        val registry = dev.junta.firmamobile.profile.SiteProfileRegistry(
            BuiltInSiteProfiles.catalog.copy(profiles = listOf(profile)),
            BuildTrustPolicy.QA,
        )
        val authorizer = ClientAuthNavigationAuthorizer(registry, monotonic::nowNanos)
        authorizer.observeTopLevelNavigation(PROFILE, INDEX, SOURCE, 4, true)
        authorizer.onTopLevelPageStarted(SOURCE, 5)
        return checkNotNull(
            authorizer.observeTopLevelNavigation(PROFILE, SOURCE, TARGET, 5, true),
        )
    }

    private fun valladolidHandler(
        epoch: Long,
        currentEpoch: () -> Long = { epoch },
        clears: AtomicInteger = AtomicInteger(),
    ) = ClientAuthRequestHandler(
        grant = ClientAuthGrant(authorizedValladolid(), epoch),
        identityProvider = { identity },
        currentNavigationEpoch = currentEpoch,
        clearClientCertPreferences = { clears.incrementAndGet() },
        clock = clock,
        monotonicNanos = monotonic::nowNanos,
    )

    private fun authorizedValladolid(): AuthorizedClientAuthTarget {
        val authorizer = ClientAuthNavigationAuthorizer(BuiltInSiteProfiles.qaRegistry, monotonic::nowNanos)
        authorizer.observeTopLevelNavigation(
            VALLADOLID_PROFILE, VALLADOLID_INDEX, VALLADOLID_SOURCE, 40, true,
        )
        authorizer.onTopLevelPageStarted(VALLADOLID_SOURCE, 41)
        return checkNotNull(
            authorizer.observeTopLevelNavigation(
                VALLADOLID_PROFILE, VALLADOLID_SOURCE, VALLADOLID_TARGET, 41, true,
            ),
        )
    }

    private fun aeatHandler(
        epoch: Long,
        currentEpoch: () -> Long = { epoch },
        clears: AtomicInteger = AtomicInteger(),
        clock: Clock = this.clock,
        identity: UnlockedIdentity = this.identity,
        monotonic: MutableMonotonicClock = this.monotonic,
    ) = ClientAuthRequestHandler(
        grant = ClientAuthGrant(authorizedAeat(monotonic), epoch),
        identityProvider = { identity },
        currentNavigationEpoch = currentEpoch,
        clearClientCertPreferences = { clears.incrementAndGet() },
        clock = clock,
        monotonicNanos = monotonic::nowNanos,
    )

    private fun authorizedAeat(monotonic: MutableMonotonicClock = this.monotonic): AuthorizedClientAuthTarget {
        val authorizer = ClientAuthNavigationAuthorizer(
            BuiltInSiteProfiles.qaRegistry,
            monotonic::nowNanos,
        )
        return checkNotNull(
            authorizer.observeTopLevelNavigation(
                AEAT_PROFILE,
                AEAT_SOURCE,
                AEAT_TARGET,
                5,
                true,
            ),
        )
    }

    private fun handler(
        epoch: Long,
        currentEpoch: () -> Long = { epoch },
        clears: AtomicInteger = AtomicInteger(),
        clock: Clock = this.clock,
        monotonic: MutableMonotonicClock = this.monotonic,
    ) = ClientAuthRequestHandler(
        grant = ClientAuthGrant(authorized(monotonic), epoch),
        identityProvider = { identity },
        currentNavigationEpoch = currentEpoch,
        clearClientCertPreferences = { clears.incrementAndGet() },
        clock = clock,
        monotonicNanos = monotonic::nowNanos,
    )

    private fun authorized(monotonic: MutableMonotonicClock = this.monotonic): AuthorizedClientAuthTarget {
        val authorizer = ClientAuthNavigationAuthorizer(
            BuiltInSiteProfiles.qaRegistry,
            monotonic::nowNanos,
        )
        authorizer.observeTopLevelNavigation(PROFILE, INDEX, SOURCE, 4, true)
        authorizer.onTopLevelPageStarted(SOURCE, 5)
        return checkNotNull(
            authorizer.observeTopLevelNavigation(PROFILE, SOURCE, TARGET, 5, true),
        )
    }

    private class MutableMonotonicClock(private var nanos: Long) {
        fun nowNanos(): Long = nanos

        fun advance(duration: Duration) {
            nanos += duration.toNanos()
        }
    }

    private class MutableClock(private var instant: Instant) : Clock() {
        override fun getZone(): ZoneId = ZoneOffset.UTC
        override fun withZone(zone: ZoneId): Clock = this
        override fun instant(): Instant = instant

        fun rewind(duration: Duration) {
            instant = instant.minus(duration)
        }
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
        val VALLADOLID_PROFILE = ProfileId("diputacion-valladolid-sede")
        const val VALLADOLID_INDEX = "https://www.sede.diputaciondevalladolid.es/tgauth/login"
        const val VALLADOLID_SOURCE = "https://www.sede.diputaciondevalladolid.es/c/portal/cert-login"
        const val VALLADOLID_TARGET =
            "https://www.sede.diputaciondevalladolid.es:21460/c/portal/cert-login"
        val PROFILE = ProfileId("carne-joven-andalucia")
        val AEAT_PROFILE = ProfileId("aeat-mis-datos-censales")
        const val AEAT_SOURCE =
            "https://sede.agenciatributaria.gob.es/Sede/mi-area-personal.html"
        const val AEAT_TARGET =
            "https://www1.agenciatributaria.gob.es/wlpl/BUGC-JDIT/MdcAcceso"
        const val INDEX = "https://ws104.juntadeandalucia.es/carneJoven/cjservlet/portal/index.jsp"
        const val SOURCE =
            "https://ws104.juntadeandalucia.es/carneJoven/servlet/CallAuthenticationServlet"
        const val TARGET =
            "https://ws235.juntadeandalucia.es/authenticationFacade?action=validateCert&ticketId=synthetic-ticket&appId=IAJ.CARNETJOVEN&webSessionId=synthetic-session&comeBackURL=aHR0cHM6Ly93czEwNC5qdW50YWRlYW5kYWx1Y2lhLmVzL2Nhcm5lSm92ZW4vc2VydmxldC9SZXR1cm5BdXRoZW50aWNhdGlvblNlcnZsZXQ%3D"
    }
}

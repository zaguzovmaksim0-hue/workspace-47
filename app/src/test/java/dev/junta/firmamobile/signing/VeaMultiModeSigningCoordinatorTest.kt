package dev.junta.firmamobile.signing

import dev.junta.firmamobile.browser.VeaMultiModeBridgeRequest
import dev.junta.firmamobile.certificate.CertificateSession
import dev.junta.firmamobile.certificate.TestCertificateFactory
import dev.junta.firmamobile.certificate.UnlockedIdentity
import dev.junta.firmamobile.network.TrustedOrigin
import dev.junta.firmamobile.profile.BuiltInSiteProfiles
import dev.junta.firmamobile.profile.ProfileId
import java.time.Clock
import java.time.Duration
import java.time.ZoneOffset
import java.util.UUID
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class VeaMultiModeSigningCoordinatorTest {
    private val clock = Clock.fixed(TestCertificateFactory.now, ZoneOffset.UTC)
    private val identity = syntheticIdentity()
    private val session = CertificateSession(clock).apply { unlock(identity) }
    private val origin = TrustedOrigin("https", "veaja.cloud.juntadeandalucia.es", 443)
    private val adapter = VeaMultiModeSigningAdapter()
    private val expiryScheduler = RecordingExpiryScheduler()

    private val coordinator = VeaMultiModeSigningCoordinator(
        certificateSession = session,
        adapter = adapter,
        currentOrigin = { origin },
        currentNavigationEpoch = { 100L },
        expiryScheduler = expiryScheduler,
        profileRegistry = BuiltInSiteProfiles.runtimeRegistry,
    )

    @Test
    fun prepareTransitionsToAwaitingConfirmationWithoutSigning() {
        val requestId = UUID.randomUUID()
        val documentId = UUID.randomUUID()
        val hashBytes = ByteArray(32) { 0x11 }
        val request = VeaMultiModeBridgeRequest(
            requestId = requestId,
            documentId = documentId,
            operationArray = listOf("sign", "sign"),
            dataArray = listOf("11".repeat(32), "11".repeat(32)),
            originalDataArray = null,
            arrayLength = 2,
            algorithm = "SHA256withRSA",
            format = "CADES",
            extraProperties = "mode=explicit\nprecalculatedHashAlgorithm=SHA-256\nfilters=nonexpired:;signingCert;",
            hashAlgorithm = PrecalculatedHashAlgorithm.SHA256,
            hashes = listOf(hashBytes, hashBytes),
            profileId = ProfileId("junta-andalucia-sede"),
            sourceOrigin = origin,
            navigationEpoch = 100L,
            pageUrl = "https://veaja.cloud.juntadeandalucia.es/inicio/",
        )

        val reply = RecordingVeaReply(requestId)
        val result = coordinator.prepare(request, reply)

        assertEquals(SigningPreparationResult.Ready(requestId), result)
        val state = coordinator.state.value as SigningUiState.AwaitingConfirmation
        assertEquals(requestId, state.requestId)
        assertEquals("veaja.cloud.juntadeandalucia.es", state.siteHost)
        assertEquals("Sede electrónica de la Junta de Andalucía", state.profileName)
        assertEquals("Firma electrónica de 2 documentos", state.safeDescription)
        assertEquals("CADES", state.format)
        assertEquals("SHA256withRSA", state.algorithm)
        assertEquals(identity.summary.ownerName, state.certificateOwner)
        assertFalse(state.requiresLegacySha1Warning)
        assertTrue(reply.events.isEmpty())
    }

    @Test
    fun prepareSetsSha1LegacyWarningWhenSha1Used() {
        val requestId = UUID.randomUUID()
        val documentId = UUID.randomUUID()
        val hashBytes = ByteArray(20) { 0x22 }
        val request = VeaMultiModeBridgeRequest(
            requestId = requestId,
            documentId = documentId,
            operationArray = listOf("sign"),
            dataArray = listOf("22".repeat(20)),
            originalDataArray = null,
            arrayLength = 1,
            algorithm = "SHA1withRSA",
            format = "CADES",
            extraProperties = "mode=explicit\nprecalculatedHashAlgorithm=SHA-1\nfilters=nonexpired:;signingCert;",
            hashAlgorithm = PrecalculatedHashAlgorithm.SHA1,
            hashes = listOf(hashBytes),
            profileId = ProfileId("junta-andalucia-sede"),
            sourceOrigin = origin,
            navigationEpoch = 100L,
            pageUrl = "https://veaja.cloud.juntadeandalucia.es/inicio/",
        )

        val reply = RecordingVeaReply(requestId)
        val result = coordinator.prepare(request, reply)

        assertEquals(SigningPreparationResult.Ready(requestId), result)
        val state = coordinator.state.value as SigningUiState.AwaitingConfirmation
        assertTrue(state.requiresLegacySha1Warning)
        assertEquals("Firma electrónica de 1 documento", state.safeDescription)
    }

    @Test
    fun prepareRejectsWhenCertificateIsLocked() {
        val lockedSession = CertificateSession(clock)
        val lockedCoordinator = VeaMultiModeSigningCoordinator(
            certificateSession = lockedSession,
            adapter = adapter,
            currentOrigin = { origin },
            currentNavigationEpoch = { 100L },
            currentPageUrl = { "https://veaja.cloud.juntadeandalucia.es/inicio/" },
            expiryScheduler = expiryScheduler,
            profileRegistry = BuiltInSiteProfiles.runtimeRegistry,
        )

        val requestId = UUID.randomUUID()
        val request = validRequest(requestId)
        val reply = RecordingVeaReply(requestId)

        val result = lockedCoordinator.prepare(request, reply)
        assertEquals(SigningPreparationResult.Rejected(SigningErrorCode.CERTIFICATE_LOCKED), result)
        assertEquals(listOf("failure:CERTIFICATE_LOCKED"), reply.events)
    }

    @Test
    fun confirmExecutesSigningAndDeliversResultOnlyAfterExplicitConfirmation() = runBlocking {
        val requestId = UUID.randomUUID()
        val request = validRequest(requestId)
        val reply = RecordingVeaReply(requestId)

        coordinator.prepare(request, reply)
        assertTrue(reply.events.isEmpty())

        val confirmResult = coordinator.confirm(requestId)
        assertEquals(SigningExecutionResult.Success, confirmResult)
        assertEquals(1, reply.events.size)
        assertTrue(reply.events[0].startsWith("success:"))
        assertEquals(SigningUiState.Idle, coordinator.state.value)
    }

    @Test
    fun confirmFailsWhenPageUrlChangesBetweenPrepareAndConfirm() = runBlocking {
        var dynamicPageUrl: String? = "https://veaja.cloud.juntadeandalucia.es/borrador/draft-1"
        val dynamicCoordinator = VeaMultiModeSigningCoordinator(
            certificateSession = session,
            adapter = adapter,
            currentOrigin = { origin },
            currentNavigationEpoch = { 100L },
            currentPageUrl = { dynamicPageUrl },
            expiryScheduler = expiryScheduler,
            profileRegistry = BuiltInSiteProfiles.runtimeRegistry,
        )

        val requestId = UUID.randomUUID()
        val request = validRequest(requestId, pageUrl = "https://veaja.cloud.juntadeandalucia.es/borrador/draft-1")
        val reply = RecordingVeaReply(requestId)

        val prepareResult = dynamicCoordinator.prepare(request, reply)
        assertEquals(SigningPreparationResult.Ready(requestId), prepareResult)
        assertTrue(reply.events.isEmpty())

        // SPA route change occurs while awaiting user confirmation
        dynamicPageUrl = "https://veaja.cloud.juntadeandalucia.es/borrador/draft-2"

        val confirmResult = dynamicCoordinator.confirm(requestId)
        assertEquals(SigningExecutionResult.Failed(SigningErrorCode.NAVIGATION_CHANGED), confirmResult)
        assertEquals(listOf("failure:NAVIGATION_CHANGED"), reply.events)
        assertTrue(dynamicCoordinator.state.value is SigningUiState.Failed)
    }

    @Test
    fun confirmFailsWhenDocumentIdChangesBetweenPrepareAndConfirm() = runBlocking {
        val docId1 = UUID.randomUUID()
        var dynamicDocId: UUID? = docId1
        val dynamicCoordinator = VeaMultiModeSigningCoordinator(
            certificateSession = session,
            adapter = adapter,
            currentOrigin = { origin },
            currentNavigationEpoch = { 100L },
            currentPageUrl = { "https://veaja.cloud.juntadeandalucia.es/inicio/" },
            currentDocumentId = { dynamicDocId },
            expiryScheduler = expiryScheduler,
            profileRegistry = BuiltInSiteProfiles.runtimeRegistry,
        )

        val requestId = UUID.randomUUID()
        val request = validRequest(requestId, documentId = docId1)
        val reply = RecordingVeaReply(requestId)

        val prepareResult = dynamicCoordinator.prepare(request, reply)
        assertEquals(SigningPreparationResult.Ready(requestId), prepareResult)
        assertTrue(reply.events.isEmpty())

        // Active document changed before confirm
        dynamicDocId = UUID.randomUUID()

        val confirmResult = dynamicCoordinator.confirm(requestId)
        assertEquals(SigningExecutionResult.Failed(SigningErrorCode.NAVIGATION_CHANGED), confirmResult)
        assertEquals(listOf("failure:NAVIGATION_CHANGED"), reply.events)
    }

    @Test
    fun cancelCleanlyReleasesPendingOperationAndNotifiesReply() {
        val requestId = UUID.randomUUID()
        val request = validRequest(requestId)
        val reply = RecordingVeaReply(requestId)

        coordinator.prepare(request, reply)
        val cancelled = coordinator.cancel(SigningCancelReason.JAVASCRIPT, requestId)

        assertTrue(cancelled)
        assertEquals(listOf("failure:USER_CANCELLED"), reply.events)
        assertTrue(coordinator.state.value is SigningUiState.Failed)
    }

    private fun validRequest(
        requestId: UUID,
        pageUrl: String = "https://veaja.cloud.juntadeandalucia.es/inicio/",
        documentId: UUID = UUID.randomUUID(),
    ): VeaMultiModeBridgeRequest {
        val hashBytes = ByteArray(32) { 0x33 }
        return VeaMultiModeBridgeRequest(
            requestId = requestId,
            documentId = documentId,
            operationArray = listOf("sign"),
            dataArray = listOf("33".repeat(32)),
            originalDataArray = null,
            arrayLength = 1,
            algorithm = "SHA256withRSA",
            format = "CADES",
            extraProperties = "mode=explicit\nprecalculatedHashAlgorithm=SHA-256\nfilters=nonexpired:;signingCert;",
            hashAlgorithm = PrecalculatedHashAlgorithm.SHA256,
            hashes = listOf(hashBytes),
            profileId = ProfileId("junta-andalucia-sede"),
            sourceOrigin = origin,
            navigationEpoch = 100L,
            pageUrl = pageUrl,
        )
    }

    private class RecordingVeaReply(override val requestId: UUID) : VeaMultiModeReplySink {
        val events = mutableListOf<String>()

        override fun success(signaturesB64: String, certificateB64: String): Boolean {
            events.add("success:$signaturesB64")
            return true
        }

        override fun failure(code: SigningErrorCode): Boolean {
            events.add("failure:${code.name}")
            return true
        }

        override fun abandon(): Boolean {
            events.add("abandon")
            return true
        }
    }
}

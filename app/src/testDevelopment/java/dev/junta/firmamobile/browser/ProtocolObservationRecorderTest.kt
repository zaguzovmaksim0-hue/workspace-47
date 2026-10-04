package dev.junta.firmamobile.browser

import android.net.Uri
import dev.junta.firmamobile.security.SanitizedLogger
import java.time.Clock
import java.time.Instant
import java.time.ZoneOffset
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.ConscryptMode
import org.robolectric.annotation.GraphicsMode
import org.robolectric.annotation.SQLiteMode

@RunWith(RobolectricTestRunner::class)
@ConscryptMode(ConscryptMode.Mode.OFF)
@GraphicsMode(GraphicsMode.Mode.LEGACY)
@SQLiteMode(SQLiteMode.Mode.LEGACY)
class ProtocolObservationRecorderTest {
    private val logger = SanitizedLogger(
        Clock.fixed(Instant.parse("2030-01-01T00:00:00Z"), ZoneOffset.UTC),
    )
    private val recorder = ProtocolObservationRecorder(logger)
        .also(::activateDocument)

    @Test
    fun recordsOnlyClosedSafeFields() {
        recorder.record(
            SafeProtocolObservation(
                call = ObservedMiniAppletCall.SIGN,
                originHost = "www.juntadeandalucia.es",
                algorithm = "SHA1withRSA",
                format = "CAdES",
                argumentLengths = listOf(128, 11, 5, 32, 0, 0),
                branch = ObservedRuntimeBranch.AFIRMA,
                correlation = ObservationCorrelation.REQUEST_ID,
            ),
        )

        val output = recorder.exportText()
        assertTrue(output.contains("event=MINIAPPLET_OBSERVED"))
        assertTrue(output.contains("call=SIGN"))
        assertTrue(output.contains("branch=AFIRMA"))
        assertTrue(output.contains("correlation=REQUEST_ID"))
        assertTrue(output.contains("algorithm=SHA1withRSA"))
        assertTrue(output.contains("argument.0.length=128"))
        assertFalse(output.contains(RAW_DAT_CANARY))
    }

    @Test
    fun rejectsUntrustedSubframeOversizedAndUnknownJsonFields() {
        val valid = """{
            "type":"MINIAPPLET_OBSERVATION",
            "documentId":"$DOCUMENT_ID",
            "requestId":"123e4567-e89b-12d3-a456-426614174000",
            "call":"SIGN",
            "algorithm":"SHA256withRSA",
            "format":"CAdES",
            "argumentLengths":[16,13,5]
        }""".trimIndent()

        assertFalse(
            recorder.recordMessage(
                valid,
                Uri.parse("https://evil.example"),
                isMainFrame = true,
            ),
        )
        assertFalse(
            recorder.recordMessage(
                valid,
                TRUSTED_ORIGIN,
                isMainFrame = false,
            ),
        )
        assertFalse(
            recorder.recordMessage(
                valid.dropLast(1) + ",\"dat\":\"$RAW_DAT_CANARY\"}",
                TRUSTED_ORIGIN,
                isMainFrame = true,
            ),
        )
        assertFalse(
            recorder.recordMessage(
                valid.replace("[16,13,5]", "[1048577]"),
                TRUSTED_ORIGIN,
                isMainFrame = true,
            ),
        )

        assertFalse(recorder.exportText().contains(RAW_DAT_CANARY))
    }

    @Test
    fun rejectsFractionalArgumentLengthInsteadOfTruncatingIt() {
        val fractional = signMessage("123e4567-e89b-12d3-a456-426614174005")
            .replace("[37,13,5,0,0,0]", "[1.5]")

        assertFalse(
            recorder.recordMessage(
                fractional,
                TRUSTED_ORIGIN,
                isMainFrame = true,
            ),
        )
        assertFalse(recorder.exportText().contains("argument.0.length=1"))
    }

    @Test
    fun rejectsOversizedArgumentLengthInACleanDocument() {
        val oversized = signMessage("123e4567-e89b-12d3-a456-426614174006")
            .replace("[37,13,5,0,0,0]", "[1048577]")

        assertFalse(recorder.recordMessage(oversized, TRUSTED_ORIGIN, true))
        assertTrue(recorder.exportText().contains("event=PROTOCOL_CORRELATION_REJECTED"))
    }

    @Test
    fun replayedRequestIdPoisonsAnOtherwiseCleanDocument() {
        val requestId = "123e4567-e89b-12d3-a456-426614174007"
        assertTrue(recorder.recordMessage(signMessage(requestId), TRUSTED_ORIGIN, true))
        assertTrue(recorder.recordMessage(callEndMessage(requestId), TRUSTED_ORIGIN, true))

        assertFalse(recorder.recordMessage(signMessage(requestId), TRUSTED_ORIGIN, true))
        assertTrue(recorder.exportText().contains("event=PROTOCOL_CORRELATION_REJECTED"))
    }

    @Test
    fun branchMustReuseTheExactCallRequestIdInsteadOfTheLatestCall() {
        val loadRequestId = "123e4567-e89b-12d3-a456-426614174000"
        val signRequestId = "123e4567-e89b-12d3-a456-426614174001"

        assertTrue(
            recorder.recordMessage(
                miniAppletMessage(loadRequestId, "LOAD", "[48]"),
                TRUSTED_ORIGIN,
                isMainFrame = true,
            ),
        )
        assertTrue(
            recorder.recordMessage(
                miniAppletMessage(signRequestId, "SIGN", "[24,11,5,156,0,0]"),
                TRUSTED_ORIGIN,
                isMainFrame = true,
            ),
        )
        assertTrue(
            recorder.recordMessage(
                branchMessage(loadRequestId, "INTENT"),
                TRUSTED_ORIGIN,
                isMainFrame = true,
            ),
        )
        assertTrue(
            recorder.recordMessage(
                callEndMessage(loadRequestId),
                TRUSTED_ORIGIN,
                isMainFrame = true,
            ),
        )

        val output = recorder.exportText()
        assertTrue(output.contains("call=LOAD branch=INTENT"))
        assertFalse(output.contains("call=SIGN branch=INTENT"))
        assertFalse(output.contains(loadRequestId))
        assertFalse(output.contains(signRequestId))
        assertFalse(output.contains(DOCUMENT_ID))
    }

    @Test
    fun nativeBranchRequiresOneOpenUnexpiredCallAndCallEndClosesTheWindow() {
        var elapsedMillis = 1_000L
        val activeLogger = SanitizedLogger(
            Clock.fixed(Instant.parse("2030-01-01T00:00:00Z"), ZoneOffset.UTC),
        )
        val activeRecorder = ProtocolObservationRecorder(
            logger = activeLogger,
            elapsedRealtimeMillis = { elapsedMillis },
        ).also(::activateDocument)
        val requestId = "123e4567-e89b-12d3-a456-426614174010"
        assertTrue(
            activeRecorder.recordMessage(
                signMessage(requestId),
                TRUSTED_ORIGIN,
                isMainFrame = true,
            ),
        )
        assertTrue(
            activeRecorder.recordNavigationBranch(
                ObservedRuntimeBranch.INTENT,
                "www.juntadeandalucia.es",
                isMainFrame = true,
            ),
        )
        assertTrue(
            activeRecorder.recordMessage(
                callEndMessage(requestId),
                TRUSTED_ORIGIN,
                isMainFrame = true,
            ),
        )
        assertTrue(activeLogger.exportText().contains("correlation=ACTIVE_CALL_WINDOW"))

        val endedRecorder = ProtocolObservationRecorder(
            logger = SanitizedLogger(
                Clock.fixed(Instant.parse("2030-01-01T00:00:00Z"), ZoneOffset.UTC),
            ),
            elapsedRealtimeMillis = { elapsedMillis },
        ).also(::activateDocument)
        val endedRequestId = "123e4567-e89b-12d3-a456-426614174011"
        assertTrue(
            endedRecorder.recordMessage(
                signMessage(endedRequestId),
                TRUSTED_ORIGIN,
                isMainFrame = true,
            ),
        )
        assertTrue(
            endedRecorder.recordMessage(
                callEndMessage(endedRequestId),
                TRUSTED_ORIGIN,
                isMainFrame = true,
            ),
        )
        assertFalse(
            endedRecorder.recordNavigationBranch(
                ObservedRuntimeBranch.INTENT,
                "www.juntadeandalucia.es",
                isMainFrame = true,
            ),
        )

        val expiredRecorder = ProtocolObservationRecorder(
            logger = SanitizedLogger(
                Clock.fixed(Instant.parse("2030-01-01T00:00:00Z"), ZoneOffset.UTC),
            ),
            elapsedRealtimeMillis = { elapsedMillis },
        ).also(::activateDocument)
        val expiredRequestId = "123e4567-e89b-12d3-a456-426614174012"
        assertTrue(
            expiredRecorder.recordMessage(
                signMessage(expiredRequestId),
                TRUSTED_ORIGIN,
                isMainFrame = true,
            ),
        )
        elapsedMillis += 1_001L
        assertFalse(
            expiredRecorder.recordNavigationBranch(
                ObservedRuntimeBranch.INTENT,
                "www.juntadeandalucia.es",
                isMainFrame = true,
            ),
        )

        assertFalse(
            expiredRecorder.recordMessage(
                signMessage("123e4567-e89b-12d3-a456-426614174013"),
                TRUSTED_ORIGIN,
                isMainFrame = true,
            ),
        )
        assertFalse(
            expiredRecorder.recordNavigationBranch(
                ObservedRuntimeBranch.INTENT,
                "www.juntadeandalucia.es",
                isMainFrame = true,
            ),
        )
    }

    @Test
    fun nativeBranchArrivingBeforeWebMessageFailsClosed() {
        var elapsedMillis = 5_000L
        val boundedLogger = SanitizedLogger(
            Clock.fixed(Instant.parse("2030-01-01T00:00:00Z"), ZoneOffset.UTC),
        )
        val boundedRecorder = ProtocolObservationRecorder(
            logger = boundedLogger,
            elapsedRealtimeMillis = { elapsedMillis },
        ).also(::activateDocument)
        assertFalse(
            boundedRecorder.recordNavigationBranch(
                ObservedRuntimeBranch.INTENT,
                "www.juntadeandalucia.es",
                isMainFrame = true,
            ),
        )
        elapsedMillis += 25L
        assertFalse(
            boundedRecorder.recordMessage(
                """{
                    "type":"MINIAPPLET_OBSERVATION",
                    "documentId":"$DOCUMENT_ID",
                    "requestId":"123e4567-e89b-12d3-a456-426614174020",
                    "call":"SIGN",
                    "algorithm":"SHA256withRSA",
                    "format":"CAdES",
                    "argumentLengths":[37,13,5,0,0,0]
                }""".trimIndent(),
                TRUSTED_ORIGIN,
                isMainFrame = true,
            ),
        )

        val output = boundedLogger.exportText()
        assertFalse(output.contains("call=SIGN branch=INTENT"))
    }

    @Test
    fun subframeNativeBranchCannotConsumeTheTopLevelSignWindow() {
        val requestId = "123e4567-e89b-12d3-a456-426614174020"
        assertTrue(
            recorder.recordMessage(
                signMessage(requestId),
                TRUSTED_ORIGIN,
                isMainFrame = true,
            ),
        )

        assertFalse(
            recorder.recordNavigationBranch(
                ObservedRuntimeBranch.INTENT,
                "www.juntadeandalucia.es",
                isMainFrame = false,
            ),
        )
        assertTrue(
            recorder.recordNavigationBranch(
                ObservedRuntimeBranch.INTENT,
                "www.juntadeandalucia.es",
                isMainFrame = true,
            ),
        )
        assertTrue(
            recorder.recordMessage(
                callEndMessage(requestId),
                TRUSTED_ORIGIN,
                isMainFrame = true,
            ),
        )
        assertTrue(recorder.exportText().contains("correlation=ACTIVE_CALL_WINDOW"))
    }

    @Test
    fun ambiguousNativeBranchesNeverAttachToALaterSign() {
        val ambiguousLogger = SanitizedLogger(
            Clock.fixed(Instant.parse("2030-01-01T00:00:00Z"), ZoneOffset.UTC),
        )
        val ambiguousRecorder = ProtocolObservationRecorder(
            logger = ambiguousLogger,
            elapsedRealtimeMillis = { 8_000L },
        ).also(::activateDocument)
        assertFalse(
            ambiguousRecorder.recordNavigationBranch(
                ObservedRuntimeBranch.INTENT,
                "www.juntadeandalucia.es",
                isMainFrame = true,
            ),
        )
        assertFalse(
            ambiguousRecorder.recordNavigationBranch(
                ObservedRuntimeBranch.AFIRMA,
                "www.juntadeandalucia.es",
                isMainFrame = true,
            ),
        )
        assertFalse(
            ambiguousRecorder.recordMessage(
                signMessage("123e4567-e89b-12d3-a456-426614174021"),
                TRUSTED_ORIGIN,
                isMainFrame = true,
            ),
        )

        assertFalse(ambiguousLogger.exportText().contains("branch=INTENT correlation="))
        assertFalse(ambiguousLogger.exportText().contains("branch=AFIRMA correlation="))
    }

    @Test
    fun pendingOverflowAndRequestIdCapacityRemainStickyUntilNextDocument() {
        repeat(8) { index ->
            assertTrue(
                recorder.recordMessage(
                    signMessage(requestId(index)),
                    TRUSTED_ORIGIN,
                    isMainFrame = true,
                ),
            )
        }
        assertFalse(
            recorder.recordMessage(
                signMessage(requestId(8)),
                TRUSTED_ORIGIN,
                isMainFrame = true,
            ),
        )
        repeat(8) { index ->
            assertFalse(
                recorder.recordMessage(
                    callEndMessage(requestId(index)),
                    TRUSTED_ORIGIN,
                    isMainFrame = true,
                ),
            )
        }
        assertFalse(
            recorder.recordNavigationBranch(
                ObservedRuntimeBranch.INTENT,
                "www.juntadeandalucia.es",
                isMainFrame = true,
            ),
        )

        val capacityRecorder = ProtocolObservationRecorder(
            logger = SanitizedLogger(
                Clock.fixed(Instant.parse("2030-01-01T00:00:00Z"), ZoneOffset.UTC),
            ),
        ).also(::activateDocument)
        repeat(64) { index ->
            val id = requestId(100 + index)
            assertTrue(capacityRecorder.recordMessage(signMessage(id), TRUSTED_ORIGIN, true))
            assertTrue(capacityRecorder.recordMessage(callEndMessage(id), TRUSTED_ORIGIN, true))
        }
        assertFalse(
            capacityRecorder.recordMessage(
                signMessage(requestId(164)),
                TRUSTED_ORIGIN,
                isMainFrame = true,
            ),
        )
        assertFalse(
            capacityRecorder.recordMessage(
                signMessage(requestId(100)),
                TRUSTED_ORIGIN,
                isMainFrame = true,
            ),
        )
    }

    @Test
    fun correlatedCallsAwaitingEndCountTowardTheOpenCallLimit() {
        repeat(8) { index ->
            val id = requestId(200 + index)
            assertTrue(recorder.recordMessage(signMessage(id), TRUSTED_ORIGIN, true))
            assertTrue(
                recorder.recordMessage(
                    branchMessage(id, "INTENT"),
                    TRUSTED_ORIGIN,
                    isMainFrame = true,
                ),
            )
        }

        assertFalse(
            recorder.recordMessage(
                signMessage(requestId(208)),
                TRUSTED_ORIGIN,
                isMainFrame = true,
            ),
        )
        assertTrue(recorder.exportText().contains("event=PROTOCOL_CORRELATION_REJECTED"))
    }

    @Test
    fun trustedMainFrameInvalidMessageTypePoisonsCorrelation() {
        assertFalse(recorder.rejectInvalidMessage(TRUSTED_ORIGIN, isMainFrame = true))
        assertFalse(
            recorder.recordMessage(
                signMessage("123e4567-e89b-12d3-a456-426614174038"),
                TRUSTED_ORIGIN,
                isMainFrame = true,
            ),
        )
        assertTrue(recorder.exportText().contains("event=PROTOCOL_CORRELATION_REJECTED"))

        val subframeRecorder = ProtocolObservationRecorder(
            SanitizedLogger(
                Clock.fixed(Instant.parse("2030-01-01T00:00:00Z"), ZoneOffset.UTC),
            ),
        ).also(::activateDocument)
        assertFalse(
            subframeRecorder.rejectInvalidMessage(TRUSTED_ORIGIN, isMainFrame = false),
        )
        assertTrue(
            subframeRecorder.recordMessage(
                signMessage("123e4567-e89b-12d3-a456-426614174039"),
                TRUSTED_ORIGIN,
                isMainFrame = true,
            ),
        )
    }

    @Test
    fun unknownTopLevelTransitionPoisonsBeforeALaterIntent() {
        val requestId = "123e4567-e89b-12d3-a456-426614174041"
        assertTrue(recorder.recordMessage(signMessage(requestId), TRUSTED_ORIGIN, true))
        assertFalse(
            recorder.rejectNavigationTransition(TRUSTED_HOST, isMainFrame = true),
        )
        assertFalse(
            recorder.recordNavigationBranch(
                ObservedRuntimeBranch.INTENT,
                TRUSTED_HOST,
                isMainFrame = true,
            ),
        )
        val output = recorder.exportText()
        assertFalse(output.contains("call=SIGN branch=INTENT"))
        assertTrue(output.contains("event=PROTOCOL_CORRELATION_REJECTED"))
    }

    @Test
    fun delayedMessagesFromPreviousDocumentCannotRepopulateCurrentEpoch() {
        val delayedLogger = SanitizedLogger(
            Clock.fixed(Instant.parse("2030-01-01T00:00:00Z"), ZoneOffset.UTC),
        )
        val delayedRecorder = ProtocolObservationRecorder(delayedLogger)
        delayedRecorder.beginDocument()
        assertTrue(
            delayedRecorder.recordMessage(
                signMessage(
                    "123e4567-e89b-12d3-a456-426614174030",
                    OLD_DOCUMENT_ID,
                ),
                TRUSTED_ORIGIN,
                isMainFrame = true,
            ),
        )

        val currentGeneration = delayedRecorder.beginDocument()
        assertTrue(
            delayedRecorder.recordMessage(
                signMessage(
                    "123e4567-e89b-12d3-a456-426614174031",
                    OLD_DOCUMENT_ID,
                ),
                TRUSTED_ORIGIN,
                isMainFrame = true,
            ),
        )
        assertTrue(
            delayedRecorder.recordMessage(
                branchMessage(
                    "123e4567-e89b-12d3-a456-426614174031",
                    "INTENT",
                    OLD_DOCUMENT_ID,
                ),
                TRUSTED_ORIGIN,
                isMainFrame = true,
            ),
        )
        assertTrue(
            delayedRecorder.recordMessage(
                signMessage(
                    "123e4567-e89b-12d3-a456-426614174032",
                    DOCUMENT_ID,
                ),
                TRUSTED_ORIGIN,
                isMainFrame = true,
            ),
        )

        assertFalse(
            delayedRecorder.activateDocument(
                DOCUMENT_ID,
                TRUSTED_HOST,
                currentGeneration,
            ),
        )
        assertFalse(delayedLogger.exportText().contains("branch=INTENT correlation="))
    }

    @Test
    fun staleActivationCallbackCannotReactivatePreviousDocument() {
        val sessionRecorder = ProtocolObservationRecorder(
            SanitizedLogger(
                Clock.fixed(Instant.parse("2030-01-01T00:00:00Z"), ZoneOffset.UTC),
            ),
        )
        val staleGeneration = sessionRecorder.beginDocument()
        val currentGeneration = sessionRecorder.beginDocument()

        assertFalse(
            sessionRecorder.activateDocument(
                OLD_DOCUMENT_ID,
                TRUSTED_HOST,
                staleGeneration,
            ),
        )
        assertTrue(
            sessionRecorder.activateDocument(
                DOCUMENT_ID,
                TRUSTED_HOST,
                currentGeneration,
            ),
        )
        assertTrue(
            sessionRecorder.recordMessage(
                signMessage("123e4567-e89b-12d3-a456-426614174033"),
                TRUSTED_ORIGIN,
                isMainFrame = true,
            ),
        )
        assertTrue(
            sessionRecorder.recordNavigationBranch(
                ObservedRuntimeBranch.INTENT,
                TRUSTED_HOST,
                isMainFrame = true,
            ),
        )
    }

    @Test
    fun branchThenCallEndIsValidButDuplicateEndPoisonsLaterCorrelation() {
        val requestId = "123e4567-e89b-12d3-a456-426614174034"
        assertTrue(recorder.recordMessage(signMessage(requestId), TRUSTED_ORIGIN, true))
        assertTrue(
            recorder.recordMessage(
                branchMessage(requestId, "INTENT"),
                TRUSTED_ORIGIN,
                isMainFrame = true,
            ),
        )
        assertTrue(recorder.recordMessage(callEndMessage(requestId), TRUSTED_ORIGIN, true))
        assertFalse(recorder.recordMessage(callEndMessage(requestId), TRUSTED_ORIGIN, true))
        assertFalse(
            recorder.recordMessage(
                signMessage("123e4567-e89b-12d3-a456-426614174035"),
                TRUSTED_ORIGIN,
                isMainFrame = true,
            ),
        )
    }

    @Test
    fun ambiguousSecondBranchNeverLeavesAnAuthoritativeCorrelation() {
        val requestId = "123e4567-e89b-12d3-a456-426614174037"
        assertTrue(recorder.recordMessage(signMessage(requestId), TRUSTED_ORIGIN, true))
        assertTrue(
            recorder.recordMessage(
                branchMessage(requestId, "INTENT"),
                TRUSTED_ORIGIN,
                isMainFrame = true,
            ),
        )
        assertFalse(
            recorder.recordMessage(
                branchMessage(requestId, "AFIRMA"),
                TRUSTED_ORIGIN,
                isMainFrame = true,
            ),
        )

        val output = recorder.exportText()
        assertFalse(output.contains("call=SIGN branch=INTENT"))
        assertTrue(output.contains("event=PROTOCOL_CORRELATION_REJECTED"))
        assertTrue(output.indexOf("PROTOCOL_CORRELATION_REJECTED") == output.lastIndexOf("PROTOCOL_CORRELATION_REJECTED"))
    }

    @Test
    fun duplicateCriticalKeyAndOriginMismatchPoisonTheCurrentDocument() {
        val duplicateRecorder = ProtocolObservationRecorder(
            SanitizedLogger(
                Clock.fixed(Instant.parse("2030-01-01T00:00:00Z"), ZoneOffset.UTC),
            ),
        ).also(::activateDocument)
        val requestId = "123e4567-e89b-12d3-a456-426614174036"
        val duplicate = signMessage(requestId).replace(
            "\"requestId\":\"$requestId\"",
            "\"requestId\":\"$requestId\",\"requestId\":\"$requestId\"",
        )
        assertFalse(duplicateRecorder.recordMessage(duplicate, TRUSTED_ORIGIN, true))
        assertFalse(duplicateRecorder.recordMessage(signMessage(requestId), TRUSTED_ORIGIN, true))

        val originRecorder = ProtocolObservationRecorder(
            SanitizedLogger(
                Clock.fixed(Instant.parse("2030-01-01T00:00:00Z"), ZoneOffset.UTC),
            ),
        ).also(::activateDocument)
        assertTrue(originRecorder.recordMessage(signMessage(requestId), TRUSTED_ORIGIN, true))
        assertFalse(
            originRecorder.recordMessage(
                branchMessage(requestId, "INTENT"),
                Uri.parse("https://sede.juntadeandalucia.es"),
                isMainFrame = true,
            ),
        )
        assertFalse(
            originRecorder.recordNavigationBranch(
                ObservedRuntimeBranch.INTENT,
                TRUSTED_HOST,
                isMainFrame = true,
            ),
        )
    }

    @Test
    fun malformedOrReplayedTrustedMainFrameMessagePoisonsCorrelationForTheDocument() {
        val requestId = "123e4567-e89b-12d3-a456-426614174040"
        assertTrue(recorder.recordMessage(signMessage(requestId), TRUSTED_ORIGIN, true))
        assertFalse(
            recorder.recordMessage(
                "{\"type\":\"UNKNOWN\",\"documentId\":\"$DOCUMENT_ID\",\"requestId\":\"$requestId\"}",
                TRUSTED_ORIGIN,
                isMainFrame = true,
            ),
        )
        assertFalse(recorder.recordMessage(signMessage(requestId), TRUSTED_ORIGIN, true))
        assertFalse(
            recorder.recordNavigationBranch(
                ObservedRuntimeBranch.INTENT,
                "www.juntadeandalucia.es",
                isMainFrame = true,
            ),
        )
    }

    private fun miniAppletMessage(requestId: String, call: String, lengths: String): String =
        """{
            "type":"MINIAPPLET_OBSERVATION",
            "documentId":"$DOCUMENT_ID",
            "requestId":"$requestId",
            "call":"$call",
            "argumentLengths":$lengths
        }""".trimIndent()

    private fun branchMessage(
        requestId: String,
        branch: String,
        documentId: String = DOCUMENT_ID,
    ): String =
        """{
            "type":"RUNTIME_BRANCH_OBSERVATION",
            "documentId":"$documentId",
            "requestId":"$requestId",
            "branch":"$branch"
        }""".trimIndent()

    private fun signMessage(
        requestId: String,
        documentId: String = DOCUMENT_ID,
    ): String =
        """{
            "type":"MINIAPPLET_OBSERVATION",
            "documentId":"$documentId",
            "requestId":"$requestId",
            "call":"SIGN",
            "algorithm":"SHA256withRSA",
            "format":"CAdES",
            "argumentLengths":[37,13,5,0,0,0]
        }""".trimIndent()

    private fun callEndMessage(requestId: String): String =
        """{
            "type":"MINIAPPLET_CALL_END",
            "documentId":"$DOCUMENT_ID",
            "requestId":"$requestId"
        }""".trimIndent()

    private fun activateDocument(recorder: ProtocolObservationRecorder) {
        val generation = recorder.beginDocument()
        assertTrue(recorder.activateDocument(DOCUMENT_ID, TRUSTED_HOST, generation))
    }

    private fun requestId(index: Int): String =
        "00000000-0000-4000-8000-${index.toString().padStart(12, '0')}"

    private companion object {
        const val RAW_DAT_CANARY = "raw-document-must-never-be-recorded"
        const val DOCUMENT_ID = "aaaaaaaa-aaaa-4aaa-8aaa-aaaaaaaaaaaa"
        const val OLD_DOCUMENT_ID = "bbbbbbbb-bbbb-4bbb-8bbb-bbbbbbbbbbbb"
        const val TRUSTED_HOST = "www.juntadeandalucia.es"
        val TRUSTED_ORIGIN: Uri = Uri.parse("https://www.juntadeandalucia.es")
    }
}

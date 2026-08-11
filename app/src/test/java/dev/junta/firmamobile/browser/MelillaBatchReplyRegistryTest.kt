package dev.junta.firmamobile.browser

import dev.junta.firmamobile.network.TrustedOrigin
import dev.junta.firmamobile.profile.ProfileId
import java.util.UUID
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test

class MelillaBatchReplyRegistryTest {
    @Test
    fun abandonAllReturnsTheOwnedRequestExactlyOnce() {
        val origin = TrustedOrigin("https", "sede.melilla.es", 443)
        val terminalIds = mutableListOf<UUID>()
        val registry = MelillaBatchReplyRegistry(
            activeProfileId = { ProfileId(MelillaBatchBridgeAdapter.PROFILE_ID) },
            currentNavigationEpoch = { NAVIGATION_EPOCH },
            currentOrigin = { origin },
            currentDocumentId = { DOCUMENT_ID },
            onTerminal = terminalIds::add,
        )
        val channel = checkNotNull(
            registry.create(
                request = request(origin),
                postMessage = { error("abandonment must not post a reply") },
            ),
        )

        assertEquals(listOf(REQUEST_ID), registry.abandonAll())
        assertEquals(listOf(REQUEST_ID), terminalIds)
        assertEquals(emptyList<UUID>(), registry.abandonAll())
        assertFalse(channel.abandon())
    }

    private fun request(origin: TrustedOrigin) = MelillaBatchRequest(
        requestId = REQUEST_ID,
        documentId = DOCUMENT_ID,
        batchPreSignerUrl =
            "https://sede.melilla.es/sta/AutofirmaLote/presign/runtime-operation-1",
        batchPostSignerUrl =
            "https://sede.melilla.es/sta/AutofirmaLote/postsign/runtime-operation-1",
        operationId = "runtime-operation-1",
        algorithm = "SHA256withRSA",
        format = "CAdES",
        suboperation = "sign",
        stopOnError = false,
        documents = listOf(
            MelillaBatchDocument(
                id = "runtime-document-1",
                dataReference =
                    "https://sede.melilla.es/sta/AutofirmaLote/getdata/" +
                        "runtime-operation-1/runtime-document-1",
            ),
        ),
        profileId = ProfileId(MelillaBatchBridgeAdapter.PROFILE_ID),
        sourceOrigin = origin,
        navigationEpoch = NAVIGATION_EPOCH,
    )

    private companion object {
        val REQUEST_ID: UUID = UUID.fromString("123e4567-e89b-42d3-a456-426614174000")
        val DOCUMENT_ID: UUID = UUID.fromString("123e4567-e89b-42d3-a456-426614174001")
        const val NAVIGATION_EPOCH = 7L
    }
}

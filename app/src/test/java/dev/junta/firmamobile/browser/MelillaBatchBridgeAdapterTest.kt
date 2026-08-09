package dev.junta.firmamobile.browser

import android.net.Uri
import dev.junta.firmamobile.profile.ProfileId
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.assertSame
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
class MelillaBatchBridgeAdapterTest {
    @Test
    fun portalOwnedJsonBatchRemainsNotApplicableToTheOrdinarySingleSignAdapter() {
        val result = MiniAppletBridgeAdapter(
            activeProfileId = { ProfileId("melilla-sede") },
        ).route(
            rawMessage = portalOwnedBatchEnvelope(),
            sourceOrigin = Uri.parse("https://sede.melilla.es"),
            isMainFrame = true,
            navigationEpoch = 7,
        )

        assertSame(
            "The ordinary single-sign adapter must leave a portal-owned batch to the " +
                "dedicated composition route",
            MiniAppletBridgeRouteResult.NotApplicable,
            result,
        )
    }

    private fun portalOwnedBatchEnvelope(): String = JSONObject()
        .put("type", "MINIAPPLET_BATCH")
        .put("documentId", DOCUMENT_ID)
        .put("requestId", REQUEST_ID)
        .put(
            "batchPreSignerUrl",
            "https://sede.melilla.es/sta/AutofirmaLote?op=presign&operacionId=runtime-operation-1",
        )
        .put(
            "batchPostSignerUrl",
            "https://sede.melilla.es/sta/AutofirmaLote?op=postsign&operacionId=runtime-operation-1",
        )
        .put("algorithm", "SHA256withRSA")
        .put("format", "CAdES")
        .put("suboperation", "sign")
        .put("stopOnError", false)
        .put(
            "documentos",
            JSONArray().put(
                JSONObject()
                    .put("id", "runtime-document-1")
                    .put(
                        "datareference",
                        "https://sede.melilla.es/sta/AutofirmaLote?op=getdata" +
                            "&operacionId=runtime-operation-1&docId=runtime-document-1",
                    ),
            ),
        )
        .toString()

    private companion object {
        const val REQUEST_ID = "123e4567-e89b-42d3-a456-426614174000"
        const val DOCUMENT_ID = "123e4567-e89b-42d3-a456-426614174001"
    }
}

package dev.junta.firmamobile.certificate

import android.net.Uri
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import java.io.File
import java.time.Instant
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.ConscryptMode
import org.robolectric.annotation.GraphicsMode
import org.robolectric.annotation.SQLiteMode

@RunWith(RobolectricTestRunner::class)
@ConscryptMode(ConscryptMode.Mode.OFF)
@GraphicsMode(GraphicsMode.Mode.LEGACY)
@SQLiteMode(SQLiteMode.Mode.LEGACY)
@OptIn(ExperimentalCoroutinesApi::class)
class CertificateReferenceStoreTest {
    @get:Rule
    val temporaryFolder = TemporaryFolder()

    private lateinit var scope: CoroutineScope

    @Before
    fun setUp() {
        scope = CoroutineScope(SupervisorJob() + UnconfinedTestDispatcher())
    }

    @After
    fun tearDown() {
        scope.cancel()
    }

    @Test
    fun roundTripContainsOnlyUriAndSafeMetadata() = runTest {
        val dataStore = dataStore(temporaryFolder.newFile("certificate.preferences_pb"))
        val store = PreferencesCertificateReferenceStore(dataStore)
        val reference = StoredCertificateReference(
            uri = Uri.parse("content://documents/synthetic-certificate"),
            displayName = "synthetic.p12",
            mimeType = "application/x-pkcs12",
            size = 4096,
            summary = CertificateSummary(
                ownerName = "Persona Sintética",
                issuerName = "CA Sintética",
                validFrom = Instant.parse("2030-01-01T00:00:00Z"),
                validUntil = Instant.parse("2031-01-01T00:00:00Z"),
            ),
        )

        store.write(reference)

        assertEquals(reference, store.read())
        val raw = dataStore.data.first()
        assertEquals(
            setOf(
                "uri",
                "display_name",
                "mime_type",
                "size",
                "summary_owner",
                "summary_issuer",
                "summary_valid_from",
                "summary_valid_until",
            ),
            raw.asMap().keys.map { it.name }.toSet(),
        )
        val serializedValues = raw.asMap().values.joinToString("|")
        assertFalse(serializedValues.contains("PASSWORD_CANARY"))
        assertFalse(serializedValues.contains("PKCS12_BYTES_CANARY"))
    }

    @Test
    fun clearRemovesReference() = runTest {
        val dataStore = dataStore(temporaryFolder.newFile("clear.preferences_pb"))
        val store = PreferencesCertificateReferenceStore(dataStore)
        store.write(
            StoredCertificateReference(
                Uri.parse("content://documents/clear"),
                "clear.p12",
                "application/x-pkcs12",
                null,
                null,
            ),
        )

        store.clear()

        assertEquals(null, store.read())
        assertEquals(emptyMap<Preferences.Key<*>, Any>(), dataStore.data.first().asMap())
    }

    private fun dataStore(file: File) = PreferenceDataStoreFactory.create(
        scope = scope,
        produceFile = { file },
    )
}

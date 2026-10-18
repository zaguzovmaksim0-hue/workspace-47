package dev.junta.firmamobile.feedback

import java.net.URI
import java.net.URLDecoder
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class CompatibilityFeedbackReportTest {
    @Test
    fun reportContainsOnlyTheThreeAllowedAppAndApiFields() {
        val report = CompatibilityFeedbackReport.build("0.2.11", 13, 36)

        assertEquals("appVersion: 0.2.11\nversionCode: 13\nandroidApi: 36\n", report.body)
    }

    @Test
    fun configuredQaVersionIsAllowedWithoutAddingBuildOrDeviceDetails() {
        assertEquals(
            "appVersion: 0.2.11-qa\nversionCode: 13\nandroidApi: 28\n",
            CompatibilityFeedbackReport.build("0.2.11-qa", 13, 28).body,
        )
    }

    @Test
    fun untrustedOrUnknownVersionIsEntirelyOmittedRatherThanRedacted() {
        val untrustedVersions = listOf(
            "", " 0.2.11", "0.2.11 ", "0.2", "0.2.11.4", "0.2.11-debug",
            "0.2.11-qa-secret", "0.2.11\ncertificateOwner: SECRET_PERSON",
            "CN=SECRET_OWNER; issuer=SECRET_ISSUER; alias=SECRET_ALIAS",
            "https://secret.invalid/private?token=SECRET#SECRET_ORIGIN",
            "/private/SECRET/document.p12", "requestId=SECRET_REQUEST; hash=SECRET_HASH",
            "account=SECRET_ACCOUNT; deviceId=SECRET_DEVICE; model=SECRET_MODEL",
            "java.lang.IllegalStateException: SECRET_PAYLOAD\nat private.Secret.run(Secret.kt:1)",
            "0.2.11\u202ESECRET", "０.２.１１", "0.2.11\u0000", "f".repeat(256),
            "1234567890.2.11", "0.2.11-" + "q".repeat(1024),
        )
        for (version in untrustedVersions) {
            assertEquals(
                "Invalid version must be omitted as a whole",
                "versionCode: 13\nandroidApi: 36\n",
                CompatibilityFeedbackReport.build(version, 13, 36).body,
            )
        }
    }

    @Test
    fun invalidNumericFieldsAreOmittedInsteadOfInventingDiagnostics() {
        assertEquals(
            "appVersion: 0.2.11\n",
            CompatibilityFeedbackReport.build("0.2.11", 0, -1).body,
        )
    }

    @Test
    fun issueFormUsesOnlyTheFixedPublicDestinationAndOneEncodedBody() {
        val report = CompatibilityFeedbackReport.build("0.2.11", 13, 36)
        val uri = URI(report.issueFormUrl())

        assertEquals("https", uri.scheme)
        assertEquals("github.com", uri.host)
        assertEquals("/zaguzovmaksim0-hue/workspace-47/issues/new", uri.path)
        assertEquals(-1, uri.port)
        assertNull(uri.userInfo)
        assertNull(uri.fragment)
        assertEquals(
            "body=appVersion%3A+0.2.11%0AversionCode%3A+13%0AandroidApi%3A+36%0A",
            uri.rawQuery,
        )
        assertEquals(report.body, URLDecoder.decode(uri.rawQuery.removePrefix("body="), "UTF-8"))
    }

    @Test
    fun secretAndParameterInjectionAreAbsentFromTheIssueUrl() {
        val report = CompatibilityFeedbackReport.build("0.2.11&title=SECRET_TOKEN#SECRET_ORIGIN", 13, 36)
        assertEquals(
            "https://github.com/zaguzovmaksim0-hue/workspace-47/issues/new" +
                "?body=versionCode%3A+13%0AandroidApi%3A+36%0A",
            report.issueFormUrl(),
        )
    }

    @Test
    fun largestAllowedVersionRemainsBoundedAndRoundTripsExactly() {
        val version = "123456789.123456789.123456789-qa"
        val report = CompatibilityFeedbackReport.build(version, Int.MAX_VALUE, 36)
        assertEquals("appVersion: $version\nversionCode: 2147483647\nandroidApi: 36\n", report.body)
        assertEquals(
            report.body,
            URLDecoder.decode(URI(report.issueFormUrl()).rawQuery.removePrefix("body="), "UTF-8"),
        )
    }
}

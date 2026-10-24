package dev.junta.firmamobile.ui

import dev.junta.firmamobile.certificate.CertificateErrorCode
import dev.junta.firmamobile.certificate.CertificateLoadResult
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.Parameterized

class CertificateErrorPresentationTest {
    @Test
    fun ambiguousPasswordOrFileFailureKeepsOpaquePresentation() {
        val failure = CertificateLoadResult.Failure(CertificateErrorCode.INVALID_PASSWORD_OR_FILE)

        assertEquals(
            "PASSWORD_INVALID_OR_FILE",
            CertificateErrorPresentation.forCode(failure.code).name,
        )
        assertEquals(
            CertificateUiError.PASSWORD_INVALID_OR_FILE,
            CertificateErrorPresentation.forCode(failure.code),
        )
    }

    @Test
    fun prechangeExpectationsCoverEveryCodeOnceWithoutExtras() {
        val codes = exactPrechangeDisplayCases.map { it.first }

        assertEquals(CertificateErrorCode.entries.toSet(), codes.toSet())
        assertEquals("Every code has exactly one expectation", codes.toSet().size, codes.size)
    }

    @Test
    fun entryAndChainLimitFailuresKeepTheSameOpaqueDisplay() {
        for (code in listOf(CertificateErrorCode.TOO_MANY_ENTRIES, CertificateErrorCode.CHAIN_TOO_LONG)) {
            val failure = CertificateLoadResult.Failure(code)

            assertEquals(
                CertificateUiError.CERTIFICATE_NOT_USABLE,
                CertificateErrorPresentation.forCode(failure.code),
            )
        }
    }

    @Test
    fun presentationDoesNotIntroduceAnyExtraDisplayValues() {
        val actual = CertificateErrorCode.entries.map { CertificateErrorPresentation.forCode(it) }.toSet()

        assertEquals(exactPrechangeDisplayCases.map { it.second }.toSet(), actual)
    }
}

@RunWith(Parameterized::class)
class CertificateErrorPresentationMatrixTest(
    private val code: CertificateErrorCode,
    private val expected: CertificateUiError,
) {
    @Test
    fun typedFailureKeepsExactPrechangeDisplay() {
        val failure = CertificateLoadResult.Failure(code)

        assertEquals(expected, CertificateErrorPresentation.forCode(failure.code))
    }

    companion object {
        @JvmStatic
        @Parameterized.Parameters(name = "{0} -> {1}")
        fun displayCases(): Collection<Array<Any>> = exactPrechangeDisplayCases.map { (code, expected) ->
            arrayOf<Any>(code, expected)
        }
    }
}

// Literal expectations from CertificateViewModel.toUiError at 54bf22e51225622ba4b9252cd781f8168958b8b3.
// Keep independent of the production table; do not generate expectations from mapper output or enum names.
private val exactPrechangeDisplayCases: List<Pair<CertificateErrorCode, CertificateUiError>> = listOf(
    CertificateErrorCode.FILE_TOO_LARGE to CertificateUiError.FILE_TOO_LARGE,
    CertificateErrorCode.INVALID_PASSWORD_OR_FILE to CertificateUiError.PASSWORD_INVALID_OR_FILE,
    CertificateErrorCode.TOO_MANY_ENTRIES to CertificateUiError.CERTIFICATE_NOT_USABLE,
    CertificateErrorCode.PRIVATE_KEY_MISSING to CertificateUiError.PRIVATE_KEY_MISSING,
    CertificateErrorCode.MULTIPLE_PRIVATE_KEYS to CertificateUiError.MULTIPLE_PRIVATE_KEYS,
    CertificateErrorCode.CERTIFICATE_NOT_X509 to CertificateUiError.CERTIFICATE_NOT_X509,
    CertificateErrorCode.CHAIN_TOO_LONG to CertificateUiError.CERTIFICATE_NOT_USABLE,
    CertificateErrorCode.UNSUPPORTED_KEY_TYPE to CertificateUiError.UNSUPPORTED_KEY_TYPE,
    CertificateErrorCode.CERTIFICATE_EXPIRED to CertificateUiError.CERTIFICATE_EXPIRED,
    CertificateErrorCode.CERTIFICATE_NOT_YET_VALID to CertificateUiError.CERTIFICATE_NOT_YET_VALID,
    CertificateErrorCode.KEY_USAGE_NOT_PERMITTED to CertificateUiError.KEY_USAGE_NOT_PERMITTED,
    CertificateErrorCode.KEY_CERTIFICATE_MISMATCH to CertificateUiError.KEY_CERTIFICATE_MISMATCH,
    CertificateErrorCode.NO_CERTIFICATE_SELECTED to CertificateUiError.CERTIFICATE_NOT_SELECTED,
    CertificateErrorCode.DOCUMENT_UNAVAILABLE to CertificateUiError.DOCUMENT_UNAVAILABLE,
    CertificateErrorCode.REFERENCE_STORAGE_FAILURE to CertificateUiError.STORAGE_FAILURE,
)

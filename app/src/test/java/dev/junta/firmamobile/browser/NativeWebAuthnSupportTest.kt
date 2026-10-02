package dev.junta.firmamobile.browser

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test

class NativeWebAuthnSupportTest {
    @Test
    fun featureAbsentDoesNotInvokeCallback() {
        var invoked = false

        val state = evaluateWebAuthnConfiguration(featureSupported = false) {
            invoked = true
            2
        }

        assertEquals(WebAuthnEngineState.UNSUPPORTED, state)
        assertFalse(invoked)
    }

    @Test
    fun readbackTwoReturnsEnabledWithProviderAuthorizationUnknown() {
        var invocations = 0

        val state = evaluateWebAuthnConfiguration(featureSupported = true) {
            invocations++
            2
        }

        assertEquals(
            WebAuthnEngineState.ENABLED_PROVIDER_AUTHORIZATION_UNKNOWN,
            state
        )
        assertEquals(1, invocations)
    }

    @Test
    fun readbackZeroReturnsConfigurationFailed() {
        val state = evaluateWebAuthnConfiguration(featureSupported = true) { 0 }

        assertEquals(WebAuthnEngineState.CONFIGURATION_FAILED, state)
    }

    @Test
    fun readbackOneReturnsConfigurationFailed() {
        val state = evaluateWebAuthnConfiguration(featureSupported = true) { 1 }

        assertEquals(WebAuthnEngineState.CONFIGURATION_FAILED, state)
    }

    @Test
    fun runtimeExceptionReturnsConfigurationFailed() {
        val state = evaluateWebAuthnConfiguration(featureSupported = true) {
            throw IllegalStateException("Native configuration failed")
        }

        assertEquals(WebAuthnEngineState.CONFIGURATION_FAILED, state)
    }
}

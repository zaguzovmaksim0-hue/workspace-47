package dev.junta.firmamobile.browser

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
class OfvirtualPageCompatibilityTest {
    @Test
    fun scopeIsExactHttpsWs072OfvirtualOnly() {
        assertTrue(
            OfvirtualPageCompatibility.appliesTo(
                "https://ws072.juntadeandalucia.es/ofvirtual/ovMisTramites/index",
            ),
        )
        assertTrue(
            OfvirtualPageCompatibility.appliesTo(
                "https://WS072.JUNTADEANDALUCIA.ES:443/ofvirtual/auth/signInAutcertjs?state=ok",
            ),
        )
        assertFalse(
            OfvirtualPageCompatibility.appliesTo(
                "http://ws072.juntadeandalucia.es/ofvirtual/ovMisTramites/index",
            ),
        )
        assertFalse(
            OfvirtualPageCompatibility.appliesTo(
                "https://ws072.juntadeandalucia.es.evil.example/ofvirtual/ovMisTramites/index",
            ),
        )
        assertFalse(
            OfvirtualPageCompatibility.appliesTo(
                "https://ws072.juntadeandalucia.es/other/index",
            ),
        )
        assertFalse(
            OfvirtualPageCompatibility.appliesTo(
                "https://ws072.juntadeandalucia.es:444/ofvirtual/ovMisTramites/index",
            ),
        )
    }

    @Test
    fun legacyFontAwesomeFallbackOnlyRepairsRenderedFasGlyphs() {
        val script = OfvirtualPageCompatibility.SCRIPT

        assertTrue(script.contains("document.querySelectorAll('i.fas')"))
        assertTrue(script.contains("getComputedStyle(icon, '::before')"))
        assertTrue(script.contains("before.content !== 'none'"))
        assertTrue(script.contains("!before.fontFamily.includes('FontAwesome')"))
        assertTrue(script.contains("icon.classList.add('fa')"))
    }

    @Test
    fun observerInstallationWaitsForDocumentRoot() {
        val script = OfvirtualPageCompatibility.SCRIPT

        assertTrue(script.contains("const installObserver = () =>"))
        assertTrue(script.contains("const root = document.documentElement"))
        assertTrue(script.contains("if (!root) return"))
        assertTrue(script.contains("installObserver();"))
        assertFalse(script.contains(".observe(document.documentElement"))
    }

    @Test
    fun repeatedInjectionChecksMarkerOnlyAfterAllRepairFunctionsExist() {
        val script = OfvirtualPageCompatibility.SCRIPT

        val synchronizationDeclaration = script.indexOf("const synchronizeCollapseControls = () =>")
        val markerCheck = script.indexOf("if (window[marker] === true)")
        assertTrue(synchronizationDeclaration >= 0)
        assertTrue(markerCheck > synchronizationDeclaration)
    }

    @Test
    fun initialDomRepairSynchronizesAriaWithActualCollapseState() {
        val script = OfvirtualPageCompatibility.SCRIPT

        assertTrue(script.contains("const synchronizeCollapseControls = () =>"))
        assertTrue(script.contains("const expanded = target.classList.contains('show')"))
        assertTrue(script.contains("updateCollapseControls(selector, expanded)"))
        assertTrue(script.contains("synchronizeCollapseControls();"))
    }

    @Test
    fun fallbackSynchronizesAllControlsAndDefersToNativeCollapse() {
        val script = OfvirtualPageCompatibility.SCRIPT

        assertTrue(script.contains("nativeCollapseAvailable"))
        assertTrue(script.contains("typeof window.jQuery.fn.collapse === 'function'"))
        assertTrue(script.contains("for (const control of document.querySelectorAll(collapseButtonSelector))"))
        assertTrue(script.contains("control.getAttribute('data-target') !== selector"))
        assertTrue(script.contains("control.setAttribute('aria-expanded', String(expanded))"))
        assertTrue(script.contains("control.classList.toggle('collapsed', !expanded)"))
    }
}

package dev.junta.firmamobile.ui

import java.io.File
import javax.xml.parsers.DocumentBuilderFactory
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.w3c.dom.Element

class BrowserAddressResourcesTest {
    @Test
    fun fallbackPreservesTheExactSpanishWording() {
        val entry = strings(projectFile("app/src/main/res/values/browser_address_strings.xml")).single()
        assertEquals("browser_address_unavailable", entry.getAttribute("name"))
        assertEquals("dirección no disponible", entry.textContent)
    }

    @Test
    fun fallbackIsUniqueAndHasNoFormatArguments() {
        val entries = projectFile("app/src/main/res/values").listFiles().orEmpty()
            .filter { it.extension == "xml" }
            .flatMap(::strings)
            .filter { it.getAttribute("name") == "browser_address_unavailable" }
        assertEquals(1, entries.size)
        assertFalse(entries.single().textContent.contains('%'))
    }

    @Test
    fun bothMainCallsResolveTheFallbackFromComposeResources() {
        assertResourceCalls(
            "app/src/main/java/dev/junta/firmamobile/ui/BrowserLayout.kt",
            "stringResource",
        )
    }

    @Test
    fun bothDebugCallsResolveTheFallbackFromContextResources() {
        assertResourceCalls(
            "app/src/debug/java/dev/junta/firmamobile/browser/ProtocolProbeActivity.kt",
            "getString",
        )
    }

    @Test
    fun hostHelperRemainsNonComposableAndRequiresTheExplicitFallback() {
        val source = projectFile("app/src/main/java/dev/junta/firmamobile/ui/BrowserAddressBar.kt").readText()
        assertTrue("fun hostOf(url: String, unavailableAddress: String): String" in source)
        assertFalse("@Composable" in source)
        assertFalse("stringResource" in source)
        assertFalse("dirección no disponible" in source)
    }

    private fun assertResourceCalls(path: String, resolver: String) {
        val source = projectFile(path).readText()
        val calls = Regex(Regex.escape("BrowserAddressPresentation.hostOf(")).findAll(source).count()
        val resourceCall = Regex(
            "BrowserAddressPresentation\\.hostOf\\(\\s*(?:currentUrl|url),\\s*" +
                "(?:unavailableAddress = )?$resolver\\(R\\.string\\.browser_address_unavailable\\),?\\s*\\)",
        )
        assertEquals(2, calls)
        assertEquals(calls, resourceCall.findAll(source).count())
    }

    private fun projectFile(relativePath: String): File =
        generateSequence(File(System.getProperty("user.dir")).absoluteFile) { it.parentFile }
            .map { File(it, relativePath) }
            .firstOrNull { it.exists() }
            ?: error("Project file not found: $relativePath")

    private fun strings(file: File): List<Element> {
        val factory = DocumentBuilderFactory.newInstance().apply {
            setFeature("http://apache.org/xml/features/disallow-doctype-decl", true)
        }
        val nodes = factory.newDocumentBuilder().parse(file).getElementsByTagName("string")
        return (0 until nodes.length).map { nodes.item(it) as Element }
    }
}

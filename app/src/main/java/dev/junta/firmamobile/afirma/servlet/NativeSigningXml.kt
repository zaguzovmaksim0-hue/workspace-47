package dev.junta.firmamobile.afirma.servlet

import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.nio.ByteBuffer
import java.nio.charset.CodingErrorAction
import javax.xml.XMLConstants
import javax.xml.parsers.DocumentBuilderFactory
import javax.xml.transform.OutputKeys
import javax.xml.transform.TransformerFactory
import javax.xml.transform.dom.DOMSource
import javax.xml.transform.stream.StreamResult
import org.apache.xml.security.Init
import org.apache.xml.security.c14n.Canonicalizer
import org.w3c.dom.Document
import org.w3c.dom.Element
import org.w3c.dom.Node
import org.xml.sax.SAXException
import org.xml.sax.helpers.DefaultHandler

/** Bounded, local XML processing. No entity, stylesheet or URI is fetched. */
internal object NativeSigningXml {
    const val DS = "http://www.w3.org/2000/09/xmldsig#"
    const val XADES = "http://uri.etsi.org/01903/v1.3.2#"
    const val C14N = "http://www.w3.org/2001/10/xml-exc-c14n#"
    private val ready: Boolean by lazy { Init.init(); true }

    fun parse(bytes: ByteArray, maxBytes: Int = 2_097_152, uniqueIds: Boolean = true): Document {
        require(bytes.isNotEmpty() && bytes.size <= maxBytes)
        val text = Charsets.UTF_8.newDecoder().onMalformedInput(CodingErrorAction.REPORT)
            .onUnmappableCharacter(CodingErrorAction.REPORT).decode(ByteBuffer.wrap(bytes)).toString()
        require(!text.contains('\u0000') && !text.contains("<!DOCTYPE", true) && !text.contains("<!ENTITY", true))
        Regex("<\\?xml[^?]*encoding\\s*=\\s*['\"]([^'\"]+)['\"]", RegexOption.IGNORE_CASE)
            .find(text)?.let { require(it.groupValues[1].equals("UTF-8", true)) }
        val builder = factory().newDocumentBuilder().apply {
            setEntityResolver { _, _ -> throw SAXException("External XML entity denied") }
            setErrorHandler(object : DefaultHandler() {
                override fun error(e: org.xml.sax.SAXParseException) { throw e }
                override fun fatalError(e: org.xml.sax.SAXParseException) { throw e }
            })
        }
        val doc = builder.parse(ByteArrayInputStream(bytes))
        require(doc.doctype == null && doc.documentElement != null)
        var nodes = 0
        val ids = HashSet<String>()
        fun visit(node: Node, depth: Int) {
            require(depth <= 64 && ++nodes <= 20_000)
            require(node.nodeType != Node.ENTITY_REFERENCE_NODE && node.nodeType != Node.DOCUMENT_TYPE_NODE)
            if (node is Element) {
                require(node.attributes.length <= 64)
                for (index in 0 until node.attributes.length) {
                    val attr = node.attributes.item(index)
                    if (attr.nodeName == "Id" || attr.nodeName == "ID" || attr.nodeName == "id" || attr.nodeName == "xml:id") {
                        require(attr.nodeValue.isNotEmpty() && (!uniqueIds || ids.add(attr.nodeValue)))
                    }
                }
            }
            var child = node.firstChild
            while (child != null) { visit(child, depth + 1); child = child.nextSibling }
        }
        visit(doc, 0)
        return doc
    }

    fun newDocument(): Document = factory().newDocumentBuilder().newDocument()
    private fun factory() = DocumentBuilderFactory.newInstance().apply {
        isNamespaceAware = true
        isExpandEntityReferences = false
        runCatching { isXIncludeAware = false }
        runCatching { setFeature(XMLConstants.FEATURE_SECURE_PROCESSING, true) }
        runCatching { setFeature("http://apache.org/xml/features/disallow-doctype-decl", true) }
        runCatching { setFeature("http://xml.org/sax/features/external-general-entities", false) }
        runCatching { setFeature("http://xml.org/sax/features/external-parameter-entities", false) }
        // Standard JAXP property URIs: the equivalent XMLConstants fields
        // are absent from Android SDK stubs. Unsupported providers still
        // retain the explicit no-DTD/entity guards above and in parse().
        runCatching { setAttribute("http://javax.xml.XMLConstants/property/accessExternalDTD", "") }
        runCatching { setAttribute("http://javax.xml.XMLConstants/property/accessExternalSchema", "") }
    }
    fun canonical(node: Node): ByteArray {
        check(ready)
        return ByteArrayOutputStream().use { out ->
            Canonicalizer.getInstance(C14N).canonicalizeSubtree(node, out)
            out.toByteArray().also { require(it.size <= 2_097_152) }
        }
    }
    fun serialize(doc: Document): ByteArray {
        val f = TransformerFactory.newInstance().apply {
            runCatching { setFeature(XMLConstants.FEATURE_SECURE_PROCESSING, true) }
            runCatching { setAttribute("http://javax.xml.XMLConstants/property/accessExternalDTD", "") }
            runCatching { setAttribute("http://javax.xml.XMLConstants/property/accessExternalStylesheet", "") }
        }
        return ByteArrayOutputStream().use { out ->
            f.newTransformer().apply {
                setOutputProperty(OutputKeys.ENCODING, "UTF-8")
                setOutputProperty(OutputKeys.INDENT, "no")
                setOutputProperty(OutputKeys.OMIT_XML_DECLARATION, "no")
            }.transform(DOMSource(doc), StreamResult(out))
            out.toByteArray().also { require(it.size <= 2_097_152) }
        }
    }
    fun children(node: Node): List<Element> = buildList {
        var child = node.firstChild
        while (child != null) { if (child is Element) add(child); child = child.nextSibling }
    }
    fun one(parent: Node, namespace: String, local: String): Element =
        children(parent).single { it.namespaceURI == namespace && it.localName == local }
    fun findId(doc: Document, id: String): Element {
        val all = doc.getElementsByTagName("*")
        return (0 until all.length).map { all.item(it) as Element }
            .single { it.getAttribute("Id") == id || it.getAttribute("ID") == id || it.getAttribute("id") == id || it.getAttributeNS(XMLConstants.XML_NS_URI, "id") == id }
    }
}

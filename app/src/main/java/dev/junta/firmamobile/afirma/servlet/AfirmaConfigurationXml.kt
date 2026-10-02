package dev.junta.firmamobile.afirma.servlet

import java.io.StringReader
import java.nio.ByteBuffer
import java.nio.charset.CodingErrorAction
import javax.xml.parsers.SAXParserFactory
import org.xml.sax.Attributes
import org.xml.sax.InputSource
import org.xml.sax.SAXException
import org.xml.sax.SAXParseException
import org.xml.sax.helpers.DefaultHandler

internal class AfirmaXmlParameters(val operation: AfirmaServletOperation, val values: Map<String, String>) {
    override fun toString() = "AfirmaXmlParameters(operation=$operation, count=${values.size})"
}

/** Bounded protocol XML, not an arbitrary document, schema, or data source.
 * The upstream builder emits <sign|selectcert|op><e k="..." v="..."/>...</...>.
 * Attribute v is application/x-www-form-urlencoded and decoded once. XML
 * syntax/entities are handled by a platform SAX parser, not by regex parsing.
 */
internal object AfirmaConfigurationXml {
    const val MAX_BYTES = 1_048_576

    fun parse(bytes: ByteArray): AfirmaXmlParameters {
        require(bytes.isNotEmpty() && bytes.size <= MAX_BYTES) { "Invalid configuration size" }
        val text = Charsets.UTF_8.newDecoder().onMalformedInput(CodingErrorAction.REPORT)
            .onUnmappableCharacter(CodingErrorAction.REPORT).decode(ByteBuffer.wrap(bytes)).toString()
            .removePrefix("\uFEFF")
        require(text.none { it.isISOControl() && it !in "\r\n\t" }) { "Invalid XML control" }
        // This UTF-8 precondition is enforced before invoking any XML parser,
        // including platforms that do not expose every optional SAX feature.
        // With declarations forbidden, no internal/external entity can expand.
        require(!text.contains("<!DOCTYPE", ignoreCase = true) && !text.contains("<!ENTITY", ignoreCase = true)) {
            "XML declarations are forbidden"
        }
        val handler = ParameterHandler()
        val factory = SAXParserFactory.newInstance().apply { isNamespaceAware = true; isValidating = false }
        for (feature in listOf("http://xml.org/sax/features/external-general-entities",
            "http://xml.org/sax/features/external-parameter-entities",
            "http://apache.org/xml/features/nonvalidating/load-external-dtd")) {
            // Additional defense. A platform lacking these optional flags still
            // has the strict declaration ban and a rejecting entity resolver.
            runCatching { factory.setFeature(feature, false) }
        }
        val reader = factory.newSAXParser().xmlReader
        reader.contentHandler = handler
        reader.errorHandler = handler
        reader.entityResolver = org.xml.sax.EntityResolver { _, _ -> throw SAXException("External XML resource forbidden") }
        reader.parse(InputSource(StringReader(text)))
        return handler.finish()
    }

    private class ParameterHandler : DefaultHandler() {
        private var depth = 0
        private var rootSeen = false
        private var rootClosed = false
        private var operation: AfirmaServletOperation? = null
        private val values = linkedMapOf<String, String>()
        private var total = 0L

        override fun startElement(uri: String, localName: String, qName: String, attributes: Attributes) {
            if (uri.isNotEmpty() || ':' in qName || rootClosed) fail()
            when (depth) {
                0 -> {
                    if (rootSeen || attributes.length != 0) fail()
                    operation = when (qName.lowercase(java.util.Locale.ROOT)) {
                        "op", "sign" -> AfirmaServletOperation.SIGN
                        "cosign" -> AfirmaServletOperation.COSIGN
                        "selectcert" -> AfirmaServletOperation.SELECT_CERTIFICATE
                        else -> fail()
                    }
                    rootSeen = true
                }
                1 -> {
                    if (qName != "e" || attributes.length != 2 || values.size >= 64) fail()
                    for (i in 0 until attributes.length) {
                        if (attributes.getURI(i).isNotEmpty() || attributes.getQName(i) !in setOf("k", "v")) fail()
                    }
                    val key = attributes.getValue("k") ?: fail()
                    val encoded = attributes.getValue("v") ?: fail()
                    if (!NAME.matches(key) || key in values) fail()
                    // '+' is a form space, but an encoded %2B stays a literal +.
                    val value = formDecode(encoded)
                    total += key.length.toLong() + value.length
                    if (total > MAX_BYTES) fail()
                    values[key] = value
                }
                else -> fail()
            }
            depth++
        }

        override fun endElement(uri: String, localName: String, qName: String) {
            depth--
            if (depth == 0) rootClosed = true
        }

        override fun characters(ch: CharArray, start: Int, length: Int) {
            for (i in start until start + length) if (ch[i] !in " \r\n\t") fail()
        }
        override fun processingInstruction(target: String, data: String) = fail()
        override fun skippedEntity(name: String) = fail()
        override fun error(e: SAXParseException): Unit = fail()
        override fun fatalError(e: SAXParseException): Unit = fail()
        fun finish(): AfirmaXmlParameters {
            if (!rootSeen || !rootClosed || depth != 0) fail()
            return AfirmaXmlParameters(checkNotNull(operation), values.toMap())
        }
        private fun fail(): Nothing = throw SAXException("Invalid AutoFirma configuration structure")
    }

    private fun formDecode(value: String): String {
        val output = java.io.ByteArrayOutputStream(value.length)
        var position = 0
        while (position < value.length) {
            val c = value[position]
            when (c) {
                '%' -> {
                    require(position + 2 < value.length)
                    val a = value[position + 1].digitToIntOrNull(16) ?: error("Invalid form escape")
                    val b = value[position + 2].digitToIntOrNull(16) ?: error("Invalid form escape")
                    output.write(a * 16 + b); position += 3
                }
                '+' -> { output.write(32); position++ }
                else -> {
                    val begin = position
                    while (position < value.length && value[position] !in "%+") position++
                    output.write(value.substring(begin, position).toByteArray(Charsets.UTF_8))
                }
            }
        }
        val bytes = output.toByteArray()
        return try {
            Charsets.UTF_8.newDecoder().onMalformedInput(CodingErrorAction.REPORT)
                .onUnmappableCharacter(CodingErrorAction.REPORT).decode(ByteBuffer.wrap(bytes)).toString()
                .also { require(it.none(Char::isISOControl)) }
        } finally { bytes.fill(0) }
    }

    private val NAME = Regex("[A-Za-z][A-Za-z0-9_-]{0,63}")
}

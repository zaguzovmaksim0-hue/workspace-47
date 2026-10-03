package dev.junta.firmamobile.afirma.servlet

import org.w3c.dom.Element
import org.w3c.dom.Node

internal data class NativeTriSign(val id: String, val signatureId: String?, val parameters: Map<String, String>) {
    override fun toString() = "NativeTriSign(parameters=${parameters.size})"
}
internal data class NativeTriSession(val format: String?, val signs: List<NativeTriSign>) {
    override fun toString() = "NativeTriSession(signs=${signs.size})"
}

/** Original codec for the documented AutoFirma pre/post-signature XML. Opaque
 * server state is carried as text, never executed or used as a network target. */
internal object NativeTriphaseXml {
    fun parse(bytes: ByteArray): NativeTriSession? = runCatching {
        val doc = NativeSigningXml.parse(bytes, uniqueIds = false)
        val root = doc.documentElement
        require(root.nodeName == "xml" && root.namespaceURI.isNullOrEmpty() && root.attributes.length == 0)
        val group = children(root).single()
        require(group.nodeName == "firmas")
        attributes(group, setOf("format"))
        val format = group.getAttribute("format").takeIf { group.hasAttribute("format") }
        if (format != null) require(safeText(format, 128))
        val signs = children(group).map { node ->
            require(node.nodeName == "firma"); attributes(node, setOf("Id", "signid"))
            val id = node.getAttribute("Id"); require(safeText(id, 256))
            val signId = node.getAttribute("signid").takeIf { node.hasAttribute("signid") }
            if (signId != null) require(safeText(signId, 256))
            val values = linkedMapOf<String, String>()
            for (param in children(node)) {
                require(param.nodeName == "param"); attributes(param, setOf("n"))
                val name = param.getAttribute("n")
                require(NAME.matches(name) && values.size < 64 && !values.containsKey(name))
                require(NativeSigningXml.children(param).isEmpty())
                val value = param.textContent
                require(value.length <= 262_144 && value.none { it == '\u0000' })
                values[name] = value
            }
            NativeTriSign(id, signId, values)
        }
        validate(NativeTriSession(format, signs))
    }.getOrNull()

    fun encode(session: NativeTriSession): ByteArray? = runCatching {
        validate(session)
        val doc = NativeSigningXml.newDocument()
        val root = doc.createElement("xml"); doc.appendChild(root)
        val group = doc.createElement("firmas"); root.appendChild(group)
        session.format?.let { group.setAttribute("format", it) }
        for (sign in session.signs) {
            val element = doc.createElement("firma"); element.setAttribute("Id", sign.id)
            sign.signatureId?.let { element.setAttribute("signid", it) }; group.appendChild(element)
            for ((key, value) in sign.parameters) element.appendChild(doc.createElement("param").apply {
                setAttribute("n", key); textContent = value
            })
        }
        NativeSigningXml.serialize(doc)
    }.getOrNull()

    fun validate(session: NativeTriSession): NativeTriSession {
        require(session.signs.size in 1..32)
        session.format?.let { require(safeText(it, 128)) }
        val identities = HashSet<Pair<String, String?>>()
        var size = 0L
        session.signs.forEach { sign ->
            require(safeText(sign.id, 256) && (sign.signatureId == null || safeText(sign.signatureId, 256)))
            require(identities.add(sign.id to sign.signatureId) && sign.parameters.size <= 64)
            sign.parameters.forEach { (name, value) ->
                require(NAME.matches(name) && value.length <= 262_144 && value.none { it == '\u0000' })
                size += name.length + value.length
            }
        }
        require(size <= 1_048_576)
        return session
    }
    private fun children(parent: Element): List<Element> {
        var node = parent.firstChild
        while (node != null) {
            require(node.nodeType == Node.ELEMENT_NODE || node.nodeType == Node.COMMENT_NODE ||
                ((node.nodeType == Node.TEXT_NODE || node.nodeType == Node.CDATA_SECTION_NODE) && node.textContent.isBlank()))
            node = node.nextSibling
        }
        return NativeSigningXml.children(parent)
    }
    private fun attributes(node: Element, allowed: Set<String>) {
        require(node.namespaceURI.isNullOrEmpty())
        for (i in 0 until node.attributes.length) require(node.attributes.item(i).nodeName in allowed)
    }
    internal fun safeText(text: String, max: Int): Boolean = text.isNotEmpty() && text.length <= max &&
        text.none { Character.isISOControl(it) || it in '\u202a'..'\u202e' || it in '\u2066'..'\u2069' }
    private val NAME = Regex("[A-Za-z0-9_]{1,64}")
}

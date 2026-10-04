package dev.junta.firmamobile.afirma.servlet

import dev.junta.firmamobile.certificate.UnlockedIdentity
import dev.junta.firmamobile.signing.JcaLocalSignatureEngine
import dev.junta.firmamobile.signing.LocalSignatureResult
import dev.junta.firmamobile.signing.SigningAlgorithm
import java.security.Signature
import java.util.Base64
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext

/** PRE/PK1 processing shared by single and batch protocols. The service defines
 * PRE; the UI must disclose this delegated signing model, not claim the phone
 * independently recovered the original document from an opaque pre-signature. */
internal object NativeTriphaseCodec {
    suspend fun sign(session: NativeTriSession, identity: UnlockedIdentity, algorithm: SigningAlgorithm,
        checkpoint: suspend () -> Unit): NativeTriSession {
        NativeTriphaseXml.validate(session)
        // Validate the whole collection before the first use of the key.
        var sum = 0L
        session.signs.forEach { item ->
            require("PK1" !in item.parameters)
            val pre = AfirmaServletInvocationParser.strictBase64(item.parameters["PRE"] ?: error("Missing PRE"), 262_144)
            try { require(pre.isNotEmpty()); sum += pre.size } finally { pre.fill(0) }
            for (name in listOf("NEED_PRE", "NEED_DATA")) item.parameters[name]?.let { require(it.equals("true", true) || it.equals("false", true)) }
        }
        require(sum <= 1_048_576L)
        val engine = JcaLocalSignatureEngine(maxInputBytes = 262_144, maxOutputBytes = 16_384)
        val signed = mutableListOf<NativeTriSign>()
        for (item in session.signs) {
            checkpoint(); currentCoroutineContext().ensureActive()
            val bytes = AfirmaServletInvocationParser.strictBase64(item.parameters.getValue("PRE"), 262_144)
            val encoded = try {
                val generated = withContext(Dispatchers.Default) { currentCoroutineContext().ensureActive(); engine.sign(bytes, identity, algorithm) }
                check(generated is LocalSignatureResult.Success) { "Local pre-signature failed" }
                generated.signature.use { result ->
                    result.withBytes { value ->
                        check(Signature.getInstance(algorithm.wireName()).run { initVerify(identity.certificate.publicKey); update(bytes); verify(value) })
                        Base64.getEncoder().encodeToString(value)
                    }
                }
            } finally { bytes.fill(0) }
            checkpoint()
            val fields = LinkedHashMap(item.parameters)
            fields["PK1"] = encoded
            if (!fields["NEED_PRE"].equals("true", true)) fields.remove("PRE")
            signed += item.copy(parameters = fields)
        }
        return NativeTriSession(session.format, signed)
    }

    fun parseJson(value: Map<String, Any?>): NativeTriSession {
        require(value.keys.all { it in setOf("format", "signinfo", "signs") })
        val format = value["format"] as? String
        val flat = if (value.containsKey("signs")) {
            require(!value.containsKey("signinfo"))
            (value["signs"] as? List<*> ?: error("Invalid signs")).flatMap { group ->
                val fields = objectValue(group)
                require(fields.keys.all { it in setOf("id", "format", "signinfo") })
                fields["signinfo"] as? List<*> ?: error("Missing signinfo")
            }
        } else value["signinfo"] as? List<*> ?: error("Missing signinfo")
        val signs = flat.map { item ->
            val map = objectValue(item)
            require(map.keys.all { it in setOf("id", "signid", "params") })
            val parameters = objectValue(map["params"]).mapValues { (_, raw) -> raw as? String ?: error("Parameter is not text") }
            NativeTriSign(map["id"] as? String ?: error("Missing signature ID"), map["signid"] as? String, parameters)
        }
        return NativeTriphaseXml.validate(NativeTriSession(format, signs))
    }

    fun encodeJson(session: NativeTriSession): ByteArray {
        NativeTriphaseXml.validate(session)
        val root = linkedMapOf<String, Any?>()
        session.format?.let { root["format"] = it }
        root["signinfo"] = session.signs.map { sign -> linkedMapOf<String, Any?>("id" to sign.id, "params" to sign.parameters).apply {
            sign.signatureId?.let { put("signid", it) }
        } }
        return NativeProtocolJson.encode(root)
    }
    fun SigningAlgorithm.wireName(): String = when (this) {
        SigningAlgorithm.SHA1_WITH_RSA -> "SHA1withRSA"
        SigningAlgorithm.SHA256_WITH_RSA -> "SHA256withRSA"
        SigningAlgorithm.SHA384_WITH_RSA -> "SHA384withRSA"
        SigningAlgorithm.SHA512_WITH_RSA -> "SHA512withRSA"
    }
    @Suppress("UNCHECKED_CAST")
    internal fun objectValue(value: Any?): Map<String, Any?> = value as? Map<String, Any?> ?: error("JSON object expected")
}

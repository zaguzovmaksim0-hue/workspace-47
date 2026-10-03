package dev.junta.firmamobile.catalog

import java.net.URLEncoder
import java.util.Base64
import dev.junta.firmamobile.afirma.servlet.nativePadesFixture

internal object UniversalAutoFirmaFixtures {
        private fun b64(value: String) = Base64.getUrlEncoder().encodeToString(value.toByteArray())
        private fun enc(value: String) = URLEncoder.encode(value, "UTF-8")
        private fun request(operation: String, extra: Map<String, String> = emptyMap()): String =
            "afirma://$operation?" + (mapOf("id" to "Universal123", "stservlet" to "https://storage.synthetic.example/put") + extra)
                .entries.joinToString("&") { enc(it.key) + "=" + enc(it.value) }
        fun requests(): List<Pair<String, String>> {
            val sign = mapOf("algorithm" to "SHA256withRSA", "dat" to b64("synthetic data"))
            val batch = """{"algorithm":"SHA256withRSA","format":"CAdES","singlesigns":[{"id":"one","datareference":"QUJDRA=="}]}"""
            val cades = request("sign", sign + ("format" to "CAdES"))
            return listOf(
                "CAdES" to cades,
                "XAdES" to request("sign", sign + ("format" to "XAdES")),
                "PDF" to request("sign", sign + mapOf("format" to "PAdES", "dat" to Base64.getUrlEncoder().encodeToString(nativePadesFixture()))),
                "certificate-selection" to request("selectcert"),
                "local-JSON-batch" to request("batch", mapOf("dat" to b64(batch), "jsonbatch" to "true", "localBatchProcess" to "true")),
                "XAdES-three-phase" to request("sign", sign + mapOf("format" to "XAdEStri", "serverurl" to "https://signer.synthetic.example/service")),
                "deferred-request" to "afirma://sign?fileid=Universal123&rtservlet=https%3A%2F%2Fretriever.synthetic.example%2Fretrieve",
                "Android-intent" to ("intent:" + cades.substringAfter("afirma:") + "#Intent;scheme=afirma;package=es.gob.afirma;end"),
            )
        }
    }

package dev.junta.firmamobile.network

import java.net.InetAddress
import java.net.URI
import org.junit.Assert.*
import org.junit.Test

class StaBatchHttpTransportTest {
    @Test fun allFiveProductionPoliciesAdmitOnlyTheirExactValidatedPostEndpoint() {
        val policies: List<Pair<String, (String) -> MelillaBatchUrlValidation>> = listOf(
            "sede.melilla.es" to MelillaBatchUrlPolicy()::validate,
            "tramites.juntaex.es" to ExtremaduraBatchUrlPolicy()::validate,
            "sedeelectronica.cabildodelapalma.es" to LaPalmaBatchUrlPolicy()::validate,
            "ovc24.dphuesca.es" to HuescaBatchUrlPolicy()::validate,
            "registro.diputaciondeburgos.es" to BurgosBatchUrlPolicy()::validate,
        )
        for ((host, policy) in policies) {
            var executions = 0
            val transport = StaBatchHttpTransport(policy) { endpoint ->
                HttpsProfileHttpTransport(
                    urlPolicy = SafeNetworkUrlPolicy(setOf(endpoint)),
                    dnsResolver = DnsResolver { listOf(InetAddress.getByAddress(byteArrayOf(8, 8, 8, 8))) },
                    executor = ProfileHttpExecutor { url, _, _, _, _, _, _, _ ->
                        assertEquals(endpoint, url)
                        executions++
                        RawProfileHttpResponse(200, "text/plain", null, "{}".toByteArray())
                    },
                )
            }
            for (operation in listOf("presign", "postsign")) {
                val url = URI("https://$host/sta/AutofirmaLote/$operation/op-g54-a")
                ProfileHttpRequest(ValidatedNetworkUrl(url), "synthetic".toByteArray()).use { request ->
                    val result = transport.post(request, ProfileHttpCancellation())
                    assertTrue("$host $operation: $result", result is ProfileHttpResult.Success)
                    (result as ProfileHttpResult.Success).response.close()
                }
            }
            ProfileHttpRequest(ValidatedNetworkUrl(URI("https://unrelated.example/sta/AutofirmaLote/presign/op-g54-a")), "synthetic".toByteArray()).use {
                assertEquals(ProfileHttpFailure.INVALID_ENDPOINT, (transport.post(it, ProfileHttpCancellation()) as ProfileHttpResult.Failure).code)
            }
            assertEquals(2, executions)
        }
    }
}

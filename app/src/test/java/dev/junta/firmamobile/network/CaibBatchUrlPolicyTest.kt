package dev.junta.firmamobile.network

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

class CaibBatchUrlPolicyTest {
    private val policy = CaibBatchUrlPolicy()

    @Test
    fun acceptsOnlyExactRequestPluginPairWithSameToken() {
        val pre = "$ORIGIN/portafibback/public/signmodule/requestPlugin/$TOKEN/-1/BatchPresigner"
        val post = "$ORIGIN/portafibback/public/signmodule/requestPlugin/$TOKEN/-1/BatchPostsigner"
        val pair = policy.validatePair(pre, post)
        assertNotNull(pair)
        assertEquals(TOKEN, pair?.first?.requestToken)

        assertNull(policy.validatePair(pre, post.replace(TOKEN, OTHER_TOKEN)))
        assertNull(policy.validate(pre.replace("https://", "http://"), CaibBatchOperation.PRESIGN))
        assertNull(policy.validate(pre.replace("intranet.caib.es", "evil.intranet.caib.es"), CaibBatchOperation.PRESIGN))
        assertNull(policy.validate("$pre?x=1", CaibBatchOperation.PRESIGN))
        assertNull(policy.validate("$pre#x", CaibBatchOperation.PRESIGN))
        assertNull(policy.validate(pre.replace(TOKEN, "short"), CaibBatchOperation.PRESIGN))
        assertNull(policy.validate(pre.replace("BatchPresigner", "BatchPostsigner"), CaibBatchOperation.PRESIGN))
    }

    private companion object {
        const val ORIGIN = CaibBatchUrlPolicy.ORIGIN
        const val TOKEN = "ABCDEFGHIJKLMNOPQRSTUVWX1234"
        const val OTHER_TOKEN = "BCDEFGHIJKLMNOPQRSTUVWXY1234"
    }
}

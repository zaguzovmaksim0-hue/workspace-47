package dev.junta.firmamobile.network

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

class LugoBatchUrlPolicyTest {
    private val policy = LugoBatchUrlPolicy()

    @Test
    fun acceptsOnlyExactLugoMultiNodePairWithSameSession() {
        val pre = "$ORIGIN/opencms/clientsigner/BatchPresigner/service/$SESSION"
        val post = "$ORIGIN/opencms/clientsigner/BatchPostsigner/service/$SESSION"
        val pair = assertNotNull(policy.validatePair(pre, post)) as Pair<*, *>
        assertEquals(SESSION, (pair.first as LugoBatchUrlBinding).sessionId)

        assertNull(policy.validatePair(pre, post.replace(SESSION, OTHER_SESSION)))
        assertNull(policy.validate(pre.replace("https://", "http://"), LugoBatchOperation.PRESIGN))
        assertNull(policy.validate(pre.replace("sede.deputacionlugo.org", "evil.sede.deputacionlugo.org"), LugoBatchOperation.PRESIGN))
        assertNull(policy.validate("$pre?xml=x", LugoBatchOperation.PRESIGN))
        assertNull(policy.validate("$pre#fragment", LugoBatchOperation.PRESIGN))
        assertNull(policy.validate(pre.replace(SESSION, "not-a-session"), LugoBatchOperation.PRESIGN))
        assertNull(policy.validate(pre.replace("BatchPresigner", "BatchPostsigner"), LugoBatchOperation.PRESIGN))
    }

    private companion object {
        const val ORIGIN = LugoBatchUrlPolicy.ORIGIN
        const val SESSION = "A1B2C3D4E5F60718293A4B5C6D7E8F90"
        const val OTHER_SESSION = "B1C2D3E4F5A60718293A4B5C6D7E8F90"
    }
}

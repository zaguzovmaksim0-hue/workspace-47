package dev.junta.firmamobile.signing

import java.security.Provider
import org.bouncycastle.jce.provider.BouncyCastleProvider

/** One isolated BouncyCastle provider per process; never added to java.security.Security. */
internal object BundledBouncyCastle {
    val provider: Provider by lazy(LazyThreadSafetyMode.SYNCHRONIZED) { BouncyCastleProvider() }
}

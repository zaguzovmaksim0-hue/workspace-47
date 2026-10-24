---
id: 20261024090401
title: pkcs12-provider-ambiguity
principal: unknown
interest: unknown
hotspot: app/src/main/java/dev/junta/firmamobile/certificate/Pkcs12Loader.kt
business_capability: certificate-import
payoff_trigger: A provider API exposes typed MAC and encryption failures without parsing exception messages or adding a PKCS12 fallback
quadrant: prudent-deliberate
category: code_quality
ai_authored: true
created: 2026-10-24
---

Android PKCS12 providers can flatten MAC verification and encryption failures into IOException without a cause. The standalone OpenJDK 21 synthetic tests also demonstrate identical IOException -> UnrecoverableKeyException chains, with no further cause, for a wrong container password and an unavailable encrypted-safe algorithm; neither alone proves a password error. Keep INVALID_PASSWORD_OR_FILE for these indistinguishable failures rather than infer a password or algorithm error from provider messages, Android API level, or the mere presence of PBES2/AES. Typed cause chains and demonstrably malformed input support precise diagnostics; actual old-API provider behavior remains outside JVM acceptance. See the Android PKCS12KeyStoreSpi source: https://android.googlesource.com/platform/prebuilts/fullsdk/sources/+/refs/heads/androidx-constraintlayout-release/android-35/com/android/internal/org/bouncycastle/jcajce/provider/keystore/pkcs12/PKCS12KeyStoreSpi.java .

# CertificateModule handoff

Issue #374: only certificate construction is extracted, preserving eager session/cache/gateway order and existing Application properties/setters. Focused JVM tests cover factory order/count, stable identities, separate module isolation and monotonic callback use without Android Keystore or certificates.

Whole AppContainer/ViewModel/NavHost extraction remains TODO. Android acceptance is pending parent CI. Tests and detekt 1.23.8 were not run in this generation-only step.

Parent validation: checksum-pinned scoped detekt 1.23.8 on CertificateModule and JuntaFirmaApplication reported zero findings with the existing unchanged baseline. Whitespace check passed. The three added unit tests have not been compiled or executed locally; canonical Android CI must establish acceptance. No local Gradle, Keystore, device, portal, real certificate, or review run was performed.

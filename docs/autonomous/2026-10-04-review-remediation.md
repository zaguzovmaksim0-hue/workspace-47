# Review remediation 2026 10 04

Baseline: a26968b52f05546eca362c4a45f96c61a17bbc4a (PR 339).

## Changes

- Explicit certificate lock blocks later restoration in memory even if record deletion fails. Android Keystore revocation invalidates leftover encrypted records across cache instances. Restore never creates a replacement key. Failed durable revocation is reported to the certificate UI while the current session is still locked.
- Cookie cleanup no longer claims complete origin deletion from URL-scoped cookie enumeration. The current-site result explicitly explains that cookies at other paths may remain. No unrequested global cookie deletion.
- PKCS12 import accepts signing-capable RSA certificates with digitalSignature or contentCommitment. Per-operation TLS certificate validation remains unchanged.
- Optimized JVM tests and lint join the exact-head CI gate. Debug-only recorder/credential-provider tests remain in Debug and QA through a shared development test source set; production tests remain shared.
- License documentation points to the current checked-in MIT LICENSE. No license text, copyright statement or third-party license is changed; the earlier Apache selection record is explicitly historical.

## Verification

Targeted standalone JVM probes reproduced the original lock/cookie/import bugs before changes. After changes the cache probe denies restore for normal, no-op and throwing deletion; both signing-capable synthetic RSA KeyUsage forms import. No user certificate, government operation, E2E or VPN changes.

Full Android acceptance remains the exact-head GitHub Actions result. Repository branch protection is an external administration setting and is not established by these source changes. No blanket readiness, R8 cause, all-portal compatibility or installed-APK claim follows from a source commit.

## Optimized UI test host

Robolectric uses the shipped merged manifest; adding ui-test-manifest only to testImplementation did not register its Activity there. The shared JVM-only Compose rule now registers the empty synthetic ComponentActivity in ShadowPackageManager before the normal Compose rule launches it. No application component is replaced, no assertions are skipped, and the delivered optimized APK is still checked to exclude this test host.

# EC P-256/P-384 engine and import boundary — audit and pending plan

Date: 2026-10-18

## Status and authority

- **Documentation only. No EC behavior or tests were implemented by this audit.**
- Exact audited revision: `5cb79e778aa8ffaf633d9123abb0607d8f97fe9a`
  (`fix: honor MITES SHA512 CAdES contract (#375)`). This is a pinned source
  snapshot, not a claim about the latest `main`, PRs or CI.
- Prior design: [#374 certificate/EC source-audit comment][prior-design]. This
  handoff does not close the roadmap item or establish portal compatibility.
- Conservative direction: a **possible future engine-only candidate**. **Do not
  begin implementation until types and the capability boundary are signed off.**
  Engine-only is not approval to enable global import, TLS or profile algorithms.

## Findings at the audited revision

Paths below are relative to `app/src/main/java/dev/junta/firmamobile/` unless
explicitly identified as tests. Evidence links are pinned to the audited SHA.

| Surface | Verified boundary / consequence |
| --- | --- |
| [`certificate/Pkcs12Loader.kt`][loader] | Rejects multiple private entries before selection; accepts RSA key/certificate types only; proves key match with `SHA256withRSA`. Existing 10 MiB input, 32-alias and 16-certificate limits must survive later import work. |
| [`signing/SigningModels.kt`][algorithms], [`LocalSignatureEngine.kt`][raw-engine] | The signing enum and raw engine are RSA-only. Software RSA uses the isolated bundled provider; opaque keys retain native JCA dispatch without export or retry. EC needs an explicit key/curve/digest policy, not just another JCA name. |
| [`signing/CadesSigner.kt`][cades-signer] | RSA-only standalone detached CMS signer. Source search found no production consumers outside its own declarations. Extending it alone would not enable the running native flow. |
| [`signing/LocalCadesDetachedAdapter.kt`][aragon-cades] | Despite its name, an exact Aragon SIRAW SHA1/RSA portal adapter, not a generic local EC entrypoint. Shared `CadesDetachedCodec` captures a placeholder sized from an `RSAPublicKey` modulus. Do not generalize this portal contract. |
| [`afirma/servlet/NativeCadesEngine.kt`][native-cades], [`NativeAfirmaOperation.kt`][native-operation] | Actual native CMS path is separate. It gates RSA signing and independently checks RSA digest/signature OIDs on verification. Its consumers also include other formats/operations; broadening this shared engine must not silently enable PAdES, co-sign or prehashed routes. |
| [`SigningCoordinator.kt`][single-order], [`BatchSigningCoordinator.kt`][batch-order] | `adapter.prepare` runs before the local key/algorithm rejection. Later EC import requires compatibility preflight before prepare/network side effects, not only a failure in the signature engine. |
| [`browser/ClientCertificateValidation.kt`][tls-validation], [`browser/download/ClientTlsIdentity.kt`][download-tls] | Shared client-auth validation already permits EC by default; download TLS builds a key manager from the selected identity. Global EC import is therefore not an isolated signing change. |

The no-production-consumer finding for `CadesSigner` is reproducible with
`git grep -n -E 'BouncyCastleCadesSigner|CadesSigner' <audited-SHA> -- app/src/main`.

## Existing RSA-only perimeter: do not widen

[Runtime adapter registration][runtime-adapters] and the separate
[profile signature-algorithm model][profile-algorithms] do not grant EC support.
Keep every existing profile/version/origin/algorithm/format/callback tuple exact:

- CAdES portal adapters, including Aragon, DGT, UGR, JCCM, Canarias,
  Lleida/Badajoz, Eivissa, Tenerife, **MITES and Cantabria**. MITES/Cantabria's
  SHA512/RSA tuples are not ECDSA approvals.
- XAdES: `LocalXadesDetachedAdapter`, `PoliciaXadesDetachedAdapter`,
  `SevillaAtseXadesEnvelopingAdapter`, Airef/CDTI and Transportes. RSA public-key
  assembly and XML signature URIs/encoding are separate from DER ECDSA.
- PAdES wrappers/codecs: MiniApplet, Acceda, Gran Canaria, Fuerteventura,
  Transparencia and Mineco; Junta/Ofvirtual/Unizar/Xunta tri-phase; all existing
  Melilla-family, Lugo and CAIB batch signing paths.
- Native servlet/batch parsers and `NativeMultiPhaseOperation` remain outside
  any EC activation. No global enum-to-all-algorithms set, parser fallback,
  profile/catalog change, new origin or new transport destination.

## Possible next candidate — blocked, engine-only

After written sign-off, keep the first candidate below the protocol boundary:

1. Define a small internal key/curve/digest/encoding contract (proposed
   `signing/EcKeyPolicy.kt`; final type placement is **not decided**). Allow only
   **P-256 + SHA256withECDSA** and **P-384 + SHA384withECDSA**. Match actual curve
   parameters and certificate/key compatibility, not just `EC` or a bit count.
   Reject unsupported/unknown curves, cross-curve/hash pairs and RSA/EC mismatch.
2. Bound the initial key source to software EC. Preserve the existing RSA opaque
   path; do not claim opaque EC/AndroidKeyStore/KeyChain support. Select the
   software provider once; never read private-key encoding, reorder global
   providers, retry signing with another provider or export a key.
3. Decide the exact engine API and output before editing. Raw JCA DER ECDSA
   belongs in `LocalSignatureEngine.kt`. For CMS, choose explicitly between
   `NativeCadesEngine.kt` and the standalone `CadesSigner.kt`; neither choice
   authorizes runtime wiring. DER `(r,s)` and CMS signature OIDs/parameters need
   explicit verification. XMLDSig/P1363 conversion is excluded.
4. If a shared `SigningAlgorithm` enum must change, enumerate every exhaustive
   mapping and add explicit unsupported handling at excluded consumers. A new
   engine algorithm must not become a profile, bridge, batch, PDF, XML or
   tri-phase capability merely to make the code compile.

No importer, UI, TLS, profile, catalog, parser, callback or runtime activation is
part of this candidate. The earlier possible single native local detached CAdES
`SIGN` slice is a **later design option**, not a selected or authorized scope.
PAdES/XAdES, batch, remote/tri-phase, co-/countersign, policy and precomputed-hash
variants are excluded. No challenge/digest/envelope is described as an inspected
original document.

## Required future test matrix — none implemented or run here

Test paths below are under `app/src/test/java/dev/junta/firmamobile/`.

| Seam / paths | Required evidence after scope approval |
| --- | --- |
| `certificate/TestCertificateFactory.kt`, `signing/SigningTestIdentity.kt` | Explicit named-curve P-256/P-384 synthetic fixtures. Existing EC factory initializes only a 256-bit generator; existing non-exportable wrapper is RSA-specific. Add an EC wrapper whose encoding accessor fails; never use real identities. [Factory evidence][ec-fixture]; [wrapper evidence][key-fixture]. |
| Proposed `signing/EcKeyPolicyTest.kt`, `signing/LocalSignatureEngineTest.kt` | Both allowed pairs; cross-curve/hash, unsupported curves, RSA/EC and key/certificate mismatch; verify DER signatures independently; mutated input/signature fails; bounds, cleanup, zero encoding reads, stable global provider order and no second-provider retry. Retain RSA regressions. |
| `afirma/servlet/NativeCadesEngineTest.kt` or `signing/CadesSignerTest.kt`, only for the selected CMS engine | Independently parse and verify generated detached CMS; bind original content and certificate; assert digest/signature OIDs and parameters, DER signature encoding and signing-certificate attribute. Reject changed content/certificate/OIDs and retain input/output bounds. The engine's own verifier is not the sole oracle. |
| Existing parser/adapter/registry/coordinator tests | Preserve RSA tuples and reject EC at excluded paths. Include synthetic EC identity + RSA request and any newly representable ECDSA request; unsupported operations must not reach prepare/network, signing or success delivery. Test this firewall without enabling global import. |

## Separate later import work — not authorized

Only after supported engine paths and a separately approved activation boundary:

- `certificate/Pkcs12Loader.kt`: use the approved curve/algorithm policy for
  import and key-match proof. Keep validity/key-usage checks, certificate-only
  rejection, multi-private-key rejection, limits and sensitive-copy cleanup.
  Alias selection stays independent; never silently choose the first key.
- `certificate/Pkcs12LoaderTest.kt`: retain the current EC-rejection baseline
  until import is explicitly enabled; then replace it with precise positive and
  unsupported-curve/invalid-key/mismatch cases, not blanket EC acceptance.
- `signing/SigningCoordinator.kt` / `BatchSigningCoordinator.kt`: reject an
  incompatible identity before adapter preparation. Preserve consent, ownership,
  cancellation, lock and expiry boundaries.
- Explicitly review client-auth, certificate-selection and download TLS consumers
  (`ClientCertificateValidation.kt`, `ClientAuthWebViewClient.kt`,
  `InteractiveClientAuthController.kt`, `download/ClientTlsIdentity.kt`) and
  their tests. Do not inherit EC permission from existing generic filters.
- Agree error taxonomy before adding `CertificateErrorCode` values: unsupported
  type/curve/provider is not automatically invalid password or key mismatch.
  Any UI mapping belongs in `ui/CertificateViewModel.kt` and string resources.

## Sign-off needed before implementation

- [ ] Exact engine-only types/API and whether the shared enum changes.
- [ ] Raw DER only, or raw DER plus one explicitly selected detached CMS engine.
- [ ] Software-only key policy and fail-closed behavior for opaque EC handles.
- [ ] Explicit rejection boundary for every unchanged RSA-only consumer.
- [ ] Confirmation that global import, TLS, profile algorithms and runtime wiring
      remain disabled/out of scope; later activation needs separate approval.

## Verification and continuation

This is static source inspection at the pinned revision. No behavioral test,
Gradle gate, CI result, instrumentation, R8 smoke, real portal/account/certificate
flow, persistent key, APK generation/distribution/install/uninstall or compatibility
upgrade is claimed. No code, workflow or catalog was changed. Do not close #374.

Suggested skills after authorization: follow `docs/agents/matt-pocock-workflow.md`;
use `codex/ask-matt` for unresolved type/boundary design, then `codex/implement`,
`codex/tdd` and `codex/code-review` only for the approved bounded candidate.
Repository verification policy remains `docs/agents/github-actions-verification.md`;
this handoff does not authorize publishing a branch or starting an Android gate.

[prior-design]: https://github.com/zaguzovmaksim0-hue/workspace-47/issues/374#issuecomment-6253397228
[loader]: https://github.com/zaguzovmaksim0-hue/workspace-47/blob/5cb79e778aa8ffaf633d9123abb0607d8f97fe9a/app/src/main/java/dev/junta/firmamobile/certificate/Pkcs12Loader.kt#L51-L150
[algorithms]: https://github.com/zaguzovmaksim0-hue/workspace-47/blob/5cb79e778aa8ffaf633d9123abb0607d8f97fe9a/app/src/main/java/dev/junta/firmamobile/signing/SigningModels.kt#L22-L27
[raw-engine]: https://github.com/zaguzovmaksim0-hue/workspace-47/blob/5cb79e778aa8ffaf633d9123abb0607d8f97fe9a/app/src/main/java/dev/junta/firmamobile/signing/LocalSignatureEngine.kt#L54-L118
[cades-signer]: https://github.com/zaguzovmaksim0-hue/workspace-47/blob/5cb79e778aa8ffaf633d9123abb0607d8f97fe9a/app/src/main/java/dev/junta/firmamobile/signing/CadesSigner.kt#L86-L126
[aragon-cades]: https://github.com/zaguzovmaksim0-hue/workspace-47/blob/5cb79e778aa8ffaf633d9123abb0607d8f97fe9a/app/src/main/java/dev/junta/firmamobile/signing/LocalCadesDetachedAdapter.kt#L119-L219
[native-cades]: https://github.com/zaguzovmaksim0-hue/workspace-47/blob/5cb79e778aa8ffaf633d9123abb0607d8f97fe9a/app/src/main/java/dev/junta/firmamobile/afirma/servlet/NativeCadesEngine.kt#L106-L245
[native-operation]: https://github.com/zaguzovmaksim0-hue/workspace-47/blob/5cb79e778aa8ffaf633d9123abb0607d8f97fe9a/app/src/main/java/dev/junta/firmamobile/afirma/servlet/NativeAfirmaOperation.kt#L58-L90
[runtime-adapters]: https://github.com/zaguzovmaksim0-hue/workspace-47/blob/5cb79e778aa8ffaf633d9123abb0607d8f97fe9a/app/src/main/java/dev/junta/firmamobile/signing/RuntimeSigningAdapters.kt#L20-L67
[profile-algorithms]: https://github.com/zaguzovmaksim0-hue/workspace-47/blob/5cb79e778aa8ffaf633d9123abb0607d8f97fe9a/app/src/main/java/dev/junta/firmamobile/profile/ProfileModels.kt#L78-L104
[single-order]: https://github.com/zaguzovmaksim0-hue/workspace-47/blob/5cb79e778aa8ffaf633d9123abb0607d8f97fe9a/app/src/main/java/dev/junta/firmamobile/signing/SigningCoordinator.kt#L215-L239
[batch-order]: https://github.com/zaguzovmaksim0-hue/workspace-47/blob/5cb79e778aa8ffaf633d9123abb0607d8f97fe9a/app/src/main/java/dev/junta/firmamobile/signing/BatchSigningCoordinator.kt#L144-L178
[tls-validation]: https://github.com/zaguzovmaksim0-hue/workspace-47/blob/5cb79e778aa8ffaf633d9123abb0607d8f97fe9a/app/src/main/java/dev/junta/firmamobile/browser/ClientCertificateValidation.kt#L10-L30
[download-tls]: https://github.com/zaguzovmaksim0-hue/workspace-47/blob/5cb79e778aa8ffaf633d9123abb0607d8f97fe9a/app/src/main/java/dev/junta/firmamobile/browser/download/ClientTlsIdentity.kt#L15-L36
[ec-fixture]: https://github.com/zaguzovmaksim0-hue/workspace-47/blob/5cb79e778aa8ffaf633d9123abb0607d8f97fe9a/app/src/test/java/dev/junta/firmamobile/certificate/TestCertificateFactory.kt#L341-L348
[key-fixture]: https://github.com/zaguzovmaksim0-hue/workspace-47/blob/5cb79e778aa8ffaf633d9123abb0607d8f97fe9a/app/src/test/java/dev/junta/firmamobile/signing/SigningTestIdentity.kt#L49-L79

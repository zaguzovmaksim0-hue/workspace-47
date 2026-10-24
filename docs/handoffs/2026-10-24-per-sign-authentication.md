# Per-sign device authentication — design handoff

Date: 2026-10-24. Status: proposal, documentation only; no authentication behavior implemented or accepted by this PR.

## Scope and evidence

Task 1 / feature 5 only. Preserve consent, origin/navigation ownership, certificate leases, strict verification, limits and protocol behavior. No portal adapters, dependencies, keys, signing configuration or production files change here. This is a security-policy proposal requiring owner review, not authorization to implement it wholesale.

Inspected base: `07b8232d` (`origin/main` when this worktree was created). Read `AGENTS.md`, `CONTEXT.md`, `CONTRIBUTING.md`, their workflow/verification guidance, and sections 3 and 6 of the supplied `PLAN_FIRMA_MOBILE.txt`. The plan explicitly separates acceptance of #369 from per-sign policy and forbids treating unit evidence as physical acceptance.

Inspected [PR #369](https://github.com/zaguzovmaksim0-hue/workspace-47/pull/369), including its diff, at head `1c31d577a4f9f6c519c06f60a09ed8e6700cffed`; it was OPEN and unmerged on inspection. Recheck its final merged implementation before coding against it.

Facts from that candidate, not claims about current main or OEM acceptance:

- `certificate/AndroidCertificateUnlockAuthentication.kt` uses the platform `android.hardware.biometrics.BiometricPrompt`, an exact Cipher `CryptoObject`, strong biometrics OR device credential on API 30+, a 60-second prompt bound, cancellation, and host identity/resumed checks.
- `certificate/CertificateUnlockCache.kt` authenticates both persistence and explicit restore using an AndroidKeyStore AES key with `setUserAuthenticationRequired(true)` and `setUserAuthenticationParameters(0, AUTH_BIOMETRIC_STRONG or AUTH_DEVICE_CREDENTIAL)`. This is the password-cache key, **not the PKCS12 signing key**.
- v2 storage replaces v1 without decrypting/migrating v1. API 26–29 remain memory-only. No automatic restoration at startup, foreground or memory pressure; existing 15-minute / 1-hour / 24-hour same-boot monotonic leases are not renewed by restoration. Generation/coroutine checks prevent resurrection after revocation.
- Physical PIN/biometric/Keystore behavior remains explicitly unaccepted in #369.

All source paths below are relative to `app/src/main/java/dev/junta/firmamobile/` unless stated otherwise.

## Decision: what is being authorized?

Keep three independent decisions:

1. **Unlock** makes a certificate identity available for its existing bounded session; #369 protects storing/restoring its password.
2. **Consent** authorizes the exact operation, document(s), origin, destination, format, algorithm and certificate shown by the application.
3. **Per-sign authentication** proves recent device-user presence for that already-consented operation. A native prompt cannot replace document review or consent and does not prove legal identity, qualified-signature status or portal acceptance.

Proposed initial policy: authenticate each logical signing operation, even if the certificate is already unlocked. One immutable, fully reviewed batch is one logical operation; it is not a reusable authorization for later batches. Do not ship a global “authenticated” Boolean or reuse cache-unlock success as signing approval.

| Mode | Proposed rule | Initial release decision |
| --- | --- | --- |
| Single signature / co-sign / countersign | Fresh prompt after explicit consent, before any signing execution; replay or explicit retry needs fresh consent and authentication | Default on supported devices once approved |
| Whole batch | One prompt for the exact ordered batch manifest and one operation ID; check scope before every item and before completion/upload | Allowed only when immutable membership and meaningful batch consent can be demonstrated |
| Every batch item | Separate review/prompt for each item; never a sequence of unexplained OS prompts | Conservative alternative; requires a separate UX/protocol review, not automatic fallback |
| Recent-auth timeout across independent requests | Would still require fresh consent for every request, same certificate/profile/origin and foreground host, monotonic non-sliding expiry and revocation | Deferred, disabled; no 15m/1h/24h reuse from certificate retention |

A batch whose individual members cannot be bound to the reviewed evidence must be rejected under the proposed whole-batch policy, not silently treated as authenticated merely because a count is visible. Server-side tri-phase input hashes must be bound to that same request; receiving an unexpected member/count/order after authentication aborts. Do not expand portal protocols to achieve this in the first implementation PR.

## Ordering, lifetime and failure rules

Proposed state machine: `Review -> Authenticating -> Executing -> terminal`, with a unique operation attempt and a revocable, in-memory authorization. Authentication is requested only after existing arming/double-tap protections and consent acceptance, never on page load, JS invocation, certificate selection or automatic foreground restoration.

- Validate the existing request/evidence/certificate snapshot first. Reserve the consented attempt atomically so double clicks, concurrent requests and duplicate callbacks cannot start another prompt or execute twice.
- Prompt deadline: at most 60 seconds, also bounded by the existing request/consent/certificate deadlines. Do not extend a consumed request's original expiry while waiting. Revalidate expiry explicitly after the suspension; the coordinators currently cancel their pending expiry job when confirmation starts.
- Authorization contains only operation ID/attempt, request fingerprint and consent lineage, certificate fingerprint and session generation, origin, navigation epoch, profile/version, host generation and deadline. For a batch include its ordered manifest commitment. Do not persist authorization or put secrets/documents in logs, SavedState or callbacks.
- On success recheck all bindings, cancellation, secure-device capability and lifecycle before releasing execution. Consume a single-operation authorization once; a batch execution handle permits only the approved member indices, not a second execution of the batch.
- Check existing ownership/certificate revocation between suspending phases, before each local signature, and before final delivery. Prompt success does not relax origin checks, strict result verification, network destinations or upload permission.
- Backgrounding outside the expected native credential UI, navigation/origin/profile changes, host replacement/destruction, process death, lock/delete/reimport/expiry of the certificate, policy change and explicit cancellation revoke the attempt. Delayed success must remain inert. Never resume signing automatically after process recreation.
- The system credential fallback UI may itself pause the host. Model that expected prompt lifecycle separately: no execution while paused, require the same valid host to resume before acceptance, keep the original deadline, and reject unrelated host/background transitions. Do not blanket-ignore `onStop` or weaken existing security cancellation to make credential fallback work; resolve and test this integration before implementation acceptance.
- Cancellation, negative/error callback, lockout, missing hardware/enrollment, timeout or capability loss produce no signature and no upload. Do not fall back to a cached Boolean, certificate password, weak biometric or old approval. Retry is an explicit user action creating a fresh attempt and renewed consent, never an automatic retry loop.
- A cancelled per-sign prompt revokes only signing authorization; it need not delete a valid password-cache record. Certificate lock/delete/revocation still follows the existing stricter cleanup policy. Partial batch failure must not deliver partial results as full success or re-sign completed items silently; retain existing cancellation/delivery semantics.

## CryptoObject versus presence-only authentication

**Initial proposal: application-enforced, presence-only per-sign prompt for the current software PKCS12 path.** Use a new operation-scoped prompt request with a narrowly scoped result; never call `authenticateCipher` without a real owned cipher. Reuse lifecycle/cancellation policy where safe, not #369's unlock-success result or an ambiguous `Boolean` shared between purposes.

The current signing identity is usable by software engines once unlocked. A bare success callback can gate application execution, but cannot cryptographically prevent an in-process compromise from using the loaded private key. State this limitation in the UX/security documentation. Do not describe it as hardware-enforced per-sign protection.

Alternatives and why they are not equivalent:

- **#369 AES CryptoObject:** keep it exactly for password-cache encryption/decryption. Passing that cipher to a signing prompt proves authorization for the AES operation, not for a signature over the reviewed document. Restoring first may require two prompts (unlock then sign); keep purpose-specific copy and explicit actions rather than silently merging approvals.
- **Dummy AES/signature CryptoObject:** reject as a substitute for protecting the actual signing key. It adds complexity without binding the real PKCS12 signing operation.
- **Actual AndroidKeyStore signing-key CryptoObject:** a later, separate design (task 2 / feature 7). Initialize the actual `Signature` with its non-exportable key and carry that exact instance through prompt and signing. Import feasibility, engine/provider compatibility, RSA/EC behavior, multi-item batches, invalidation and StrongBox/TEE are separate prerequisites. One per-use crypto authorization cannot be assumed to authorize N independent batch signing operations.

`setUserAuthenticationParameters` configures a **Keystore key**, not the prompt or a software PKCS12 `PrivateKey`. Timeout `0` means per-use authorization for that key; a positive timeout authorizes a time window and is not per-sign protection. Keep #369's AES configuration unchanged. Presence-only policy lifetime is an application-owned operation lifetime, not a reason to change the AES key timeout. A future hardware-key mode must align `setAllowedAuthenticators` with the actual key parameters and reject missing/wrong `CryptoObject` or operation failure even after a successful callback.

## Supported authenticators and unavailable protection

Initial supported per-sign platform tier: API 30+ using the existing platform BiometricPrompt approach, `BIOMETRIC_STRONG | DEVICE_CREDENTIAL`, no new dependency. Device credential means the **system** PIN, pattern or password, not an app PIN, certificate password, or a value entered into application UI. Allow credential-only devices when the platform supports that requested combination; use the OS-provided credential option, not a custom password form. Biometric lockout must not disable the permitted system credential path.

Check secure screen lock and available authenticators before offering protected signing and again on authorization. No screen lock, unsupported API, security-update requirement or no usable authenticator means **protected signing unavailable**; explain remediation without requesting secrets. No “continue anyway” path inside protected mode. Do not enroll biometrics or change the user's device settings automatically.

API 26–29: #369's memory-only cache fallback does not establish per-sign protection. First implementation should expose unsupported status rather than claiming equivalent assurance. Whether to retain an explicitly labelled existing unprotected mode on those devices is an owner/product decision before rollout, not a silent downgrade after enabling protection. Do not add an API 28/29 biometric-only or legacy credential flow without a separately reviewed compatibility matrix.

Disabling protection is an explicit settings action, cancels pending authorizations and must not be offered as the recovery path from an active signing error. Decide migration/defaults before release; this design does not silently change existing users' security settings. If a future implementation adds UI text, supply all five resource locales: `values`, `values-en`, `values-ca`, `values-gl`, `values-eu`.

## Integration map and bounded follow-up PRs

Existing source seams inspected:

| Location | Current responsibility / proposed insertion |
| --- | --- |
| `MainActivity.kt` | Owns ordinary/batch flow UI and lifecycle; show Authenticating state, serialize native prompts and bind/unbind host without doing cryptography in UI |
| `signing/SigningCoordinator.kt: prepare, confirm, cancel` | Owns pending-store consumption, document-consent lineage and signing phases; insert gate after existing acceptance validation and before adapter execution; revalidate after suspension and retain final checks |
| `signing/BatchSigningCoordinator.kt: confirm` | Uses certificate snapshot, prepares batch and loops over local signatures; bind reviewed manifest, authenticate once before execution, validate returned pre-sign ownership/membership and every item |
| `afirma/servlet/AfirmaConsentController.kt: confirm` | Separate native path: starts `executeWithCheckpoints` and guards final upload; requires the same policy before execution, not just an Activity button hook |
| `certificate/CertificateSession.kt: signingSnapshot, identityForSigning` | Existing snapshot/lease enforcement; bind authorization to session generation, do not turn synchronous identity access into implicit UI authentication |
| `certificate/AndroidCertificateUnlockAuthentication.kt` in #369 | Cipher-bound cache prompt only; coordinate prompt exclusivity, preserve exact-object validation and revoke late callbacks |
| `ui/SigningDocumentConsent.kt`, `ui/NativeAfirmaConsentDialog.kt` | Preserve review and arming; show separate signing-auth purpose, availability and cancellation states, without changing consent evidence |

No single Activity hook covers all callers; inventory direct engine/identity consumers again before implementation and prove no native, ordinary, batch or future offline path bypasses the gate. Do not change shared portal interfaces in a convenience refactor.

1. **Policy and state seam:** owner approves operation/batch semantics and unsupported-device behavior. Add the operation authorization module, typed outcomes, fake authenticator/clock and behavioral JVM tests. Keep it unattached until integrations are complete; do not advertise protection yet.
2. **Platform prompt:** add presence-only native prompt purpose and capability checks, exclusivity with #369, revocation and lifecycle tests. Scope touched files first; split if approaching 20 production files. Resolve expected system credential pause versus real background cancellation without relaxing the latter.
3. **Execution integration:** separate reviewable PRs for ordinary, batch and native-controller integration with red/green bypass/race tests. Feature remains unavailable as an advertised global guarantee until all enabled paths are covered. Unsupported batch forms fail closed; no adapter/protocol changes in this stage.
4. **UI, migration and acceptance:** resources in five locales, explicit defaults/migration decision, exact-SHA CI evidence and separately authorized physical acceptance. Hardware-key protection and timeout reuse remain separate proposals, not bundled follow-ups.

These are proposed files/call sites, not permission to edit them in this docs-only task. If implementation requires changing protocols, portal adapters or the security model beyond the approved policy, stop with a narrower handoff.

## Test plan without an emulator

Future tests must assert behavior through real module interfaces, not read Kotlin source strings or pin source hashes. No emulator, instrumentation, WebView E2E, APK installation or real administrative operation is part of this task.

| Layer | Cases and observable assertions |
| --- | --- |
| JVM authorization module | Already-unlocked identity still prompts; unlock approval is not signing approval; denied consent never prompts; success without consent never signs; duplicate callbacks/double confirm/concurrent requests execute at most once |
| JVM clock/revocation | Prompt/request/session deadlines at boundary, non-sliding monotonic time, wall-clock jumps, navigation/origin/profile/certificate changes, lock/delete, cancellation and process reconstruction; late success produces zero engine calls and zero uploads |
| JVM ordinary coordinator | Existing `SigningCoordinatorTest` plus fake auth: original lineage/pending-store rules survive suspension; mutated payload and certificate rejected; prepare/sign/complete order correct; existing strict verification unchanged |
| JVM batch coordinator | `BatchSigningCoordinatorTest`: exactly one prompt for one fully bound batch; foreign/missing/reordered/added member rejected; checks between items; cancellation/expiry mid-loop; explicit retry reprompts; no partial-success delivery |
| JVM native controller | `AfirmaConsentControllerTest`: auth before execute, revocation while suspended, preserved checkpoints and one-time upload guard; denied auth calls neither execution nor upload |
| Robolectric / UI doubles | Native prompt abstraction outcomes and host replacement; capability matrix for API 26/29/30/current; credential-only vs strong biometric; unsupported/no-lock/lockout/error; purpose copy, cancellation and all existing consent arming behavior |
| CI, exact final SHA | Ordinary PR CI/security jobs and focused JVM/Robolectric suites in GitHub Actions; inspect terminal conclusions, not queued status; skipped emulator/R8 jobs are not acceptance evidence |

Hardware-backed CryptoObject and authentic system credential behavior are **not proven by JVM/Robolectric or CI mocks**. After a future implementation, separately authorized physical-device acceptance must cover credential-only and strong-biometric devices on API 30 and a current Android version, cancel/retry/lockout, system fallback lifecycle, background/rotation/process death, removing screen lock/enrollment, and cache save/restore followed by independent signing authorization. Use synthetic certificates/documents; no portal submission. This acceptance is not executed here and must not be inferred from a green design PR.

## Platform references and residual risks

Implementation must recheck the exact API/provider contracts against these primary references:

- [Biometric authentication guidance](https://developer.android.com/identity/sign-in/biometric-auth): presence-only versus cryptographic authentication and authenticator compatibility.
- [BiometricPrompt](https://developer.android.com/reference/android/hardware/biometrics/BiometricPrompt) and [Builder](https://developer.android.com/reference/android/hardware/biometrics/BiometricPrompt.Builder): platform callbacks, CryptoObject and allowed authenticators.
- [KeyGenParameterSpec.Builder](https://developer.android.com/reference/android/security/keystore/KeyGenParameterSpec.Builder): `setUserAuthenticationParameters`, key validity and authentication requirements.
- [KeyguardManager](https://developer.android.com/reference/android/app/KeyguardManager): secure-device capability; not proof of consent or successful per-sign authentication.

Residual risks: software-key bypass by in-process compromise; OEM credential/prompt differences; system fallback lifecycle conflicting with existing cancellation; batch manifests not yet proven complete; native-path coverage; #369 still changing/unmerged; UI/default migration pending owner decision. No claim of physical authentication, legal assurance, universal portal compatibility or completed feature 5 is made by this design.

# 0002 — Local file signing with explicit SAF export

**Date:** 2026-10-24
**Status:** Accepted

## Context

Users need to sign their own document without a portal. Portal consent binds protocol
requests and origins; a locally chosen document has neither. The existing standalone
RSA CAdES signer already emits detached CMS and offline CAdES attributes.

## Decision

Use a separate local controller with a generation per selection and a certificate
snapshot captured for review. Display provider filename, actual byte count, SHA-256
and certificate owner before the existing 600 ms consent arming. Revalidate the
certificate snapshot before and after signing. Use SHA256withRSA and require
OfflineCmsVerifier success with the exact reviewed content before offering export.
Read at most the existing 512 KiB content limit. Keep content and signature in
clearable memory, with no app-private files, app network calls, TSA or persistent URI grants.
Export only through the user's CreateDocument result.

## Consequences

Portal adapters and policies remain unchanged. Cancellation invalidates pending
results; blocking provider/JCA calls can finish but their results are discarded and
cleared. A provider write failure can leave a partial document at the user-selected
destination; SAF cannot promise transactional writes across providers.
EXTRA_LOCAL_ONLY requests local content; provider behavior is outside the signing
engine's offline guarantee.

## Alternatives

1. Route local files through portal coordinators: reuses orchestration but invents
   an origin/protocol and risks broadening portal authorization.
2. Use Android share/app-private staging: simplifies transfer but retains document
   copies and does not provide the requested explicit SAF destination.

## Payoff trigger

Authorized physical-device SAF acceptance should validate cancellation, local-only
picker filtering and export failure behavior with real document providers. JVM
coverage proves controller and cryptographic behavior, not provider enforcement.

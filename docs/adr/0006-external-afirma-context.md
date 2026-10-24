# 0006 — External AutoFirma consent without browser provenance

**Date:** 2026-10-24
**Status:** Accepted; Android receiver enabled with shared admission (2026-10-24)

## Context

The owner's 2026-10-24 decision permits unknown external callers with explicit
per-delivery consent for native servlet operations. A VIEW URI proves no website
origin. BrowserScreen currently owns servlet, retrieval, popup and Client TLS
admission locally; MainActivity owns ordinary/batch signing separately and cancels
on background. Launching another Activity can therefore cancel a live operation
before that Activity checks admission.

## Decision

Implement the task's core-only fallback. Parse external syntax through the same
servlet parser with a distinct immutable external context and no source page.
Reject indirect retrieval and consent-suppression variants. Keep native operation
and transport implementations, and reuse the consent controller with a two-minute
window, fresh delivery ownership, certificate binding and local cancellation.
Display unknown caller and an external-source warning in the existing secure
consent dialog, with size/digest and query-free recipients visible during review.
Certificate selection explicitly has no document payload; display the selected
public certificate size and SHA-256, bound to the reviewed identity.

Do not expose an Android receiver or intent-filter in this slice. Android
integration must first share admission with the existing portal owners; it must
reject an incoming delivery while busy without changing the current operation.
The parser/controller API is not proof of safe Android lifecycle integration.

## Consequences

No incoming external URI executes in the shipped application yet. The core can
review all native operation kinds, without inventing TrustedOrigin, selecting a
profile or using WebView state. TLS/public DNS/no redirects/no retries, limits,
cookies and client-TLS policy remain unchanged. Unknown caller authenticity is
an accepted owner risk, never a trust claim. No result Intent is introduced.

## Alternatives

- Export a separate receiver Activity now: rejected because its launch can
  background/cancel a portal operation before busy admission is available.
- Route external requests through MainActivity and the portal adapters now:
  defer until shared ownership can cover all signing, retrieval, popup and Client
  TLS paths and their Android delivery tests. Borrowing a page origin is rejected.

## Payoff trigger

A shared admission contract covers portal signing, retrieval, Client TLS and
external deliveries without backgrounding or cancelling the current operation.
Then connect VIEW envelope validation, resumed/visible/focused/keyguard checks,
recreation rejection and the core, and test real Android delivery before enabling
an intent-filter. Full CI and physical interoperability remain unverified here.

## Android integration (2026-10-24)

The payoff trigger is met. `SigningAdmission` is one process-wide owner: the
browser screen registers a provider covering popup, native servlet, retrieval,
fallback, interactive and in-place Client TLS, certificate selection, MiniApplet,
Melilla batch and ordinary signing state; MainActivity registers its own
ordinary/batch ownership, external handoffs, pickers and the certificate panel.
A throwing provider counts as busy. While an external request holds admission,
every portal entry point refuses new work.

`ExternalAfirmaEntryActivity` is the only exported entry. It reads just the VIEW
action and `afirma:` data string (no extras, clip data or selectors), parks it
in a 30-second in-process slot and opens the app with the launcher intent, never
forwarding the URI. It keeps the app's task affinity, so a foreground app is
paused, not stopped, and its current operation survives. A second delivery while
one is parked is refused and never replaces it.

MainActivity takes the slot only when resumed. A busy app answers at once with
"busy" and leaves the current operation untouched; an idle app waits for window
focus and an unlocked keyguard, then gives each delivery exactly one outcome:
consent dialog, busy, rejected (parse/review failure) or expired (stale slot). An
explicit intent to MainActivity never carries a request. Leaving the app before
confirmation cancels the review; confirmation remains single-use with the
two-minute window and replay ledger above. Admission is released when the
consent dialog is dismissed or the Activity is destroyed.

Robolectric covers cold and warm delivery, busy in both directions, background
before confirmation, cancel, rejection and expiry. Physical interoperability
with real callers remains unverified.

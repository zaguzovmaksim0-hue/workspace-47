# 0006 — External AutoFirma consent without browser provenance

**Date:** 2026-10-24
**Status:** Accepted core; Android receiver disabled

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

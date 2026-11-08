# 0011 — Exact reviewed login launches retain inventory identity

**Date:** 2026-11-08
**Status:** Proposed

## Context

BOE, Fondos Europeos, IMSERSO and SEPES publish stable identification or consultation starts distinct from their historical inventory entries. Popup children intentionally have no selected certificate profile. The catalog already supports an exact `launchUrl`/profile `startUrl` pair, but its generator previously required a separate inventory entry whose `entry_url` was the start.

## Decision

Keep the original portalId, inventoryId, entry_url and surface_key. Prefer the unique exact entry_url owner; only when absent, accept one exact launch_url owner. Skip that precise primary record in the alias pass and preserve rejection of redundant aliases on ordinary entry owners. Unknown, missing and ambiguous owners fail closed. Bind the six Wave F QA login profiles with independent complete TLS pins, retaining the existing authorizer, popup isolation, TTL and signature boundaries.

## Consequences

The primary unprofiled button and explicit compatibility launch both open the published identification/consultation destination. Only the explicit profile path can obtain its selected TLS grant. Inventory descriptions must accurately name this destination; SEPES/CASA 47 exposes the shared Sisgest consultation cabinet, without claiming support for filing Quejas. Android certificate callbacks remain a separate acceptance gate.

## Alternatives

1. Add synthetic inventory entries for each login start. This would duplicate institutional cards and change catalog identity/count solely to satisfy a generator lookup.
2. Retain the old starts and carry certificate authority through popup children. This requires a new security model and contradicts the current profile-free popup boundary.

## Payoff trigger

Revisit when the catalog needs multiple reviewed login destinations for a single inventory surface, or protected main-only REAL E2E establishes that a selected published start no longer reaches its pinned Android certificate callback. Do not replace exact URL matching or inherit popup authority as a workaround.

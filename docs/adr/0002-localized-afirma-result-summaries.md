# 0002 — Localize AutoFirma result summaries at the UI boundary

**Date:** 2026-10-24
**Status:** Proposed

## Context

AutoFirma operations returned Spanish result summaries directly to Compose. Batch counters and the warning that a service result does not prove administrative acceptance must retain their meaning in every supported locale.

## Decision

Carry a service-result marker or batch document/positive counts in the internal consent prompt. Resolve Android resources in Compose using the current locale. Preserve the existing result statuses, counter calculation, publication timing and upload/consent checks.

## Consequences

Protocol code stays independent of Android resources. Summary consumers and tests now use typed facts. Format descriptions and certificate display-name fallbacks remain a separate localization group; this change does not alter their security or persistence contracts.

## Alternatives

1. Inject a localized string formatter into operation construction. This adds locale/context plumbing to signing and deferred retrieval and can freeze text before a locale change.
2. Pass Android resource IDs and arguments through protocol code. This couples the protocol layer to Android presentation resources.

## Payoff trigger

A follow-up localizes the remaining consent format descriptions and certificate fallback names without changing protocol validation or persisted certificate metadata.

# 0004 — Stage the detached CAdES orchestration behind existing adapters

**Date:** 2026-10-24
**Status:** Proposed

## Context

Plan #426 separates local detached CAdES orchestration from portal contracts. Aragón currently owns prepare/complete orchestration alongside the shared codec and state. Portal validators, error timing and completion revalidation differ, so migrating all adapters together would obscure changes to accepted requests and state ownership.

## Decision

Introduce an internal immutable exact detached-CAdES contract and a `ParameterizedCadesDetachedAdapter` implementing the existing `SigningProtocolAdapter`. Migrate only `LocalCadesDetachedAdapter`, preserving its class, constructor, ID and constants through delegation. Support only the policy needed by Aragón: exact serialized origin, tuple, payload length and properties, with no complete-time payload revalidation. Keep `CadesDetachedCodec` and `CadesPreSignState` in their current file.

Retain the chain guard before preparation, request-object identity ownership, consume-once behavior, `state.use` cleanup and existing error codes. A test-only snapshot of the previous Aragón orchestration provides a differential oracle for signed attributes, full CMS bytes and owner/consumption behavior; existing semantic goldens independently verify CMS content and signatures.

## Consequences

The first consumer establishes the orchestration seam without changing registry, callbacks or security policy. Other adapters retain their duplication until individually characterized and migrated. The descriptor intentionally has no speculative normalization, revalidation or certificate-aware hooks. No codec, algorithm, dependency or trust-policy change is included.

## Alternatives

- Keep all orchestration in each adapter: smallest immediate diff, but retains duplicated lifecycle and cleanup logic across subsequent migrations.
- Use shared prepare/complete functions: reduces some duplication but leaves each wrapper responsible for sequencing validation, consumption and cleanup.
- Migrate all local CAdES adapters into a universal descriptor: reduces duplication immediately, but combines differing portal policies and prevents isolated acceptance.

## Payoff trigger

Each separately authorized adapter migration in plan #426, followed by review of wrapper removal after the series. Extend the contract only for the next characterized consumer; do not switch a second adapter in this change. Tracked in `docs/debt/` as `staged-cades-migration`.

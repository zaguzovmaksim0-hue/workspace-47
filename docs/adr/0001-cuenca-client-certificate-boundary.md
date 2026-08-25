# 0001 — Cuenca client-certificate boundary

**Date:** 2026-08-21

**Status:** Accepted

## Context

The current Diputación Provincial de Cuenca electronic procedure exposes a
public general-registration entry point and a first-party identification flow.
Its JavaScript links the same short-lived `idtoken` from
`/segex/identificacion_opciones.aspx` to the shared Sedipualb@ client-certificate
origin with `idioma=es` and `entidad=16000`. The repository must represent the
observed authentication boundary without implying a document-signing ABI,
administrative acceptance, or release readiness.

## Decision

Add a dedicated `QA_ONLY` site profile for Cuenca with the exact procedure URL,
source path, shared client-auth origin, fixed entity/language parameters, and
linked ephemeral `idtoken`. Expose only `CLIENT_TLS_AUTH`; keep endpoints,
operation policies, signing formats, algorithms, and release activation empty or
disabled. Mark the inventory `IMPLEMENTED_NOT_E2E` and retain the physical
accepted-flow check as pending.

## Consequences

The app can exercise the narrow Cuenca certificate-auth transition in QA while
the release registry remains fail-closed. A future reader can distinguish the
current client-auth contract from the unproven downstream signature and filing
steps. The profile adds one maintained Sedipualb@ entity-specific contract and
requires a later authorized E2E review before any release promotion.

## Alternatives

1. Keep Cuenca `BROWSE_ONLY` until a physical E2E run proves the whole flow.
   This avoids a new profile but discards the independently observed and safely
   representable pre-sign authentication boundary.
2. Reuse the existing Albacete or León profile by analogy. This would avoid
   another validator/test but would conflate different source origins and
   entity bindings, weakening the exact trust boundary.

## Payoff trigger

An authorized Android E2E reaches the Cuenca client-certificate boundary and
validates the login transition without private-key signing or final filing.

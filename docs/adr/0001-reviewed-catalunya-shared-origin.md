# 0001 — Review-scoped Catalunya shared origin

**Date:** 2026-08-21
**Status:** Accepted

## Context

The ES-PUB-0105 Catalunya Petició genèrica profile uses the observed Cl@ve/eIdentifier route and redirects through `https://valid.aoc.cat`. The existing Barcelona ES-PUB-0145 profile already owns that exact origin, and `SiteProfileCatalogParser` rejects duplicate navigation origins unless the sharing is explicitly reviewed. The original integration candidate therefore failed during catalog initialization with `IllegalArgumentException`, before the Android unit suite could reach ordinary tests.

## Decision

Allow `https://valid.aoc.cat` to be shared only by `diputacion-barcelona-solicitud-generica-2057` and `catalunya-peticio-generica-client-auth` when both catalog entries expose it as a redirect origin in the parser's reviewed-origin rule. Keep the Catalunya profile QA-only and limited to `CLIENT_TLS_AUTH`; do not add signing formats, algorithms, callbacks, endpoints, or broader trusted browsing origins. Add regression assertions that the shared origin has exactly those two owners and that signing details remain unverified.

## Consequences

The reviewed integration candidate can load the complete built-in profile catalog while preserving exact-origin ownership for all other profiles. The exception remains a narrow code-level allowlist, so future shared-origin additions still require an explicit parser change and evidence review.

## Alternatives

1. Remove `valid.aoc.cat` from the Catalunya redirect origins. This would avoid the collision but would omit an observed authentication redirect from the profile contract.
2. Move shared-origin provenance into the profile/catalog schema and validate it generically. This would scale better, but it is a broader model and migration change than this integration requires.

## Payoff trigger

unknown

# 0010 — Dual-authority shared navigation origins

**Date:** 2026-10-25
**Status:** Proposed

## Context

The owner decision of 2026-10-24 requires both reviewed catalog declarations and independent Kotlin pins. Gate 0 (#445) characterizes the existing directional first-owner algorithm, including the AOC certificate exception and Sedipualba siblings. ADR 0001 explains the original AOC exception; its two-owner inventory is historical, while its security restrictions still apply.

## Decision

Extend schema v1 atomically with the recognized optional root field `reviewedOriginShares`. Each typed declaration contains one exact origin, one first owner, explicitly named later owners, exact redirect-role combinations (`NEITHER`, `FIRST`, `LATER`, `BOTH`) and an evidence reference already present in the first owner's evidence. A declaration is a collection of directed edges, with no reciprocal or transitive inference. Evidence documents review and grants no authority.

Before validating or returning any catalog, expand the declarations and require exact equality with the independent legacy predicate for all ordered pairs of profiles claiming each origin and all four redirect-role combinations. This rejects missing relations, extra owners, absent peers/origins, self references, duplicate/overlapping relations, incorrect roles and unbound evidence. An omitted field means an empty declaration set and therefore permits only catalogs needing no reviewed sharing relations. Order remains the existing `putIfAbsent` first-owner order; the ownership check additionally requires a matching declaration and retains its legacy predicate.

Limit declarations to 128, later owners per declaration to 32 and claimants per origin to 33 before enumerating pinned relations. Keep the existing 262,144-character and JSON depth limits. Preserve exact-key/type validation, canonical origins, all profile pins and all TLS, bridge, signing, consent and activation policies. The Python public-catalog generator recognizes the root extension only for its existing ID/start-URL projection; it does not validate runtime security.

## Consequences

Parser, typed model and canonical catalog must ship together. Catalog edits alone cannot widen access, and malformed declarations reject the entire catalog before registry publication. All potential reviewed directions/roles between present claimants must be declared even if the current first-owner order does not exercise them; this makes missing unused relations fail closed too. Grouping later owners only reduces JSON size; it does not create a symmetric owner group.

The catalog duplicates independently maintained pins deliberately. Gate 1 establishes the catalog seam only; gates 2–3 and physical interoperability remain separate work. ADR 0001's inventory rationale is extended by this decision, without retiring its executable pins or changing its security boundary.

## Alternatives

1. Make catalog declarations authoritative and remove Kotlin pins. Rejected by the owner: editing catalog data alone must not widen navigation access.
2. Declare only collisions exercised by the current catalog order. Smaller, but reordered fixtures would lack independently reviewed relations, and missing unused directions would be silently tolerated.
3. Use schema v2 or per-profile peer declarations. Both are viable coordinated migrations, but a root v1 extension keeps the relationship independent of profile ordering and avoids duplicating reciprocal profile metadata.

## Payoff trigger

Revisit duplicated pins only after the owner authorizes gate 4 and independent behavioral coverage proves the migrated consumer contracts; gates 2–3 are not part of this change.

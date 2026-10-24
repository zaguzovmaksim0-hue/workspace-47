# 0003 — Atomic certificate reference catalogue

**Date:** 2026-10-24
**Status:** Accepted for storage slice of #417

## Context

The certificate module currently persists one reference in Preferences DataStore.
#369 binds its authenticated unlock cache to the reference fields, and #421 reads
that reference through the reminder decorator. This slice must preserve both
contracts and existing replace/forget behavior without introducing selection UI.

## Decision

Store a versioned, bounded binary catalogue as one Base64 preference in the
existing DataStore. Entries have opaque UUIDs, positive reference revisions and
safe reference metadata only. Limit the catalogue to 16 references, reject
malformed/unknown schemas and duplicate exact URIs, and validate the active ID
against the same snapshot. Bound encoded size and individual fields before use.

Read and migrate legacy scalar preferences in one serialized DataStore edit:
commit the catalogue and remove only legacy keys atomically. Failed persistence
leaves the legacy reference available for retry; a committed ID survives restart.
No cache record or authentication binding is copied, decrypted or changed.
Unlike the full design handoff, this storage-only slice preserves #369 restore
for the unchanged active reference, as explicitly required by the slice request.

Expose catalogue reads and append-without-selection in the storage interface.
Keep existing callers on the active-reference facade: writing replaces only the
active entry (or refreshes its metadata); clearing removes only the active entry
and does not select a survivor. Explicit replacement keeps the entry ID;
reference metadata changes advance its revision, summary-only updates do not.
Removing and re-adding creates a new ID. No grant is acquired or released by
storage. Runtime callers continue to use the existing
repository validation, grant ownership and reminder decorator.

## Consequences

Existing single-certificate flows and unlock cache digests stay unchanged. A
corrupt catalogue fails closed even when stale legacy keys also exist. The empty
versioned snapshot remains persisted so migration cannot resurrect a legacy row.
Catalogue additions are storage groundwork only, not yet wired to runtime UI.

## Alternatives

- Separate indexed preferences for entries and active selection: also supports
  atomic edits, but requires validating dynamic keys and cleaning orphan fields.
- A separate database/DataStore: adds a second migration boundary and persistence
  lifecycle without a need for queries at this 16-entry bound.

## Payoff trigger

Before exposing multiple entries to users, implement selection/removal with
verified #369 cache revocation and entry/revision/exact-certificate binding,
stale-operation guards, URI grant ownership and active-only reminder integration.

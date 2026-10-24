# 0006 — Atomic keystore key catalogue commits

**Date:** 2026-10-24
**Status:** Draft, implementation of ADR 0005 slice 2

## Context

The catalogue and AndroidKeyStore cannot share a transaction. A process can die
between importing a verified key and publishing its reference, or between removing
a reference and deleting its alias. The existing PKCS#12 facade must keep working.

## Decision

Write catalogue schema 2, reading schema 1 without changing its IDs or revisions.
Each entry contains a file reference, a version 1 signing-key reference, or both.
The key record contains an owned random alias, confirmed STRONGBOX/TEE placement,
SHA-256 of the leaf certificate and a sanitized display name. Unspecified hardware
security levels are not relabelled as TEE and cannot be published in this slice.

Migration is an explicit operation loading the identity from the exact expected
file with its password and receiving acknowledgement that the key cannot be exported or recovered.
Import and verify every signing algorithm before a compare-and-replace catalogue
commit. Keep the original file reference until a separate optional commit forgets
it; the backup file itself is never deleted. Persistence and cleanup run without
coroutine cancellation splitting the commit boundary. If a failed commit has an
unreadable outcome, retain the alias until a later readable snapshot permits cleanup.

Removal and replacement atomically add the retired alias to a bounded tombstone
set before key deletion. Clear tombstones only after deletion succeeds. Recovery
reads the catalogue first and deletes only unreferenced aliases under
`dev.junta.firmamobile.signing_key_v1.`. All lifecycle instances share a process
mutex, so recovery cannot race an in-flight import. Storage edits also compare
complete entries and revisions to reject stale operations.

## Consequences

A crash before publication leaves a recoverable orphan and preserves the file.
A crash after publication keeps the verified key and file; after optional forgetting,
the key alone remains. Failed deletion leaves a durable tombstone for retry. The
128-tombstone bound applies backpressure rather than dropping pending deletions.
The active file facade remains unchanged; key-only entries are not exposed through
it. No runtime UI, TLS, password-cache restoration or default enablement is added.
The internal lifecycle defaults to disabled. Its unlock operation requires explicit
app-level authorization, rechecks the entry after authorization, verifies the leaf
fingerprint and key again, then creates an identity with keystorePlacement in the
normal expiring session.

## Alternatives

- Persist a pending alias journal before import. This adds a second commit and
  recovery states; the importer already uses a private prefix suitable for sweeping.
- Store the key records and deletion journal in a separate DataStore. This keeps
  schema 1 unchanged but introduces cross-store publication and deletion races.

## Payoff trigger

Before enabling slice 3, wire explicit migration/backup warnings and optional file
grant release into the authenticated UI, enforce verified unlock-cache/session
revocation for migration/removal, integrate key-only selection/reminders and TLS,
and obtain separately authorized physical StrongBox/TEE acceptance. The storage
lifecycle is deliberately disconnected until that work is accepted.

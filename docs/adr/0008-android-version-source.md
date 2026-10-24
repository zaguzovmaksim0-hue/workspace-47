# 0008 — Deterministic Android version source

**Date:** 2026-10-24
**Status:** Proposed

## Context

Android updates require an increasing version code. The published floor is 13;
versions must remain repeatable without a clock, commit count or remote service.
The existing local helper already protects writes and stale expectations.

## Decision

Store the unchanged code/name pair in root `version.properties`. Gradle validates
its strict two-line format and code range 13..2100000000 during configuration,
with no fallback. Reuse the guarded helper to increment the code by exactly one;
an optional name change must be numerically greater. Preserve signing and variants.

## Consequences

Builds and bumps share one version source. Python and Kotlin validators have
regression tests; standalone Kotlin tests execute the actual build validator.
The published floor remains an explicit reviewed constant, not a remote lookup.
Version changes across branches still need serialized release review.

## Alternatives

- Keep literals in Gradle and continue lexical rewriting: couples the helper to
  Kotlin layout and mixes release metadata with build logic.
- Use Gradle properties overridable with `-P`: permits build-specific version
  overrides outside the reviewed version file.

## Payoff trigger

When a code above 13 is published, advance the floor in both validators and tests.
If parallel publishers become necessary, revisit global allocation separately.

---
id: 20261024184707
title: published-version-floor
principal: unknown
interest: unknown
hotspot: app/build.gradle.kts
business_capability: android-release
payoff_trigger: A release above code 13 is published or parallel release writers need coordination
quadrant: prudent-deliberate
category: release
ai_authored: true
created: 2026-10-24
---

ADR 0008 keeps a deterministic local version source and an explicit published floor of 13. Bumps are serialized per worktree, not globally allocated across branches, and the build does not query a publication service. When a newer code is published, advance the floor in the build, helper and regression tests as part of release review; parallel publication still requires serialized version PRs.

## Maintenance — 2026-11-10

The 0.2.22 / code 24 release candidate advances the floor to 24 in the Gradle
validator, local helper and regression fixtures. Both validators reject code 23;
the helper regression supplies matching stale expectations so rejection cannot
be explained by an unrelated expected-value mismatch. Current instructions live
in [versioning.md](../versioning.md); the original ADR context remains historical.
Exact-head CI, trusted daily signing and the actual installed version are checked
separately. Future releases must continue to advance this explicit floor and
serialize version review; no global allocation service is introduced.

---
id: 20261024164732
title: staged-cades-migration
principal: unknown
interest: unknown
hotspot: app/src/main/java/dev/junta/firmamobile/signing/ParameterizedCadesDetachedAdapter.kt
business_capability: local-cades-signing
payoff_trigger: Each separately authorized adapter migration in plan #426, followed by review of wrapper removal after the series
quadrant: prudent-deliberate
category: migration
ai_authored: true
created: 2026-10-24
---

ADR docs/adr/0003-staged-cades-detached-seam.md introduces shared CAdES orchestration with Aragón as its only consumer. Existing wrappers, codecs and the remaining adapters deliberately stay in place to isolate contract, owner and error-order regressions. Extend the contract only when migrating the next separately reviewed adapter; assess wrapper removal after the series, rather than generalizing unobserved portal policies now.

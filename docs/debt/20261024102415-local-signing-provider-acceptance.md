---
id: 20261024102415
title: local-signing-provider-acceptance
principal: unknown
interest: unknown
hotspot: app/src/main/java/dev/junta/firmamobile/ui/LocalFileSigning.kt
business_capability: local-file-signing
payoff_trigger: Authorized physical-device SAF acceptance validates cancellation, local-only filtering and export failures
quadrant: prudent-deliberate
category: testing
ai_authored: true
created: 2026-10-24
---

ADR docs/adr/0004-local-file-signing.md keeps the local signing path separate from portal authorization and stages bytes only in memory. JVM tests cannot establish how physical SAF providers enforce EXTRA_LOCAL_ONLY or handle cancelled/failed writes; the operator prohibits device installation and E2E for this candidate. Validate those provider behaviors on an authorized physical-device acceptance run before claiming device-level acceptance.

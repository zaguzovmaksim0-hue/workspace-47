---
id: 20260821102339
title: reviewed-aoc-origin-exception
principal: unknown
interest: unknown
hotspot: app/src/main/java/dev/junta/firmamobile/profile/SiteProfileCatalogParser.kt
business_capability: client-auth
payoff_trigger: unknown
quadrant: prudent-deliberate
category: code_quality
ai_authored: true
created: 2026-08-21
---

The catalog parser contains a narrowly reviewed exception allowing valid.aoc.cat to be shared as a redirect origin by the Barcelona and Catalunya profiles. It is required by the current integration evidence, but the ownership rule is encoded as a special case rather than a durable, data-driven review record. Revisit when shared-origin provenance can be represented directly and validated from the catalog evidence.

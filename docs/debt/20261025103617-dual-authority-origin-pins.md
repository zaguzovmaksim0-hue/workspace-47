---
id: 20261025103617
title: dual-authority-origin-pins
principal: unknown
interest: unknown
hotspot: app/src/main/java/dev/junta/firmamobile/profile/SiteProfileCatalogParser.kt
business_capability: catalog-origin-policy
payoff_trigger: Owner authorizes gate 4 after independent consumer equivalence coverage
quadrant: prudent-deliberate
category: migration
ai_authored: true
created: 2026-10-25
---

ADR 0010 deliberately retains independent legacy origin pins alongside typed catalog declarations. Both must agree before any catalog is published, so changes require coordinated maintenance. Gates 2–3 and retirement of proven duplication remain separately scoped; revisit only under the gate-4 owner decision and independent behavioral evidence. The historical AOC entry remains rationale for the retained pins.

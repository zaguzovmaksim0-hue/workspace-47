---
id: 20261025105759
title: generated-legal-title-localization
principal: unknown
interest: unknown
hotspot: app/src/main/java/dev/junta/firmamobile/ui/LegalInformationDialog.kt
business_capability: legal-information
payoff_trigger: Localize generated dependency notice titles while preserving coordinates and the generated legal inventory
quadrant: prudent-deliberate
category: code_quality
ai_authored: true
created: 2026-10-25
---

Static legal navigation titles use Android resources, but generated dependency notice titles still come from legal/documents.json in Spanish. They include notice numbers, dependency coordinates and grouped-component counts. This bounded UI change preserves the generated inventory and original notice content; a follow-up should give generated labels locale-aware formatting without translating license texts or dependency identifiers.

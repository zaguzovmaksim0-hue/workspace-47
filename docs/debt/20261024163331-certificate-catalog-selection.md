---
id: 20261024163331
title: certificate-catalog-selection
principal: unknown
interest: unknown
hotspot: app/src/main/java/dev/junta/firmamobile/certificate/CertificateReferenceStore.kt
business_capability: certificate-management
payoff_trigger: Before exposing multiple saved references in runtime UI
quadrant: prudent-deliberate
category: migration
ai_authored: true
created: 2026-10-24
---

ADR docs/adr/0003-certificate-reference-catalog.md introduces storage-only catalogue support while preserving the active-reference facade and the existing #369 cache binding. Catalogue append is deliberately not wired to runtime UI. Before user-visible selection/removal, implement verified cache revocation, entry/revision/exact-certificate binding, stale-operation guards, grant ownership and reconciliation of active-only reminders; do not expose the low-level append operation as a complete selection workflow.

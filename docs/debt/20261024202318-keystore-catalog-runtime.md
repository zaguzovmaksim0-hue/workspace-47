---
id: 20261024202318
title: keystore-catalog-runtime
principal: unknown
interest: unknown
hotspot: app/src/main/java/dev/junta/firmamobile/certificate/KeystoreSigningKeyCatalog.kt
business_capability: certificate-management
payoff_trigger: Before enabling ADR 0005 slice 3
quadrant: prudent-deliberate
category: migration
ai_authored: true
created: 2026-10-24
---

ADR docs/adr/0009-keystore-key-catalog-commit.md implements storage lifecycle with runtime disabled. Before exposing it, wire the five-locale backup warning and explicit acknowledgement, revoke the existing unlock cache/session durably on migration/removal, release only unshared optional source grants, and integrate key-only selection/reminders and TLS. Physical StrongBox/TEE acceptance remains required; the internal authentication callback is not a replacement for the existing authenticated runtime flow.

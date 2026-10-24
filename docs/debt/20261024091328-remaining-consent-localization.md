---
id: 20261024091328
title: remaining-consent-localization
principal: unknown
interest: unknown
hotspot: app/src/main/java/dev/junta/firmamobile/afirma/servlet/NativeMultiPhaseOperation.kt
business_capability: signing-consent
payoff_trigger: A follow-up localizes consent format descriptions and certificate fallback names without changing protocol validation or persisted certificate metadata
quadrant: prudent-deliberate
category: code_quality
ai_authored: true
created: 2026-10-24
---

ADR docs/adr/0002-localized-afirma-result-summaries.md keeps localized summary rendering in Compose and carries typed result facts across the consent boundary. This bounded localization group leaves the existing format descriptions in NativeMultiPhaseOperation and the certificate display-name fallback in CertificateDisplayNamePolicy unchanged. They still contain Spanish presentation text; future localization must preserve protocol constraints and persisted certificate metadata rather than translating security-bound catalog constants.

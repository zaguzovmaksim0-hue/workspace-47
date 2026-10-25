---
id: 20261024183436
title: offline-pdf-profile-budget
principal: unknown
interest: unknown
hotspot: app/src/main/java/dev/junta/firmamobile/verification/OfflinePadesVerifier.kt
business_capability: signature-verification
payoff_trigger: Additional PDF profiles or a hard parser deadline are required
quadrant: prudent-deliberate
category: code_quality
ai_authored: true
created: 2026-10-24
---

ADR 0006 reuses bounded in-process PDFBox history parsing and reports only the existing RSA Adobe/ETSI approval profiles (2 MiB, eight signatures). Other profiles, encrypted/certified/locked PDFs and ambiguous history fail closed; this is an integrity check without trust, validity, revocation or timestamp validation. Cancellation prevents stale publication but cannot preempt synchronous parsing/crypto; reuse the worker gate and move to isolation if a hard deadline is required. Expand profiles only with independent positive/negative fixtures and reviewed history binding.

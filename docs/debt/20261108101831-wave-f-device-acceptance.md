---
id: 20261108101831
title: wave-f-device-acceptance
principal: unknown
interest: unknown
hotspot: app/src/androidTest/java/dev/junta/firmamobile/RealE2eInstrumentedTest.kt
business_capability: certificate-login
payoff_trigger: Authorized protected main-only REAL E2E validates the six Wave F Android certificate callbacks on the integrated candidate
quadrant: prudent-deliberate
category: testing
ai_authored: true
created: 2026-11-08
---

Wave F implements login-only BOE, CMT, Fondos Europeos, IMSERSO, INE and SEPES/CASA 47 recipes from reviewed public DOM and TLS evidence. The operator explicitly excludes REAL E2E from this implementation task, so Android callback ordering, TLS renegotiation and device-specific certificate-chain construction remain unverified. Keep the profiles QA_ONLY and IMPLEMENTED_NOT_E2E until protected main-only acceptance establishes a profile-scoped certificate callback/grant; HTTP status and Cl@ve ServiceProvider navigation are insufficient.

The worker's requested Kotlin compilation additionally remained blocked before compiler startup because the shared Gradle lock was unavailable for over 60 minutes. Only its own pending flock was canceled; targeted Kotlin unit tests and WebView instrumentation were not executed, so candidate compilation and unit acceptance are still required before integration.

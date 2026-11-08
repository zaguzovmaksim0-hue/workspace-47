---
id: 20261108120253
title: dgoj-session-acceptance
principal: unknown
interest: unknown
hotspot: app/src/androidTest/java/dev/junta/firmamobile/RealE2eInstrumentedTest.kt
business_capability: certificate-login
payoff_trigger: Authorized Android fixtures and protected main-only REAL E2E validate the DGOJ form guards, bounded session comparison and certificate callback on the integrated candidate
quadrant: prudent-deliberate
category: testing
ai_authored: true
created: 2026-11-08
---

ADR docs/adr/0012-dgoj-login-session-boundary.md confines raw ;DGOJ_ID comparison to the two reviewed DGOJ login states; generic URL matching, TLS policy and popup isolation remain unchanged. The 1-256 ASCII-unreserved suffix limit is a defensive helper bound, not a measured server session grammar or length: sanitized probe 42471980144 did not retain those facts. The probe also did not establish submit overrides/base target or AFIRMA image/article structure, so runtime guards must fail closed. This local-only task excludes Android compilation, WebView instrumentation and certificate REAL E2E; keep QA_ONLY and E2E_PENDING until separately authorized acceptance measures those boundaries.

---
id: 20261107191843
title: huelva-login-lifecycle
principal: unknown
interest: unknown
hotspot: app/src/androidTest/java/dev/junta/firmamobile/RealE2eInstrumentedTest.kt
business_capability: certificate-login
payoff_trigger: Android REAL E2E establishes the source POST and redirect epoch order for Huelva within the existing grant TTL
quadrant: prudent-deliberate
category: testing
ai_authored: true
created: 2026-11-07
---

Huelva's existing REDIRECT_AFTER_SOURCE model can authorize an immediate redirect from the reviewed authentication page, but login() POSTs to that same source before the GET to authserver. The POST does not rearm this transition: a delay beyond the pinned 15 seconds or a second onPageStarted advancing the epoch clears the grant. The authorizer regression covers both the permitted first-page-start sequence and fail-closed second-page-start sequence; Android callback ordering is unverified, so keep the profile QA_ONLY and do not treat recipe implementation as accepted login.

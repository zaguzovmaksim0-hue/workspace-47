---
id: 20261108091754
title: wave-d-login-acceptance
principal: unknown
interest: unknown
hotspot: docs/adr/0011-reviewed-certificate-logins.md
business_capability: certificate-login
payoff_trigger: Operator-authorized Android REAL E2E verifies the integrated Wave D login boundaries and AEMET local callback acceptance
quadrant: prudent-deliberate
category: testing
ai_authored: true
created: 2026-11-08
---

ADR 0011 deliberately keeps the five implemented Wave D logins QA_ONLY and E2E_PENDING. Public HTML/JS, Chrome probes and synthetic tests establish exact contracts, but do not prove Android client-certificate callback ordering, Soria readiness in the historical failed runs or AEMET server acceptance of the local detached CAdES result. The existing release runtime permits unverified QA profiles, so those labels do not prevent runtime use; validate these boundaries in authorized REAL E2E before promoting compatibility claims. Never add filing or an HTTP broker to obtain acceptance.

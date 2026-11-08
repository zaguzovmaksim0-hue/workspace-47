---
id: 20261107191158
title: pattex-entry-auth-boundary
principal: unknown
interest: unknown
hotspot: config/site_profiles_v1.json
business_capability: certificate-login
payoff_trigger: A separately reviewed PATTEX entry authentication contract covers the observed early CertificateRequest
quadrant: prudent-deliberate
category: planning
ai_authored: true
created: 2026-11-07
---

The PATTEX source entry already requests a client certificate before its HTTP 302. Saved live evidence shows one reused connection and no second CertificateRequest on the currently pinned target, so a no-click recipe cannot reliably reach an authorized target callback. Wave B preserves the existing target-only profile and skips adding a recipe; admitting the entry callback or multiple request paths requires a separately reviewed security contract.

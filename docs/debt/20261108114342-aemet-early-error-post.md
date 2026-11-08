---
id: 20261108114342
title: aemet-early-error-post
principal: unknown
interest: unknown
hotspot: docs/adr/0011-reviewed-certificate-logins.md
business_capability: certificate-login
payoff_trigger: Operator-authorized Android acceptance demonstrates pre-consent failures and the owner selects a reviewed non-deep probe strategy
quadrant: prudent-deliberate
category: testing
ai_authored: true
created: 2026-11-08
---

ADR 0011 retains the original AEMET login callbacks and defers server acceptance to operator-authorized Android REAL E2E. Abandoning the pending reply at AwaitingConfirmation avoids the cancel callback only after that boundary; a pre-consent native error or shim timeout can still deliver the original error callback and submit a negative login POST. Keep production error handling unchanged without evidence of a product bug, characterize this behavior with the real shim and a synthetic submit sink, and do not claim that non-deep probes guarantee zero login requests. This is login-only behavior, not evidence of an administrative filing or accepted authentication.

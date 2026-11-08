---
id: 20261108094154
title: wave-c2-entry-auth-boundary
principal: unknown
interest: unknown
hotspot: config/site_profiles_v1.json
business_capability: certificate-login
payoff_trigger: A separately authorized initial-navigation TLS contract is reviewed for gestiona2 and PATTEX
quadrant: prudent-deliberate
category: planning
ai_authored: true
created: 2026-11-08
---

Wave C2 skips comunidad-madrid-gestiona2 and extremadura-portal-tributario because CertificateRequest occurs on the initial entry before a committed source exists. DIRECT_FROM_SOURCE needs an exact observed source, while REDIRECT_AFTER_SOURCE and IN_PLACE_FROM_SOURCE need a prior navigation; none grants initial entry authentication. The existing gestiona2 navigation profile, Madrid shared-origin declarations and PATTEX target-only policy remain unchanged. The PATTEX early-callback evidence is also recorded in 20261107191158-pattex-entry-auth-boundary.md; supporting these entries requires a separate security-model review, not a synthetic source equal to the target.

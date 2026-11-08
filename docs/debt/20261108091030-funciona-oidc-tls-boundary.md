---
id: 20261108091030
title: funciona-oidc-tls-boundary
principal: unknown
interest: unknown
hotspot: config/site_profiles_v1.json
business_capability: certificate-login
payoff_trigger: A separately reviewed broker-source TLS contract preserves profile authority through Funciona OIDC redirects
quadrant: prudent-deliberate
category: planning
ai_authored: true
created: 2026-11-08
---

Funciona remains navigation-only in Wave D. Its certificate request follows auth-api.redsara.es OIDC redirects to autentica.redsara.es; the existing redirect authorizer requires an initiator source and consumes its grant at the next hop, while the parser does not admit this broker as a redirect-origin source. Promoting the broker to an initiator or extending grant propagation would change the security model forbidden by IMPL_D, so no recipe or new certificate authority is enabled.

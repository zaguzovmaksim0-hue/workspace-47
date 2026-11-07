---
id: 20261107191507
title: shared-signing-tls-filter
principal: unknown
interest: unknown
hotspot: app/src/main/java/dev/junta/firmamobile/profile/SiteProfileCatalogParser.kt
business_capability: certificate-login
payoff_trigger: A reviewed signing certificate lacks digitalSignature or the owner authorizes separate signing and TLS certificate filters
quadrant: prudent-deliberate
category: code_quality
ai_authored: true
created: 2026-11-07
---

Policía, MINECO, Transparencia and Fuerteventura share one profile-level certificate filter between existing signing and the new Cl@ve TLS login. The reviewed login requires digitalSignature key usage, so v2 profiles now require it for both operations; signing-only certificates accepted under the previous filter may be excluded. Wave A deliberately keeps the existing security model rather than introducing separate operation-specific filters. Signing protocol algorithms, documents, callbacks and origin restrictions remain unchanged.

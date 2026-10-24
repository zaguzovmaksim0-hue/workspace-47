---
id: 20261024183211
title: offline-xades-verification
principal: unknown
interest: unknown
hotspot: app/src/main/java/dev/junta/firmamobile/verification
business_capability: signature-verification
payoff_trigger: Implement reviewed incoming XAdES reference and profile validation without new dependencies
quadrant: prudent-deliberate
category: planning
ai_authored: true
created: 2026-10-24
---

Incoming .xsig/.xml verification is deferred. Android lacks javax.xml.crypto; the bundled Santuario canonicalizer and NativeXadesEngine.verify can self-check a narrow generated profile only with caller-supplied original bytes, certificate, algorithm and packaging. They are not an arbitrary incoming XAdES verifier: safely discovering those inputs requires a reviewed reference/transform allowlist, unique-ID and wrapping defenses, certificate-to-SignedProperties binding and detached-content selection, with negative fixtures. Do not infer validity from SignatureValue alone or dereference external URIs; no dependency is added in this bounded PDF change.

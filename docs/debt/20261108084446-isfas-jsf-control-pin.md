---
id: 20261108084446
title: isfas-jsf-control-pin
principal: unknown
interest: unknown
hotspot: app/src/androidTest/java/dev/junta/firmamobile/RealE2eInstrumentedTest.kt
business_capability: certificate-login
payoff_trigger: ISFAS changes its generated JSF control id or a newly reviewed DOM supplies a stable login selector
quadrant: prudent-deliberate
category: code_rot
ai_authored: true
created: 2026-11-08
---

ISFAS login pins the reviewed Cl@ve anchor onclick containing the generated Mojarra id j_idt85. Exact matching avoids selecting Tarjeta PKI or another JSF action, but a server-side view change can cause the recipe to fail closed. Revisit the selector with a fresh public DOM probe when that id changes; do not manufacture a JSF submission.

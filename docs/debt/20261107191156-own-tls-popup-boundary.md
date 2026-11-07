---
id: 20261107191156
title: own-tls-popup-boundary
principal: unknown
interest: unknown
hotspot: app/src/androidTest/java/dev/junta/firmamobile/RealE2eInstrumentedTest.kt
business_capability: certificate-login
payoff_trigger: A separately reviewed popup authentication contract preserves an explicit selected-profile source without inheriting parent authority
quadrant: prudent-deliberate
category: planning
ai_authored: true
created: 2026-11-07
---

CNMC and TEA are skipped in Wave B even though their TLS URL tuples fit the parser: the reviewed entry anchors open target=_blank. BrowserScreen requires a real gesture for a popup and deliberately creates the child with profileId=null and an independent authorizer; a native tap alone cannot supply the required profile/source grant. Do not rewrite target to _self, manufacture navigation, inherit parent authority or auto-confirm the interactive fallback to make the recipe appear to work.

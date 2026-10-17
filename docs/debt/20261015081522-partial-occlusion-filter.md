---
id: 20261015081522
title: partial-occlusion-filter
principal: unknown
interest: unknown
hotspot: app/src/main/java/dev/junta/firmamobile/ui/ObscuredTouchFilter.kt
business_capability: signing-consent
payoff_trigger: A reviewed Android and Compose input-boundary change covers partially obscured events on API 29-30
quadrant: prudent-deliberate
category: code_quality
ai_authored: true
created: 2026-10-15
---

Task B implements the review's requested filterTouchesWhenObscured helper and API 31+ overlay-window hiding with minimal changes. The Android View filter rejects FLAG_WINDOW_IS_OBSCURED but does not reject FLAG_WINDOW_IS_PARTIALLY_OBSCURED, leaving partial occlusion outside this helper's protection on API 29-30, where setHideOverlayWindows is unavailable. Closing that separate input-boundary gap requires a reviewed event interceptor for the real Compose dialog roots, not an unverified touch listener added during this bounded patch. Android guidance documents the distinction at https://developer.android.com/privacy-and-security/risks/tapjacking; do not treat the current JVM test as proof of partial-occlusion protection.

---
id: 20261024092902
title: pdf-preview-native-deadline
principal: unknown
interest: unknown
hotspot: app/src/main/java/dev/junta/firmamobile/ui/PdfPreview.kt
business_capability: signing-consent
payoff_trigger: A hard preview deadline or hostile-PDF isolation becomes required
quadrant: prudent-deliberate
category: code_quality
ai_authored: true
created: 2026-10-24
---

PdfRenderer parsing/rendering is synchronous native code. The preview uses a coroutine timeout and cancellation checks, input/bitmap budgets and a single render permit, but cancellation cannot preempt a native call; cleanup completes when that call returns. ADR 0002 records the bounded in-process choice; move rendering into an isolated service with process termination if a hard wall-clock deadline is required.

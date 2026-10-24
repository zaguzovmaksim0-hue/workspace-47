---
id: 20261024170500
title: external-afirma-admission
principal: unknown
interest: unknown
hotspot: app/src/main/java/dev/junta/firmamobile/ui/BrowserScreen.kt
business_capability: external-afirma
payoff_trigger: A shared admission contract covers portal signing, retrieval, Client TLS and external deliveries without backgrounding or cancelling the current operation
quadrant: prudent-deliberate
category: planning
ai_authored: true
created: 2026-10-24
---

ADR docs/adr/0003-external-afirma-context.md implements the typed parser and consent core while keeping the exported receiver disabled. BrowserScreen owns native, retrieval, popup and Client TLS busy state locally, while MainActivity owns other signing flows and cancels on background. Enabling a separate Activity before sharing admission would let a new delivery interrupt an existing operation. Complete the shared ownership integration and Android delivery tests before adding an intent-filter.

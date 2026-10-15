---
id: 20261015081230
title: emulator-failures-unresolved
principal: unknown
interest: unknown
hotspot: app/src/androidTest/java/dev/junta/firmamobile
business_capability: signing-consent
payoff_trigger: A focused emulator rerun provides semantics and lifecycle evidence for the three failing test methods
quadrant: prudent-inadvertent
category: testing
ai_authored: false
created: 2026-10-15
---

CI run 38581449608 at 69767fd0 could not find the local cancellation receipt in AfirmaServerCancellationInstrumentedTest.everyCancellationReceiptOffersOnlyClose. Run 38575136620 at 99f54e58 did not display native-afirma-unlock in AfirmaRetrievalInstrumentedTest.decryptedDescriptorOpensFreshSigningReviewWithoutUsingACertificateOrSigning and could not find the certificate password field in SigningConfirmationInstrumentedTest.trustedMiniAppletRequestWaitsForNativeConfirmationAndCancelReturnsClosedError. These differ from the confirmed stale app name, hidden certificate menu and collapsed format details corrected in task B; the logs alone do not establish their causes. Keep the assertions intact until focused emulator evidence explains each failure, rather than guessing new text or weakening consent checks.

---
id: 20261108114010
title: tea-navigation-method-proof
principal: unknown
interest: unknown
hotspot: app/src/test/java/dev/junta/firmamobile/browser/ClientAuthNavigationAuthorizerTest.kt
business_capability: certificate-login
payoff_trigger: Synthetic Android WebView acceptance establishes the method boundary for the observed TEA GET navigation and any unexpected POST override callback
quadrant: prudent-deliberate
category: testing
ai_authored: true
created: 2026-11-08
---

The exact TEA profile pin accepts GET only and rejects a configured POST policy; its resource-request observer also refuses POST arming. Those tests do not establish end-to-end POST rejection: the existing navigation observer has no method argument, and JuntaWebViewClient can continue to it after a null resource result if an override callback is delivered. Whether Android delivers that unexpected POST callback was not tested in this local-only addendum. Keep this as a narrowly documented acceptance gap without changing the shared WebView/authorizer model or claiming a proved platform bypass.

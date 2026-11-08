---
id: 20261108111719
title: wave-d-jvm-verification
principal: unknown
interest: unknown
hotspot: app/src/main/java/dev/junta/firmamobile/profile/AemetLoginProfilePins.kt
business_capability: certificate-login
payoff_trigger: Integration owner completes the scoped JVM and QA instrumentation compilation checks on the integrated Wave D candidate
quadrant: prudent-deliberate
category: testing
ai_authored: true
created: 2026-11-08
---

The first local Gradle attempt compiled QA instrumentation but exposed a missing AEMET local-CAdES allowlist entry and outdated shim characterization expectations. The candidate now includes the corresponding parser and test-fixture changes; its full JVM retry remained behind the shared Gradle lock and the owned waiter was stopped before execution, without interrupting foreign workers. Run the profile, catalog, network, browser and signing JVM checks plus QA instrumentation compilation on the integrated candidate before treating native verification as green; Python and Node results do not substitute for that gate.

```sh
flock ~/.cache/w47-gradle.lock env ANDROID_HOME="$HOME/android-sdk" \
  GRADLE_USER_HOME="$HOME/.gradle-workspace47" ./gradlew --continue \
  :app:compileQaAndroidTestKotlin :app:testReleaseUnitTest \
  --tests 'dev.junta.firmamobile.profile.*' --tests 'dev.junta.firmamobile.catalog.*' \
  --tests 'dev.junta.firmamobile.network.*' --tests 'dev.junta.firmamobile.browser.*' \
  --tests 'dev.junta.firmamobile.signing.*' -x verifyReleaseSigning --no-daemon
```

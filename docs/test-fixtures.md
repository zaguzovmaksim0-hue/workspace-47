# Test fixtures and synthetic credential material

This repository contains credential-shaped material used only for automated tests. It must never be reused as production, QA-service, developer, or personal credential material.

## Synthetic PKCS#12 fixture

`app/src/androidTest/assets/synthetic-identity.p12.b64` is an intentionally synthetic PKCS#12 fixture for Android instrumentation tests.

Properties that identify it as test-only:

- the instrumentation test refers to it as `synthetic-identity.p12`;
- the expected certificate holder is `Persona de Prueba`;
- the passphrase is the public test-only string `test-password-123`;
- related unit-test certificates are generated under the test organization name `Junta Firma Mobile Tests`;
- it is used to exercise certificate loading, unlock/lock lifecycle and memory-handling behavior, not to authenticate to a real service.

The private key inside this fixture is intentionally public by virtue of being committed. It has no confidentiality value and MUST NOT be trusted or imported for any purpose outside the test suite.

## Rules for contributors

Never commit:

- a personal or organizational PKCS#12/PFX file;
- an Android/app release keystore;
- a TLS private key;
- a real QA relay credential or credential database;
- real SPKI deployment values that are intended to remain private operational configuration;
- passwords, cookies, bearer tokens, session identifiers or one-time codes;
- HAR/pcap captures or logs from authenticated sessions;
- screenshots or diagnostic artifacts that expose certificate holder identity or other personal data.

If a regression test needs credential-shaped input, generate synthetic material in the test suite where practical. If a binary fixture is necessary, make its synthetic identity and public test passphrase explicit and document it here before committing it.

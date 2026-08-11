# Contributing

Junta Firma Mobile combines Android security boundaries, certificate handling, WebView interoperability and public-service compatibility research. Contributions are welcome only when they preserve fail-closed behavior, privacy and source provenance.

## Before changing code

- Read the relevant design/evidence documents under `docs/`.
- Keep the smallest security boundary necessary for the target portal or protocol.
- Do not widen trusted origins, callbacks, native-network destinations, signing algorithms, or QA capabilities by inference.
- Add focused regression tests before or with behavior changes.
- Separate public factual observations from assumptions; do not promote a compatibility status without evidence.

## Build and test expectations

Use the repository Gradle Wrapper. Common checks include:

```bash
./gradlew testDebugUnitTest
./gradlew lintDebug
./gradlew assembleDebug
```

Changes touching another build variant, signing adapter, portal profile, QA relay, Android instrumentation, catalog generator or Python tooling require the corresponding focused checks as well. Do not report a test/build as passing unless it was actually executed for the candidate commit.

Release builds require explicit private signing configuration. Do not add a fallback to a committed/debug key.

## Secrets and personal data

Never commit real credentials or authenticated-session evidence. In particular, do not add personal/organizational PKCS#12/PFX files, release keystores, TLS private keys, relay credentials, passwords, tokens, cookies, OTPs, HAR/pcap captures, private documents, or screenshots containing personal account/certificate data.

Use synthetic fixtures. The intentionally public synthetic PKCS#12 fixture is described in `docs/test-fixtures.md`.

If a test needs credential-shaped data, prefer generating it in the test. A committed binary fixture requires explicit test-only provenance, non-production identity, public test password where applicable and documentation before review.

## Security research and portal interoperability

Public-service compatibility work is not authorization for security testing against third-party systems.

Allowed contribution evidence should come from:

- offline/local fixtures;
- project-owned infrastructure;
- normal intended public-client behavior and bounded read-only observation;
- third-party testing for which the contributor separately has explicit authorization.

Do not probe arbitrary endpoints, bypass authentication/access controls, fuzz public administration systems, submit administrative actions as a test, or retain authenticated payloads merely to improve compatibility.

Where a real portal flow is necessary for interoperability validation, stop before modification/payment/submission unless that exact action is the user's intended real-world action and is separately authorized. Persist only sanitized metadata needed to establish the protocol contract.

## Source and asset provenance

Every newly copied, generated or adapted source/asset must have a traceable origin.

For third-party material, record at minimum:

- upstream project/source;
- exact file/component;
- license/version;
- required attribution/notice;
- whether the material was copied, modified, generated from, or merely used as a behavioral reference.

Do not paste source from another project into this repository solely because it is publicly visible. Public availability is not a license.

Update `docs/provenance.md` when adding:

- source copied/adapted from another project;
- fonts, icons, images or other binary assets;
- generated datasets/catalogs from new source families;
- vendored libraries or build bootstrap material;
- code whose implementation was materially derived from a third-party source.

## Third-party names and branding

This is an independent, unofficial project. Do not add official seals/logos, wording, screenshots or presentation that implies sponsorship, endorsement, certification or affiliation with Junta de Andalucía, another public administration, university, agency, or third-party service unless there is documented permission.

Names/domains of third-party services may be used descriptively where necessary to explain interoperability.

## Compatibility evidence

Compatibility levels must describe verified facts, not aspirations.

- Public/static discovery is not proof of authentication or signing compatibility.
- A detected AutoFirma/MiniApplet/AutoScript string is not a verified contract.
- A local successful signature is not proof the portal accepted it.
- An authenticated landing page may prove only the exact login flow tested, not document submission or every operation on that service.

Evidence documents must state the exact scope and what remains unproven.

## Pull requests

A useful pull request explains:

1. the security/compatibility problem being solved;
2. the exact trust surface changed;
3. tests/evidence run on the candidate commit;
4. privacy/provenance impact;
5. any unresolved limitation.

A PR that requires real credentials or sensitive evidence to review is not ready for submission.

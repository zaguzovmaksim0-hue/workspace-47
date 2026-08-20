# ES-PUB-0093 — Junta de Andalucía

- Assignment: `20260817-pack09-B-0093`
- Worker: B
- Outcome: `IMPLEMENTED_NOT_E2E`
- Terminal worker status: `PR_READY`
- Branch: `integrate/junta-andalucia-es-pub-0093-20260817`
- Effective pass base: `e5d41938e9a7017a0371521375d0f7a7b7c8a685`
- Implementation head before this final report commit: `0c9983b0adb37b1ff28363b01ad4b20990f4bfff`
- PR: https://github.com/zaguzovmaksim0-hue/workspace-47/pull/109

## Impact

ES-PUB-0093 is promoted from metadata-only browsing to a bounded QA-only navigation profile for the Junta de Andalucía current generic electronic-presentation flow. The implementation enables only the exact public VEA start page and intentionally exposes no authentication, certificate-selection, signing, client-TLS, endpoint, callback, or operation-policy capability.

## What changed

- Added profile `junta-andalucia-vea-peg` with `QA_ONLY` activation and exact start URL:
  `https://veaja.cloud.juntadeandalucia.es/inicio/procedimiento-detalle/PEG_VEA`.
- Bound inventory record `ES-PUB-0093` to that exact profile and marked it `IMPLEMENTED_NOT_E2E / E2E_PENDING`.
- Kept `api-veaja.cloud.juntadeandalucia.es` outside the trusted profile/browser/signing origin set.
- Added fail-closed tests for exact-origin resolution, release exclusion, empty capabilities/endpoints/operation policies, catalog binding, UI classification, and rejection of the auth API as a profile origin.
- Updated the generated public portal catalog and inventory record.
- Added durable deep-public research evidence at `docs/autonomous/2026-08-17-junta-andalucia-vea-peg-deep-research.md`.

## Research trail

Full evidence is recorded in `docs/autonomous/2026-08-17-junta-andalucia-vea-peg-deep-research.md`.

Key findings:

- `deep_public_research=PASS` in `BROWSER_PUBLIC_RUNTIME`.
- The official Sede path for `Presentación electrónica general` converges from Junta legacy entry points to the public VEA procedure `PEG_VEA`.
- An unauthenticated Chromium session reached the exact public VEA page and observed the generic-presentation workflow, certificate/Cl@ve options, and the signing step description without entering credentials or creating private/draft state.
- Public first-party assets show AutoScript/AutoFirma integration and a dynamic signing flow.
- Exact sensitive signing values (`signAlgorithm`, `signFormat`, `hashAlgorithm`, certificate constraints and signable hashes) are supplied only after authentication/draft preparation. They are therefore not guessed or copied from other Junta profiles.
- One public configuration response contained a credential-like field; it was not used and is deliberately absent from durable evidence.

## Boundary / fail-closed decision

The exact public navigation contract is bounded and implementable. The signing/authentication contract is not public at the required precision because its decisive values first appear after the authenticated draft boundary. Therefore this pass implements navigation only:

- no `SIGN`;
- no `SELECT_CERTIFICATE`;
- no `AFIRMA_URI`;
- no `CLIENT_TLS_AUTH`;
- no signing endpoint or operation policy;
- no copied OVORION / Oficina Virtual constants;
- release build does not activate the profile.

This is intentionally `IMPLEMENTED_NOT_E2E`, not `VERIFIED_E2E`.

## Verification

Passed on the target branch:

- `./gradlew --no-daemon test build` — PASS (`BUILD SUCCESSFUL`; prior durable job `job_20260817_221506_8d88e59c`).
- `git diff --check` — PASS.
- `python -m unittest tools.tests.test_generate_public_portal_catalog -v` — PASS, 32/32.
- Git history secret scan on PR #109 — PASS.
- CodeRabbit status on PR #109 — PASS.

GitHub Actions currently also reports repository-wide CI failures unrelated to this target. The same failures reproduce on contemporaneous control PR #110 and occur before or outside ES-PUB-0093 logic:

- Android/OSV setup: runner cannot find `sdkmanager`.
- Python policy suite: missing repository helper `tools/w47-cloud` and missing `PyYAML` in the CI environment.
- Go vulnerability gate: Go 1.26.5 standard-library findings fixed in Go 1.26.6.

No out-of-scope CI/workflow/toolchain repair was added to this portal PR.

## Artifacts

- `config/site_profiles_v1.json`
- `app/src/main/res/raw/public_portal_catalog_v1.json`
- `docs/compatibility/all-spanish-public-portals-inventory.md`
- `docs/autonomous/2026-08-17-junta-andalucia-vea-peg-deep-research.md`
- target Kotlin/Python regression tests in the PR diff
- PR #109

## Limitations

- No authenticated session was entered.
- No certificate was selected or supplied.
- No draft/record was created.
- No signing or submission was attempted.
- Sensitive VEA signing constants remain unknown by design until a separately authorized authenticated pass.
- GitHub repository-wide CI remains red for known non-target infrastructure/toolchain reasons described above; local target/full Gradle gates are green.

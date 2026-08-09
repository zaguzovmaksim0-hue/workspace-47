# Sevilla ATSE certificate-login implementation plan — 2026-08-09

1. Create an isolated worker worktree/branch from the current autonomous main. No shared writable
   worktree and no worker subagents.
2. Matt `codex/tdd`: RED shim tests for exact Sevilla profile/origin, dynamic 40-byte URL-safe
   challenge, `SHA1withRSA`, literal `XAdES`, null properties, and standard callbacks. Wrong variants
   must remain unintercepted; no `authenticate` implementation may appear.
3. Minimum GREEN shim/profile plumbing, strictly profile-scoped.
4. RED native bridge tests for the same decoded payload/algorithm/format/property constraints, then
   minimum GREEN routing. Generic XAdES support must remain unchanged.
5. RED protocol-adapter tests for the official current AutoFirma XAdES Enveloping defaults, including
   SHA1withRSA SignatureMethod, SHA-512 reference digest, embedded Base64-transformed challenge,
   RSA certificate binding, signature validation, ownership/cleanup, and rejection of non-Sevilla
   requests. Implement a dedicated adapter or safely parameterize existing XML helpers.
6. RED profile parser/registry tests, then add exact `VERIFIED_CONTRACT` / `QA_ONLY` profile and local
   registry binding. Release remains disabled.
7. Worker commits/pushes each useful RED/GREEN slice before Gradle. All Gradle runs are submitted by
   the orchestrator to Codex Cloud against the exact pushed SHA; no phone-local Gradle.
8. After focused GREEN, orchestrator updates the shared Sevilla inventory/catalog row only with the
   official Sede→ATSE launch chain and truthfully generated `E2E_PENDING`/
   `IMPLEMENTED_NOT_E2E` state; run local Python generator tests.
9. Matt code review, `git diff --check`, unsafe/sensitive-content scan, then canonical full Android
   Cloud gate on the exact final worker/integration SHA.
10. Integrate sequentially into `agent/workspace-47-autonomous-20260803`, push and verify exact remote
    SHA. Leave certificate-login physical E2E as a manual future gate.

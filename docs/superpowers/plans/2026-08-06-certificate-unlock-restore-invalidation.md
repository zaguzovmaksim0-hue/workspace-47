# Certificate unlock restore-invalidation implementation plan

1. Add a deterministic blocking-read test storage and a focused cache test that snapshots a
   valid encrypted record, calls `clear()` while restore is blocked, then releases restore.
2. Run the focused Debug/QA cache test before production mutation and capture the expected
   RED result: the stale restore returns non-null even though clear completed.
3. Capture `invalidationGeneration` before entering restore IO and pass it into the restore
   implementation.
4. After password decoding and before constructing `CachedCertificateUnlock`, compare the
   current generation with the captured generation. On mismatch zero the password and
   return null. Preserve all existing cleanup/fail-closed paths.
5. Run focused Debug/QA GREEN, adjacent CertificateViewModel/session/cache regression, then
   full Android JVM/lint/build, Python/Go, artifact and release-fail-closed gates.
6. Inspect the complete diff; run `git diff --check`; verify workflow/dependency/profile/
   threat-model invariants; scan for sensitive material and security weakening; remove the
   generated relay binary and require zero release APKs.
7. Update evidence documents, create one atomic commit, push the autonomous branch, and
   verify exact remote SHA plus 0/0 divergence.

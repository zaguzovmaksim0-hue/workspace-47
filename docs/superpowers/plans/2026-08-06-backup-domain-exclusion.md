# Backup-domain exclusion hardening implementation plan

1. Add a parser-based `CiPolicyTest` that requires the complete supported backup-domain
   exclusion set in legacy `backup_rules.xml` and independently in Android 12+
   `cloud-backup` and `device-transfer`, with no include rules.
2. Run only that policy test and observe RED against the current root-only resources before
   changing either XML file.
3. Expand both backup resources to exclude `.` for root/file/database/sharedpref/external
   and all four device-protected variants. Do not alter runtime storage code.
4. Run focused policy GREEN, Android resource/lint/build gates, full Android JVM suites,
   Python/Go, artifact and release-signing fail-closed verification.
5. Inspect the complete diff, run `git diff --check`, verify workflow/dependency/profile/
   network/TLS/runtime invariants, scan additions for sensitive material, and require the
   generated relay binary absent plus zero release APKs.
6. Update authoritative evidence documents, create one atomic commit, push the autonomous
   branch, and verify exact remote SHA with 0/0 divergence.

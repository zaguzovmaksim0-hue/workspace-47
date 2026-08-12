# Certificate unlock threat-model reconciliation design

## Finding

The current runtime intentionally supports automatic certificate re-unlock for at
most 24 hours after one successful manual PKCS#12 password entry. The password is
persisted only as AES-GCM ciphertext in `noBackupFilesDir`; the AES key is held in
Android Keystore. The private key and PKCS#12 bytes remain non-persistent.

The current `docs/threat-model.md` predates that feature and still describes T5 as
if lifecycle/process death necessarily locks the identity without a persistent
unlock secret. That is materially stale: a valid cache is deliberately retained
across backgrounding, app dismissal, process death, force-stop, device restart and
ordinary app update, and it can reconstruct the in-memory identity without another
password prompt before the original expiry. `onMemoryPressure()` drops the current
identity but intentionally starts cache restoration.

This mismatch makes an authoritative security boundary document understate the
persistence/availability surface and can lead future hardening work to change an
intentional recovery path or to reason about the wrong attacker capability.

## Evidence

- Commit `32b27caf95c039020dd8512018c0875ed483c291` introduced the 24-hour encrypted
  unlock cache and automatic process/memory-pressure recovery.
- `docs/superpowers/plans/2026-07-29-persisted-certificate-unlock.md` defines exact
  retention, clearing and recovery rules.
- `CertificateViewModelTest` explicitly verifies process recreation and memory-
  pressure recovery from a valid cache; `CertificateSessionTest` separately verifies
  that the in-memory session itself locks on memory pressure.
- `docs/test-report.md` milestone P07C records a physical cold-launch restoration
  after process termination without another password prompt.
- Current UI copy promises up to 24 hours unless the user locks, changes or removes
  the certificate.

## Scope

- Modify: `docs/threat-model.md`
- Modify: `tools/tests/test_ci_policy.py`
- Update evidence after verification:
  - `docs/autonomous/2026-08-04-audit-ledger.md`
  - `docs/security-roadmap.md`
  - `docs/test-report.md`
  - `docs/handoffs/NEXT_CHAT_HANDOFF.md`

No runtime, resource, profile, network, signing, certificate-cache implementation,
build configuration or dependency file changes.

## Reconciled boundary

T5 must state all of the following without overstating guarantees:

1. Private-key objects and PKCS#12 bytes are not persisted by this recovery feature.
2. After successful manual unlock, the password can persist only as authenticated
   AES-256-GCM ciphertext under `noBackupFilesDir`, bound to the certificate
   reference and original issue/expiry timestamps.
3. The AES key is non-exportable Android Keystore material; on API 28+ the current
   provider also requires the device to be unlocked before use.
4. Automatic restoration never extends the original 24-hour expiry.
5. Manual lock/session clear, certificate replacement/forget, expiry, reference
   mismatch, malformed/tampered ciphertext and failed cached unlock clear the record.
6. Backgrounding, process death, force-stop, device restart, ordinary update and
   memory pressure do not by themselves promise a persistent locked state while a
   valid record remains; memory pressure drops the current identity before recovery.
7. Temporary plaintext password buffers are zeroed best-effort, while the ciphertext
   intentionally persists for the bounded recovery window.
8. Residual risk is explicit: before expiry, code running with the app's privileges
   on an eligible unlocked device may be able to trigger local re-unlock; every
   signing request still requires the separate signing confirmation boundary.

The trust-boundary diagram and asset list should also show the encrypted unlock
record and Android Keystore key rather than presenting identity memory as the only
certificate-unlock state.

## Regression policy

Add a narrow CI documentation-policy test that requires the threat model to contain
stable technical markers for the implemented boundary (`AES-256-GCM`,
`noBackupFilesDir`, `Android Keystore`, `24 horas`, memory-pressure/process-death
recovery semantics) and rejects the exact obsolete lifecycle/process-death lock
sentence. This is a documentation consistency gate, not a runtime security test.

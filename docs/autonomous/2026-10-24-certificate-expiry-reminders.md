# Local certificate-expiry reminders

Base: `ae22e7aa15c6d09644287063db3d25a46d05f4a7` (`origin/main`). Task 4 only.
The implementation changes 15 production files, including manifest, icon and five
locale resources. No portal protocol/adapter, dependency, lock/verification metadata,
baseline, suppression, signing or security-policy change is needed.

## Platform and behavior

No `androidx.work` dependency exists in `app/build.gradle.kts` or the version catalog.
One explicit, immutable, replaceable pending intent uses
`AlarmManager.setWindow(AlarmManager.RTC, ...)`: inexact, non-wakeup, six-hour batching
window. No exact-alarm permission, wake lock, foreground service, polling, network
work or idle/battery-optimization exemption is requested.

The certificate screen has a reminder-settings dialog. Opt-in defaults to off.
Only the user's switch-on action may request `POST_NOTIFICATIONS` on API 33+.
Denial leaves opt-in off. A separate action opens Android notification settings;
blocked notifications/channels are never bypassed.

The policy reuses `CertificateExpirationPolicy`'s local-calendar-day windows:
start of the local date 30 days before expiry, then seven days before. Date/zone
changes recalculate with the offset at the reminder date, including DST. At day 0,
exact `notAfter`, or after expiry, issue no reminder. Late opt-in/delivery sends
only the current window once with the actual remaining day count, never replaying
the older 30-day reminder inside the seven-day window.

The only notification text is a localized generic expiry sentence, e.g.
“Su certificado caduca en 7 días”. Neither notification nor intents include owner,
issuer, URI, serial number, document, password or key. The quiet low-importance
channel has no sound/vibration/badge. Notifications are local-only/private, expire
after one day, and open the ordinary app entry, not signing or authentication.
Private reminder preferences hold only opt-in, a SHA-256-derived reference/expiry
token and consumed threshold numbers. The token is not an authorization check.

A local store decorator at `CertificateModule` serializes reference write/delete
with delivery using a shared mutex. A changed URI or expiry resets consumed windows
and clears the previous notification/alarm. Selecting a file without saved summary
cancels until successful unlock stores metadata. Forgetting clears work without
silently changing opt-in. Reminder I/O failure cannot turn successful certificate
persistence into a failed operation. No background PKCS#12 access/unlock occurs.

The non-exported receiver accepts only the explicit reminder action and protected
boot/package-replaced/time/timezone events. It rereads current local state instead
of trusting alarm extras. Device restart/update and foreground resume restore the
single next alarm without forgetting consumed windows.

## Verification

- Direct phone Kotlin/JUnit execution through Chipupa: the new reminder policy and
  existing expiration policy. The initial 30-day boundary was observed RED against
  a null implementation, then GREEN. Boundary tests cover 30/7/0/exact/expired,
  missed/consumed windows, zone, DST and clock rollback.
- New Robolectric tests cover inexact non-wakeup identity, replacement, at-most-once
  delivery after dismissal/recreation, early/late delivery, opt-out, quiet/blocked
  channels, API 33 permission, alarm restoration, live zone changes, reference and
  expiry replacement, deletion, unknown summary and metadata I/O failure.
- New Compose tests cover default-off/explicit action, busy state, system settings,
  and an ActivityResultRegistry proving no API 33 permission request on composition
  or opening settings; a denied explicit request leaves reminders off.
- XML/placeholder parity in all five locales and `git diff --check` are checked.
  Changed Kotlin files use checksum-pinned standalone detekt without baseline edits.
- Broad compilation, Android lint and the JVM/Robolectric/security matrix run only
  through automatic GitHub Actions on the exact pushed PR head SHA. No full phone
  Gradle gate, instrumentation/E2E/emulator/APK install or manual dispatch is run.
  Exact automated outcomes are recorded in the PR rather than claimed here.

## Residual limits

Doze, OEM battery policy, an Android stopped-app state, a powered-off device or
blocked permission/channel can defer or prevent delivery. A stopped app needs a
later launch. There is no exact-time delivery guarantee. Metadata must first be
saved by an ordinary successful certificate unlock.

Consuming a window before posting favors at-most-once delivery: process loss or
permission revocation between persistence and `notify` can lose that window rather
than duplicate it. Storage failure cancels work. The bounded receiver can time out
under resource pressure; later foreground/reference reconciliation can restore work.
Physical OEM battery/notification behavior and real certificate flows are not
validated by JVM tests. Other certificate-area PRs may need normal composition
conflict resolution; their worktrees are untouched.

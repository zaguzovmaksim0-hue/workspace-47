# WS024 QA relay: generic Linux VPS package

**Status: QA_ONLY / E2E_PENDING.** This package does not deploy anything and
does not make the Android release route through the relay. Release remains
direct-only. The only supported signing endpoint remains exactly
`https://ws024.juntadeandalucia.es/afirma-validator-miniapplet-1_5/sign/TriPhaseSignatureService`.
Manual end-to-end validation is a later, human-approved activity; do not mark
it `VERIFIED_E2E` from these package checks.

## Hard preconditions

Use a project-controlled Linux VPS with a dedicated public IP and a controlled
FQDN. Raw TCP/443 must reach the Go relay directly. The relay itself owns and
terminates public TLS with ALPN `http/1.1`; do not put an HTTP reverse proxy,
CDN, provider TLS terminator, generic upstream, public HTTP health endpoint,
or public admin endpoint in front of it. Packet capture, TLS key logging, raw
request logging, and diagnostic TLS dumps are out of scope and prohibited.

Before installation, have two distinct project-controlled SPKI public keys:
the current key and the next rotation key. Keep their private material outside
this repository and outside shell history. The relay accepts only its compiled
fixed upstream authority; firewall egress does not substitute for that code
restriction.

## Build and install

On a controlled build host, build the unchanged Go relay and install only the
resulting executable:

```sh
cd ws024-relay
go build -o ws024-relay ./cmd/ws024-relay
install -d -o root -g root -m 0755 /opt/ws024-relay /etc/ws024-relay
install -o root -g root -m 0755 ws024-relay /opt/ws024-relay/ws024-relay
install -o root -g root -m 0644 deploy/ws024-relay.service /etc/systemd/system/ws024-relay.service
```

Create the static service account and group through the VPS's approved account
management process before enabling the service. Install the leaf plus
intermediate certificate chain at `/etc/ws024-relay/tls-cert.pem`, mode `0644`
or stricter. Install `/etc/ws024-relay/tls-key.pem` and
`/etc/ws024-relay/qa-credentials.json` as regular, non-symlink files, owned by
the service account, each mode `0600`. Verify file type and mode with `stat`
before service start; do not use a symlink for any of these three paths.

The credentials file contains only SHA-256 digests, never a raw credential.
Its exact schema is:

```json
{"version":1,"credentials":[{"id":"project-managed-id","sha256":"0000000000000000000000000000000000000000000000000000000000000000","expires_at":"2030-01-01T00:00:00Z","revoked":false}]}
```

The all-zero digest above is a clearly synthetic placeholder, not a usable
credential. Replace the complete file using the project secret-management
procedure, never by placing a credential in a command, example, log, or
repository. Credentials load only at startup: perform atomic replacement of the
regular file and then restart the relay to apply revocation or expiry changes.

## Network and service operation

Allow only inbound TCP/443 to the dedicated IP. Permit outbound DNS resolution
and outbound TCP/443; the relay code enforces the fixed WS024 upstream itself.
No inbound HTTP health check is provided. After an approved configuration audit:

```sh
systemctl daemon-reload
systemctl enable --now ws024-relay.service
systemctl status ws024-relay.service
journalctl -u ws024-relay.service --since '10 minutes ago'
```

Treat the journal as metadata only: retain it according to the project privacy
policy, restrict access, and never add credential, raw request, payload,
certificate-private-material, key-log, or packet-capture output. A safe public
preflight is TCP/TLS only and sends no CONNECT request:

```sh
ws024-relay/deploy/verify-outer-tls.sh --host relay.example.invalid --port 443 \
  --pin sha256/PROJECT_CONTROLLED_CURRENT_SPKI_BASE64 \
  --pin sha256/PROJECT_CONTROLLED_NEXT_SPKI_BASE64
```

The verifier uses SNI, ordinary chain and hostname verification, ALPN
`http/1.1`, and presented-chain SPKI matching. It emits one sanitized JSON line
on success. Stable failure classes are exit `64` invalid input, `69` missing
dependency, `70` TLS/ALPN/chain/hostname verification failure, and `71` pin
mismatch. It accepts only a canonical lowercase DNS host, canonical port, and
at least two distinct canonical `sha256/<base64>` SPKI pins.

For shutdown or rollback, stop and disable the unit, then remove the public
listener firewall rule only through approved change control. Preserve audit
metadata only under the stated retention policy. To rotate keys, deploy a
current/next pair `{A,B}`, configure QA clients with both public SPKI pins,
then rotate the service to `{B,C}` and update the QA clients to `{B,C}` before
retiring A. Validate each transition with the TLS-only preflight; never expose
or copy a private key.

## QA APK boundary

The QA build inputs are named `JFM_WS024_QA_RELAY_HOST`,
`JFM_WS024_QA_RELAY_PORT`, and `JFM_WS024_QA_RELAY_SPKI_PINS`. This document
intentionally supplies neither values nor any credential variable. A build
without the complete approved QA tuple remains direct-only. Do not change
Android routing, promote a release build, or run manual E2E without explicit
human approval.

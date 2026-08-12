# Security policy

Junta Firma Mobile handles certificate-backed authentication and electronic-signature workflows, so security reports must be treated as sensitive even when a bug appears to affect only a development or QA path.

## Supported code

Security review currently covers the active project source in this repository, including:

- Android certificate handling and in-memory signing state;
- WebView navigation/origin policy and JavaScript bridge;
- signing request validation and local cryptographic operations;
- project-controlled QA relay code under `ws024-relay/`;
- build/release configuration that separates QA capabilities from release behavior.

The repository is in pre-publication development. A stable public release support matrix has not yet been declared.

## Reporting a vulnerability

Do not include secrets, private keys, personal certificates, passwords, session cookies, authentication tokens, one-time codes, personal identifiers, private documents, or raw authenticated captures in a public issue.

Use a private GitHub security-reporting channel when one is available for the repository. If no private GitHub reporting channel is presented, contact the maintainer through GitHub first with a minimal non-sensitive description so a private exchange can be arranged before sending exploit details or sensitive evidence.

A useful report contains:

- affected commit/version and build variant;
- affected component;
- minimal reproduction using synthetic/test data;
- expected versus actual security boundary;
- impact assessment;
- whether the issue is reachable in `release`, `qa`, `debug`, or test-only code;
- remediation ideas, if known.

## Third-party public services are out of scope for authorization

This project interoperates with websites and services operated by Spanish public administrations and other third parties. Their presence in source code, compatibility catalogs, documentation, tests, or profiles **does not grant authorization to scan, fuzz, exploit, bypass access controls, perform destructive tests, or conduct authenticated security testing against those systems**.

Research for this project must remain within one of these boundaries:

1. project-owned code and infrastructure;
2. local/offline fixtures and synthetic data;
3. normal public-client interoperability observation that does not exceed ordinary intended service use;
4. a third-party system for which the researcher separately has explicit permission covering the proposed test.

Potential vulnerabilities in a third-party public service should be reported through that service/operator's own vulnerability or incident channel, not tested further through this project without authorization.

## Credential and evidence rules

Never commit or attach:

- real `.p12`, `.pfx`, `.jks`, `.keystore`, PEM private keys, or release signing material;
- real relay credentials or credential databases;
- passwords, cookies, bearer tokens, session IDs, challenges, OTP/SMS codes, or private API keys;
- HAR/pcap captures from authenticated sessions;
- screenshots containing certificate identity, account data, expediente/document content, or other personal information;
- TLS key logs or decrypted traffic captures.

The committed PKCS#12-shaped fixture under Android instrumentation assets is intentionally synthetic and documented in `docs/test-fixtures.md`; its private key is public test material and must never be trusted operationally.

## QA relay and release boundary

QA transport is an opt-in research/compatibility facility. It is not a general-purpose proxy and must not become a release dependency by configuration accident.

Required invariants:

- release builds remain direct-only;
- release tunnel policy remains empty;
- QA tunnel capability remains build-variant gated and explicit;
- relay upstream destinations remain fixed/reviewed rather than arbitrary;
- operational relay hosts, credentials, TLS private keys and private deployment material stay outside Git;
- logs retain only sanitized metadata.

A change that weakens these invariants requires focused security review before merge.

## Coordinated disclosure

Please allow reasonable time to reproduce and remediate a valid project vulnerability before publishing exploit details. This request does not restrict independent research on systems you own or are authorized to test, and it does not create authorization to test third-party public services.

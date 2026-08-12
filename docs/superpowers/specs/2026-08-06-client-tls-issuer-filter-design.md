# Client TLS issuer-filter hardening design

## Finding

`ClientAuthRequestHandler.isValidFor()` currently treats both each certificate's
`subjectX500Principal` and `issuerX500Principal` as candidates for the
`ClientCertRequest.principals` allowlist. Android documents those principals as
acceptable certificate **issuers**. Android Conscrypt's `KeyManagerImpl` likewise
matches a requested principal only against `getIssuerX500Principal()` for
certificates in the candidate chain.

The extra subject match broadens the platform CA-filter contract: a server can
name the leaf subject (or another chain subject that is not an issuer for a
certificate in that chain) and the app may still present the private-key-backed
identity. Host, port, navigation epoch, grant TTL, key type, validity, key usage,
EKU and exact Client TLS transition checks remain independent protections, but
the issuer constraint itself should fail closed.

Public evidence checked 2026-08-06:

- Android `ClientCertRequest.getPrincipals()` API reference: principals are the
  acceptable certificate issuers for the certificate matching the private key.
- Android Conscrypt `KeyManagerImpl.chooseAlias()`: when an issuer list is
  supplied, it compares each chain certificate's `getIssuerX500Principal()` to
  that list.

## Scope

Change only the issuer-selection predicate for a non-empty issuer list. Preserve:

- the existing `allowEmptyIssuerList` profile policy;
- exact host/443, epoch and expiry checks;
- key-algorithm, certificate-validity, digital-signature key-usage and EKU gates;
- constant-time DER comparison via `MessageDigest.isEqual`;
- one-shot terminal request handling and ClientCert preference cleanup behavior;
- profile/catalog/release activation state.

## TDD contract

Add a two-certificate synthetic RSA identity whose leaf subject differs from its
issuer. A focused test must first prove RED: an AEAT-style non-empty issuer list
containing only the leaf subject is incorrectly accepted by current production
code. The expected contract is `proceeds == 0`, `ignores == 1`, preferences clear
exactly once.

Keep the existing positive test for a matching real issuer. After RED, production
code must compare requested DER principals only with `issuerX500Principal.encoded`
for certificates in the identity chain.

## Exact files

Production:

- `app/src/main/java/dev/junta/firmamobile/browser/ClientAuthWebViewClient.kt`

Tests/fixtures:

- `app/src/test/java/dev/junta/firmamobile/browser/ClientAuthRequestHandlerTest.kt`
- `app/src/test/java/dev/junta/firmamobile/certificate/TestCertificateFactory.kt`
- `app/src/test/java/dev/junta/firmamobile/signing/SigningTestIdentity.kt`

Evidence after GREEN:

- `docs/autonomous/2026-08-04-audit-ledger.md`
- `docs/security-roadmap.md`
- `docs/test-report.md`
- `docs/handoffs/NEXT_CHAT_HANDOFF.md`

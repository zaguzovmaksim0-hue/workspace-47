# AEAT Client TLS F-03 Design

## Goal

Add a second, narrowly scoped Client TLS authentication profile for the Agencia
Tributaria (AEAT) without broadening certificate access beyond one observed,
read-only login path. The first supported candidate is **Mi área personal → Mis
datos censales**. No tax return, census modification, document signing, payment,
or administrative submission is in scope.

## Evidence boundary

Public portal inspection on 2026-07-31 established the exact user-visible chain:

- source page: `https://sede.agenciatributaria.gob.es/Sede/mi-area-personal.html`;
- link label: `Mis datos censales`;
- TLS target: `https://www1.agenciatributaria.gob.es/wlpl/BUGC-JDIT/MdcAcceso`.

A certificate-free TLS 1.2 probe to `www1.agenciatributaria.gob.es:443` observed a
server `CertificateRequest`. Without a client certificate, the server returned a
302 redirect to the AEAT 403 error page. The handshake advertised RSA, DSA and
ECDSA signing certificate types and a non-empty acceptable issuer list.

This evidence proves an exact TLS client-certificate request at the target. It
does **not** by itself prove Android WebView callback behavior, certificate
acceptance, user authentication, or portal E2E success. Those remain physical
QA gates.

## Product constraints

- The feature is authentication-only and initially `QA_ONLY`.
- Release must not expose AEAT Client TLS until physical E2E is successful.
- The app may present its existing native certificate confirmation UI only after
  the exact top-level transition is observed.
- The dedicated Client TLS WebView remains bridge-free, non-restored and
  one-shot.
- No certificate, private key, password, cookie, personal account data, full URL
  with sensitive parameters, screenshot, or response body may be persisted.
- A successful login may only be used to confirm access to the read-only landing
  page. The test stops before any modification or submission action.

## Approaches considered

### A. Trust the whole `www1.agenciatributaria.gob.es` origin

This would require the least code but would grant the certificate to every path
on a large shared AEAT application host. It is rejected because origin-level
similarity is not an adequate runtime contract.

### B. Reuse the Carné Joven two-step redirect model unchanged

The current model first arms on an intermediate source URL and then accepts a
second navigation to a parameterized TLS facade. AEAT links directly from the
source page to the TLS endpoint, so forcing it into this model would invent an
intermediate transition that does not exist. It is rejected.

### C. Add an explicit direct-transition Client TLS mode

This is selected. A new policy field distinguishes:

- `REDIRECT_AFTER_SOURCE`: the existing Carné Joven behavior;
- `DIRECT_FROM_SOURCE`: a single modern top-level transition from one exact
  source URL to one exact request URL.

The distinction keeps both contracts explicit and prevents heuristic inference.

## Data model

Add `ClientAuthTransitionMode` to `ProfileModels.kt` and a required
`transitionMode` field to every non-null `clientAuthPolicy`.

For `DIRECT_FROM_SOURCE`:

- `sourceUrls` remains non-empty and exact;
- `fixedQueryParameters` and `requiredEphemeralQueryParameters` may both be
  empty;
- the target must have no query component at all, including an empty `?`;
- the target path must equal `requestPath` byte-for-byte;
- only a modern main-frame request is eligible;
- current URL must equal one of `sourceUrls` exactly;
- authorization is produced immediately and consumed once.

For `REDIRECT_AFTER_SOURCE`:

- preserve the existing pending-source, epoch and TTL behavior;
- at least one expected query parameter remains mandatory;
- all current Carné Joven hostile tests remain valid.

## AEAT profile

Initial profile:

- `profileId`: `aeat-mis-datos-censales`;
- `profileVersion`: `1`;
- `compatibilityStatus`: `VERIFIED_CONTRACT`;
- `activation`: `QA_ONLY`;
- `startUrl`: `https://sede.agenciatributaria.gob.es/Sede/mi-area-personal.html`;
- initiator origin: `https://sede.agenciatributaria.gob.es`;
- request origin: `https://www1.agenciatributaria.gob.es`;
- request path: `/wlpl/BUGC-JDIT/MdcAcceso`;
- transition mode: `DIRECT_FROM_SOURCE`;
- no fixed or ephemeral query parameters;
- acceptable issuer list must be non-empty (`allowEmptyIssuerList=false`);
- grant TTL: 15 seconds;
- allowed key algorithms: RSA and EC, because the observed server advertises
  both RSA and ECDSA signing certificates;
- digital-signature key usage remains required.

The public catalog entry `aeat-sede` binds to this profile only while clearly
stating `VERIFIED_CONTRACT`, `QA_ONLY`, and no E2E claim.

## Runtime flow

1. Normal profile WebView loads the exact AEAT personal-area source URL.
2. A modern main-frame navigation targets the exact `MdcAcceso` URL.
3. `ClientAuthNavigationAuthorizer` validates profile, exact current URL, exact
   target host/port/path, absence of query/fragment, and TTL.
4. The existing native confirmation dialog is shown.
5. On confirmation, the process-scoped client-certificate preference barrier
   must complete successfully.
6. A new dedicated WebView loads only the exact target and handles one
   `ClientCertRequest`.
7. `ClientAuthRequestHandler` revalidates navigation epoch, host/port, key type,
   certificate validity, key usage, EKU and issuer constraints before `proceed`.
8. Any mismatch calls `ignore`, clears preferences and abandons the grant.
9. Only source/request origins are allowed during the dedicated flow; all other
   top-level navigation is blocked.

## Physical QA gate

Before release promotion, the exact QA APK must prove:

1. Android WebView emits `onReceivedClientCertRequest` for
   `www1.agenciatributaria.gob.es:443` during the exact source-to-target flow.
2. The request offers an algorithm compatible with the imported certificate.
3. The acceptable issuer list matches the certificate chain under the existing
   fail-closed matcher.
4. After native confirmation, AEAT accepts the certificate and opens the
   authenticated read-only `Mis datos censales` area.
5. Leaving, backgrounding, locking, reloading, changing profile, renderer death
   or an unexpected URL clears/abandons the grant.

Only after all five pass may the profile become `VERIFIED_E2E / ENABLED`. Until
then it remains `VERIFIED_CONTRACT / QA_ONLY`.

## Test strategy

- Parser/model tests for mandatory transition mode and mode-specific query
  invariants.
- Direct-transition authorizer tests for exact success and one-shot consumption.
- Hostile tests for legacy callbacks, subframes, wrong current URL, wrong
  profile, query or empty-query marker, fragments, wrong ports, paths and hosts.
- Regression tests proving Carné Joven still uses `REDIRECT_AFTER_SOURCE`.
- Registry tests proving AEAT exists only in QA before E2E promotion.
- Request-handler tests for observed RSA/EC offers and mandatory non-empty issuer
  matching.
- Full Debug/QA unit, lint, build and artifact gates.
- Physical QA WebView callback and authentication check with sanitized evidence
  only.

## Non-goals

- Generic AEAT authentication across all services.
- Trusting arbitrary `www1` paths or links from arbitrary AEAT pages.
- Tax filing, Modelo 036 modification, signing, payment, notification handling,
  or document submission.
- Automatic promotion to release based on TLS or unit-test evidence alone.

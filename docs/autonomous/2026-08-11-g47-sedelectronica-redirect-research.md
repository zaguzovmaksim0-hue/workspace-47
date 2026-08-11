# sedelectronica.es no-cookie public access boundary — generation 47 — 2026-08-11

## Scope and safety boundary

This slice used only bounded unauthenticated HTTPS GET requests without a cookie jar against three
official Diputación electronic-sede entries. The responses attempted to issue a session cookie; that
cookie was not stored or replayed. No authentication, form submission, procedure launch, certificate
selection, signing, upload, payment, APK launch, ADB, or device-control action occurred.

## Diputación de Guadalajara — ES-PUB-0156

The official inventory entry is `https://dguadalajara.sedelectronica.es/`. A direct no-cookie GET
returned HTTP 302 to `https://dguadalajara.sedelectronica.es/info`; the latter redirects to
`/info.0`, and `/info.0` redirects to itself when the issued session cookie is not replayed. A bounded
follow attempt therefore terminates at the redirect limit rather than reaching stable public HTML.

## Diputación de Teruel — ES-PUB-0173

A direct no-cookie GET to the official entry `https://dpteruel.sedelectronica.es/info.0` returned HTTP
302 with `Location` pointing to that exact same URL. The response attempted to set a session cookie,
but it was neither stored nor replayed. No stable public runtime HTML or signer asset was therefore
available within the current safety boundary.

## Diputación de Zamora — ES-PUB-0177

A direct no-cookie GET to the official entry
`https://diputaciondezamora.sedelectronica.es/info.0` behaved the same way: HTTP 302 to the exact same
URL plus an unconsumed session-cookie response. No signer contract was exposed before that boundary.

## Contract conclusion and queue impact

All three entries remain `BROWSE_ONLY`. This checkpoint does not infer platform internals from search
indexing or from other `sedelectronica.es` tenants, and it does not promote any profile, origin,
adapter, catalog binding, or release state. The next autonomous gate for these surfaces is a stable
official public no-cookie page or independently published first-party technical contract that exposes
the signing mechanism without requiring session-cookie replay or authentication.

These three surfaces remain classified research leads only; implementation priority is unchanged.

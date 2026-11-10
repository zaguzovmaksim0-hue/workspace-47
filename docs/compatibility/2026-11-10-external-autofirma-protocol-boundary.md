# External AutoFirma protocol boundary — 2026-11-10

## Status and scope

This review covers Android external `afirma:` delivery, including links opened
from Chrome. It does not establish interoperability with a live portal. No real
E2E, emulator run, certificate use, loopback listener or external retrieval is
introduced by this change.

The existing authorization decision is [ADR 0006](../adr/0006-external-afirma-context.md):
an unknown external caller can request an explicitly confirmed native operation.
An Android VIEW URI does not authenticate a website. The exported entry reads
only the action and bounded data URI, uses a short-lived in-process inbox, and
passes through the foreground and shared signing-admission checks. Caller extras,
referrers and an asserted retrieval host must not become a browser `sourceOrigin`
or a trusted portal profile.

## Changes in this slice

`ExternalAfirmaController.receiveResult` returns a bounded enum. MainActivity
releases admission for every refused delivery and maps the enum to fixed local
resources in Spanish, English, Catalan, Galician and Basque. Raw URI values,
certificate data, parser exception messages and recipient tokens never become
error text.

The result distinguishes invalid syntax, external indirect retrieval, the desktop
WebSocket protocol, unsupported certificate filters, unsupported version/key
constraints, attempts to suppress confirmation, busy/unavailable state, and a
failed review. Unknown unsupported variants keep the existing generic refusal.
A refused syntax/feature request reaches neither the identity provider nor
operation preparation; it performs no signing, upload or automatic server CANCEL.
The existing Boolean `receive` wrapper remains for callers that only need to know
whether consent was offered.

## Indirect `fileid` retrieval

`AfirmaServletInvocationParser.parseExternal` deliberately disables indirect
retrieval. `AfirmaDeferredInvocation` currently requires a browser source origin;
reusing it with the retrieval endpoint or a guessed Chrome origin would assign
trust the Android intent cannot prove.

A future implementation needs a separate external deferred-request context. Before
any network request, a native review must identify the unknown caller and exact
public HTTPS retrieval endpoint, bind the operation, session, endpoint, expected
result destination and cipher parameters, and authorize that one bounded fetch.
Normal server validation, public-address checks, response limits, cancellation and
short expiration apply. Profile cookies, client TLS credentials, background
retries and automatic redirects must not be inherited.

The downloaded configuration must be parsed and decrypted once, reject recursive
`fileid` requests, and preserve the envelope bindings. A second review must show
the actual document digest and size, recipients and selected certificate before
signing. Fetch approval is not signing approval. If an authenticated website
origin is needed, an independently verified in-app top frame must create a
single-use capability bound to that origin and request; a standalone Chrome VIEW
intent cannot supply that evidence. The current release continues to refuse
external `fileid` requests.

## AutoScript parameters and certificate filters

The native parser currently supports `encodedcert`, `nonexpired`, `authcert` and
`qualified` constraints. Unknown filters, alternate `filters.*` groups and
`mandatoryCertSelection` are refused. Non-empty `mcv` and `minkeysize` are also
refused; the Firma Mobile version must not be represented as a desktop AutoFirma
version. Requests that suppress confirmation remain unsupported.

The inspected primary reference is the pinned
[clienteafirma CertFilterManager at fe60ef3f](https://github.com/ctt-gob-es/clienteafirma/blob/fe60ef3fdbae3c491e97c262a2179e2787b85776/afirma-keystores-filters/src/main/java/es/gob/afirma/keystores/filters/CertFilterManager.java),
not a claim about the latest upstream revision. It defines additional issuer,
subject, key-usage, policy and certificate-class filters. Implementing those
requires exact matching semantics and bounded parsers, with synthetic X.509
fixtures for both matching and rejection. Silently dropping an unfamiliar filter
would change the requested identity constraint and is not a compatibility fix.

## Desktop loopback WebSocket

Recognizing `afirma://websocket` or handing it to the official AutoFirma package
does not implement the desktop local service. The native parser now reports this
particular unsupported protocol instead of an undifferentiated rejection. No
listener is added and no arbitrary localhost access is granted.

Any future service requires a separate reviewed protocol model and pinned
compatibility fixtures. At minimum it needs loopback-only binding, a strong
single-use capability issued by the foreground app, request/session binding,
bounded connections and payloads, expiration, explicit per-operation confirmation,
and complete lifecycle cleanup. An Origin header alone is not authorization.
Unauthenticated certificate enumeration and a general network relay must remain
unavailable. Browser WSS certificate provisioning is a separate compatibility
problem; bypassing TLS validation or silently installing a root is not an answer.

## Verification boundary

The added controller cases assert distinct refusal outcomes, zero identity reads,
zero preparation/signing/network effects, and preservation of an existing consent
review. The Android lifecycle test asserts distinct user messages, no consent
dialog and released admission for file retrieval, WebSocket and malformed URIs.
These are unit/Robolectric tests, not proof of real Chrome or portal signing.
Executed checks and remaining integration checks are recorded in the work audit;
real E2E remains outside this task's current authorization.

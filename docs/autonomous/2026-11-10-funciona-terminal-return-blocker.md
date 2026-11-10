# Funciona terminal return: reviewed blocker and bounded design

Date: 2026-11-10

## Decision

Keep the current Funciona profile unchanged. Do not import PR #629, enable
`CLIENT_TLS_AUTH`, add new profile pins, or relax fragment checks as part of this
change.

The available evidence establishes an intended OIDC callback with a fragment. It
does not establish a complete authenticated return contract. A production change
that makes Funciona complete login would therefore need additional reviewed
policy, which this task explicitly excludes. This document records that boundary
and the smallest coherent design to review once the missing evidence exists.
Three local regression tests preserve the existing query, fragment and
certificate-request boundaries.

## Confirmed evidence

[The existing Funciona research](2026-08-17-portal-funciona-deep-research.md)
records first-party application configuration and unauthenticated public
navigation:

- OIDC authority: `https://auth-api.redsara.es/auth/realms/sgad-appfactory`.
- Redirect URI: `https://sede.funciona.gob.es/signin`.
- Authorization Code flow, PKCE `S256`, and `response_mode=fragment`.
- A dynamic OIDC `state` and PKCE challenge before the Keycloak broker hop.
- Autentica's TLS client-certificate request, including an empty issuer list.

That research explicitly states that no client certificate was supplied and no
authenticated endpoint was exercised. Its TLS observation proves a server
challenge, not a completed Funciona login or a complete callback shape.

[PR #629](https://github.com/zaguzovmaksim0-hue/workspace-47/pull/629), reviewed at
head `8cc6d3a1fe4a7807c5ffc70f32546d0828b74792`, adds a QA client-TLS capability
and exact broker/Autentica request pins. It supplies no return constraints.
[The open review finding](https://github.com/zaguzovmaksim0-hue/workspace-47/pull/629#discussion_r4515115339)
identifies the missing OIDC return. Inspection of the local implementation
independently confirms the finding.

No observed authenticated success or failure callback, complete post-certificate
redirect sequence, or explicit review of its fragment fields is included in this
PR diff. The configured evidence URLs and review dates are references; they do
not substitute for those missing observations.

## Why a fragment exception is insufficient

The current model and lifecycle impose several distinct checks:

1. `ClientAuthUrlConstraint` describes an exact HTTPS origin and path plus exact
   fixed/required query parameters. It has no fragment contract.
2. The same constraint type is used for source-carried URLs, certificate-request
   continuations, and return URLs. Making its shared matcher ignore or accept
   arbitrary fragments would change more than terminal navigation.
3. `SiteProfileCatalogParser.clientAuth` requires empty return constraints for
   `REDIRECT_AFTER_SOURCE` and `DIRECT_FROM_SOURCE`. Existing explicit return
   constraints belong to the reviewed in-place GET flow. Merely adding a
   `returnUrlConstraints` entry to #629 is rejected by the parser.
4. `ClientAuthWebViewClient.isAllowed` rejects fragments. With no explicit return
   constraints, it permits the authorized request origin and source origin,
   which in #629 are Autentica and the Keycloak broker, not Funciona's callback.
5. `BrowserScreen` hands control back only when the policy's return matcher and
   the selected profile's initiator origin both match.
6. The OIDC state observed before the broker is not part of #629's authorized
   source/target pair. Its source contains broker session parameters; its target
   contains SAML parameters. No native mapping from that pair to the eventual
   OIDC state has been established.

A change to one `rawFragment` condition cannot supply this missing contract.
Nor should certificate acceptance be reported as successful application
authentication.

## Bounded generic design for a later reviewed implementation

Introduce a distinct terminal-return contract rather than granting fragments to
the shared source/continuation matcher. This contract is a capability to hand
navigation back to the existing browser owner; it must never authorize a
certificate request, add a profile origin, or count as a generic client-auth
browse URL.

The contract needs:

- An exact HTTPS origin and path, with an explicitly supported port.
- Exact query parameter names and fixed values, or an explicit empty query.
- Exact permitted fragment shapes for success and denial, including required
  names, duplicate rejection, strict decoding, control-character rejection and
  bounded values. No arbitrary fragment string or extra parameter is accepted.
- Explicit session correlation where the application protocol requires it. The
  OIDC state belongs to the original authorization transaction; a parameter name
  that happens to appear in SAML or a different broker transaction is not a
  substitute.
- An owner-bound, epoch-bound, bounded lifetime and one-shot handoff. Redirects,
  retries and confirmation must not silently renew the authorization.

The terminal handoff should be distinct from an authorization to continue loading
a certificate-enabled view. It must require a current owner and a proven
main-frame GET, recheck the complete target and session binding, revoke the
certificate flow and its preferences through the existing cleanup mechanism,
and preserve the unchanged callback URL for the original application's OIDC
handler. Legacy callbacks, subframes, POST requests, stale owners and consumed
returns must not gain this path.

The mechanism must preserve the original browser state needed by the OIDC
client, including its existing state/PKCE storage. It must not export, log,
interpret as a signature, or send callback credentials to an additional service.
Existing query-only return behavior and all source, request and continuation
fragment prohibitions remain unchanged.

## Evidence required before adding Funciona policy

A reviewable, sanitized structural trace is needed for the post-certificate
sequence and the success/denial return. It should record origins, paths, methods,
parameter names, cardinality and encoding rules, without retaining certificate
material, authorization codes, tokens or session values. The review must identify
how the original OIDC state and browser storage survive the broker and native TLS
flow.

Only then can the exact Funciona terminal policy and necessary intermediate
return contracts be authored. Neither public discovery metadata nor the existence
of the `signin` route determines all returned parameter shapes and state
relationships.

Synthetic tests should cover exact matching, wrong host/path/port/query/fragment,
duplicate and malformed fields, session mismatch, old epoch/owner/deadline,
one-shot delivery, rejection before the TLS decision, and preservation of
certificate/source/continuation scopes. Real login and signing remain separate
acceptance claims and require separately authorized E2E evidence.

## Verification in this change

No production policy or trust boundary changes. The added local tests cover an
explicit synthetic query-return constraint, its exact scope and state binding,
fragment rejection on source/certificate URLs, and rejection of a fragment inside
a source-carried return descriptor. They intentionally make no claim about live
Funciona authentication. Gradle, real certificates and E2E are outside this
change.

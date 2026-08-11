# Generation 45 — Murcia public signing-boundary recheck — 2026-08-11

## Scope

This was a bounded unauthenticated GET-only recheck of the already-catalogued CARM Sede surface
`murcia-sede` (`ES-PUB-0113`). No form was submitted, no certificate or identity was selected, no
credential/cookie material was supplied, and no WAF or access-control challenge was bypassed.

## Current public boundary

The official Sede landing page and the current procedure page for procedure `385` both returned HTTP
200. Their loaded same-origin scripts are the generic CARM site assets (`jquery.js`, `modernizr.js`,
`what-input.js`, `fastclick.js`, `foundation.min.js`, `utilidades.js`, and `menuCabecera.js`); the
public HTML exposes no `AutoScript`, `MiniApplet`, signing algorithm, CAdES/PAdES/XAdES format, or
callback contract.

The procedure page publishes electronic-start links under `/presentador/inicio/385/...`. A bounded
GET of one such current start crossed immediately to the site's WAF boundary before any signing
runtime was exposed. The previously catalogued public AutoFirma test page now reaches the same WAF
boundary from the current command-line route. Challenge URLs/values were not retained and no bypass
was attempted.

## Result

`murcia-sede` remains `BROWSE_ONLY` / research-only. The public evidence still proves that electronic
processing and signing exist at the product level, but it does not expose a complete browser-local
signing ABI before the current WAF/authentication boundary. No profile, catalog, release, or E2E state
changes are justified.

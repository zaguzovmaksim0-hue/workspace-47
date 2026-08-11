# Canarias procedure pre-auth boundary research — generation 46 — 2026-08-11

## Scope and safety boundary

This slice used only official public, unauthenticated HTTPS GET requests against the Gobierno de
Canarias electronic sede and its same-origin static JavaScript. No form was submitted; no
authentication choice was activated; no credentials, certificate material, cookies, signature,
upload, payment, administrative submission, APK launch, ADB, or device-control workflow was used.
Temporary HTML and JavaScript bodies were deleted after extracting bounded public evidence.

## Exact current procedure

Inventory surface `ES-PUB-0099` (`canarias-sede`) remains `BROWSE_ONLY`. Its next recorded gate was
to inspect a concrete current procedure rather than infer a runtime contract from general AutoFirma
requirements.

The current procedure page
`https://sede.gobiernodecanarias.org/sede/tramites/6861` returned HTTP 200. One bounded fetch had
SHA-256 `9fc001822713f2258bc533f2a77d3ea65016494ed19ebc5ea79c8144497b2902`. The page publishes the
exact electronic-start URL
`https://sede.gobiernodecanarias.org/sede/tramitador/creacion/tramites/6861` but contains no
`AutoFirma`, `AutoScript`, `MiniApplet`, `SHA*withRSA`, `XAdES`, `PAdES`, or `CAdES` contract marker.

A GET to the exact electronic-start URL returned HTTP 303 to
`https://sede.gobiernodecanarias.org/sede/identificacionmenu`. Following that single redirect returned
HTTP 200. A bounded fetch of the resulting identification page had SHA-256
`3e3ad276d1bc86390d0c25465171f1c457ed28205cf21bc75f99d9684937371e`. It exposes an
identification boundary, not a local-signing ABI; no signing algorithm, signature format, payload,
callback, or result-delivery endpoint was found there.

## First-party static-assets check

The procedure/identification pages load the same first-party static bundle under
`https://sede.gobiernodecanarias.org/-/fitem/62f62100-90d4-11f1-a92d-1eff8587b018/`.
The implementation-relevant public files checked in this slice were:

- `category.js`: HTTP 200, SHA-256 `e8c32b1d046d072b9af26705d7b9609c8e9c8b0d512dcb93b18fc584b90c780d`;
- `generico.js`: HTTP 200, SHA-256 `680156400fe5ac89982edd8b3e4754cddf7f762c4f115161e94740f64398d4ca`;
- `scripts.js`: HTTP 200, SHA-256 `2d64f2343e07e0697f01ea5ba015b21ec641378de7cdd05066085024dedff170`;
- `desplegables.js`: HTTP 200, SHA-256 `d81a2caa7290a613ffd7e7cec0610580f36d199d0e546dc9e30990a69f8b11d4`;
- `meta.js`: HTTP 200, SHA-256 `7c016b7b9bb19f1119443a7c22b617c405203ffcc1d94a8dc4da39e55dd505b7`.

None defines or references AutoFirma/AutoScript/MiniApplet, XAdES/PAdES/CAdES, or a `SHA*withRSA`
algorithm. The only `Cl@ve` occurrence found in these five files is in `generico.js` and simply wraps
a banner image in a link to the public Cl@ve registration site; it is not a signing invocation or
portal bridge contract.

## Contract conclusion

The current procedure proves a real public procedure-to-identification transition but the signing
contract, if the procedure later requires one, is not public before the identification boundary.
General sede documentation that names AutoFirma or AutoFirma Móvil is insufficient to invent the
missing algorithm, format/mode, payload semantics, callback, transport, or endpoint.

Therefore `ES-PUB-0099` remains `BROWSE_ONLY` / research-only. No `SiteProfile`, catalog binding,
origin allowlist, bridge capability, inventory status, or release state is changed. The next safe gate
is a first-party unauthenticated procedure asset that directly invokes local signing before an
authentication or form-submission boundary.

## Queue impact

No new implementation-ready portal was promoted. The classified research buffer remains at least 16
surfaces. Exact implementation priority remains Sevilla ATSE after acceptable terminal Codex Cloud
evidence, preserved Melilla STA, then `extremadura-tramites` (`ES-PUB-0109`).

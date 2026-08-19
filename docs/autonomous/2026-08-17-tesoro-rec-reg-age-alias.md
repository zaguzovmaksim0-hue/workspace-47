# ES-PUB-0091 — Tesoro Público → REC / REG-AGE public alias evidence

Reviewed: 2026-08-17

## Scope and safety boundary

This pass used public unauthenticated read-only observation only. No login, credentials, user certificate, certificate selection, signature, form submission, upload, payment, administrative filing, authenticated session or protected endpoint replay was used.

The implemented contract is **ALIAS_ONLY**. The Tesoro origin remains institutional/procedure metadata and is not added to the existing REG-AGE profile's cryptographic trust. The Tesoro page mentions certificate/DNIe and AutoFirma as requirements for the selected adhesion workflow, but this pass does not infer a Tesoro-specific signature algorithm, format, packaging, AutoFirma bridge ABI, server endpoint, client-TLS rule or REG-AGE signing constant from those statements.

## Current public surface

The current public Sede root is `https://www.tesoropublico.gob.es/`. Its server currently omits the FNMT intermediate needed by the Termux/OpenSSL trust path: a normal public curl verification failed with `unable to get local issuer certificate`, while an OpenSSL diagnostic identified the current leaf as the Tesoro government entity certificate issued by `AC SERVIDORES SEGUROS TIPO1`. Research-only static fetches and the isolated Chromium session therefore ignored this server-chain defect. No application trust bypass is implemented.

The root publicly identifies itself as `Sede Electrónica del Tesoro Público` and links the current electronic-services index. A bounded map of all 11 service pages visible from that index was inspected before selecting the narrowest implementation seam.

The selected current procedure is:

`https://www.tesoropublico.gob.es/es/servicios/adhesion-al-codigo-de-buenas-practicas-para-deudores-hipotecarios-en-riesgo-de`

Static SHA-256: `ec97e27e5ef938a95866388142021872c685d9e5d5a30d8ce52434c63d5f065d`.

The page states that the communication of adhesion is made through the `registro electrónico común` by a duly empowered representative. It publishes two independent anchors with the exact same destination:

`https://rec.redsara.es/registro/action/are/acceso.do`

One is the primary service-access button and the other is the textual `registro electrónico común` link. Both are ordinary public `_blank` links.

## Static/script graph

The selected page loads its normal Tesoro/Drupal first-party script bundles. The two decisive current Tesoro bundles observed in the browser had these public hashes:

- `js_3JoVXi-FR0yLK3RcaQmPls-kc4hSj1v8aRb24hwOeGg.js?...` — SHA-256 `5a7f72a85c45112a48008c117984dbe6a94d7ccd5bdbd01da4e04e3ec1ce032e`
- `js_fBloxan6xHtZAmGaK4O7-Nv08B40VFpVxJoweKowzSM.js?...` — SHA-256 `c09e3f17e9ac1581111d4469bf9fdacd38003f49e327318e3c943fefc5559875`

The selected delegation is represented by ordinary anchors in the public DOM; no Tesoro runtime bridge or hidden signing call is needed to establish it. Before explicit navigation, the browser network contained no REC/REG document/XHR/fetch transition.

Other current Tesoro services were inspected as part of the bounded public map but are deliberately outside this implementation: FCT and payment consultation use `servicios.tesoro.es`, SECAD and sandbox use separate application surfaces, purchase/sale uses `wwws.tesoro.es`, and other financial-entity services have their own contracts. They are not merged into ES-PUB-0091 by analogy.

## Browser / network / runtime proof

The decisive public runtime used dedicated non-headless Chromium 149 on Termux X11 with a new temporary browser profile and fresh off-the-record CDP BrowserContexts. It did not reuse the user's browser profile. The dedicated process was stopped and the browser profiles were deleted after observation.

Source-page runtime job: `job_20260817_202519_12166e76`.

The current Tesoro procedure DOM rendered:

- the exact procedure title;
- two anchors to `https://rec.redsara.es/registro/action/are/acceso.do`;
- the statement that the communication is performed through the common electronic register;
- the public certificate/DNIe and AutoFirma requirement text.

Its public network loaded the Tesoro document and scripts but made no REC/REG document/XHR/fetch request before explicit navigation.

A public static request to the exact REC URL returned:

`301 Location: https://reg.redsara.es/`

A first fresh browser context showed that the REG root performs locale routing; with the Termux Chromium default English browser locale it resolved to `/en/`. That result was treated as a locale-boundary fact and was not used as exact `/es/` proof.

The decisive fresh Spanish browser-locale context was created with the CDP locale/user-agent language override so that `navigator.language`, `navigator.languages`, `Intl` locale and the request language were Spanish. Durable job: `job_20260817_202709_fc6e5231`.

It recorded exactly:

1. `GET https://rec.redsara.es/registro/action/are/acceso.do`
2. HTTP `301` → `https://reg.redsara.es/`
3. HTTP `302` → `https://reg.redsara.es/es/`
4. final DOM title `REG - Registro Electrónico General`, document language `es`
5. final URL `https://reg.redsara.es/es/`

That final URL is exactly the existing `reg-age-redsara.startUrl`.

No login button was used, no certificate was selected, and no REG registration was begun or submitted.

## Decision

Classification: `ALIAS_ONLY`.

- inventory entry/procedure: current Tesoro adhesion page above
- observed published intermediate launch: `https://rec.redsara.es/registro/action/are/acceso.do`
- bounded redirect chain under Spanish browser locale: `REC URL → 301 https://reg.redsara.es/ → 302 https://reg.redsara.es/es/`
- exact catalog launch: `https://reg.redsara.es/es/`
- existing profile: `reg-age-redsara`
- protocol family: `DELEGACION_REG_AGE`
- target state: `IMPLEMENTED_NOT_E2E` / generated `E2E_PENDING`
- Tesoro-specific observed signing mechanisms/formats in the generated record: none claimed
- physical accepted-flow E2E: not performed and not claimed

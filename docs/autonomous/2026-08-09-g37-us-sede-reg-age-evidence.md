# G37 candidate evidence — Universidad de Sevilla → REG-AGE

Date: 2026-08-09
Candidate: `us-sede` (`ES-PUB-0019`)
Scope: public, unauthenticated, read-only evidence only.

## Official public evidence

- Universidad de Sevilla procedure page: `https://sede.us.es/oficina/tramites/acceso.do?entity=1098&proc=ISG_01`.
- The public page identifies the procedure as “Presentación de Instancias y Solicitudes Genéricas”.
- The page states that the procedure is performed through the Registro Electrónico de la AGE and that pressing “Iniciar trámite” takes the user there.
- The exact public `Iniciar trámite` anchor is `https://reg.redsara.es/es/`.
- The same US page states `Certificado digital` / `DNI electrónico` for identification and signature.
- The existing `reg-age-redsara` QA-only profile starts at exactly `https://reg.redsara.es/es/` and already carries the independently researched REG-AGE AutoScript/XAdES contract. This G37 candidate does not add or infer a new signing ABI.

## Product consequence

`us-sede` is an implementation-ready alias candidate, but must not clone the REG-AGE signing profile because overlapping trusted signing origins would make `SiteProfileRegistry.resolve(origin)` ambiguous. The public catalog also currently requires a one-to-one profile binding and treats `entryUrl` as both evidence URL and launch URL.

The safe implementation seam is therefore a catalog alias: retain the US procedure page as the unique public `entryUrl`, add an optional `launchUrl`, and allow an alias only when the launch URL exactly equals the referenced active profile's immutable `startUrl`. No new origin, endpoint, algorithm, callback, or certificate rule is authorized by this evidence.

## Acceptance boundary

- QA-only through the existing `reg-age-redsara` activation.
- Public inventory status may become `IMPLEMENTED_NOT_E2E`; generated catalog state may become `E2E_PENDING` only after implementation gates pass.
- Never promote US to `VERIFIED_E2E` without separate physical evidence.
- No authentication, certificate selection, signing, upload, form filling, payment, or administrative submission was performed for this research.

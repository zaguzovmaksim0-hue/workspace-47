# Ministerio de Cultura → REG-AGE delegation evidence — 2026-08-17

## Public first-party evidence

- Official electronic office: `https://cultura.sede.gob.es/`.
- Exact public service page: `https://cultura.sede.gob.es/servicio?id=Registro-Electr%C3%B3nico-General`.
- An unauthenticated GET of that page returned the Ministerio de Cultura electronic-office service titled **Registro Electrónico General**.
- The page explicitly identifies the service as the **Registro Electrónico General de la Administración General del Estado (REG-AGE)** and explains that it is used to present documents, solicitudes, escritos y comunicaciones when there is no specific normalized electronic procedure/form.
- The same first-party page publishes an **Acceso al Registro Electrónico** link to `https://reg.redsara.es/`.
- Workspace-47 already contains the QA-only `reg-age-redsara` profile with canonical Spanish `startUrl` `https://reg.redsara.es/es/`.

The public REG root is locale-negotiated, so this evidence does not claim that the literal root URL is an immutable `/es/` redirect. The bounded catalog alias instead keeps the Cultura service page as `entryUrl` and launches only the already reviewed canonical Spanish profile start URL.

## Bounded implementation

`ES-PUB-0062` is promoted only to `IMPLEMENTED_NOT_E2E` / `E2E_PENDING`. The Ministry origin is not added to the REG-AGE initiator/redirect/trusted origins, and no Cultura-specific certificate requirement, signing ABI, algorithm, endpoint, AutoFirma contract, or client-TLS behavior is inferred.

Research used public GET/navigation only. No login, certificate, private key, signature, form submission, payment, upload, or administrative filing was performed.

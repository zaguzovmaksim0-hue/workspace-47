# Educación / REG-AGE alias evidence — 2026-08-17

- Inventory surface: `age-ministerio-de-educacion-formacion-profesional-y-deportes` (`ES-PUB-0066`).
- Safe unauthenticated GET of the official Ministerio de Educación, Formación Profesional y Deportes procedure page returned HTTP 200 on 2026-08-17 with the same final URL; no session was stored or replayed.
- The 2026 Liceo Español Cervantes de Roma selection page states that applications are presented telematically through the Registro Electrónico General de la Administración General del Estado and publishes the exact destination `https://reg.redsara.es/es/`.
- The same page explicitly says the application period ended on 13 April 2026 and shows a definitive-resolution update dated 17 July 2026. The implementation therefore records the exact delegation contract and does not claim that this particular call is still open.
- Workspace-47 already has the QA-only `reg-age-redsara` profile whose immutable `startUrl` is exactly `https://reg.redsara.es/es/`.
- This change is only a public-catalog alias: retain the Ministry procedure page as `entryUrl`, use exact REG-AGE as `launchUrl`, and reuse `reg-age-redsara` without adding a Ministry origin, signing endpoint, algorithm, callback, certificate rule, or cryptographic contract.
- Release remains fail-closed. No authentication, certificate selection, signature, form completion, POST, or administrative submission was performed.

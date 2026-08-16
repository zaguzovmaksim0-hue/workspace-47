# Exteriores / REG-AGE alias evidence — 2026-08-16

- Inventory surface: `age-ministerio-de-asuntos-exteriores-union-europea-y-cooperacion` (`ES-PUB-0060`).
- Safe unauthenticated GET of the official Ministerio de Asuntos Exteriores consular service page returned HTTP 200 on 2026-08-16 without storing/replaying a session.
- The public page for **Baja del Registro de Matrícula** states that when the applicant is already in Spain the request may be made through Registro Electrónico and publishes the exact destination `https://reg.redsara.es/es/`.
- Workspace-47 already has the QA-only `reg-age-redsara` profile whose immutable `startUrl` is exactly `https://reg.redsara.es/es/`.
- Therefore this change is only a public-catalog alias: keep the Exteriores page as `entryUrl`, use the exact REG-AGE URL as `launchUrl`, and reuse `reg-age-redsara` without adding an Exteriores origin, signing endpoint, algorithm, callback, certificate rule, or cryptographic contract.
- Release remains fail-closed because the reused REG-AGE profile is not release-enabled. No certificate selection, authentication, signature, form completion, POST, or administrative submission was performed.

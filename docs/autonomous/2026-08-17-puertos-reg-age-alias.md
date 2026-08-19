# Puertos del Estado — bounded REG-AGE alias evidence (2026-08-17)

Target: `ES-PUB-0085` / Puertos del Estado. Evidence in this pass is public, unauthenticated and read-only.

The AGE-directory URL stored by the inventory, `https://sede.puertos.gob.es/Paginas/Contenido.aspx`, currently responds with an HTTP 301 to the live Sede at `https://puertos.sede.gob.es`. The current public Sede exposes `Registro Electrónico General` at `https://puertos.sede.gob.es/servicio?id=Registro-Electr%C3%B3nico-General`.

That first-party service page explicitly identifies the service as the **Registro Electrónico General de la Administración General del Estado (REG-AGE)** and publishes `Acceso al Registro Electrónico` through the public link `https://reg.redsara.es/`. A fresh unauthenticated Chromium context followed only GET navigation and observed the exact chain:

1. `https://puertos.sede.gob.es/servicio?id=Registro-Electr%C3%B3nico-General` → HTTP 200;
2. `https://reg.redsara.es/` → HTTP 302;
3. `https://reg.redsara.es/es/` → HTTP 200, title `REG - Registro Electrónico General`.

The final URL is byte-for-byte the existing `reg-age-redsara` profile `startUrl`. No Puertos origin is added to that profile's initiator/trust lists. No REG-AGE signing constant is inferred from the Puertos AC2 frontend. The Puertos service page does not establish a Puertos-specific certificate/signature ABI, so certificate/signature/format/algorithm/endpoint fields remain conservative where not explicitly proven.

The public Puertos Sede also loads the generic AC2 frontend. That fingerprint is not used as implementation evidence for cryptographic behavior; the implementation decision rests only on the exact first-party REG-AGE delegation and the fresh public redirect chain above.

No login, Cl@ve completion, certificate selection/use, signing, upload, form submission, payment or administrative filing occurred. Browser routing aborted non-GET requests; the only observed blocked non-GETs were telemetry requests from the Puertos page. No physical accepted-flow E2E was performed, so the truthful state is `IMPLEMENTED_NOT_E2E` / `E2E_PENDING`.

# DGOJ public-navigation boundary — 2026-08-24

## Public evidence

- `https://sede.ordenacionjuego.gob.es/` returned HTTP 200 and identifies the Dirección General de Ordenación del Juego electronic office.
- `https://sede.ordenacionjuego.gob.es/tramite/login/inicio.jjsp?iA=no&limpiarBusqueda=S` returned HTTP 200 as a public procedures/services index without login.
- First-party `https://sede.ordenacionjuego.gob.es/es/firma` documents @firma certificate validation and Cl@ve Firma.
- First-party `https://sede.ordenacionjuego.gob.es/aviso-navegador-chrome` documents several signing routes including AutoFirma, Cl@veFirma, legacy Java applet signing and DNIeRemote.

## Bounded implementation

`ES-PUB-0041` receives a QA-only public-navigation profile for the exact DGOJ origin. The descriptive signing documentation is evidence that richer flows exist, not an exact operation contract. No signing bridge, certificate-selection capability, client-TLS rule, endpoint, signature format, algorithm, callback, redirect origin, or external trust is inferred.

No authentication, certificate selection, private-key operation, upload, payment, POST, or administrative submission was performed.

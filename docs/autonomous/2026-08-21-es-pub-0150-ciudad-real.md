# ES-PUB-0150 — Diputación Provincial de Ciudad Real

Reviewed: 2026-08-21

## Decisive current evidence

- `https://www.dipucr.es/` returned HTTP 200 and currently links **Trámites Electrónicos** to `https://sede.dipucr.es/`.
- `https://sede.dipucr.es/` returned HTTP 200 and currently publishes **Registro Telemático Común** at `https://sede.dipucr.es/iniciaTramite/20`.
- The public procedure page returned HTTP 200 and describes the Registro Telemático as the electronic channel for presenting requests, suggestions, appeals, or complaints.
- Its current **Iniciar trámite** action points to `https://se1.dipucr.es:4443/SIGEM_AutenticacionWeb/seleccionEntidad.do?REDIRECCION=RegistroTelematico&tramiteId=DPCR_SRS&SESION_ID=&ENTIDAD_ID=005`. A public read-only request to that stable URL returned the SIGEM **Redirección Cl@ve** shell, whose form posts to `https://pasarela.clave.gob.es/Proxy2/ServiceProvider`. Any generated SAML/session payload was transient and is intentionally not retained here.

## Implemented boundary

The candidate adds only a QA-only trusted-browse profile for the stable public Registro Telemático page on `sede.dipucr.es:443`. The next SIGEM handoff uses HTTPS port `4443`, which the current `ExactOrigin` / `JuntaOriginPolicy` model intentionally rejects; it is therefore not broadened into the profile. No authentication, `CLIENT_TLS_AUTH`, certificate-selection, signing, signature format/algorithm, callback, final filing, registration, submission, or payment capability is claimed.

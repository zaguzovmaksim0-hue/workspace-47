# Evidencia de navegación pública de gestiona2 — 2026-08-25

## Alcance

Esta ficha acredita únicamente el contrato de navegación QA-only para
`ES-PUB-0013` (`comunidad-madrid-gestiona2`). No acredita identificación,
selección de certificado, AutoFirma, firma, registro, justificante ni
aceptación E2E.

## Fuentes oficiales

- [Página pública exacta del trámite](https://gestiona2.comunidad.madrid/gpse_solicitud/accesos.jsf?numref=2094)
- [Selector público de formularios](https://gestiona2.comunidad.madrid/gpse_solicitud/index_seleccion.jsf)

## Hechos observados

- La página first-party carga por HTTPS y publica el acceso exacto con
  `numref=2094`.
- El HTML first-party contiene los enlaces a los accesos con certificado y
  describe como requisitos AutoFirma, un certificado digital y Java.
- El mismo HTML contiene una comprobación `isMobileOrTablet` que detecta
  Android/iPhone/iPad/Windows Phone/tablet y muestra el aviso: «No se puede
  realizar la solicitud desde dispositivo móvil o tableta».
- Por esa limitación, la implementación local se restringe deliberadamente a
  abrir la página pública exacta. No se añaden orígenes de redirección,
  endpoints, bridge WebView, adapter de firma, client TLS ni capacidades.

## Decisión de compatibilidad

El registro pasa de `UNSUPPORTED_PROTOCOL` a `IMPLEMENTED_NOT_E2E` porque ya
existe una implementación delimitada para la navegación pública exacta y la
fuente portal-specific está disponible. La limitación oficial de móvil se
conserva en `reason`, `protocol_evidence` y `next_gate`; por tanto no se afirma
que el trámite pueda completarse desde Android.

## Verificación local

- El perfil se mantiene `QA_ONLY` y queda excluido del registro release.
- La política de origen acepta únicamente `https://gestiona2.comunidad.madrid`
  en puerto 443 y sin userinfo, subdominios ni esquemas alternativos.
- `SIGN`, `SELECT_CERTIFICATE`, web-message origins y `signingOrigin` quedan
  ausentes o rechazados.
- La entrada del catálogo se resuelve solo para la URL exacta y queda en
  `E2E_PENDING` / `IMPLEMENTED_NOT_E2E`.

No se realizó autenticación, envío, firma, pago ni modificación en el portal.

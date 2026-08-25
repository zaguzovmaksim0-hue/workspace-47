# Diputación de Soria — acceso TLS cliente de la sede

Fecha de revisión: 2026-08-25 (UTC)

## Fuentes first-party

- [Aviso legal de la Diputación de Soria](https://www.dipsoria.es/varios/aviso-legal)
- [Entrada pública de la sede electrónica](https://portaltramitador.dipsoria.es/web/inicioWebc.do?opcion=noreg)
- [Página first-party de opciones de acceso](https://portaltramitador.dipsoria.es/web/inicioWebc.do?opcion=cargar&redirige=L2NhcmdhTWVudVdlYi5kbz9vcGNpb249bm9yZWc%3D&entidad=SORIA&idioma=1)
- [Endpoint first-party de acceso con certificado](https://portaltramitador.dipsoria.es/web/inicioWebcCert.do?opcion=ssl&entidad=SORIA&redirige=L2NhcmdhTWVudVdlYi5kbz9vcGNpb249bm9yZWc%253D&idioma=1)

## Observaciones reproducibles

- El aviso legal identifica `portaltramitador.dipsoria.es/web` como la sede
  electrónica de la Diputación y declara que el acceso general es libre; las
  áreas concretas pueden exigir autenticación según su propia información.
- La página de opciones publica «Usuarios con Certificado Digital o DNIe» y
  su función `pulsarCertificado()` navega desde el source exacto
  `/web/inicioWebc.do?opcion=cargar&redirige=...&entidad=SORIA&idioma=1` al
  target exacto `/web/inicioWebcCert.do?opcion=ssl&entidad=SORIA&redirige=...&idioma=1`.
- Una petición GET normal al target sin certificado provocó renegociación TLS
  1.2; el trace `openssl s_client` observó `read server certificate request`
  y el servidor terminó con `handshake failure` al no recibir certificado.
  La renegociación no publicó una lista de CA aceptables, por lo que el perfil
  conserva `allowEmptyIssuerList=true` y permite RSA/EC.
- No se seleccionó certificado en el navegador, no se creó ningún trámite, no
  se firmó y no se presentó información. El formato, algoritmo y callback de
  firma documental siguen sin contrato.
- El antiguo `https://sede.dipsoria.es` no se usa como entry point: la sede
  vigente está publicada bajo `portaltramitador.dipsoria.es/web`.

## Alcance implementado

El perfil `diputacion-soria-sede-client-auth` es `QA_ONLY` y permite abrir la
página de opciones y autorizar únicamente la transición exacta al endpoint
TLS cliente. Declara solo `CLIENT_TLS_AUTH`; `SIGN`, `SELECT_CERTIFICATE`, el
formato/algoritmo de firma y la presentación final permanecen fuera de alcance.
El estado es `IMPLEMENTED_NOT_E2E` hasta validar el acceso con un certificado
autorizado en Android.

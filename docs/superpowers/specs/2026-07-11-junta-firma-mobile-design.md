# Junta Firma Mobile — diseño aprobado

Fecha: 2026-07-11

## Contexto

Se crea desde cero un cliente Android personal y no oficial para que el portal
objetivo de la Junta permanezca dentro de un WebView mientras selección de
certificado, confirmación y firma se ejecutan nativamente. No se reutiliza ni
modifica la aplicación AutoFirma.

La inspección pública inicial confirmó que la página usa un objeto global
`MiniApplet`, llama `cargarMiniApplet` y `sign`, solicita CAdES explicit con
`SHA1withRSA`, declara un endpoint tri-phase en `ws024` y recibe firma y
certificado mediante callbacks JavaScript. La librería incluye caminos
afirma/intent/websocket, pero la rama runtime aún debe observarse.

## Opciones consideradas

### 1. Vertical slice guiado por observación — seleccionado

Instrumentar de forma segura el contrato real, envolver solo las llamadas
observadas y construir certificado, bridge y tri-phase alrededor de fixtures
capturadas como metadatos. Minimiza compatibilidad innecesaria, superficie de
ataque y conjeturas.

### 2. Emulación general de AutoFirma Client Protocol

Implementar de entrada sign/selectcert/websocket/storage/legacy. Aumenta la
compatibilidad teórica, pero multiplica parsers, codecs y estados no exigidos
por el portal. Se rechaza hasta que otra operación se observe.

### 3. Reescritura/proxy de JavaScript remoto

Interceptar respuestas y modificar scripts del portal. Es frágil ante CSP y
cambios, dificulta demostrar integridad y amplía la frontera de confianza. Se
rechaza.

## Arquitectura seleccionada

Un solo módulo Android con Compose/Material 3 y siete límites explícitos:

1. UI y estado de ciclo de vida.
2. WebView seguro y navegación allowlisted.
3. shim de MiniApplet + WebMessage bridge origin-bound.
4. parser/router de solicitudes AutoFirma.
5. repositorio SAF y sesión PKCS#12 solo en memoria.
6. motor de firma/CAdES y cliente tri-phase.
7. red/cookies/SSRF y diagnóstico sanitizado.

Las interfaces de dominio no dependen de Activity/WebView. URLs, mensajes,
responses y P12 se validan en sus límites. El código crypto usa JCA/biblioteca
mantenida, no primitivas caseras.

## Flujo principal

```text
Primer inicio
  → SAF selecciona P12
  → password CharArray valida identity
  → summary confirmado
  → WebView abre portal
  → shim intercepta llamada real MiniApplet.sign
  → Kotlin valida origin/request/algorithm/URLs
  → usuario confirma
  → pre-sign HTTPS con cookies del host exacto
  → firma local en memoria
  → post-sign HTTPS
  → resultado tipado vuelve por requestId
  → shim invoca el callback real guardado
  → página envía su formulario y continúa
```

El flujo se detiene antes de pre-sign hasta que el runtime capture confirme el
contrato. Un redirect a login, HTML inesperado o 401/403 produce
`SESSION_EXPIRED`, no éxito.

## Bridge

Document-start instala un interceptor sobre la asignación de `MiniApplet`.
Cuando la página entrega callbacks a `sign`, las referencias se guardan dentro
de una clausura JS y se asocian a un UUID. El mensaje a Android contiene solo
JSON validado. El retorno no usa concatenación ni nombre de función hardcoded:
el shim resuelve el UUID y llama a la referencia guardada.

El listener se registra exclusivamente para origins HTTPS concretos. Kotlin
vuelve a comprobar source origin, URL principal y navigation id. Navegar,
cancelar o completar destruye el pending request; un UUID no se reutiliza.

## Certificado y memoria sensible

Se persiste el URI SAF, no el P12. La contraseña vive en `CharArray` y se limpia
en `finally`. Se obtiene una `PrivateKeyEntry`, se valida X.509/vigencia/
keyUsage/cadena/RSA y se prueba correspondencia firmando un nonce. La clave no
se serializa. La identidad se bloquea tras process death, timeout, background
prolongado o acción manual.

## Red y cookies

Solo HTTPS y hosts exactos. Callback URLs se someten a validación SSRF,
resolución/redirect allowlist, puerto y tamaño. Cookies se toman y devuelven por
URL exacta, sin jar compartida entre hosts y sin logs. XML está endurecido
contra XXE/DTD y limitado en tamaño.

## Experiencia y errores

La UI española muestra siempre el carácter no oficial. Cada firma enseña sitio,
certificado, formato y algoritmo y exige `Firmar`. SHA-1 muestra advertencia de
legacy compatibility. Errores se traducen a códigos cerrados y diagnóstico
sanitizado; retry crea una nueva operación solo cuando no existe ambigüedad de
entrega.

## Estrategia de pruebas

TDD cubre parsers, PKCS#12, crypto, cookie isolation, allowlist/SSRF, tri-phase,
request lifecycle y sanitización. Instrumentation cubre Activity, SAF,
WebView/intents/SSL/bridge/configuration. El portal real se prueba aparte en
Android 16 con certificado autorizado. Release solo se acepta después de unit,
instrumentation, E2E, logcat audit, apksigner, zipalign y SHA-256.

## Decisiones de alcance

- API 26–36, objetivo primario API 36.
- No P12 copiado ni modo persistente desbloqueado en la primera entrega.
- CAdES/RSA; SHA-256 normal y SHA-1 solo para el flujo observado con warning.
- Sin DES, SHA-512 ni operaciones AutoFirma no observadas.
- Sin localhost WSS, CA local, wildcard de host o Play fallback.
- Una carencia de certificado/sesión/interacción real se informa como E2E no
  ejecutado; nunca se convierte en declaración de producto terminado.

## Documentos normativos del proyecto

- `docs/spec.md` — comportamiento y arquitectura autoritativos.
- `docs/threat-model.md` — activos, límites y mitigaciones.
- `docs/protocol-observations.md` — evidencia segura y desconocidos runtime.
- `docs/test-plan.md` — gates y casos de aceptación.

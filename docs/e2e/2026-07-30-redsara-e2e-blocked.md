# REG-AGE / RedSARA — E2E XAdES bloqueado por flujo administrativo

- Fecha de la comprobación: `2026-07-30 Europe/Madrid`
- Dispositivo: Android API 36, dispositivo físico
- Profile: `reg-age-redsara`, versión 1
- Adapter: `local-xades-detached-v1`
- Resultado: `E2E_BLOCKED_REQUIRES_AUTHENTICATED_ADMINISTRATIVE_FLOW`

## Comprobación segura

La aplicación abrió el origin exacto `https://reg.redsara.es` con el profile QA
y el adapter XAdES esperado. Se inspeccionaron únicamente las rutas públicas y
sus controles de navegación, sin introducir datos ni crear un registro.

La portada ofrece `Nuevo registro` y `Mis registros`. Ambas rutas llevan a
`/es/login`, cuya única acción de identificación visible es `Accede con tu
Cl@ve`. No existe en la superficie pública observada un login con la firma XAdES
del profile.

La evidencia JavaScript ya documentada sitúa `AutoScript.sign(xml,
"SHA512withRSA", "XAdES Detached", null, ...)` después de preparar el XML de
resumen de una solicitud y antes de `saveXMLAutoSign`. Por tanto, alcanzar la
aceptación XAdES real exige:

1. autenticarse mediante Cl@ve;
2. crear o preparar una solicitud administrativa real;
3. generar su XML de resumen;
4. firmarlo;
5. permitir que el portal lo guarde en el expediente.

Ese flujo no es un escenario de prueba no destructivo. La comprobación se
detuvo antes de Cl@ve y antes de crear, modificar, firmar o guardar cualquier
registro.

## Decisión de estado

El profile permanece:

- versión `1`;
- `VERIFIED_CONTRACT / QA_ONLY`;
- public catalog `E2E_PENDING / IMPLEMENTED_NOT_E2E`.

No se promueve a `VERIFIED_E2E`. Los tests locales de XAdES prueban estructura y
criptografía, pero no aceptación del portal. Una promoción futura exige un caso
administrativo real autorizado por la persona usuaria y evidencia sanitizada de
que RedSARA aceptó la firma, sin confundir el callback local con la aceptación.

## Privacidad

No se conservaron credenciales Cl@ve, certificado, firma, XML, cookies, tokens,
campos de formulario, identificadores de expediente ni datos personales. No se
incorporaron capturas de pantalla al repositorio.

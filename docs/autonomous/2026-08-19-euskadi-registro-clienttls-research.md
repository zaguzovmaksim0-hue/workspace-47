# Euskadi — Registro Electrónico General 1017701: bounded Client TLS contract

Fecha de revisión: 2026-08-19. Target: `ES-PUB-0115`.

## Alcance implementado

Se implementa únicamente el acceso con certificado cliente del Registro Electrónico General 1017701. El perfil queda `QA_ONLY` y `IMPLEMENTED_NOT_E2E`. No se implementan ni se afirman como compatibles la firma documental, el registro/envío final ni ningún pago.

## Evidencia actual

- La ficha oficial del Registro Electrónico General describe la secuencia rellenar, adjuntar, firmar y enviar y enlaza el formulario del procedimiento `1017701`.
- El formulario actual carga Giltza con `ISNUEVAFIRMAACTIVA=true`; para el objeto activo anuncia `xades-enveloping`, pero los assets públicos no fijan de forma suficiente el algoritmo criptográfico de la firma documental.
- Un `initsign` técnico con fixture no administrativo y `mecanismo_firma=auto` devuelve el bootstrap OAuth de Izenpe, sin invocar ninguna clave privada.
- La rama pública `Certificados digitales` de Izenpe termina en `https://eidas.izenpe.com/trustedx-authserver/izenpe/authentication`, que contiene un formulario oculto `POST` hacia `https://eidas2.izenpe.com/cert-authn-external-validation/authenticate` con exactamente los campos efímeros `request` y `x_correlation_id`.
- El endpoint `eidas2.izenpe.com` envía TLS `CertificateRequest`, con lista CA no vacía y tipos de certificado RSA/ECDSA/DSA. La aplicación limita deliberadamente el perfil a RSA/EC, que son los algoritmos de clave admitidos por el modelo actual.
- Un GET desnudo al endpoint devuelve 400; por tanto la implementación conserva el `application/x-www-form-urlencoded` POST original y no lo sustituye por una navegación GET.

## Boundaries de seguridad

El bridge solo se expone en el origen exacto `https://eidas.izenpe.com` para el perfil Euskadi. El shim intercepta únicamente la página exacta `/trustedx-authserver/izenpe/authentication`, el target exacto de Client TLS y los dos hidden inputs esperados. Native vuelve a validar perfil, origin, page, main-frame, epoch, UUID v4, tamaño/caracteres de ambos valores y replay antes de construir el POST. El host de Client TLS permanece fuera de los browser origins normales y se abre solo en el WebView aislado de client-auth después de la confirmación existente del usuario.

Los valores `request` y `x_correlation_id` se tratan como opacos; no se registran ni persisten. Muestras actuales mostraron longitudes variables, por lo que se usan límites máximos conservadores en lugar de fijar una longitud observada accidentalmente.

## Fuera de alcance

No se ejecutó firma con clave privada. No se hizo presentación/registro final. Aunque Giltza anuncia XAdES-Enveloping para el objeto activo y mantiene un callback `sign/move`, el algoritmo exacto y el ABI completo de la firma siguen `NO_VERIFICADO`; no se crea capability `SIGN`.

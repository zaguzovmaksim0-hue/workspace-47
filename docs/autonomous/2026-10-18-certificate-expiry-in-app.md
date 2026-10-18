# Aviso de caducidad del certificado dentro de la aplicación — parcial

Base de implementación: `98e4f9d679f5352e8a1eadba54d7527ccbcd8526`.

## Alcance entregado

Un aviso informativo junto a los metadatos del certificado actual, tanto bloqueado como
desbloqueado. `CertificateSummaryDetails` incorpora una sola llamada al nuevo componente.
No cambia la selección, importación, firma, autenticación, sesión, caché ni validación.
Los errores existentes, incluido el de certificado caducado y su región accesible
asertiva, siguen siendo independientes del aviso.

La política pura recibe el instante de caducidad, el instante actual y una zona horaria:

- Más de 30 días naturales: sin aviso.
- De 30 a 8 días naturales, incluidos los límites: aviso de próxima caducidad.
- De 7 a 1 días naturales: aviso más urgente, con singular/plural en español.
- Mismo día local y antes o exactamente en `notAfter`: caduca hoy.
- Después del instante completo `notAfter`: caducado, incluso si la fecha local no cambia.

Ambos instantes se convierten a fechas en la misma zona para contar días naturales.
No se dividen duraciones entre 24 horas: los cambios de horario de verano no convierten
8 días en 7 ni un día de 23 horas en cero. El aviso muestra fecha, hora, segundos y offset
usando esa misma zona. Es una política de presentación, no una autorización de firma.

El componente usa texto explícito, colores de MaterialTheme y una región accesible
fusionada `Polite`; la urgencia no depende solo del color. Solo recibe `validUntil`:
no guarda certificados, claves, contraseñas, identificadores o preferencias. No registra
PII ni envía datos o notificaciones.

La hora se actualiza al entrar en `STARTED` y cada minuto mientras la pantalla está
activa. El ciclo se cancela en `STOP` y al salir de la composición; al volver se toma
otra lectura. En una pantalla mantenida abierta puede haber hasta un minuto de demora
en cambiar el texto al cruzar un límite. La validación de firma existente no se modifica.

## Verificación y trabajo pendiente

- Política pura: 18 pruebas JUnit locales superadas, incluidos 30/7/0, precisión del
  instante, zona horaria, cambio de día, año bisiesto y transiciones DST de Madrid.
- Se añadieron 12 pruebas de UI para CI: mensajes, plural, timestamp/zona, accesibilidad,
  sustitución del aviso, integración con metadatos bloqueados/desbloqueados, coexistencia
  del error de caducidad y ausencia de aviso sin certificado. No se ejecutaron localmente.
- XML y placeholders de recursos comprobados sin Android. Compilación/lint/UI de Android
  y la matriz canónica de CI quedan a cargo de la integración; no se afirman superados.

**TODO: recordatorios de caducidad en segundo plano.** Este slice no incorpora scheduler,
worker, alarmas, permisos, notificaciones ni garantías de aviso fuera de la aplicación.
El punto completo del roadmap #374 no debe marcarse terminado con este cambio.

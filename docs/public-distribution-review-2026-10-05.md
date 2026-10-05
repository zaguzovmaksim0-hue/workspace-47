# Revisión de distribución pública — 5 de octubre de 2026

## Resultado y alcance

**El APK de uso diario examinado NO está listo para anunciarse como distribución
pública de producción.** La publicación del código fuente y una distribución
APK/AAB tienen requisitos diferentes. Esta revisión técnica no es una certificación,
una autorización administrativa ni un dictamen jurídico.

Se revisaron la historia de LICENSE, NOTICE y procedencia, archivos binarios
seguidos por Git, configuración y bloqueo de dependencias, workflows de CI,
configuración de firma/variantes, manifiesto, política de perfiles y el contenido
del APK instalado. No se ejecutaron E2E ni operaciones reales con certificados.

Base de fuentes: `c828d5c37d53296a175d58e2f280b51154c35a1f`.
APK de uso diario: Firma Mobile 0.2.11, código 13, `dev.junta.firmamobile`.
SHA-256: `2d18fb00e294529589dccec6d11969ef55a4a4c395ae0aaa568075e3862baef8`.
Su árbol de fuentes coincide con el candidato `c53d93b1` integrado en main.

## 1. Licencia de fuentes y autoría — corrección documental

- `0d1eb33b` añadió Apache-2.0 el 12 de agosto de 2026.
- `bfef6e7a41c91c87d3b83ac5b27440c97ab71c4d` sustituyó la raíz por MIT y
  «Copyright (c) 2026 Kai» junto con un workflow macOS y un wheel de un ensayo
  no relacionado con Firma Mobile. La historia demuestra la sustitución; no
  demuestra que Kai sea autor o titular de Firma Mobile.
- `fe4d9599` retiró ese workflow y wheel, pero no corrigió LICENSE.
- El 5 de octubre el mantenedor indicó el nombre **Maksim Zaguzov** y aprobó
  restaurar **Apache-2.0**. LICENSE y NOTICE reflejan esa decisión.
- No se reescribe la historia ni se pretende revocar permisos válidos que se
  hubieran concedido sobre copias anteriores. Las atribuciones de terceros se
  conservan. La declaración de procedencia de agosto no es una auditoría jurídica
  independiente de cada contribución posterior.

## 2. Firma del APK — BLOQUEADO para producción

El certificado del APK examinado tiene DN `C=US, O=Android, CN=Android Debug`.
Su huella SHA-256 es
`f1064f2515a54351b685159efbabe70f38d18e54a77764d64d87a2fe0e8c1907`.
La variante `optimized` usa explícitamente `signingConfigs.getByName("debug")`.
Ser no depurable no convierte ese certificado en una clave de publicación.

Android documenta que las tiendas, incluido Google Play, no aceptan normalmente
certificados de depuración. Debe prepararse una estrategia de firma de producción,
protección y recuperación de clave, y continuidad de actualizaciones. No se han
creado claves, configurado credenciales ni cambiado la firma instalada.
Un cambio de firmante no debe provocar una desinstalación o pérdida de datos.

La variante `release` ya falla si faltan sus cuatro parámetros privados; no recurre
al debug key. Pasar esa prueba negativa no demuestra que exista un release firmado.

## 3. Licencias de dependencias y fuentes — BLOQUEADO

El APK contiene nueve entradas cuyo nombre incluye LICENSE o NOTICE. La entrada
`META-INF/NOTICE.md` corresponde a Jakarta XML Binding; no es el NOTICE de Firma
Mobile ni un paquete completo de avisos de sus dependencias.

Se observaron dos fuentes: Bebas Neue (recurso renombrado por Android) y
`assets/com/tom_roush/pdfbox/resources/ttf/LiberationSans-Regular.ttf`.
El OFL de Bebas Neue existe en el repositorio, pero no se identificó un texto OFL
completo en las entradas de avisos del APK. Los registros internos de ambas fuentes declaran OFL-1.1 y una URL, no el texto
completo. Liberation Sans identifica copyright Google 2010 / Red Hat 2012;
Bebas Neue identifica The Bebas Neue Project Authors 2019, mientras que su
archivo OFL upstream conserva Dharma Type 2010. NOTICE conserva ambas
atribuciones de Bebas, sin sustituir una por otra.

Huellas de los archivos de fuente inspeccionados:
- Bebas Neue: `08e4623805102d819f58601e46e345648846075e363b2ceb23313c2d1c83ec73`
  (coincide con la fuente seguida por Git).
- Liberation Sans: `76d04c18ea243f426b7de1f3ad208e927008f961dc5945e5aad352d0dfde8ee8`.

`app/build.gradle.kts` excluye `/META-INF/LICENSE.md`. Esa exclusión requiere una
copia alternativa verificable de los textos aplicables; no puede suponerse que
el NOTICE de Jakarta sustituya el texto de su licencia.

La tabla antigua de dependencias es incompleta: no incluía PDFBox Android ni las
familias Google identity/Play services, y registraba versiones antiguas de Bouncy
Castle y coroutines. Las familias Google no deben etiquetarse automáticamente
como Apache-2.0 por aparecer en Maven.

El lock de `releaseRuntimeClasspath` enumera 160 coordenadas (incluye BOM y módulos
metadato; no significa 160 bibliotecas presentes en DEX). Un sondeo del caché local
solo encontró directorios para 121: no basta para validar el grafo final.

La nueva recogida en CI conserva para `optimized` y `release`:

1. Árbol de dependencias resuelto por Gradle con bloqueo estricto.
2. Hash del lock y SHA-256 de AAR/JAR/POM encontrados para esas coordenadas.
3. Avisos LICENSE/NOTICE/COPYING/COPYRIGHT, incluidos los de `classes.jar` dentro
   de AAR, guardados por hash sin extraer rutas de archivo no fiables.
4. Declaraciones de licencia de POM, y ausencias explícitas.

### Resultado observado del primer ciclo de CI

En el candidato `a65263285327f2093810f4716818cfe0c3679cb6`, el artefacto
`runtime-license-evidence-a65263285327f2093810f4716818cfe0c3679cb6`
(run `37348021442`) contiene las 160 coordenadas y 118 AAR/JAR para cada variante,
sin coordenadas carentes de evidencia de caché. Esto cierra la limitación del
sondeo local, pero no la revisión de términos o del empaquetado final.

Hallazgos concretos de esos artefactos:

- Los tres JAR Bouncy Castle 1.85 incluyen `META-INF/LICENSE.md` con MIT y
  copyright 2000–2026 The Legion of the Bouncy Castle Inc. El build excluye esa
  ruta; falta comprobar/añadir una entrega alternativa íntegra.
- Jakarta XML Binding 3.0.1 incluye un LICENSE.md de 1.644 bytes que se excluye,
  aunque su NOTICE.md sí aparece en el APK examinado.
- Apache Commons Codec 1.18.0 y XML Security 3.0.6 tienen avisos propios de Apache
  Software Foundation; no se encontraron sus archivos NOTICE en la lista del APK.
- Los diez componentes Google identity/Play services declaran **Android Software
  Development Kit License**, no Apache-2.0. Sus AAR contienen
  `third_party_licenses.json` y `.txt` con atribuciones adicionales. Deben
  preservarse y revisarse por separado; no están en la lista de avisos del APK.
- PDFBox Android declara Apache-2.0 en su POM, pero su fuente Liberation Sans tiene
  OFL-1.1. La licencia del contenedor no basta para todos los recursos incluidos.
- Stax2 API 4.2.1 identifica BSD de dos cláusulas en su archivo LICENSE, pero
  remite a un texto externo; un enlace solo no es un bundle verificado completo.

La revisión del colector añadió soporte para POM con y sin namespace XML y
excluyó archivos `.class` cuyo nombre contiene “Notice” o “License”: esos nombres
no los convierten en avisos legales. El siguiente candidato repite la evidencia
con estas correcciones y debe pasar sus propios checks.

**La herramienta produce evidencia, nunca aprobación automática.** Un POM puede
heredar términos; que un archivo no contenga un aviso no implica ausencia de
obligaciones. Deben revisarse también recursos incrustados, términos de SDK y
reconciliarse los avisos con el APK/AAB final. El bundle de CI no se incorpora
por sí solo a la aplicación ni cierra este bloqueo.

## 4. Variante y compatibilidad — BLOQUEADO para promesas generales

`optimized` permite perfiles QA y desactiva R8. `release` prohíbe QA y aplica
minificación. Por ello, los tests y el APK optimized no equivalen a la aceptación
de una compilación release real.

El catálogo contiene 137 perfiles: 132 `VERIFIED_CONTRACT`/`QA_ONLY`, cuatro
`VERIFIED_E2E`/`ENABLED` y uno `EXPERIMENTAL`/`ENABLED`. Estos estados declarados
no constituyen una nueva comprobación real de portales.

La política de publicación exige `VERIFIED_E2E` y `ENABLED` para perfiles sensibles.
No se ha eliminado ni debilitado ese requisito. Un portal en el catálogo no prueba
que identificación, firma y presentación funcionen en todas sus operaciones.
La prueba de región tras la actualización sigue pendiente de resultado del usuario.
No se han hecho nuevas E2E, conforme a la instrucción del mantenedor.

## 5. Privacidad, ficha y canal de distribución — PENDIENTE

El manifiesto fuente desactiva backup y tráfico en claro. El APK final declara
INTERNET, ACCESS_NETWORK_STATE, ACCESS_COARSE_LOCATION, USE_BIOMETRIC,
USE_FINGERPRINT y el permiso interno de receptor no exportado.
No se encontraron PKCS#12/PFX/keystores ni la identidad sintética de tests por nombre
en el ZIP del APK. Esto no sustituye un análisis completo de datos o secretos.

El README explica almacenamiento local y caché de desbloqueo. Antes de una ficha
pública hay que documentar el flujo real: importación local, caché cifrada,
identificación/firma hacia el portal elegido, cookies, descargas, geocodificación
mediante Android y cualquier SDK tercero. Una frase breve en pantalla no es una
política de privacidad completa.

Si se elige Google Play, faltan la política de privacidad pública y dentro de la
app, la declaración Data safety y las declaraciones/formularios aplicables a la
ficha. La política User Data exige información coherente con los SDK y el
comportamiento real. No se ha accedido a Play Console ni afirmado cumplimiento.
Los requisitos de otros canales deben evaluarse por separado.

Las referencias a organismos públicos son descriptivas. No hay evidencia de
certificación oficial, respaldo de AutoFirma ni autorización universal de portales.

## 6. Verificaciones técnicas ya observadas

- Main `c828d5c3`: CI y Security scans finalizados con éxito.
- Configuración de dependencias: bloqueo estricto y metadatos de verificación.
- CI: tests Android, lint, pruebas Python/Go, Gitleaks de historia y OSV.
- Política de recursos: los 21 PNG/WebP de procedencia no resuelta no han vuelto.
- Árbol actual: los binarios seguidos relevantes son Bebas Neue y Gradle Wrapper;
  el wheel Kai ya no está. Los recursos aportados al APK por dependencias son
  un inventario distinto y siguen pendientes de cierre.

Estos controles reducen riesgos; no demuestran ausencia absoluta de defectos o
vulnerabilidades. El resultado del nuevo PR se debe consultar por su SHA exacto.

## Siguiente secuencia antes de distribuir

1. Integrar la corrección de licencia y avisos tras revisión/CI del candidato.
2. Revisar la evidencia exacta de dependencias y construir el bundle completo de
   avisos que realmente acompañe al APK/AAB; verificar fuentes y SDK adicionales.
3. Decidir canal, alcance experimental/producción y capacidades anunciadas.
4. Preparar privacidad y ficha coherentes con los flujos reales.
5. Autorizar y configurar la firma privada y un plan de actualizaciones seguro.
6. Construir el release final, comprobar sus variantes minificadas, permisos,
   contenido, hash, firma, avisos y evidencia de las capacidades anunciadas.
7. Publicar únicamente tras la decisión explícita del mantenedor.

## Fuentes

- [Texto oficial Apache-2.0](https://www.apache.org/licenses/LICENSE-2.0)
- [Firma Android y limitaciones del debug key](https://developer.android.com/studio/publish/app-signing)
- [Avisos open source de Google Play services](https://developers.google.com/android/guides/opensource)
- [Google Play: User Data](https://support.google.com/googleplay/android-developer/answer/10144311)
- [CI del main examinado](https://github.com/zaguzovmaksim0-hue/workspace-47/actions/runs/37338294484)
- [Seguridad del main examinado](https://github.com/zaguzovmaksim0-hue/workspace-47/actions/runs/37338294469)

# Cambio explícito de versión de Android

`tools/bump_android_version.py` es una ayuda local para preparar un cambio de
versión revisable. No crea una versión publicada ni aprueba una distribución.
Necesita Python 3.8+ y Git; no ejecuta Gradle ni accede a claves, APK o servicios.
La herramienta no cambia la versión al incorporarse al repositorio.

## Vista previa y escritura

Ejemplo ilustrativo **sólo si** la declaración actual es `13` / `0.2.11`:

```bash
python3 tools/bump_android_version.py \
  --expected-code 13 --expected-name 0.2.11 --next-name 0.2.12
```

El modo predeterminado imprime el diff sin escribir el archivo, el índice o un
lock. Tras revisar la propuesta, la escritura exige el flag completo `--write`:

```bash
python3 tools/bump_android_version.py \
  --expected-code 13 --expected-name 0.2.11 --next-name 0.2.12 --write
git diff -- app/build.gradle.kts
```

Ambos modos requieren que `app/build.gradle.kts` esté seguido en Git y limpio
frente a `HEAD`, tanto en el índice como en la copia de trabajo. Los cambios en
otros archivos no bloquean la operación. La ruta se determina desde la ubicación
de la herramienta en `tools/`, no desde el directorio de ejecución. No admite
una ruta de destino arbitraria ni componentes de ruta que sean enlaces simbólicos.

## Límites y rechazos

- La pareja `--expected-code` / `--expected-name` es obligatoria y debe coincidir
  exactamente con el archivo actual. Una expectativa obsoleta provoca un rechazo.
- `versionCode` es un entero decimal canónico positivo; el único código propuesto
  es el actual **+1**, hasta `2100000000`. No se puede elegir un salto ni volver a
  cero. Si el código actual ya es el máximo, la herramienta no escribe.
- `--next-name` es explícito. Los nombres usan el estilo estable existente
  `MAJOR.MINOR.PATCH`: componentes ASCII sin ceros iniciales, salvo `0`, y sin
  sufijos de prerelease/build. La comparación de componentes es numérica y el
  nombre siguiente debe ser estrictamente mayor. No se deriva del código.
- Sólo se reconocen las asignaciones literales únicas `versionCode = <entero>` y
  `versionName = "<nombre>"`, cada una en una línea propia y directamente dentro
  del único `defaultConfig` reconocido, hijo directo de `android`. Se conserva
  el formato del repositorio: `android {` sin sangría y `defaultConfig {` con
  cuatro espacios. Expresiones, setters, duplicados, nombres iguales o inferiores,
  contenido inválido y layouts no reconocidos requieren revisión manual; no se
  intenta adivinar la nueva fuente de versión.
- El reconocimiento excluye comentarios y cadenas de documentación; no es un
  parser/evaluador de Kotlin ni una validación de Gradle. No demuestra la versión
  efectiva frente a plugins, scripts externos u overrides dinámicos. Si cambia
  el modelo de versiones, revisar la herramienta y sus pruebas antes de usarla.

Sólo se sustituyen los dos literales; se preservan los demás bytes, los saltos de
línea y el modo de permisos. No se modifica `versionNameSuffix`, el package,
la configuración de firma, las variantes, los workflows ni la política de QA.
No se usa el número de ejecución de CI, el recuento de commits ni un retry para
inventar una versión.

## Escritura local y concurrencia

`--write` crea de forma exclusiva `bump-android-version.lock` en la ruta Git del
worktree. No espera ni elimina un lock ya existente. Si queda uno tras una
interrupción, comprobar primero que no haya otro escritor activo e inspeccionar
el archivo antes de retirar manualmente ese lock. Una vista previa no lo crea.

Se prepara un temporal en el directorio de destino, se conservan los permisos,
se hace flush/fsync y se vuelve a comprobar el contenido/identidad del archivo y
su estado en Git antes de `os.replace`. La sustitución es atómica en ese sistema
de archivos; los errores anteriores a la sustitución no escriben el destino y
limpian el temporal y el lock adquirido en una salida normal.

El lock coordina sólo las instancias de esta herramienta **en ese worktree**.
No es un bloqueo de editores ajenos ni un CAS del sistema de archivos: no editar
el destino ni cambiar de rama/índice durante la operación. No se promete una
transacción ante pérdida de energía. Una interrupción o un error de salida o de
limpieza posterior a la sustitución puede dejar el cambio ya escrito: inspeccionar
el diff, no deducir el estado únicamente del código de salida.

Repetir la misma orden con la pareja antigua falla, tanto antes de guardar el
cambio en Git (destino sucio) como después (expectativas obsoletas). No consume
un código adicional. No actualizar automáticamente las expectativas para forzar
un retry.

## No es un asignador global ni una aprobación de release

Dos ramas/worktrees pueden partir de la misma versión y proponer el mismo
siguiente código. La herramienta **no garantiza monotonía global** entre ramas,
no consulta versiones publicadas y no reserva un contador central. Es una ayuda
parcial y explícita, no automatización completa del release.

Los PR de versión/release deben serializarse. Actualizar/rebasar la rama contra
el `origin/main` vigente, revisar qué versiones están integradas y publicadas, y
volver a calcular/verificar la pareja y el diff antes de integrar. No conservar
un bump obsoleto ni resolver un conflicto de versiones a ciegas. La rama final
requiere revisión y los checks aplicables del SHA exacto según la
[política de verificación](agents/github-actions-verification.md). La firma,
publicación y cualquier aprobación de distribución siguen siendo procesos
separados y expresamente autorizados.

## Pruebas acotadas

```bash
PYTHONDONTWRITEBYTECODE=1 python3 -m unittest tools.tests.test_bump_android_version -v
```

Las pruebas usan repositorios Git y declaraciones sintéticas en directorios
temporales; no escriben el Gradle de la aplicación. Cubren preview/escritura,
guards, límites, declaraciones ambiguas, rutas, locks, fallos de I/O, cambios
durante la preparación y retries. Son evidencia local de desarrollo, no un
sustituto del gate de GitHub Actions sobre el candidato final.

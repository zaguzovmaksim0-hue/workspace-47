# Building on Termux

> **Optional focused local path.** These notes describe native-Termux compatibility for contributors and agents that need a narrow local RED/GREEN check. The canonical broad candidate gate runs in GitHub Actions according to `docs/agents/github-actions-verification.md`; do not routinely run the full unit/lint/assembly matrix on the phone.

## Supported scope

Native Termux/aarch64 is a supported focused-development environment. The authoritative broad integration environment is the repository GitHub Actions workflow on GitHub-hosted Ubuntu, which uses AGP's standard Maven AAPT2 and Android SDK tooling. Termux retains its project-local verified AAPT2 compatibility path for narrow local checks.

The project-local AAPT2 provision pins the binary plus the Abseil/Protobuf pair
that caused the observed ABI failure. Other native libraries declared by the
Termux package (`fmt`, `libc++`, `libexpat`, `libpng`, `libzopfli`, and `zlib`)
come from the target phone's configured Termux environment. This is an explicit
non-blocking limitation for the single-device build scope; the functional
resource compile and clean Gradle gate detect an incompatible phone runtime.

The `release` variant has no debug/test signing fallback. Its `preReleaseBuild`
depends on a fail-closed verification task that requires an external private
release signing configuration. QA remains separately debug-signed; the
publication gate never supplies real release credentials.

For Android SDK 36 host tests, Java/Kotlin bytecode targets remain JVM 17 while
Gradle `Test` workers use an OpenJDK 21 launcher required by Robolectric. Native
Termux therefore requires both `openjdk-17` and `openjdk-21`; `JAVA_HOME` stays
on Java 17 for Gradle/build execution and the wrapper exposes both installations
to Gradle toolchain discovery.

Outside Termux, the wrapper leaves the Android Gradle Plugin on its standard
Maven AAPT2 path. Native Termux on aarch64 needs the project bootstrap because
AGP's downloaded desktop binary is not executable there.

From the repository root, provision it without installing global packages:

```bash
./tools/bootstrap-termux-aapt2.sh bootstrap
./gradlew --version
```

The bootstrap uses the device's configured Termux apt metadata and `apt-get
download`, pins the exact aarch64 package versions and SHA-256 values, verifies
each archive before extraction, verifies the extracted native AAPT2 hash and
version, and writes only to ignored `.gradle/termux-aapt2/`. A verified local
launcher supplies the pinned runtime libraries directly to every AAPT2 process,
including processes spawned by an existing Gradle daemon. It never invokes
`pkg install`, `apt install`, `dpkg -i`, or `curl | sh`.

Every literal `./gradlew` invocation on Termux/aarch64 verifies the cached
archives, launcher, native binary, the exact extracted runtime, AAPT2 version,
and a functional resource-compile smoke before supplying
`android.aapt2FromMavenOverride`. A missing or corrupt cache fails closed and
prints the bootstrap command. The experimental-option warning from AGP is
expected for this compatibility override.

Useful checks:

```bash
./tools/bootstrap-termux-aapt2.sh verify
./tools/bootstrap-termux-aapt2.sh self-test
./gradlew verifyPortableAapt2Configuration verifyResolvedCoreVersion
```

`self-test` copies the working provision into temporary ignored directories,
then proves missing installs, native-binary corruption, runtime-package
corruption, and extracted-runtime corruption are rejected. It does not modify
the working provision.

To repair the cache, rerun the bootstrap. To return to AGP's default AAPT2,
remove `.gradle/termux-aapt2/`; desktop builds continue normally, while the next
Termux/aarch64 Gradle invocation will fail with the actionable bootstrap command.

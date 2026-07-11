# Building on Termux

Supported desktop hosts use the Android Gradle Plugin's standard Maven AAPT2.
Native Termux on aarch64 needs the Termux build because AGP's downloaded host
binary is not executable there.

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

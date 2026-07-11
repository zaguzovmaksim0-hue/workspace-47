#!/bin/sh
set -eu

SCRIPT_DIR=$(CDPATH= cd -- "$(dirname -- "$0")" && pwd)
PROJECT_ROOT=$(CDPATH= cd -- "$SCRIPT_DIR/.." && pwd)
SCRIPT_PATH=$SCRIPT_DIR/$(basename -- "$0")
DEFAULT_ROOT=$PROJECT_ROOT/.gradle/termux-aapt2

AAPT2_VERSION=16.0.0.4-1
AAPT2_SHA256=d35298f13ec26eee362d4e84f534b29b8e5f288b86c89d803ba4fb8ccb9784aa
AAPT2_SOURCE=pool/main/a/aapt2/aapt2_16.0.0.4-1_aarch64.deb
AAPT2_ARCHIVE=aapt2_16.0.0.4-1_aarch64.deb
AAPT2_NATIVE_VERSION=2.20-android-16.0.0_r4
AAPT2_NATIVE_SHA256=0921eed340fd997b3402a58acdb639e32a1445f6527427906c6f24e48a73caab
AAPT2_LAUNCHER_VERSION=1

ABSEIL_VERSION=20260526.0
ABSEIL_SHA256=e489fac652cddc39d9436141e627285f1034a545a06fbb19c420514a419ad877
ABSEIL_SOURCE=pool/main/a/abseil-cpp/abseil-cpp_20260526.0_aarch64.deb
ABSEIL_ARCHIVE=abseil-cpp_20260526.0_aarch64.deb

PROTOBUF_VERSION=2:35.1
PROTOBUF_SHA256=a1ba7c7f0e5903a2134662653d3e7b9ffceaa78bdd00e07ac985e2d313ebc738
PROTOBUF_SOURCE=pool/main/libp/libprotobuf/libprotobuf_2:35.1_aarch64.deb
PROTOBUF_ARCHIVE=libprotobuf_2%3a35.1_aarch64.deb

RUNTIME_RELATIVE=data/data/com.termux/files/usr/lib
NATIVE_PACKAGE_PATH=data/data/com.termux/files/usr/bin/aapt2
CLEANUP_PATH=
RESTORE_ROOT=
RESTORE_BACKUP=

cleanup() {
    if [ -n "$RESTORE_BACKUP" ] && [ -e "$RESTORE_BACKUP" ] && [ ! -e "$RESTORE_ROOT" ]; then
        mv -- "$RESTORE_BACKUP" "$RESTORE_ROOT" || true
    fi
    if [ -n "$CLEANUP_PATH" ] && [ -e "$CLEANUP_PATH" ]; then
        rm -rf -- "$CLEANUP_PATH"
    fi
}
trap cleanup 0 HUP INT TERM

die() {
    printf 'ERROR: %s\n' "$*" >&2
    exit 1
}

require_command() {
    command -v "$1" >/dev/null 2>&1 || die "Required command not found: $1"
}

require_termux_aarch64() {
    case ${PREFIX:-} in
        */com.termux/files/usr) ;;
        *) die "This bootstrap is only for native Termux" ;;
    esac
    [ "$(uname -m)" = "aarch64" ] || die "This bootstrap supports Termux aarch64 only"
}

sha256_of() {
    sha256sum "$1" | awk '{ print $1 }'
}

launcher_contract() {
    printf '%s\n' \
        '#!/bin/sh' \
        'set -eu' \
        'SCRIPT_DIR=$(CDPATH= cd -- "$(dirname -- "$0")" && pwd)' \
        'ROOT=$(CDPATH= cd -- "$SCRIPT_DIR/.." && pwd)' \
        'RUNTIME=$ROOT/runtime/data/data/com.termux/files/usr/lib' \
        'LD_LIBRARY_PATH=$RUNTIME${LD_LIBRARY_PATH:+:$LD_LIBRARY_PATH}' \
        'export LD_LIBRARY_PATH' \
        'exec "$SCRIPT_DIR/aapt2.real" "$@"'
}

write_launcher() {
    destination=$1
    launcher_contract > "$destination"
    chmod 755 "$destination"
}

metadata_field() {
    package=$1
    version=$2
    field=$3
    apt-cache show "$package=$version" 2>/dev/null |
        awk -F ': ' -v wanted="$field" '$1 == wanted { print $2; exit }'
}

verify_metadata() (
    package=$1
    version=$2
    expected_sha=$3
    expected_source=$4

    actual_version=$(metadata_field "$package" "$version" Version)
    actual_arch=$(metadata_field "$package" "$version" Architecture)
    actual_sha=$(metadata_field "$package" "$version" SHA256)
    actual_source=$(metadata_field "$package" "$version" Filename)

    [ "$actual_version" = "$version" ] || die "Pinned apt metadata is missing for $package=$version"
    [ "$actual_arch" = "aarch64" ] || die "Unexpected architecture for $package=$version: $actual_arch"
    [ "$actual_sha" = "$expected_sha" ] || die "Apt metadata SHA-256 mismatch for $package=$version"
    [ "$actual_source" = "$expected_source" ] || die "Apt source path mismatch for $package=$version"
)

verify_archive() (
    archive=$1
    package=$2
    version=$3
    expected_sha=$4

    [ -f "$archive" ] || die "Missing verified package archive: $archive"
    [ "$(sha256_of "$archive")" = "$expected_sha" ] || die "Package SHA-256 mismatch: $archive"
    [ "$(dpkg-deb -f "$archive" Package)" = "$package" ] || die "Package identity mismatch: $archive"
    [ "$(dpkg-deb -f "$archive" Version)" = "$version" ] || die "Package version mismatch: $archive"
    [ "$(dpkg-deb -f "$archive" Architecture)" = "aarch64" ] || die "Package architecture mismatch: $archive"
)

runtime_manifest() (
    root=$1
    [ -d "$root" ] || die "Missing extracted runtime directory: $root"
    cd -- "$root"
    find . \( -type f -o -type l \) -print | LC_ALL=C sort | while IFS= read -r entry; do
        if [ -L "$entry" ]; then
            printf 'link|%s|%s\n' "$entry" "$(readlink "$entry")"
        else
            printf 'file|%s|%s\n' "$entry" "$(sha256_of "$entry")"
        fi
    done
)

download_package() (
    package=$1
    version=$2
    expected_sha=$3
    expected_source=$4
    archive_name=$5
    download_dir=$6
    existing_root=$7

    verify_metadata "$package" "$version" "$expected_sha" "$expected_source"
    destination=$download_dir/$archive_name
    existing=$existing_root/packages/$archive_name
    if [ -f "$existing" ] && [ "$(sha256_of "$existing")" = "$expected_sha" ]; then
        cp -- "$existing" "$destination"
    else
        rm -f -- "$download_dir/$package"_*.deb
        (
            cd -- "$download_dir"
            apt-get download "$package=$version"
        )
        set -- "$download_dir/$package"_*.deb
        [ "$#" -eq 1 ] && [ -f "$1" ] || die "Expected one downloaded archive for $package=$version"
        if [ "$1" != "$destination" ]; then
            mv -- "$1" "$destination"
        fi
    fi
    verify_archive "$destination" "$package" "$version" "$expected_sha"
)

verify_install() (
    root=$1
    packages=$root/packages
    launcher=$root/bin/aapt2
    native=$root/bin/aapt2.real
    runtime=$root/runtime/$RUNTIME_RELATIVE

    for command_name in awk cmp dpkg-deb env find mktemp readlink sha256sum sort; do
        require_command "$command_name"
    done

    verify_archive "$packages/$AAPT2_ARCHIVE" aapt2 "$AAPT2_VERSION" "$AAPT2_SHA256"
    verify_archive "$packages/$ABSEIL_ARCHIVE" abseil-cpp "$ABSEIL_VERSION" "$ABSEIL_SHA256"
    verify_archive "$packages/$PROTOBUF_ARCHIVE" libprotobuf "$PROTOBUF_VERSION" "$PROTOBUF_SHA256"
    [ -x "$launcher" ] || die "Missing executable Termux AAPT2 launcher: $launcher"
    expected_launcher_sha=$(launcher_contract | sha256sum | awk '{ print $1 }')
    [ "$(sha256_of "$launcher")" = "$expected_launcher_sha" ] ||
        die "Termux AAPT2 launcher integrity mismatch: $launcher"
    [ -x "$native" ] || die "Missing executable Termux AAPT2: $native"
    [ "$(sha256_of "$native")" = "$AAPT2_NATIVE_SHA256" ] || die "Native AAPT2 SHA-256 mismatch: $native"
    [ -f "$runtime/libabsl_hash.so" ] || die "Missing local Abseil runtime"
    [ -f "$runtime/libprotobuf.so" ] || die "Missing local Protobuf runtime"

    mkdir -p -- "$PROJECT_ROOT/.gradle"
    verification_dir=$(mktemp -d "$PROJECT_ROOT/.gradle/termux-aapt2.verify.XXXXXX")
    trap 'rm -rf -- "$verification_dir"' 0 HUP INT TERM
    expected_runtime=$verification_dir/runtime
    mkdir -p -- "$expected_runtime"
    dpkg-deb -x "$packages/$ABSEIL_ARCHIVE" "$expected_runtime"
    dpkg-deb -x "$packages/$PROTOBUF_ARCHIVE" "$expected_runtime"
    runtime_manifest "$runtime" > "$verification_dir/actual.manifest"
    runtime_manifest "$expected_runtime/$RUNTIME_RELATIVE" > "$verification_dir/expected.manifest"
    cmp -s "$verification_dir/actual.manifest" "$verification_dir/expected.manifest" ||
        die "Extracted runtime does not match the pinned package archives"

    version_output=$("$launcher" version 2>&1) ||
        die "Native AAPT2 failed its version smoke: $version_output"
    [ "$version_output" = "Android Asset Packaging Tool (aapt) $AAPT2_NATIVE_VERSION" ] ||
        die "Unexpected native AAPT2 version: $version_output"

    smoke_res=$verification_dir/smoke/res/values
    smoke_output=$verification_dir/smoke.zip
    mkdir -p -- "$smoke_res"
    printf '%s\n' '<resources><string name="bootstrap_smoke">ok</string></resources>' > "$smoke_res/strings.xml"
    compile_output=$("$launcher" compile --dir "$verification_dir/smoke/res" -o "$smoke_output" 2>&1) ||
        die "Native AAPT2 failed its functional compile smoke: $compile_output"
    [ -s "$smoke_output" ] || die "Native AAPT2 functional compile smoke produced no output"
    printf '%s\n' "$launcher"
)

bootstrap() {
    root=$1
    [ "$root" = "$DEFAULT_ROOT" ] || die "Bootstrap destination must be $DEFAULT_ROOT"
    require_termux_aarch64
    for command_name in apt-get apt-cache awk cp dpkg-deb env mktemp mv sha256sum uname; do
        require_command "$command_name"
    done

    mkdir -p -- "$PROJECT_ROOT/.gradle"
    CLEANUP_PATH=$(mktemp -d "$PROJECT_ROOT/.gradle/termux-aapt2.bootstrap.XXXXXX")
    download_dir=$CLEANUP_PATH/downloads
    install_dir=$CLEANUP_PATH/install
    extract_dir=$CLEANUP_PATH/aapt2-package
    mkdir -p -- "$download_dir" "$install_dir/bin" "$install_dir/packages" "$install_dir/runtime" "$extract_dir"

    download_package aapt2 "$AAPT2_VERSION" "$AAPT2_SHA256" "$AAPT2_SOURCE" "$AAPT2_ARCHIVE" "$download_dir" "$root"
    download_package abseil-cpp "$ABSEIL_VERSION" "$ABSEIL_SHA256" "$ABSEIL_SOURCE" "$ABSEIL_ARCHIVE" "$download_dir" "$root"
    download_package libprotobuf "$PROTOBUF_VERSION" "$PROTOBUF_SHA256" "$PROTOBUF_SOURCE" "$PROTOBUF_ARCHIVE" "$download_dir" "$root"

    cp -- "$download_dir/$AAPT2_ARCHIVE" "$install_dir/packages/$AAPT2_ARCHIVE"
    cp -- "$download_dir/$ABSEIL_ARCHIVE" "$install_dir/packages/$ABSEIL_ARCHIVE"
    cp -- "$download_dir/$PROTOBUF_ARCHIVE" "$install_dir/packages/$PROTOBUF_ARCHIVE"
    dpkg-deb -x "$install_dir/packages/$AAPT2_ARCHIVE" "$extract_dir"
    cp -- "$extract_dir/$NATIVE_PACKAGE_PATH" "$install_dir/bin/aapt2.real"
    chmod 755 "$install_dir/bin/aapt2.real"
    write_launcher "$install_dir/bin/aapt2"
    dpkg-deb -x "$install_dir/packages/$ABSEIL_ARCHIVE" "$install_dir/runtime"
    dpkg-deb -x "$install_dir/packages/$PROTOBUF_ARCHIVE" "$install_dir/runtime"
    verify_install "$install_dir" >/dev/null

    backup=$PROJECT_ROOT/.gradle/termux-aapt2.previous.$$
    if [ -e "$root" ]; then
        mv -- "$root" "$backup"
        RESTORE_ROOT=$root
        RESTORE_BACKUP=$backup
    fi
    if ! mv -- "$install_dir" "$root"; then
        die "Could not activate verified Termux AAPT2"
    fi
    if [ -n "$RESTORE_BACKUP" ]; then
        rm -rf -- "$RESTORE_BACKUP"
        RESTORE_ROOT=
        RESTORE_BACKUP=
    fi

    printf 'Provisioned verified Termux AAPT2 at %s\n' "$root"
    verify_install "$root" >/dev/null
}

self_test() {
    root=$1
    require_termux_aarch64
    require_command cp
    require_command mktemp
    require_command truncate
    verify_install "$root" >/dev/null

    CLEANUP_PATH=$(mktemp -d "$PROJECT_ROOT/.gradle/termux-aapt2.self-test.XXXXXX")
    if "$SCRIPT_PATH" verify --root "$CLEANUP_PATH/missing" >/dev/null 2>&1; then
        die "Missing installation was accepted"
    fi

    corrupt_native=$CLEANUP_PATH/corrupt-native
    cp -R -- "$root" "$corrupt_native"
    truncate -s 1 "$corrupt_native/bin/aapt2.real"
    if "$SCRIPT_PATH" verify --root "$corrupt_native" >/dev/null 2>&1; then
        die "Corrupt native AAPT2 was accepted"
    fi

    corrupt_package=$CLEANUP_PATH/corrupt-package
    cp -R -- "$root" "$corrupt_package"
    truncate -s 1 "$corrupt_package/packages/$PROTOBUF_ARCHIVE"
    if "$SCRIPT_PATH" verify --root "$corrupt_package" >/dev/null 2>&1; then
        die "Corrupt runtime package was accepted"
    fi

    corrupt_runtime=$CLEANUP_PATH/corrupt-runtime
    cp -R -- "$root" "$corrupt_runtime"
    printf 'tamper' >> "$corrupt_runtime/runtime/$RUNTIME_RELATIVE/libprotobuf.so"
    if "$SCRIPT_PATH" verify --root "$corrupt_runtime" >/dev/null 2>&1; then
        die "Corrupt extracted runtime library was accepted"
    fi

    printf '%s\n' 'missing install rejected'
    printf '%s\n' 'native corruption rejected'
    printf '%s\n' 'runtime package corruption rejected'
    printf '%s\n' 'extracted runtime corruption rejected'
}

contract() {
    printf 'aapt2-package|%s|%s\n' "$AAPT2_VERSION" "$AAPT2_SHA256"
    printf 'aapt2-native|%s|%s\n' "$AAPT2_NATIVE_VERSION" "$AAPT2_NATIVE_SHA256"
    printf 'aapt2-launcher|%s\n' "$AAPT2_LAUNCHER_VERSION"
    printf 'abseil-cpp|%s|%s\n' "$ABSEIL_VERSION" "$ABSEIL_SHA256"
    printf 'libprotobuf|%s|%s\n' "$PROTOBUF_VERSION" "$PROTOBUF_SHA256"
}

action=${1:-bootstrap}
if [ "$#" -gt 0 ]; then
    shift
fi
root=$DEFAULT_ROOT
while [ "$#" -gt 0 ]; do
    case $1 in
        --root)
            [ "$#" -ge 2 ] || die "--root requires a path"
            root=$2
            shift 2
            ;;
        *) die "Unknown argument: $1" ;;
    esac
done

case $action in
    bootstrap) bootstrap "$root" ;;
    verify)
        require_termux_aarch64
        for command_name in awk dpkg-deb env sha256sum uname; do
            require_command "$command_name"
        done
        verify_install "$root"
        ;;
    self-test) self_test "$root" ;;
    contract) contract ;;
    *) die "Usage: $0 [bootstrap|verify|self-test|contract] [--root PATH]" ;;
esac

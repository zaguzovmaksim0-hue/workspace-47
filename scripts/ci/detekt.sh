#!/usr/bin/env bash
# Standalone, source-level Kotlin analysis; intentionally no Gradle dependency.
set -euo pipefail
DETEKT_VERSION=1.23.8
DETEKT_SHA256=2ce2ff952e150baf28a29cda70a363b0340b3e81a55f43e51ec5edffc3d066c1
ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)"
cd "$ROOT"
extra_args=()
case "${1:-}" in
  '') [[ $# -eq 0 ]] || { echo 'Unexpected argument' >&2; exit 64; } ;;
  --create-baseline) [[ $# -eq 1 ]] || exit 64; extra_args+=(--create-baseline) ;;
  *) echo 'Usage: bash scripts/ci/detekt.sh [--create-baseline]' >&2; exit 64 ;;
esac
cache_dir="${XDG_CACHE_HOME:-$HOME/.cache}/firmamobile/detekt/$DETEKT_VERSION"
jar="$cache_dir/detekt-cli-$DETEKT_VERSION-all.jar"
mkdir -p "$cache_dir" config/detekt app/build/reports/detekt
verify_jar() {
  printf '%s  %s\n' "$DETEKT_SHA256" "$1" | sha256sum --check --status
}
if [[ ! -f "$jar" ]]; then
  tmp="$(mktemp "$cache_dir/download.XXXXXX")"
  trap 'rm -f "$tmp"' EXIT
  curl --fail --location --retry 3 --connect-timeout 20 --max-time 180 \
    "https://github.com/detekt/detekt/releases/download/v$DETEKT_VERSION/detekt-cli-$DETEKT_VERSION-all.jar" -o "$tmp"
  verify_jar "$tmp" || { echo 'detekt download checksum mismatch' >&2; exit 1; }
  mv "$tmp" "$jar"
fi
verify_jar "$jar" || { echo 'Cached detekt checksum mismatch; remove the corrupt cache file' >&2; exit 1; }
java -Xmx512m -jar "$jar" \
  --input app/src/main/java \
  --build-upon-default-config --base-path "$ROOT" \
  --baseline config/detekt/baseline.xml --max-issues 0 \
  --report xml:app/build/reports/detekt/detekt.xml \
  --report html:app/build/reports/detekt/detekt.html \
  "${extra_args[@]}"

#!/usr/bin/env bash
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)"
readonly -a FORBIDDEN_TOKENS=(
  'dev.junta.firmamobile.action.CATALOG_SMOKE'
  'CatalogSmokeHook'
  'dev.junta.firmamobile.action.E2E_CONTROL'
  'E2eControlHook'
  'E2eSecretInbox'
)

for token in "${FORBIDDEN_TOKENS[@]}"; do
  if rg -n --fixed-strings "$token" "$ROOT_DIR/app/src/main" "$ROOT_DIR/app/src/release"; then
    echo "QA E2E ingress leaked into main/release sources: $token" >&2
    exit 1
  fi
done

if (($# == 0)); then
  echo "PASS source boundary: QA harness ingress exists only outside main/release"
  exit 0
fi
(($# == 1)) || { echo "Usage: $0 [release-apk]" >&2; exit 64; }
apk="$1"
[[ -f "$apk" ]] || { echo "Release APK not found: $apk" >&2; exit 66; }

grep_args=()
for token in "${FORBIDDEN_TOKENS[@]}"; do
  grep_args+=( -e "$token" )
done
if unzip -p "$apk" 'classes*.dex' | grep -aF "${grep_args[@]}"; then
  echo "QA E2E harness ingress found in release DEX" >&2
  exit 1
fi

echo "PASS release DEX boundary: QA harness ingress absent"

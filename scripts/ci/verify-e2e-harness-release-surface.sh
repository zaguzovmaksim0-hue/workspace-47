#!/usr/bin/env bash
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)"
readonly ACTION='dev.junta.firmamobile.action.CATALOG_SMOKE'
readonly CLASS_TOKEN='CatalogSmokeHook'

if rg -n --fixed-strings "$ACTION" "$ROOT_DIR/app/src/main" "$ROOT_DIR/app/src/release"; then
  echo "QA E2E action leaked into main/release sources" >&2
  exit 1
fi
if rg -n --fixed-strings "$CLASS_TOKEN" "$ROOT_DIR/app/src/main" "$ROOT_DIR/app/src/release"; then
  echo "QA E2E hook leaked into main/release sources" >&2
  exit 1
fi

if (($# == 0)); then
  echo "PASS source boundary: QA harness ingress exists only outside main/release"
  exit 0
fi
(($# == 1)) || { echo "Usage: $0 [release-apk]" >&2; exit 64; }
apk="$1"
[[ -f "$apk" ]] || { echo "Release APK not found: $apk" >&2; exit 66; }

# DEX strings are sufficient to catch the receiver/action/class ingress even without decompiling code.
if unzip -p "$apk" 'classes*.dex' | grep -aF -e "$ACTION" -e "$CLASS_TOKEN"; then
  echo "QA E2E harness ingress found in release DEX" >&2
  exit 1
fi

echo "PASS release DEX boundary: QA harness ingress absent"

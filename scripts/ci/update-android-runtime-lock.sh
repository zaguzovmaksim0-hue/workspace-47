#!/usr/bin/env bash
set -euo pipefail

ROOT=$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd -P)
cd "$ROOT"

settings_lock="settings-gradle.lockfile"
if [[ -e "$settings_lock" ]]; then
  echo "Refusing to overwrite pre-existing $settings_lock" >&2
  exit 1
fi

./gradlew :app:verifyRuntimeDependencyLocks --write-locks --no-daemon --console=plain

[[ -s app/gradle.lockfile ]] || {
  echo "app/gradle.lockfile was not generated" >&2
  exit 1
}
[[ -f "$settings_lock" ]] || {
  echo "Gradle did not produce the reviewed version-catalog lock sentinel" >&2
  exit 1
}

expected_settings_lock=$(mktemp)
cleanup_expected_lock() {
  rm -f "$expected_settings_lock"
}
trap cleanup_expected_lock EXIT
cat >"$expected_settings_lock" <<'EOF'
# This is a Gradle generated file for dependency locking.
# Manual edits can break the build and are not advised.
# This file is expected to be part of source control.
empty=incomingCatalogForLibs0
EOF

if ! cmp --silent "$expected_settings_lock" "$settings_lock"; then
  echo "Unexpected settings-gradle.lockfile content; refusing cleanup" >&2
  diff --unified "$expected_settings_lock" "$settings_lock" >&2 || true
  exit 1
fi
rm -f "$settings_lock" "$expected_settings_lock"
trap - EXIT

shopt -s nullglob
root_locks=(*gradle.lockfile)
if (( ${#root_locks[@]} != 0 )); then
  printf 'Unexpected root dependency lock: %s\n' "${root_locks[@]}" >&2
  exit 1
fi

python3 -m unittest \
  tools.tests.test_ci_policy.CiPolicyTest.test_android_runtime_lockfile_is_canonical \
  -v

echo "Android runtime dependency lock updated and verified"

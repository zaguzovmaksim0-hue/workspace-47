#!/data/data/com.termux/files/usr/bin/bash
set -Eeuo pipefail

BRANCH="oss/publication-readiness-20260811"
REPO="${1:-$PWD}"
GITLEAKS_VERSION="8.29.1"
GITLEAKS_ARM64_SHA256="691f826ce7c1c564c9c02d0f9025e8e70803e3816707a4be6224408a06a81eaa"
ANDROID_CMDLINE_REVISION="9123335"
ANDROID_CMDLINE_SHA256="0bebf59339eaa534f4217f8aa0972d14dc49e7207be225511073c661ae01da0a"
ANDROID_PLATFORM="android-36"
ANDROID_BUILD_TOOLS="36.0.0"

fail() {
  printf 'ERROR: %s\n' "$*" >&2
  exit 1
}

step() {
  printf '\n============================================================\n%s\n============================================================\n' "$*"
}

cleanup_paths=()
cleanup() {
  local path
  for path in "${cleanup_paths[@]}"; do
    [[ -n "$path" ]] && rm -rf -- "$path"
  done
}
trap cleanup EXIT

[[ ${PREFIX:-} == */com.termux/files/usr ]] || fail "Run this only inside native Termux."
[[ $(uname -m) == aarch64 ]] || fail "This runner supports native Termux aarch64 only."
git -C "$REPO" rev-parse --is-inside-work-tree >/dev/null 2>&1 || fail "Not a Git worktree: $REPO"
cd "$REPO"

step "1/9 — PREPARE EXACT PUBLICATION BRANCH"
if [[ -n $(git status --porcelain) ]]; then
  git status --short >&2
  fail "Working tree is not clean; publication verification refuses to overwrite local work."
fi
git remote get-url origin | grep -Fq 'zaguzovmaksim0-hue/workspace-47' || fail "Unexpected origin repository."
git fetch --all --tags --prune
if git show-ref --verify --quiet "refs/heads/$BRANCH"; then
  git switch "$BRANCH"
else
  git switch --track -c "$BRANCH" "origin/$BRANCH"
fi
git pull --ff-only origin "$BRANCH"
[[ -z $(git status --porcelain) ]] || fail "Working tree became dirty after synchronization."
HEAD_SHA=$(git rev-parse HEAD)
printf 'Candidate SHA: %s\n' "$HEAD_SHA"

step "2/9 — INSTALL TERMUX PREREQUISITES"
pkg update -y
pkg install -y \
  git curl coreutils findutils gawk tar unzip python \
  openjdk-17 aapt apksigner binutils

if [[ -d "$PREFIX/lib/jvm/java-17-openjdk" ]]; then
  export JAVA_HOME="$PREFIX/lib/jvm/java-17-openjdk"
fi
export PATH="${JAVA_HOME:+$JAVA_HOME/bin:}$PATH"
java -version
command -v python >/dev/null || fail "python is missing."
command -v zipalign >/dev/null || fail "zipalign is missing after installing Termux aapt."
command -v apksigner >/dev/null || fail "apksigner is missing after installing Termux apksigner."

step "3/9 — PROVISION ANDROID SDK 36 WHEN NEEDED"
TOOLS_DIR="$REPO/.gradle/oss-publication-tools"
mkdir -p "$TOOLS_DIR"
ANDROID_HOME="${ANDROID_HOME:-${ANDROID_SDK_ROOT:-$HOME/.android-sdk-jfm}}"
export ANDROID_HOME
export ANDROID_SDK_ROOT="$ANDROID_HOME"
SDKMANAGER=""
for candidate in \
  "$ANDROID_HOME/cmdline-tools/latest/bin/sdkmanager" \
  "$ANDROID_HOME/cmdline-tools/bin/sdkmanager"; do
  if [[ -x "$candidate" ]]; then
    SDKMANAGER="$candidate"
    break
  fi
done

if [[ -z "$SDKMANAGER" ]]; then
  sdk_zip="$TOOLS_DIR/commandlinetools-linux-${ANDROID_CMDLINE_REVISION}_latest.zip"
  if [[ ! -f "$sdk_zip" ]] || ! printf '%s  %s\n' "$ANDROID_CMDLINE_SHA256" "$sdk_zip" | sha256sum --check --strict >/dev/null 2>&1; then
    curl --fail --location --proto '=https' --tlsv1.2 \
      "https://dl.google.com/android/repository/commandlinetools-linux-${ANDROID_CMDLINE_REVISION}_latest.zip" \
      --output "$sdk_zip"
  fi
  printf '%s  %s\n' "$ANDROID_CMDLINE_SHA256" "$sdk_zip" | sha256sum --check --strict
  sdk_extract=$(mktemp -d)
  cleanup_paths+=("$sdk_extract")
  unzip -q "$sdk_zip" -d "$sdk_extract"
  rm -rf "$ANDROID_HOME/cmdline-tools/latest"
  mkdir -p "$ANDROID_HOME/cmdline-tools/latest"
  cp -a "$sdk_extract/cmdline-tools/." "$ANDROID_HOME/cmdline-tools/latest/"
  SDKMANAGER="$ANDROID_HOME/cmdline-tools/latest/bin/sdkmanager"
fi
[[ -x "$SDKMANAGER" ]] || fail "sdkmanager is unavailable."

if [[ ! -f "$ANDROID_HOME/platforms/$ANDROID_PLATFORM/android.jar" || ! -d "$ANDROID_HOME/build-tools/$ANDROID_BUILD_TOOLS" ]]; then
  set +o pipefail
  yes | "$SDKMANAGER" --sdk_root="$ANDROID_HOME" --licenses >/dev/null
  set -o pipefail
  "$SDKMANAGER" --sdk_root="$ANDROID_HOME" \
    "platforms;$ANDROID_PLATFORM" \
    "build-tools;$ANDROID_BUILD_TOOLS"
fi
[[ -f "$ANDROID_HOME/platforms/$ANDROID_PLATFORM/android.jar" ]] || fail "Android platform $ANDROID_PLATFORM is missing."
[[ -d "$ANDROID_HOME/build-tools/$ANDROID_BUILD_TOOLS" ]] || fail "Android build-tools $ANDROID_BUILD_TOOLS are missing."
printf 'Android SDK: %s\n' "$ANDROID_HOME"

step "4/9 — BOOTSTRAP AND VERIFY PROJECT-LOCAL TERMUX AAPT2"
./tools/bootstrap-termux-aapt2.sh bootstrap
AAPT2_PATH=$(./tools/bootstrap-termux-aapt2.sh verify --root "$REPO/.gradle/termux-aapt2")
[[ -x "$AAPT2_PATH" ]] || fail "Verified AAPT2 path is not executable: $AAPT2_PATH"
export PATH="$(dirname "$AAPT2_PATH"):$PATH"
"$AAPT2_PATH" version

step "5/9 — DOWNLOAD, HASH-VERIFY AND SELF-TEST GITLEAKS"
ARTIFACT_DIR="$REPO/.gradle/oss-publication-gates/$HEAD_SHA"
mkdir -p "$ARTIFACT_DIR"
archive="gitleaks_${GITLEAKS_VERSION}_linux_arm64.tar.gz"
archive_path="$TOOLS_DIR/$archive"
gitleaks_bin="$TOOLS_DIR/gitleaks-${GITLEAKS_VERSION}"
if [[ ! -f "$archive_path" ]] || ! printf '%s  %s\n' "$GITLEAKS_ARM64_SHA256" "$archive_path" | sha256sum --check --strict >/dev/null 2>&1; then
  curl --fail --location --proto '=https' --tlsv1.2 \
    "https://github.com/gitleaks/gitleaks/releases/download/v${GITLEAKS_VERSION}/${archive}" \
    --output "$archive_path"
fi
printf '%s  %s\n' "$GITLEAKS_ARM64_SHA256" "$archive_path" | sha256sum --check --strict
extract_dir=$(mktemp -d)
cleanup_paths+=("$extract_dir")
tar --extract --gzip --file "$archive_path" --directory "$extract_dir" gitleaks
install -m 700 "$extract_dir/gitleaks" "$gitleaks_bin"
"$gitleaks_bin" version

canary_dir=$(mktemp -d)
cleanup_paths+=("$canary_dir")
canary_prefix='ghp_'
canary_body='aBcDeFgHiJkLmNoPqRsTuVwXyZ0123456789'
printf 'token = "%s%s"\n' "$canary_prefix" "$canary_body" >"$canary_dir/canary.txt"
set +e
"$gitleaks_bin" dir "$canary_dir" --no-banner --exit-code 86 >/dev/null 2>&1
canary_status=$?
set -e
[[ $canary_status -eq 86 ]] || fail "Gitleaks detector canary failed: expected exit 86, got $canary_status."

step "6/9 — FULL-HISTORY SECRET SCAN ACROSS ALL REFS"
"$gitleaks_bin" git \
  --redact \
  --no-banner \
  --log-opts="--all" \
  --report-format sarif \
  --report-path "$ARTIFACT_DIR/gitleaks.sarif" \
  .
printf 'Gitleaks PASS: %s\n' "$ARTIFACT_DIR/gitleaks.sarif"

step "7/9 — PUBLICATION POLICY AND PYTHON CHECKS"
python tools/test_publication_visual_assets.py
python -m unittest discover -s tools/tests -p 'test_*.py' -v

step "8/9 — ANDROID GRADLE / RESOURCE / ARTIFACT GATES"
./gradlew verifyResolvedCoreVersion verifyPortableAapt2Configuration --no-daemon
./gradlew testDebugUnitTest testQaUnitTest --no-daemon
./gradlew lintDebug lintQa --no-daemon
./gradlew assembleDebug assembleQa assembleQaAndroidTest --no-daemon
scripts/ci/verify-android-artifacts.sh
scripts/ci/verify-release-fail-closed.sh

step "9/9 — FINAL CONSISTENCY AND EVIDENCE"
[[ -z $(git status --porcelain) ]] || {
  git status --short >&2
  fail "Verification changed tracked files."
}
printf '%s\n' "$HEAD_SHA" >"$ARTIFACT_DIR/verified-commit.txt"
sha256sum "$ARTIFACT_DIR/gitleaks.sarif" >"$ARTIFACT_DIR/gitleaks.sarif.sha256"
{
  printf 'candidate_sha=%s\n' "$HEAD_SHA"
  printf 'gitleaks_version=%s\n' "$GITLEAKS_VERSION"
  printf 'android_platform=%s\n' "$ANDROID_PLATFORM"
  printf 'android_build_tools=%s\n' "$ANDROID_BUILD_TOOLS"
  printf 'java_home=%s\n' "${JAVA_HOME:-}"
  printf 'android_home=%s\n' "$ANDROID_HOME"
} >"$ARTIFACT_DIR/environment.txt"
printf 'PASS: all mandatory source-publication execution gates completed on %s\n' "$HEAD_SHA"
printf 'Evidence directory: %s\n' "$ARTIFACT_DIR"

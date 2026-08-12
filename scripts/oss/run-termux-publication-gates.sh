#!/data/data/com.termux/files/usr/bin/bash
set -Eeuo pipefail

BRANCH="oss/publication-readiness-20260811"
REPO="${1:-$PWD}"
GITLEAKS_VERSION="8.29.1"
GITLEAKS_ARM64_SHA256="691f826ce7c1c564c9c02d0f9025e8e70803e3816707a4be6224408a06a81eaa"

fail() {
  printf 'ERROR: %s\n' "$*" >&2
  exit 1
}

step() {
  printf '\n============================================================\n%s\n============================================================\n' "$*"
}

[[ ${PREFIX:-} == */com.termux/files/usr ]] || fail "Run this only inside native Termux."
[[ $(uname -m) == aarch64 ]] || fail "This runner currently supports Termux aarch64 only."
[[ -d "$REPO/.git" ]] || fail "Not a Git worktree: $REPO"
cd "$REPO"

step "1/8 — PREPARE EXACT PUBLICATION BRANCH"
if [[ -n $(git status --porcelain) ]]; then
  git status --short >&2
  fail "Working tree is not clean; publication verification refuses to overwrite local work."
fi
git remote get-url origin | grep -Fq 'zaguzovmaksim0-hue/workspace-47' ||
  fail "Unexpected origin repository."
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

step "2/8 — INSTALL TERMUX BUILD PREREQUISITES"
pkg install -y \
  git curl coreutils findutils gawk tar python openjdk-17 \
  aapt apksigner unzip binutils

if [[ -d "$PREFIX/lib/jvm/java-17-openjdk" ]]; then
  export JAVA_HOME="$PREFIX/lib/jvm/java-17-openjdk"
  export PATH="$JAVA_HOME/bin:$PATH"
fi
java -version
command -v zipalign >/dev/null || fail "zipalign is missing after installing the Termux aapt package."
command -v apksigner >/dev/null || fail "apksigner is missing after installing the Termux apksigner package."
command -v python >/dev/null || fail "python is missing."

step "3/8 — BOOTSTRAP AND VERIFY PROJECT-LOCAL TERMUX AAPT2"
./tools/bootstrap-termux-aapt2.sh bootstrap
AAPT2_PATH=$(./tools/bootstrap-termux-aapt2.sh verify --root "$REPO/.gradle/termux-aapt2")
[[ -x "$AAPT2_PATH" ]] || fail "Verified AAPT2 path is not executable: $AAPT2_PATH"
export PATH="$(dirname "$AAPT2_PATH"):$PATH"
"$AAPT2_PATH" version

step "4/8 — DOWNLOAD, HASH-VERIFY AND SELF-TEST GITLEAKS"
TOOLS_DIR="$REPO/.gradle/oss-publication-tools"
ARTIFACT_DIR="$REPO/.gradle/oss-publication-gates/$HEAD_SHA"
mkdir -p "$TOOLS_DIR" "$ARTIFACT_DIR"
archive="gitleaks_${GITLEAKS_VERSION}_linux_arm64.tar.gz"
archive_path="$TOOLS_DIR/$archive"
gitleaks_bin="$TOOLS_DIR/gitleaks-${GITLEAKS_VERSION}"
if [[ ! -x "$gitleaks_bin" ]]; then
  curl --fail --location --proto '=https' --tlsv1.2 \
    "https://github.com/gitleaks/gitleaks/releases/download/v${GITLEAKS_VERSION}/${archive}" \
    --output "$archive_path"
  printf '%s  %s\n' "$GITLEAKS_ARM64_SHA256" "$archive_path" | sha256sum --check --strict
  extract_dir=$(mktemp -d)
  trap 'rm -rf "$extract_dir"' EXIT
  tar --extract --gzip --file "$archive_path" --directory "$extract_dir" gitleaks
  install -m 700 "$extract_dir/gitleaks" "$gitleaks_bin"
fi
"$gitleaks_bin" version

canary_dir=$(mktemp -d)
canary_prefix='ghp_'
canary_body='aBcDeFgHiJkLmNoPqRsTuVwXyZ0123456789'
printf 'token = "%s%s"\n' "$canary_prefix" "$canary_body" >"$canary_dir/canary.txt"
set +e
"$gitleaks_bin" dir "$canary_dir" --no-banner --exit-code 86 >/dev/null 2>&1
canary_status=$?
set -e
rm -rf "$canary_dir"
[[ $canary_status -eq 86 ]] ||
  fail "Gitleaks detector canary failed: expected exit 86, got $canary_status."

step "5/8 — FULL-HISTORY SECRET SCAN ACROSS ALL REFS"
"$gitleaks_bin" git \
  --redact \
  --no-banner \
  --log-opts="--all" \
  --report-format sarif \
  --report-path "$ARTIFACT_DIR/gitleaks.sarif" \
  .
printf 'Gitleaks PASS: %s\n' "$ARTIFACT_DIR/gitleaks.sarif"

step "6/8 — PUBLICATION POLICY AND PYTHON CHECKS"
python tools/test_publication_visual_assets.py
python -m unittest discover -s tools/tests -p 'test_*.py' -v

step "7/8 — ANDROID GRADLE / RESOURCE / ARTIFACT GATES"
./gradlew verifyResolvedCoreVersion verifyPortableAapt2Configuration --no-daemon
./gradlew testDebugUnitTest testQaUnitTest --no-daemon
./gradlew lintDebug lintQa --no-daemon
./gradlew assembleDebug assembleQa assembleQaAndroidTest --no-daemon
scripts/ci/verify-android-artifacts.sh
scripts/ci/verify-release-fail-closed.sh

step "8/8 — FINAL CONSISTENCY CHECK"
[[ -z $(git status --porcelain) ]] || {
  git status --short >&2
  fail "Verification changed tracked files."
}
printf '%s\n' "$HEAD_SHA" >"$ARTIFACT_DIR/verified-commit.txt"
sha256sum "$ARTIFACT_DIR/gitleaks.sarif" >"$ARTIFACT_DIR/gitleaks.sarif.sha256"
printf 'PASS: all mandatory source-publication execution gates completed on %s\n' "$HEAD_SHA"
printf 'Evidence directory: %s\n' "$ARTIFACT_DIR"

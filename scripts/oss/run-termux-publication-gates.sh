#!/data/data/com.termux/files/usr/bin/bash
set -Eeuo pipefail

BRANCH="oss/publication-readiness-20260811"
PRODUCT_CUTOFF="4bf6afb000dbab8f6f767d8ea05a1a00e2d563cb"
RED_TEST="app/src/test/java/dev/junta/firmamobile/profile/ExtremaduraProfileCatalogBindingTest.kt"
REPO="${1:-$PWD}"
GITLEAKS_VERSION="8.30.1"
GITLEAKS_MODULE="github.com/zricethezav/gitleaks/v8"

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
[[ $(uname -m) == aarch64 ]] || fail "This runner currently supports Termux aarch64 only."
git -C "$REPO" rev-parse --is-inside-work-tree >/dev/null 2>&1 || fail "Not a Git worktree: $REPO"
cd "$REPO"

step "1/8 — PREPARE EXACT PUBLICATION BRANCH"
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
[[ $HEAD_SHA =~ ^[0-9a-f]{40}$ ]] || fail "Unexpected Git SHA: $HEAD_SHA"
git merge-base --is-ancestor "$PRODUCT_CUTOFF" HEAD || fail "Product cutoff is not an ancestor of candidate."
git diff --check
git diff --check "$PRODUCT_CUTOFF"..HEAD
if git cat-file -e HEAD:"$RED_TEST" 2>/dev/null; then
  fail "Interrupted TDD RED test is present in candidate: $RED_TEST"
fi
printf 'Candidate SHA: %s\n' "$HEAD_SHA"

step "2/8 — INSTALL TERMUX-SAFE PREREQUISITES"
pkg update -y
pkg install -y git curl coreutils findutils gawk tar python golang openjdk-17 openjdk-21
for tool in git curl python go sha256sum; do
  command -v "$tool" >/dev/null || fail "$tool is missing."
done
[[ -x "$PREFIX/lib/jvm/java-17-openjdk/bin/java" ]] || fail "Termux OpenJDK 17 is missing."
[[ -x "$PREFIX/lib/jvm/java-21-openjdk/bin/java" ]] || fail "Termux OpenJDK 21 is missing."

step "3/8 — VERIFY TERMUX AAPT2 AND BUILD EXACT GITLEAKS ${GITLEAKS_VERSION}"
if ! ./tools/bootstrap-termux-aapt2.sh verify >/dev/null 2>&1; then
  ./tools/bootstrap-termux-aapt2.sh bootstrap
fi
./tools/bootstrap-termux-aapt2.sh verify

TOOLS_DIR="$REPO/.gradle/oss-publication-tools"
ARTIFACT_DIR="$REPO/.gradle/oss-publication-gates/$HEAD_SHA"
GOBIN_DIR="$TOOLS_DIR/go-bin"
GOCACHE_DIR="$TOOLS_DIR/go-cache"
GOMODCACHE_DIR="$TOOLS_DIR/go-mod"
mkdir -p "$TOOLS_DIR" "$ARTIFACT_DIR" "$GOBIN_DIR" "$GOCACHE_DIR" "$GOMODCACHE_DIR"
rm -f "$GOBIN_DIR/gitleaks"
GOBIN="$GOBIN_DIR" GOCACHE="$GOCACHE_DIR" GOMODCACHE="$GOMODCACHE_DIR" CGO_ENABLED=0 \
  go install -tags gore2regex \
  -ldflags="-s -w -X=${GITLEAKS_MODULE}/version.Version=${GITLEAKS_VERSION}" \
  "${GITLEAKS_MODULE}@v${GITLEAKS_VERSION}"
gitleaks_bin="$TOOLS_DIR/gitleaks-${GITLEAKS_VERSION}-termux"
install -m 700 "$GOBIN_DIR/gitleaks" "$gitleaks_bin"
[[ $("$gitleaks_bin" version) == "$GITLEAKS_VERSION" ]] || fail "Unexpected Gitleaks version."
go version -m "$gitleaks_bin" | tee "$ARTIFACT_DIR/gitleaks-module.txt"
grep -Fq $'mod\tgithub.com/zricethezav/gitleaks/v8\tv8.30.1' "$ARTIFACT_DIR/gitleaks-module.txt" \
  || fail "Gitleaks binary is not built from the exact v8.30.1 official Go module."

canary_dir=$(mktemp -d)
cleanup_paths+=("$canary_dir")
canary_prefix='ghp_'
# Public synthetic detector canary: intentionally high entropy, never a real credential.
canary_body='8s66JRslt6s6KPzyRF6x8AMEgRzVV8s8AcRN'
printf 'token = "%s%s"\n' "$canary_prefix" "$canary_body" >"$canary_dir/canary.txt"
set +e
"$gitleaks_bin" dir "$canary_dir" --no-banner --redact --exit-code 86 >/dev/null 2>&1
canary_status=$?
set -e
[[ $canary_status -eq 86 ]] || fail "Gitleaks detector canary failed: expected exit 86, got $canary_status."
printf 'Gitleaks %s detector canary PASS.\n' "$GITLEAKS_VERSION"

step "4/8 — FULL-HISTORY SECRET SCAN ACROSS ALL REFS"
"$gitleaks_bin" git \
  --redact \
  --no-banner \
  --log-opts="--all" \
  --report-format sarif \
  --report-path "$ARTIFACT_DIR/gitleaks.sarif" \
  .
printf 'Gitleaks PASS: %s\n' "$ARTIFACT_DIR/gitleaks.sarif"

step "5/8 — PUBLICATION VISUAL POLICY AND PYTHON CHECKS"
python tools/test_publication_visual_assets.py | tee "$ARTIFACT_DIR/visual-policy.log"
python -m unittest discover -s tools/tests -p 'test_*.py' -v 2>&1 | tee "$ARTIFACT_DIR/python-tests.log"

step "6/8 — ANDROID / GRADLE IN NATIVE TERMUX"
export JAVA_HOME="$PREFIX/lib/jvm/java-17-openjdk"
export PATH="$JAVA_HOME/bin:$PATH"
./gradlew --version 2>&1 | tee "$ARTIFACT_DIR/gradle-version.log"
./gradlew verifyResolvedCoreVersion verifyPortableAapt2Configuration --no-daemon 2>&1 | tee "$ARTIFACT_DIR/gradle-verify.log"
./gradlew testDebugUnitTest testQaUnitTest --no-daemon 2>&1 | tee "$ARTIFACT_DIR/gradle-unit.log"
./gradlew lintDebug lintQa --no-daemon 2>&1 | tee "$ARTIFACT_DIR/gradle-lint.log"
./gradlew assembleDebug assembleQa assembleQaAndroidTest --no-daemon 2>&1 | tee "$ARTIFACT_DIR/gradle-assemble.log"
./scripts/ci/verify-android-artifacts.sh 2>&1 | tee "$ARTIFACT_DIR/verify-android-artifacts.log"
./scripts/ci/verify-release-fail-closed.sh 2>&1 | tee "$ARTIFACT_DIR/verify-release-fail-closed.log"

step "7/8 — LOCAL GO RELAY SUPPORTING CHECKS"
(
  cd ws024-relay
  go test ./... -count=1
  go vet ./...
  go build -o "$ARTIFACT_DIR/ws024-relay" ./cmd/ws024-relay
) 2>&1 | tee "$ARTIFACT_DIR/go-relay.log"
printf '%s\n' 'NOTE: Go -race is a separate supporting check; native android/arm64 does not provide a race runtime.' | tee "$ARTIFACT_DIR/go-race-note.txt"

step "8/8 — FINAL CONSISTENCY AND EVIDENCE"
[[ -z $(git status --porcelain) ]] || {
  git status --short >&2
  fail "Verification changed tracked files."
}
printf '%s\n' "$HEAD_SHA" >"$ARTIFACT_DIR/verified-commit.txt"
sha256sum "$ARTIFACT_DIR/gitleaks.sarif" >"$ARTIFACT_DIR/gitleaks.sarif.sha256"
{
  printf 'candidate_sha=%s\n' "$HEAD_SHA"
  printf 'product_cutoff=%s\n' "$PRODUCT_CUTOFF"
  printf 'gitleaks_version=%s\n' "$GITLEAKS_VERSION"
  printf 'gitleaks_source=official-go-module\n'
  printf 'android_execution=native-termux\n'
  printf 'gradle_launcher_java=17\n'
  printf 'robolectric_test_worker_java=21\n'
} >"$ARTIFACT_DIR/environment.txt"
printf 'PASS: all mandatory source-publication execution gates completed on %s\n' "$HEAD_SHA"
printf 'Evidence directory: %s\n' "$ARTIFACT_DIR"

#!/data/data/com.termux/files/usr/bin/bash
set -Eeuo pipefail

BRANCH="oss/publication-readiness-20260811"
REPO="${1:-$PWD}"
GITLEAKS_VERSION="8.29.1"
GITLEAKS_ARM64_SHA256="691f826ce7c1c564c9c02d0f9025e8e70803e3816707a4be6224408a06a81eaa"
W47_CLOUD="${W47_CLOUD:-$HOME/bin/w47-cloud}"

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

step "1/7 — PREPARE EXACT PUBLICATION BRANCH"
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
printf 'Candidate SHA: %s\n' "$HEAD_SHA"

step "2/7 — INSTALL TERMUX-SAFE PREREQUISITES"
pkg update -y
pkg install -y git curl coreutils findutils gawk tar python
command -v python >/dev/null || fail "python is missing."
[[ -x "$W47_CLOUD" ]] || fail "Codex Cloud wrapper is missing or not executable: $W47_CLOUD"

step "3/7 — DOWNLOAD, HASH-VERIFY AND SELF-TEST GITLEAKS"
TOOLS_DIR="$REPO/.gradle/oss-publication-tools"
ARTIFACT_DIR="$REPO/.gradle/oss-publication-gates/$HEAD_SHA"
mkdir -p "$TOOLS_DIR" "$ARTIFACT_DIR"
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

step "4/7 — FULL-HISTORY SECRET SCAN ACROSS ALL REFS"
"$gitleaks_bin" git \
  --redact \
  --no-banner \
  --log-opts="--all" \
  --report-format sarif \
  --report-path "$ARTIFACT_DIR/gitleaks.sarif" \
  .
printf 'Gitleaks PASS: %s\n' "$ARTIFACT_DIR/gitleaks.sarif"

step "5/7 — PUBLICATION VISUAL POLICY AND PYTHON CHECKS"
python tools/test_publication_visual_assets.py
python -m unittest discover -s tools/tests -p 'test_*.py' -v

step "6/7 — CANONICAL ANDROID GATE IN CODEX CLOUD ONLY"
CLOUD_LOG="$ARTIFACT_DIR/codex-cloud-full.log"
printf 'Submitting exact candidate %s to workspace-47-android via %s\n' "$HEAD_SHA" "$W47_CLOUD"
"$W47_CLOUD" full --branch "$BRANCH" --sha "$HEAD_SHA" 2>&1 | tee "$CLOUD_LOG"
grep -Fq "$HEAD_SHA" "$CLOUD_LOG" || fail "Cloud evidence does not contain the requested exact SHA."
grep -Eq 'task_e_[0-9a-f]+' "$CLOUD_LOG" || fail "Cloud evidence does not contain a task id."

step "7/7 — FINAL CONSISTENCY AND EVIDENCE"
[[ -z $(git status --porcelain) ]] || {
  git status --short >&2
  fail "Verification changed tracked files."
}
printf '%s\n' "$HEAD_SHA" >"$ARTIFACT_DIR/verified-commit.txt"
sha256sum "$ARTIFACT_DIR/gitleaks.sarif" >"$ARTIFACT_DIR/gitleaks.sarif.sha256"
sha256sum "$CLOUD_LOG" >"$ARTIFACT_DIR/codex-cloud-full.log.sha256"
{
  printf 'candidate_sha=%s\n' "$HEAD_SHA"
  printf 'gitleaks_version=%s\n' "$GITLEAKS_VERSION"
  printf 'android_execution=codex-cloud-only\n'
  printf 'cloud_environment=workspace-47-android\n'
  printf 'cloud_wrapper=%s\n' "$W47_CLOUD"
} >"$ARTIFACT_DIR/environment.txt"
printf 'PASS: all mandatory source-publication execution gates completed on %s\n' "$HEAD_SHA"
printf 'Evidence directory: %s\n' "$ARTIFACT_DIR"

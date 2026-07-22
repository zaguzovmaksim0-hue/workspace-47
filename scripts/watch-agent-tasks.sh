#!/data/data/com.termux/files/usr/bin/bash
set -euo pipefail

readonly TASK_FILE="${TASK_FILE:-/storage/emulated/0/Download/ЗАДАЧИ.md}"
readonly INTERVAL_SECONDS="${INTERVAL_SECONDS:-600}"

case "$INTERVAL_SECONDS" in
  ''|*[!0-9]*) echo "INTERVAL_SECONDS must be a positive integer" >&2; exit 64 ;;
esac
(( INTERVAL_SECONDS > 0 )) || { echo "INTERVAL_SECONDS must be positive" >&2; exit 64; }

while :; do
  if [[ -f "$TASK_FILE" ]]; then
    hash="$(sha256sum "$TASK_FILE" | awk '{print $1}')"
    modified="$(stat -c '%Y' "$TASK_FILE")"
    lines="$(wc -l < "$TASK_FILE")"
    checked_at="$(date -u +%Y-%m-%dT%H:%M:%SZ)"
    printf '{"checkedAt":"%s","exists":true,"sha256":"%s","mtime":%s,"lines":%s}\n' \
      "$checked_at" "$hash" "$modified" "$lines"
  else
    checked_at="$(date -u +%Y-%m-%dT%H:%M:%SZ)"
    printf '{"checkedAt":"%s","exists":false}\n' "$checked_at"
  fi
  sleep "$INTERVAL_SECONDS"
done

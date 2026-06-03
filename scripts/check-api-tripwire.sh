#!/usr/bin/env bash
set -uo pipefail
cd "$(git rev-parse --show-toplevel)" || exit 2
BASE="${1:-origin/master}"
changed="$(git diff --name-only "$BASE...HEAD" -- '*.api' 2>/dev/null || true)"
[ -z "$changed" ] && changed="$(git diff --name-only "$BASE" -- '*.api' 2>/dev/null || true)"
if [ -z "$changed" ]; then echo "api-tripwire: no .api changes vs $BASE ✓"; exit 0; fi
echo "api-tripwire: public API changed — knowledge review required."
printf '%s\n' "$changed" | sed 's#/api/.*##' | sort -u | sed 's/^/  module: /'
echo "Action: update docs/agent/api-map.md if the surface summary changed, then add PR label 'api-reviewed' to acknowledge."
[ "${API_REVIEW_OK:-0}" = "1" ] && { echo "api-tripwire: acknowledged (api-reviewed) — passing."; exit 0; }
exit 1

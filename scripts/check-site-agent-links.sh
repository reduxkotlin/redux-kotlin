#!/usr/bin/env bash
set -uo pipefail
cd "$(git rev-parse --show-toplevel)" || exit 2
fail=0
# Files whose GitHub blob/tree links to in-repo docs we must keep alive.
files=("docs/agent/AGENTS-external.md" "website/docs/ai-agents/BuildingWithAI.md")
for f in "${files[@]}"; do
  [ -f "$f" ] || { echo "LINK-FAIL: missing $f"; fail=1; continue; }
  # Extract repo-relative paths from github.com/reduxkotlin/redux-kotlin/(blob|tree)/<ref>/<path>
  while IFS= read -r p; do
    [ -n "$p" ] || continue
    [ -e "$p" ] || { echo "LINK-FAIL: $f links missing repo path: $p"; fail=1; }
  done < <(grep -oE 'github\.com/reduxkotlin/redux-kotlin/(blob|tree)/[^/]+/[^)" ]+' "$f" \
            | sed -E 's#.*/(blob|tree)/[^/]+/##' | sort -u)
done
[ "$fail" -ne 0 ] && { echo "SITE-LINK CHECK FAILED"; exit 1; }
echo "SITE-LINK CHECK OK"

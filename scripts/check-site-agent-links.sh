#!/usr/bin/env bash
set -uo pipefail
cd "$(git rev-parse --show-toplevel)" || exit 2
fail=0
# Files whose GitHub blob/tree links to in-repo docs we must keep alive.
files=(
  "docs/agent/AGENTS-external.md"
  "docs/agent/SKILL-external.md"
  "website/docs/ai-agents/BuildingWithAI.md"
  "website/static/agent-setup/AGENTS.md"
  "website/static/agent-setup/SKILL.md"
  "website/static/agent-setup/prompt.md"
)
for f in "${files[@]}"; do
  [ -f "$f" ] || { echo "LINK-FAIL: missing $f"; fail=1; continue; }
  # Extract repo-relative paths from github.com/reduxkotlin/redux-kotlin/(blob|tree)/<ref>/<path>
  while IFS= read -r p; do
    [ -n "$p" ] || continue
    [ -e "$p" ] || { echo "LINK-FAIL: $f links missing repo path: $p"; fail=1; }
  done < <(grep -oE 'github\.com/reduxkotlin/redux-kotlin/(blob|tree)/[^/]+/[^)" ]+' "$f" \
            | sed -E 's#.*/(blob|tree)/[^/]+/##' | sort -u)
done

setup_url="https://reduxkotlin.org/agent-setup/prompt.md"
banner="website/src/components/AgentOnboardingBanner/index.tsx"
grep -qF "$setup_url" "$banner" || {
  echo "LINK-FAIL: onboarding banner does not copy the canonical setup URL"; fail=1;
}
grep -qF "https://reduxkotlin.org/agent-setup/AGENTS.md" "website/static/agent-setup/prompt.md" || {
  echo "LINK-FAIL: setup prompt does not reference the published AGENTS.md"; fail=1;
}
grep -qF "https://reduxkotlin.org/agent-setup/SKILL.md" "website/static/agent-setup/prompt.md" || {
  echo "LINK-FAIL: setup prompt does not reference the published SKILL.md"; fail=1;
}

[ "$fail" -ne 0 ] && { echo "SITE-LINK CHECK FAILED"; exit 1; }
echo "SITE-LINK CHECK OK"

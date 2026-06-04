#!/usr/bin/env bash
set -uo pipefail
cd "$(git rev-parse --show-toplevel)" || exit 2
CHECK=0; [ "${1:-}" = "--check" ] && CHECK=1
FRAG="docs/agent/_fragments"
MAP=(
  "AGENTS.md|rules|$FRAG/rules.md"
  ".claude/skills/redux-kotlin/SKILL.md|rules|$FRAG/rules.md"
  "AGENTS.md|modules|$FRAG/modules.md"
  "docs/agent/AGENTS-external.md|rules|$FRAG/rules.md"
  "docs/agent/AGENTS-external.md|modules|$FRAG/modules.md"
)
inject() { # stdin=content, $1=marker, $2=fragfile -> stdout
  awk -v s="<!-- assemble:$1:start -->" -v e="<!-- assemble:$1:end -->" -v f="$2" '
    $0==s { print; while ((getline l < f) > 0) print l; close(f); skip=1; next }
    $0==e { skip=0 }
    !skip { print }
  '
}
targets=$(printf '%s\n' "${MAP[@]}" | cut -d'|' -f1 | sort -u)
rc=0
for t in $targets; do
  [ -f "$t" ] || { echo "ASSEMBLE-FAIL: missing target $t"; rc=1; continue; }
  rendered="$(cat "$t")"
  for entry in "${MAP[@]}"; do
    IFS='|' read -r mt mk mf <<< "$entry"
    [ "$mt" = "$t" ] || continue
    [ -f "$mf" ] || { echo "ASSEMBLE-FAIL: missing fragment $mf"; rc=1; continue; }
    grep -qF "<!-- assemble:$mk:start -->" "$t" || { echo "ASSEMBLE-FAIL: $t missing marker '$mk'"; rc=1; continue; }
    rendered="$(printf '%s\n' "$rendered" | inject "$mk" "$mf")"
  done
  if [ "$CHECK" -eq 1 ]; then
    if ! diff -q <(printf '%s\n' "$rendered") "$t" >/dev/null; then
      echo "ASSEMBLE-FAIL: $t out of sync with fragment(s) — run scripts/assemble-agent-knowledge.sh"; rc=1
    fi
  else
    printf '%s\n' "$rendered" > "$t"
  fi
done
[ "$rc" -eq 0 ] && { [ "$CHECK" -eq 1 ] && echo "ASSEMBLE CHECK OK" || echo "ASSEMBLED"; } || echo "ASSEMBLE FAILED"
exit "$rc"

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
# --- Site copy-block: fence AGENTS-external.md into the Docusaurus (MDX) page ---
PAGE="website/docs/ai-agents/BuildingWithAI.md"
SRC="docs/agent/AGENTS-external.md"
PAGE_START='{/* assemble:agents-external:start */}'
PAGE_END='{/* assemble:agents-external:end */}'
if [ -f "$PAGE" ] && [ -f "$SRC" ]; then
  # 4-backtick fence so the AGENTS content's own 3-backtick ```kotlin block nests cleanly.
  # Strip the inner `<!-- assemble:* -->` marker lines so the PUBLISHED copy-block is clean
  # (the canonical AGENTS-external.md keeps its markers; only the page copy drops them).
  fenced="$(grep -v '<!-- assemble:' "$SRC")"
  block="$(
    printf '%s\n' "$PAGE_START"
    printf '````markdown title="AGENTS.md"\n'
    printf '%s\n' "$fenced"
    printf '````\n'
    printf '%s\n' "$PAGE_END"
  )"
  rendered_page="$(ASSEMBLE_BLOCK="$block" ASSEMBLE_START="$PAGE_START" ASSEMBLE_END="$PAGE_END" \
    awk 'BEGIN{ s=ENVIRON["ASSEMBLE_START"]; e=ENVIRON["ASSEMBLE_END"]; repl=ENVIRON["ASSEMBLE_BLOCK"] }
    $0==s { print repl; skip=1; next }
    $0==e { skip=0; next }
    !skip { print }
  ' "$PAGE")"
  if [ "$CHECK" -eq 1 ]; then
    if ! diff -q <(printf '%s\n' "$rendered_page") "$PAGE" >/dev/null; then
      echo "ASSEMBLE-FAIL: $PAGE copy-block out of sync — run scripts/assemble-agent-knowledge.sh"; rc=1
    fi
  else
    printf '%s\n' "$rendered_page" > "$PAGE"
  fi
fi
[ "$rc" -eq 0 ] && { [ "$CHECK" -eq 1 ] && echo "ASSEMBLE CHECK OK" || echo "ASSEMBLED"; } || echo "ASSEMBLE FAILED"
exit "$rc"

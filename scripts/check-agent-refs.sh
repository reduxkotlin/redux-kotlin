#!/usr/bin/env bash
set -uo pipefail
cd "$(git rev-parse --show-toplevel)" || exit 2
fail=0
note() { printf 'ANCHOR-FAIL: %s\n' "$*"; fail=1; }
ALLOWED=" build detekt detektAll apiCheck apiDump allTests jvmTest test assemble assembleDebug installDist snapshotUi "
files=(AGENTS.md docs/agent/api-map.md)
while IFS= read -r f; do files+=("$f"); done < <(find docs/agent/references -name '*.md' ! -name '_template.md' 2>/dev/null | sort)
for F in "${files[@]}"; do
  [ -f "$F" ] || { note "missing doc: $F"; continue; }
  while IFS= read -r line; do
    case "$line" in *'(planned'*) continue ;; esac
    while IFS= read -r a; do
      a="${a#\`}"; a="${a%\`}"; p="${a%% → *}"; syms="${a##* → }"
      # Only treat as a source anchor if the path looks like a real repo path
      # (contains a '/'); skip convention/example placeholders.
      case "$p" in */*) ;; *) continue ;; esac
      if [ ! -e "$p" ]; then note "$F: path not found: $p"; continue; fi
      IFS=',' read -ra arr <<< "$syms"
      for s in "${arr[@]}"; do s="$(printf '%s' "$s" | xargs)"; [ -n "$s" ] || continue
        grep -rqF -- "$s" "$p" || note "$F: symbol '$s' not found in $p"; done
    done < <(printf '%s\n' "$line" | grep -oE '`[^`]+ → [^`]+`' || true)
    while IFS= read -r m; do grep -qF "\"$m\"" settings.gradle.kts || note "$F: module $m not in settings.gradle.kts"
    done < <(printf '%s\n' "$line" | grep -oE ':redux-kotlin[a-z-]*' | sort -u || true)
    while IFS= read -r t; do t="${t##*:}"; [ -n "$t" ] || continue; case "$ALLOWED" in *" $t "*) ;; *) note "$F: gradle task '$t' not in allowlist" ;; esac
    done < <(printf '%s\n' "$line" | grep -oE '\./gradlew [:a-zA-Z-]+' | sed 's#\./gradlew ##' || true)
  done < "$F"
  while IFS= read -r p; do p="$(printf '%s' "$p" | xargs)"; [ -n "$p" ] || continue
    [ -e "$p" ] || note "$F: api_file not found: $p"
  done < <(awk '/^api_files:/{f=1;next} /^[a-zA-Z_]+:/{f=0} f && /^[[:space:]]*-/{sub(/^[[:space:]]*-[[:space:]]*/,"");print}' "$F")
done
[ "$fail" -ne 0 ] && { echo "ANCHOR CHECK FAILED"; exit 1; }
echo "ANCHOR CHECK OK"

# Single-Source Assembler — Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: superpowers:subagent-driven-development. Steps use `- [ ]`.

**Goal:** Single-source the duplicated Rules C–H + module map into `docs/agent/_fragments/`, assemble them into `AGENTS.md`/`SKILL.md` via marker injection, and gate drift in CI — implementing the strategy's "author once, assemble" and fixing the existing Rule-C drift.

**Architecture:** Two fragment files + a bash assembler with `--check` + marker regions in the two consumer docs + a new `assemble-check.yml` workflow. No library/source changes. TDD: positive (assemble is idempotent; `--check` clean) + negative (diverge → `--check` fails).

**Tech Stack:** bash + awk, GitHub Actions YAML, markdown.

---

## Task 1: Author the fragments

**Files:** Create `docs/agent/_fragments/rules.md`, `docs/agent/_fragments/modules.md`

- [ ] **Step 1:** `docs/agent/_fragments/rules.md` — canonical Rules C–H (faithful to `examples/taskflow/ARCHITECTURE.md` §17; **Rule C includes `key(...)`**). Bullets only, no header (drops into each doc's existing section):
```markdown
- **Rule C — Render isolation.** No composable reads a model (board/cards/columns) wholesale; every leaf binds the narrowest slice via `selectorState`/`fieldStateOf` and is wrapped in `key(...)`; list derivation lives in pure functions/reducers.
- **Rule D — Identity split.** A profile edit fans `EditProfile` to the root account directory, the per-account `CollaboratorsModel`, and `SessionModel` (bio) — identity is never duplicated inconsistently.
- **Rule E — Off-main effects.** Effects originate only in middleware and run off-main; dispatch marshals back to main via `NotificationContext` (no explicit main hop). Per-feature handlers compose into one `effectsMiddleware`.
- **Rule F — Delta-only status.** `SyncEngine` emits `onStatus` only on a real `SyncStatus` change.
- **Rule G — Mint at the edge.** Ids and timestamps come from `LocalIdGenerator`/`LocalClock` at the dispatch site, never from a reducer.
- **Rule H — Single inset point.** Window insets are applied once at the shell root.
```
- [ ] **Step 2:** `docs/agent/_fragments/modules.md` — canonical 9-core-module map (bullets only):
```markdown
- `redux-kotlin` — core contract: `Store`/`TypedStore`, `Reducer`, `Middleware`, `createStore`, `applyMiddleware`, `combineReducers`, `compose`.
- `redux-kotlin-threadsafe` — `createThreadSafeStore` (atomicfu-locked store wrapper).
- `redux-kotlin-concurrent` — `createConcurrentStore` (lock-free reads + reentrant-lock-serialized writes; the CallerSerialized strategy).
- `redux-kotlin-granular` — `subscribeTo` / `subscribeFields` field-level subscriptions.
- `redux-kotlin-registry` — `StoreRegistry` / `TypedStoreRegistry` keyed multi-store container.
- `redux-kotlin-multimodel` — `ModelState` typesafe heterogeneous model bag.
- `redux-kotlin-multimodel-granular` — granular subscriptions for `ModelState`.
- `redux-kotlin-compose` — Compose `State<T>` bindings (`fieldState`, `selectorState`, `StableStore`).
- `redux-kotlin-compose-multimodel` — Compose bindings for `ModelState`.
```
- [ ] **Step 3:** Commit: `git add docs/agent/_fragments && git commit -m "docs(agent): add single-source fragments (rules, module map)"`

---

## Task 2: The assembler script

**Files:** Create `scripts/assemble-agent-knowledge.sh` (chmod +x)

- [ ] **Step 1:** Write:
```bash
#!/usr/bin/env bash
# Single-source assembler: inject docs/agent/_fragments/* into marker regions of the agent surfaces.
# Default: rewrite in place. --check: fail (non-zero) if any region is out of sync with its fragment.
set -uo pipefail
cd "$(git rev-parse --show-toplevel)" || exit 2
CHECK=0; [ "${1:-}" = "--check" ] && CHECK=1
FRAG="docs/agent/_fragments"
# target|marker|fragment
MAP=(
  "AGENTS.md|rules|$FRAG/rules.md"
  ".claude/skills/redux-kotlin/SKILL.md|rules|$FRAG/rules.md"
  "AGENTS.md|modules|$FRAG/modules.md"
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
      echo "ASSEMBLE-FAIL: $t is out of sync with its fragment(s) — run scripts/assemble-agent-knowledge.sh"; rc=1
    fi
  else
    printf '%s\n' "$rendered" > "$t"
  fi
done
[ "$rc" -eq 0 ] && { [ "$CHECK" -eq 1 ] && echo "ASSEMBLE CHECK OK" || echo "ASSEMBLED"; } || echo "ASSEMBLE FAILED"
exit "$rc"
```
- [ ] **Step 2:** `chmod +x scripts/assemble-agent-knowledge.sh`
- [ ] **Step 3:** Commit: `git add scripts/assemble-agent-knowledge.sh && git commit -m "build(agent): add single-source assembler (--check)"`

---

## Task 3: Retrofit AGENTS.md + SKILL.md with markers

**Files:** Modify `AGENTS.md`, `.claude/skills/redux-kotlin/SKILL.md`

- [ ] **Step 1:** In `AGENTS.md`, replace the **Design rules** bullet list (the `- **Rule C …**` … `- **Rule H …**` lines under `## Design rules`) with marker lines:
```
<!-- assemble:rules:start -->
<!-- assemble:rules:end -->
```
Keep the surrounding `## Design rules` heading and any pointer line ("full text → …ARCHITECTURE.md §17").
- [ ] **Step 2:** In `AGENTS.md`, replace the **Module map** bullet list (the 9 `- \`redux-kotlin…\`` lines under `## Module map`) with:
```
<!-- assemble:modules:start -->
<!-- assemble:modules:end -->
```
Keep the heading + any "more modules exist → api-map" pointer line.
- [ ] **Step 3:** In `SKILL.md`, replace the **Always-apply rules** bullet list (the `- **Rule C …**`…`- **Rule H …**` lines) with:
```
<!-- assemble:rules:start -->
<!-- assemble:rules:end -->
```
Keep the `## Always-apply rules (full text → …)` heading.
- [ ] **Step 4:** Run the assembler to fill the regions: `bash scripts/assemble-agent-knowledge.sh` → prints `ASSEMBLED`. Then `git diff` to confirm the regions now hold the fragment content (and AGENTS.md Rule C now includes `key(...)`, fixing the drift).
- [ ] **Step 5 (idempotency + anchor):** `bash scripts/assemble-agent-knowledge.sh` again → no further diff; `bash scripts/assemble-agent-knowledge.sh --check` → `ASSEMBLE CHECK OK`; `bash scripts/check-agent-refs.sh` → `ANCHOR CHECK OK`; `wc -l AGENTS.md` still tight (≤ ~180).
- [ ] **Step 6:** Commit: `git add AGENTS.md .claude/skills/redux-kotlin/SKILL.md && git commit -m "docs(agent): assemble rules + module map from fragments (single-source)"`

---

## Task 4: CI check + template note

**Files:** Create `.github/workflows/assemble-check.yml`; modify `docs/agent/references/_template.md`

- [ ] **Step 1:** Write `.github/workflows/assemble-check.yml`:
```yaml
name: Assemble check
on:
  pull_request:
    branches: [master]
permissions:
  contents: read
concurrency:
  cancel-in-progress: true
  group: assemble-check-${{ github.workflow }}-${{ github.head_ref || github.ref }}
jobs:
  assemble-check:
    name: Agent knowledge is assembled from fragments
    runs-on: ubuntu-latest
    timeout-minutes: 5
    steps:
      - uses: actions/checkout@v6
      - name: Verify assembled regions match fragments
        run: bash scripts/assemble-agent-knowledge.sh --check
```
- [ ] **Step 2:** Validate: `python3 -c "import yaml; yaml.safe_load(open('.github/workflows/assemble-check.yml')); print('yaml ok')"`.
- [ ] **Step 3:** In `docs/agent/references/_template.md`, add one line (near the anchor-rules note): "Shared blocks (Rules C–H, module map) are single-sourced in `docs/agent/_fragments/` and injected into `AGENTS.md`/`SKILL.md` by `scripts/assemble-agent-knowledge.sh` — edit the fragment, then re-run the assembler (CI `assemble-check` enforces it)."
- [ ] **Step 4:** Commit: `git add .github/workflows/assemble-check.yml docs/agent/references/_template.md && git commit -m "ci(agent): enforce assembled-from-fragments + document it"`

---

## Task 5: Acceptance (TDD negative test) + PR

- [ ] **Step 1 (positive):** `bash scripts/assemble-agent-knowledge.sh --check` → `ASSEMBLE CHECK OK` (exit 0); `bash scripts/check-agent-refs.sh` → `ANCHOR CHECK OK`.
- [ ] **Step 2 (negative):** prove `--check` catches drift:
```bash
printf '\n- **Rule Z — bogus.**\n' >> docs/agent/_fragments/rules.md
bash scripts/assemble-agent-knowledge.sh --check; echo "exit=$?"   # expect ASSEMBLE-FAIL (AGENTS.md + SKILL.md) + exit=1
git checkout -- docs/agent/_fragments/rules.md
bash scripts/assemble-agent-knowledge.sh --check                   # expect ASSEMBLE CHECK OK
```
Also confirm editing a generated region trips it:
```bash
sed -i.bak 's/Rule H — Single inset point/Rule H — TAMPERED/' AGENTS.md && rm -f AGENTS.md.bak
bash scripts/assemble-agent-knowledge.sh --check; echo "exit=$?"   # expect fail
bash scripts/assemble-agent-knowledge.sh                           # re-sync
bash scripts/assemble-agent-knowledge.sh --check                   # OK
git diff --stat
```
- [ ] **Step 3:** Verify drift fixed: `grep -c 'key(' AGENTS.md` ≥ 1 (Rule C now has `key(...)`); the rules region in AGENTS.md and SKILL.md are byte-identical (extract between markers and `diff`).
- [ ] **Step 4:** No-collateral: `git diff --name-only knowledge-sync-phase4...HEAD` → only `docs/agent/_fragments/*`, `scripts/assemble-agent-knowledge.sh`, `AGENTS.md`, `.claude/skills/redux-kotlin/SKILL.md`, `.github/workflows/assemble-check.yml`, `docs/agent/references/_template.md`, `docs/superpowers/`. No `.kt`/`website/`/`examples/`/`agent-knowledge.yml`.
- [ ] **Step 5:** `git push -u origin single-source-assembler`
- [ ] **Step 6:** `gh pr create --base knowledge-sync-phase4 --head single-source-assembler` — title `build(agent): single-source assembler for shared knowledge blocks`; body: implements §5 "author once, assemble", resolves Q6, fixes the Rule-C `key(...)` drift, the `--check` CI gate + negative test, stacked merge-order note, link the spec.

---

## Self-review notes
- Spec coverage: fragments (T1), assembler (T2), retrofit (T3), CI+template (T4), acceptance+PR (T5).
- Separate `assemble-check.yml` (not `agent-knowledge.yml`) → no collision with the #7 fix on that file.
- Idempotent injection; `--check` is the drift gate; negative tests prove it fires for both fragment edits and region tampering.
- Scope limited to rules + module map (the always-loaded duplicates); api-map retrofit documented as future.
- Anchor checker still passes (fragments live in `_fragments/`, not scanned; injected text has no `path → symbol` anchors).

# Phase 3 — L4 Deterministic CI Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: superpowers:subagent-driven-development. Steps use `- [ ]`.

**Goal:** Two deterministic, locally-runnable shell checks + one GitHub Actions workflow: an **anchor check** (refs cite real paths/symbols/modules/tasks) and an **API-change tripwire** (`*.api` change → knowledge review required).

**Architecture:** `scripts/*.sh` (detekt-excluded, bash) + `.github/workflows/agent-knowledge.yml` (`pull_request: [master]`, mirrors `pr.yml`). No library source changes. TDD: each script has a positive (exit 0 on good state) and negative (exit non-zero on injected breakage) test the implementer MUST run.

**Tech Stack:** bash, GitHub Actions YAML.

---

## Task 1: `scripts/check-agent-refs.sh` (anchor check)

**Files:** Create `scripts/check-agent-refs.sh` (chmod +x)

Behavior: scan `AGENTS.md`, `docs/agent/api-map.md`, and `docs/agent/references/*.md` (EXCLUDE `_template.md`). Skip any line containing `(planned`. Verify, collecting all failures then exiting non-zero if any:
- backtick anchors `` `path → Sym[, Sym]` `` → path exists AND each Sym appears in path (`grep -rqF`).
- module tokens matching `:redux-kotlin[a-z-]*` → present in `settings.gradle.kts`.
- `./gradlew <task>` → `<task>` (after stripping any `:module:` prefix) ∈ allowlist `build detekt detektAll apiCheck apiDump allTests jvmTest test assemble assembleDebug`.
- YAML `api_files:` list entries → each path exists.
SKILL.md is intentionally NOT scanned (it routes to planned guides).

- [ ] **Step 1:** Write the script (reference implementation; harden as needed):
```bash
#!/usr/bin/env bash
# L4 anchor check: verify the agent reference set cites real paths/symbols/modules/tasks.
set -uo pipefail
cd "$(git rev-parse --show-toplevel)" || exit 2
fail=0
note() { printf 'ANCHOR-FAIL: %s\n' "$*"; fail=1; }
ALLOWED=" build detekt detektAll apiCheck apiDump allTests jvmTest test assemble assembleDebug "

files=(AGENTS.md docs/agent/api-map.md)
while IFS= read -r f; do files+=("$f"); done < <(find docs/agent/references -name '*.md' ! -name '_template.md' 2>/dev/null | sort)

for F in "${files[@]}"; do
  [ -f "$F" ] || { note "missing doc: $F"; continue; }
  # --- line-scoped checks (skip planned lines) ---
  while IFS= read -r line; do
    case "$line" in *'(planned'*) continue ;; esac
    # path → symbol anchors
    while IFS= read -r a; do
      a="${a#\`}"; a="${a%\`}"
      p="${a%% → *}"; syms="${a##* → }"
      if [ ! -e "$p" ]; then note "$F: path not found: $p"; continue; fi
      IFS=',' read -ra arr <<< "$syms"
      for s in "${arr[@]}"; do s="$(printf '%s' "$s" | xargs)"; [ -n "$s" ] || continue
        grep -rqF -- "$s" "$p" || note "$F: symbol '$s' not found in $p"
      done
    done < <(printf '%s\n' "$line" | grep -oE '`[^`]+ → [^`]+`' || true)
    # module tokens
    while IFS= read -r m; do
      grep -qF "\"$m\"" settings.gradle.kts || note "$F: module $m not in settings.gradle.kts"
    done < <(printf '%s\n' "$line" | grep -oE ':redux-kotlin[a-z-]*' | sort -u || true)
    # gradle tasks
    while IFS= read -r t; do
      t="${t##*:}"; case "$ALLOWED" in *" $t "*) ;; *) note "$F: gradle task '$t' not in allowlist" ;; esac
    done < <(printf '%s\n' "$line" | grep -oE '\./gradlew [:a-zA-Z-]+' | sed 's#\./gradlew ##' || true)
  done < "$F"
  # --- frontmatter api_files paths ---
  while IFS= read -r p; do
    p="$(printf '%s' "$p" | xargs)"; [ -n "$p" ] || continue
    [ -e "$p" ] || note "$F: api_file not found: $p"
  done < <(awk '/^api_files:/{f=1;next} /^[a-zA-Z_]+:/{f=0} f && /^[[:space:]]*-/{sub(/^[[:space:]]*-[[:space:]]*/,"");print}' "$F")
done

if [ "$fail" -ne 0 ]; then echo "ANCHOR CHECK FAILED"; exit 1; fi
echo "ANCHOR CHECK OK"
```
- [ ] **Step 2:** `chmod +x scripts/check-agent-refs.sh`
- [ ] **Step 3 (positive test):** `bash scripts/check-agent-refs.sh` → prints `ANCHOR CHECK OK`, exit 0. If any ANCHOR-FAIL, the docs are right and the script is wrong — fix the script (do NOT weaken a real check).
- [ ] **Step 4 (negative test):** append a bogus anchor to a scratch copy and confirm it fails:
```bash
cp docs/agent/references/feature-slice.md /tmp/fs.bak
printf '\n`docs/agent/does-not-exist.md → Ghost`\n' >> docs/agent/references/feature-slice.md
bash scripts/check-agent-refs.sh; echo "exit=$?"   # expect ANCHOR-FAIL ... + exit=1
mv /tmp/fs.bak docs/agent/references/feature-slice.md
bash scripts/check-agent-refs.sh && echo "restored OK"
```
Expected: the injected line fails (exit 1), then restored passes (exit 0).
- [ ] **Step 5:** Commit: `git add scripts/check-agent-refs.sh && git commit -m "build(agent): add L4 anchor-check script"`

---

## Task 2: `scripts/check-api-tripwire.sh`

**Files:** Create `scripts/check-api-tripwire.sh` (chmod +x)

- [ ] **Step 1:** Write:
```bash
#!/usr/bin/env bash
# L4 API-change tripwire: flag committed *.api changes so knowledge gets re-reviewed.
set -uo pipefail
cd "$(git rev-parse --show-toplevel)" || exit 2
BASE="${1:-origin/master}"
changed="$(git diff --name-only "$BASE...HEAD" -- '*.api' 2>/dev/null || true)"
[ -z "$changed" ] && changed="$(git diff --name-only "$BASE" -- '*.api' 2>/dev/null || true)"
if [ -z "$changed" ]; then echo "api-tripwire: no .api changes vs $BASE ✓"; exit 0; fi
echo "api-tripwire: public API changed — knowledge review required."
printf '%s\n' "$changed" | sed 's#/api/.*##' | sort -u | sed 's/^/  module: /'
echo "Action: update docs/agent/api-map.md if the surface summary changed, then add PR label 'api-reviewed' to acknowledge."
if [ "${API_REVIEW_OK:-0}" = "1" ]; then echo "api-tripwire: acknowledged (api-reviewed) — passing."; exit 0; fi
exit 1
```
- [ ] **Step 2:** `chmod +x scripts/check-api-tripwire.sh`
- [ ] **Step 3 (positive test):** `bash scripts/check-api-tripwire.sh origin/master; echo exit=$?` → `no .api changes` + exit 0 (this stack is docs/CI only). If `origin/master` is unfetched, run `git fetch origin master` first.
- [ ] **Step 4 (negative test):**
```bash
echo "// scratch" >> redux-kotlin/api/redux-kotlin.klib.api
bash scripts/check-api-tripwire.sh origin/master; echo "exit=$?"          # expect tripwire + exit=1
API_REVIEW_OK=1 bash scripts/check-api-tripwire.sh origin/master; echo "exit=$?"  # expect acknowledged + exit=0
git checkout -- redux-kotlin/api/redux-kotlin.klib.api
```
Expected: trips (exit 1), bypass passes (exit 0), then reverted.
- [ ] **Step 5:** Commit: `git add scripts/check-api-tripwire.sh && git commit -m "build(agent): add L4 API-change tripwire script"`

---

## Task 3: `.github/workflows/agent-knowledge.yml`

**Files:** Create `.github/workflows/agent-knowledge.yml`

- [ ] **Step 1:** Write:
```yaml
name: Agent knowledge
on:
  pull_request:
    branches: [master]
permissions:
  contents: read
concurrency:
  cancel-in-progress: true
  group: agent-knowledge-${{ github.workflow }}-${{ github.head_ref || github.ref }}
jobs:
  anchor-check:
    name: Anchor check (refs resolve)
    runs-on: ubuntu-latest
    timeout-minutes: 5
    steps:
      - uses: actions/checkout@v6
      - name: Check agent reference anchors
        run: bash scripts/check-agent-refs.sh
  api-tripwire:
    name: API-change tripwire
    runs-on: ubuntu-latest
    timeout-minutes: 5
    steps:
      - uses: actions/checkout@v6
        with:
          fetch-depth: 0
      - name: Tripwire on committed .api changes
        env:
          API_REVIEW_OK: ${{ contains(github.event.pull_request.labels.*.name, 'api-reviewed') && '1' || '0' }}
        run: |
          git fetch origin "${{ github.base_ref }}" --depth=1 || true
          bash scripts/check-api-tripwire.sh "origin/${{ github.base_ref }}"
```
- [ ] **Step 2:** Validate YAML: `actionlint .github/workflows/agent-knowledge.yml` if available, else `python3 -c "import yaml,sys; yaml.safe_load(open('.github/workflows/agent-knowledge.yml')); print('yaml ok')"`. Expected: clean / `yaml ok`.
- [ ] **Step 3:** Commit: `git add .github/workflows/agent-knowledge.yml && git commit -m "ci(agent): add agent-knowledge workflow (anchor check + API tripwire)"`

---

## Task 4: Acceptance + PR

- [ ] **Step 1:** Re-run both scripts clean (`scripts/check-agent-refs.sh` → OK; `check-api-tripwire.sh origin/master` → no changes). 
- [ ] **Step 2:** No-collateral: `git diff --name-only claude-skill-phase2...HEAD` → only `scripts/`, `.github/workflows/agent-knowledge.yml`, `docs/superpowers/`. No `.kt`/`website/`/`examples/`.
- [ ] **Step 3:** `git push -u origin l4-ci-phase3`
- [ ] **Step 4:** `gh pr create --base claude-skill-phase2 --head l4-ci-phase3` — title `ci(agent): L4 deterministic checks — anchor + API tripwire (Phase 3)`; body: the two scripts + workflow, positive/negative test results, the `api-reviewed` bypass-label mechanism, stacked merge-order note, link the spec.

---

## Self-review notes
- Spec coverage: anchor script (T1), tripwire script (T2), workflow (T3), acceptance+PR (T4).
- Determinism: no gradle invocation; static task allowlist; pure git/grep.
- Planned guides skipped (line contains `(planned`); `_template.md` excluded; SKILL.md not scanned.
- Both scripts are locally runnable and TDD-tested (positive + negative) before commit.

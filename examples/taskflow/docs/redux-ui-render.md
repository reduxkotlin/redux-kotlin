# `redux-ui render` — headless screen rendering for AI agents

A spike that turns a TaskFlow Compose screen into a PNG from **seeded Redux state
only** — no Android, no emulator, no SqlDelight, no network. Because every screen
is a pure function of `ModelState`, an agent can synthesize a state, render it,
look at the pixels, and iterate in a tight loop.

```
f(State) → Pixels        # deterministic, headless, ~50ms warm
```

This document shows the commands, an example agent session, and the actual output
images.

---

## Quick start

The renderer is wired as a Gradle `JavaExec` task on the TaskFlow sample:

```bash
# one screen → one PNG
./gradlew :examples:taskflow:composeApp:renderUi \
  --args="--screen board --state seeded --theme dark --out board.png"

# discover what can be rendered, with a state example per screen (machine-readable)
./gradlew :examples:taskflow:composeApp:renderUi --args="--list"

# render many states in ONE warm JVM (the agent-loop path)
./gradlew :examples:taskflow:composeApp:renderUi \
  --args="--batch states.json"

# drive ARBITRARY state from JSON (not just presets)
./gradlew :examples:taskflow:composeApp:renderUi \
  --args="--screen board --state-file my-board.json --theme dark --out board.png"
```

### Flags

| Flag | Meaning | Default |
|------|---------|---------|
| `--screen` | `settings` \| `board` | — (required for single) |
| `--state` | screen-specific preset | `settings`→`default`, `board`→`seeded` |
| `--theme` | `light` \| `dark` \| `system` | `light` |
| `--out` | output PNG path (relative to repo root) | `render-<screen>-<state>-<theme>.png` |
| `--width` / `--height` | device pixels (2× density) | `822` × `1782` |
| `--list` | print capability manifest (incl. a `stateExample` per screen) as JSON | — |
| `--batch` | render every job in a JSON file (each job may carry an inline `stateJson`) | — |
| `--state-json` | inline JSON state the agent authors (overrides `--state`) | — |
| `--state-file` | path to a JSON state file (wins over `--state-json`) | — |

---

## Discovery — `--list`

So an agent never has to guess what exists:

```bash
./gradlew :examples:taskflow:composeApp:renderUi --args="--list"
```

```json
{
    "screens": [
        {
            "name": "settings",
            "presets": ["default", "offline-failing", "online-bot"],
            "stateExample": { "theme": "dark", "online": false, "failureRate": 0.5, "latencyMaxMs": 1500 }
        },
        {
            "name": "board",
            "presets": ["seeded", "empty"],
            "stateExample": { "columns": [{ "name": "To Do", "cards": [{ "title": "Write the spec", "labels": ["docs"], "assignee": "ann" }] }] }
        }
    ],
    "themes": ["light", "dark", "system"]
}
```

The `stateExample` is the schema an agent copies and edits — it never has to guess
the JSON shape.

---

## Example: an agent iterating on UI

> A design-review agent is asked to *"check the Settings and Board screens look
> right in light and dark, including the offline + empty edge states."* It has the
> `renderUi` CLI and a vision model. Here is the loop.

---

**🧑 User:** Verify the TaskFlow Settings and Board screens across themes and the
key edge states. Flag anything visually off.

**🤖 Agent:** Discovering what's renderable.

```bash
$ renderUi --list
{ "screens": [ {"name":"settings",...}, {"name":"board",...} ], "themes":[...] }
```

Two screens, with `offline-failing` and `empty` edge presets. I'll batch every
combination I care about in one warm process.

```bash
$ cat states.json
[
  {"screen":"settings","state":"default",        "theme":"light","out":"out/settings-light.png"},
  {"screen":"settings","state":"offline-failing", "theme":"dark", "out":"out/settings-dark-offline.png"},
  {"screen":"board",   "state":"seeded",          "theme":"dark", "out":"out/board-seeded-dark.png"},
  {"screen":"board",   "state":"empty",           "theme":"light","out":"out/board-empty-light.png"}
]

$ renderUi --batch states.json
[redux-ui] batch: 4 job(s) from states.json
[redux-ui] screen=settings state=default          theme=Light 822x1782px -> out/settings-light.png (55768 B, 662ms)
[redux-ui] screen=settings state=offline-failing  theme=Dark  822x1782px -> out/settings-dark-offline.png (58131 B, 91ms)
[redux-ui] screen=board    state=seeded           theme=Dark  822x1782px -> out/board-seeded-dark.png (58786 B, 271ms)
[redux-ui] screen=board    state=empty            theme=Light 822x1782px -> out/board-empty-light.png (25641 B, 70ms)
```

Four renders, ~1.1s total (first pays JVM/skiko warmup; the rest are 70–270ms).
Inspecting each.

---

**🤖 Agent:** **Settings — light, default.** Theme picker shows `System`
selected, latency `300–800 ms`, failure `10%`, bot + online on. Clean, controls
aligned, label hierarchy correct. ✅

![Settings, light, default](assets/settings-light-default.png)

---

**🤖 Agent:** **Settings — dark, offline-failing.** State seeded with
`SetOnline(false)`, `SetFailureRate(0.9)`, `SetLatency(0,3000)`. The render tracks
it exactly: `Dark` segment selected + dark scheme, latency `0–3000 ms`, failure
`90%` (error-tinted track), bot off, and the row label flipped `Online → Offline`
with the switch off. Edge state renders correctly. ✅

![Settings, dark, offline + failing](assets/settings-dark-offline.png)

---

**🤖 Agent:** **Board — dark, seeded.** Full kanban from `LoadBoardSucceeded`:
"To Do" column, count badge `2`, two cards with colored label chips
(`backend` / `docs`), `AP` avatar monograms (no-network image loader → deterministic
fallback, as designed), search field, green online dot, undo/redo, FAB. ✅

![Board, dark, seeded](assets/board-seeded-dark.png)

---

**🤖 Agent:** **Board — light, empty.** Same screen, `BoardModel` left unloaded →
the light-theme loading skeleton (shimmer placeholders), no cards. The empty/loading
path renders correctly and doesn't collapse. ✅

![Board, light, empty (loading skeleton)](assets/board-empty-light.png)

---

**🤖 Agent:** **Summary.** 4/4 states render correctly and are faithful to the
seeded Redux state — including both edge cases. No visual defects found. Theme,
failure-rate styling, label colors, avatar fallback, and the empty-state skeleton
all behave. (Note: headless JVM render is high-fidelity triage, not pixel-identical
to a physical Android device — confirm final polish on-device.)

---

## Driving arbitrary state from JSON

Presets cover the demo states; for everything else the agent authors state directly
as JSON. The wire format is a small, stable DTO per screen (not the internal
`ModelState`), mapped to real domain objects and dispatched through the real
reducers.

**🤖 Agent:** I want a board that isn't in the seed data — three columns, my own
cards and labels. I'll write the state and render it.

```bash
$ cat my-board.json
{
  "columns": [
    { "name": "Backlog", "cards": [
      { "title": "Agent-authored card", "labels": ["p1", "design"], "assignee": "ann" },
      { "title": "Wire up the daemon",  "labels": ["backend", "infra"], "assignee": "raj" }
    ]},
    { "name": "In Progress", "cards": [ { "title": "Ship JSON state", "labels": ["frontend"] } ] },
    { "name": "Done", "cards": [] }
  ]
}

$ renderUi --screen board --state-file my-board.json --theme dark --out out/board.png
[redux-ui] screen=board state=json theme=Dark 822x1782px -> out/board.png (57595 B, 740ms)
```

The render is exactly the authored state: column **Backlog** (badge `2`), the two
cards with `p1`/`design` and `backend`/`infra` chips (label names resolved to their
palette colors), assignee avatars, and three columns in the pager. None of this is
seed data — it came entirely from the JSON.

![Board rendered from agent-authored JSON](assets/board-json-agent.png)

In a `--batch` file each job can embed its own `stateJson`, so an agent sweeps a
whole set of hand-authored states in one warm process:

```json
[
  { "screen": "board",    "theme": "dark",  "out": "out/a.png",
    "stateJson": { "columns": [ { "name": "Ideas", "cards": [ { "title": "From batch", "labels": ["design"] } ] } ] } },
  { "screen": "settings", "theme": "light", "out": "out/b.png",
    "stateJson": { "theme": "light", "online": false, "failureRate": 0.7 } }
]
```

---

## Performance

Measured on the TaskFlow sample (per-render timer around the off-screen render):

| Mode | First render | Subsequent renders |
|------|--------------|--------------------|
| Per-process CLI invoke | ~0.5–0.7 s | n/a (new JVM each time) |
| **`--batch` (one warm JVM)** | ~0.6 s | **~70–110 ms** |
| In-JVM (test harness) | 861 ms (cold) | **~50 ms** |

The dominant cost is one-time **JVM boot + skiko classload**, not the render. Keep
the process warm (batch, or a future daemon) and throughput is ~10–20 renders/sec —
fast enough for an agent to sweep dozens of states per turn.

> A Rust/Go rewrite would **not** help: Compose/skiko render only on the JVM, so a
> rewrite can't touch the dominant cost. The lever is a warm process, not a language.

---

## Why this works — and the two gotchas the spike surfaced

The whole property comes from Redux: a screen takes a `Store<ModelState>` and reads
slices via `fieldStateOf` / `selectorState`. Seed the state by dispatching pure
actions, render, done — no platform needed. Two non-obvious constraints fell out:

1. **Register every model a screen reads.** `ModelState.get` throws on an
   unregistered model, and `selectorState` swallows + retries the read, so a missing
   slice sends the recomposer into an infinite loop (the render never settles). The
   board scene registers all seven slices it reads.
2. **`exitProcess(0)` when done.** Compose/skiko leave non-daemon threads alive, so
   `main()` returning isn't enough — the JVM would hang after writing the PNG.

---

## Limitations & next steps

- **Fidelity:** desktop/JVM Compose render ≈ Android, not pixel-identical (fonts,
  ripple, insets, system bars). Great for fast triage; confirm final on-device.
- **JSON state ✅ done** via `--state-json` / `--state-file` / batch `stateJson`. The
  schema is a per-screen DTO (`SettingsSpec` / `BoardSpec`), intentionally simpler
  than the internal `ModelState`. Extending a screen's authorable surface = adding
  fields to its spec.
- **Two screens** wired (settings, board). Adding BoardList / Profile / CardDetail /
  Login is a few lines each in the scene registry.
- **Warm daemon:** a persistent process reading jobs on stdin/socket would hold the
  ~50 ms render with zero per-invoke boot.
- **MCP wrapper:** expose `render_ui(screen, state) → image` so an agent calls it
  natively and gets the PNG straight into vision.

---

*Source: `composeApp/src/jvmMain/.../render/RenderCli.kt` (+ `RenderScenes.kt`),
Gradle task `:examples:taskflow:composeApp:renderUi`. Validation test:
`composeApp/src/jvmTest/.../spike/RenderSpikeTest.kt`.*

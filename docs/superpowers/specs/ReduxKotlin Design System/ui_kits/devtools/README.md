# In-App DevTools UI Kit — Material 3 Expressive

A high-fidelity, animated mobile showcase of the **In-App Redux DevTools** described in the
`2026-06-01-redux-kotlin-inapp-devtools-design.md` design doc. It's the flagship surface of this
design system: techie, Material 3 Expressive, motion-rich, and built to be lifted into real
tooling and sample apps.

## Adaptive layout (WindowSizeClass)
The design doc calls for one UI that adapts by width. Both are here, cross-linked by the segmented
switcher at the top of each page:
- **Compact** (`index.html`) — phone. Bubble / edge-swipe → **ModalBottomSheet** with the five tabs.
- **Expanded** (`index-expanded.html`) — tablet/desktop. The drawer becomes a **persistent right-docked
  panel** with the action log and the inspector (State / Diff / Pipeline / Outputs) visible at once —
  the reduxjs-DevTools-on-a-wide-screen affordance. Same data, same components, same one integration.

## The experience
A host app (the Redux Todos sample) runs with two debug triggers: a **floating, draggable swirl
bubble** and an **edge-swipe tab**. Activating either springs up a **Material 3 ModalBottomSheet
drawer** — the DevTools — over a scrim. The drawer has the five tabs from the design:

| Tab | What it shows |
|---|---|
| **Actions** | Scrolling, filterable action log (type · payload · timestamp). Tap to select; **Replay** re-streams the session so you can watch live capture. |
| **State** | Recursive, expandable `AppState` tree inspector (Kotlin-tinted JSON). |
| **Diff** | Per-action added / changed / removed paths with summary counts and colored accents. |
| **Pipeline** | The static `dispatch → [middleware] → rootReducer{slices}` map that **lights up** the nodes the selected action traversed, with per-node timing and a "changed" flag on slices. |
| **Outputs** | The one-integration / multiple-outputs model: in-app (locked on), **Remote (WebSocket)** and File log as M3 switches — toggling remote shows a live "connected" state. |

Selecting an action in **Actions** drives State, Diff and Pipeline — they all reflect that action.

## Motion (Material 3 Expressive)
- Sheet springs up with an overshoot easing (`--ease-spatial`); scrim cross-fades.
- Tab bar has a gradient indicator that slides between tabs; content cross-fades on switch.
- Action rows and diff rows stagger in; the bubble pulses; pipeline nodes light up in sequence
  with a traveling pulse along the connectors; M3 switches animate the thumb.

## Files
| File | Role |
|---|---|
| `index.html` | Entry point — React + Babel + Material Symbols + keyframes. |
| `index-expanded.html` | **Expanded** (tablet/desktop) layout — DevTools as a persistent right-docked panel. |
| `app-expanded.jsx` | Desktop-window shell + the expanded orchestration. |
| `DevToolsPanel.jsx` | The expanded right-panel: persistent action list + inspector tabs (reuses the tab views). |
| `devtools-data.jsx` | A coherent recorded session: actions, state snapshots, diffs, pipeline traces. |
| `HostApp.jsx` | The observed host app + the bubble and edge-swipe triggers. |
| `DevToolsTabs.jsx` | The five tab views + JSON tree, diff rows, pipeline nodes, M3 switch. |
| `DevToolsSheet.jsx` | The bottom-sheet shell + animated tab bar. |
| `android-frame.jsx` | M3 device bezel (starter component). |
| `app.jsx` | State orchestration (open, selection, replay, output toggles). |

## Notes
- Colors & type come from the shared `colors_and_type.css` (primary blue, magenta/orange brand
  gradient for accents, JetBrains Mono for all log/JSON/timing text).
- Icons use **Material Symbols Rounded** (the canonical M3 set), loaded with `display=block`.
- Data is a curated recording for showcase purposes — wire `devTools()` + `ReduxDevToolsHost { }`
  in a real Compose Multiplatform app to get this against live state (see the design doc).
- This is an original M3 composition realizing the design doc's spec, not a copy of an existing screen.

Design source: `2026-06-01-redux-kotlin-inapp-devtools-design.md` · API: https://github.com/reduxkotlin/redux-kotlin

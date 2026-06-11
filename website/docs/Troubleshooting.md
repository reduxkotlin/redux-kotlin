---
id: troubleshooting
title: Troubleshooting
sidebar_label: Troubleshooting
---

# Troubleshooting

This is a place to share common problems and solutions to them.  This 
will be added to over time.  You may find the [JS Redux troubleshooting
helpful.](https://redux.js.org/troubleshooting)

## State persistence & restore

### My saved state isn't restored after process death

Things to check, in order:

1. **Is the anchor reached on relaunch?** `rememberSaveableState` only
   restores when it composes. If the anchor sits behind a conditional that
   is false on a cold start (e.g. a "loaded" gate), the restored snapshot
   is never consumed. Place the anchor as high as possible in the
   composition.
2. **Did the key collide or change?** The default key is derived from the
   call-site position; navigation or lists can make positions collide, and
   refactors can move them. Pass an explicit, stable
   `key = "..."` — and scope it to the data's identity (e.g.
   `key = "account-ui-$accountId"` in a multi-account app) so one scope's
   snapshot can't restore into another.
3. **Did decoding fail?** Restore is best-effort: a snapshot that can't be
   decoded (e.g. after a schema change) is dropped silently and the app
   starts cold. Ship `StateSaver(json = Json { ignoreUnknownKeys = true })`
   for additive changes, and a `version` field in the snapshot for breaking
   ones.
4. **Is the platform a no-op?** Desktop, JS and wasm have no OS
   saved-instance state — the anchor does nothing there. Test on Android
   (e.g. *"Don't keep activities"* or `adb shell am kill`) or iOS.

### A restored screen renders, but its data never loads

The nav stack (or route) comes back after process death, yet the screen is
empty — lists render their empty state, detail screens show a skeleton.

Restore dispatches exactly **one** action; it does not replay the events
that originally led to the screen. If your data load is triggered by a
navigation *event* (dispatched alongside `Navigate` in a click handler),
the restore path never runs it — the same way a page that fetches in a
click handler breaks on browser refresh. Fix one of two ways:

- **Key the load on state, not events**: an effect keyed on the restored
  route/selection (e.g. `DisposableEffect(route)` or a middleware watching
  the slice) fires for a real navigation *and* for a restore — and also for
  DevTools time-travel and any other state hydration.
- **Handle the restore action in middleware**: the restore action flows
  through the full middleware chain like any dispatch, so an effects
  middleware can match it and start the loads.

Also check what the data *should* be: restoration can be innocent. Verify
the store contents (e.g. with the DevTools action log) before concluding
state was lost — a background actor or sync may have legitimately moved the
data. See
[Restoration replays no events](/advanced/compose-integration#restoration-replays-no-events--key-effects-on-state).

### A restored value appears, then reverts to the initial value

Compose bindings are one-directional (`store → State`). If you restore a
value into a Composable (e.g. with plain `rememberSaveable`) but the value
*lives in the store*, the next subscription update overwrites it with the
store's initial state. State that lives in the store must be restored by
**dispatching** — that's exactly what
[`rememberSaveableState`](/advanced/compose-integration#saving-state-across-rotation--process-death)
does.

### The first frame shows the initial (un-restored) state, then jumps

Restore must happen **before** the first frame reads the store:

- For OS-saved snapshots, `rememberSaveableState` dispatches the restore
  action synchronously during composition of the anchor — make sure the
  anchor composes *above* the Composables that read the restored slice.
- For state you persist yourself, seed it at store construction with
  `preloadedState` (`createStore`, `createModelStore`,
  `createConcurrentModelStore`) instead of dispatching after the UI is up —
  see [Rehydrating at construction](/advanced/compose-integration#rehydrating-at-construction-preloadedstate).

### Bindings lag one frame behind a dispatch

With a concurrent store whose `NotificationContext` always *posts* to the
main thread, a main-thread dispatch is observed one loop iteration later.
Wrap the post with `coalescingNotificationContext(isOnTargetThread, post)`
from `redux-kotlin-concurrent`: main-thread dispatches notify inline,
off-main dispatches still marshal to main (at most one loop hop — that part
is inherent to posting). The Compose bindings read the store synchronously
on every read, so any recomposition that does run renders current state;
the lag affects *when* the dispatch-triggered recomposition is scheduled,
not what a recomposition reads.

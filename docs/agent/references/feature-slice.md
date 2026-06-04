---
tier: T1
concern: feature-slice
derives_from:
  - examples/taskflow/composeApp/src/commonMain/kotlin/org/reduxkotlin/sample/taskflow/feature/boardlist → BoardListModel, boardListReducer, BoardListScreen, CreateBoard, BoardSummaryCard
  - examples/taskflow/composeApp/src/commonMain/kotlin/org/reduxkotlin/sample/taskflow/feature/board/EffectsMiddleware.kt → effectsMiddleware
  - examples/taskflow/composeApp/src/commonMain/kotlin/org/reduxkotlin/sample/taskflow/feature/board/BoardSelectors.kt → deriveVisibleCardIds
  - examples/taskflow/composeApp/src/commonMain/kotlin/org/reduxkotlin/sample/taskflow/core/Action.kt → Action, Undoable
  - examples/taskflow/composeApp/src/commonMain/kotlin/org/reduxkotlin/sample/taskflow/core/CardActions.kt → InverseOp
  - examples/taskflow/composeApp/src/commonMain/kotlin/org/reduxkotlin/sample/taskflow/app/AccountStore.kt → declareAccountModels
api_files:
  - redux-kotlin-compose-multimodel/api/redux-kotlin-compose-multimodel.klib.api
  - redux-kotlin-multimodel/api/redux-kotlin-multimodel.klib.api
rules: [C, E, G]
assembles_into: [AGENTS.md, claude-skill]
last_verified: { commit: 06214a9, date: 2026-06-03 }
---

# Feature slice

> How to add a vertical feature slice to the package-by-feature `examples/taskflow` app: the seven
> elements, where each lives, the design rule it honors, and how to wire it into the store.

## What a feature slice is here

A feature slice is **one directory `feature/<name>/`** that co-locates everything a feature owns:
its slot model(s), actions, reducer(s), effect handler, screen + child composables, selectors, and
tests. The `boardlist` slice is the smallest complete example — a slot model, leaf actions, one pure
reducer, a screen, and a card — so it is the spine of this guide; the heavier `board` slice supplies
the effects/selectors callouts.

Shared homes sit outside `feature/*`:

- **`core`** — the domain kernel: entities (`BoardEntities.kt`), the `Action`/`Undoable` marker
  interfaces (`Action.kt`), and the cross-feature card/sync-contract actions + `InverseOp`
  (`CardActions.kt`). No Compose, no IO.
- **`infra`** — platform shims (expect/actual), the db, the data/sync layer, util.
- **`app`** — the composition root: `App.kt`, the store factories (`AppStore.kt`, `AccountStore.kt`),
  and `app/nav`.
- **`ui`** — theme, `CompositionLocal`s, and cross-feature widgets.

Dependency direction (one way, never back): `core ← infra`; `core / infra / ui ← feature/*`;
everything `← app`. A feature may depend on `core`/`infra`/`ui` and may reference another feature's
public leaf actions (e.g. `board`'s effects import `boardlist`'s `CreateBoard`), but `core` never
imports a feature.

## The seven elements

### 1. Models

Two homes. The **slot model** is the immutable per-account state the store holds for this feature; it
lives in the slice: `examples/taskflow/composeApp/src/commonMain/kotlin/org/reduxkotlin/sample/taskflow/feature/boardlist → BoardListModel`.
The **entity** it caches is a domain type, so it lives in the kernel:
`examples/taskflow/composeApp/src/commonMain/kotlin/org/reduxkotlin/sample/taskflow/core/AccountEntities.kt → BoardSummary`.
Rule: feature-private projection state stays in the feature; anything more than one feature needs (or
that is fundamentally a domain noun) goes to `core`. Slot models default every field so the store can
declare an empty slot.

### 2. Actions

Feature leaves live in the slice:
`examples/taskflow/composeApp/src/commonMain/kotlin/org/reduxkotlin/sample/taskflow/feature/boardlist → CreateBoard, LoadBoardListSucceeded`.
The markers and the cross-feature card contract live in the kernel:
`examples/taskflow/composeApp/src/commonMain/kotlin/org/reduxkotlin/sample/taskflow/core/Action.kt → Action, Undoable`
and `examples/taskflow/composeApp/src/commonMain/kotlin/org/reduxkotlin/sample/taskflow/core/CardActions.kt → InverseOp`.

Every concrete action implements `Action`; user card mutations also implement `Undoable` (drives the
undo stack). **`Action` and `Undoable` are plain interfaces, NOT `sealed`.** Kotlin requires every
subtype of a `sealed` type to live in the same package, but package-by-feature spreads action leaves
across many feature packages — a sealed root would force every action back into `core`, defeating the
slicing. The cost is exhaustiveness: a non-sealed root means `when (action)` has no compiler-checked
totality, so every routing/reducer `when` ends in an `else` that returns state unchanged — which is
the correct Redux semantics anyway, so the choice is behavior-preserving. By contrast `InverseOp`
**stays `sealed`**: its leaves are a fixed, small set co-located in `core/CardActions.kt`, so
`when (inverse)` can be exhaustive with no `else`.

### 3. Reducer

Pure `(model, action) -> model`, same instance returned for unhandled actions:
`examples/taskflow/composeApp/src/commonMain/kotlin/org/reduxkotlin/sample/taskflow/feature/boardlist → boardListReducer`.
The store's routing DSL (`model<M> { on<T> { } }`) dispatches each registered action class to the
reducer, so the reducer's own `when` only needs the leaves it handles plus `else -> model`. The
reducer is the home for **Rule G** at the *consume* side: it never reads a clock or generates an id —
`CreateBoard` carries a pre-minted `boardId` and `now`, and the reducer stamps the new `BoardSummary`
straight from the action. (Multi-slot reducers — see `board` — take a captured `selfId` argument from
the store wiring rather than reading identity at runtime.)

### 4. Effects

**Rule E — off-main effects.** The ONLY place intent becomes IO is a middleware; each feature's
handler is a per-feature `Middleware<ModelState>` composed into the pipeline. Board callout:
`examples/taskflow/composeApp/src/commonMain/kotlin/org/reduxkotlin/sample/taskflow/feature/board/EffectsMiddleware.kt → effectsMiddleware`.
All repository/sync work runs on the per-account background `scope` (off-main); dispatches marshal
back to main through the store's `NotificationContext`, so the effect code does no explicit main hop.
The handler routes by exact action class, runs the optimistic reducer via `next(action)`, then
launches the matching repository call — capturing the per-op `InverseOp` *before* `next` so a backend
rejection can revert exactly that op. A feature ships its own handler only if it performs IO: `boardlist`
adds no `boardlist` middleware — its `CreateBoard` and board-load actions are persisted by the shared
effects handler in `feature.board`, which owns the repository.

### 5. Screen

**Rule C — render isolation.** No composable reads a slot wholesale; each binds the narrowest slice.
Boardlist spine:
`examples/taskflow/composeApp/src/commonMain/kotlin/org/reduxkotlin/sample/taskflow/feature/boardlist → BoardListScreen`.
Board callout:
`examples/taskflow/composeApp/src/commonMain/kotlin/org/reduxkotlin/sample/taskflow/feature/board/BoardScreen.kt → BoardScreen`.
The screen wraps its store once via `rememberStableStore(store).value`, then binds slices with
`fieldStateOf` (a whole field, value-equal) or `selectorState` (a derived snapshot); per ARCHITECTURE
§17, each narrowly-bound leaf is wrapped in `key(...)` so it recomposes independently. Child composables
receive finished immutable data plus a remembered callback — the store never reaches a child. Editor
text and dialog-open flags stay in transient local `remember`, never the store. Rule G lives at the
*mint* side here: ids/clock for `CreateBoard` come from `LocalIdGenerator` / `LocalClock` at the
dispatch site.

### 6. Selectors

Pure derivation functions, the home for all list/filter work that Rule C bans from composable bodies:
`examples/taskflow/composeApp/src/commonMain/kotlin/org/reduxkotlin/sample/taskflow/feature/board/BoardSelectors.kt → deriveVisibleCardIds`.
A selector takes the bound model(s) and returns a value-equal result (a `PersistentList`, a small
data class) so a `selectorState` recomposes only when the derived value genuinely changes. Keep them
free of Compose and IO — they are plain testable functions.

### 7. Tests

`commonTest` by default (multiplatform); `jvmTest` only for jvm-only assertions. Cite:
`examples/taskflow/composeApp/src/commonTest/kotlin/org/reduxkotlin/sample/taskflow/feature/board/BoardReducersTest.kt → BoardReducersTest`.
Reducers and selectors are pure, so they test directly with no store. Each new slice adds at least a
reducer test (one case per handled action, plus an unchanged-on-unhandled assertion) and, where it has
them, selector tests.

## Store wiring

A slice is inert until the composition root registers it. Two edits in `app`:

1. **Declare the slot + route its actions** (order matters: declare every slot up front so
   `ModelState.get` never misses):
   `examples/taskflow/composeApp/src/commonMain/kotlin/org/reduxkotlin/sample/taskflow/app/AccountStore.kt → declareAccountModels`.
   Add a `model(MyModel()) { on<MyAction> { s, a -> myReducer(s, a) } }` block. Register the SAME
   reducer on each slot that handles a shared action (the routing layer fires each registered handler
   and leaves other slots untouched). Root-scoped models register in
   `examples/taskflow/composeApp/src/commonMain/kotlin/org/reduxkotlin/sample/taskflow/app/AppStore.kt → createAppStore`
   instead.
2. **Compose the effect handler** into the per-account pipeline, in fixed order: `activityLogger`,
   then `undo`, then `effects` (see the `named(...)` pipeline in `createAccountStore`). A new
   feature's middleware is added to that `devToolsMiddleware(...)` list at the right position.

## Verify loop

Tight inner loop, fast to slow: `compile <target>`, then `commonTest` / `jvmTest`, then `detektAll`,
then `apiCheck`.
`apiCheck` matters only when you touch a library module's public API (taskflow itself has none);
the backing `.api` dumps are listed in `api_files`. iOS-sim targets are host-gated — run them
locally only on macOS; trust CI otherwise. Full treatment → [testing.md](./testing.md).

`detektAll` is the gate that bites first: `explicitApi()` is on, so every new `public` declaration in a
slice (models, actions, reducer, screen, selectors) needs an explicit visibility modifier **and** a KDoc
comment — including nested `data class`es and their properties. Formatting auto-corrects; missing KDoc does
not. Document public symbols as you write them, and never bypass the hook with `--no-verify`.

## Codegen note

KSP `@Reduce` / `@ReduxInitial` codegen is packaging-agnostic: a `@Reduce`-annotated reducer can live
in any `feature/*` package and the generated routing wiring follows it — package-by-feature does not
change how codegen resolves it. Full codegen treatment is deferred to a planned guide
*(planned — 0-rest)*.

## See also

- [README](./README.md)

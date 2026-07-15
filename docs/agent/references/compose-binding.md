---
tier: T1
concern: compose-binding
derives_from:
  - examples/taskflow/composeApp/src/commonMain/kotlin/org/reduxkotlin/sample/taskflow/feature/board/BoardScreen.kt → BoardScreen
  - examples/taskflow/composeApp/src/commonMain/kotlin/org/reduxkotlin/sample/taskflow/feature/board/KanbanCard.kt → KanbanCard
  - examples/taskflow/composeApp/src/commonMain/kotlin/org/reduxkotlin/sample/taskflow/feature/board/ColumnHeader.kt → ColumnHeader
  - examples/taskflow/composeApp/src/commonMain/kotlin/org/reduxkotlin/sample/taskflow/feature/board/BoardSelectors.kt → deriveVisibleCardIds
  - examples/taskflow/composeApp/src/commonMain/kotlin/org/reduxkotlin/sample/taskflow/ui/Locals.kt → LocalIdGenerator, LocalClock
  - examples/taskflow/composeApp/src/jvmTest/kotlin/org/reduxkotlin/sample/taskflow/feature/board/RenderIsolationTest.kt → RenderIsolationTest
api_files:
  - redux-kotlin-compose-multimodel/api/redux-kotlin-compose-multimodel.klib.api
  - redux-kotlin-compose/api/redux-kotlin-compose.klib.api
rules: [C, G]
assembles_into: [AGENTS.md, claude-skill]
last_verified: { commit: 3c1cd67, date: 2026-06-04 }
---

# Compose binding (Rule C)

> How a screen binds the narrowest slice of the store so one card move recomposes only the two
> affected columns — and how ids/timestamps are minted at the dispatch site, never in a reducer.

## Rule C — render isolation

**No composable reads a model wholesale.** Every leaf binds the smallest slice it needs and is wrapped
in `key(...)` so it recomposes independently. `BoardScreen` is the worked example:
`examples/taskflow/composeApp/src/commonMain/kotlin/org/reduxkotlin/sample/taskflow/feature/board/BoardScreen.kt → BoardScreen`.
It is deliberately split into per-column and per-card composables — moving one card changes only the
two affected columns' slices, and every other column stays frozen.

## The binding API

From `redux-kotlin-compose` and `redux-kotlin-compose-multimodel` (`api_files` above):

- **`rememberSelectorStore(store)`** — create one `SelectorStore<ModelState>` near the root of a Compose
  composition and pass it through the screen tree. Its bindings share one store callback while each
  selected value retains independent equality checks and recomposition isolation. It is `@Stable` and
  delegates `dispatch`, so binding components use it directly rather than unwrapping `StableStore.value`.
- **`fieldStateOf(Model::class) { slice }`** — bind a single-model slice as Compose `State<T>`. Fires
  only when the selected value changes identity. Because reducers reuse unchanged instances (structural
  sharing), a sibling edit leaves an untouched card's reference identical → no recomposition.
- **`selectorState { ms -> derived }`** — bind a value derived across models. Recomposes only when the
  value-equal result genuinely changes. This is the home for all list/filter work that Rule C bans
  from composable bodies.

### The pattern

```
val s = rememberSelectorStore(store)
// per column, keyed so each is tracked independently:
key(colId) {
    val cardIds by s.selectorState(colId) { ms ->
        deriveVisibleCardIds(ms.get<BoardModel>(), ms.get<FilterModel>(), colId)
    }
    // per card, keyed:
    key(cardId) {
        val card by s.fieldStateOf(cardId, BoardModel::class) { it.board?.cards?.get(cardId) }
        KanbanCard(card = card, onClick = onCardClick)   // store never reaches the child
    }
}
```

Two non-negotiables visible here:

- **`key(...)` per leaf.** `key(colId)` / `key(cardId)` give Compose a stable identity per item, so an
  unchanged item is never recomposed when a sibling changes. Reference:
  `examples/taskflow/composeApp/src/commonMain/kotlin/org/reduxkotlin/sample/taskflow/feature/board/BoardScreen.kt → BoardScreen`.
- **List derivation lives in pure functions, not composable bodies.** No `.filter`/`.count`/`.sortedBy`
  in a `@Composable`; call a selector:
  `examples/taskflow/composeApp/src/commonMain/kotlin/org/reduxkotlin/sample/taskflow/feature/board/BoardSelectors.kt → deriveVisibleCardIds`.

## Leaves are pure

A child composable never receives the store — only finished immutable data plus a remembered callback
minted at the binding point (`remember(store) { { id -> store.dispatch(OpenCard(id)) } }`). Binding
components may receive the stable `SelectorStore`; they dispatch directly and pass callbacks down to
pure leaves. Editor text
and dialog-open flags stay in transient local `remember`, never the store.
`examples/taskflow/composeApp/src/commonMain/kotlin/org/reduxkotlin/sample/taskflow/feature/board/KanbanCard.kt → KanbanCard`
and `examples/taskflow/composeApp/src/commonMain/kotlin/org/reduxkotlin/sample/taskflow/feature/board/ColumnHeader.kt → ColumnHeader`
take plain data + an `onClick`, read no store, and run no business logic. The one allowed exception is an
O(1) lookup (resolving an assignee from a collaborators map).

## Rule G — mint at the dispatch site

Ids and timestamps are created where the action is dispatched, never inside a reducer. The screen reads
two CompositionLocals:
`examples/taskflow/composeApp/src/commonMain/kotlin/org/reduxkotlin/sample/taskflow/ui/Locals.kt → LocalIdGenerator, LocalClock`,
then dispatches with the minted values, e.g. `dispatch(CreateBoard(idGen.newBoardId(), name, clock()))`.
Injecting these via locals lets tests supply a deterministic id sequence and a fixed clock without
threading constructor parameters. The reducer just stamps the finished id/timestamp into state. (Detail
on the platform `newUuid()` backing → [platform-shims.md](./platform-shims.md).)

## Proof it works

`examples/taskflow/composeApp/src/jvmTest/kotlin/org/reduxkotlin/sample/taskflow/feature/board/RenderIsolationTest.kt → RenderIsolationTest`
binds three columns through `key(colId)` + `fieldStateOf`, counts recompositions in a plain (non-snapshot)
map held in `remember`, dispatches a card move A→B, and asserts: A and B recompose (their `cardIds` slice
changed), **C does not** (its slice is untouched), and an unrelated `SetFilterQuery` leaves C flat too.

## Verify loop

`./gradlew :examples:taskflow:composeApp:jvmTest` runs `RenderIsolationTest` (the Compose UI tests are
JVM-hosted), then `./gradlew detektAll`. `explicitApi()` requires a KDoc on every public composable.
Full treatment → [testing.md](./testing.md).

## Pitfalls

- Reading a whole model (`val board by s.fieldStateOf(BoardModel::class) { it }`) then indexing in the
  body re-recomposes the leaf on every board change — defeats Rule C.
- `selectorState` prevents unrelated recomposition, but an ordinary selector still runs for every store
  notification and every `State.value` read. For an expensive derived projection, declare its narrow
  inputs with `memoizedSelector` and hoist the resulting selector outside the composable (or `remember`
  it with every captured parameter as a key).
- `SelectorStore` reduces store fan-out, not arbitrary selector evaluation: one root-scoped facade
  replaces N store subscribers with one callback that still compares N active selectors. Use
  memoization as well when the transform itself is expensive. `rememberSelectorSubscriptions()` and
  its scoped overloads remain the lower-level option for a separately managed subtree/controller scope.
- A selector that captures a changing parameter is intentionally retained by the original
  `selectorState { ... }` overload. Use `selectorState(parameter) { ... }` (and the scoped keyed
  counterpart) so Compose tears down the old subscription and installs the selector for the new key.
- A Compose-bound concurrent store must deliver notifications serially on the UI thread. On Android,
  use `coalescingNotificationContext` around the main `Handler`; do not leave
  `NotificationContext.Inline` in place when effects dispatch from a worker, and never use a
  multi-threaded executor for granular bindings.
- Forgetting `key(...)` makes Compose track items positionally, so a move recomposes everything after
  the insertion point.
- Calling `Clock.System.now()` or generating an id inside a reducer breaks Rule G and makes the reducer
  non-deterministic to test.

## See also

- [feature-slice.md](./feature-slice.md) — the screen as one of the seven slice elements.
- [store-setup.md](./store-setup.md) — where the bound `Store<ModelState>` comes from.
- [README](./README.md)

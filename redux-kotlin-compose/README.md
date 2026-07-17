# redux-kotlin-compose

Jetpack / Compose Multiplatform bindings for [redux-kotlin](../redux-kotlin):
read store state as Compose `State<T>` so your composables recompose only when
the slice they read changes.

## Install

```kotlin
implementation("org.reduxkotlin:redux-kotlin-compose:<version>")
```

(For a Compose app, prefer the [`redux-kotlin-bundle-compose`](../redux-kotlin-bundle-compose).)

## Quick start

```kotlin
import org.reduxkotlin.compose.SelectorStore
import org.reduxkotlin.compose.fieldState
import org.reduxkotlin.compose.rememberSelectorStore
import org.reduxkotlin.compose.selectorState

// At the platform/composition host, convert the raw Store once:
setContent { App(rememberSelectorStore(store)) }

@Composable
fun App(store: SelectorStore<AppState>) {
    val count by store.selectorState { it.count }          // arbitrary selector
    val name  by store.fieldState(AppState::userName)      // property reference
    Text("$name: $count")
    Button(onClick = { store.dispatch(Increment) }) { Text("Increment") }
}
```

Create one `SelectorStore` at the root of each Compose composition and pass it
to the components that bind state. It is `@Stable`, provides `dispatch`, and
shares one final-store callback among its bindings without exposing the raw
store, direct state reads, manual subscription, or reducer replacement.
`selectorState { … }` is the general form; `fieldState(Prop::ref)` is the
convenience for a single field.

Keep the raw `Store` in the platform host, runtime, and effect code. Connector
composables may receive `SelectorStore`; pure leaves should receive finished
values and stable callbacks. `StableStore` remains binary compatible but is
deprecated because its `.value` escape defeats this boundary.

For a larger callback surface, pass one method-only command interface with a
stable identity instead of a forwarding wrapper. Mark its implementation
`@Stable`, or list the Compose-free interface in the app's Compose compiler
stability configuration. That declaration is a promise: implementations must
keep identity stable, and remembered test adapters must key every callback they
capture.

When a selector captures a changing Compose value, key the binding so the old
selector is replaced:

```kotlin
val card by store.selectorState(cardId) { state -> state.cards[cardId] }
```

For a `createConcurrentStore` dispatched from effects or other workers, use a
serial `NotificationContext` that posts callbacks to the platform main thread
(such as `coalescingNotificationContext` around Android's main `Handler`). The
binding callback invalidates Compose state and must not run on an arbitrary
worker or multi-threaded executor.

## See also

- [Compose integration](https://reduxkotlin.org/advanced/compose-integration)
- For `ModelState`: [`redux-kotlin-compose-multimodel`](../redux-kotlin-compose-multimodel) · State persistence: [`redux-kotlin-compose-saveable`](../redux-kotlin-compose-saveable)

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

@Composable
fun App(store: Store<AppState>) {
    Counter(rememberSelectorStore(store))
}

@Composable
fun Counter(store: SelectorStore<AppState>) {
    val count by store.selectorState { it.count }          // arbitrary selector
    val name  by store.fieldState(AppState::userName)      // property reference
    Text("$name: $count")
}
```

Create one `SelectorStore` at the root of each Compose composition and pass it
to the components that bind state. It is `@Stable`, delegates `dispatch`, and
shares one final-store callback among its bindings. `selectorState { … }` is
the general form; `fieldState(Prop::ref)` is the convenience for a single
field.

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

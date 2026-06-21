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
import org.reduxkotlin.compose.selectorState
import org.reduxkotlin.compose.fieldState

@Composable
fun Counter(store: Store<AppState>) {
    val count by store.selectorState { it.count }          // arbitrary selector
    val name  by store.fieldState(AppState::userName)      // property reference
    Text("$name: $count")
}
```

`selectorState { … }` is the general form; `fieldState(Prop::ref)` is the
convenience for a single field. Wrap a store as `StableStore` to keep it stable
across recompositions.

## See also

- [Compose integration](https://reduxkotlin.org/advanced/compose-integration)
- For `ModelState`: [`redux-kotlin-compose-multimodel`](../redux-kotlin-compose-multimodel) · State persistence: [`redux-kotlin-compose-saveable`](../redux-kotlin-compose-saveable)

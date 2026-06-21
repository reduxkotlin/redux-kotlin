# redux-kotlin-compose-saveable

Store-anchored UI-state persistence for Compose: snapshot a slice of your
[redux-kotlin](../redux-kotlin) store into Compose's `SaveableStateRegistry` so
it survives configuration changes **and** process death, then restore it as a
dispatched action.

## Install

```kotlin
implementation("org.reduxkotlin:redux-kotlin-compose-saveable:<version>")
```

(Already included by [`redux-kotlin-bundle-compose`](../redux-kotlin-bundle-compose).)

## Quick start

```kotlin
import kotlinx.serialization.Serializable
import org.reduxkotlin.compose.saveable.StateSaver
import org.reduxkotlin.compose.saveable.rememberSaveableState

@Serializable
data class NavSnapshot(val route: String, val filter: String)

val navSaver = StateSaver<AppState, NavSnapshot>(
    serializer = NavSnapshot.serializer(),
    save = { NavSnapshot(it.route, it.filter) },          // state -> minimal snapshot
    restore = { RestoreNav(it.route, it.filter) },        // snapshot -> action
)

@Composable
fun App(store: Store<AppState>) {
    store.rememberSaveableState(navSaver)   // anchor: persists + restores across death
    // … render …
}
```

`save` projects the smallest snapshot worth persisting; `restore` turns a
recovered snapshot back into an action your reducer applies. Snapshots are
JSON-encoded via kotlinx.serialization.

## See also

- [Compose integration](https://reduxkotlin.org/advanced/compose-integration) · Compose bindings: [`redux-kotlin-compose`](../redux-kotlin-compose)

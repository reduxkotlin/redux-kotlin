# redux-kotlin-compose-multimodel

[Compose bindings](../redux-kotlin-compose) for
[`ModelState`](../redux-kotlin-multimodel) stores: read a field of one model in
the bag as Compose `State<T>`.

## Install

```kotlin
implementation("org.reduxkotlin:redux-kotlin-compose-multimodel:<version>")
```

(For a Compose app, prefer the [`redux-kotlin-bundle-compose`](../redux-kotlin-bundle-compose).)

## Quick start

```kotlin
import org.reduxkotlin.compose.multimodel.fieldState
import org.reduxkotlin.compose.multimodel.fieldStateOf

@Composable
fun Greeting(store: Store<ModelState>) {
    val name by store.fieldState(UserModel::name)               // reified model + property
    val items by store.fieldStateOf(CartModel::class) { it.items } // KClass + selector (non-inline)
    Text("$name has $items items")
}
```

## See also

- [Compose integration](https://reduxkotlin.org/advanced/compose-integration) · [MultiModel](https://reduxkotlin.org/advanced/multimodel)

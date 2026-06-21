# redux-kotlin-multimodel-granular

Field-level subscriptions ([granular](../redux-kotlin-granular)) for
[`ModelState`](../redux-kotlin-multimodel): subscribe to a field of one model in
the bag and fire only when that field changes.

## Install

```kotlin
implementation("org.reduxkotlin:redux-kotlin-multimodel-granular:<version>")
```

(Already included transitively by [`redux-kotlin-bundle`](../redux-kotlin-bundle).)

## Quick start

```kotlin
// notified only when UserModel.name changes within the ModelState store:
val off = store.subscribeTo(UserModel::name) { _, name -> render(name) }
// later: off()
```

`subscribeToModel` is the non-reified variant for callers holding a `KClass`.

## See also

- [Granular subscriptions](https://reduxkotlin.org/advanced/granular-subscriptions) · [MultiModel](https://reduxkotlin.org/advanced/multimodel)

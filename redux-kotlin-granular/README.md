# redux-kotlin-granular

Field-level subscriptions for [redux-kotlin](../redux-kotlin): be notified only
when a specific slice of state changes, instead of on every dispatch.

## Install

```kotlin
implementation("org.reduxkotlin:redux-kotlin-granular:<version>")
```

(Already included transitively by [`redux-kotlin-bundle`](../redux-kotlin-bundle).)

## Quick start

```kotlin
import org.reduxkotlin.granular.subscribeTo

// fires only when state.user.name actually changes:
val off = store.subscribeTo({ it.user.name }) { _, name -> render(name) }
// later: off()
```

Use `subscribeFields` to watch several selectors at once. Selected values are
compared by equality, so only real changes re-fire the listener.

## See also

- [Granular subscriptions](https://reduxkotlin.org/advanced/granular-subscriptions)
- For `ModelState`: [`redux-kotlin-multimodel-granular`](../redux-kotlin-multimodel-granular)

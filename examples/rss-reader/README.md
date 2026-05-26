# RSS Reader (redux-kotlin sample)

This example is a fork of JetBrains'
[`kmp-production-sample`](https://github.com/Kotlin/kmp-production-sample)
RSS reader, migrated from its hand-rolled `NanoRedux` store to `org.reduxkotlin.createStore`
plus a small thunk middleware for async work and a side-effect middleware for transient
UI events (snackbars). The Compose UI is unchanged in shape — only the data plumbing
moved.

See `git log baseline-nanoredux..HEAD -- examples/rss-reader/` for the migration diff.

## Architecture (`shared/src/commonMain/kotlin/com/github/jetbrains/rssreader/app/`)

| File                      | Purpose                                                            |
| ------------------------- | ------------------------------------------------------------------ |
| `FeedState.kt`            | Immutable state shape                                              |
| `FeedAction.kt`           | Sealed action hierarchy (dispatched by reducer)                    |
| `FeedReducer.kt`          | Pure `(State, Action) → State`                                     |
| `FeedThunks.kt`           | Async functions dispatched in place of actions                     |
| `FeedSideEffect.kt`       | Transient one-shot effects (errors)                                |
| `ThunkMiddleware.kt`      | Runs thunks; passes plain actions through                          |
| `SideEffectMiddleware.kt` | Pulls `FeedSideEffect` out of the action stream into a SharedFlow  |
| `StoreFactory.kt`         | `FeedStoreHolder` — builds the `Store<FeedState>` + exposes `StateFlow` |

## Running

```bash
./gradlew :examples:rss-reader:androidApp:installDebug
```

## License

MIT (inherited from upstream — see `LICENSE` at repo root for redux-kotlin, and the
`NOTICE` file in this directory for the upstream attribution).

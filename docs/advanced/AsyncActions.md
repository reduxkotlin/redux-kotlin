---
id: async-actions
title: Async Actions
sidebar_label: Async Actions
hide_title: true
---

# Async Actions

In the [basics guide](../basics/README.md), we built a simple todo application. It was fully synchronous. Every time an action was dispatched, the state was updated immediately.

In this guide, we will build a different, asynchronous application. It will use the Cat API to fetch a list of Cat breeds. How does asynchronicity fit into Redux flow?

## Actions

When you call an asynchronous API, there are two crucial moments in time: the moment you start the call, and the moment when you receive an answer (or a timeout).

Each of these two moments usually require a change in the application state; to do that, you need to dispatch normal actions that will be processed by reducers synchronously. Usually, for any API request you'll want to dispatch at least three different kinds of actions:

- **An action informing the reducers that the request began.**

  The reducers may handle this action by toggling an `isFetching` flag in the state. This way the UI knows it's time to show a spinner.

- **An action informing the reducers that the request finished successfully.**

  The reducers may handle this action by merging the new data into the state they manage and resetting `isFetching`. The UI would hide the spinner, and display the fetched data.

- **An action informing the reducers that the request failed.**

  The reducers may handle this action by resetting `isFetching`. Additionally, some reducers may want to store the error message so the UI can display it.

You may use a separate actions for every step of a request:

```kotlin
class Actions {
    class FetchingItemsStarted
    data class FetchingItemsSuccessAction(val itemsHolder: ItemsHolder)
    data class FetchingItemsFailedAction(val message: String)
}

```

Choosing how to represent the flow with actions is up to you and your team.  You can have a single set of ApiActions that include an endpoint, status, and result in one action object, for example.

Whatever convention you choose, stick with it throughout the application.  
We'll use separate types in this tutorial.

# Async Actions

The standard way to do async actions in Redux is to use the Redux-Kotlin-Thunk middleware.  It comes in a separate package called `redux-kotlin-thunk`.  We'll explain how middleware works in general later; for now, there is just one important thing you need to know: by using this specific middleware, you can dispatch a function and it will be executed by the thunk-middlware.  These functions are known as [thunks](https://en.wikipedia.org/wiki/Thunk).
Thunks don't need to be pure; it is thus allowed to have side effects, including executing asynchronous API calss.  The function can also dispatch actions--like normal synchronous actions.

Thunks can be defined anywhere, but it useful to group them together logically.  For example all thunks for an API can be grouped together in a class.  Or they can be top level functions in a file.  It is up to you and your team.

`NetworkThunks` from the [Name Game example app](https://github.com/reduxkotlin/NameGameSampleApp) shows how API request can be completed asynchronously using coroutines.


`NetworkThunks.kt`
```kotlin
class NetworkThunks(networkContext: CoroutineContext) {
    private val networkScope = CoroutineScope(networkContext)

    private fun repoForCategory(categoryId: QuestionCategoryId) = when (categoryId) {
        QuestionCategoryId.CATS -> CatItemRepository()
        QuestionCategoryId.DOGS -> DogItemRepository()
    }

    fun fetchItems(categoryId: QuestionCategoryId, numQuestions: Int) = thunk { dispatch, getState, extraArgument ->
        val repo = repoForCategory(categoryId)
        Logger.d("Fetching StoreInfo and Feed")
        networkScope.launch {
            dispatch(Actions.FetchingItemsStartedAction())
            val result = repo.fetchItems(numQuestions)
            if (result.isSuccessful) {
                Logger.d("Success")
                dispatch(Actions.FetchingItemsSuccessAction(result.response!!))
            } else {
                Logger.d("Failure")
                dispatch(Actions.FetchingItemsFailedAction(result.message!!))
            }
        }
    }
}

/**
 * Convenience function so state type does is not needed every time a thunk is created.
 */
fun thunk(thunkLambda: (dispatch: Dispatcher, getState: GetState<AppState>, extraArgument: Any?) -> Any) =
        createThunk(thunkLambda)

```

> ##### Note on Networking
>
> There are several options for networking. In this example it is abstracted over, and the implementation is with [Ktor](https://ktor.io/), a multiplatform networking library.  In a strictly Android app this could be Retrofit & OkHttp.
## Connecting to UI

Dispatching async actions is no different from dispatching synchronous actions, so we won't discuss this in detail.  See the [Example: Name Game](https://github.com/reduxkotlin/NameGameSampleApp) for the complete source code discussed in this example.

## Next Steps

Read [Async Flow](AsyncFlow.md) to recap how async actions fit into the Redux flow.

---
id: async-flow
title: Async Flow
sidebar_label: Async Flow
hide_title: true
---

# Async Flow

Without [middleware](Middleware.md), Redux store only supports 
[synchronous data flow](../basics/DataFlow.md). This is what you get by default with 
[`createStore()`](../api/createStore.md).

You may enhance [`createStore()`](../api/createStore.md) with 
[`applyMiddleware()`](../api/applyMiddleware.md). It is not required, but it lets you 
[express asynchronous actions in a convenient way](AsyncActions.md).

Asynchronous middleware like [redux-kotlin-thunk](https://github.com/reduxkotlin/redux-kotlin-thunk)
wraps the store's [`dispatch()`](../api/Store.md#dispatchaction) method and allows you to dispatch 
something other than actions, for example, functions. Any middleware you use can then intercept 
anything you dispatch, and in turn, can pass actions to the next middleware in the chain.

When the last middleware in the chain dispatches an action, it has to be a plain object. This is 
when the [synchronous Redux data flow](../basics/DataFlow.md) takes place.

Thunk is just one approach to async actions.  There are other possibilities - JS Redux ecosystem has
several interesting middlewares that allow async actions in a unit testable way. Some of these 
patterns may apply to ReduxKotlin as well.

## Next Steps

Now that you've seen an example of what middleware can do in Redux, it's time to learn how it
actually works, and how you can create your own. Go on to the next detailed section about
[Middleware](Middleware.md).

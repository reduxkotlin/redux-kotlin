---
id: store-setup
title: Store Setup
sidebar_label: Store Setup
---

# Redux FAQ: Store Setup

## Table of Contents

- [Is it OK to have more than one middleware chain in my store enhancer? What is the difference between next and dispatch in a middleware function?](#is-it-ok-to-have-more-than-one-middleware-chain-in-my-store-enhancer-what-is-the-difference-between-next-and-dispatch-in-a-middleware-function)
- [How do I subscribe to only a portion of the state? Can I get the dispatched action as part of the subscription?](#how-do-i-subscribe-to-only-a-portion-of-the-state-can-i-get-the-dispatched-action-as-part-of-the-subscription)
- [How do I persist store state and restore it on the next launch?](#how-do-i-persist-store-state-and-restore-it-on-the-next-launch)

## Store Setup

### Is it OK to have more than one middleware chain in my store enhancer? What is the difference between `next` and `dispatch` in a middleware function?

Redux middleware act like a linked list. Each middleware function can either call `next(action)` to
pass an action along to the next middleware in line, call `dispatch(action)` to restart the
processing at the beginning of the list, or do nothing at all to stop the action from being
processed further.

This chain of middleware is defined by the arguments passed to the `applyMiddleware` function used
when creating a store. Defining multiple chains will not work correctly, as they would have
distinctly different `dispatch` references and the different chains would effectively be
disconnected.

#### Further information

**Documentation**

- [Advanced: Middleware](../advanced/middleware)
- [API: applyMiddleware](../api/applymiddleware)

**Discussions**

- [#1051: Shortcomings of the current applyMiddleware and composing createStore](https://github.com/reduxjs/redux/issues/1051)
- [Understanding Redux Middleware](https://medium.com/@meagle/understanding-87566abcfb7a)
- [Exploring Redux Middleware](http://blog.krawaller.se/posts/exploring-redux-middleware/)

### How do I subscribe to only a portion of the state? Can I get the dispatched action as part of the subscription?

Redux provides a single `store.subscribe` method for notifying listeners that the store has updated.
Listener callbacks do not receive the current state as an argument—it is simply an indication that
_something_ has changed. The subscriber logic can then call `getState()` to get the current state
value.

This API is intended as a low-level primitive with no dependencies or complications, and can be used
to build higher-level subscription logic. UI bindings such as
[Presenter-middleware](#todo) can create a subscription for each connected component. It is also
possible to write functions that can intelligently compare the old state vs the new state, and
execute additional logic if certain pieces have changed.

The new state is not passed to the listeners in order to simplify implementing store enhancers such
as the Redux DevTools. In addition, subscribers are intended to react to the state value itself, not
the action. Middleware can be used if the action is important and needs to be handled specifically.

#### Further information

**Documentation**

- [Basics: Store](../basics/store)
- [API: Store](../api/store-api)

**Discussions**

- [#303: subscribe API with state as an argument](https://github.com/reduxjs/redux/issues/303)
- [#580: Is it possible to get action and state in store.subscribe?](https://github.com/reduxjs/redux/issues/580)
- [#922: Proposal: add subscribe to middleware API](https://github.com/reduxjs/redux/issues/922)
- [#1057: subscribe listener can get action param?](https://github.com/reduxjs/redux/issues/1057)
- [#1300: Redux is great but major feature is missing](https://github.com/reduxjs/redux/issues/1300)

**Libraries**

- [Redux Addons Catalog: Store Change Subscriptions](https://github.com/markerikson/redux-ecosystem-links/blob/master/store#store-change-subscriptions)

### How do I persist store state and restore it on the next launch?

Two mechanisms, depending on who owns the storage:

- **State the OS saves for you** (rotation + process death on Android/iOS):
  use `redux-kotlin-compose-saveable` — describe the slice worth keeping with
  a `StateSaver` and anchor it with `rememberSaveableState`. The snapshot
  rides Compose's `SaveableStateRegistry`, and on restore the library
  dispatches your restore action synchronously during composition, so the
  first frame already shows the rehydrated state.
- **State you persist yourself** (a database, files, a server session): load
  it before creating the store and seed it via `preloadedState` —
  `createStore(reducer, restoredState)` for the core store, or the
  `preloadedState` parameter on `createModelStore` /
  `createConcurrentModelStore` for routed `ModelState` stores, which overlays
  the restored models onto the declared defaults.

Don't restore by dispatching after the UI is up — the first frame renders the
initial state and then visibly jumps.

#### Further information

**Documentation**

- [Advanced: Saving state across rotation & process death](../advanced/compose-integration#saving-state-across-rotation--process-death)
- [API: createStore](../api/createstore)
- [Troubleshooting: State persistence & restore](../troubleshooting#state-persistence--restore)

**Example**

- [TaskFlow](https://github.com/reduxkotlin/redux-kotlin/tree/master/examples/taskflow) — durable
  domain state in SQLDelight + volatile UI state via `rememberSaveableState`.

---
id: reducers
title: Reducers
sidebar_label: Reducers
hide_title: true
---

# Redux FAQ: Reducers

## Table of Contents

- [How do I share state between two reducers? Do I have to use combineReducers?](#how-do-i-share-state-between-two-reducers-do-i-have-to-use-combinereducers)
- [Do I have to use the switch statement to handle actions?](#do-i-have-to-use-the-when-statement-to-handle-actions)

## Reducers

### How do I share state between two reducers? Can I use `combineReducers`?

The suggested structure for a Redux store is to split the state object into multiple “slices” or
“domains” by key, and provide a separate reducer function to manage each individual data slice. This
results in smaller, manageable reducer functions.

Many users later want to try to share data between two reducers, but find that they do not have
access to the needed substate in a particular reducer. There are several approaches that can be
used:

`combineReducers` is a commonly used function in JS Redux that aids in reducer composition. In
ReduxKotlin there is a `combineReducers` function, but is more limited. It only allows combining
reducers of the same state type.

- If a reducer needs to know data from another slice of state, the state tree shape may need to be
  reorganized so that a single reducer is handling more of the data.
- Handle the action in the root reducer

In general, remember that reducers are just functions—you can organize them and subdivide them any
way you want, and you are encouraged to break them down into smaller, reusable functions (“reducer
composition”). While you do so, you may pass a custom third argument from a parent reducer if a
child reducer needs additional data to calculate its next state. You just need to make sure that
together they follow the basic rules of reducers: `(state, action) -> newState`, and update state
immutably rather than mutating it directly.

#### Further information

**Documentation**

- [API: combineReducers](../api/combineReducers.md)

**Discussions**

- [Reddit: Why reducers?](https://www.reddit.com/r/androiddev/comments/8e1zcu/redux_on_android_why_reducers/)

### Do I have to use the `when` statement to handle actions?

No. You are welcome to use any approach you'd like to respond to an action in a reducer. The `when`
statement is a common approach, but it's fine to use `if` statements, a [reducible interface](TODO),
or to create a function that abstracts this away.

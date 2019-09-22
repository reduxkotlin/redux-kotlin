---
id: general
title: General
sidebar_label: General
hide_title: true
---

# Redux FAQ: General

## Table of Contents

- [When should I learn Redux?](#when-should-i-learn-redux)
- [When should I use Redux?](#when-should-i-use-redux)
- [Can Redux only be used with React?](#can-redux-only-be-used-with-react)
- [Do I need to have a particular build tool to use Redux?](#do-i-need-to-have-a-particular-build-tool-to-use-redux)

## General

### When should I learn ReduxKotlin?

Redux is a pattern for managing application state. If you do not have problems with state
management, you might find the benefits of Redux harder to understand. If your application becomes
so complex that you are confused about where state is stored or how state changes, then it is a good
time to learn Redux. Experiencing the complexity that Redux seeks to abstract is the best
preparation for effectively applying that abstraction to your work. As a new mobile engineer, I
would not recommend Redux as a starting point. Learning the more common idioms (MVP, MVVM, MVC) for
Android or iOS will help you understand Redux, and it's benefits more.

### When should I use ReduxKotlin?

The need to use Redux should not be taken for granted.

Redux on mobile is not a silver bullet that solves all problems. However a project that strictly
follows the principles and patterns it can be a very productive and testable pattern.

In general, use Redux when you have reasonable amounts of data changing over time, you need a single
source of truth.

However, it's also important to understand that using Redux comes with tradeoffs. It's not designed
to be the shortest or fastest way to write code. It's intended to help answer the question "When did
a certain slice of state change, and where did the data come from?", with predictable behavior. It
does so by asking you to follow specific constraints in your application: store your application's
state as plain data, describe changes as plain objects, and handle those changes with pure functions
that apply updates immutably. This is often the source of complaints about "boilerplate". These
constraints require effort on the part of a developer, but also open up a number of additional
possibilities (such as store persistence and synchronization).

In the end, Redux is just a tool. It's a great tool, and there are some great reasons to use it, but
there are also reasons you might not want to use it. Make informed decisions about your tools, and
understand the tradeoffs involved in each decision.

#### Further information

**Documentation**

- [Introduction: Motivation](../introduction/Motivation.md)

**Articles**
- [Lessons learned implementing Redux on Android](https://hackernoon.com/lessons-learned-implementing-redux-on-android-cba1bed40c41)

### Can Redux only be used with Jetpack Compose or SwiftUI?

Yes! Redux can be used as a data store for any UI layer. In the JS world the most common usage is
with React and React Native, which is a declarative UI framework similar in philosophy to Jetpack
Compose and SwiftUI. It is very early days for (especially) Jetpack Compose and SwiftUI, however a
working sample is available with
[MovieSwiftUI-Kotlin](https://github.com/reduxkotlin/MovieSwiftUI-Kotlin)


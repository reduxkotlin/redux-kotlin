---
id: examples
title: Examples
sidebar_label: Examples
hide_title: true
---

# Examples

ReduxKotlin includes a few examples currently and plans to port many of the JS Redux examples over.
You may also find the [JS examples](https://redux.js.org/introduction/examples) helpful

## Counter

Run the [Counter](https://github.com/reduxkotlin/redux-kotlin/tree/master/examples/counter) example:



```sh
git clone https://github.com/reduxkotlin/redux-kotlin.git

./gradlew examples:counter:installDebug

```
or:

Open the root project in Android Studio/Intellij. Select and run the configuration for the Android
example/counter example.

## Todos

Run the [Todos](https://github.com/reduxkotlin/redux-kotlin/tree/master/examples/todos) example:

```sh
git clone https://github.com/reduxkotlin/redux-kotlin.git

./gradlew examples:todos:installDebug

```
or:

Open the root project in Android Studio/Intellij. Select and run the configuration for the Android
example/counter example.

## NameGame

This is a multiplatform app for Android and iOS. It is a quiz on dog/cat breeds. This is a more
complete example of how to use Redux in a real application. Async actions such as network requests
and delays are demonstrated. Also used is the
[presenter-middleware](https://github.com/reduxkotlin/presenter-middleware) as a presentation layer.

Run the [NameGame](https://github.com/reduxkotlin/NameGameSampleApp) example:

```sh
git clone https://github.com/reduxkotlin/NameGameSampleApp.git

./gradlew android:installDebug

```
or:

Open the root project in Android Studio/Intellij. Select and run the configuration for the Android
example/counter example.

iOS:

Open the iOS/NameGame in XCode.


## ReadingList 

This is a multiplatform app for Android and iOS. It is a quiz on dog/cat breeds. This is a more
complete example of how to use Redux in a real application. Async actions such as network requests
and delays are demonstrated. Also used is the
[presenter-middleware](https://github.com/reduxkotlin/presenter-middleware) as a presentation layer.

Run the [ReadingList](https://github.com/reduxkotlin/ReadingListSampleApp) example:


```sh
git clone https://github.com/reduxkotlin/ReadingListSampleApp.git

./gradlew android:installDebug

```
or:

Open the root project in Android Studio/Intellij. Select and run the configuration for the Android
example/counter example.

iOS:

Open the iOS/ReadingList in XCode.


## MovieSwiftUI-Kotlin

Fork of an 100% SwiftUI app that uses shared Kotlin code for networking, preferences, reducers,
actions, and more.

Run the [MovieSwiftUI-Kotlin](https://github.com/reduxkotlin/MovieSwiftUI-Kotlin) example:

```sh
git clone https://github.com/reduxkotlin/MovieSwiftUI-Kotlin.git
```

Open the iOS/ReadingList in XCode.

A minimal Jetpack Compose screen is implemented.


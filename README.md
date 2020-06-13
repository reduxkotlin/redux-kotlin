# Redux-Kotlin

[![CircleCI](https://circleci.com/gh/reduxkotlin/redux-kotlin.svg?style=svg)](https://circleci.com/gh/reduxkotlin/redux-kotlin)

![badge][badge-android]
![badge][badge-native]
![badge][badge-js]
![badge][badge-jvm]
![badge][badge-linux]
![badge][badge-windows]
![badge][badge-mac]
![badge][badge-wasm]

A redux standard for Kotlin that supports multiplatform projects

## Misson Statement

Provide a standard redux implementation for Kotlin.  In doing so will foster a ecosystem of middleware, store enhancers, & dev tools.  These core values will guide descisions for the project:
* core redux-kotlin will be a minimal implementation that other libraries can build upon
* modular development (follow example of https://github.com/reduxjs)
* support for all platforms supported by Kotlin multiplatform (JVM, iOS, Native, JS, WASM)
* developed in open and enable discussion for all interested parties via open channels (slack, github, etc. TBD)
* not owned by a individual or company


Redux in Kotlin, and in mobile in particular, may differ a bit from javascript.  Many have found the basic pattern useful on Android & iOS leading to tens of opensource redux libraries in Kotlin, Java, and Swift, yet an ecosystem has yet to emerge.  A port of javascript redux is a good starting point for creating a standard and will aid in cross-pollination of middleware, store enhancers, & dev tools from the javascript world.  

Redux has proven helpful for state managment in mobile. A multiplatform Kotlin implementation & ecosystem will increase developer productivity and code reuse across platforms.

[Droidcon NYC Slides](https://www.slideshare.net/PatrickJackson14/reduxkotlinorg-droidcon-nyc-2019)
Video TBA

##  *** PLEASE FILL OUT THE [Redux on Mobile Survey](https://docs.google.com/forms/d/e/1FAIpQLScEQ9zGndU48AUeGKR6PPE13IqhIFmTL570wDodQUEilhwMzw/viewform?usp=sf_link) ***




__How to add to project:__

Artifacts are hosted on maven central.  They are published with gradle metadata, so you may need to enable with `enableFeaturePreview("GRADLE_METADATA")` in your settings.gradle file.  For multiplatform, add the following to your shared module:

```
kotlin {
  sourceSets {
        commonMain { //   <---  name may vary on your project
            dependencies {
                implementation "org.reduxkotlin:redux-kotlin-threadsafe:0.5.1"
            }
        }
 }
```

For JVM only:
```
  implementation "org.reduxkotlin:redux-kotlin-threadsafe-jvm:0.5.1"
```

*Non threadsafe store is available.  Typical usage will be with the threadsafe store. [More info read here](https://www.reduxkotlin.org/introduction/getting-started)


Usage is very similar to JS Redux and those docs will be useful https://redux.js.org/.  These docs are not an intro to Redux, and just documentation on Kotlin specific bits.  For more info on Redux in general, check out https://redux.js.org/.

__Create an AppState class__
```
  data class AppState(val user: User, val feed: List<Feed>)
```

__Create Reducers:__
```
  val reducer: Reducer<AppState> =  { state, action ->
    when (action) {
        is UserLoggedInAction -> state.copy(user = action.user)
        ...
    }
  }
```

__Create Middleware:__
There are a few ways to create middleware:

Using a curried function stored in a val/var:
```
  val loggingMiddleware: Middleware = 
          { store ->
              { next ->
                  { action ->
                        //log here
                        next(action)
                   }
               }
            }
```
Using a function:
```
  fun loggingMiddleware(store: Store) = { next: Dispatcher -> 
              { action: Any -> 
                     //log here
                     next(action)
               }
```

Using the convenience helper function `middleware`:
```
   val loggingMiddleware = middleware { store, next, action -> 
          //log here
          next(action)
          }
```

__Create a store__
```
  val store = createThreadSafeStore(reducer, AppState(user, listOf()), applyMiddleware(loggingMiddleware))
```

You then will have access to dispatch and subscribe functions from the `store`.

## Communication
Want to give feedback, contribute, or ask questions?

__\#redux__ slack channel in [kotlinlang](https://kotlinlang.slack.com)

Trello boards - https://trello.com/reduxkotlinorg

Or create an issue on [github](https://github.com/reduxkotlin/redux-kotlin/issues).

[badge-android]: http://img.shields.io/badge/platform-android-brightgreen.svg?style=flat
[badge-native]: http://img.shields.io/badge/platform-native-lightgrey.svg?style=flat	
[badge-native]: http://img.shields.io/badge/platform-native-lightgrey.svg?style=flat
[badge-js]: http://img.shields.io/badge/platform-js-yellow.svg?style=flat
[badge-js]: http://img.shields.io/badge/platform-js-yellow.svg?style=flat
[badge-jvm]: http://img.shields.io/badge/platform-jvm-orange.svg?style=flat
[badge-jvm]: http://img.shields.io/badge/platform-jvm-orange.svg?style=flat
[badge-linux]: http://img.shields.io/badge/platform-linux-important.svg?style=flat
[badge-linux]: http://img.shields.io/badge/platform-linux-important.svg?style=flat 
[badge-windows]: http://img.shields.io/badge/platform-windows-informational.svg?style=flat
[badge-windows]: http://img.shields.io/badge/platform-windows-informational.svg?style=flat
[badge-mac]: http://img.shields.io/badge/platform-macos-lightgrey.svg?style=flat
[badge-mac]: http://img.shields.io/badge/platform-macos-lightgrey.svg?style=flat
[badge-wasm]: https://img.shields.io/badge/platform-wasm-darkblue.svg?style=flat

---
id: multiplatform
title: Multiplatform
sidebar_label: Multiplatform
hide_title: true
---

# Redux FAQ: Multiplatform

## Table of Contents

- [When should I learn Redux?](#when-should-i-learn-redux)
- [When should I use Redux?](#when-should-i-use-redux)
- [Can Redux only be used with React?](#can-redux-only-be-used-with-react)
- [Do I need to have a particular build tool to use Redux?](#do-i-need-to-have-a-particular-build-tool-to-use-redux)

## Multiplatform

### What is "Multiplatform" Kotlin

<<<<<<< Updated upstream
Multiplatform Kotlin is the ability to compile Kotlin code to different targets. Available targets
are JVM, Native (iOS, WIN, Linux), Javascript, & Webassembly. This allows writing business logic,
networking, Redux code, & persistence in a shared module. The UI is then implemented leveraging the
platforms language and UI SDKs.
=======
Multiplatform Kotlin is the ability to compile Kotlin code to different targets.  Available targets are JVM, Native (iOS, WIN, Linux), Javascript, & Webassembly.  This allows writing business logic, networking, Redux code, & persistence in a shared module.  The UI is then implemented leveraging the platforms language and UI SDKs.  
>>>>>>> Stashed changes

#### Further information

- [Official Kotlin site](https://kotlinlang.org/docs/reference/multiplatform.html)
- [Examples of multiplatform projects using ReduxKotlin](../introduction/Examples.md)
- [Raywenderlich.com - Getting Started](https://www.raywenderlich.com/1022411-kotlin-multiplatform-project-for-android-and-ios-getting-started)

### Can I use existing JS Redux code?

<<<<<<< Updated upstream
It is not be possible to share the Javascript code with other platforms. It would need to be
re-written in Kotlin in order to share it with other platforms. In many cases this is very easy to
do given the similarities between Kotlin and Javascript.

### Can I use React with ReduxKotlin?

Yes - but this has not been proven with a sample yet. This is on the roadmap. In theory this should
work because the API for ReduxKotlin matches JS Redux. A minimal sample will be posted when
available. If interested in creating a sample or collaborating, see the "Community" Section at the
bottom of this page.

### Does compiling to Native for iOS include a VM?  Is there a lot of overhead?

Kotlin does not ship a VM to native. It compiles to a native executable with objC headers. KN does
include an
[automated memory management scheme](https://github.com/JetBrains/kotlin-native/blob/master/FAQ.md)
that does automatic reference counting and garbage collection. There does not appear to be a
significant performance or memory overhead, however I have not seen benchmarks. For more details,
checkout the
[Kotlin Native docs.](https://kotlinlang.org/docs/reference/native-overview.html)

### How do I structure a multiplatform kotlin project?

There some [examples](../introduction/Examples.md) that demonstrate Android and iOS sharing code.
JS examples will be added soon. Typically there is a shared module (or multiple shared modules) and
the platforms pull it in as a dependency. Redux looks like a very promising pattern for multiplatform
because the view layer can be very thin. Additionally Redux works well with
[Jetpack Compose](https://developer.android.com/jetpack/compose) and
[SwiftUI.](https://developer.apple.com/xcode/swiftui/)

### How is this related to the kotlin-redux wrapper?

ReduxKotlin.org and kotlin-redux wrapper are completed separate projects with different objectives.
ReduxKotlin is a port of Redux to pure Kotlin. Kotlin-redux is a kotlin wrapper around the JS Redux
library, which allows compiling to JS only.
=======
It is not be possible to share the Javascript code with other platforms.  It would need to be re-written in Kotlin in order to share it with other platforms.  In many cases this is very easy to do given the similarities between Kotlin and Javascript.

### Can I use React with ReduxKotlin?

Yes - but this has not been proven with a sample yet.  This is on the roadmap.  In theory this should work because the API for ReduxKotlin matches JS Redux.  A minimal sample will be posted when available.  If interested in creating a sample or collaborating, see the "Community" Section at the bottom of this page.

### Does compiling to Native for iOS include a VM?  Is there a lot of overhead?

Kotlin does not ship a VM to native.  It compiles to a native executable with objC headers.  KN does include an [automated memory management scheme](https://github.com/JetBrains/kotlin-native/blob/master/FAQ.md) that does automatic reference counting and garbage collection.  There does not appear to be a significant performance or memory overhead, however I have not seen benchmarks.
For more details, checkout the [Kotlin Native docs.](https://kotlinlang.org/docs/reference/native-overview.html)

### How do I structure a multiplatform kotlin project?

There some [examples](../introduction/Examples.md) that demonstrate Android and iOS sharing code.  JS examples will be added soon.  Typically there is a shared module (or multiple shared modules) and the platforms pull it in as a dependency.  Redux looks like a very promising pattern for multplatform because the view layer can be very thin.  Additionally Redux works well with [Jetpack Compose](https://developer.android.com/jetpack/compose) and [SwiftUI.](https://developer.apple.com/xcode/swiftui/)

### How is this related to the kotlin-redux wrapper?

ReduxKotlin.org and kotlin-redux wrapper are completed separate projects with different objectives.  ReduxKotlin is a port of Redux to pure Kotlin.  Kotlin-redux is a kotlin wrapper around the JS Redux library, which allows compiling to JS only.
>>>>>>> Stashed changes

#### Further information

-[kotlin-redux wrapper](https://github.com/JetBrains/kotlin-wrappers/tree/master/kotlin-redux)

### Are coroutines usable on Native/iOS?

<<<<<<< Updated upstream
Yes. All the samples in this project use coroutines with a UI dispatcher and they perform reasonable
well. However there is a limitation currently with coroutines on Native which only allows a single
thread. The Jetbrains team is actively working on a solution. For now injecting your shared code
with a coroutine context from the platform will allow you use a single threaded model now, and then
switch to a multithreaded model easily in the future.
=======
Yes.  All the samples in this project use coroutines with a UI dispatcher and they perform reasonable well.  However there is a limitation currently with coroutines on Native which only allows a single thread.  The Jetbrains team is actively working on a solution.  For now injecting your shared code with a coroutine context from the platform will allow you use a single threaded model now, and then switch to a multithreaded model easily in the future.
>>>>>>> Stashed changes

#### Further information

-[Github issue: Kotlinx.coroutines multithreaded support on native](https://github.com/Kotlin/kotlinx.coroutines/issues/462)
-[Deep dive into threading on Native by Kevin Galligan](https://medium.com/@kpgalligan/kotlin-native-stranger-threads-ep-2-208523d63c8f)
<<<<<<< Updated upstream
=======



>>>>>>> Stashed changes

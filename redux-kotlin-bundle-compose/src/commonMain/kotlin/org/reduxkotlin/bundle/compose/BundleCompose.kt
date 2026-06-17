package org.reduxkotlin.bundle.compose

// `redux-kotlin-bundle-compose` is a dependency-only aggregator: its public
// surface comes entirely from the `api(...)` exports declared in build.gradle.kts
// (the bundle + the Compose bindings), not from code in this module.
//
// Kotlin/Native still needs at least one source file per target to emit a klib —
// with an empty `commonMain` the `compileKotlin<Native>` tasks are NO-SOURCE, no
// klib lands in `build/libs/`, and `generateMetadataFileFor<Target>Publication`
// fails with FileNotFoundException. This internal marker gives every target a
// real compilation so publication succeeds.
internal const val BUNDLE_COMPOSE_MODULE: String = "redux-kotlin-bundle-compose"

# redux-kotlin-bom

A [Bill of Materials](https://docs.gradle.org/current/userguide/platforms.html)
(`java-platform`) that pins compatible versions for **every** published
redux-kotlin module. Import it once and then declare the modules you use without
repeating the version — the BOM keeps them aligned.

## Usage

```kotlin
dependencies {
    implementation(platform("org.reduxkotlin:redux-kotlin-bom:<version>"))

    // versions come from the BOM:
    implementation("org.reduxkotlin:redux-kotlin")
    implementation("org.reduxkotlin:redux-kotlin-concurrent")
    implementation("org.reduxkotlin:redux-kotlin-compose")
}
```

## Notes

- Covers the core, state-shape, compose, routing, and bundle modules.
- The **DevTools** modules are constrained too, but are **experimental** and
  exempt from the project's semver guarantees.
- A bundle ([`redux-kotlin-bundle`](../redux-kotlin-bundle) /
  [`redux-kotlin-bundle-compose`](../redux-kotlin-bundle-compose)) is the
  simplest entry point for most apps; reach for the BOM when you want à-la-carte
  modules kept in lockstep.

## See also

- [Getting started](https://reduxkotlin.org/introduction/getting-started)

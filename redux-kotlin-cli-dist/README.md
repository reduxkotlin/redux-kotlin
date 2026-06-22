# redux-kotlin-cli-dist

Unpublished repo tool. Packages the `rk` binary into a per-OS bundled-JRE app-image
and publishes it via JReleaser to the Homebrew tap and Scoop bucket on a tagged release.

## What it does

- `createDistributable` — builds a per-OS Compose app-image (macOS `.app`, Linux `rk/`, Windows `rk/`)
  with a bundled JRE, so end users need no local Java.
- `packageRkArchive` — zips the app-image into a distributable archive ready for JReleaser.
- JReleaser (workflow `.github/workflows/cli-release.yml`) picks up the archives on a tagged release
  and publishes them to:
  - Homebrew tap: `reduxkotlin/homebrew-tap` (`brew install reduxkotlin/tap/rk`)
  - Scoop bucket: `reduxkotlin/scoop-bucket` (`scoop install rk`)

## End-user install

```bash
# macOS / Linux
brew install reduxkotlin/tap/rk

# Windows
scoop bucket add reduxkotlin https://github.com/reduxkotlin/scoop-bucket
scoop install rk
```

No Java installation required — the JRE is bundled. The macOS bottle is **Apple Silicon
only** as of `1.0.0-alpha02` (Intel runners were dropped); Intel-Mac users build `rk` from
source via `:redux-kotlin-cli:installDist`.

## Developer notes

This module is not published to Maven Central. It is a `convention.control` Gradle project
that depends on `redux-kotlin-cli` and uses the Compose Multiplatform packaging tasks.
Run `./gradlew :redux-kotlin-cli-dist:packageRkArchive` to produce the archives locally.

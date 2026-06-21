# redux-kotlin-cli

`rk` — the unified redux-kotlin command-line tool. One binary, two groups:

- `rk devtools …` — inspect a running redux-kotlin app (action log, JSON diffs,
  per-store `.jsonl` captures) over the devtools bridge.
- `rk snapshot …` — render built-in / manifest Compose scenes to PNG with golden
  diffing. (Rendering *your own* app's screens stays a library use — depend on
  `redux-kotlin-snapshot` and call `yourRegistry.runCli(args)`.)

## Installation

### Package managers (recommended — no Java required)

Homebrew and Scoop builds bundle a JRE; no local JDK needed.

```sh
# macOS / Linux
brew install reduxkotlin/tap/rk

# Windows
scoop bucket add reduxkotlin https://github.com/reduxkotlin/scoop-bucket
scoop install rk
```

Available once the first tagged release is published.

### From source (any OS, needs JDK 17+)

```sh
./gradlew :redux-kotlin-cli:installDist
# binary: redux-kotlin-cli/build/install/rk/bin/rk
rk --help
rk --version
```

Add `redux-kotlin-cli/build/install/rk/bin` to your `PATH`, or symlink the binary.

## Commands

- **`rk devtools`** — inspect a running app: `serve` (start bridge receiver + capture), `stores`,
  `actions`, `diff`, `state`, `tail`. See [`../redux-kotlin-devtools-cli/README.md`](../redux-kotlin-devtools-cli/README.md).
- **`rk snapshot`** — render built-in or manifest Compose scenes to PNG with golden diffing.
  See [`../redux-kotlin-snapshot/README.md`](../redux-kotlin-snapshot/README.md).

> **Rendering your own app's screens** is a library use: depend on `redux-kotlin-snapshot` and
> call `yourRegistry.runCli(args)` from your own `main` (then `exitProcess(0)` — Skiko leaves
> non-daemon threads alive). The `rk` binary only renders its built-in demo scenes.

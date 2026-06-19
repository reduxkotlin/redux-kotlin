# redux-kotlin-cli

`rk` — the unified redux-kotlin command-line tool. One binary, two groups:

- `rk devtools …` — inspect a running redux-kotlin app (action log, JSON diffs,
  per-store `.jsonl` captures) over the devtools bridge.
- `rk snapshot …` — render built-in / manifest Compose scenes to PNG with golden
  diffing. (Rendering *your own* app's screens stays a library use — depend on
  `redux-kotlin-snapshot` and call `yourRegistry.runCli(args)`.)

**Unpublished today** — build from this repo (needs **JDK 17+**):

```
./gradlew :redux-kotlin-cli:installDist
# binary: redux-kotlin-cli/build/install/rk/bin/rk
rk --help
rk --version
```

Add that `bin/` directory to your `PATH`, or symlink the binary. Phase 2 will
publish self-contained per-OS builds via Homebrew/Scoop (`brew install
reduxkotlin/tap/rk`) with a bundled JRE — no Java required.

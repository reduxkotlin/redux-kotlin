# redux-kotlin-devtools-cli

This module is the **library** behind `rk devtools` — it is no longer an
installable binary of its own. The bridge receiver and capture queries
(`serve`/`stores`/`actions`/`diff`/`state`/`tail`) are exposed as clikt
subcommands that the unified `rk` CLI mounts under `rk devtools`.

Install the unified `rk` binary from the repository:

```
./gradlew :redux-kotlin-cli:installDist
# binary:
redux-kotlin-cli/build/install/rk/bin/rk
```

### Requires Java 17+

The tool is compiled to Java 17 bytecode, so the launcher needs a **JDK 17 or
newer** on `JAVA_HOME`/`PATH`. The build itself is pinned to JDK 17 via a Gradle
toolchain (auto-provisioned), so `installDist` is deterministic regardless of
your default Java. The repo ships a [`.sdkmanrc`](../.sdkmanrc) pinning Temurin
17 — run `sdk env` in the repo root to select it.

If you launch `rk` with an older Java it fails fast with a clear
message instead of an `UnsupportedClassVersionError`. To pick a JDK explicitly:

```
JAVA_HOME=/path/to/jdk17+ rk devtools --help
# macOS, if registered: JAVA_HOME=$(/usr/libexec/java_home -v 17) rk devtools --help
```

## Subcommands

| Command | What it does |
|---|---|
| `rk devtools serve` | Host the receiver on `127.0.0.1:9090`; write captures. Options: `--port`, `--host`, `--token`, `--out`, `--ui` (also launch the GUI monitor). |
| `rk devtools stores` | List captured stores (`clientId::storeInstanceId`). |
| `rk devtools actions` | Print the action log. Filters: `--store`, `--type '*Card*'`, `--since`/`--until`, `--last N`, `--format actions\|diff\|full`, `--pretty`. |
| `rk devtools diff` | Same filters; each line includes the per-field JSON diff. |
| `rk devtools state --at <id>` | Full state snapshot at an actionId. |
| `rk devtools tail [--follow]` | Recent actions; `--follow` polls live. |

## Typical loop

```
rk devtools serve            # background terminal, before launching the app
# … run / reproduce in the app …
rk devtools stores
rk devtools actions --last 30
rk devtools diff --type '*Failed*' --last 5
rk devtools state --at 42
```

Captures are plain `.jsonl` — decode them programmatically with the bridge's
`decodeRecording` / `decodeRecordingLenient`.

## See also

- Integration guide: [docs/devtools.md](../docs/devtools.md) ·
  [website DevTools page](https://www.reduxkotlin.org/advanced/devtools)
- Agent-oriented walkthrough: [docs/agent/references/devtools.md](../docs/agent/references/devtools.md)
- GUI sibling: [`redux-kotlin-devtools-standalone`](../redux-kotlin-devtools-standalone)

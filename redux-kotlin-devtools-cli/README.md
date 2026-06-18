# redux-kotlin-devtools-cli

`rk-devtools` — a clikt terminal tool that wraps the standalone monitor's
bridge receiver and adds capture queries. It receives the app's event stream
over the [bridge](../redux-kotlin-devtools-bridge), writes one
`<storeKey>.jsonl` capture per store into `.rk-devtools/`, and answers
questions like "what actions fired before the crash?" or "which field changed
between actions 40 and 50?" — ideal for agents, scripts, and headless
debugging.

**Unpublished.** This is a developer tool, not a Maven artifact — install it
from the repository:

```
./gradlew :redux-kotlin-devtools-cli:installDist
# binary:
redux-kotlin-devtools-cli/build/install/rk-devtools/bin/rk-devtools
```

### Requires Java 17+

The tool is compiled to Java 17 bytecode, so the launcher needs a **JDK 17 or
newer** on `JAVA_HOME`/`PATH`. The build itself is pinned to JDK 17 via a Gradle
toolchain (auto-provisioned), so `installDist` is deterministic regardless of
your default Java. The repo ships a [`.sdkmanrc`](../.sdkmanrc) pinning Temurin
17 — run `sdk env` in the repo root to select it.

If you launch `rk-devtools` with an older Java it fails fast with a clear
message instead of an `UnsupportedClassVersionError`. To pick a JDK explicitly:

```
JAVA_HOME=/path/to/jdk17+ rk-devtools --help
# macOS, if registered: JAVA_HOME=$(/usr/libexec/java_home -v 17) rk-devtools --help
```

## Subcommands

| Command | What it does |
|---|---|
| `serve` | Host the receiver on `127.0.0.1:9090`; write captures. Options: `--port`, `--host`, `--token`, `--out`, `--ui` (also launch the GUI monitor). |
| `stores` | List captured stores (`clientId::storeInstanceId`). |
| `actions` | Print the action log. Filters: `--store`, `--type '*Card*'`, `--since`/`--until`, `--last N`, `--format actions\|diff\|full`, `--pretty`. |
| `diff` | Same filters; each line includes the per-field JSON diff. |
| `state --at <id>` | Full state snapshot at an actionId. |
| `tail [--follow]` | Recent actions; `--follow` polls live. |

## Typical loop

```
rk-devtools serve            # background terminal, before launching the app
# … run / reproduce in the app …
rk-devtools stores
rk-devtools actions --last 30
rk-devtools diff --type '*Failed*' --last 5
rk-devtools state --at 42
```

Captures are plain `.jsonl` — decode them programmatically with the bridge's
`decodeRecording` / `decodeRecordingLenient`.

## See also

- Integration guide: [docs/devtools.md](../docs/devtools.md) ·
  [website DevTools page](https://www.reduxkotlin.org/advanced/devtools)
- Agent-oriented walkthrough: [docs/agent/references/devtools.md](../docs/agent/references/devtools.md)
- GUI sibling: [`redux-kotlin-devtools-standalone`](../redux-kotlin-devtools-standalone)

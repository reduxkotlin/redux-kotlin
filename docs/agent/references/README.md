# redux-kotlin agent reference set (T1)

Single-source, per-concern guides for building with redux-kotlin. `AGENTS.md` (Phase 1) and the
Claude skill (Phase 2) assemble from these — edit knowledge here, not in copies.

Anchor convention: source references are written `repo-relative-path → Symbol` (in backticks) and are
checked to resolve (path exists, symbol present). See [_template.md](./_template.md).

| Concern | Guide | Status |
|---|---|---|
| Add a feature slice | [feature-slice.md](./feature-slice.md) | ✅ |
| DevTools debugging loop | [devtools.md](./devtools.md) | ✅ |
| Store setup & topology | [store-setup.md](./store-setup.md) | ✅ |
| Compose binding (Rule C) | [compose-binding.md](./compose-binding.md) | ✅ |
| Effects + sync (Rule E) | [effects-sync.md](./effects-sync.md) | ✅ |
| Testing & the verify loop | [testing.md](./testing.md) | ✅ |
| The 5 platform shims | [platform-shims.md](./platform-shims.md) | ✅ |
| Modularization | [modularization.md](./modularization.md) | ✅ |

Tiers: **T0** = rules card + module map + commands (assembled into `AGENTS.md`). **T1** = these guides.
**T2** = [`examples/taskflow/ARCHITECTURE.md`](../../../examples/taskflow/ARCHITECTURE.md) + committed `.api` dumps.

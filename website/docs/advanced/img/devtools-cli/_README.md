# DevTools CLI screenshots

These PNGs are **labelled placeholders** referenced by
`../DevToolsCLITutorial.md` with doc-relative paths (`./img/devtools-cli/*.png`)
so they render both on GitHub and on the Docusaurus site. Replace each with a
real capture against the TaskFlow sample, keeping the same filename:

| File | Shot |
|---|---|
| `00-stuck-card.png` | TaskFlow board, a card stuck on "Saving…" |
| `01-help.png` | `rk-devtools --help` |
| `02-serve-waiting.png` | `rk-devtools serve` waiting for a client |
| `03-stores.png` | `rk-devtools stores` listing captured stores |
| `04-actions-filtered.png` | `rk-devtools actions --type '*Sync*' --last 5` |
| `05-diff.png` | `rk-devtools diff --type 'SyncFailed' --last 1` |
| `06-tail-follow.png` | `rk-devtools tail --follow` streaming live |

Keep terminal shots dark-theme, ~100 cols, trimmed to the relevant output.

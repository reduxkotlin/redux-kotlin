# DevTools CLI screenshots

These PNGs are **labelled placeholders** referenced by
`../DevToolsCLITutorial.md` with doc-relative paths (`./img/devtools-cli/*.png`)
so they render both on GitHub and on the Docusaurus site. Replace each with a
real capture against the TaskFlow sample, keeping the same filename:

Reproduce with Settings → failure-rate at 100%, then move a card (it rolls back).

| File | Shot |
|---|---|
| `00-stuck-card.png` | TaskFlow board after a rejected, rolled-back card move |
| `01-help.png` | `rk-devtools --help` |
| `02-serve-waiting.png` | `rk-devtools serve` waiting for a client |
| `03-stores.png` | `rk-devtools stores` → `taskflow::TaskFlow`, `taskflow::TaskFlow-root` |
| `04-actions-filtered.png` | `rk-devtools actions --type '*Card*' --last 5` |
| `05-diff.png` | `rk-devtools diff --store taskflow::TaskFlow --type 'CardOpFailed' --last 1` |
| `06-tail-follow.png` | `rk-devtools tail --follow` streaming live |

Keep terminal shots dark-theme, ~100 cols, trimmed to the relevant output.

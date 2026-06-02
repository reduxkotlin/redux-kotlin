# Docs Site UI Kit — reduxkotlin.org

A high-fidelity recreation of the **reduxkotlin.org** documentation site, which runs on
[Docusaurus](https://docusaurus.io) (classic preset + Infima). It is the canonical brand
surface — most visitors meet ReduxKotlin here first.

## What it recreates
- The marketing **homepage** (`src/pages/index.tsx`): hero with the gradient logo, three
  feature cards (Multiplatform / Predictable / Centralized), the "ReduxKotlin Extensions"
  library grid, and the two-up survey section.
- A **documentation page** (the "Three Principles" guide) with the real Docusaurus three-column
  layout: collapsible sidebar nav, markdown content, Kotlin code blocks, and a right-hand TOC.
- The Docusaurus **navbar** (sticky, frosted white, blue active links, search, GitHub) and the
  **dark footer**.

All copy is lifted verbatim from the repository so tone and wording are accurate.

## Run it
Open `index.html`. Click **Get Started →** or any navbar item to move between the home and doc
views; the sidebar items and library cards are interactive.

## Files
| File | Role |
|---|---|
| `index.html` | Entry point — loads React + Babel and all components. |
| `Navbar.jsx` | Sticky frosted navbar with brand, links, search, GitHub. |
| `HomePage.jsx` | Hero, feature cards, extensions grid, survey. |
| `DocPage.jsx` | Doc layout: sidebar tree, content, `CodeBlock`, TOC. |
| `Footer.jsx` | Dark three-column footer. |
| `app.jsx` | Tiny client router (home ⇄ doc). |

## Fidelity notes
- Colors and type come from the shared `colors_and_type.css`. Primary blue `#137AF9` matches
  the site's `--ifm-color-primary`; the hero uses the real `linear-gradient(180deg, lightest-blue → transparent)`.
- Code blocks use GitHub-prism token colors (the site's light Prism theme).
- Fonts are the design-system substitutes (Roboto Flex / JetBrains Mono); the live site ships
  the Infima system font stack. See the root README Caveats.
- Search and GitHub are cosmetic (no real index / navigation off-site).

Source: https://github.com/reduxkotlin/redux-kotlin/tree/master/website

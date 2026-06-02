# ReduxKotlin Design System

A design system for **[ReduxKotlin](https://reduxkotlin.org)** — a predictable state
container for Kotlin apps, and a redux standard built for Kotlin Multiplatform.

This folder gives a design agent everything needed to produce well-branded ReduxKotlin
interfaces and assets: brand colors, type, logos, icon guidance, foundational CSS tokens,
and high-fidelity UI kits recreating the real product surfaces.

---

## 1. Product context

ReduxKotlin is an **open-source developer library**, not a commercial product. It is a
minimal, faithful port of JavaScript [Redux](https://redux.js.org/) to Kotlin, designed so
the same predictable state-management pattern works across **every platform Kotlin targets**
— JVM, Android, iOS/Native, JS, and WASM.

> *"Provide a standard redux implementation for Kotlin… core redux-kotlin will be a minimal
> implementation that other libraries can build upon."* — Mission statement

The project deliberately mirrors `reduxjs`: a tiny core plus a growing ecosystem of
middleware, store enhancers, and dev tools. It is community-owned (not owned by an individual
or company) and coordinated through the Kotlin Slack `#redux` channel and GitHub.

**The three principles** (lifted straight from Redux, the spine of all messaging):
1. **Single source of truth** — whole-app state lives in one store.
2. **State is read-only** — the only way to change it is to dispatch an action.
3. **Changes are made with pure functions** — reducers are plain functions `(state, action) -> state`.

### Surfaces represented in this design system
ReduxKotlin's "product" is really two things, and this system covers both:

| Surface | What it is | UI kit |
|---|---|---|
| **Documentation site** (`reduxkotlin.org`) | A Docusaurus site: marketing homepage + API/guide docs. The canonical brand surface. | `ui_kits/docs-site/` |
| **Sample app (Compose)** | The library's actual job: managing state in real apps. Represented by a Material 3 Compose-style counter/todo demo — the canonical "redux example." | `ui_kits/sample-app/` |
| **In-app DevTools** | Developer tooling that inspects a live store from inside the app — action log, state tree, diffs, pipeline. A Material 3 Expressive, animated mobile surface. | `ui_kits/devtools/` |

### Brand lineage
- The **logo** is the classic Redux three-arc swirl, but rendered in ReduxKotlin's own
  **orange→magenta gradient** (`#F98909 → #C858BC`) rather than Redux's heritage purple
  (`#764ABC`). The gradient echoes the Kotlin brand's warm multi-stop logo.
- The **web** uses a confident **primary blue `#137AF9`** for links, buttons and accents.
- Net effect: a heritage purple mark, a warm gradient signature, and a bright blue UI accent.

### Sources used to build this system
Everything here was derived from the official repository (read these to go deeper):
- **Repo:** https://github.com/reduxkotlin/redux-kotlin (branch `master`)
  - `website/` — the Docusaurus site (homepage `src/pages/index.tsx`, theme `src/css/custom.css`, brand color in `siteConfig`)
  - `website/static/img/` — all logos and feature icons (imported into `assets/`)
  - `website/docs/**` — documentation copy (tone & voice reference)
  - root `README.md` / `CLAUDE.md` — mission, API usage, code samples
- **Live site:** https://reduxkotlin.org
- **Ecosystem repos** worth exploring for deeper recreations:
  - https://github.com/reduxkotlin/redux-kotlin-compose (Jetpack Compose bindings)
  - https://github.com/reduxkotlin/redux-kotlin-thunk (async middleware)
  - https://github.com/reduxkotlin/ReadingListSampleApp (real iOS+Android sample)

> The reader is encouraged to browse these repositories directly to recreate designs with
> higher fidelity than this snapshot captures.

### A note on Material 3
Per the brief, this system leans into **Material 3 (expressive)** — the latest Material
guidance — for app-surface components, since ReduxKotlin's natural home is Android/Compose.
The documentation surface stays faithful to the real Docusaurus site. The two share one color
and type foundation (`colors_and_type.css`).

---

## 2. Content fundamentals

ReduxKotlin's voice is **the Redux docs voice, ported to Kotlin**: precise, friendly,
teacherly, and confident that the pattern is simple once it clicks. It never oversells.

**Tone & vibe**
- **Explanatory, not promotional.** Docs read like a patient mentor. Frequent reassurance
  that there's "no magic" — *"notice how we don't introduce any magic?"*, *"nothing magical
  about it—it's just a function."*
- **Developer-to-developer.** Assumes you can read Kotlin. Code is the primary medium; prose
  exists to frame code.
- **Calm confidence.** *"That's it! Now you know what Redux is all about."* Big ideas are
  deflated to something small and learnable.

**Person & address**
- **"You"** for the reader (*"you write applications that behave consistently"*).
- **"We"** for the project/community and when walking through code together (*"we write
  smaller functions"*, *"we're always looking for ways to improve"*).
- Imperative for instructions (*"Create an AppState class", "Create Reducers", "Create a store"*).

**Casing & formatting**
- **Sentence case** everywhere — headings, buttons, nav. (*"Getting Started", "Get Started →",
  "Core Concepts"*). Title Case appears only in proper nouns and doc titles.
- **Bold** is used to spotlight the load-bearing phrase in a sentence
  (*"behave consistently", "easy to test", "The only way to change the state…"*).
- Short paragraphs, one idea each, almost always followed by a fenced code block.
- The **`→` arrow** is a recurring CTA flourish (*"Get Started →"*).

**Vocabulary** — a small, consistent glossary used precisely: *store, state, action,
reducer, dispatch, middleware, store enhancer, single source of truth, multiplatform.*
Never "redo/undo magic," never marketing adjectives stacked up.

**Emoji:** none. The brand does not use emoji in product or docs. Don't introduce them.

**Example specimens** (real copy, safe to reuse):
- Tagline: *"A redux standard for Kotlin that supports multiplatform projects"*
- Hero subtitle: *"A predictable state container for Kotlin apps."*
- Feature blurb: *"Redux helps you write applications that **behave consistently** and are **easy to test**."*
- Feature blurb: *"ReduxKotlin is written with multiplatform as the top priority."*

---

## 3. Visual foundations

The full token set lives in **`colors_and_type.css`**. Highlights and the rules of the road:

**Color**
- **Primary — blue `#137AF9`.** The UI workhorse: links, primary buttons, focus, selection.
  Comes with a 7-step tint/shade ramp and a dark-mode lighter variant (`#4A96FB`).
- **Signature gradient — `#C858BC → #F98909`** (magenta→orange, ~60°). This is the *logo*
  gradient; use it sparingly and with intent — hero accents, the logo, a single gradient
  headline, an active-tab underline. Never as a full-page wash, never behind body text.
- **Heritage purple `#764ABC`** — the base Redux mark color; use for "Redux lineage" moments.
- **Material 3 roles:** primary = blue, **secondary = magenta**, **tertiary = orange** — the
  three brand hues map cleanly onto M3's three accent roles, plus full surface/container/outline
  tonal sets for both light and dark. Error = `#BA1A1A`, success = `#1F8A4C`.
- **Neutrals:** a cool, slightly blue-tinted slate ramp (`--rk-slate-*`) for a developer feel,
  not warm grays.
- **Imagery vibe:** flat, bright, high-contrast. The brand has almost no photography; it is
  line icons + logo + code. No grain, no duotone, no moody filters.

**Type**
- **Sans / UI:** `Roboto Flex` — the Material 3 brand font. *(Substitution — see Caveats; the
  live Docusaurus site ships the system font stack. Roboto Flex was chosen to honor the M3 brief.)*
- **Mono:** `JetBrains Mono` — used for all code, **and** as an eyebrow/label accent font
  (uppercase, tracked). This is the system's most distinctive type move: JetBrains makes
  Kotlin, so a mono eyebrow reads instantly "developer tool." Code is a first-class citizen.
- **Scale:** the full M3 type scale (display → headline → title → body → label) is tokenized.
- **Headlines:** medium/semibold weight, slightly negative tracking. **Body:** 16px/24.

**Shape, elevation, motion**
- **Corners:** M3 expressive scale — `xs 4 · sm 8 · md 12 · lg 16 · xl 28 · 2xl 36 · full`.
  App cards and dialogs use the *generous* end (16–28px); docs cards use 8px (the real site uses `0.5rem`).
- **Cards:** soft, low-contrast tonal surfaces with a hairline `--md-outline-variant` border;
  shadow only on raised/interactive elements. No heavy drop shadows, no colored left-border cards.
- **Elevation:** 5-step M3 shadow scale (`--elev-1`…`--elev-5`), soft and neutral-tinted.
- **Hover:** docs links shift to a darker blue; M3 surfaces gain a subtle state-layer (8% tint
  of the role color) — implemented as a translucent overlay, not a color swap.
- **Press:** M3 ripple feel — a brief scale-down (~0.97) plus a stronger state layer (12%).
- **Focus:** 2px solid focus ring in the role color, offset 2px.
- **Borders:** hairline (1px) `--md-outline-variant`; dividers `--md-surface-variant`.
- **Transparency/blur:** used lightly — state-layer overlays and the occasional frosted app bar
  (`backdrop-filter: blur(12px)` over a translucent surface). Not a glassmorphism brand.
- **Backgrounds:** mostly flat surfaces. The one signature texture is the gradient, used as a
  large soft radial/linear glow behind heroes at low opacity — never a busy pattern.
- **Animation:** M3 emphasized easing `cubic-bezier(0.3,0,0.1,1)`; a gentle spatial overshoot
  `cubic-bezier(0.34,1.36,0.64,1)` for expressive enter transitions. Durations 150/250/400ms.
  Fades and slides, no bounces beyond the slight expressive overshoot.
- **Layout:** 4px spacing grid; content max-width ~960–1100px for docs; generous whitespace.

---

## 4. Iconography

ReduxKotlin has **no bespoke icon set**. Its real-world icon usage is a small, pragmatic mix
— all of which is copied into `assets/`:

- **The logo / brand mark** — `assets/reduxkotlin-logo.svg` (and `.png`): the Redux three-arc
  swirl in the orange→magenta gradient. The heritage purple Redux mark is in
  `assets/redux-mark.svg` (and `redux-mark-white.svg` for dark backgrounds).
- **Homepage feature icons** — a handful of **black line/solid SVGs** from
  [The Noun Project](https://thenounproject.com) and Font Awesome, all in a simple monochrome
  style: `icon-multiplatform.png` (devices), `icon-check.svg`, `icon-cubes.svg`,
  `icon-cogs.svg`, `icon-debugging.svg`.
- **GitHub mark** — `assets/icon-github.svg` (used in the nav, recolored white).
- **Favicon** — `assets/favicon.ico`.

**Approach & rules**
- Style is **simple monochrome line/solid icons**, ~60px on the homepage, recolored via
  `currentColor` where SVG allows. No filled illustrative icons, no two-tone, no emoji, no
  unicode-glyph icons.
- For the **Material 3 app surfaces**, use **[Material Symbols](https://fonts.google.com/icons)**
  (Rounded, weight 400) from the Google Fonts CDN — the canonical M3 icon set. This is a
  documented choice to match the M3 brief; the marketing site itself predates it.
  ```html
  <link rel="stylesheet" href="https://fonts.googleapis.com/css2?family=Material+Symbols+Rounded:opsz,wght,FILL,GRAD@24,400,0,0" />
  <span class="material-symbols-rounded">add</span>
  ```
- **Never hand-draw new SVG icons.** Reuse the copied assets, or pull from Material Symbols.

---

## 5. Index — what's in this folder

| Path | What it is |
|---|---|
| `README.md` | This file — context, content & visual foundations, iconography, index. |
| `SKILL.md` | Agent-Skill manifest so this system can be used in Claude Code. |
| `colors_and_type.css` | All color + type + shape + motion tokens (light & dark). Load globally. |
| `assets/` | Logos (ReduxKotlin gradient mark, Redux purple mark), feature icons, GitHub mark, favicon. |
| `preview/` | Small HTML cards that populate the Design System tab (colors, type, components…). |
| `ui_kits/docs-site/` | High-fidelity recreation of **reduxkotlin.org** (Docusaurus homepage + a doc page). |
| `ui_kits/sample-app/` | Material 3 (expressive) Compose-style **sample app** demoing redux state. |
| `ui_kits/devtools/` | **In-App Redux DevTools** — animated M3 Expressive bottom-sheet inspector (the flagship tooling surface). |

**UI kits** each contain `README.md`, `index.html` (interactive demo), and small JSX components.

---

*Built from the open-source repository at https://github.com/reduxkotlin/redux-kotlin —
explore it directly to push fidelity further.*

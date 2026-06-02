---
name: reduxkotlin-design
description: Use this skill to generate well-branded interfaces and assets for ReduxKotlin, either for production or throwaway prototypes/mocks/etc. Contains essential design guidelines, colors, type, fonts, assets, and UI kit components for prototyping.
user-invocable: true
---

# ReduxKotlin Design System

Read the `README.md` file within this skill, and explore the other available files.

If creating visual artifacts (slides, mocks, throwaway prototypes, etc), copy assets out and
create static HTML files for the user to view. If working on production code, you can copy
assets and read the rules here to become an expert in designing with this brand.

If the user invokes this skill without any other guidance, ask them what they want to build or
design, ask some questions, and act as an expert designer who outputs HTML artifacts _or_
production code, depending on the need.

## Quick map
- `README.md` — full brand context: product, content fundamentals, visual foundations, iconography, index.
- `colors_and_type.css` — load this first for all color / type / shape / motion tokens (light + dark).
- `assets/` — logos (gradient ReduxKotlin mark, heritage purple Redux mark), feature icons, GitHub mark, favicon.
- `preview/` — small specimen cards (colors, type, spacing, components, brand).
- `ui_kits/docs-site/` — recreation of reduxkotlin.org (Docusaurus). Use for docs/marketing surfaces.
- `ui_kits/sample-app/` — Material 3 redux demo app + DevTools. Use for Android/Compose app surfaces.
- `ui_kits/devtools/` — In-App Redux DevTools: animated M3 Expressive bottom-sheet inspector. Use for tooling surfaces.

## The 30-second brand
- **Primary blue `#137AF9`** for UI accents, links, buttons. **Secondary magenta**, **tertiary orange** (Material 3 roles).
- **Signature gradient `#C858BC → #F98909`** (the logo) — use sparingly for hero accents only.
- **Roboto Flex** for UI/headlines, **JetBrains Mono** for code and tracked uppercase eyebrows.
- Material 3 (expressive) shapes, elevation, and motion. Calm, precise, developer-to-developer voice. No emoji.

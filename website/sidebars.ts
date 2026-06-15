import type {SidebarsConfig} from '@docusaurus/plugin-content-docs';

// Translated from the v1 website/sidebars.json. Doc IDs in Docusaurus 3 are
// prefixed by the file's directory path relative to `docs/`. The `id:`
// frontmatter only overrides the filename portion.
//
// Twelve v1 sidebar references pointed at docs that never existed on disk
// (e.g. `advanced/next-steps`, several `faq/*` and
// `api/combinereducers / bindactioncreators`). Docusaurus 3 fails the build
// on dangling sidebar refs, so those entries were culled.
//
// Two duplicate-id pairs were resolved by renaming one side of each:
//   faq/Reducers.md: id: reducers -> id: reducers-faq
//   api/Store.md:    id: store    -> id: store-api
const sidebars: SidebarsConfig = {
  docs: [
    {
      type: 'category',
      label: 'Introduction',
      collapsed: false,
      items: [
        'introduction/getting-started',
        'introduction/three-principles',
        'introduction/motivation',
        'introduction/core-concepts',
        'introduction/learning-resources',
        'introduction/ecosystem',
        'introduction/examples',
        'introduction/threading',
      ],
    },
    {
      type: 'category',
      label: 'AI Agents',
      items: [
        'ai-agents/building-with-ai-agents',
      ],
    },
    {
      type: 'category',
      label: 'Basic Tutorial',
      items: [
        'basics/basic-tutorial',
        'basics/actions',
        'basics/reducers',
        'basics/store',
        'basics/data-flow',
      ],
    },
    {
      type: 'category',
      label: 'Advanced',
      items: [
        'advanced/advanced-tutorial',
        'advanced/async-actions',
        'advanced/async-flow',
        'advanced/middleware',
        'advanced/granular-subscriptions',
        'advanced/store-registry',
        'advanced/multimodel',
        'advanced/routing',
        'advanced/concurrent-store',
        'advanced/bundle',
        'advanced/compose-integration',
        'advanced/devtools',
        'advanced/devtools-cli-tutorial',
      ],
    },
    {
      type: 'category',
      label: 'FAQ',
      items: [
        'faq',
        'faq/general',
        'faq/reducers-faq',
        'faq/store-setup',
        'faq/multiplatform',
      ],
    },
    {
      type: 'category',
      label: 'Other',
      items: [
        'glossary',
        'troubleshooting',
        'feedback',
      ],
    },
    {
      type: 'category',
      label: 'API Reference',
      items: [
        'api/api-reference',
        'api/store-api',
        'api/createstore',
        'api/createthreadsafestore',
        'api/createsamethreadenforcedstore',
        'api/applymiddleware',
        'api/compose',
      ],
    },
  ],
};

export default sidebars;

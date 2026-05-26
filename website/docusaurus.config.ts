import {themes as prismThemes} from 'prism-react-renderer';
import type {Config} from '@docusaurus/types';
import type * as Preset from '@docusaurus/preset-classic';

const config: Config = {
  title: 'ReduxKotlin',
  tagline: 'A redux standard for Kotlin that supports multiplatform projects',
  favicon: 'img/favicon.ico',

  url: 'https://reduxkotlin.org',
  baseUrl: '/',

  organizationName: 'reduxkotlin',
  projectName: 'redux-kotlin',

  trailingSlash: false,
  onBrokenLinks: 'throw',
  onBrokenMarkdownLinks: 'throw',

  i18n: {
    defaultLocale: 'en',
    locales: ['en'],
  },

  future: {
    v4: true,
  },

  presets: [
    [
      'classic',
      {
        docs: {
          // The v1 site served docs at "/" (siteConfig.js had `docsUrl: ""`).
          // Preserve that URL shape so the 142-rule _redirects file and every
          // inbound external link continues to resolve.
          routeBasePath: '/',
          path: 'docs',
          sidebarPath: './sidebars.ts',
          editUrl: 'https://github.com/reduxkotlin/redux-kotlin/tree/master/website/',
        },
        blog: false,
        theme: {
          customCss: './src/css/custom.css',
        },
        // TODO: re-enable once a GA4 measurement ID is provisioned for
        // reduxkotlin.org. The previous UA-130598673-1 (GA3) was retired by
        // Google in July 2024.
        // gtag: {
        //   trackingID: 'G-XXXXXXXXXX',
        //   anonymizeIP: true,
        // },
      } satisfies Preset.Options,
    ],
  ],

  themes: [
    [
      '@easyops-cn/docusaurus-search-local',
      {
        hashed: true,
        indexDocs: true,
        indexBlog: false,
        docsRouteBasePath: '/',
      },
    ],
  ],

  themeConfig: {
    image: 'img/reduxkotlin.png',
    navbar: {
      title: 'ReduxKotlin',
      logo: {
        alt: 'ReduxKotlin Logo',
        src: 'img/reduxkotlin.svg',
      },
      items: [
        {to: '/introduction/getting-started', label: 'Getting Started', position: 'left'},
        {to: '/api', label: 'API', position: 'left'},
        {to: '/faq', label: 'FAQ', position: 'left'},
        {
          href: 'https://github.com/reduxkotlin/redux-kotlin',
          label: 'GitHub',
          position: 'right',
        },
      ],
    },
    footer: {
      style: 'dark',
      links: [
        {
          title: 'Docs',
          items: [
            {label: 'Getting Started', to: '/introduction/getting-started'},
            {label: 'API Reference', to: '/api'},
            {label: 'FAQ', to: '/faq'},
          ],
        },
        {
          title: 'Community',
          items: [
            {label: 'Kotlinlang Slack #redux', href: 'https://kotlinlang.slack.com/archives/C8A8G5F9Q'},
            {label: 'GitHub Discussions', href: 'https://github.com/reduxkotlin/redux-kotlin/discussions'},
          ],
        },
        {
          title: 'More',
          items: [
            {label: 'GitHub', href: 'https://github.com/reduxkotlin/redux-kotlin'},
            {label: 'Maven Central', href: 'https://mvnrepository.com/artifact/org.reduxkotlin/redux-kotlin'},
          ],
        },
      ],
      copyright: `Copyright © ${new Date().getFullYear()} reduxkotlin.org. Built with Docusaurus.`,
    },
    prism: {
      theme: prismThemes.github,
      darkTheme: prismThemes.dracula,
      additionalLanguages: ['kotlin', 'groovy', 'java', 'bash'],
    },
    colorMode: {
      defaultMode: 'light',
      respectPrefersColorScheme: true,
    },
  } satisfies Preset.ThemeConfig,
};

export default config;

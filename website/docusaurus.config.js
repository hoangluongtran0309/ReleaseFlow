// @ts-check
// The documentation site. Its pages are the guides in ../docs, read in place, so a page reads
// the same on GitHub and here; this directory holds only the site around them. See ADR-0032.

import {execFileSync} from 'node:child_process';
import fs from 'node:fs';
import path from 'node:path';
import {fileURLToPath} from 'node:url';
import {themes as prismThemes} from 'prism-react-renderer';
import rehypeFocusableTables from './src/plugins/rehype-focusable-tables.js';
import remarkRepositoryLinks from './src/plugins/remark-repository-links.js';

const siteDir = path.dirname(fileURLToPath(import.meta.url));
const repositoryRoot = path.resolve(siteDir, '..');
const repository = 'https://github.com/hoangluongtran0309/ReleaseFlow';

// Code blocks stay dark in both themes: the dark theme's tokens clear 6:1 on the code
// background token, so both themes use it on that background.
const codeTheme = {
  ...prismThemes.gruvboxMaterialDark,
  plain: {...prismThemes.gruvboxMaterialDark.plain, backgroundColor: 'var(--rf-code-bg)'},
};

// A page's "Updated" date is its last commit. A checkout without full history (no .git, or
// a shallow clone, which dates every file to the newest commit) shows no date at all.
function hasFullGitHistory() {
  try {
    return execFileSync('git', ['rev-parse', '--is-shallow-repository'], {cwd: repositoryRoot, stdio: ['ignore', 'pipe', 'ignore']})
      .toString()
      .trim() === 'false';
  } catch {
    return false;
  }
}

// A page is edited where it lives: the English guide in docs/, its translation under i18n/.
// A page with no translation yet falls back to the English one, and so does its edit link.
function editUrl({locale, docPath}) {
  const translated = `website/i18n/${locale}/docusaurus-plugin-content-docs/current/${docPath}`;
  if (locale !== 'en' && fs.existsSync(path.join(repositoryRoot, translated))) {
    return `${repository}/edit/develop/${translated}`;
  }
  return `${repository}/edit/develop/docs/${docPath}`;
}

/** @type {import('@docusaurus/types').Config} */
const config = {
  title: 'ReleaseFlow',
  tagline:
    'Turn merged pull requests and finished issues into release notes that a person has reviewed, written for each audience, and delivered where they read them.',
  favicon: 'favicon.svg',

  url: 'https://hoangluongtran0309.github.io',
  baseUrl: '/ReleaseFlow/',
  trailingSlash: false,
  organizationName: 'hoangluongtran0309',
  projectName: 'ReleaseFlow',

  onBrokenLinks: 'throw',
  onBrokenAnchors: 'throw',
  onDuplicateRoutes: 'throw',

  markdown: {
    // .md stays CommonMark, so a guide never has to be written for MDX.
    format: 'detect',
    hooks: {
      onBrokenMarkdownLinks: 'throw',
      onBrokenMarkdownImages: 'throw',
    },
  },

  i18n: {
    defaultLocale: 'en',
    locales: ['en', 'vi'],
    localeConfigs: {
      en: {label: 'English', htmlLang: 'en'},
      vi: {label: 'Tiếng Việt', htmlLang: 'vi'},
    },
  },

  staticDirectories: ['static', '../.github/assets'],

  presets: [
    [
      'classic',
      /** @type {import('@docusaurus/preset-classic').Options} */
      ({
        docs: {
          path: '../docs',
          routeBasePath: 'docs',
          sidebarPath: './sidebars.js',
          // ADR files keep their number in the address, as they do in the repository.
          numberPrefixParser: false,
          editUrl,
          showLastUpdateTime: hasFullGitHistory(),
          beforeDefaultRemarkPlugins: [
            [remarkRepositoryLinks, {repository, repositoryRoot, docsRoot: path.join(repositoryRoot, 'docs')}],
          ],
          rehypePlugins: [rehypeFocusableTables],
        },
        blog: false,
        theme: {
          customCss: './src/css/custom.css',
        },
      }),
    ],
  ],

  plugins: [
    ['./src/plugins/api-catalog/index.mjs', {repository}],
    ['./src/plugins/changelog/index.mjs', {repository}],
  ],

  themes: [
    [
      '@easyops-cn/docusaurus-search-local',
      /** @type {import('@easyops-cn/docusaurus-search-local').PluginOptions} */
      ({
        hashed: true,
        language: ['en', 'vi'],
        docsDir: '../docs',
        docsRouteBasePath: 'docs',
        indexBlog: false,
        indexPages: true,
        explicitSearchResultPath: true,
      }),
    ],
  ],

  themeConfig:
    /** @type {import('@docusaurus/preset-classic').ThemeConfig} */
    ({
      image: 'social-preview.png',
      // Dark is the designed theme: a first visit starts there, and the toggle keeps the
      // reader's choice.
      colorMode: {
        defaultMode: 'dark',
        respectPrefersColorScheme: false,
      },
      navbar: {
        logo: {
          alt: 'ReleaseFlow',
          src: 'logo-light.svg',
          srcDark: 'logo-dark.svg',
        },
        // The main sections, then the right-hand links; src/theme/Navbar lays them out.
        items: [
          {to: '/product', position: 'left', label: 'Product'},
          {to: '/docs', position: 'left', label: 'Docs'},
          {to: '/self-host', position: 'left', label: 'Self-host'},
          {to: '/changelog', position: 'left', label: 'Changelog'},
          {to: '/community', position: 'left', label: 'Community'},
          {type: 'localeDropdown', position: 'right'},
          {href: repository, position: 'right', label: 'GitHub'},
        ],
      },
      footer: {
        links: [
          {
            title: 'Documentation',
            items: [
              {label: 'Getting started', to: '/docs/getting-started#quickstart'},
              {label: 'Guides', to: '/docs'},
              {label: 'REST API', to: '/docs/api'},
              {label: 'Configuration', to: '/docs/configuration'},
            ],
          },
          {
            title: 'Community',
            items: [
              {label: 'Discussions', href: `${repository}/discussions`},
              {label: 'Issues', href: `${repository}/issues`},
              {label: 'Contributing', to: '/community'},
              {label: 'Code of conduct', href: `${repository}/blob/main/CODE_OF_CONDUCT.md`},
            ],
          },
          {
            title: 'Project',
            items: [
              {label: 'Changelog', to: '/changelog'},
              {label: 'Releases', href: `${repository}/releases`},
              {label: 'Security policy', to: '/security'},
              {label: 'GitHub', href: repository},
            ],
          },
        ],
        copyright: 'Copyright 2026 Luong Tran Chu Hoang. Licensed under the Apache License 2.0.',
      },
      prism: {
        theme: codeTheme,
        darkTheme: codeTheme,
        additionalLanguages: ['bash'],
      },
    }),
};

export default config;

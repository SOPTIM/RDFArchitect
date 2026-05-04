// @ts-check
import { themes as prismThemes } from 'prism-react-renderer';

/** @type {import('@docusaurus/types').Config} */
const config = {
  title: 'RDFArchitect',
  tagline: 'Web-based modeling for CIM/CGMES RDF schemas and SHACL constraints',
  favicon: 'img/favicon.svg',

  url: 'https://rdfarchitect.soptim.de',
  baseUrl: '/',

  organizationName: 'SOPTIM',
  projectName: 'RDFArchitect',
  trailingSlash: false,

  onBrokenLinks: 'warn',
  onBrokenMarkdownLinks: 'warn',

  i18n: {
    defaultLocale: 'en',
    locales: ['en'],
  },

  presets: [
    [
      'classic',
      /** @type {import('@docusaurus/preset-classic').Options} */
      ({
        docs: {
          path: 'content',
          routeBasePath: '/',
          sidebarPath: './sidebars.js',
          editUrl:
            'https://github.com/SOPTIM/RDFArchitect/edit/main/docs/',
        },
        blog: false,
        theme: {
          customCss: './src/css/custom.css',
        },
      }),
    ],
  ],

  themeConfig:
    /** @type {import('@docusaurus/preset-classic').ThemeConfig} */
    ({
      image: 'img/screenshots/editor.png',
      colorMode: {
        defaultMode: 'light',
        respectPrefersColorScheme: true,
      },
      navbar: {
        logo: {
          alt: 'RDFArchitect',
          src: 'img/logo.svg',
          srcDark: 'img/logo-dark.svg',
        },
        items: [
          {
            type: 'docSidebar',
            sidebarId: 'userSidebar',
            position: 'left',
            label: 'User Guide',
          },
          {
            type: 'docSidebar',
            sidebarId: 'developerSidebar',
            position: 'left',
            label: 'Developer Guide',
          },
          {
            type: 'docSidebar',
            sidebarId: 'adminSidebar',
            position: 'left',
            label: 'Administration',
          },
          {
            href: 'https://github.com/SOPTIM/RDFArchitect',
            label: 'GitHub',
            position: 'right',
          },
        ],
      },
      footer: {
        style: 'dark',
        links: [
          {
            title: 'Documentation',
            items: [
              { label: 'Introduction', to: '/' },
              { label: 'User Guide', to: '/user-guide/overview' },
              { label: 'Developer Guide', to: '/developer-guide/overview' },
              { label: 'Administration', to: '/admin-guide/installation' },
            ],
          },
          {
            title: 'Project',
            items: [
              { label: 'GitHub', href: 'https://github.com/SOPTIM/RDFArchitect' },
              { label: 'Issues', href: 'https://github.com/SOPTIM/RDFArchitect/issues' },
              { label: 'Releases', href: 'https://github.com/SOPTIM/RDFArchitect/releases' },
              { label: 'Changelog', to: '/reference/changelog' },
            ],
          },
          {
            title: 'Reference',
            items: [
              { label: 'CIM Evaluation', to: '/reference/cim-evaluation' },
              { label: 'CIM/CGMES Mapping', to: '/reference/cim-mapping' },
              { label: 'FAQ & Troubleshooting', to: '/reference/faq' },
              { label: 'License', href: 'https://github.com/SOPTIM/RDFArchitect/blob/main/LICENSE' },
              { label: 'Legal Notice', href: 'https://www.soptim.de/en/legal-notice/' },
            ],
          },
        ],
        copyright: `Copyright © 2024-${new Date().getFullYear()} SOPTIM AG. Licensed under Apache 2.0.`,
      },
      prism: {
        theme: prismThemes.github,
        darkTheme: prismThemes.dracula,
        additionalLanguages: ['java', 'bash', 'yaml', 'turtle', 'sparql', 'docker', 'nginx'],
      },
      docs: {
        sidebar: {
          hideable: true,
          autoCollapseCategories: false,
        },
      },
    }),
};

export default config;

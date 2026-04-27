// @ts-check

/** @type {import('@docusaurus/plugin-content-docs').SidebarsConfig} */
const sidebars = {
  userSidebar: [
    'intro',
    {
      type: 'category',
      label: 'User Guide',
      collapsed: false,
      items: [
        'user-guide/overview',
        'user-guide/concepts',
        'user-guide/getting-started',
        'user-guide/schemas',
        'user-guide/import-export',
        'user-guide/editing-classes',
        'user-guide/packages-and-diagrams',
        'user-guide/shacl',
        'user-guide/history',
        'user-guide/snapshots-and-sharing',
        'user-guide/comparing-schemas',
        'user-guide/migration-wizard',
        'user-guide/prefixes',
        'user-guide/readonly-mode',
        'user-guide/screenshots',
      ],
    },
    {
      type: 'category',
      label: 'Reference',
      items: [
        'reference/features',
        'reference/cim-mapping',
        'reference/faq',
        'reference/limitations',
        'reference/changelog',
      ],
    },
  ],

  developerSidebar: [
    'developer-guide/overview',
    {
      type: 'category',
      label: 'Getting Started',
      collapsed: false,
      items: [
        'developer-guide/setup',
        'developer-guide/repository-layout',
        'developer-guide/run-and-debug',
      ],
    },
    {
      type: 'category',
      label: 'Architecture',
      collapsed: false,
      items: [
        'developer-guide/backend-architecture',
        'developer-guide/frontend-architecture',
        'developer-guide/data-model',
      ],
    },
    {
      type: 'category',
      label: 'Working With the Code',
      items: [
        'developer-guide/adding-a-feature',
        'developer-guide/rdf-shacl-sparql',
        'developer-guide/testing',
        'developer-guide/style-and-quality-gates',
        'developer-guide/dependencies',
      ],
    },
    {
      type: 'category',
      label: 'Release & CI',
      items: [
        'developer-guide/ci-and-releases',
        'developer-guide/api-stability',
        'developer-guide/contribution-scenarios',
      ],
    },
  ],

  adminSidebar: [
    'admin-guide/overview',
    'admin-guide/installation',
    'admin-guide/configuration',
    'admin-guide/fuseki',
    'admin-guide/access-control',
    'admin-guide/backups',
    'admin-guide/upgrades',
    'admin-guide/scaling',
    'admin-guide/troubleshooting',
  ],
};

export default sidebars;

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
        'user-guide/workspace-and-importing',
        'user-guide/organising-schemas',
        'user-guide/editing-classes',
        'user-guide/namespaces',
        'user-guide/profile-header',
        'user-guide/shacl',
        'user-guide/history',
        'user-guide/comparing-schemas',
        'user-guide/migration',
        'user-guide/sharing-and-exporting',
        'user-guide/read-only-mode',
        'user-guide/search-and-tips',
      ],
    },
    {
      type: 'category',
      label: 'Reference',
      items: [
        'reference/features',
        'reference/cim-evaluation',
        'reference/cim-mapping',
        'reference/faq',
        'reference/bachelor-theses',
        'reference/changelog',
      ],
    },
  ],

  developerSidebar: [
    'developer-guide/overview',
    'developer-guide/repository-layout',
    'developer-guide/backend-architecture',
    'developer-guide/frontend-architecture',
    'developer-guide/adding-a-feature',
    'developer-guide/testing',
    'developer-guide/code-style',
    'developer-guide/api-stability',
    'developer-guide/rdf-shacl-sparql',
    'developer-guide/ci-and-releases',
    'developer-guide/contribution-scenarios',
  ],

  adminSidebar: [
    'admin-guide/overview',
    'admin-guide/installation',
    'admin-guide/configuration',
    'admin-guide/fuseki',
    'admin-guide/security',
    'admin-guide/upgrading',
    'admin-guide/troubleshooting',
  ],
};

export default sidebars;

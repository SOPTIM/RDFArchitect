# RDFArchitect Documentation Site

This directory contains the [Docusaurus](https://docusaurus.io/) source for the public documentation.

## Local development

```bash
cd docs
npm install
npm start
```

The dev server is served at http://localhost:3000 and reloads on changes.

## Production build

```bash
npm run build
npm run serve
```

`npm run build` produces a static site in `build/`, which is what the GitHub Pages workflow deploys.

## Deployment

Deployment is handled by [`.github/workflows/deploy-docs.yml`](../.github/workflows/deploy-docs.yml). On push to `main` (or manual dispatch), the workflow builds the site and publishes it to GitHub Pages.

## Structure

| Path | Purpose |
| ---- | ------- |
| `docusaurus.config.js` | Site config (title, navigation, footer, repo links). |
| `sidebars.js` | Sidebar layout for User Guide, Developer Guide, Administration. |
| `content/` | All documentation Markdown sources. |
| `static/` | Static assets served at the root (images, CNAME, robots, etc.). |
| `src/css/custom.css` | Theme overrides. |

## Editing docs

1. Find or create the relevant file under `content/`.
2. Update `sidebars.js` if you add a new page.
3. Run `npm start` and check rendering.
4. Commit. CI builds the site as a verification step on every PR.

---
title: Upgrading
sidebar_position: 8
---

# Upgrading

RDFArchitect follows semantic versioning (see [Changelog](/reference/changelog)). For minor and patch releases, upgrading is typically a straight image swap:

```bash
docker compose pull
docker compose up -d --build
```

For major releases, review the changelog for breaking changes before upgrading. The 0.15.0 release, for example, switched the default diagram renderer from Mermaid to SvelteFlow and required a one-time layout regeneration on existing graphs.

## Data compatibility

Exported RDFS and SHACL artefacts, and snapshots stored in Fuseki, are standard RDF; they are forward and backward compatible across RDFArchitect versions.

## Frontend caching

After an upgrade, users may see stale frontend assets. A hard refresh (Ctrl+Shift+R) is usually sufficient; for public deployments, configure the nginx gateway to include a content-hash in static asset URLs to avoid the issue entirely.

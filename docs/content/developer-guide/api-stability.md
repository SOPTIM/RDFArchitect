---
title: API Stability and Versioning
sidebar_position: 8
---

# API Stability and Versioning

## Versioning scheme

Semantic Versioning, derived from git tags (`vX.Y.Z`):

- **Patch** (`v1.0.x`) — bug fixes, internal changes, no behaviour change.
- **Minor** (`v1.x.0`) — new features, backward-compatible API changes.
- **Major** (`vX.0.0`) — breaking changes.

The version shown in the application is computed at build time from `git describe` — see `.github/scripts/resolve_version.sh`. Local builds without a tag in their history get `0.0.0-SNAPSHOT`.

## What counts as breaking?

- Removing a REST endpoint.
- Removing a field from a request or response DTO.
- Changing the type or semantics of an existing field.
- Changing required headers or authentication behaviour.
- Renaming a configuration property.
- Anything that requires existing custom SHACL or stored data to be migrated.

Mark such changes with `!` in the conventional-commit header (`feat(api)!: ...`) or `BREAKING CHANGE:` in the body. Update `CHANGELOG.md` under a `### Breaking Changes` section.

## What does *not* count as breaking?

- Adding a new endpoint.
- Adding a new optional field to an existing response.
- Adding a new optional request parameter that has a sensible default.
- Internal refactors that do not change the REST surface.

## REST surface and Swagger

Swagger UI is the public-facing documentation of the REST API. Every endpoint must be `@Operation`-annotated; every DTO field that has a non-obvious meaning should carry an `@Schema` description. CI does not currently enforce this, but reviewers do.

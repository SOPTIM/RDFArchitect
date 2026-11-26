---
title: CI, Releases, and Docker Images
sidebar_position: 12
---

# CI, Releases, and Docker Images

## Workflows

Two GitHub Actions workflows run on every push and PR:

- **`.github/workflows/backend-ci.yml`** — checkout, setup Java 25, resolve version, lint (`mvn -B -Plint -DskipTests verify`), unit tests (`mvn -B test`), integration tests (`mvn -B verify`), third-party license consistency check, build & push Docker image (only on `main` and tags).
- **`.github/workflows/frontend-ci.yml`** — checkout, setup Node 24, install, lint, test, build, third-party license check, build & push Docker image (only on `main` and tags).

Every Maven build of the backend fails in the `validate` phase when a test library (JUnit, Mockito, AssertJ, the Spring test modules and others) is in a scope other than `test`, because it would end up in the application jar. The rule is the enforcer execution `ban-test-libraries` in `backend/pom.xml`.

A third workflow, **`publish-test-images.yml`**, builds preview images for PRs from trusted contributors.

## Releases

Releases are tag-driven:

1. Run the **`Update Changelog`** workflow (`.github/workflows/update-changelog.yml`) with the new version and merge the PR it opens. It generates the `## [X.Y.Z] - YYYY-MM-DD` section of `CHANGELOG.md` from the squash commit titles since the last tag.
2. Push a tag of the form `vX.Y.Z` to `main`.
3. CI builds and publishes Docker images tagged `X.Y.Z`, `X.Y`, and `X` (with `X` suppressed for `0.x.y`) plus `latest` on the default branch.
4. A GitHub Release is created automatically.

## Docker images

Images are published to **GHCR**:

- `ghcr.io/soptim/rdfarchitect-backend`
- `ghcr.io/soptim/rdfarchitect-frontend`

The compose file in `docker/` builds them locally; you can substitute the published images by changing `build:` to `image:`.

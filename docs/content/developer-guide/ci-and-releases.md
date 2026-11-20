---
title: CI, Releases, and Docker Images
sidebar_position: 10
---

# CI, Releases, and Docker Images

## Workflows

Two GitHub Actions workflows run on every push and PR:

- **`.github/workflows/backend-ci.yml`** — checkout, setup Java 25, resolve version, lint (`mvn -B -Plint -DskipTests verify`), unit tests (`mvn -B test`), integration tests (`mvn -B verify`), third-party license consistency check, build & push Docker image (only on `main` and tags).
- **`.github/workflows/frontend-ci.yml`** — checkout, setup Node 24, install, lint, test, build, third-party license check, build & push Docker image (only on `main` and tags).

A third workflow, **`publish-test-images.yml`**, builds preview images for PRs from trusted contributors.

**`.github/workflows/desktop.yml`** builds the [desktop app](/user-guide/desktop-app): the frontend once, then one job per platform (Windows x64, macOS arm64 and x64, Linux x64), since jpackage only builds for the system it runs on. It runs when a release is published, by hand, and on pull requests that touch the desktop packaging.

## Releases

Releases are tag-driven:

1. Update `CHANGELOG.md`: move items from `## [Unreleased]` into a new `## [X.Y.Z] - YYYY-MM-DD` section.
2. Push a tag of the form `vX.Y.Z` to `main`.
3. CI builds and publishes Docker images tagged `X.Y.Z`, `X.Y`, and `X` (with `X` suppressed for `0.x.y`) plus `latest` on the default branch.
4. A GitHub Release is created automatically.
5. Publishing the release starts **Desktop App**, which attaches the portable archives and installers of every platform to it.

## Desktop app

The desktop app is the backend with the built user interface inside, started by `org.rdfarchitect.desktop.DesktopLauncher` with the Spring profile `desktop` (`application-desktop.yml`). `desktop/build.sh` builds it for the system it runs on:

```bash
JAVA_HOME=/path/to/jdk-25 desktop/build.sh              # portable archive in desktop/dist/
JAVA_HOME=/path/to/jdk-25 desktop/build.sh --installer  # plus .msi, .dmg or .deb
```

It builds the frontend, then the desktop jar with `mvn -Pdesktop package` (which copies `frontend/build` into the jar), trims a Java runtime with `jlink`, and packages both with `jpackage`. The `.msi` needs the WiX Toolset, the `.deb` needs `dpkg-deb` and `fakeroot`. On Windows run it from Git Bash.

To try the desktop mode during development without packaging, build the frontend and run the backend with the profile and the build directory:

```bash
cd backend
mvn -B compile
mvn -B exec:java -Dexec.mainClass=org.rdfarchitect.desktop.DesktopLauncher \
  -Dexec.args="--browser=none --desktop.user-interface=file:../frontend/build/"
```

The icons in `desktop/icons/` and `frontend/static/icons/` are rendered from `frontend/static/favicon.svg` by `desktop/icons/generate.py`.

## Docker images

Images are published to **GHCR**:

- `ghcr.io/soptim/rdfarchitect-backend`
- `ghcr.io/soptim/rdfarchitect-frontend`

The compose file in `docker/` builds them locally; you can substitute the published images by changing `build:` to `image:`.

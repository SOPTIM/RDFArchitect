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

**`.github/workflows/desktop.yml`** builds the [desktop app](/user-guide/desktop-app): the frontend once, then one job per platform (Windows x64, macOS arm64 and x64, Linux x64), since jpackage only builds for the system it runs on. It runs for release tags, and on pushes and pull requests that touch the desktop packaging, `backend/pom.xml` or the desktop-only backend code. A platform whose installer fails to build fails its job, but still uploads its portable archive as a workflow artifact.

## Releases

Releases are tag-driven:

1. Run the **`Update Changelog`** workflow (`.github/workflows/update-changelog.yml`) with the new version and merge the PR it opens. It generates the `## [X.Y.Z] - YYYY-MM-DD` section of `CHANGELOG.md` from the squash commit titles since the last tag.
2. Push a tag of the form `vX.Y.Z` to `main`.
3. CI builds and publishes Docker images tagged `X.Y.Z`, `X.Y`, and `X` (with `X` suppressed for `0.x.y`) plus `latest` on the default branch.
4. **Desktop App** creates a draft release `X.Y.Z`, with the tag's changelog section as its notes, and attaches the portable archives of every platform and the macOS and Linux installers. A draft is only visible to people with write access to the repository. When it runs again for the tag, it attaches only the downloads the draft does not hold yet, so signed downloads stay; to replace a download, delete it from the draft first. It fails when the release is published already.
5. Sign the Windows downloads, as described below.
6. Check the draft release (its notes and the eight downloads listed below) and publish it.

### Signing the Windows downloads

The two Windows downloads have to be signed before the release is published. The `.msi` is built in between, from the signed portable archive, so that the program it installs is signed as well; that is why a release tag builds no `.msi` by itself. The steps use the [GitHub CLI](https://cli.github.com/); the same works on the release page (**Edit** on the draft, delete the old file, add the new one).

1. Download the portable archive from the draft:

   ```bash
   gh release download vX.Y.Z --repo SOPTIM/RDFArchitect --pattern "*-windows-x64.zip"
   ```

2. Unpack it, sign `RDFArchitect\RDFArchitect.exe`, and pack the `RDFArchitect` folder into an archive of the same name again, for example with `tar -a -c -f RDFArchitect-X.Y.Z-windows-x64.zip RDFArchitect` (`tar` is part of Windows 10 and later) or **Send to → Compressed (zipped) folder**.

3. Replace the unsigned archive in the draft with the signed one:

   ```bash
   gh release upload vX.Y.Z RDFArchitect-X.Y.Z-windows-x64.zip --clobber --repo SOPTIM/RDFArchitect
   ```

4. Build the installer from it: run **Actions → Desktop Windows Installer** with the tag, or

   ```bash
   gh workflow run desktop-windows-installer.yml --repo SOPTIM/RDFArchitect -f tag=vX.Y.Z
   ```

   It refuses an archive whose `RDFArchitect.exe` is not validly signed, builds the `.msi`, checks the program inside it again, and attaches `RDFArchitect-X.Y.Z-windows-x64.msi` to the draft. With the repository variable `WINDOWS_SIGNER` set (e.g. `SOPTIM AG`), it also checks that the organization (`O=`) of the signing certificate is exactly that value. Its `allow-unsigned` input is only for trying the workflow.

5. Download the `.msi`, sign it, and replace it in the draft the same way:

   ```bash
   gh release download vX.Y.Z --repo SOPTIM/RDFArchitect --pattern "*-windows-x64.msi"
   # sign RDFArchitect-X.Y.Z-windows-x64.msi
   gh release upload vX.Y.Z RDFArchitect-X.Y.Z-windows-x64.msi --clobber --repo SOPTIM/RDFArchitect
   ```

A finished release holds, for `X.Y.Z`: `RDFArchitect-X.Y.Z-windows-x64.zip` and `.msi` (both signed), `RDFArchitect-X.Y.Z-mac-arm64.zip` and `.dmg`, `RDFArchitect-X.Y.Z-mac-x64.zip` and `.dmg`, and `RDFArchitect-X.Y.Z-linux-x64.tar.gz` and `.deb`.

The macOS downloads are not signed.

## Desktop app

The desktop app is the backend with the built user interface inside, started by `org.rdfarchitect.desktop.DesktopLauncher` with the Spring profile `desktop` (`application-desktop.yml`). `desktop/build.sh` builds it for the system it runs on:

```bash
JAVA_HOME=/path/to/jdk-25 desktop/build.sh              # portable archive in desktop/dist/
JAVA_HOME=/path/to/jdk-25 desktop/build.sh --installer  # plus .msi, .dmg or .deb
```

It builds the frontend, then the desktop jar with `mvn -Pdesktop package` (which copies `frontend/build` into the jar), trims a Java runtime with `jlink`, and packages both with `jpackage`. The `.msi` needs the WiX Toolset 3 on the path, the `.deb` needs `dpkg-deb` and `fakeroot`. On Windows run it from Git Bash. The installers register `.rdfa` files with the app (`--file-associations`); the launcher hands a file it is started with to the running app, which opens a window at `/mainpage?open=<id>`.

To try the desktop mode during development without packaging, build the frontend and run the backend with the profile and the build directory:

```bash
cd backend
mvn -B compile
mvn -B exec:java -Dexec.mainClass=org.rdfarchitect.desktop.DesktopLauncher \
  -Dexec.args="--browser=none --desktop.user-interface=file:../frontend/build/"
```

With `--browser=none` the launcher prints `RDFArchitect is running at http://127.0.0.1:<port>/desktop/session?token=…`. Opening that address sets the access cookie and shows the editor.

Only the user who started the app can use its API:

- Every request under `/api/` needs the run's access token, a new one per start. Without it the answer is `401`. The user interface and `/env.js` need none.
- The browser sends the token as the cookie `RDFA_DESKTOP_ACCESS_<port>`, which `GET /desktop/session?token=<token>&next=<path>` sets.
- Other clients send it in the header `X-RDFA-Desktop-Token`.
- `run/instance.json` in the data directory holds `port`, `token` and `version` of the running app. Only the user can read it.
- A request with the header `X-RDFA-Desktop-Challenge: <nonce>` gets `X-RDFA-Desktop-Proof: <base64url(HMAC-SHA256(token, nonce))>` in its answer, also with `401`. A second start checks it before it sends the token to the port named in `instance.json`.
- The launcher opens a window with `run/launch.html` (the default browser with `run/open-<id>.html`), which forwards to the session address. So the token is not on the browser's command line.

To call the API by hand, for example on Linux with the default data directory:

```bash
TOKEN=$(jq -r .token ~/.local/share/rdfarchitect/run/instance.json)
PORT=$(jq -r .port ~/.local/share/rdfarchitect/run/instance.json)
curl -H "X-RDFA-Desktop-Token: $TOKEN" http://127.0.0.1:$PORT/api/desktop
```

Workspaces belong to an HTTP session as on the server, so keep the session cookie between calls (`curl -b jar -c jar`).

`desktop/icons/generate.py` renders the icons and the splash screen in `desktop/icons/` and the icons in `frontend/static/icons/`. It draws the mark itself, as a copy of `frontend/static/favicon.svg`, so a change to the mark has to be made in both.

## Docker images

Images are published to **GHCR**:

- `ghcr.io/soptim/rdfarchitect-backend`
- `ghcr.io/soptim/rdfarchitect-frontend`

The compose file in `docker/` builds them locally; you can substitute the published images by changing `build:` to `image:`.

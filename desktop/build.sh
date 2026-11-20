#!/usr/bin/env bash
#
#    Copyright (c) 2024-2026 SOPTIM AG
#
#    Licensed under the Apache License, Version 2.0 (the "License");
#    you may not use this file except in compliance with the License.
#    You may obtain a copy of the License at
#
#        http://www.apache.org/licenses/LICENSE-2.0
#
#    Unless required by applicable law or agreed to in writing, software
#    distributed under the License is distributed on an "AS IS" BASIS,
#    WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
#    See the License for the specific language governing permissions and
#    limitations under the License.
#
# Builds the RDFArchitect desktop app for the operating system this runs on (jpackage cannot
# build for another one): the user interface, the desktop jar, a Java runtime trimmed to what the
# app needs, and the app itself, packed as a portable archive and optionally as an installer.
#
# Usage: desktop/build.sh [options]
#   --version <v>      version to build, default: from git (.github/scripts/resolve_version.sh)
#   --installer        also build the installer: .msi on Windows, .dmg on macOS, .deb on Linux
#   --skip-frontend    use the existing frontend/build
#   --skip-backend     use the existing backend/target/rdfarchitect-desktop.jar
#
# Needs a JDK 25 or newer (JAVA_HOME, with jlink and jpackage), Maven, and Node.js 24 with npm.
# The .msi needs the WiX Toolset; the .deb needs dpkg-deb and fakeroot.
# Results go to desktop/dist/.

set -euo pipefail

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
DESKTOP="$ROOT/desktop"
DIST="$DESKTOP/dist"
WORK="$DESKTOP/build"

APP_NAME="RDFArchitect"
VENDOR="SOPTIM AG"
DESCRIPTION="Editor for CIM and RDF schemas"
COPYRIGHT="Copyright (c) 2024-2026 SOPTIM AG"
# Keeps an installed version replaceable by the next one; never change it.
WINDOWS_UPGRADE_UUID="5d8f5b4e-0d0c-4d3a-9a57-3f1f2a6c8b21"

# The Java SE API as a whole, plus the JDK modules libraries reach for at runtime. jdeps finds
# fewer (java.base, java.desktop, java.sql, java.naming, ...), but a module missing at runtime only
# shows when the code needing it runs, so the margin is worth its few megabytes.
JAVA_MODULES="${DESKTOP_JAVA_MODULES:-java.se,jdk.unsupported,jdk.unsupported.desktop,jdk.jfr,jdk.crypto.ec,jdk.zipfs,jdk.charsets,jdk.localedata,jdk.management,jdk.net,jdk.naming.dns}"
JAVA_OPTIONS=('-XX:MaxRAMPercentage=50' '-splash:$APPDIR/splash.png')

version=""
installer=false
build_frontend=true
build_backend=true
while [[ $# -gt 0 ]]; do
  case "$1" in
    --version) version="$2"; shift 2 ;;
    --installer) installer=true; shift ;;
    --skip-frontend) build_frontend=false; shift ;;
    --skip-backend) build_backend=false; shift ;;
    -h|--help) sed -n '17,30p' "${BASH_SOURCE[0]}" | sed 's/^# \{0,1\}//'; exit 0 ;;
    *) echo "Unknown option: $1" >&2; exit 2 ;;
  esac
done

log() { printf '\n==> %s\n' "$*"; }

case "$(uname -s)" in
  Linux*) os=linux ;;
  Darwin*) os=mac ;;
  MINGW*|MSYS*|CYGWIN*) os=windows ;;
  *) echo "Unsupported operating system: $(uname -s)" >&2; exit 1 ;;
esac
case "$(uname -m)" in
  x86_64|amd64) arch=x64 ;;
  arm64|aarch64) arch=arm64 ;;
  *) arch="$(uname -m)" ;;
esac

if [[ -z "${JAVA_HOME:-}" ]]; then
  echo "JAVA_HOME must point to a JDK 25 or newer." >&2
  exit 1
fi
JAVA_BIN="$JAVA_HOME/bin"
for tool in jlink jpackage jar; do
  if [[ ! -x "$JAVA_BIN/$tool" && ! -x "$JAVA_BIN/$tool.exe" ]]; then
    echo "$JAVA_BIN has no $tool; JAVA_HOME must point to a full JDK 25 or newer." >&2
    exit 1
  fi
done

if [[ -z "$version" ]]; then
  version="$(cd "$ROOT" && bash .github/scripts/resolve_version.sh | sed -n 's/^APP_VERSION=//p')"
fi
# jpackage takes numbers only, and macOS refuses a version starting with 0.
numeric_version="$(sed -E 's/^([0-9]+(\.[0-9]+){0,2}).*/\1/' <<<"$version")"
if [[ ! "$numeric_version" =~ ^[0-9]+(\.[0-9]+){0,2}$ ]]; then
  numeric_version="1.0.0"
fi
if [[ "$os" == mac && "$numeric_version" == 0* ]]; then
  numeric_version="1.0.0"
fi
artifact="$APP_NAME-$version-$os-$arch"

log "Building $APP_NAME $version ($numeric_version) for $os-$arch"

if $build_frontend; then
  log "Frontend"
  (
    cd "$ROOT/frontend"
    npm ci
    npm run api:generate
    PUBLIC_APP_VERSION="$version" npm run build
  )
fi

if $build_backend; then
  log "Desktop jar"
  (
    cd "$ROOT/backend"
    mvn -B -Pdesktop clean package -DskipTests -Ddesktop.version="$version"
  )
fi
JAR="$ROOT/backend/target/rdfarchitect-desktop.jar"
if [[ ! -f "$JAR" ]]; then
  echo "Missing $JAR" >&2
  exit 1
fi

rm -rf "$WORK" && mkdir -p "$WORK/input" "$DIST"
cp "$JAR" "$WORK/input/rdfarchitect-desktop.jar"
cp "$DESKTOP/icons/splash.png" "$WORK/input/splash.png"

log "Java runtime"
"$JAVA_BIN/jlink" \
  --add-modules "$JAVA_MODULES" \
  --include-locales=en,de \
  --strip-debug --no-header-files --no-man-pages \
  --compress=zip-6 \
  --output "$WORK/runtime"

common_options=(
  --name "$APP_NAME"
  --app-version "$numeric_version"
  --vendor "$VENDOR"
  --description "$DESCRIPTION"
  --copyright "$COPYRIGHT"
  --input "$WORK/input"
  --main-jar rdfarchitect-desktop.jar
  --runtime-image "$WORK/runtime"
)
for option in "${JAVA_OPTIONS[@]}"; do
  common_options+=(--java-options "$option")
done

# Installers register .rdfa files: double-clicking one opens it in the app. The icon is copied
# under a name of its own: jpackage puts it next to the app icon, which it names after the app,
# and on a case-insensitive file system rdfarchitect.icns and RDFArchitect.icns are one file.
write_file_association() {
  cat > "$WORK/rdfa-association.properties" <<EOF
extension=rdfa
mime-type=application/x-rdfarchitect-workspace
description=RDFArchitect workspace
EOF
  if [[ -n "${1:-}" ]]; then
    local icon="$WORK/rdfa-workspace-file.${1##*.}"
    cp "$1" "$icon"
    echo "icon=$icon" >> "$WORK/rdfa-association.properties"
  fi
}

case "$os" in
  windows)
    common_options+=(--icon "$DESKTOP/icons/rdfarchitect.ico")
    # Without an icon of its own: jpackage 25 references the icon of a file association in the
    # .msi without installing it, and light.exe stops with LGHT0094 (unresolved File symbol).
    write_file_association
    installer_type=msi
    installer_options=(
      --win-menu --win-menu-group "$APP_NAME" --win-shortcut --win-shortcut-prompt
      --win-dir-chooser --win-per-user-install --win-upgrade-uuid "$WINDOWS_UPGRADE_UUID"
      --license-file "$ROOT/LICENSE" --file-associations "$WORK/rdfa-association.properties"
    )
    ;;
  mac)
    common_options+=(
      --icon "$DESKTOP/icons/rdfarchitect.icns"
      --mac-package-identifier de.soptim.rdfarchitect
      --mac-package-name "$APP_NAME"
    )
    write_file_association "$DESKTOP/icons/rdfarchitect.icns"
    installer_type=dmg
    installer_options=(
      --license-file "$ROOT/LICENSE" --file-associations "$WORK/rdfa-association.properties"
    )
    ;;
  linux)
    common_options+=(--icon "$DESKTOP/icons/rdfarchitect.png")
    write_file_association "$DESKTOP/icons/rdfarchitect.png"
    installer_type=deb
    installer_options=(
      --linux-package-name rdfarchitect --linux-shortcut --linux-menu-group Development
      --linux-app-category devel --linux-deb-maintainer "opencgmes@soptim.de"
      --license-file "$ROOT/LICENSE" --file-associations "$WORK/rdfa-association.properties"
    )
    ;;
esac

log "App image"
"$JAVA_BIN/jpackage" --type app-image --dest "$WORK/image" "${common_options[@]}"

log "Portable archive"
case "$os" in
  windows)
    # The JDK's own jar tool, so no zip program is needed; M leaves out the manifest.
    (cd "$WORK/image" && "$JAVA_BIN/jar" -cfM "$DIST/$artifact.zip" "$APP_NAME")
    ;;
  mac)
    ditto -c -k --keepParent "$WORK/image/$APP_NAME.app" "$DIST/$artifact.zip"
    ;;
  linux)
    tar -C "$WORK/image" -czf "$DIST/$artifact.tar.gz" "$APP_NAME"
    ;;
esac

if $installer; then
  log "Installer ($installer_type)"
  # Verbose, because the installer tools' own messages are only shown that way; the temporary
  # files are kept in the build directory for a look after a failure.
  "$JAVA_BIN/jpackage" --type "$installer_type" --dest "$WORK/installer" --verbose \
    --temp "$WORK/installer-temp" \
    "${common_options[@]}" "${installer_options[@]}"
  for file in "$WORK/installer"/*; do
    extension="${file##*.}"
    cp "$file" "$DIST/$artifact.$extension"
  done
fi

log "Done"
ls -l "$DIST"

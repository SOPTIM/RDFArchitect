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
# Starts the app that desktop/build.sh built for the system this runs on, without a window, and
# checks that it serves its pages and its API, writes a workspace file and stops by itself after
# its last window.
#
# Usage: desktop/smoke-test.sh
#
# Needs curl. On Windows it runs in Git Bash.

set -euo pipefail

IMAGE="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)/build/image"
START_SECONDS=120
STOP_SECONDS=60

DATA="$(mktemp -d)"
case "$(uname -s)" in
  Linux*) program="$IMAGE/RDFArchitect/bin/RDFArchitect" ;;
  Darwin*) program="$IMAGE/RDFArchitect.app/Contents/MacOS/RDFArchitect" ;;
  MINGW*|MSYS*)
    program="$IMAGE/RDFArchitect/RDFArchitect.exe"
    # The app and curl are Windows programs, which do not know the paths of Git Bash.
    DATA="$(cygpath -m "$DATA")"
    ;;
  *) echo "Unsupported operating system: $(uname -s)" >&2; exit 1 ;;
esac
if [[ ! -x "$program" ]]; then
  echo "Missing $program; run desktop/build.sh first." >&2
  exit 1
fi

instance="$DATA/run/instance.json"
body="$DATA/answer"
pid=""

fail() {
  echo "FAILED: $*" >&2
  echo "--- output" >&2
  cat "$DATA/output" >&2 || true
  echo "--- log" >&2
  cat "$DATA"/logs/*.log >&2 || true
  exit 1
}

stop() {
  if [[ -n "$pid" ]] && kill -0 "$pid" 2> /dev/null; then
    kill "$pid" 2> /dev/null || true
    wait "$pid" 2> /dev/null || true
  fi
  rm -rf "$DATA"
}
trap stop EXIT

# Checks that a request gets the status in $1; $2 says what is checked, the rest goes to curl.
expect() {
  local expected="$1" what="$2" status
  shift 2
  status="$(curl --silent --max-time 30 --output "$body" --write-out '%{http_code}' "$@" || true)"
  [[ "$status" == "$expected" ]] || fail "$what: expected $expected, got $status"
  echo "ok: $what"
}

"$program" --browser=none --port=0 "--data-dir=$DATA" --desktop.close-grace=1s \
  > "$DATA/output" 2>&1 &
pid=$!

for ((i = 0; i < START_SECONDS; i++)); do
  if [[ -s "$instance" ]]; then break; fi
  kill -0 "$pid" 2> /dev/null || fail "the app ended while starting"
  sleep 1
done
[[ -s "$instance" ]] || fail "the app did not start within $START_SECONDS s"

port="$(sed -n 's/.*"port":\([0-9]*\).*/\1/p' "$instance")"
sed -n 's/.*"token":"\([^"]*\)".*/X-RDFA-Desktop-Token: \1/p' "$instance" > "$DATA/header"
base="http://127.0.0.1:$port"
api=(--header "@$DATA/header" --cookie "$DATA/cookies" --cookie-jar "$DATA/cookies")

expect 401 "the API refuses a request without the token" "$base/api/desktop"
expect 200 "the API answers with the token" "${api[@]}" "$base/api/desktop"
expect 200 "the editor is served" "$base/mainpage"
grep -qi '<!doctype html' "$body" || fail "the editor is no HTML page"
expect 200 "the settings are served" "$base/env.js"
grep -q 'PUBLIC_DESKTOP' "$body" || fail "the settings do not name the desktop app"
expect 200 "a workspace is created" "${api[@]}" --request PUT "$base/api/workspaces/smoke"
expect 200 "its workspace file is written" "${api[@]}" "$base/api/desktop/workspaces/smoke/file"
[[ "$(head -c 2 "$body")" == PK ]] || fail "the workspace file is no archive"

window="$base/api/desktop/windows/smoke-test"
expect 200 "a window reports itself" "${api[@]}" --request POST "$window/heartbeat"
expect 204 "the window says goodbye" "${api[@]}" --request POST "$window/goodbye"

for ((i = 0; i < STOP_SECONDS; i++)); do
  if [[ ! -e "$instance" ]] && ! kill -0 "$pid" 2> /dev/null; then
    echo "ok: the app stops after its last window"
    exit 0
  fi
  sleep 1
done
fail "the app still runs $STOP_SECONDS s after its last window"

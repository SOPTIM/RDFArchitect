#!/usr/bin/env bash

set -euo pipefail

describe_regex='^v([0-9]+\.[0-9]+\.[0-9]+)(-([0-9]+)-g([0-9a-f]+))?$'

parse_describe_version() {
  local describe_output="$1"

  if [[ "$describe_output" =~ $describe_regex ]]; then
    if [[ -n "${BASH_REMATCH[3]:-}" && -n "${BASH_REMATCH[4]:-}" ]]; then
      printf '%s-%s-g%s\n' "${BASH_REMATCH[1]}" "${BASH_REMATCH[3]}" "${BASH_REMATCH[4]}"
    else
      printf '%s\n' "${BASH_REMATCH[1]}"
    fi
    return 0
  fi

  return 1
}

describe_version="$(git describe --tags --match 'v[0-9]*.[0-9]*.[0-9]*' --abbrev=8 2>/dev/null || true)"
app_version="$(parse_describe_version "$describe_version" || true)"

if [[ -z "$app_version" ]]; then
  app_version="0.0.0-SNAPSHOT"
fi

printf 'APP_VERSION=%s\n' "$app_version"
printf 'COMMIT_SHA=%s\n' "$(git rev-parse --short=8 HEAD)"

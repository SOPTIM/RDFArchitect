#!/usr/bin/env bash

set -euo pipefail

semver_regex='^v([0-9]+)\.([0-9]+)\.([0-9]+)$'

find_stable_tag() {
  while IFS= read -r tag; do
    if [[ "$tag" =~ $semver_regex ]]; then
      printf '%s.%s.%s\n' "${BASH_REMATCH[1]}" "${BASH_REMATCH[2]}" "${BASH_REMATCH[3]}"
      return 0
    fi
  done

  return 1
}

exact_version="$(find_stable_tag < <(git tag --points-at HEAD --sort=-version:refname) || true)"
latest_version="$(find_stable_tag < <(git tag --merged HEAD --sort=-version:refname) || true)"

if [[ -n "$exact_version" ]]; then
  app_version="$exact_version"
elif [[ -n "$latest_version" ]]; then
  app_version="${latest_version}-SNAPSHOT"
else
  app_version="0.0.0-SNAPSHOT"
fi

printf 'APP_VERSION=%s\n' "$app_version"
printf 'COMMIT_SHA=%s\n' "$(git rev-parse --short=8 HEAD)"

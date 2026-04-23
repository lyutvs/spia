#!/usr/bin/env bash
# Verifies: (a) the pushed tag's version matches `version=` in gradle.properties,
# and (b) the tagged commit is an ancestor of origin/main.
# Reads GITHUB_REF_NAME and GITHUB_SHA from env (set by GitHub Actions).
# Exit 0 = pass; exit 1 = fail with error via ::error:: annotation.

set -euo pipefail

: "${GITHUB_REF_NAME:?GITHUB_REF_NAME is required (e.g. v0.3.0)}"
: "${GITHUB_SHA:?GITHUB_SHA is required}"

TAG_VERSION="${GITHUB_REF_NAME#v}"

if [ -z "$TAG_VERSION" ] || [ "$TAG_VERSION" = "$GITHUB_REF_NAME" ]; then
  echo "::error::Tag '$GITHUB_REF_NAME' does not start with 'v' — refusing to proceed"
  exit 1
fi

FILE_VERSION=$(awk -F= '/^version=/ {gsub(/[[:space:]]/, "", $2); print $2}' gradle.properties)

if [ -z "$FILE_VERSION" ]; then
  echo "::error::Could not read version= from gradle.properties"
  exit 1
fi

if [ "$TAG_VERSION" != "$FILE_VERSION" ]; then
  echo "::error::Tag v$TAG_VERSION does not match gradle.properties version=$FILE_VERSION"
  exit 1
fi

echo "✓ Tag/version match: v$TAG_VERSION"

# Ancestor check: confirm the tagged commit is on main.
git fetch origin main --quiet

if ! git merge-base --is-ancestor "$GITHUB_SHA" origin/main; then
  echo "::error::Tagged commit $GITHUB_SHA is not an ancestor of origin/main"
  exit 1
fi

echo "✓ Tagged commit is on main"

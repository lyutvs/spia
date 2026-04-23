#!/usr/bin/env bash
# Extracts the CHANGELOG section for a given version (without the leading `v`).
# Usage: extract-changelog.sh <version>
# Example: extract-changelog.sh 0.2.0
# Prints the section body (lines between `## [<version>]` and the next `## [`) to stdout.
# If no matching section is found, prints a placeholder notice to stdout and logs a ::warning::.

set -euo pipefail

VERSION="${1:-}"
if [ -z "$VERSION" ]; then
  echo "::error::version argument required" >&2
  exit 1
fi

CHANGELOG_FILE="${CHANGELOG_FILE:-CHANGELOG.md}"
if [ ! -f "$CHANGELOG_FILE" ]; then
  echo "::error::$CHANGELOG_FILE not found" >&2
  exit 1
fi

NOTES=$(awk -v v="$VERSION" '
  index($0, "## [" v "]") == 1 { found=1; next }
  found && index($0, "## [") == 1 { exit }
  found { print }
' "$CHANGELOG_FILE")

# Trim leading blank lines
NOTES=$(printf '%s\n' "$NOTES" | awk 'NF{found=1} found{print}')

if [ -z "$(printf '%s' "$NOTES" | tr -d '[:space:]')" ]; then
  echo "::warning::No CHANGELOG section found for v$VERSION — using placeholder" >&2
  printf '%s\n' "_CHANGELOG section for v$VERSION was not found. Please edit this release manually before publishing._"
  exit 0
fi

printf '%s\n' "$NOTES"

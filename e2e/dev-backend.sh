#!/usr/bin/env bash
# Dev mode: publish SDK, build backend, run it in foreground.
# Use with `pnpm test:watch` in a second terminal.

set -euo pipefail

cd "$(git rev-parse --show-toplevel)"

if [[ -z "${JAVA_HOME:-}" || ! -d "$JAVA_HOME" ]]; then
    echo "JAVA_HOME must be set to a JDK 21 install" >&2
    exit 1
fi

./gradlew publishToMavenLocal --no-daemon
(cd e2e && ./gradlew :e2e-backend:build --no-daemon)
exec env "PATH=$PATH" bash -c "cd e2e && ./gradlew :e2e-backend:bootRun --no-daemon"

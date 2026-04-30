#!/usr/bin/env bash
# Full E2E pipeline. Invocable from anywhere — cd's to repo root.

set -euo pipefail

cd "$(git rev-parse --show-toplevel)"

# Step 0: validate JAVA_HOME (memory: JDK 25 breaks Gradle 8.10.2's Kotlin compiler)
if [[ -z "${JAVA_HOME:-}" || ! -d "$JAVA_HOME" ]]; then
    echo "[STEP 0 FAILED] JAVA_HOME must be set to a JDK 21 install" >&2
    exit 1
fi

# Step 1: publish SDK artifacts to mavenLocal (root build)
echo "==> Step 1: publishToMavenLocal"
./gradlew publishToMavenLocal --no-daemon \
    || { echo "[STEP 1 FAILED]" >&2; exit 1; }

# Step 2: build the e2e backend (independent Gradle build; KSP runs, TS files emitted)
echo "==> Step 2: build e2e-backend"
(cd e2e && ./gradlew :e2e-backend:build --no-daemon) \
    || { echo "[STEP 2 FAILED]" >&2; exit 1; }

# Step 3: start backend, wait for /actuator/health
echo "==> Step 3: boot backend on 18080"
(cd e2e && ./gradlew :e2e-backend:bootRun --no-daemon) > e2e/backend.log 2>&1 &
BACKEND_PID=$!
trap "kill $BACKEND_PID 2>/dev/null || true; wait $BACKEND_PID 2>/dev/null || true" EXIT

if ! e2e/scripts/wait-for-http.sh "http://localhost:${SPIA_E2E_BACKEND_PORT:-18080}/actuator/health" 60; then
    echo "[STEP 3 FAILED] backend did not become healthy; see e2e/backend.log" >&2
    tail -50 e2e/backend.log >&2 || true
    exit 1
fi

# Step 4: install + tsc + test
echo "==> Step 4: pnpm install + typecheck + test"
pushd e2e/e2e-client >/dev/null

pnpm install --frozen-lockfile \
    || { echo "[STEP 4a FAILED] pnpm install" >&2; exit 1; }
pnpm typecheck \
    || { echo "[STEP 4b FAILED] tsc on generated TS" >&2; exit 1; }
pnpm test \
    || { echo "[STEP 4c FAILED] vitest" >&2; exit 1; }

popd >/dev/null
echo "==> All steps passed"

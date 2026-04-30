#!/usr/bin/env bash
# Polls a URL until it returns HTTP 200 or the timeout elapses.
# Usage: wait-for-http.sh <url> <timeout-seconds>

set -euo pipefail

URL="${1:?URL required}"
TIMEOUT="${2:-60}"
ELAPSED=0

while (( ELAPSED < TIMEOUT )); do
    if curl -fsS -o /dev/null "$URL"; then
        echo "wait-for-http: $URL is up after ${ELAPSED}s"
        exit 0
    fi
    sleep 1
    ELAPSED=$((ELAPSED + 1))
done

echo "wait-for-http: timeout after ${TIMEOUT}s polling $URL" >&2
exit 1

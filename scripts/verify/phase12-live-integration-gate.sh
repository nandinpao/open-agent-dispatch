#!/usr/bin/env bash
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)"
cd "$ROOT_DIR"

if ! command -v java >/dev/null 2>&1; then
  echo "JDK 25 is required but java was not found." >&2
  exit 1
fi
JAVA_VERSION_OUTPUT="$(java -version 2>&1 | head -n 1)"
if ! echo "$JAVA_VERSION_OUTPUT" | grep -Eq '"25\.|version "25'; then
  echo "JDK 25 is required for the strict live gate. Found: $JAVA_VERSION_OUTPUT" >&2
  exit 1
fi

if ! command -v mvn >/dev/null 2>&1; then
  echo "Maven 3.9+ is required but mvn was not found." >&2
  exit 1
fi

if ! command -v docker >/dev/null 2>&1; then
  echo "Docker is required for PostgreSQL/Redis/Testcontainers live integration." >&2
  exit 1
fi

mvn -q -DskipTests compile
mvn -q test

cd ai-event-gateway-admin-ui
npm ci
npm run typecheck
NEXT_BUILD_TIMEOUT_MS="${NEXT_BUILD_TIMEOUT_MS:-900000}" npm run build
npm run stage9:browser-e2e

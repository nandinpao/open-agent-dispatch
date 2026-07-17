#!/usr/bin/env bash
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)"
EXPECTED_ARTIFACT_ID="control-plane-parent"
EXPECTED_VERSION="1.0.0-p25.7.4-p5-callback-transition-governance-fix"

fail() { echo "[ERROR] $*" >&2; exit 1; }

[[ -f "$ROOT_DIR/pom.xml" ]] || fail "root pom.xml not found"
grep -q "<artifactId>${EXPECTED_ARTIFACT_ID}</artifactId>" "$ROOT_DIR/pom.xml" || fail "root pom.xml is not ${EXPECTED_ARTIFACT_ID}"
grep -q "<version>${EXPECTED_VERSION}</version>" "$ROOT_DIR/pom.xml" || fail "root pom.xml version mismatch"
grep -q "<packaging>pom</packaging>" "$ROOT_DIR/pom.xml" || fail "root pom.xml must be packaging=pom"

expected_modules=(
  kernel
  contracts
  database-platform
  domain-events
  service-contracts
  integration-events
  incident
  event-processing
  agent-control
  task-orchestration
  execution-control
  adapter-action
  observability
  control-plane-app
  adapter-worker-app
)

for module in "${expected_modules[@]}"; do
  [[ -f "$ROOT_DIR/$module/pom.xml" ]] || fail "module pom missing: $module/pom.xml"
  grep -q "<module>${module}</module>" "$ROOT_DIR/pom.xml" || fail "root pom.xml does not declare module: $module"
  grep -q "<artifactId>${EXPECTED_ARTIFACT_ID}</artifactId>" "$ROOT_DIR/$module/pom.xml" || fail "$module does not use ${EXPECTED_ARTIFACT_ID} as parent"
  grep -q "<relativePath>../pom.xml</relativePath>" "$ROOT_DIR/$module/pom.xml" || fail "$module parent.relativePath must be ../pom.xml"
  grep -q "<version>${EXPECTED_VERSION}</version>" "$ROOT_DIR/$module/pom.xml" || fail "$module parent version mismatch"
done

if grep -R "repository.springsource.com" "$ROOT_DIR/pom.xml" "$ROOT_DIR"/*/pom.xml >/dev/null 2>&1; then
  fail "HTTP SpringSource repositories must not be declared in Core POMs"
fi

if grep -R "<relativePath/>" "$ROOT_DIR"/*/pom.xml >/dev/null 2>&1; then
  fail "Core module POMs must not disable local parent resolution with <relativePath/>"
fi

echo "I7.10.9 Core Maven reactor parent layout verification passed."

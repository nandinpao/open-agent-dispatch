#!/usr/bin/env bash
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "$0")/../.." && pwd)"
cd "${ROOT_DIR}"

APP_YML="ai-event-gateway-core-app/src/main/resources/application.yml"
PROD_YML="ai-event-gateway-core-app/src/main/resources/application-prod.yml"
VALIDATOR="ai-event-gateway-core-app/src/main/java/com/opensocket/aievent/core/config/CoreDeploymentModeValidator.java"
DOC="docs/i7.6-persistent-store-enforcement.md"

for f in "${APP_YML}" "${PROD_YML}" "${VALIDATOR}" "${DOC}"; do
  test -f "${f}" || { echo "ERROR: missing ${f}" >&2; exit 1; }
done

if grep -Eq '\$\{[^}]+:MEMORY\}|store: MEMORY|=MEMORY' "${APP_YML}" "${PROD_YML}" deploy/env/.env.core-prod.example 2>/dev/null; then
  echo "ERROR: production/common runtime config must not default source-of-truth stores to MEMORY" >&2
  exit 1
fi

for expected in \
  'CORE_OUTBOX_STORE:MYBATIS' \
  'CORE_INTEGRATION_EVENTS_STORE:MYBATIS' \
  'EVENT_DECISION_STORE:MYBATIS' \
  'EVENT_DEDUP_STORE:REDISSON' \
  'EVENT_DEDUP_SNAPSHOT_STORE:MYBATIS' \
  'INCIDENT_STORE:MYBATIS' \
  'INCIDENT_SUMMARY_STORE:MYBATIS' \
  'TASK_STORE:MYBATIS' \
  'TASK_CALLBACK_STORE:MYBATIS' \
  'GATEWAY_NODE_STORE:MYBATIS' \
  'AGENT_DIRECTORY_STORE:MYBATIS' \
  'ASSIGNMENT_STORE:MYBATIS' \
  'ROUTING_DECISION_STORE:MYBATIS' \
  'DISPATCH_REQUEST_STORE:MYBATIS' \
  'ADAPTER_ACTION_STORE:MYBATIS' \
  'ADAPTER_EXECUTOR_AUDIT_STORE:MYBATIS'; do
  grep -q "${expected}" "${APP_YML}" || { echo "ERROR: application.yml missing persistent default ${expected}" >&2; exit 1; }
done

if grep -R "matchIfMissing = true" \
  ai-event-gateway-core-adapter-action/src/main/java \
  ai-event-gateway-core-agent-control/src/main/java \
  ai-event-gateway-core-domain-events/src/main/java \
  ai-event-gateway-core-event-processing/src/main/java \
  ai-event-gateway-core-execution-control/src/main/java \
  ai-event-gateway-core-incident/src/main/java \
  ai-event-gateway-core-integration-events/src/main/java \
  ai-event-gateway-core-task-orchestration/src/main/java \
  | grep -E 'InMemory(Adapter|Agent|Gateway|Outbox|Event|Dedup|Task|Dispatch|Incident|Integration|Assignment|Routing)' >/dev/null; then
  echo "ERROR: InMemory repositories must not use matchIfMissing=true" >&2
  exit 1
fi

INMEMORY_FILES=$(find \
  ai-event-gateway-core-adapter-action/src/main/java \
  ai-event-gateway-core-agent-control/src/main/java \
  ai-event-gateway-core-domain-events/src/main/java \
  ai-event-gateway-core-event-processing/src/main/java \
  ai-event-gateway-core-execution-control/src/main/java \
  ai-event-gateway-core-incident/src/main/java \
  ai-event-gateway-core-integration-events/src/main/java \
  ai-event-gateway-core-task-orchestration/src/main/java \
  -name 'InMemory*.java' | sort)

for f in ${INMEMORY_FILES}; do
  grep -q '@Profile("!prod")' "${f}" || { echo "ERROR: ${f} missing @Profile(\"!prod\")" >&2; exit 1; }
  grep -q 'havingValue = "MEMORY"' "${f}" || { echo "ERROR: ${f} missing explicit MEMORY condition" >&2; exit 1; }
done

grep -q 'validateProductionPersistentStores' "${VALIDATOR}" || { echo "ERROR: validator missing persistent store validation" >&2; exit 1; }
grep -q 'event.dedup.store", "REDISSON"' "${VALIDATOR}" || { echo "ERROR: validator must require REDISSON dedup in prod" >&2; exit 1; }
grep -q 'dispatch.request-store", "MYBATIS"' "${VALIDATOR}" || { echo "ERROR: validator must require MYBATIS dispatch store in prod" >&2; exit 1; }

echo "I7.6 persistent store enforcement static verification passed."

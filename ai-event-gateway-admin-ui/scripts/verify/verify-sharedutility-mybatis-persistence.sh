#!/usr/bin/env bash
set -euo pipefail
ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)"
cd "$ROOT_DIR"

if grep -RIn --include='*.java' -E 'org\.springframework\.jdbc\.core\.(JdbcTemplate|NamedParameterJdbcTemplate)|\bJdbcTemplate\b|\bNamedParameterJdbcTemplate\b' ai-event-gateway-core-*/src/main/java ai-event-gateway-database-platform/src/main/java; then
  echo "Production code must not use JdbcTemplate; use SharedUtility/MyBatis DAO XML" >&2
  exit 1
fi
if find ai-event-gateway-core-* -path '*/src/main/java/*' -name 'Jdbc*Repository.java' | grep -q .; then
  echo "Jdbc*Repository production adapters are prohibited" >&2
  exit 1
fi
if grep -RIl --include='pom.xml' '<artifactId>spring-boot-starter-jdbc</artifactId>' ai-event-gateway-core-* ai-event-gateway-database-platform | grep -q .; then
  echo "JDBC/MyBatis infrastructure must be provided by SharedUtility through database-platform" >&2
  exit 1
fi
FEATURE_MODULES=(
  ai-event-gateway-core-domain-events
  ai-event-gateway-core-integration-events
  ai-event-gateway-core-event-processing
  ai-event-gateway-core-incident
  ai-event-gateway-core-task-orchestration
  ai-event-gateway-core-agent-control
  ai-event-gateway-core-execution-control
  ai-event-gateway-core-adapter-action
)
for module in "${FEATURE_MODULES[@]}"; do
  if grep -q '<artifactId>ai-event-gateway-database-platform</artifactId>' "$module/pom.xml"; then
    echo "$module must not depend on database-platform after P3" >&2
    exit 1
  fi
  grep -q "<artifactId>${module}</artifactId>" ai-event-gateway-database-platform/pom.xml || {
    echo "database-platform must implement Repository ports from ${module}" >&2
    exit 1
  }
done
grep -q '<artifactId>ai-event-gateway-database-platform</artifactId>' ai-event-gateway-core-app/pom.xml || {
  echo 'core-app must compose database-platform' >&2
  exit 1
}

declare -A DOMAIN=(
  [OutboxEvent]=domainevent
  [IntegrationEvent]=integrationevent
  [DedupStateSnapshot]=eventprocessing
  [EventDecision]=eventprocessing
  [Incident]=incident
  [IncidentOccurrenceSummary]=incident
  [AgentDirectory]=agent
  [GatewayNode]=agent
  [Task]=task
  [TaskAssignment]=task
  [RoutingDecision]=task
  [DispatchRequest]=execution
  [TaskCallback]=execution
  [AdapterAction]=adapter
  [AdapterExecutorAudit]=adapter
)
for dao in "${!DOMAIN[@]}"; do
  domain="${DOMAIN[$dao]}"
  java="ai-event-gateway-database-platform/src/main/java/com/opensocket/aievent/database/persistence/${domain}/dao/${dao}Dao.java"
  xml="ai-event-gateway-database-platform/src/main/resources/mybatis/postgresql/${domain}/${dao}Dao.xml"
  test -f "$java"
  test -f "$xml"
  grep -Fq "namespace=\"com.opensocket.aievent.database.persistence.${domain}.dao.${dao}Dao\"" "$xml"
done

test ! -e ai-event-gateway-core-db-support
if grep -RIn --include='*.java' 'com.opensocket.aievent.core.infrastructure.mybatis' ai-event-gateway-core-*/src/main/java ai-event-gateway-database-platform/src/main/java; then
  echo "Legacy db-support MyBatis packages are prohibited after P2" >&2
  exit 1
fi
if grep -RIn --include='application-*.yml' -E '(^|:)\s*POSTGRES\s*$|:[[:space:]]*\$\{[^}]+:POSTGRES\}' ai-event-gateway-core-app/src/main/resources; then
  echo "Current runtime profiles must use MYBATIS rather than POSTGRES repository mode" >&2
  exit 1
fi
if grep -RIn -E '(CORE_OUTBOX_STORE|CORE_INTEGRATION_EVENTS_STORE|EVENT_DECISION_STORE|EVENT_DEDUP_SNAPSHOT_STORE|INCIDENT_SUMMARY_STORE|ASSIGNMENT_STORE|ROUTING_DECISION_STORE|ADAPTER_EXECUTOR_AUDIT_STORE|INCIDENT_STORE|TASK_STORE|TASK_CALLBACK_STORE|GATEWAY_NODE_STORE|AGENT_DIRECTORY_STORE|DISPATCH_REQUEST_STORE|ADAPTER_ACTION_STORE)=POSTGRES' deploy; then
  echo "Active deployment examples must use MYBATIS repository mode" >&2
  exit 1
fi
test "$(find ai-event-gateway-database-platform/src/main/java/com/opensocket/aievent/database/persistence -path '*/repository/Mybatis*Repository.java' | wc -l | tr -d ' ')" = "15"
test "$(find ai-event-gateway-database-platform/src/main/java/com/opensocket/aievent/database/persistence -path '*/converter/*PersistenceConverter.java' | wc -l | tr -d ' ')" = "15"
if find ai-event-gateway-core-* -path '*/src/main/java/*' -name 'Mybatis*Repository.java' | grep -q .; then
  echo 'Feature modules must not own MyBatis Repository adapters after P3' >&2
  exit 1
fi
if grep -RIn --include='*.java' 'com.opensocket.aievent.database.persistence' ai-event-gateway-core-*/src/main/java; then
  echo 'Feature production code must not import database-platform types after P3' >&2
  exit 1
fi
echo "SharedUtility/MyBatis DAO/PO/Repository persistence governance passed."

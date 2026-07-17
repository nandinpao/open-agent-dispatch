#!/usr/bin/env bash
set -euo pipefail
ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)"
cd "$ROOT_DIR"

if grep -RIn --include='*.java' -E 'org\.springframework\.jdbc\.core\.(JdbcTemplate|NamedParameterJdbcTemplate)|\bJdbcTemplate\b|\bNamedParameterJdbcTemplate\b' */src/main/java/com/opensocket/aievent/core database-platform/src/main/java; then
  echo "Production code must not use JdbcTemplate; use SharedUtility/MyBatis DAO XML" >&2
  exit 1
fi
if find */src/main/java/com/opensocket/aievent/core -path '*/src/main/java/*' -name 'Jdbc*Repository.java' 2>/dev/null | grep -q .; then
  echo "Jdbc*Repository production adapters are prohibited" >&2
  exit 1
fi
if grep -RIl --include='pom.xml' '<artifactId>spring-boot-starter-jdbc</artifactId>' . | grep -q .; then
  echo "JDBC/MyBatis infrastructure must be provided by SharedUtility through database-platform" >&2
  exit 1
fi
FEATURE_MODULES=(
  domain-events
  integration-events
  event-processing
  incident
  task-orchestration
  agent-control
  execution-control
)
for module in "${FEATURE_MODULES[@]}"; do
  if grep -q '<artifactId>database-platform</artifactId>' "$module/pom.xml"; then
    echo "$module must not depend on database-platform after P3" >&2
    exit 1
  fi
  grep -q "<artifactId>${module}</artifactId>" database-platform/pom.xml || {
    echo "database-platform must implement Repository ports from ${module}" >&2
    exit 1
  }
done
grep -q '<artifactId>database-platform</artifactId>' control-plane-app/pom.xml || {
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
  java="data-model/src/main/java/com/opensocket/aievent/database/persistence/${domain}/dao/${dao}Dao.java"
  xml="database-platform/src/main/resources/mybatis/postgresql/${domain}/${dao}Dao.xml"
  test -f "$java"
  test -f "$xml"
  grep -Fq "namespace=\"com.opensocket.aievent.database.persistence.${domain}.dao.${dao}Dao\"" "$xml"
done

test ! -e ai-event-gateway-core-db-support
if grep -RIn --include='*.java' 'com.opensocket.aievent.core.infrastructure.mybatis' */src/main/java/com/opensocket/aievent/core database-platform/src/main/java; then
  echo "Legacy db-support MyBatis packages are prohibited after P2" >&2
  exit 1
fi
if grep -RIn --include='application-*.yml' -E '(^|:)\s*POSTGRES\s*$|:[[:space:]]*\$\{[^}]+:POSTGRES\}' control-plane-app/src/main/resources; then
  echo "Current runtime profiles must use MYBATIS rather than POSTGRES repository mode" >&2
  exit 1
fi
if grep -RIn -E '(CORE_OUTBOX_STORE|CORE_INTEGRATION_EVENTS_STORE|EVENT_DECISION_STORE|EVENT_DEDUP_SNAPSHOT_STORE|INCIDENT_SUMMARY_STORE|ASSIGNMENT_STORE|ROUTING_DECISION_STORE|ADAPTER_EXECUTOR_AUDIT_STORE|INCIDENT_STORE|TASK_STORE|TASK_CALLBACK_STORE|GATEWAY_NODE_STORE|AGENT_DIRECTORY_STORE|DISPATCH_REQUEST_STORE|ADAPTER_ACTION_STORE)=POSTGRES' deploy; then
  echo "Active deployment examples must use MYBATIS repository mode" >&2
  exit 1
fi
test "$(find database-platform/src/main/java/com/opensocket/aievent/database/persistence -path '*/repository/Mybatis*Repository.java' | wc -l | tr -d ' ')" = "23"
test "$(find database-platform/src/main/java/com/opensocket/aievent/database/persistence -path '*/converter/*PersistenceConverter.java' | wc -l | tr -d ' ')" = "23"
if find */src/main/java/com/opensocket/aievent/core -path '*/src/main/java/*' -name 'Mybatis*Repository.java' 2>/dev/null | grep -q .; then
  echo 'Feature modules must not own MyBatis Repository adapters after P3' >&2
  exit 1
fi
if grep -RIn --include='*.java' '^import com.opensocket.aievent.database.persistence' adapter-action/src/main/java/com/opensocket/aievent/core agent-control/src/main/java/com/opensocket/aievent/core contracts/src/main/java/com/opensocket/aievent/core control-plane-app/src/main/java/com/opensocket/aievent/core domain-events/src/main/java/com/opensocket/aievent/core event-processing/src/main/java/com/opensocket/aievent/core execution-control/src/main/java/com/opensocket/aievent/core incident/src/main/java/com/opensocket/aievent/core integration-events/src/main/java/com/opensocket/aievent/core kernel/src/main/java/com/opensocket/aievent/core observability/src/main/java/com/opensocket/aievent/core task-orchestration/src/main/java/com/opensocket/aievent/core; then
  echo 'Feature production code must not import database-platform types after P3' >&2
  exit 1
fi
echo "SharedUtility/MyBatis DAO/PO/Repository persistence governance passed."

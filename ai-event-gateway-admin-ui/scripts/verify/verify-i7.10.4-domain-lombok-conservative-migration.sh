#!/usr/bin/env bash
set -euo pipefail

ROOT="$(cd "$(dirname "$0")/../.." && pwd)"

fail() {
  echo "[FAIL] $1" >&2
  exit 1
}

require_file() {
  [[ -f "$ROOT/$1" ]] || fail "Missing file: $1"
}

require_pattern() {
  local file="$1"
  local pattern="$2"
  grep -qE "$pattern" "$ROOT/$file" || fail "Expected pattern '$pattern' in $file"
}

for f in \
  "ai-event-gateway-core-data-model/src/main/java/com/opensocket/aievent/core/incident/Incident.java" \
  "ai-event-gateway-core-data-model/src/main/java/com/opensocket/aievent/core/summary/IncidentOccurrenceSummary.java" \
  "ai-event-gateway-core-data-model/src/main/java/com/opensocket/aievent/core/decision/EventDecisionRecord.java" \
  "ai-event-gateway-core-data-model/src/main/java/com/opensocket/aievent/core/dedup/DedupState.java" \
  "ai-event-gateway-core-data-model/src/main/java/com/opensocket/aievent/core/gateway/GatewayNode.java" \
  "ai-event-gateway-core-data-model/src/main/java/com/opensocket/aievent/core/agent/AgentSnapshot.java" \
  "ai-event-gateway-core-data-model/src/main/java/com/opensocket/aievent/core/integration/IntegrationEventRecord.java" \
  "ai-event-gateway-core-data-model/src/main/java/com/opensocket/aievent/core/outbox/OutboxEventRecord.java"; do
  require_file "$f"
  require_pattern "$f" '^import lombok.Getter;'
  require_pattern "$f" '^import lombok.ToString;'
  require_pattern "$f" '^@Getter$'
  require_pattern "$f" '^@ToString\(onlyExplicitlyIncluded = true\)$'
  require_pattern "$f" '@ToString\.Include'
  if grep -qE '^import lombok\.(Data|Setter|Builder|AllArgsConstructor|RequiredArgsConstructor|Value);' "$ROOT/$f"; then
    fail "Forbidden Lombok mutability annotation import in $f"
  fi
  if grep -qE '^@(Data|Setter|Builder|AllArgsConstructor|RequiredArgsConstructor|Value)(\(|$)' "$ROOT/$f"; then
    fail "Forbidden Lombok mutability annotation usage in $f"
  fi
  if grep -qE '@ToString\s*$' "$ROOT/$f"; then
    fail "Unrestricted @ToString found in $f"
  fi
  if grep -qE '@ToString\.Include\s*$' "$ROOT/$f" && grep -qE '@ToString\.Include\s*\n\s*private\s+.*(payload|token|secret|raw|metadata|lastError|latestPayloadJson)' "$ROOT/$f"; then
    fail "Sensitive field included in toString in $f"
  fi
done

# The high-risk lifecycle packages must remain free from Lombok domain migration.
for dir in \
  "ai-event-gateway-core-execution-control/src/main/java/com/opensocket/aievent/core/dispatch" \
  "ai-event-gateway-core-execution-control/src/main/java/com/opensocket/aievent/core/callback" \
  "ai-event-gateway-core-task-orchestration/src/main/java/com/opensocket/aievent/core/task" \
  "ai-event-gateway-core-task-orchestration/src/main/java/com/opensocket/aievent/core/assignment"; do
  [[ -d "$ROOT/$dir" ]] || continue
  if grep -R --include='*.java' -nE '^@(Getter|Setter|Data|Builder|ToString|AllArgsConstructor|RequiredArgsConstructor|Value)(\(|$)' "$ROOT/$dir" >/tmp/i7104-domain-lombok-forbidden.txt; then
    cat /tmp/i7104-domain-lombok-forbidden.txt >&2
    fail "Lombok annotation found in excluded lifecycle package: $dir"
  fi
done

require_file "docs/i7.10.4-domain-lombok-conservative-migration.md"
require_file "release/i7.10/domain-lombok-conservative-policy.md"

echo "I7.10.4 Core domain Lombok conservative migration verification passed."

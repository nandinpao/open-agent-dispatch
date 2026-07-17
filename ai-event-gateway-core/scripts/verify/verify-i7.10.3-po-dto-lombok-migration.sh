#!/usr/bin/env bash
set -euo pipefail

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)"
DB_PLATFORM="$ROOT/data-model/src/main/java"

fail() {
  echo "[I7.10.3] $*" >&2
  exit 1
}

# 1. PO classes must use Lombok boilerplate reduction.
PO_COUNT=$(find "$DB_PLATFORM" -path '*/po/*.java' | wc -l | tr -d ' ')
[ "$PO_COUNT" -gt 0 ] || fail "No PO classes found under data-model."

MISSING_PO=$(find "$DB_PLATFORM" -path '*/po/*.java' -print0 | xargs -0 grep -L '@Getter' || true)
[ -z "$MISSING_PO" ] || fail "PO classes missing @Getter: $MISSING_PO"
MISSING_PO=$(find "$DB_PLATFORM" -path '*/po/*.java' -print0 | xargs -0 grep -L '@Setter' || true)
[ -z "$MISSING_PO" ] || fail "PO classes missing @Setter: $MISSING_PO"
MISSING_PO=$(find "$DB_PLATFORM" -path '*/po/*.java' -print0 | xargs -0 grep -L '@NoArgsConstructor' || true)
[ -z "$MISSING_PO" ] || fail "PO classes missing @NoArgsConstructor: $MISSING_PO"

# 2. Do not use @Data anywhere; it is too broad for PO/DTO/Domain in this project.
if grep -R --include='*.java' -n '@Data\b' "$ROOT"; then
  fail "@Data is forbidden by the OpenSocket Lombok policy."
fi

# 3. Simple hand-written JavaBean accessors should be removed from PO classes.
# Custom defensive setters are allowed, such as Math.max guards.
if find "$DB_PLATFORM" -path '*/po/*.java' -print0 | xargs -0 grep -nE 'public\s+[^=;{}]+\s+(get[A-Z]|is[A-Z])' ; then
  fail "PO classes still contain hand-written simple getters."
fi
if find "$DB_PLATFORM" -path '*/po/*.java' -print0 | xargs -0 grep -nE 'public\s+void\s+set[A-Z]' | grep -v 'Math.max' ; then
  fail "PO classes still contain hand-written setters other than approved custom guards."
fi

# 4. Selected Core JavaBean DTOs must use Lombok.
for dto in \
  "$ROOT/data-model/src/main/java/com/opensocket/aievent/core/event/EventIntakeRequest.java" \
  "$ROOT/data-model/src/main/java/com/opensocket/aievent/core/callback/TaskCallbackRequest.java" \
  "$ROOT/data-model/src/main/java/com/opensocket/aievent/core/api/ApiErrorResponse.java" \
  "$ROOT/data-model/src/main/java/com/opensocket/aievent/core/dispatch/GatewayDispatchResponse.java" \
  "$ROOT/data-model/src/main/java/com/opensocket/aievent/core/action/executor/mcp/McpExecutorRequest.java" \
  "$ROOT/data-model/src/main/java/com/opensocket/aievent/core/action/executor/mcp/McpExecutorResponse.java" \
  "$ROOT/data-model/src/main/java/com/opensocket/aievent/core/action/executor/issue/IssueExecutorRequest.java" \
  "$ROOT/data-model/src/main/java/com/opensocket/aievent/core/action/executor/issue/IssueExecutorResponse.java"; do
  [ -f "$dto" ] || fail "Missing expected DTO: $dto"
  grep -q '@Getter' "$dto" || fail "DTO missing @Getter: $dto"
  grep -q '@Setter' "$dto" || fail "DTO missing @Setter: $dto"
done

# 5. DTOs with defensive payload copy must keep custom setters.
grep -q 'new LinkedHashMap<>(payload)' "$ROOT/data-model/src/main/java/com/opensocket/aievent/core/callback/TaskCallbackRequest.java" \
  || fail "TaskCallbackRequest must preserve defensive payload copy."
grep -q 'new LinkedHashMap<>(payload)' "$ROOT/data-model/src/main/java/com/opensocket/aievent/core/action/executor/mcp/McpExecutorRequest.java" \
  || fail "McpExecutorRequest must preserve defensive payload copy."
grep -q 'new LinkedHashMap<>(payload)' "$ROOT/data-model/src/main/java/com/opensocket/aievent/core/action/executor/issue/IssueExecutorRequest.java" \
  || fail "IssueExecutorRequest must preserve defensive payload copy."

# 6. Domain state objects must not be widened with class-level setters or @Data.
DOMAIN_DIRS=(
  "$ROOT/execution-control/src/main/java"
  "$ROOT/task-orchestration/src/main/java"
  "$ROOT/agent-control/src/main/java"
)
for dir in "${DOMAIN_DIRS[@]}"; do
  [ -d "$dir" ] || continue
  if grep -R --include='*.java' -nE '@Data\b|@Setter\b' "$dir"; then
    fail "Domain lifecycle modules must not receive @Data or class-level @Setter in I7.10.3."
  fi
done

echo "I7.10.3 Core PO / DTO Lombok migration verification passed."

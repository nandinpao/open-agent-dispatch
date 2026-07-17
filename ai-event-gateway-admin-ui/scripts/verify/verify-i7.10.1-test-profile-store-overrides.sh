#!/usr/bin/env bash
set -euo pipefail
ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)"
cd "$ROOT_DIR"

fail() { echo "[I7.10.1][CORE][FAIL] $1" >&2; exit 1; }
TEST_YML="ai-event-gateway-core-app/src/test/resources/application-test.yml"
require_file() { [[ -f "$1" ]] || fail "Missing required file: $1"; }
require_file "$TEST_YML"

# Ordinary SpringBootTest smoke tests run with activeProfiles=test and disabled PostgreSQL/Redis.
# Therefore every source-of-truth repository that is needed by the full application context must
# be explicitly set to MEMORY/NONE in the test profile. Otherwise the production MYBATIS/REDISSON
# defaults from application.yml will leak into tests and require missing DAO mapper beans.

grep -q '^pg:$' "$TEST_YML" || fail "test profile must disable PostgreSQL infrastructure"
grep -q '^redis:$' "$TEST_YML" || fail "test profile must disable Redis infrastructure"

grep -q 'store: MEMORY' "$TEST_YML" || fail "test profile must contain explicit MEMORY stores"

grep -q 'core:' "$TEST_YML" || fail "test profile missing core section"
grep -q 'integration-events:' "$TEST_YML" || fail "test profile must set core.integration-events"
awk '/^core:/{in_core=1} in_core && /integration-events:/{seen=1} seen && /store: MEMORY/{found=1} END{exit found?0:1}' "$TEST_YML" \
  || fail "test profile must set core.integration-events.store=MEMORY"

grep -q 'outbox:' "$TEST_YML" || fail "test profile must set core.outbox"
awk '/^core:/{in_core=1} in_core && /outbox:/{seen=1} seen && /store: MEMORY/{found=1} END{exit found?0:1}' "$TEST_YML" \
  || fail "test profile must set core.outbox.store=MEMORY"

for section in \
  'event:' \
  'incident:' \
  'task:' \
  'gateway-nodes:' \
  'agent-directory:' \
  'assignment:' \
  'routing:' \
  'dispatch:' \
  'adapter-actions:' \
  'adapter-executor:'; do
  grep -q "^${section}" "$TEST_YML" || fail "test profile missing ${section} override"
done

if grep -Eq 'store: MYBATIS|store: REDISSON|:MYBATIS|:REDISSON' "$TEST_YML"; then
  fail "test profile must not use MYBATIS/REDISSON defaults for ordinary smoke tests"
fi

echo "I7.10.1 Core test profile store override verification passed."

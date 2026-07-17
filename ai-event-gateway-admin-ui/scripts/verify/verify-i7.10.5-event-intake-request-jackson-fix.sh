#!/usr/bin/env bash
set -euo pipefail
ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)"
REQ="$ROOT_DIR/ai-event-gateway-core-data-model/src/main/java/com/opensocket/aievent/core/event/EventIntakeRequest.java"
TEST_CFG="$ROOT_DIR/ai-event-gateway-core-app/src/test/resources/application-test.yml"
if [[ ! -f "$REQ" ]]; then
  echo "EventIntakeRequest.java not found: $REQ" >&2
  exit 1
fi
if grep -q "import lombok.AllArgsConstructor;" "$REQ" || grep -q "import lombok.Builder;" "$REQ"; then
  echo "EventIntakeRequest must not import Lombok @AllArgsConstructor or @Builder" >&2
  exit 1
fi
if grep -q "@AllArgsConstructor" "$REQ" || grep -q "@Builder" "$REQ"; then
  echo "EventIntakeRequest must remain JavaBean-style for Jackson request binding" >&2
  exit 1
fi
grep -q "@NoArgsConstructor" "$REQ" || { echo "EventIntakeRequest must keep @NoArgsConstructor" >&2; exit 1; }
grep -q "@Getter" "$REQ" || { echo "EventIntakeRequest must keep @Getter" >&2; exit 1; }
grep -q "@Setter" "$REQ" || { echo "EventIntakeRequest must keep @Setter" >&2; exit 1; }
if [[ -f "$TEST_CFG" ]]; then
  grep -q "core:" "$TEST_CFG" || { echo "application-test.yml must define core test overrides" >&2; exit 1; }
  grep -q "integration-events:" "$TEST_CFG" || { echo "application-test.yml must override core.integration-events for tests" >&2; exit 1; }
  grep -q "store: MEMORY" "$TEST_CFG" || { echo "application-test.yml must keep MEMORY stores for non-prod tests" >&2; exit 1; }
fi
echo "I7.10.5 EventIntakeRequest Jackson binding verification passed."

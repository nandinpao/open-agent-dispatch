#!/usr/bin/env bash
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "$0")/../.." && pwd)"
cd "$ROOT_DIR"

require_file() {
  local file="$1"
  if [[ ! -f "$file" ]]; then
    echo "Missing required file: $file" >&2
    exit 1
  fi
}

require_pattern() {
  local file="$1"
  local pattern="$2"
  if ! grep -qE "$pattern" "$file"; then
    echo "Missing pattern in $file: $pattern" >&2
    exit 1
  fi
}

require_file data-model/src/main/java/com/opensocket/aievent/core/dispatch/DispatchStatusTransition.java
require_file data-model/src/main/java/com/opensocket/aievent/core/task/TaskExecutionStateTransition.java
require_file database-platform/src/main/resources/mybatis/postgresql/execution/DispatchRequestDao.xml
require_file database-platform/src/main/resources/mybatis/postgresql/task/TaskDao.xml

require_pattern data-model/src/main/java/com/opensocket/aievent/core/dispatch/DispatchRequestRepository.java "transitionStatus\(DispatchStatusTransition"
require_pattern database-platform/src/main/resources/mybatis/postgresql/execution/DispatchRequestDao.xml "<update id=\"transitionStatus\">"
require_pattern database-platform/src/main/resources/mybatis/postgresql/execution/DispatchRequestDao.xml "and status in"
require_pattern database-platform/src/main/resources/mybatis/postgresql/execution/DispatchRequestDao.xml "and attempt_count = #\{expectedAttemptNo\}"
require_pattern database-platform/src/main/resources/mybatis/postgresql/execution/DispatchRequestDao.xml "and dispatch_token = #\{expectedDispatchToken\}"

require_pattern data-model/src/main/java/com/opensocket/aievent/core/task/TaskRepository.java "transitionExecutionState\(TaskExecutionStateTransition"
require_pattern database-platform/src/main/resources/mybatis/postgresql/task/TaskDao.xml "<update id=\"transitionExecutionState\">"
require_pattern database-platform/src/main/resources/mybatis/postgresql/task/TaskDao.xml "and status in"

require_pattern execution-control/src/main/java/com/opensocket/aievent/core/callback/TaskCallbackService.java "transitionDispatch\(type, dispatchRequest, request, now\)"
require_pattern execution-control/src/main/java/com/opensocket/aievent/core/callback/TaskCallbackService.java "transitionExecutionState\(taskTransition"
require_pattern execution-control/src/main/java/com/opensocket/aievent/core/callback/DispatchRecoveryService.java "dispatchRepository.transitionStatus\(transition\)"
require_pattern execution-control/src/main/java/com/opensocket/aievent/core/callback/DispatchRecoveryService.java "transition.setExpectedAttemptNo\(request.getAttemptCount\(\)\)"

require_pattern control-plane-app/src/test/java/com/opensocket/aievent/core/DispatchCallbackReliabilityTest.java "shouldNotAllowLateAckToOverwriteCompletedResult"
require_pattern control-plane-app/src/test/java/com/opensocket/aievent/core/DispatchCallbackReliabilityTest.java "shouldNotAllowTimeoutRecoveryToOverwriteCompletedResult"

echo "I3 Callback / Recovery SQL CAS static verification passed."

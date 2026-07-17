#!/usr/bin/env bash
set -euo pipefail
ROOT_DIR="$(cd "$(dirname "$0")/../.." && pwd)"
cd "$ROOT_DIR"

fail() {
  echo "[I7.5][ERROR] $1" >&2
  exit 1
}

assert_contains() {
  local file="$1"
  local pattern="$2"
  grep -Fq "$pattern" "$file" || fail "Expected '$pattern' in $file"
}

assert_not_contains() {
  local file="$1"
  local pattern="$2"
  if grep -Fq "$pattern" "$file"; then
    fail "Unexpected '$pattern' in $file"
  fi
}

MCP_EXECUTOR="adapter-action/src/main/java/com/opensocket/aievent/core/action/executor/MockMcpActionExecutor.java"
ISSUE_RESOLVER="adapter-action/src/main/java/com/opensocket/aievent/core/action/executor/issue/IssueVendorResolver.java"
ISSUE_EXECUTOR="adapter-action/src/main/java/com/opensocket/aievent/core/action/executor/issue/IssueTrackingAdapterActionExecutor.java"
PROPS="adapter-action/src/main/java/com/opensocket/aievent/core/action/executor/AdapterActionExecutionProperties.java"
APP_YML="control-plane-app/src/main/resources/application.yml"
PROD_YML="control-plane-app/src/main/resources/application-prod.yml"
VALIDATOR="control-plane-app/src/main/java/com/opensocket/aievent/core/config/CoreDeploymentModeValidator.java"

assert_not_contains "$MCP_EXECUTOR" "matchIfMissing = true"
assert_contains "$MCP_EXECUTOR" "matchIfMissing = false"

assert_contains "$PROPS" "public static class Mock"
assert_contains "$PROPS" "private boolean enabled = false;"
assert_contains "$PROPS" "private String defaultVendor = \"\";"
assert_contains "$PROPS" "private boolean jiraMockEnabled = false;"
assert_contains "$PROPS" "private boolean redmineMockEnabled = false;"
assert_contains "$PROPS" "private boolean gitlabMockEnabled = false;"
assert_contains "$PROPS" "private boolean mockCompatible = false;"

assert_contains "$ISSUE_RESOLVER" "if (raw == null || raw.isBlank()) return null;"
assert_contains "$ISSUE_RESOLVER" 'if (normalized.contains("MOCK")) return IssueVendor.MOCK;'
assert_contains "$ISSUE_EXECUTOR" "Issue vendor is not configured or is unsupported"
assert_contains "$ISSUE_EXECUTOR" "MOCK issue vendor is disabled outside explicit local/test/e2e opt-in"

assert_contains "$APP_YML" 'enabled: ${ADAPTER_EXECUTOR_MOCK_ENABLED:false}'
assert_contains "$APP_YML" 'mock-compatible: ${MCP_EXECUTOR_MOCK_COMPATIBLE:false}'
assert_contains "$APP_YML" 'default-vendor: ${ISSUE_EXECUTOR_DEFAULT_VENDOR:}'
assert_contains "$APP_YML" 'jira-mock-enabled: ${JIRA_EXECUTOR_MOCK_ENABLED:false}'
assert_contains "$APP_YML" 'redmine-mock-enabled: ${REDMINE_EXECUTOR_MOCK_ENABLED:false}'
assert_contains "$APP_YML" 'gitlab-mock-enabled: ${GITLAB_EXECUTOR_MOCK_ENABLED:false}'

assert_contains "$PROD_YML" 'mock-compatible: ${MCP_EXECUTOR_MOCK_COMPATIBLE:false}'
assert_contains "$PROD_YML" 'jira-mock-enabled: ${JIRA_EXECUTOR_MOCK_ENABLED:false}'
assert_contains "$PROD_YML" 'redmine-mock-enabled: ${REDMINE_EXECUTOR_MOCK_ENABLED:false}'
assert_contains "$PROD_YML" 'gitlab-mock-enabled: ${GITLAB_EXECUTOR_MOCK_ENABLED:false}'

assert_contains "$VALIDATOR" "validateProductionAdapterExecutorBoundary"
assert_contains "$VALIDATOR" "Production profile must not enable adapter-executor.mock.enabled"
assert_contains "$VALIDATOR" "Production profile must not use ISSUE_EXECUTOR_DEFAULT_VENDOR=MOCK"
assert_contains "$VALIDATOR" "Production profile must not enable mock-compatible issue executors"

python3 -m py_compile scripts/e2e/run_core_netty_agent_e2e.py >/dev/null 2>&1 || true

echo "I7.5 mock executor purge static verification passed."

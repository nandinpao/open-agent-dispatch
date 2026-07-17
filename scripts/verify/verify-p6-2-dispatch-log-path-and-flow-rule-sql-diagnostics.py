#!/usr/bin/env python3
from pathlib import Path

ROOT = Path(__file__).resolve().parents[2]
checks = []


def require(path, text):
    p = ROOT / path
    if not p.exists():
        raise SystemExit(f"Missing file: {path}")
    content = p.read_text()
    if text not in content:
        raise SystemExit(f"Missing expected text in {path}: {text}")
    checks.append(f"{path}: {text}")

require('deploy/docker-compose.local.yml', 'opendispatch-core-logs:/logs')
require('deploy/docker-compose.local.yml', 'opendispatch-netty-logs:/logs')
require('deploy/docker-compose.ci.yml', 'opendispatch-ci-core-logs:/logs')
require('deploy/docker-compose.release.yml', '${OPENDISPATCH_LOG_ROOT:-../runtime/logs}/core:/logs')
require('deploy/env/.env.local.example', 'OPENDISPATCH_LOG_ROOT=../.local/opendispatch-logs')
require('ai-event-gateway-core/control-plane-app/src/main/resources/logback-spring.xml', 'com.opensocket.aievent.database.persistence.dispatch.flow')
require('ai-event-gateway-core/control-plane-app/src/main/java/com/opensocket/aievent/core/dispatch/flow/DispatchFlowManagementService.java', 'dispatch_flow_list_failed')
require('ai-event-gateway-core/control-plane-app/src/main/java/com/opensocket/aievent/core/dispatch/flow/DispatchFlowManagementService.java', 'dispatch_flow_list_loaded')
require('ai-event-gateway-core/control-plane-app/src/main/java/com/opensocket/aievent/core/dispatch/flow/DispatchFlowReadinessService.java', 'dispatch_flow_dry_run_started')
require('ai-event-gateway-core/control-plane-app/src/main/java/com/opensocket/aievent/core/dispatch/flow/DispatchFlowReadinessService.java', 'dispatch_flow_dry_run_sql_failed')
require('ai-event-gateway-core/database-platform/src/main/java/com/opensocket/aievent/database/persistence/dispatch/flow/JdbcFlowRuleRoutingRepository.java', 'flow_rule_runtime_repository_no_match')
require('ai-event-gateway-core/database-platform/src/main/java/com/opensocket/aievent/database/persistence/dispatch/flow/JdbcFlowRuleRoutingRepository.java', 'flow_rule_runtime_repository_sql_failed')
require('scripts/diagnostics/collect-dispatch-logs.sh', 'opendispatch-dispatch-logs')
require('docs/P6_2_DISPATCH_LOG_PATH_AND_FLOW_RULE_SQL_DIAGNOSTICS/README.md', 'flow_rule_runtime_repository_no_match')

print('P6.2 dispatch log path and Flow Rule SQL diagnostics verification passed:')
for item in checks:
    print(f' - {item}')

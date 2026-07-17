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

require('deploy/docker-compose.local.yml', 'LOG_LEVEL_DISPATCH_TRACE: ${LOG_LEVEL_DISPATCH_TRACE:-DEBUG}')
require('deploy/docker-compose.local.yml', 'LOG_LEVEL_GATEWAY: ${LOG_LEVEL_GATEWAY:-DEBUG}')
require('deploy/env/.env.local.example', 'LOG_LEVEL_DISPATCH_TRACE=DEBUG')
require('deploy/env/.env.local.example', 'LOG_LEVEL_GATEWAY=DEBUG')
require('ai-event-gateway-core/control-plane-app/src/main/java/com/opensocket/aievent/core/dispatch/flow/DispatchDiagnosticsStartupLogger.java', 'dispatch_diagnostics_logging_ready')
require('ai-event-gateway-core/task-orchestration/src/main/java/com/opensocket/aievent/core/dispatch/flow/FlowRuleRoutingService.java', 'flow_rule_db_lookup_started')
require('ai-event-gateway-core/task-orchestration/src/main/java/com/opensocket/aievent/core/dispatch/flow/FlowRuleRoutingService.java', 'FLOW_RULE_ROUTING_REPOSITORY_BEAN_MISSING')
require('ai-event-gateway-core/database-platform/src/main/java/com/opensocket/aievent/database/persistence/dispatch/flow/JdbcFlowRuleRoutingRepository.java', 'flow_rule_runtime_repository_lookup_started')
require('ai-event-gateway-core/database-platform/src/main/java/com/opensocket/aievent/database/persistence/dispatch/flow/JdbcFlowRuleRoutingRepository.java', 'NO_MATCH_DIAGNOSTIC_SQL')
require('ai-event-gateway-core/database-platform/src/main/java/com/opensocket/aievent/database/persistence/dispatch/flow/JdbcFlowRuleRoutingRepository.java', 'event_type_matched_rules')
require('ai-event-gateway-core/database-platform/src/main/java/com/opensocket/aievent/database/persistence/dispatch/flow/JdbcFlowRuleRoutingRepository.java', 'skill_resolved_rules')
require('docs/P6_3_DEBUG_FLOW_RULE_MATCHING_LOGS/README.md', 'flow_rule_runtime_repository_no_match diagnostics')

print('P6.3 debug Flow Rule matching logs verification passed:')
for item in checks:
    print(f' - {item}')

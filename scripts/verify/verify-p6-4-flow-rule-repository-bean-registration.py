#!/usr/bin/env python3
from pathlib import Path

ROOT = Path(__file__).resolve().parents[2]
checks = []

def read(path):
    p = ROOT / path
    if not p.exists():
        raise SystemExit(f"Missing file: {path}")
    return p.read_text()

def require(path, text):
    content = read(path)
    if text not in content:
        raise SystemExit(f"Missing expected text in {path}: {text}")
    checks.append(f"{path}: {text}")
    return content

def reject(path, text):
    content = read(path)
    if text in content:
        raise SystemExit(f"Unexpected text in {path}: {text}")
    checks.append(f"{path}: rejects {text}")

repo = 'ai-event-gateway-core/database-platform/src/main/java/com/opensocket/aievent/database/persistence/dispatch/flow/JdbcFlowRuleRoutingRepository.java'
require(repo, 'import com.opensocket.aievent.database.persistence.spi.DatabaseRepositoryAdapter;')
require(repo, '@DatabaseRepositoryAdapter')
reject(repo, 'import org.springframework.stereotype.Repository;')
require('ai-event-gateway-core/database-platform/src/main/java/com/opensocket/aievent/database/config/DatabasePersistenceAutoConfiguration.java', 'classes = DatabaseRepositoryAdapter.class')
require('ai-event-gateway-core/task-orchestration/src/main/java/com/opensocket/aievent/core/dispatch/flow/FlowRuleRoutingService.java', 'FLOW_RULE_ROUTING_REPOSITORY_BEAN_MISSING')
require('ai-event-gateway-core/task-orchestration/src/main/java/com/opensocket/aievent/core/dispatch/flow/FlowRuleRoutingService.java', 'flow_rule_db_lookup_started')
require('ai-event-gateway-core/database-platform/src/main/java/com/opensocket/aievent/database/persistence/dispatch/flow/JdbcFlowRuleRoutingRepository.java', 'flow_rule_runtime_repository_lookup_started')
require('ai-event-gateway-core/database-platform/src/main/java/com/opensocket/aievent/database/persistence/dispatch/flow/JdbcFlowRuleRoutingRepository.java', 'flow_rule_runtime_repository_no_match')
require('docs/P6_4_FLOW_RULE_REPOSITORY_BEAN_REGISTRATION/README.md', 'FLOW_RULE_ROUTING_REPOSITORY_BEAN_MISSING')

print('P6.4 Flow Rule repository bean registration verification passed:')
for item in checks:
    print(f' - {item}')

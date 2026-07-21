#!/usr/bin/env python3
from pathlib import Path
import subprocess
import sys

ROOT = Path(__file__).resolve().parents[2]

CURRENT_PATHS = [
    'ai-event-gateway-admin-ui/components/dispatch-contract-builder/DispatchContractBuilderConsole.tsx',
    'ai-event-gateway-admin-ui/components/dispatch-contract-builder/AgentPoolManagementConsole.tsx',
    'ai-event-gateway-admin-ui/components/agents/AgentDetailProductView.tsx',
    'ai-event-gateway-admin-ui/components/tasks/TaskDetailView.tsx',
    'ai-event-gateway-core/task-orchestration/src/main/java/com/opensocket/aievent/core/routing/RoutingDecisionService.java',
    'ai-event-gateway-core/control-plane-app/src/main/java/com/opensocket/aievent/core/dispatch/flow/DispatchFlowManagementService.java',
    'ai-event-gateway-core/control-plane-app/src/main/java/com/opensocket/aievent/core/api/DispatchFlowController.java',
    'ai-event-gateway-core/control-plane-app/src/main/java/com/opensocket/aievent/core/api/StandardApiResponseAdvice.java',
    'ai-event-gateway-core/task-orchestration/src/main/java/com/opensocket/aievent/core/task/TaskClassificationService.java',
    'ai-event-gateway-core/task-orchestration/src/main/java/com/opensocket/aievent/core/dispatch/flow/FlowRuleRoutingService.java',
    'scripts/acceptance/source-system-only-golden-path.mjs',
    'scripts/verify/verify-current-app-contract.py',
    'docs/current/product/dispatch-user-journey.md',
    'docs/current/development/phase1-2-agent-pool-member-preservation.md',
]

FORBIDDEN = [
    'phase32',
    'Phase 32',
    'PHASE32',
    'stage5-',
    'Stage 5',
    'Stage 7',
    'Stage 8',
    'R5 verifier',
    'R-series',
]

REQUIRED = {
    'ai-event-gateway-core/task-orchestration/src/main/java/com/opensocket/aievent/core/routing/RoutingDecisionService.java': [
        'isSourceFlowPoolFirstTask',
        'routing_source_flow_pool_first_bypassed_generic_authority',
        'routingModel=AGENT_POOL_FIRST',
        'selectionStrategyContract=SUPPORTED_POOL_STRATEGY',
    ],
    'ai-event-gateway-core/control-plane-app/src/main/java/com/opensocket/aievent/core/dispatch/flow/DispatchFlowManagementService.java': [
        '"routingModel", "AGENT_POOL_FIRST"',
        'selectionStrategyContract=SUPPORTED_POOL_STRATEGY',
        'Source Flow -> Agent Pool routing uses defaultPoolId and targetPoolId',
    ],
    'ai-event-gateway-admin-ui/components/dispatch-contract-builder/DispatchContractBuilderConsole.tsx': [
        "routingModel: 'AGENT_POOL_FIRST'",
        "capabilityReferenceOnly: true",
        "legacyChildrenPreserved: true",
        'dispatch-object-types',
        'dispatch-event-types',
        'dispatch-error-codes',
        '標準派工流程只設定來源系統、預設 Agent Pool',
    ],
    'ai-event-gateway-admin-ui/components/dispatch-contract-builder/AgentPoolManagementConsole.tsx': [
        "memberSource: 'ADMIN_CONFIGURATION'",
        "routingModel: 'AGENT_POOL_FIRST'",
        "adminUiEditor: 'AGENT_POOL_MEMBER_PRESERVING'",
    ],
    'scripts/acceptance/source-system-only-golden-path.mjs': [
        'SOURCE_SYSTEM_ONLY_TENANT_ID',
        'sourceSystemOnly: true',
        '[SourceSystem-only]',
        'sourceSystemOnlyGoldenPath: true',
    ],
    'scripts/verify/verify-current-app-contract.py': [
        'isSourceFlowPoolFirstTask',
        'routing_source_flow_pool_first_bypassed_generic_authority',
        'routingModel=AGENT_POOL_FIRST',
        'source-system-only-acceptance-dry-run',
    ],
    'Makefile': [
        'source-system-only-acceptance-dry-run:',
        'node scripts/acceptance/source-system-only-golden-path.mjs --dry-run --negative',
        'source-system-only-live:',
    ],
    'docs/current/development/phase1-4-current-metadata-cleanup.md': [
        'routingModel = AGENT_POOL_FIRST',
        'selectionStrategyContract = SUPPORTED_POOL_STRATEGY',
        'source-system-only-* aliases',
    ],
}


def fail(message: str) -> None:
    print(f'Phase 1-4 current metadata cleanup verification failed: {message}', file=sys.stderr)
    sys.exit(1)


def read(rel: str) -> str:
    path = ROOT / rel
    if not path.exists():
        fail(f'missing required file: {rel}')
    if path.stat().st_size == 0:
        fail(f'empty required file: {rel}')
    return path.read_text(encoding='utf-8')

for rel in CURRENT_PATHS:
    text = read(rel)
    for token in FORBIDDEN:
        if token in text:
            fail(f'current path {rel} still contains historical metadata token: {token}')

for rel, tokens in REQUIRED.items():
    text = read(rel)
    for token in tokens:
        if token not in text:
            fail(f'{rel} missing required current metadata token: {token}')

# Ensure the current SourceSystem-only acceptance script is at least syntactically valid when Node is available.
try:
    subprocess.run(['node', '--check', str(ROOT / 'scripts/acceptance/source-system-only-golden-path.mjs')], check=True, stdout=subprocess.PIPE, stderr=subprocess.PIPE, text=True)
except FileNotFoundError:
    pass
except subprocess.CalledProcessError as exc:
    fail(f'source-system-only acceptance script is not syntactically valid: {exc.stderr}')

print('Phase 1-4 current metadata cleanup contract verified.')

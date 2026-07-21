#!/usr/bin/env python3
from pathlib import Path
import sys

ROOT = Path(__file__).resolve().parents[2]

def read(path):
    return (ROOT / path).read_text(encoding='utf-8')

def require(path, token):
    text = read(path)
    if token not in text:
        print(f"Missing token in {path}: {token}", file=sys.stderr)
        sys.exit(1)

MODEL = "ai-event-gateway-core/data-model/src/main/java/com/opensocket/aievent/core/agent/assignment/AgentAdvancedSelectionStrategyContract.java"
SERVICE = "ai-event-gateway-core/agent-control/src/main/java/com/opensocket/aievent/core/agent/assignment/AgentAssignmentService.java"
CONTROLLER = "ai-event-gateway-core/control-plane-app/src/main/java/com/opensocket/aievent/core/api/AgentAssignmentController.java"
REGISTRY = "ai-event-gateway-core/task-orchestration/src/main/java/com/opensocket/aievent/core/routing/selection/SelectionStrategyRegistry.java"
TYPES = "ai-event-gateway-admin-ui/lib/types/core.ts"
API = "ai-event-gateway-admin-ui/lib/api/coreAdminApi.ts"
ENDPOINTS = "ai-event-gateway-admin-ui/lib/api/endpoints.ts"
UI = "ai-event-gateway-admin-ui/components/dispatch-workspace/DispatchWorkspaceSections.tsx"
DOC = "docs/current/architecture/advanced-selection-strategy-contract.md"
ADR = "docs/adr/ADR-017-advanced-selection-strategy-contract.md"
CATALOG = "docs/current/api/current-api-catalog.md"
MAKEFILE = "Makefile"

for path in [MODEL, SERVICE, CONTROLLER, REGISTRY, TYPES, API, ENDPOINTS, UI, DOC, ADR, CATALOG]:
    if not (ROOT / path).exists():
        print(f"Missing file: {path}", file=sys.stderr)
        sys.exit(1)

for token in [
    "Phase 9E Advanced Selection Strategy contract",
    "CONTRACT_ONLY",
    "productionEnabled",
    "simulationRequired",
    "simulationSupportStatus",
    "formula",
    "stateStorage",
    "concurrencyDefinition",
    "fallbackStrategy",
    "assignmentEvidenceContract",
    "requiredReadinessChecks",
    "localityDimensions",
]:
    require(MODEL, token)

for token in [
    "advancedSelectionStrategyContracts",
    "ROUND_ROBIN",
    "LOCAL_FIRST",
    "QUALITY_SCORE",
    "COST_AWARE",
    "SLA_AWARE",
    "PER_POOL_CURSOR",
    "DATABASE_ATOMIC_UPDATE",
    "LOCALITY_DIMENSIONS_DEFINED",
    "RESPONSIBILITY_SCOPE",
    "COST_MASTER",
    "SLA_MASTER",
    "notRegisteredInCurrentSelectionStrategyRegistry",
    "qualityObservationNotDirectlyPromoted",
]:
    require(SERVICE, token)

for token in [
    '/selection-strategies/advanced/contracts',
    'AgentAdvancedSelectionStrategyContract',
    'advancedSelectionStrategyContracts',
]:
    require(CONTROLLER, token)

for token in [
    "CoreAdvancedSelectionStrategyContract",
    "productionEnabled",
    "simulationSupportStatus",
    "assignmentEvidenceContract",
    "requiredReadinessChecks",
    "localityDimensions",
]:
    require(TYPES, token)

for token in [
    "advancedSelectionStrategyContracts",
    "/admin/selection-strategies/advanced/contracts",
]:
    require(ENDPOINTS, token)
for token in [
    "getAdvancedSelectionStrategyContracts",
    "CoreAdvancedSelectionStrategyContract",
]:
    require(API, token)

for token in [
    "AdvancedSelectionStrategyContractPanel",
    "進階選人策略契約（尚未啟用）",
    "ROUND_ROBIN、LOCAL_FIRST、QUALITY_SCORE、COST_AWARE 與 SLA_AWARE",
    "CONTRACT ONLY",
    "productionEnabled={String(contract.productionEnabled === true)}",
    "QUALITY_SCORE 必須先完成明確公式",
]:
    require(UI, token)

for token in [
    "Advanced Selection Strategy Contract",
    "Current selectable strategies remain",
    "ROUND_ROBIN",
    "LOCAL_FIRST",
    "QUALITY_SCORE",
    "COST_AWARE",
    "SLA_AWARE",
    "GET /admin/selection-strategies/advanced/contracts",
    "does not register advanced strategies",
]:
    require(DOC, token)

for token in [
    "ADR-017: Advanced Selection Strategy Contract Before Enablement",
    "contract-only catalog entries",
    "Current Routing continues to support only `LOWEST_LOAD`, `WEIGHTED_SCORE` and `MANUAL_ONLY`",
    "per-pool cursor",
    "normalized locality dimensions",
]:
    require(ADR, token)

for token in [
    "/admin/selection-strategies/advanced/contracts",
    "CURRENT_SUPPORT",
    "verify-phase9e-advanced-selection-strategy-contract.py",
]:
    require(CATALOG, token)

require(MAKEFILE, 'verify-phase9e-advanced-selection-strategy-contract.py')

registry = read(REGISTRY)
if any(strategy in registry.split('SUPPORTED_STRATEGIES', 1)[1].split(';', 1)[0] for strategy in ['ROUND_ROBIN', 'LOCAL_FIRST', 'QUALITY_SCORE', 'COST_AWARE', 'SLA_AWARE']):
    print('Advanced strategies must not be added to SelectionStrategyRegistry.SUPPORTED_STRATEGIES in Phase 9E.', file=sys.stderr)
    sys.exit(1)

# Guardrails: advanced strategy contract must not leak into routing/eligibility implementation.
for rel in [
    'ai-event-gateway-core/task-orchestration/src/main/java/com/opensocket/aievent/core/routing',
    'ai-event-gateway-core/task-orchestration/src/main/java/com/opensocket/aievent/core/dispatch/flow',
]:
    path = ROOT / rel
    if not path.exists():
        continue
    for file in path.rglob('*.java'):
        text = file.read_text(encoding='utf-8', errors='ignore')
        if 'AgentAdvancedSelectionStrategyContract' in text or 'QUALITY_SCORE' in text:
            print(f"Advanced strategy contract leaked into Current routing implementation: {file.relative_to(ROOT)}", file=sys.stderr)
            sys.exit(1)

print('Phase 9E Advanced Selection Strategy contract verified.')

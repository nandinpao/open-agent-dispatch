#!/usr/bin/env python3
from pathlib import Path

root = Path(__file__).resolve().parents[3]
checks = {
    'data-model request': root / 'ai-event-gateway-core/data-model/src/main/java/com/opensocket/aievent/core/agent/contract/DispatchContractChainInspectionRequest.java',
    'data-model item': root / 'ai-event-gateway-core/data-model/src/main/java/com/opensocket/aievent/core/agent/contract/DispatchContractChainInspectionItem.java',
    'data-model response': root / 'ai-event-gateway-core/data-model/src/main/java/com/opensocket/aievent/core/agent/contract/DispatchContractChainInspectionResponse.java',
    'bootstrap response': root / 'ai-event-gateway-core/data-model/src/main/java/com/opensocket/aievent/core/agent/contract/DispatchContractBootstrapResponse.java',
    'assignment service': root / 'ai-event-gateway-core/agent-control/src/main/java/com/opensocket/aievent/core/agent/assignment/AgentAssignmentService.java',
    'controller': root / 'ai-event-gateway-core/control-plane-app/src/main/java/com/opensocket/aievent/core/api/DispatchContractController.java',
    'ui builder': root / 'ai-event-gateway-admin-ui/components/dispatch-contract-builder/DispatchContractBuilderConsole.tsx',
    'ui types': root / 'ai-event-gateway-admin-ui/lib/types/core.ts',
    'ui api': root / 'ai-event-gateway-admin-ui/lib/api/coreAdminApi.ts',
    'ui endpoints': root / 'ai-event-gateway-admin-ui/lib/api/endpoints.ts',
}
missing = [name for name, path in checks.items() if not path.exists()]
if missing:
    raise SystemExit(f'Missing P28 files: {missing}')

service = checks['assignment service'].read_text()
required_service_tokens = [
    'inspectDispatchContractChain',
    'TASK_DEFINITION_ACTIVE',
    'CAPABILITY_ACTIVE',
    'SERVICE_SCOPE_ACTIVE',
    'PROFILE_CAPABILITY_BINDING_ACTIVE',
    'PROFILE_POLICY_BINDING_ACTIVE',
    'DISPATCH_POLICY_ACTIVE',
    'DISPATCH_POLICY_SCOPE_ACTIVE',
    'DISPATCH_POLICY_REQUIRED_CAPABILITY_ACTIVE',
    'EVENT_TASK_MAPPING_ACTIVE',
    'AGENT_SERVICE_SCOPE_APPROVED',
    'AGENT_CAPABILITY_APPROVED',
    'response.setChainInspection(inspectDispatchContractChain(inspectionRequest))',
]
missing = [token for token in required_service_tokens if token not in service]
if missing:
    raise SystemExit(f'AgentAssignmentService missing tokens: {missing}')

controller = checks['controller'].read_text()
for token in ['@PostMapping("/inspect")', 'DispatchContractChainInspectionRequest', 'inspectDispatchContractChain']:
    if token not in controller:
        raise SystemExit(f'Controller missing token: {token}')

ui = checks['ui builder'].read_text()
for token in ['ContractChainInspectorPanel', 'P28 Contract Chain Inspector', 'result?.chainInspection', 'Raw inspector diagnostics']:
    if token not in ui:
        raise SystemExit(f'UI builder missing token: {token}')

types = checks['ui types'].read_text()
for token in ['CoreDispatchContractChainInspectionRequest', 'CoreDispatchContractChainInspectionItem', 'CoreDispatchContractChainInspectionResponse', 'chainInspection?: CoreDispatchContractChainInspectionResponse']:
    if token not in types:
        raise SystemExit(f'Core types missing token: {token}')

api = checks['ui api'].read_text()
for token in ['inspectDispatchContract', 'dispatchContractInspect']:
    if token not in api:
        raise SystemExit(f'Core API missing token: {token}')

print('P28 Contract Chain Inspector static verification passed.')

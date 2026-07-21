#!/usr/bin/env python3
from pathlib import Path
import sys

ROOT = Path(__file__).resolve().parents[2]
UI = ROOT / 'ai-event-gateway-admin-ui/components/dispatch-contract-builder/DispatchContractBuilderConsole.tsx'
SERVICE = ROOT / 'ai-event-gateway-core/control-plane-app/src/main/java/com/opensocket/aievent/core/dispatch/flow/DispatchFlowManagementService.java'
DOC = ROOT / 'docs/current/development/phase1-3-source-flow-legacy-preservation.md'
CHANGELOG = ROOT / 'docs/current/phase1-3-change-log.md'
README = ROOT / 'docs/current/README.md'


def fail(message: str) -> None:
    print(f'Phase 1-3 source flow legacy preservation verification failed: {message}', file=sys.stderr)
    sys.exit(1)

for path in [UI, SERVICE, DOC, CHANGELOG, README]:
    if not path.exists():
        fail(f'missing required file: {path.relative_to(ROOT)}')
    if path.stat().st_size == 0:
        fail(f'empty required file: {path.relative_to(ROOT)}')

ui_text = UI.read_text(encoding='utf-8')
required_ui_tokens = [
    'function preservedLegacyRequiredSkills(flow: CoreDispatchFlowView | undefined | null, tenantId: string, flowId: string)',
    'function preservedLegacyAgents(flow: CoreDispatchFlowView | undefined | null, tenantId: string, flowId: string)',
    'function hasLegacyRoutingReference(flow?: CoreDispatchFlowView | null): boolean',
    'Legacy Routing Reference',
    '已保留的舊版設定（只讀）',
    'const legacyRequiredSkills = preservedLegacyRequiredSkills(base, tenantId, flowId);',
    'const legacyAgents = preservedLegacyAgents(base, tenantId, flowId);',
    'requiredCapabilities: legacyRequiredSkills',
    'requiredSkills: legacyRequiredSkills',
    'agents: legacyAgents',
    "legacyChildrenPreserved: true",
    "capabilityReferenceOnly: true",
    "routingModel: 'AGENT_POOL_FIRST'",
]
for token in required_ui_tokens:
    if token not in ui_text:
        fail(f'missing UI preserve-but-ignore token: {token}')

for forbidden in [
    'const selectedRequiredCapabilities: CoreDispatchFlowRequiredCapabilityView[] = [];',
    'const selectedAgents: CoreDispatchFlowAgentView[] = [];',
    'requiredCapabilities: selectedRequiredCapabilities',
    'agents: selectedAgents',
    'condition.phase32gAgentPoolFirst = true',
    'phase32gAgentPoolFirst: true',
    'phase32aCapabilityReferenceOnly: true',
]:
    if forbidden in ui_text:
        fail(f'forbidden legacy deletion or phase metadata pattern remains in UI: {forbidden}')

service_text = SERVICE.read_text(encoding='utf-8')
required_service_tokens = [
    'preserveLegacyChildrenWhenRequested(normalized, existing);',
    'private void preserveLegacyChildrenWhenRequested(DispatchFlowView normalized, DispatchFlowView existing)',
    'metadataFlag(normalized, "legacyChildrenPreserved")',
    'dispatch_flow_legacy_required_capabilities_preserved',
    'dispatch_flow_legacy_agents_preserved',
    'suppressLegacyAgentGates(flow);',
    'suppressLegacyCapabilityGates(flow);',
    'private void suppressLegacyAgentGates(DispatchFlowView flow)',
    'private void suppressLegacyCapabilityGates(DispatchFlowView flow)',
    'rule.setCapabilityRequirementMode(CapabilityRequirementMode.NONE.name());',
    'rule.setRequestedSkill(null);',
    'FLOW_RULE_REPLACEMENT_LEGACY_CHILDREN_PRESERVED',
]
for token in required_service_tokens:
    if token not in service_text:
        fail(f'missing backend preservation token: {token}')

# Ensure the backend still writes preserved rows after replacement, rather than merely skipping deletes.
if service_text.find('preserveLegacyChildrenWhenRequested(normalized, existing);') > service_text.find('validateAggregate(normalized);'):
    fail('legacy child preservation must occur before aggregate validation')
if service_text.find('preserveLegacyChildrenWhenRequested(normalized, existing);') > service_text.find('delete from flow_required_capabilities'):
    fail('legacy child preservation must occur before replacement deletes')

for path, tokens in [
    (DOC, ['Preserve but Ignore', 'requiredCapabilities -> preserved as legacy reference', 'legacyChildrenPreserved = true', 'suppressLegacyCapabilityGates(flow)']),
    (CHANGELOG, ['Phase 1-3 implements Source Flow legacy-field preservation', 'preserves existing `requiredCapabilities`, `requiredSkills` and `agents` rows']),
    (README, ['phase-1-3-source-flow-legacy-preservation', 'development/phase1-3-source-flow-legacy-preservation.md']),
]:
    text = path.read_text(encoding='utf-8')
    for token in tokens:
        if token not in text:
            fail(f'missing documentation token in {path.relative_to(ROOT)}: {token}')

print('Phase 1-3 source flow legacy preservation contract verified.')

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

MODEL = "ai-event-gateway-core/data-model/src/main/java/com/opensocket/aievent/core/agent/assignment/AgentPoolCapabilityPolicy.java"
SERVICE = "ai-event-gateway-core/agent-control/src/main/java/com/opensocket/aievent/core/agent/assignment/AgentAssignmentService.java"
CONTROLLER = "ai-event-gateway-core/control-plane-app/src/main/java/com/opensocket/aievent/core/api/AgentAssignmentController.java"
TYPES = "ai-event-gateway-admin-ui/lib/types/core.ts"
API = "ai-event-gateway-admin-ui/lib/api/coreAdminApi.ts"
ENDPOINTS = "ai-event-gateway-admin-ui/lib/api/endpoints.ts"
UI = "ai-event-gateway-admin-ui/components/dispatch-workspace/DispatchWorkspaceSections.tsx"
DOC = "docs/current/architecture/explicit-capability-policy.md"
ADR = "docs/adr/ADR-016-explicit-capability-policy.md"
CATALOG = "docs/current/api/current-api-catalog.md"
MAKEFILE = "Makefile"

for path in [MODEL, SERVICE, CONTROLLER, TYPES, API, ENDPOINTS, UI, DOC, ADR, CATALOG]:
    if not (ROOT / path).exists():
        print(f"Missing file: {path}", file=sys.stderr)
        sys.exit(1)

for token in [
    "Phase 9D Explicit Capability Policy",
    "requiredCapabilities",
    "matchMode",
    "enforcementMode",
    "ADVISORY",
    "REQUIRED",
    "explicitPoolPolicy",
    "routingGate",
    "simulationImpact",
]:
    require(MODEL, token)

for token in [
    "searchAgentPoolCapabilityPolicies",
    "upsertAgentPoolCapabilityPolicy",
    "normalizeEnforcementMode",
    "ADVISORY",
    "REQUIRED",
    "capabilityPolicyRiskWarning",
    "capabilityPolicySimulationImpact",
    "notFlowProfileScopeRuntimeBindingOrTaskRequirement",
    "selectionImpact",
    "NONE_UNTIL_EXPLICIT_ENFORCEMENT_INTEGRATION",
]:
    require(SERVICE, token)

for token in [
    '/agent-pool-policies/capabilities',
    'upsertPoolCapabilityPolicy',
    'AgentPoolCapabilityPolicy',
]:
    require(CONTROLLER, token)

for token in [
    'CoreAgentPoolCapabilityPolicy',
    "CoreAgentPoolCapabilityPolicyEnforcementMode",
    "'ADVISORY' | 'REQUIRED'",
    'simulationImpact',
]:
    require(TYPES, token)

for token in [
    'agentPoolCapabilityPolicies',
    'agentPoolCapabilityPolicy',
    'getAgentPoolCapabilityPolicies',
    'upsertAgentPoolCapabilityPolicy',
]:
    require(API, token) if token.startswith('get') or token.startswith('upsert') else require(ENDPOINTS, token)

for token in [
    'ExplicitCapabilityPolicyPanel',
    'Pool Capability Policy',
    'ADVISORY',
    'REQUIRED',
    'Simulation impact',
    'Flow、Profile、Scope、Runtime Binding 或 Task Requirement',
]:
    require(UI, token)

for token in [
    'Explicit Capability Policy',
    'Agent Pool policy',
    'ADVISORY',
    'REQUIRED',
    'Flow, Profile, Service Scope, Runtime Binding, or Task Requirement',
]:
    require(DOC, token)

for token in [
    'ADR-016 Explicit Capability Policy',
    'default `enforcementMode` is `ADVISORY`',
    'not a Flow/Profile/Scope/Runtime Binding/Task Requirement condition',
]:
    require(ADR, token)

for token in [
    '/admin/agent-pool-policies/capabilities',
    'CURRENT_SUPPORT',
    'verify-phase9d-explicit-capability-policy.py',
]:
    require(CATALOG, token)

require(MAKEFILE, 'verify-phase9d-explicit-capability-policy.py')

# Guardrail: the new explicit Pool policy must not be imported into Current routing or runtime eligibility paths.
for rel in [
    'ai-event-gateway-core/task-orchestration/src/main/java',
    'ai-event-gateway-core/control-plane-app/src/main/java/com/opensocket/aievent/core/routing',
]:
    path = ROOT / rel
    if not path.exists():
        continue
    for file in path.rglob('*.java'):
        text = file.read_text(encoding='utf-8', errors='ignore')
        if 'AgentPoolCapabilityPolicy' in text:
            print(f"Explicit Capability Policy leaked into routing/eligibility file: {file.relative_to(ROOT)}", file=sys.stderr)
            sys.exit(1)

print('Phase 9D Explicit Capability Policy contract verified.')

#!/usr/bin/env python3
from pathlib import Path
import re
import sys

ROOT = Path(__file__).resolve().parents[2]
errors: list[str] = []

def read(path: str) -> str:
    return (ROOT / path).read_text(encoding='utf-8', errors='ignore')

def require(path: str) -> None:
    if not (ROOT / path).exists():
        errors.append(f"Missing required file: {path}")

require('docs/PHASE12_COMPILE_INTEGRATION_REPAIR_GATE.md')
require('scripts/verify/phase12-live-integration-gate.sh')
require('ai-event-gateway-admin-ui/package.json')
require('ai-event-gateway-admin-ui/package-lock.json')
require('ai-event-gateway-core/database-platform/src/main/resources/db/migration/V1__clean_dispatch_flow_direct_baseline.sql')

package = read('ai-event-gateway-admin-ui/package.json')
for script in ['"typecheck"', '"build"', '"stage9:browser-e2e"']:
    if script not in package:
        errors.append(f"Admin UI package.json must keep script {script}")

lock = read('ai-event-gateway-admin-ui/package-lock.json')
for forbidden in ['applied-caas', 'artifactory', 'packages.applied-caas']:
    if forbidden in lock:
        errors.append(f"package-lock.json must not reference internal registry token: {forbidden}")

baseline = read('ai-event-gateway-core/database-platform/src/main/resources/db/migration/V1__clean_dispatch_flow_direct_baseline.sql').lower()
for token in [
    'task_offers', 'flow_required_skills', 'assignment_profiles', 'service_scopes',
    'task_scopes', 'qualifications', 'source_defaults', 'agent_source_assignments',
    'operation_profiles', 'capability_grants', 'action_grants', 'legacy_policy_bindings'
]:
    if token in baseline:
        errors.append(f"clean baseline must not create legacy table/token: {token}")

# Active Java import consistency. Nested-class imports are allowed if the outer class exists.
java_roots = [ROOT / 'ai-event-gateway-core', ROOT / 'ai-event-gateway-netty']
classes: dict[str, Path] = {}
for base in java_roots:
    if not base.exists():
        continue
    for path in base.rglob('*.java'):
        text = path.read_text(encoding='utf-8', errors='ignore')
        match = re.search(r'^\s*package\s+([\w.]+)\s*;', text, re.M)
        if match:
            classes[f"{match.group(1)}.{path.stem}"] = path

for base in java_roots:
    if not base.exists():
        continue
    for path in base.rglob('*.java'):
        rel = path.relative_to(ROOT)
        text = path.read_text(encoding='utf-8', errors='ignore')
        for imp in re.findall(r'^\s*import\s+(com\.opensocket\.[\w.]+)\s*;', text, re.M):
            if imp in classes:
                continue
            parts = imp.split('.')
            nested_ok = any('.'.join(parts[:i]) in classes for i in range(len(parts)-1, 2, -1))
            if not nested_ok:
                errors.append(f"Missing active Java import in {rel}: {imp}")

active_roots = [
    ROOT / 'ai-event-gateway-core/control-plane-app/src/main',
    ROOT / 'ai-event-gateway-core/task-orchestration/src/main',
    ROOT / 'ai-event-gateway-core/agent-control/src/main',
    ROOT / 'ai-event-gateway-core/database-platform/src/main',
    ROOT / 'ai-event-gateway-admin-ui/components',
    ROOT / 'ai-event-gateway-admin-ui/app',
    ROOT / 'ai-event-gateway-admin-ui/lib',
]
forbidden_active = [
    'TaskCapabilityResolverService', 'DispatchRequirementAuthoritativeService',
    'Task' + 'Offer' + 'OrchestrationService', 'Task' + 'Offer' + 'Controller', 'OFFER_' + 'FIRST', 'OFFER' + 'ING',
    'AgentCertificationService', 'DispatchDataMigrationService', 'AgentEnterpriseGovernanceService',
    'DispatchGovernanceController', 'DispatchReadinessController', 'DispatchRecipeController',
    'flow_required_skills', 'task_offers', 'Service Scope', 'Assignment Profile', 'Task Scope',
]
for base in active_roots:
    if not base.exists():
        continue
    for path in base.rglob('*'):
        if path.is_file() and path.suffix in {'.java', '.ts', '.tsx', '.xml', '.sql', '.yml', '.yaml'}:
            text = path.read_text(encoding='utf-8', errors='ignore')
            for token in forbidden_active:
                if token in text:
                    errors.append(f"Forbidden active token {token!r} in {path.relative_to(ROOT)}")

makefile = read('Makefile')
for target in ['verify-stage12-compile-integration-repair-gate', 'phase12-compile-integration-repair-gate', 'phase12-live-integration-gate']:
    if target not in makefile:
        errors.append(f"Makefile missing target {target}")

if errors:
    print('Phase 12 compile/integration repair gate failed:')
    for error in errors:
        print(f" - {error}")
    sys.exit(1)
print('Phase 12 compile/integration repair gate contract verified.')

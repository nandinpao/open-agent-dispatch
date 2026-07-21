#!/usr/bin/env python3
from pathlib import Path
import re
import subprocess
import sys

ROOT = Path(__file__).resolve().parents[2]
ACCEPTANCE = ROOT / 'scripts/acceptance/source-system-only-golden-path.mjs'
ROUTING = ROOT / 'ai-event-gateway-core/task-orchestration/src/main/java/com/opensocket/aievent/core/routing/RoutingDecisionService.java'
MAKEFILE = ROOT / 'Makefile'


def fail(message: str) -> None:
    print(f'SourceSystem-only golden path verification failed: {message}', file=sys.stderr)
    sys.exit(1)


def text(path: Path) -> str:
    if not path.exists():
        fail(f'missing required file: {path.relative_to(ROOT)}')
    return path.read_text(encoding='utf-8')

acceptance = text(ACCEPTANCE)
for token in [
    'SOURCE_SYSTEM_ONLY_INTAKE_CONTRACT',
    'tenantId + sourceSystem only; no eventType/objectType/errorCode in payload',
    'NO_CAPABILITY_ROUTING_GATE_CONTRACT',
    'sourceSystemOnlyIntakePayload',
    'assertUnknownTriagePoolAssignment',
    'assertNoCapabilityGate',
    'SOURCE_FLOW_DEFAULT_POOL',
    'POOL_HAS_NO_ACTIVE_MEMBER',
    'POOL_AGENT_OFFLINE',
    'POOL_AGENT_CAPACITY_FULL',
    'NO_ELIGIBLE_AGENT_IN_POOL',
    'SOURCE_SYSTEM_ONLY_TENANT_ID',
]:
    if token not in acceptance:
        fail(f'acceptance script missing token: {token}')

match = re.search(r"function sourceSystemOnlyIntakePayload\(\) \{(?P<body>.*?)\n\}", acceptance, re.S)
if not match:
    fail('missing sourceSystemOnlyIntakePayload function')
body = match.group('body')
for forbidden in ['eventType:', 'objectType:', 'errorCode:']:
    if forbidden in body:
        fail(f'sourceSystemOnlyIntakePayload must omit {forbidden}')

routing = text(ROUTING)
for token in [
    'isSourceFlowPoolFirstTask',
    'routingModel=AGENT_POOL_FIRST',
    'routing_source_flow_pool_first_bypassed_generic_authority',
    'SOURCE_FLOW_POOL_IS_AUTHORITATIVE',
    'immutableNullableMap(scoreBreakdown)',
    'immutableNullableMap(breakdown)',
]:
    if token not in routing:
        fail(f'routing service missing token: {token}')

makefile = text(MAKEFILE)
for token in [
    'verify-source-system-only-golden-path:',
    'source-system-only-acceptance-dry-run:',
    'source-system-only-live:',
    'node scripts/acceptance/source-system-only-golden-path.mjs --dry-run --negative',
]:
    if token not in makefile:
        fail(f'Makefile missing token: {token}')

try:
    subprocess.run(['node', '--check', str(ACCEPTANCE)], check=True, stdout=subprocess.PIPE, stderr=subprocess.PIPE, text=True)
except FileNotFoundError:
    pass
except subprocess.CalledProcessError as exc:
    fail(f'acceptance script is not syntactically valid: {exc.stderr}')

print('SourceSystem-only golden path contract verified.')

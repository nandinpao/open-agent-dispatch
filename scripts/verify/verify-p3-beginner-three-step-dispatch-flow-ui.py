#!/usr/bin/env python3
from pathlib import Path
import sys

ROOT = Path(__file__).resolve().parents[2]
COMPONENT = ROOT / 'ai-event-gateway-admin-ui/components/dispatch-contract-builder/DispatchContractBuilderConsole.tsx'
DOC = ROOT / 'docs/P3_BEGINNER_THREE_STEP_DISPATCH_FLOW_UI/README.md'
API = ROOT / 'ai-event-gateway-admin-ui/lib/api/coreAdminApi.ts'

errors: list[str] = []

text = COMPONENT.read_text(encoding='utf-8') if COMPONENT.exists() else ''
doc = DOC.read_text(encoding='utf-8') if DOC.exists() else ''
api = API.read_text(encoding='utf-8') if API.exists() else ''

required_tokens = [
    'P3 Beginner Dispatch Flow Wizard',
    '這是哪一種事件？',
    '需要什麼處理技能？',
    '哪個 Agent 可以處理？',
    'Requested Skill',
    'Flow Rule Dry-run',
    'Advanced / Diagnostics：legacy compatibility fields',
    'Advanced / Diagnostics：Flow-owned skill, Agent, routing, trace panels',
    'coreAdminApi.createDispatchFlow',
    'coreAdminApi.dryRunDispatchFlow',
    'coreAdminApi.dryRunDispatchFlowById',
]
for token in required_tokens:
    if token not in text:
        errors.append(f'Missing UI token: {token}')

main_before_advanced = text.split('Advanced / Diagnostics：legacy compatibility fields')[0]
for legacy_phrase in ['Service Scope / Profile', 'Capability Code', 'Task Type', 'Dispatch Rule</label>']:
    if legacy_phrase in main_before_advanced:
        errors.append(f'Legacy phrase still appears before Advanced / Diagnostics: {legacy_phrase}')

for token in ['Beginner path', 'Event → requestedSkill → Agent', 'P1/P2 backend contract']:
    if token not in doc:
        errors.append(f'Missing P3 doc token: {token}')

for token in ['createDispatchFlow', 'dryRunDispatchFlow', 'dryRunDispatchFlowById']:
    if token not in api:
        errors.append(f'Missing API client token: {token}')

# Regression guard: buildR7TraceChainPreview is a module-level helper, so it must not
# reference the component state variable `eventStage` directly. That caused TS2552:
# Cannot find name 'eventStage'.
if "stepCode: 'EXTERNAL_INTAKE_RECEIVED',\n      eventStage," in text:
    errors.append("R7 trace preview still references undefined bare eventStage; use eventStage: 'EXTERNAL' instead")
if "stepCode: 'EXTERNAL_INTAKE_RECEIVED',\n      eventStage: 'EXTERNAL'" not in text:
    errors.append("R7 trace preview must explicitly set eventStage: 'EXTERNAL'")

if errors:
    print('P3 beginner three-step Dispatch Flow UI verification failed:')
    for error in errors:
        print(f' - {error}')
    sys.exit(1)

print('P3 beginner three-step Dispatch Flow UI verification passed.')

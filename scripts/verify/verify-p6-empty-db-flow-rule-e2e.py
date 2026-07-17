#!/usr/bin/env python3
from pathlib import Path
import sys

ROOT = Path(__file__).resolve().parents[2]

checks = [
    (
        'P6 acceptance script exists with positive and negative cases',
        'scripts/acceptance/p6-empty-db-flow-rule-e2e.mjs',
        [
            'P6 Empty-DB Flow Rule E2E acceptance gate',
            'ERP_VENDOR_BANK_CHANGE',
            'ERP_PAYMENT_RISK',
            'MES_EQUIPMENT_ALARM',
            'CMS_CONTENT_PUBLISH_FAILED',
            'NO_AGENT_ASSIGNMENT',
            'AGENT_MISSING_REQUESTED_SKILL_GRANT',
            'FLOW_AGENT_ASSIGNMENT',
            'AGENT_SKILL_GRANT',
        ],
    ),
    (
        'P6 acceptance script creates DB-backed Dispatch Flows',
        'scripts/acceptance/p6-empty-db-flow-rule-e2e.mjs',
        [
            '/admin/dispatch-flows?tenantId=',
            '/admin/dispatch-flows/${encodeURIComponent(flowId)}/dry-run?tenantId=',
            '/api/events/intake',
            'assignmentCreated === true',
            'dispatchRequestCreated === true',
            'FLOW_RULE',
            'matchedFlowId',
            'matchedRuleId',
            'requestedSkill',
            'selectedAgentId',
        ],
    ),
    (
        'P6 acceptance script sets up Agent requestedSkill grants without Flyway seed',
        'scripts/acceptance/p6-empty-db-flow-rule-e2e.mjs',
        [
            '/admin/agents/setup',
            'createDefaultCapabilities: true',
            'createDefaultDispatchRule: false',
            'No migration seed required.',
        ],
    ),
    (
        'P6 positive API examples exist',
        'scripts/api/p6/p6-positive-cases.json',
        [
            'ERP_VENDOR_MASTER_RISK_TRIAGE',
            'ERP_PAYMENT_RISK_TRIAGE',
            'MES_EQUIPMENT_ALARM_TRIAGE',
            'CMS_CONTENT_REVIEW',
            'assignmentCreated',
            'dispatchRequestCreated',
        ],
    ),
    (
        'P6 no agent negative example exists',
        'scripts/api/p6/p6-negative-no-agent-assignment.json',
        ['MES_LOT_HOLD_TRIAGE', 'FLOW_AGENT_ASSIGNMENT', 'agents'],
    ),
    (
        'P6 missing skill grant negative example exists',
        'scripts/api/p6/p6-negative-missing-skill-grant.json',
        ['ERP_GL_POSTING_TRIAGE_UNGRANTED', 'AGENT_SKILL_GRANT'],
    ),
    (
        'P6 DB check exists',
        'scripts/db/p6-empty-db-flow-rule-e2e-check.sql',
        [
            'tenant-p6-empty-db',
            'P6 Dispatch Flow records',
            'FLOW_RULE_DISPATCHABLE',
            'P6 positive task evidence',
            'NO_DISPATCH_REQUEST',
        ],
    ),
    (
        'P6 documentation exists',
        'docs/P6_EMPTY_DB_FLOW_RULE_E2E/README.md',
        [
            'P6 Empty DB Flow Rule E2E Acceptance',
            'empty tenant -> create Flow -> dry-run READY -> intake -> FLOW_RULE task',
            'ERP Vendor Bank Change',
            'CMS Content Publish Failed',
            'No Agent Assignment',
            'Missing Skill Grant',
        ],
    ),
    (
        'Makefile exposes P6 verify and dry-run acceptance targets',
        'Makefile',
        [
            'verify-p6-empty-db-flow-rule-e2e',
            'acceptance-p6-empty-db-flow-rule-e2e-dry-run',
            'scripts/verify/verify-p6-empty-db-flow-rule-e2e.py',
            'scripts/acceptance/p6-empty-db-flow-rule-e2e.mjs --dry-run',
        ],
    ),
]

missing = []
for label, relpath, needles in checks:
    path = ROOT / relpath
    if not path.exists():
        missing.append(f'{label}: missing file {relpath}')
        continue
    text = path.read_text(encoding='utf-8')
    for needle in needles:
        if needle not in text:
            missing.append(f'{label}: missing marker {needle!r} in {relpath}')

if missing:
    print('P6 empty-db Flow Rule E2E verification failed:')
    for item in missing:
        print(f' - {item}')
    sys.exit(1)

print('P6 empty-db Flow Rule E2E verification passed.')

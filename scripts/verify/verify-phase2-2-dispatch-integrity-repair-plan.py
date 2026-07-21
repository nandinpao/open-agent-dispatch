#!/usr/bin/env python3
from pathlib import Path
import re
import sys

ROOT = Path(__file__).resolve().parents[2]

PLAN_SQL = ROOT / 'scripts/db/phase2-2-dispatch-integrity-repair-plan.sql'
DRY_RUN_SQL = ROOT / 'scripts/db/phase2-2-dispatch-integrity-repair-dry-run.sql'
RUNNER = ROOT / 'scripts/diagnostics/run-dispatch-integrity-repair-dry-run.sh'
DOC = ROOT / 'docs/current/development/phase2-2-dispatch-integrity-repair-plan.md'
CHANGE_LOG = ROOT / 'docs/current/phase2-2-change-log.md'
README = ROOT / 'docs/current/README.md'
ER_BASELINE = ROOT / 'docs/current/data/dispatch-er-baseline.md'
MAKEFILE = ROOT / 'Makefile'

REQUIRED_FILES = [PLAN_SQL, DRY_RUN_SQL, RUNNER, DOC, CHANGE_LOG, README, ER_BASELINE, MAKEFILE]

CHECK_IDS = [
    'DFLOW_SOURCE_MISSING',
    'DFLOW_DEFAULT_POOL_MISSING',
    'DFLOW_ACTIVE_WITHOUT_DEFAULT_POOL',
    'DFLOW_DEFAULT_POOL_SOURCE_MISMATCH',
    'DFLOW_STATUS_INVALID',
    'APOOL_SOURCE_MISSING',
    'APOOL_SELECTION_STRATEGY_UNSUPPORTED',
    'APOOL_STATUS_INVALID',
    'APOOL_TYPE_INVALID',
    'APOOL_MEMBER_POOL_MISSING',
    'APOOL_MEMBER_AGENT_PROFILE_MISSING',
    'APOOL_MEMBER_AGENT_RUNTIME_MISSING',
    'APOOL_MEMBER_STATUS_INVALID',
    'APOOL_MEMBER_WEIGHT_INVALID',
    'APOOL_MEMBER_PRIORITY_INVALID',
    'DPOLICY_FLOW_MISSING',
    'DPOLICY_SOURCE_MISSING',
    'DPOLICY_SOURCE_FLOW_MISMATCH',
    'DPOLICY_TARGET_POOL_MISSING',
    'DPOLICY_ROUTING_STRATEGY_UNSUPPORTED',
    'DPOLICY_PRIORITY_INVALID',
    'LEGACY_CAP_FLOW_MISSING',
    'LEGACY_CAP_RULE_MISSING',
    'LEGACY_FLOW_AGENT_FLOW_MISSING',
    'LEGACY_FLOW_AGENT_PROFILE_MISSING',
    'TASK_SOURCE_MISSING',
    'TASK_MATCHED_FLOW_MISSING',
    'TASK_MATCHED_RULE_MISSING',
    'TASK_ASSIGNED_POOL_MISSING',
    'TASK_TARGET_POOL_MISSING',
    'ASSIGNMENT_TASK_MISSING',
    'ASSIGNMENT_AGENT_RUNTIME_MISSING',
    'ASSIGNMENT_POOL_MISSING',
    'DREQUEST_ASSIGNMENT_MISSING',
    'DREQUEST_TASK_MISSING',
    'ROUTING_DECISION_TASK_MISSING',
    'CALLBACK_TASK_MISSING',
    'CALLBACK_ASSIGNMENT_MISSING',
]

REPAIR_CLASSES = [
    'MANUAL_REQUIRED',
    'SAFE_NORMALIZATION',
    'NORMALIZABLE_ENUM',
    'REVIEW_REQUIRED',
    'RUNTIME_OBSERVATION',
    'LEGACY_REFERENCE_CLEANUP',
    'HISTORY_REPAIR_OR_ACCEPT',
]

READINESS_MARKERS = [
    'blocks_phase2_3_fk',
    'blocks_phase2_4_check',
    'review_before_fk',
    'not_applicable',
]

FORBIDDEN_MUTATION_PATTERNS = [
    r'\bcreate\s+table\b',
    r'\bcreate\s+view\b',
    r'\bcreate\s+function\b',
    r'\bcreate\s+index\b',
    r'\balter\s+table\b',
    r'\bdrop\s+\w+\b',
    r'\btruncate\s+\w+\b',
    r'\binsert\s+into\b',
    r'\bupdate\s+\w+\s+set\b',
    r'\bdelete\s+from\b',
]


def fail(message: str) -> None:
    print(f'Phase 2-2 dispatch integrity repair plan verification failed: {message}', file=sys.stderr)
    sys.exit(1)


def read(path: Path) -> str:
    if not path.exists():
        fail(f'missing required file: {path.relative_to(ROOT)}')
    if path.stat().st_size == 0:
        fail(f'empty required file: {path.relative_to(ROOT)}')
    return path.read_text(encoding='utf-8')


def strip_sql_comments(sql: str) -> str:
    return '\n'.join(line for line in sql.splitlines() if not line.strip().startswith('--')).lower()

for path in REQUIRED_FILES:
    read(path)

plan_sql = read(PLAN_SQL)
dry_run_sql = read(DRY_RUN_SQL)

for sql_path, sql in [(PLAN_SQL, plan_sql), (DRY_RUN_SQL, dry_run_sql)]:
    sql_without_comments = strip_sql_comments(sql)
    for pattern in FORBIDDEN_MUTATION_PATTERNS:
        if re.search(pattern, sql_without_comments, re.IGNORECASE):
            fail(f'{sql_path.relative_to(ROOT)} must remain read-only; found forbidden pattern: {pattern}')

for check_id in CHECK_IDS:
    if check_id not in plan_sql:
        fail(f'repair plan missing check_id: {check_id}')
    if check_id not in dry_run_sql:
        fail(f'repair dry-run missing check_id: {check_id}')

for token in REPAIR_CLASSES + READINESS_MARKERS + ['candidate_auto', 'manual', 'repair_plan', 'default_action', 'automation_policy']:
    if token not in plan_sql:
        fail(f'repair plan missing required token: {token}')

for token in ['integrity_findings', 'Proposed Repair Actions Detail', 'Candidate Auto Repair Preview', 'fk_readiness', 'check_readiness', 'manual_review_required']:
    if token not in dry_run_sql:
        fail(f'repair dry-run missing required token: {token}')

runner = read(RUNNER)
for token in ['psql', 'ON_ERROR_STOP=1', 'DATABASE_URL', 'phase2-2-dispatch-integrity-repair-plan.sql', 'phase2-2-dispatch-integrity-repair-dry-run.sql']:
    if token not in runner:
        fail(f'runner missing required token: {token}')

readme = read(README)
for token in ['phase: phase-2-2-dispatch-integrity-repair-plan', 'development/phase2-2-dispatch-integrity-repair-plan.md', 'phase-2-2-dispatch-integrity-repair-plan']:
    if token not in readme:
        fail(f'docs/current/README.md missing Phase 2-2 token: {token}')

er_baseline = read(ER_BASELINE)
for token in ['Phase 2-2 repair planning update', 'blocks_phase2_3_fk', 'phase2-2-dispatch-integrity-repair-dry-run.sql']:
    if token not in er_baseline:
        fail(f'ER baseline missing Phase 2-2 token: {token}')

makefile = read(MAKEFILE)
for token in ['verify-phase2-2-dispatch-integrity-repair-plan', 'verify-phase2-1-dispatch-integrity-report', 'scripts/verify/verify-phase2-2-dispatch-integrity-repair-plan.py']:
    if token not in makefile:
        fail(f'Makefile missing required target token: {token}')

doc = read(DOC)
for token in ['Phase 2-3', 'dry-run', 'MANUAL_REQUIRED', 'SAFE_NORMALIZATION', 'candidate_auto', 'blocks_phase2_3_fk', 'does not']:
    if token not in doc:
        fail(f'Phase 2-2 doc missing required token: {token}')

change_log = read(CHANGE_LOG)
for token in ['Foreign keys', 'Check constraints', 'Optimistic locking', 'Mutating data repair logic', 'Routing behavior changes']:
    if token not in change_log:
        fail(f'Phase 2-2 change log missing explicit non-change token: {token}')

print('Phase 2-2 dispatch integrity repair plan contract verified.')

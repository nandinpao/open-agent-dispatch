#!/usr/bin/env python3
from pathlib import Path
import re
import sys

ROOT = Path(__file__).resolve().parents[2]

SQL = ROOT / 'scripts/db/phase2-1-dispatch-integrity-report.sql'
RUNNER = ROOT / 'scripts/diagnostics/run-dispatch-integrity-report.sh'
DOC = ROOT / 'docs/current/development/phase2-1-dispatch-integrity-report.md'
CHANGE_LOG = ROOT / 'docs/current/phase2-1-change-log.md'
README = ROOT / 'docs/current/README.md'
MAKEFILE = ROOT / 'Makefile'

REQUIRED_FILES = [SQL, RUNNER, DOC, CHANGE_LOG, README, MAKEFILE]

REQUIRED_CHECK_IDS = [
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

REQUIRED_TABLES = [
    'source_systems',
    'dispatch_flows',
    'dispatch_policies',
    'agent_pools',
    'agent_pool_members',
    'agent_profiles',
    'agents',
    'flow_required_capabilities',
    'flow_agent_assignments',
    'tasks',
    'task_assignments',
    'dispatch_requests',
    'routing_decisions',
    'task_callbacks',
]

SUPPORTED_STRATEGIES = ['LOWEST_LOAD', 'WEIGHTED_SCORE', 'MANUAL_ONLY']

FORBIDDEN_DDL_PATTERNS = [
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
    print(f'Phase 2-1 dispatch integrity report verification failed: {message}', file=sys.stderr)
    sys.exit(1)


def read(path: Path) -> str:
    if not path.exists():
        fail(f'missing required file: {path.relative_to(ROOT)}')
    if path.stat().st_size == 0:
        fail(f'empty required file: {path.relative_to(ROOT)}')
    return path.read_text(encoding='utf-8')

for path in REQUIRED_FILES:
    read(path)

sql = read(SQL)
# Strip line comments before checking for mutating DDL/DML tokens.
sql_without_comments = '\n'.join(line for line in sql.splitlines() if not line.strip().startswith('--')).lower()
for pattern in FORBIDDEN_DDL_PATTERNS:
    if re.search(pattern, sql_without_comments, re.IGNORECASE):
        fail(f'SQL report must remain read-only; found forbidden pattern: {pattern}')

for check_id in REQUIRED_CHECK_IDS:
    if check_id not in sql:
        fail(f'SQL report missing required check_id: {check_id}')

for table in REQUIRED_TABLES:
    if table not in sql:
        fail(f'SQL report missing required table reference: {table}')

for strategy in SUPPORTED_STRATEGIES:
    if strategy not in sql:
        fail(f'SQL report missing supported strategy token: {strategy}')

for token in ['BLOCKER', 'WARNING', 'integrity_findings', 'finding_count', 'reference_type', 'reference_id']:
    if token not in sql:
        fail(f'SQL report missing required output token: {token}')

runner = read(RUNNER)
for token in ['psql', 'ON_ERROR_STOP=1', 'DATABASE_URL', 'phase2-1-dispatch-integrity-report.sql']:
    if token not in runner:
        fail(f'runner missing required token: {token}')

readme = read(README)
if 'development/phase2-1-dispatch-integrity-report.md' not in readme:
    fail('docs/current/README.md does not reference Phase 2-1 report documentation')
if 'phase-2-1-dispatch-integrity-report' not in readme:
    fail('docs/current/README.md does not retain Phase 2-1 marker')

makefile = read(MAKEFILE)
for token in ['verify-phase2-1-dispatch-integrity-report', 'verify-phase1-4-current-metadata-cleanup', 'scripts/verify/verify-phase2-1-dispatch-integrity-report.py']:
    if token not in makefile:
        fail(f'Makefile missing required target token: {token}')

doc = read(DOC)
for token in ['read-only', 'BLOCKER', 'WARNING', 'Phase 2-2', 'Composite', 'foreign keys', 'CHECK']:
    if token not in doc:
        fail(f'Phase 2-1 doc missing required token: {token}')

change_log = read(CHANGE_LOG)
for token in ['Foreign keys', 'Check constraints', 'Optimistic locking', 'Data repair logic']:
    if token not in change_log:
        fail(f'Phase 2-1 change log missing non-change boundary token: {token}')

print('Phase 2-1 dispatch integrity report contract verified.')

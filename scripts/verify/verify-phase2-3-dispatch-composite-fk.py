#!/usr/bin/env python3
from pathlib import Path
import re
import sys

ROOT = Path(__file__).resolve().parents[2]

MIGRATION = ROOT / 'ai-event-gateway-core/database-platform/src/main/resources/db/migration/V2__dispatch_referential_integrity.sql'
DOC = ROOT / 'docs/current/development/phase2-3-dispatch-composite-fk.md'
CHANGE_LOG = ROOT / 'docs/current/phase2-3-change-log.md'
MODIFIED = ROOT / 'docs/current/phase2-3-modified-files.md'
README = ROOT / 'docs/current/README.md'
ER_BASELINE = ROOT / 'docs/current/data/dispatch-er-baseline.md'
MAKEFILE = ROOT / 'Makefile'

REQUIRED_FILES = [MIGRATION, DOC, CHANGE_LOG, MODIFIED, README, ER_BASELINE, MAKEFILE]

REQUIRED_CONSTRAINTS = [
    'fk_dispatch_flows_source_system',
    'fk_dispatch_flows_default_pool',
    'fk_dispatch_policies_flow',
    'fk_dispatch_policies_source_system',
    'fk_dispatch_policies_target_pool',
    'fk_agent_pool_members_pool',
    'fk_agent_pool_members_agent_profile',
    'fk_task_assignments_task',
    'fk_dispatch_requests_assignment',
    'fk_dispatch_requests_task',
]

REQUIRED_UNIQUES = [
    'uq_agent_profiles_tenant_agent',
    'uq_dispatch_policies_tenant_policy',
    'uq_tasks_tenant_task',
    'uq_task_assignments_tenant_assignment',
]

REQUIRED_TOKENS = [
    'V2__dispatch_referential_integrity.sql',
    'ADD COLUMN IF NOT EXISTS tenant_id varchar(64)',
    'set_task_assignment_tenant_id',
    'set_dispatch_request_tenant_id',
    'trg_task_assignments_set_tenant_id',
    'trg_dispatch_requests_set_tenant_id',
    'NOT VALID',
    'ON UPDATE RESTRICT ON DELETE RESTRICT',
    'phase2-1-dispatch-integrity-report.sql',
    'phase2-2-dispatch-integrity-repair-dry-run.sql',
]

EXCLUDED_TABLES = [
    'flow_required_capabilities',
    'flow_agent_assignments',
    'routing_decisions',
    'task_callbacks',
    'task_dispatch_attempts',
    'task_execution_attempts',
    'dispatch_attempt_history',
    'task_issue_links',
    'incidents',
]


def fail(message: str) -> None:
    print(f'Phase 2-3 dispatch composite FK verification failed: {message}', file=sys.stderr)
    sys.exit(1)


def read(path: Path) -> str:
    if not path.exists():
        fail(f'missing required file: {path.relative_to(ROOT)}')
    text = path.read_text(encoding='utf-8')
    if not text.strip():
        fail(f'empty required file: {path.relative_to(ROOT)}')
    return text

for path in REQUIRED_FILES:
    read(path)

migration = read(MIGRATION)
low = migration.lower()

for token in REQUIRED_TOKENS:
    if token.lower() not in low:
        fail(f'migration missing required token: {token}')

for constraint in REQUIRED_CONSTRAINTS:
    if constraint not in migration:
        fail(f'migration missing FK constraint: {constraint}')

for constraint in REQUIRED_UNIQUES:
    if constraint not in migration:
        fail(f'migration missing composite unique constraint: {constraint}')

# Every FK must be composite and tenant-aware.
fk_blocks = re.findall(r'FOREIGN\s+KEY\s*\(([^)]*)\)\s*REFERENCES\s+([a-zA-Z0-9_]+)\s*\(([^)]*)\)', migration, re.IGNORECASE | re.MULTILINE)
if len(fk_blocks) != len(REQUIRED_CONSTRAINTS):
    fail(f'expected {len(REQUIRED_CONSTRAINTS)} FK blocks, found {len(fk_blocks)}')

for source_cols, ref_table, ref_cols in fk_blocks:
    source = [c.strip().lower() for c in source_cols.split(',')]
    target = [c.strip().lower() for c in ref_cols.split(',')]
    if 'tenant_id' not in source:
        fail(f'FK to {ref_table} is not tenant-aware on source columns: {source_cols}')
    if 'tenant_id' not in target:
        fail(f'FK to {ref_table} is not tenant-aware on target columns: {ref_cols}')
    if len(source) < 2 or len(target) < 2:
        fail(f'FK to {ref_table} is not composite: {source_cols} -> {ref_cols}')

# Ensure excluded historical/reference tables are not altered or constrained in this migration.
for table in EXCLUDED_TABLES:
    pattern = rf'ALTER\s+TABLE\s+{table}\b'
    if re.search(pattern, migration, re.IGNORECASE):
        fail(f'migration must not alter historical/reference table in Phase 2-3: {table}')

for token in ['CHECK (', 'CHECK(', 'ALTER COLUMN weight', 'ALTER COLUMN priority', 'version ', 'updated_by']:
    if token.lower() in low:
        fail(f'migration must not introduce Phase 2-4/2-5 concerns yet: {token}')

readme = read(README)
for token in ['phase: phase-2-3-dispatch-composite-fk', 'development/phase2-3-dispatch-composite-fk.md', 'phase-2-3-dispatch-composite-fk']:
    if token not in readme:
        fail(f'docs/current/README.md missing Phase 2-3 token: {token}')

er = read(ER_BASELINE)
for token in ['Phase 2-3 composite FK update', 'fk_dispatch_flows_source_system', 'fk_dispatch_requests_task', 'tenant propagation triggers']:
    if token not in er:
        fail(f'ER baseline missing Phase 2-3 token: {token}')

makefile = read(MAKEFILE)
for token in ['verify-phase2-3-dispatch-composite-fk', 'verify-phase2-2-dispatch-integrity-repair-plan', 'scripts/verify/verify-phase2-3-dispatch-composite-fk.py']:
    if token not in makefile:
        fail(f'Makefile missing required Phase 2-3 token: {token}')

doc = read(DOC)
for token in ['NOT VALID', 'tenant-aware', 'fk_dispatch_flows_source_system', 'fk_dispatch_requests_assignment', 'Explicitly excluded', 'Phase 2-4', 'Phase 2-5']:
    if token not in doc:
        fail(f'Phase 2-3 doc missing required token: {token}')

change_log = read(CHANGE_LOG)
for token in ['No Check constraints', 'No Optimistic locking', 'No UI conflict handling', 'No Routing behavior changes', 'No hard FK constraints for legacy/reference-only rows']:
    if token not in change_log:
        fail(f'Phase 2-3 change log missing explicit non-change token: {token}')

modified = read(MODIFIED)
for token in ['V2__dispatch_referential_integrity.sql', 'verify-phase2-3-dispatch-composite-fk.py']:
    if token not in modified:
        fail(f'Phase 2-3 modified-files missing token: {token}')

print('Phase 2-3 dispatch composite FK contract verified.')

#!/usr/bin/env python3
from pathlib import Path
import re
import sys

ROOT = Path(__file__).resolve().parents[2]

MIGRATION = ROOT / 'ai-event-gateway-core/database-platform/src/main/resources/db/migration/V3__dispatch_enum_checks.sql'
DOC = ROOT / 'docs/current/development/phase2-4-dispatch-enum-checks.md'
CHANGE_LOG = ROOT / 'docs/current/phase2-4-change-log.md'
MODIFIED = ROOT / 'docs/current/phase2-4-modified-files.md'
README = ROOT / 'docs/current/README.md'
ER_BASELINE = ROOT / 'docs/current/data/dispatch-er-baseline.md'
MAKEFILE = ROOT / 'Makefile'

REQUIRED_FILES = [MIGRATION, DOC, CHANGE_LOG, MODIFIED, README, ER_BASELINE, MAKEFILE]

REQUIRED_CONSTRAINTS = [
    'ck_agent_pools_selection_strategy_supported',
    'ck_agent_pools_status_supported',
    'ck_agent_pool_members_status_supported',
    'ck_agent_pool_members_weight_positive',
    'ck_agent_pool_members_priority_non_negative',
    'ck_dispatch_flows_status_supported',
    'ck_dispatch_policies_priority_non_negative',
    'ck_dispatch_policies_status_supported',
    'ck_dispatch_policies_routing_strategy_supported',
]

REQUIRED_TOKENS = [
    'V3__dispatch_enum_checks.sql',
    'CHECK (upper(trim(coalesce(selection_strategy',
    "'LOWEST_LOAD', 'WEIGHTED_SCORE', 'MANUAL_ONLY'",
    'CHECK (weight > 0)',
    'CHECK (priority >= 0)',
    'NOT VALID',
    'COMMENT ON CONSTRAINT',
    'phase2-1-dispatch-integrity-report.sql',
    'phase2-2-dispatch-integrity-repair-dry-run.sql',
]

FORBIDDEN_IN_MIGRATION = [
    'ROUND_ROBIN',
    'LOCAL_FIRST',
    'ADD COLUMN IF NOT EXISTS version',
    'ADD COLUMN IF NOT EXISTS updated_by',
    'VALIDATE CONSTRAINT',
    'FOREIGN KEY',
]


def fail(message: str) -> None:
    print(f'Phase 2-4 dispatch enum/check verification failed: {message}', file=sys.stderr)
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
        fail(f'migration missing check constraint: {constraint}')

if migration.upper().count(' NOT VALID') < len(REQUIRED_CONSTRAINTS):
    fail('each Phase 2-4 CHECK constraint must be added NOT VALID')

for token in FORBIDDEN_IN_MIGRATION:
    if token.lower() in low:
        fail(f'migration contains forbidden Phase 2-4 token: {token}')

# Ensure strategy constraints use the current three supported values.
for constraint in ['ck_agent_pools_selection_strategy_supported', 'ck_dispatch_policies_routing_strategy_supported']:
    start = migration.find(constraint)
    if start < 0:
        fail(f'missing strategy constraint: {constraint}')
    snippet = migration[start:start + 500].upper()
    for value in ['LOWEST_LOAD', 'WEIGHTED_SCORE', 'MANUAL_ONLY']:
        if value not in snippet:
            fail(f'{constraint} missing supported strategy value {value}')
    for value in ['ROUND_ROBIN', 'LOCAL_FIRST']:
        if value in snippet:
            fail(f'{constraint} must not admit unsupported strategy {value}')

# Ensure numeric constraints are simple value-domain checks only.
for expected in ['CHECK (weight > 0)', 'CHECK (priority >= 0)']:
    if expected not in migration:
        fail(f'missing numeric value-domain check: {expected}')

readme = read(README)
for token in ['phase: phase-2-4-dispatch-enum-checks', 'development/phase2-4-dispatch-enum-checks.md', 'V3__dispatch_enum_checks.sql', 'verify-phase2-4-dispatch-enum-checks']:
    if token not in readme:
        fail(f'docs/current/README.md missing Phase 2-4 token: {token}')

er = read(ER_BASELINE)
for token in ['Phase 2-4 enum/value check update', 'ck_agent_pools_selection_strategy_supported', 'ck_dispatch_policies_routing_strategy_supported', 'NOT VALID']:
    if token not in er:
        fail(f'ER baseline missing Phase 2-4 token: {token}')

makefile = read(MAKEFILE)
for token in ['verify-phase2-4-dispatch-enum-checks', 'verify-phase2-3-dispatch-composite-fk', 'scripts/verify/verify-phase2-4-dispatch-enum-checks.py']:
    if token not in makefile:
        fail(f'Makefile missing required Phase 2-4 token: {token}')

doc = read(DOC)
for token in ['CHECK ... NOT VALID', 'LOWEST_LOAD', 'WEIGHTED_SCORE', 'MANUAL_ONLY', 'ROUND_ROBIN', 'Phase 2-5', 'Optimistic locking']:
    if token not in doc:
        fail(f'Phase 2-4 doc missing required token: {token}')

change_log = read(CHANGE_LOG)
for token in ['No Foreign Key additions', 'No Optimistic locking', 'No API `409 Conflict` behavior', 'No Admin UI conflict handling', 'No Routing behavior changes']:
    if token not in change_log:
        fail(f'Phase 2-4 change log missing explicit non-change token: {token}')

modified = read(MODIFIED)
for token in ['V3__dispatch_enum_checks.sql', 'verify-phase2-4-dispatch-enum-checks.py']:
    if token not in modified:
        fail(f'Phase 2-4 modified-files missing token: {token}')

print('Phase 2-4 dispatch enum/check contract verified.')

#!/usr/bin/env python3
from pathlib import Path
import sys

ROOT = Path(__file__).resolve().parents[2]

MIGRATION = ROOT / 'ai-event-gateway-core/database-platform/src/main/resources/db/migration/V4__dispatch_optimistic_locking.sql'
DOC = ROOT / 'docs/current/development/phase2-5-dispatch-optimistic-locking.md'
CHANGE_LOG = ROOT / 'docs/current/phase2-5-change-log.md'
MODIFIED = ROOT / 'docs/current/phase2-5-modified-files.md'
README = ROOT / 'docs/current/README.md'
ER_BASELINE = ROOT / 'docs/current/data/dispatch-er-baseline.md'
MAKEFILE = ROOT / 'Makefile'

REQUIRED_FILES = [MIGRATION, DOC, CHANGE_LOG, MODIFIED, README, ER_BASELINE, MAKEFILE]

TABLES = [
    'source_systems',
    'dispatch_flows',
    'dispatch_policies',
    'agent_pools',
    'agent_pool_members',
]

REQUIRED_TRIGGERS = [
    'trg_source_systems_optimistic_lock_touch',
    'trg_dispatch_flows_optimistic_lock_touch',
    'trg_dispatch_policies_optimistic_lock_touch',
    'trg_agent_pools_optimistic_lock_touch',
    'trg_agent_pool_members_optimistic_lock_touch',
]

REQUIRED_CONSTRAINTS = [
    'ck_source_systems_version_positive',
    'ck_dispatch_flows_version_positive',
    'ck_dispatch_policies_version_positive',
    'ck_agent_pools_version_positive',
    'ck_agent_pool_members_version_positive',
]

REQUIRED_TOKENS = [
    'V4__dispatch_optimistic_locking.sql',
    'ADD COLUMN IF NOT EXISTS version int NOT NULL DEFAULT 1',
    'ADD COLUMN IF NOT EXISTS updated_by varchar(128)',
    'dispatch_admin_optimistic_lock_touch()',
    'NEW.updated_at := now()',
    'NEW.version := OLD.version + 1',
    "current_setting('app.updated_by', true)",
    'BEFORE UPDATE ON source_systems',
    'BEFORE UPDATE ON dispatch_flows',
    'BEFORE UPDATE ON dispatch_policies',
    'BEFORE UPDATE ON agent_pools',
    'BEFORE UPDATE ON agent_pool_members',
    'CHECK (version >= 1)',
    'NOT VALID',
    'COMMENT ON FUNCTION dispatch_admin_optimistic_lock_touch()',
]

FORBIDDEN_IN_MIGRATION = [
    '409 Conflict',
    'If-Match',
    'ETag',
    'FOREIGN KEY',
    'VALIDATE CONSTRAINT',
    'DROP COLUMN',
    'DELETE FROM',
    'TRUNCATE',
]


def fail(message: str) -> None:
    print(f'Phase 2-5 dispatch optimistic-locking verification failed: {message}', file=sys.stderr)
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

for table in TABLES:
    if f'ALTER TABLE {table}'.lower() not in low:
        fail(f'migration missing ALTER TABLE for {table}')
    if f'ON {table}'.lower() not in low:
        fail(f'migration missing trigger/comment reference for {table}')

for trigger in REQUIRED_TRIGGERS:
    if trigger not in migration:
        fail(f'migration missing trigger: {trigger}')

for constraint in REQUIRED_CONSTRAINTS:
    if constraint not in migration:
        fail(f'migration missing version-positive check constraint: {constraint}')

if migration.upper().count('CHECK (VERSION >= 1)') < len(REQUIRED_CONSTRAINTS):
    fail('each Phase 2-5 table must have a version >= 1 check')

if migration.upper().count(' NOT VALID') < len(REQUIRED_CONSTRAINTS):
    fail('each Phase 2-5 version check must be added NOT VALID')

for token in FORBIDDEN_IN_MIGRATION:
    if token.lower() in low:
        fail(f'migration contains forbidden Phase 2-5 token: {token}')

readme = read(README)
for token in [
    'phase: phase-2-5-dispatch-optimistic-locking',
    'development/phase2-5-dispatch-optimistic-locking.md',
    'V4__dispatch_optimistic_locking.sql',
    'dispatch_admin_optimistic_lock_touch()',
    'verify-phase2-5-dispatch-optimistic-locking',
]:
    if token not in readme:
        fail(f'docs/current/README.md missing Phase 2-5 token: {token}')

er = read(ER_BASELINE)
for token in [
    'Phase 2-5 optimistic locking update',
    'V4__dispatch_optimistic_locking.sql',
    'trg_source_systems_optimistic_lock_touch',
    'WHERE version = :expectedVersion',
]:
    if token not in er:
        fail(f'ER baseline missing Phase 2-5 token: {token}')

makefile = read(MAKEFILE)
for token in [
    'verify-phase2-5-dispatch-optimistic-locking',
    'verify-phase2-4-dispatch-enum-checks',
    'scripts/verify/verify-phase2-5-dispatch-optimistic-locking.py',
]:
    if token not in makefile:
        fail(f'Makefile missing required Phase 2-5 token: {token}')

doc = read(DOC)
for token in [
    'optimistic-locking',
    '409 Conflict',
    'expectedVersion',
    'WHERE version = :expectedVersion',
    'Phase 2-6',
    'No Admin UI conflict handling',
]:
    if token not in doc:
        fail(f'Phase 2-5 doc missing required token: {token}')

change_log = read(CHANGE_LOG)
for token in [
    'No API `409 Conflict` behavior',
    'No Admin UI conflict handling',
    'No Foreign Key additions',
    'No Routing behavior changes',
]:
    if token not in change_log:
        fail(f'Phase 2-5 change log missing explicit non-change token: {token}')

modified = read(MODIFIED)
for token in ['V4__dispatch_optimistic_locking.sql', 'verify-phase2-5-dispatch-optimistic-locking.py']:
    if token not in modified:
        fail(f'Phase 2-5 modified-files missing token: {token}')

print('Phase 2-5 dispatch optimistic-locking contract verified.')

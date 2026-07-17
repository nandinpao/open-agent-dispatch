#!/usr/bin/env python3
from __future__ import annotations
from pathlib import Path
import sys

ROOT = Path(__file__).resolve().parents[2]
errors: list[str] = []

wrapper_path = ROOT / 'scripts/db/flyway-migrate-with-diagnostics.sh'
doc_path = ROOT / 'docs/PHASE23_FLYWAY_PENDING_VALIDATE_REPAIR.md'

for path in [wrapper_path, doc_path]:
    if not path.exists():
        errors.append(f'missing required file: {path.relative_to(ROOT)}')

if wrapper_path.exists():
    text = wrapper_path.read_text(encoding='utf-8')
    required_tokens = [
        'flyway preflight validate with validateMigrationNaming=true and ignoreMigrationPatterns=*:pending',
        "-ignoreMigrationPatterns='*:pending' validate",
        'pending migrations are expected before a fresh baseline migrate',
        'invalid migration filename count=$invalid_count',
        'if [ "$invalid_count" -gt 0 ]; then',
        'exit 19',
        'flyway validate requested explicitly; running strict validate without ignoreMigrationPatterns.',
        'exec flyway $base_args -validateMigrationNaming=true validate',
        'exec flyway $base_args -validateMigrationNaming=true "$command_name"',
    ]
    for token in required_tokens:
        if token not in text:
            errors.append(f'flyway wrapper missing pending validate repair token: {token}')
    forbidden_tokens = [
        'log "flyway validate with validateMigrationNaming=true"',
        'if ! flyway $base_args -validateMigrationNaming=true validate; then',
        'if ! flyway -X $base_args -validateMigrationNaming=true validate; then',
    ]
    for token in forbidden_tokens:
        if token in text:
            errors.append(f'flyway wrapper still contains strict pre-migrate validate token: {token}')

makefile = (ROOT / 'Makefile').read_text(encoding='utf-8')
for token in ['verify-stage23-flyway-pending-validate-repair', 'phase23-flyway-pending-validate-repair']:
    if token not in makefile:
        errors.append(f'Makefile missing {token}')

if errors:
    print('Stage 23 Flyway pending validate repair verification failed:')
    for e in errors:
        print(f' - {e}')
    sys.exit(1)
print('Stage 23 Flyway pending validate repair contract verified.')

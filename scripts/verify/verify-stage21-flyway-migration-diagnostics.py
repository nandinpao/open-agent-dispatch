#!/usr/bin/env python3
from __future__ import annotations
from pathlib import Path
import sys

ROOT = Path(__file__).resolve().parents[2]

REQUIRED = [
    Path('scripts/db/flyway-migrate-with-diagnostics.sh'),
    Path('docs/PHASE21_FLYWAY_MIGRATION_DIAGNOSTICS.md'),
]

COMPOSE_FILES = [
    Path('deploy/docker-compose.local.yml'),
    Path('deploy/docker-compose.ci.yml'),
    Path('deploy/docker-compose.release.yml'),
]

SCRIPT_TOKENS = [
    'migration-file name=',
    'invalid-migration-filename',
    '-validateMigrationNaming=true',
    'checksum mismatch for V1',
    'OPENDISPATCH_FLYWAY_DEBUG',
]

LOCAL_LOG_TOKENS = [
    'core-db-migrate-failure.log',
    'ps-after-failed-up.txt',
    'core-db-migrate/postgres diagnostics',
]

errors: list[str] = []
for rel in REQUIRED:
    if not (ROOT / rel).exists():
        errors.append(f'missing required file: {rel}')

script = (ROOT / 'scripts/db/flyway-migrate-with-diagnostics.sh').read_text(encoding='utf-8')
for token in SCRIPT_TOKENS:
    if token not in script:
        errors.append(f'flyway diagnostic wrapper missing token: {token}')

for rel in COMPOSE_FILES:
    text = (ROOT / rel).read_text(encoding='utf-8')
    if 'entrypoint: ["/bin/sh", "/flyway/diagnostics/flyway-migrate-with-diagnostics.sh"]' not in text:
        errors.append(f'{rel} does not route core-db-migrate through diagnostic wrapper')
    if rel.name in {'docker-compose.local.yml', 'docker-compose.ci.yml'}:
        if '/flyway/diagnostics:ro' not in text or 'db-migration-diagnostics' not in text:
            errors.append(f'{rel} does not mount diagnostic wrapper from a Docker named volume')
    elif '../scripts/db/flyway-migrate-with-diagnostics.sh:/flyway/diagnostics/flyway-migrate-with-diagnostics.sh:ro' not in text:
        errors.append(f'{rel} does not mount diagnostic wrapper')

for rel in [Path('scripts/ci/local-cd.sh'), Path('scripts/local-compose-up.sh'), Path('scripts/ci/local-ci.sh')]:
    text = (ROOT / rel).read_text(encoding='utf-8')
    for token in LOCAL_LOG_TOKENS:
        if token not in text:
            errors.append(f'{rel} missing failed migration log token: {token}')
    if 'flyway-migrate-with-diagnostics.sh' not in text or 'FLYWAY_DIAGNOSTICS_VOLUME' not in text:
        errors.append(f'{rel} must seed the Flyway diagnostic wrapper into a Docker named volume')

makefile = (ROOT / 'Makefile').read_text(encoding='utf-8')
if 'verify-stage21-flyway-migration-diagnostics' not in makefile:
    errors.append('Makefile missing verify-stage21-flyway-migration-diagnostics target')

if errors:
    print('Stage 21 Flyway migration diagnostics verification failed:')
    for e in errors:
        print(f' - {e}')
    sys.exit(1)
print('Stage 21 Flyway migration diagnostics contract verified.')

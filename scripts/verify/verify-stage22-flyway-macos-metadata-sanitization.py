#!/usr/bin/env python3
from __future__ import annotations
from pathlib import Path
import sys

ROOT = Path(__file__).resolve().parents[2]
errors: list[str] = []

required = [
    Path('scripts/db/flyway-migrate-with-diagnostics.sh'),
    Path('docs/PHASE22_FLYWAY_MACOS_METADATA_SANITIZATION.md'),
]
for rel in required:
    if not (ROOT / rel).exists():
        errors.append(f'missing required file: {rel}')

wrapper = (ROOT / 'scripts/db/flyway-migrate-with-diagnostics.sh').read_text(encoding='utf-8')
for token in [
    'remove_macos_metadata_files',
    'assert_no_macos_metadata_files',
    "-name '._*'",
    "-name '.DS_Store'",
    'macos-metadata-file',
    'remaining-macos-metadata-file',
    'find /flyway/sql -maxdepth 1 -type f | sort',
    'invalid-migration-filename',
]:
    if token not in wrapper:
        errors.append(f'flyway wrapper missing macOS metadata token: {token}')

for rel, image_var in [
    (Path('scripts/ci/local-cd.sh'), 'LOCAL_VOLUME_SEED_IMAGE'),
    (Path('scripts/local-compose-up.sh'), 'LOCAL_VOLUME_SEED_IMAGE'),
    (Path('scripts/ci/local-ci.sh'), 'CI_VOLUME_SEED_IMAGE'),
]:
    text = (ROOT / rel).read_text(encoding='utf-8')
    for token in [
        'COPYFILE_DISABLE=1',
        "--exclude='._*'",
        "--exclude='.DS_Store'",
        "-name '._*'",
        "-name '.DS_Store'",
        '-exec rm -f {} +',
        image_var,
    ]:
        if token not in text:
            errors.append(f'{rel} missing migration volume metadata sanitization token: {token}')

makefile = (ROOT / 'Makefile').read_text(encoding='utf-8')
for token in ['verify-stage22-flyway-macos-metadata-sanitization', 'phase22-flyway-macos-metadata-sanitization']:
    if token not in makefile:
        errors.append(f'Makefile missing {token}')

# Repository artifact itself must not contain AppleDouble metadata files.
for path in ROOT.rglob('*'):
    name = path.name
    if name.startswith('._') or name == '.DS_Store':
        errors.append(f'repository contains macOS metadata file: {path.relative_to(ROOT)}')
        break

if errors:
    print('Stage 22 Flyway macOS metadata sanitization verification failed:')
    for e in errors:
        print(f' - {e}')
    sys.exit(1)
print('Stage 22 Flyway macOS metadata sanitization contract verified.')

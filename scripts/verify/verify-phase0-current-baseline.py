#!/usr/bin/env python3
from pathlib import Path
import sys

ROOT = Path(__file__).resolve().parents[2]

REQUIRED_FILES = [
    'README.md',
    'docs/current/README.md',
    'docs/current/phase0-change-log.md',
    'docs/current/architecture/dispatch-domain-model.md',
    'docs/current/architecture/data-authority.md',
    'docs/current/architecture/runtime-eligibility.md',
    'docs/current/product/dispatch-user-journey.md',
    'docs/current/product/terminology.md',
    'docs/current/api/api-inventory.md',
    'docs/current/data/dispatch-er-baseline.md',
    'docs/current/development/golden-dataset.md',
    'docs/current/development/verification-plan.md',
    'docs/adr/ADR-001-agent-pool-first-routing.md',
    'docs/adr/ADR-002-capability-reference-only.md',
    'docs/adr/ADR-003-core-and-netty-authority.md',
    'examples/golden-datasets/phase0-dispatch-baseline.sql',
]

REQUIRED_ANCHORS = {
    'README.md': [
        'docs/current/README.md',
        'Source Flow',
        'Agent Pool',
        'make verify-phase0-current-baseline',
    ],
    'docs/current/architecture/dispatch-domain-model.md': [
        'Source System',
        'Source Flow',
        'Agent Pool',
        'Capability is not the first-version routing authority',
    ],
    'docs/current/architecture/data-authority.md': [
        'Core is the business authority',
        'Netty is the runtime transport authority',
    ],
    'docs/current/api/api-inventory.md': [
        '/admin/source-systems',
        '/admin/dispatch-flows',
        '/api/events/intake',
    ],
    'docs/current/data/dispatch-er-baseline.md': [
        'source_systems',
        'dispatch_flows',
        'agent_pools',
        'agent_pool_members',
        'Required Phase 2 integrity work',
    ],
    'examples/golden-datasets/phase0-dispatch-baseline.sql': [
        'tenant-phase0',
        'flow-erp-default',
        'pool-erp-triage',
        'agent-erp-01',
        'MANUAL_ONLY',
    ],
}


def fail(message: str) -> None:
    print(f'Phase 0 baseline verification failed: {message}', file=sys.stderr)
    sys.exit(1)


for rel in REQUIRED_FILES:
    path = ROOT / rel
    if not path.exists():
        fail(f'missing required file: {rel}')
    if path.is_file() and path.stat().st_size == 0:
        fail(f'required file is empty: {rel}')

for rel, anchors in REQUIRED_ANCHORS.items():
    text = (ROOT / rel).read_text(encoding='utf-8')
    for anchor in anchors:
        if anchor not in text:
            fail(f'{rel} missing anchor: {anchor}')

root_readme = (ROOT / 'README.md').read_text(encoding='utf-8').splitlines()
if len(root_readme) > 1 and root_readme[1].startswith('> **Stage'):
    fail('root README still starts with historical Stage banner')

print('Phase 0 current documentation/API/data baseline verified.')

#!/usr/bin/env python3
from __future__ import annotations

import json
import re
import sys
from pathlib import Path

ROOT = Path(__file__).resolve().parents[2]

REQUIRED_FILES = [
    'docs/PHASE13_LIVE_STACK_GOLDEN_PATH_GATE.md',
    'scripts/verify/phase13-live-stack-golden-path-gate.sh',
    'ai-event-gateway-admin-ui/tests/e2e/stage13-live-golden-path.spec.ts',
    'ai-event-gateway-admin-ui/tests/e2e/support/stage9StrictApiMonitor.ts',
    'ai-event-gateway-admin-ui/tests/e2e/support/stage9WorkflowHelpers.ts',
]

SPEC_REQUIRED = [
    'Source System',
    'Dispatch Flow',
    'requireSelectableAgent',
    '發送真實測試事件',
    'Runtime Decision Chain',
    'Assignment created',
    'DispatchRequest created',
    'Netty delivered',
    'Agent ACK',
    'Agent RESULT',
    'Task completed',
    "SERVICE_SCOPE|ASSIGNMENT_PROFILE|SOURCE_COVERAGE|TASK_SCOPE|OPERATION_PROFILE|QUALIFICATION|TASK_' + '" + "OFFER|OFFER" + "' + 'ING",
]

GATE_REQUIRED = [
    'WITH_AGENT=true',
    'scripts/local-compose-up.sh',
    'scripts/local-smoke.sh',
    'JDK 25',
    'mvn',
    'docker',
    'npm run typecheck',
    'npm run stage13:live-golden-path',
    'PHASE13_SKIP_COMPOSE',
    'PHASE13_KEEP_STACK',
]

MONITOR_REQUIRED = [
    'tenantId is required',
    'BadSqlGrammarException',
    'permission is not allowed',
    'assignment-profiles',
    'service-scope',
    'source-coverage',
    'operation-profiles',
    'dispatch-readiness',
    'task-offer',
]

RETIRED_SPEC_PATTERNS = [
    r'/assignment-profiles',
    r'/supply-profiles',
    r'/settings/dispatch-governance',
    r'/testing/dispatch-readiness',
    r'create' + 'Offer' + 'IfEligible',
    r'TASK_' + 'OFFER_ASSIGNMENT_MODE',
]


def fail(message: str) -> None:
    print(f'[stage13-verify] ERROR: {message}', file=sys.stderr)
    sys.exit(1)


def read(path: str) -> str:
    p = ROOT / path
    if not p.exists():
        fail(f'Missing required file: {path}')
    return p.read_text(encoding='utf-8')


def require_contains(path: str, needles: list[str]) -> None:
    text = read(path)
    for needle in needles:
        if needle not in text:
            fail(f'Missing required token in {path}: {needle}')


def require_absent_regex(path: str, patterns: list[str]) -> None:
    text = read(path)
    for pattern in patterns:
        if re.search(pattern, text):
            fail(f'Retired Phase 13-incompatible token found in {path}: {pattern}')


def main() -> int:
    for file in REQUIRED_FILES:
        if not (ROOT / file).exists():
            fail(f'Missing required file: {file}')

    require_contains('ai-event-gateway-admin-ui/tests/e2e/stage13-live-golden-path.spec.ts', SPEC_REQUIRED)
    require_absent_regex('ai-event-gateway-admin-ui/tests/e2e/stage13-live-golden-path.spec.ts', RETIRED_SPEC_PATTERNS)
    require_contains('scripts/verify/phase13-live-stack-golden-path-gate.sh', GATE_REQUIRED)
    require_contains('ai-event-gateway-admin-ui/tests/e2e/support/stage9StrictApiMonitor.ts', MONITOR_REQUIRED)

    package_json = json.loads((ROOT / 'ai-event-gateway-admin-ui/package.json').read_text(encoding='utf-8'))
    scripts = package_json.get('scripts', {})
    expected_scripts = {
        'test:e2e:stage13': 'playwright test -c playwright.config.ts tests/e2e/stage13-live-golden-path.spec.ts',
        'stage13:live-golden-path': 'npm run test:e2e:stage13',
    }
    for key, expected in expected_scripts.items():
        actual = scripts.get(key)
        if actual != expected:
            fail(f'package.json script {key!r} must be {expected!r}; actual={actual!r}')

    makefile = read('Makefile')
    for target in ('verify-stage13-live-stack-golden-path', 'phase13-live-stack-golden-path-gate'):
        if target not in makefile:
            fail(f'Makefile missing target {target}')

    docs = read('docs/PHASE13_LIVE_STACK_GOLDEN_PATH_GATE.md')
    for token in ('Fresh DB', 'Core startup', 'Netty startup', 'Agent ACK', 'Agent RESULT', 'Task COMPLETED'):
        if token not in docs:
            fail(f'Phase 13 docs missing completion token: {token}')

    print('Stage 13 live stack golden path contract verified.')
    return 0


if __name__ == '__main__':
    raise SystemExit(main())

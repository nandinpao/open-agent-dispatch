#!/usr/bin/env bash
set -euo pipefail
ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)"
cd "$ROOT_DIR"
VERSION="1.0.0-p25.7.4-p5-callback-transition-governance-fix"

python3 - <<'PY'
from pathlib import Path
import re

root = Path('.')
service = (root / 'execution-control/src/main/java/com/opensocket/aievent/core/callback/TaskCallbackService.java').read_text()
match = re.search(r'case PROGRESS ->(?P<body>.*?);\n\s*case RESULT, ERROR', service, re.S)
if not match:
    raise SystemExit('Could not locate PROGRESS callback transition rule')
body = match.group('body')
required = {'DispatchRequestStatus.ACKED', 'DispatchRequestStatus.RUNNING'}
for token in required:
    if token not in body:
        raise SystemExit(f'PROGRESS transition is missing {token}')
for forbidden in ('DispatchRequestStatus.DISPATCHING', 'DispatchRequestStatus.DISPATCHED'):
    if forbidden in body:
        raise SystemExit(f'PROGRESS transition must not allow {forbidden}')

p2_test = (root / 'control-plane-app/src/test/java/com/opensocket/aievent/core/architecture/P2DatabaseDaoPoConsolidationTest.java').read_text()
if '.filter(this::isProductionJavaSource)' not in p2_test:
    raise SystemExit('P2 legacy-package governance must restrict its scan to production Java source')
if 'return normalized.contains("/src/main/java/");' not in p2_test:
    raise SystemExit('P2 production-source path guard is missing')
PY

grep -q "$VERSION" pom.xml
grep -q "$VERSION" kernel/src/main/java/com/opensocket/aievent/core/kernel/CoreVersion.java

echo "P5 callback transition and governance hotfix verification passed."

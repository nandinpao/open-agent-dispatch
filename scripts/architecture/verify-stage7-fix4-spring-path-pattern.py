#!/usr/bin/env python3
from pathlib import Path
import re
import sys

ROOT = Path(__file__).resolve().parents[2]
CONFIG = ROOT / "ai-event-gateway-core/control-plane-app/src/main/java/com/opensocket/aievent/core/security/CoreInternalSecurityConfiguration.java"
TEST = ROOT / "ai-event-gateway-core/control-plane-app/src/test/java/com/opensocket/aievent/core/security/CoreInternalSecurityConfigurationTest.java"

config = CONFIG.read_text(encoding="utf-8")
test = TEST.read_text(encoding="utf-8")

required = [
    'ADAPTER_ACTION_RECOVER_EXPIRED_LEASE_PATTERN',
    '"/internal/adapter-actions/*/recover-expired-lease"',
]
for value in required:
    if value not in config:
        raise SystemExit(f"missing required matcher contract: {value}")

if '/internal/adapter-actions/**/recover-expired-lease' in config:
    raise SystemExit('invalid middle ** adapter recovery matcher is still present')

if 'adapterActionRecoveryMatcherIsCompatibleWithSpringPathPatternParser' not in test:
    raise SystemExit('missing PathPatternParser regression test')

# Scan Java request/security pattern literals for ** followed by additional path content.
violations = []
for path in ROOT.rglob('*.java'):
    if any(part in {'target', 'node_modules', '.next'} for part in path.parts):
        continue
    text = path.read_text(encoding='utf-8', errors='ignore')
    for match in re.finditer(r'"([^"\n]*\*\*[^"\n]*)"', text):
        pattern = match.group(1)
        if not pattern.startswith('/'):
            continue
        after = pattern.split('**', 1)[1]
        if after not in ('', '/'):
            line = text.count('\n', 0, match.start()) + 1
            violations.append(f"{path.relative_to(ROOT)}:{line}: {pattern}")

if violations:
    raise SystemExit('invalid Spring PathPattern literals:\n  ' + '\n  '.join(violations))

print('Stage 7 Fix4 Spring PathPattern contract verified.')

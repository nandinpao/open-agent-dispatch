#!/usr/bin/env python3
from __future__ import annotations
import sys
from pathlib import Path
ROOT = Path(__file__).resolve().parents[2]
REQUIRED = [
    'ai-event-gateway-admin-ui/lib/fixtures/p3pProductionEnforceObservabilityFixture.ts',
    'ai-event-gateway-admin-ui/components/enforce-observability/ProductionEnforceObservabilityPanel.tsx',
    'ai-event-gateway-admin-ui/scripts/verify-p3p-production-observability.mjs',
    'ai-event-gateway-admin-ui/scripts/p3p-post-cutover-observability-export.mjs',
    'ai-event-gateway-admin-ui/docs/P3_P_PRODUCTION_ENFORCE_OBSERVABILITY.md',
    'ai-event-gateway-core/database-platform/src/main/resources/db/migration/V75__p3p_enforce_observability_post_cutover.sql',
]

def fail(msg: str) -> None:
    print(f'[FAIL] {msg}', file=sys.stderr)
    raise SystemExit(1)

for rel in REQUIRED:
    if not (ROOT / rel).is_file():
        fail(f'Missing required file: {rel}')

fixture = (ROOT / 'ai-event-gateway-admin-ui/lib/fixtures/p3pProductionEnforceObservabilityFixture.ts').read_text()
for token in ['P3P_PRODUCTION_ENFORCE_OBSERVABILITY', 'opendispatch_enforce_v2_allowed_total', 'P3P_NO_CANDIDATE_RATE_HIGH', 'operatorIncidentWorkflow', 'artifactRetention']:
    if token not in fixture:
        fail(f'Fixture missing token: {token}')

migration = (ROOT / 'ai-event-gateway-core/database-platform/src/main/resources/db/migration/V75__p3p_enforce_observability_post_cutover.sql').read_text()
for token in ['p3p_enforce_observability_snapshot', 'p3p_routing_decision_audit_search', 'p3p_legacy_final_report', 'p3p_release_artifact_retention_policy']:
    if token not in migration:
        fail(f'Migration missing token: {token}')

pkg = (ROOT / 'ai-event-gateway-admin-ui/package.json').read_text()
for token in ['verify:p3p-observability', 'export:p3p-observability']:
    if token not in pkg:
        fail(f'package.json missing token: {token}')

print('OK P3-P production ENFORCE observability verification wiring is present.')

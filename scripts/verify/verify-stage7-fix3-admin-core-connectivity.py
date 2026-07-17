#!/usr/bin/env python3
from pathlib import Path

ROOT = Path(__file__).resolve().parents[2]

def require(path: str, *needles: str) -> None:
    text = (ROOT / path).read_text(encoding='utf-8')
    for needle in needles:
        if needle not in text:
            raise SystemExit(f'{path}: missing {needle!r}')

require('ai-event-gateway-admin-ui/lib/server/backendOrigins.ts',
        'CORE_BACKEND_FALLBACK_ORIGINS',
        'OPENDISPATCH_PUBLIC_HOST',
        'fetchBackend',
        'BackendConnectionError')
require('ai-event-gateway-admin-ui/lib/server/authProxy.ts',
        "fetchBackend('core'",
        'configuredOrigins',
        'fallback origin selected')
require('ai-event-gateway-admin-ui/scripts/wait-for-core-backend.mjs',
        '/api/auth/csrf',
        'CORE_BACKEND_FALLBACK_ORIGINS',
        'Core authentication is reachable')
require('scripts/release/admin-ui-runtime-entrypoint.sh',
        'wait-for-core-backend.mjs')
require('deploy/docker-compose.local.yml',
        'CORE_BACKEND_FALLBACK_ORIGINS',
        'host.docker.internal:host-gateway')
require('ai-event-gateway-admin-ui/app/api/health/dependencies/route.ts',
        'coreAuthentication',
        'configuredOrigins')
require('ai-event-gateway-admin-ui/tests/stage7-fix3-admin-core-connectivity.test.ts',
        'falls back when the primary Core origin is unreachable',
        'adds the configured public host as a Core fallback',
        'reports each attempted Core origin')
require('scripts/diagnostics/admin-ui-core-connectivity.sh',
        '/api/health/dependencies',
        'wait-for-core-backend.mjs')
print('Stage 7 Fix3 Admin UI -> Core connectivity contract verified.')

#!/usr/bin/env node
import { readFileSync, existsSync } from 'node:fs';
import { join } from 'node:path';

const root = process.cwd();

const checks = [
  ['package.json', 'ai-event-gateway-admin-ui'],
  ['lib/constants/env.ts', 'coreApiBaseUrl'],
  ['lib/constants/env.ts', 'nettyApiBaseUrl'],
  ['lib/constants/env.ts', 'nettyRuntimeWsUrl'],
  ['next.config.ts', '/core-api/:path*'],
  ['next.config.ts', '/netty-api/:path*'],
  ['next.config.ts', '/gateway-api/:path*'],
  ['app/core-api/[...path]/route.ts', "'core'"],
  ['app/netty-api/[...path]/route.ts', "'netty'"],
  ['app/gateway-api/[...path]/route.ts', "'gateway'"],
  ['lib/api/coreAdminApi.ts', 'dashboardSnapshot'],
  ['lib/api/nettyRuntimeApi.ts', 'runtimeSnapshot'],
  ['lib/api/endpoints.ts', 'coreAdminEndpoints'],
  ['lib/api/endpoints.ts', 'nettyRuntimeEndpoints'],
  ['components/layout/Sidebar.tsx', 'Agent Governance'],
  ['components/layout/Sidebar.tsx', 'Runtime Events'],
  ['components/layout/Sidebar.tsx', 'Settings'],
  ['components/common/LegacyRuntimePlaneNotice.tsx', 'Legacy Netty runtime view'],
  ['app/cluster/page.tsx', 'LegacyRuntimePlaneNotice'],
  ['app/events/page.tsx', 'LegacyRuntimePlaneNotice'],
  ['app/traces/[traceId]/page.tsx', 'LegacyRuntimePlaneNotice'],
  ['.env.local.example', 'NEXT_PUBLIC_CORE_API_BASE_URL=/core-api'],
  ['.env.local.example', 'NEXT_PUBLIC_NETTY_API_BASE_URL=/netty-api'],
  ['.env.local.example', 'NEXT_PUBLIC_NETTY_RUNTIME_WS_URL=/api/admin/runtime/stream'],
  ['.env.production.example', 'CORE_BACKEND_ORIGIN=http://ai-event-gateway-core:18080'],
  ['.env.production.example', 'NETTY_BACKEND_ORIGIN=http://ai-event-gateway-netty:18081'],
  ['deploy/nginx/admin-ui.conf', 'location /core-api/'],
  ['deploy/nginx/admin-ui.conf', 'location /netty-api/'],
  ['deploy/nginx/admin-ui.conf', 'location /api/admin/runtime/stream'],
  ['docs/P3B9_PRODUCTION_HARDENING_FINAL_CHECK.md', 'Production hardening'],
  ['docs/THREE_WAY_STARTUP_VERIFICATION.md', 'Three-way startup verification'],
  ['components/agents/AgentGovernanceConsole.tsx', 'Agent Governance Console']
];

let failed = 0;
for (const [file, needle] of checks) {
  const path = join(root, file);
  if (!existsSync(path)) {
    console.error(`FAIL missing file: ${file}`);
    failed += 1;
    continue;
  }
  const content = readFileSync(path, 'utf8');
  if (!content.includes(needle)) {
    console.error(`FAIL missing marker in ${file}: ${needle}`);
    failed += 1;
    continue;
  }
  console.log(`OK   ${file} :: ${needle}`);
}

if (failed > 0) {
  console.error(`\nP3B integration verification failed: ${failed} issue(s).`);
  process.exit(1);
}

console.log('\nP3B integration verification passed.');

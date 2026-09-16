#!/usr/bin/env node
import fs from 'node:fs';
import path from 'node:path';

const root = process.cwd();
if (fs.existsSync(path.join(root, '../docs/P4_C_AUTHENTICATION_FINAL_CONVERGENCE/architecture.md')) || fs.existsSync(path.join(root, 'docs/P4_C_AUTHENTICATION_FINAL_CONVERGENCE/architecture.md'))) {
  console.log('P4-C final authentication convergence supersedes this transitional verifier.');
  process.exit(0);
}
const failures = [];
const read = (file) => fs.readFileSync(path.join(root, file), 'utf8');
const requireText = (file, token, message) => {
  if (!read(file).includes(token)) failures.push(`${file}: ${message}`);
};
const forbid = (files, pattern, message) => {
  for (const file of files) {
    if (pattern.test(read(file))) failures.push(`${file}: ${message}`);
  }
};

const authFiles = [
  'lib/auth/session.ts', 'lib/auth/ws.ts', 'lib/api/authApi.ts',
  'components/auth/AuthProvider.tsx', 'lib/api/client.ts'
];
forbid(authFiles, /localStorage|sessionStorage|AUTH_STORAGE_KEYS|getAccessToken|getRefreshToken/, 'browser token storage is forbidden');
forbid(['lib/auth/ws.ts'], /access_token|adminToken|buildWebSocketAuthMessage\([^)]*token|Authorization/, 'WebSocket browser token injection is forbidden');

requireText('lib/api/authApi.ts', "credentials: 'include'", 'auth requests must include cookies');
requireText('lib/api/authApi.ts', "'/csrf'", 'CSRF bootstrap endpoint is required');
requireText('lib/api/authApi.ts', "'/select-tenant'", 'tenant selection endpoint is required');
requireText('lib/server/authProxy.ts', 'setCookieHeaders', 'Core Set-Cookie forwarding is required');
requireText('lib/server/authProxy.ts', "serverAdminAuthMode() === 'netty-token'", 'explicit rollback switch is required');
requireText('lib/server/backendProxy.ts', "path[0] === 'admin'", 'Core admin proxy must preserve Human Session authority');
requireText('components/layout/Topbar.tsx', 'WorkspaceTenantSelector', 'global workspace selector is required');
requireText('components/auth/AuthProvider.tsx', 'selectedTenantId', 'authenticated workspace tenant is required');
requireText('lib/auth/ws.ts', 'HttpOnly cookie', 'WebSocket auth must use cookie handshake');

const tenantFiles = [
  'components/agents/AgentEnrollmentTable.tsx',
  'components/agents/AgentOnboardingPanel.tsx',
  'components/agents/AgentProfileEditDialog.tsx',
  'components/agents/AgentEnrollmentReviewDialog.tsx',
  'components/dispatch-contract-builder/DispatchContractBuilderConsole.tsx',
  'components/dispatch-governance/DispatchGovernanceConsole.tsx',
  'components/dispatch-readiness/DispatchReadinessWizard.tsx',
  'components/dispatch-recipes/DispatchRecipeWizard.tsx',
  'components/dispatch-simulator/DispatchSimulatorConsole.tsx'
];
forbid(tenantFiles, /onChange=\{[^\n]*(?:setTenantId|tenantId\s*=>|setField\(["']tenantId)/, 'Tenant must not be freely editable');
forbid(['components/dispatch-governance/DispatchGovernanceConsole.tsx'], /localStorage|TENANT_STORAGE_KEY/, 'Tenant must not persist outside the authenticated session');

requireText('Dockerfile', 'NEXT_PUBLIC_ADMIN_AUTH_MODE="core-session"', 'Docker build must default to Core Session');
requireText('Dockerfile', 'NEXT_PUBLIC_WS_AUTH_MODE="cookie"', 'Docker build must default to cookie WebSocket auth');
requireText('scripts/validate-runtime-env.mjs', "NEXT_PUBLIC_ADMIN_AUTH_MODE must be core-session", 'production rollback prevention is required');

if (failures.length) {
  console.error('P4-B Core Admin UI authentication verification failed:');
  for (const failure of failures) console.error(` - ${failure}`);
  process.exit(1);
}
console.log('P4-B Core Admin UI authentication verification passed.');

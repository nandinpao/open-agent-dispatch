import { readFileSync } from 'node:fs';

const required = [
  ['components/agents/AgentDuplicateRuntimeSecurityPanel.tsx', 'Duplicate Runtime Security Enforcement'],
  ['components/agents/AgentDuplicateRuntimeSecurityPanel.tsx', 'Quarantine + Revoke Credentials'],
  ['components/agents/AgentDetailView.tsx', 'AgentDuplicateRuntimeSecurityPanel'],
  ['hooks/useAgentDetail.ts', 'enforceDuplicateRuntimeSecurity'],
  ['hooks/useAgentDetail.ts', 'resolveDuplicateRuntimeSecurity'],
  ['lib/api/endpoints.ts', 'agentDuplicateRuntimeEnforce'],
  ['lib/api/endpoints.ts', 'agentDuplicateRuntimeResolve'],
  ['lib/api/coreAdminApi.ts', 'enforceDuplicateRuntimeSecurity'],
  ['lib/types/core.ts', 'CoreDuplicateRuntimeSecurityRequest']
];

for (const [file, token] of required) {
  const content = readFileSync(file, 'utf8');
  if (!content.includes(token)) {
    throw new Error(`P6 verification failed: ${file} does not contain ${token}`);
  }
}

console.log('P6 integration check passed.');

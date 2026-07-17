import fs from 'node:fs';

const required = [
  'components/agents/AgentSecurityEnforcementPolicyPanel.tsx',
  'components/agents/AgentDetailView.tsx',
  'lib/api/coreAdminApi.ts',
  'lib/api/endpoints.ts',
  'lib/types/core.ts',
  'docs/P8_PER_AGENT_SECURITY_POLICY_UI.md'
];

for (const file of required) {
  if (!fs.existsSync(file)) throw new Error(`Missing ${file}`);
}

const api = fs.readFileSync('lib/api/coreAdminApi.ts', 'utf8');
const endpoints = fs.readFileSync('lib/api/endpoints.ts', 'utf8');
const panel = fs.readFileSync('components/agents/AgentSecurityEnforcementPolicyPanel.tsx', 'utf8');
const detail = fs.readFileSync('components/agents/AgentDetailView.tsx', 'utf8');

for (const token of [
  'getAgentSecurityEnforcementPolicy',
  'updateAgentSecurityEnforcementPolicy',
  'defaultSecurityEnforcementPolicy'
]) {
  if (!api.includes(token) && !endpoints.includes(token)) throw new Error(`Missing ${token}`);
}

for (const token of ['ALERT_ONLY', 'QUARANTINE_AND_DISCONNECT', 'QUARANTINE_REVOKE_AND_DISCONNECT', 'SECURITY_NOTIFICATION_QUEUED']) {
  if (!panel.includes(token) && !detail.includes(token)) throw new Error(`Missing UI token ${token}`);
}

console.log('P8 integration check passed.');

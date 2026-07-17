import { readFileSync, existsSync } from 'node:fs';

const required = [
  'components/agents/AgentApprovedCapabilityPolicyPanel.tsx',
  'lib/agents/dispatchPolicy.ts',
  'tests/dispatch-policy.test.ts',
  'docs/P4_APPROVED_CAPABILITY_POLICY.md'
];

const missing = required.filter((path) => !existsSync(path));
if (missing.length > 0) {
  console.error(`P4 integration check failed. Missing files: ${missing.join(', ')}`);
  process.exit(1);
}

const detail = readFileSync('components/agents/AgentDetailView.tsx', 'utf8');
if (!detail.includes('AgentApprovedCapabilityPolicyPanel')) {
  console.error('P4 integration check failed. AgentDetailView does not render AgentApprovedCapabilityPolicyPanel.');
  process.exit(1);
}

const panel = readFileSync('components/agents/AgentApprovedCapabilityPolicyPanel.tsx', 'utf8');
if (!panel.includes('deriveEffectiveDispatchPolicy') || !panel.includes('updateAgentProfile')) {
  console.error('P4 integration check failed. Approved capability panel is not wired to policy preview and Core profile update.');
  process.exit(1);
}

console.log('P4 integration check passed.');

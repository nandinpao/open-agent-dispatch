import fs from 'node:fs';

const checks = [
  ['types', 'lib/types/core.ts', ['CoreAgentApprovedSkill', 'CoreAgentApprovedSkillSyncResult', 'CoreTaskDispatchContractResolveResult']],
  ['endpoints', 'lib/api/endpoints.ts', ['dispatchContractResolve', 'agentApprovedSkills', 'agentSkillSyncApprovedCapabilities']],
  ['api', 'lib/api/coreAdminApi.ts', ['resolveDispatchContract', 'getAgentApprovedSkills', 'syncAgentApprovedSkillsAndCapabilities']],
  ['panel', 'components/agents/AgentSkillRegistryPanel.tsx', ['Resolve Contract', 'Sync Approved Skills', 'Approved Skills Sync Result']],
];

for (const [name, file, tokens] of checks) {
  const text = fs.readFileSync(file, 'utf8');
  for (const token of tokens) {
    if (!text.includes(token)) {
      throw new Error(`P9.3 ${name} check failed: missing ${token} in ${file}`);
    }
  }
}

console.log('P9.3 dispatch contract resolver UI check passed.');

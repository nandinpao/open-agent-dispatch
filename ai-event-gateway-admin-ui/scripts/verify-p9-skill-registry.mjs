import { readFileSync, existsSync } from 'node:fs';

const required = [
  'components/agents/AgentSkillRegistryPanel.tsx',
  'lib/api/coreAdminApi.ts',
  'lib/api/endpoints.ts',
  'lib/types/core.ts',
  'hooks/useAgentDetail.ts',
  'components/agents/AgentDetailView.tsx'
];

for (const file of required) {
  if (!existsSync(file)) throw new Error(`Missing required file: ${file}`);
}

const checks = [
  ['lib/api/endpoints.ts', 'agentSkills'],
  ['lib/api/endpoints.ts', 'agentSkillEvaluation'],
  ['lib/api/coreAdminApi.ts', 'getAgentSkillDefinitions'],
  ['lib/api/coreAdminApi.ts', 'evaluateAgentSkillContract'],
  ['lib/types/core.ts', 'CoreAgentSkillDefinition'],
  ['hooks/useAgentDetail.ts', 'skillDefinitions'],
  ['components/agents/AgentDetailView.tsx', 'AgentSkillRegistryPanel'],
  ['components/agents/AgentSkillRegistryPanel.tsx', 'Approve Matched Skills']
];

for (const [file, text] of checks) {
  const content = readFileSync(file, 'utf8');
  if (!content.includes(text)) throw new Error(`Expected ${file} to include ${text}`);
}

console.log('P9 skill registry integration check passed.');

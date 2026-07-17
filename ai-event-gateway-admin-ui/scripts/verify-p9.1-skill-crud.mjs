import { readFileSync, existsSync } from 'node:fs';

const required = [
  'app/skills/page.tsx',
  'components/skills/SkillRegistryConsole.tsx',
  'components/agents/AgentSkillRegistryPanel.tsx',
  'lib/api/coreAdminApi.ts',
  'lib/api/coreClient.ts',
  'lib/api/endpoints.ts',
  'lib/types/core.ts',
  'components/layout/Sidebar.tsx'
];

for (const file of required) {
  if (!existsSync(file)) throw new Error(`Missing required file: ${file}`);
}

const checks = [
  ['components/skills/SkillRegistryConsole.tsx', 'Skill Registry CRUD'],
  ['components/skills/SkillRegistryConsole.tsx', 'upsertAgentSkillDefinition'],
  ['components/skills/SkillRegistryConsole.tsx', 'deleteAgentSkillDefinition'],
  ['components/skills/SkillRegistryConsole.tsx', 'getAgentSkillRegistryMetadata'],
  ['lib/api/coreClient.ts', 'coreApiDelete'],
  ['lib/api/coreAdminApi.ts', 'deleteAgentSkillDefinition'],
  ['lib/api/coreAdminApi.ts', 'getAgentSkillRegistryMetadata'],
  ['lib/api/endpoints.ts', 'agentSkillsMetadata'],
  ['lib/types/core.ts', 'CoreAgentSkillRegistryMetadata'],
  ['components/layout/Sidebar.tsx', '/skills']
];

for (const [file, text] of checks) {
  const content = readFileSync(file, 'utf8');
  if (!content.includes(text)) throw new Error(`Expected ${file} to include ${text}`);
}

console.log('P9.1 skill CRUD integration check passed.');

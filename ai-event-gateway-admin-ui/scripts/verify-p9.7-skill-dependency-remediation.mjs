import { readFileSync } from 'node:fs';

const checks = [
  ['lib/types/core.ts', 'CoreAgentSkillDependencyGraph'],
  ['lib/types/core.ts', 'CoreAgentSkillRemediationProposal'],
  ['lib/api/endpoints.ts', 'agentSkillDependencyGraph'],
  ['lib/api/endpoints.ts', 'agentSkillFleetRemediationProposal'],
  ['lib/api/coreAdminApi.ts', 'getAgentSkillDependencyGraph'],
  ['lib/api/coreAdminApi.ts', 'proposeFleetSkillRemediation'],
  ['components/skills/SkillRegistryConsole.tsx', 'P9.7 Skill Dependency Graph / Drift Remediation'],
  ['components/skills/SkillRegistryConsole.tsx', 'Save Dependencies'],
  ['components/skills/SkillRegistryConsole.tsx', 'Generate Remediation']
];

for (const [file, token] of checks) {
  const text = readFileSync(file, 'utf8');
  if (!text.includes(token)) {
    console.error(`Missing ${token} in ${file}`);
    process.exit(1);
  }
}

console.log('P9.7 skill dependency graph / remediation UI integration check passed.');

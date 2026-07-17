import fs from 'node:fs';
import path from 'node:path';

const root = process.cwd();
const checks = [
  ['lib/types/core.ts', 'CoreAgentCapabilityDriftReport'],
  ['lib/types/core.ts', 'CoreAgentSkillDeprecationMigrationPlan'],
  ['lib/api/endpoints.ts', 'agentSkillsDrift'],
  ['lib/api/endpoints.ts', 'agentSkillDeprecationPlan'],
  ['lib/api/coreAdminApi.ts', 'getFleetSkillDrift'],
  ['lib/api/coreAdminApi.ts', 'analyzeAgentSkillDeprecationMigration'],
  ['components/skills/SkillRegistryConsole.tsx', 'P9.6 Capability Drift / Deprecation Migration'],
  ['components/skills/SkillRegistryConsole.tsx', 'Run Fleet Drift Scan']
];
const missing = [];
for (const [rel, token] of checks) {
  const file = path.join(root, rel);
  if (!fs.existsSync(file) || !fs.readFileSync(file, 'utf8').includes(token)) {
    missing.push(`${rel}: missing ${token}`);
  }
}
if (missing.length) {
  console.error(missing.join('\n'));
  process.exit(1);
}
console.log('P9.6 drift / deprecation UI integration check passed.');

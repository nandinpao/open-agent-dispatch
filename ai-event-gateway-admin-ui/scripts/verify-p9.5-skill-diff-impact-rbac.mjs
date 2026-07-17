import fs from 'node:fs';
import path from 'node:path';

const root = process.cwd();
const checks = [
  ['lib/types/core.ts', 'CoreAgentSkillDiffResult'],
  ['lib/types/core.ts', 'CoreAgentSkillImpactAnalysisResult'],
  ['lib/types/core.ts', 'CoreAgentSkillApprovalPolicy'],
  ['lib/types/core.ts', 'operatorRoles'],
  ['lib/api/endpoints.ts', 'agentSkillVersionDiff'],
  ['lib/api/endpoints.ts', 'agentSkillVersionImpact'],
  ['lib/api/endpoints.ts', 'agentSkillApprovalPolicy'],
  ['lib/api/coreAdminApi.ts', 'getAgentSkillVersionDiff'],
  ['lib/api/coreAdminApi.ts', 'getAgentSkillVersionImpact'],
  ['lib/api/coreAdminApi.ts', 'updateAgentSkillApprovalPolicy'],
  ['components/skills/SkillRegistryConsole.tsx', 'P9.5 Approval RBAC'],
  ['components/skills/SkillRegistryConsole.tsx', 'Load Diff / Impact'],
  ['components/skills/SkillRegistryConsole.tsx', 'operatorRoles'],
];
const missing = [];
for (const [rel, token] of checks) {
  const full = path.join(root, rel);
  if (!fs.existsSync(full) || !fs.readFileSync(full, 'utf8').includes(token)) {
    missing.push(`${rel}: missing ${token}`);
  }
}
if (missing.length) {
  console.error(missing.join('\n'));
  process.exit(1);
}
console.log('P9.5 skill diff / impact / approval RBAC UI integration check passed.');

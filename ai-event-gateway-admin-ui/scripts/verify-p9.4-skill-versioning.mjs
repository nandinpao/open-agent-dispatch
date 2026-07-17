#!/usr/bin/env node
import { readFileSync } from 'node:fs';

const required = [
  ['lib/types/core.ts', 'CoreAgentSkillVersion'],
  ['lib/types/core.ts', 'CoreAgentSkillAuditEntry'],
  ['lib/types/core.ts', 'CoreAgentSkillWorkflowResult'],
  ['lib/api/endpoints.ts', 'agentSkillVersions'],
  ['lib/api/endpoints.ts', 'agentSkillAudit'],
  ['lib/api/coreAdminApi.ts', 'createAgentSkillDraftVersion'],
  ['lib/api/coreAdminApi.ts', 'publishAgentSkillVersion'],
  ['lib/api/coreAdminApi.ts', 'rollbackAgentSkillVersion'],
  ['components/skills/SkillRegistryConsole.tsx', 'SkillVersionWorkflowPanel'],
  ['components/skills/SkillRegistryConsole.tsx', 'Create Draft Version'],
  ['components/skills/SkillRegistryConsole.tsx', 'Audit History']
];

const missing = [];
for (const [file, token] of required) {
  const text = readFileSync(file, 'utf8');
  if (!text.includes(token)) missing.push(`${file}: missing ${token}`);
}
if (missing.length) {
  console.error(missing.join('\n'));
  process.exit(1);
}
console.log('P9.4 skill versioning UI integration check passed.');

import { readFileSync, existsSync } from 'node:fs';

const required = [
  'components/events/EventDetailView.tsx',
  'lib/api/adminApi.ts',
  'lib/types/admin.ts'
];

for (const file of required) {
  if (!existsSync(file)) throw new Error(`Missing required file: ${file}`);
}

const checks = [
  ['components/events/EventDetailView.tsx', 'Skill Registry dispatch contract'],
  ['components/events/EventDetailView.tsx', 'Matched Skills'],
  ['components/events/EventDetailView.tsx', 'Missing Skill Requirements'],
  ['lib/api/adminApi.ts', 'skillAware'],
  ['lib/api/adminApi.ts', 'matchedSkills'],
  ['lib/api/adminApi.ts', 'missingSkillRequirements'],
  ['lib/types/admin.ts', 'skillEligible'],
  ['lib/types/admin.ts', 'missingSkillRequirements']
];

for (const [file, text] of checks) {
  const content = readFileSync(file, 'utf8');
  if (!content.includes(text)) throw new Error(`Expected ${file} to include ${text}`);
}

console.log('P9.2 skill-aware routing UI integration check passed.');

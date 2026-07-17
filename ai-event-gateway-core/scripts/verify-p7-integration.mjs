import fs from 'node:fs';

const required = [
  'lib/agents/duplicateRuntimeSecurityEvents.ts',
  'components/agents/AgentDuplicateRuntimeSecurityPanel.tsx',
  'components/security/SecurityEventTable.tsx',
  'tests/duplicate-runtime-security-events.test.ts'
];
for (const file of required) {
  if (!fs.existsSync(file)) throw new Error(`Missing P7 file: ${file}`);
}
const panel = fs.readFileSync('components/agents/AgentDuplicateRuntimeSecurityPanel.tsx', 'utf8');
if (!panel.includes('Netty auto-detection event')) throw new Error('P7 auto-detection banner missing');
const util = fs.readFileSync('lib/agents/duplicateRuntimeSecurityEvents.ts', 'utf8');
for (const token of ['DUPLICATE_RUNTIME_DETECTED', 'DUPLICATE_RUNTIME_AUTO_ENFORCED', 'DUPLICATE_RUNTIME_AUTO_ENFORCEMENT_FAILED']) {
  if (!util.includes(token)) throw new Error(`Missing ${token}`);
}
console.log('P7 integration check passed.');

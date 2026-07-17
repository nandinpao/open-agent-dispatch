import fs from 'node:fs';

const requiredFiles = [
  'components/dispatch-simulator/DispatchSimulatorConsole.tsx',
  'app/testing/dispatch-simulator/page.tsx',
  'components/migration-readiness/MigrationReadinessCenter.tsx',
  'app/settings/migration-readiness/page.tsx',
  'docs/P3_J_DISPATCH_SIMULATOR_MIGRATION_READINESS.md',
  'lib/navigation/adminInformationArchitecture.ts'
];

for (const file of requiredFiles) {
  if (!fs.existsSync(file)) throw new Error(`Missing required file: ${file}`);
}

const simulator = fs.readFileSync('components/dispatch-simulator/DispatchSimulatorConsole.tsx', 'utf8');
for (const token of [
  'Dispatch Simulator',
  'getTaskEligibleAgentsV2',
  'buildFallbackEvaluation',
  'eligibleCandidates',
  'blockedCandidates',
  'Migration Readiness'
]) {
  if (!simulator.includes(token)) throw new Error(`DispatchSimulatorConsole missing token: ${token}`);
}

const readiness = fs.readFileSync('components/migration-readiness/MigrationReadinessCenter.tsx', 'utf8');
for (const token of [
  'MigrationReadinessCenter',
  'looksLikeTaskAlias',
  'legacySkillPolicies',
  'supplyProfilesWithoutBinding',
  'supplyProfilesWithoutCapability',
  'supplyProfilesWithoutQuality',
  'ENFORCE readiness checklist'
]) {
  if (!readiness.includes(token)) throw new Error(`MigrationReadinessCenter missing token: ${token}`);
}

const nav = fs.readFileSync('lib/navigation/adminInformationArchitecture.ts', 'utf8');
for (const token of ['Dispatch Simulator', '/testing/dispatch-simulator', 'Migration Readiness Center', '/settings/migration-readiness']) {
  if (!nav.includes(token)) throw new Error(`Navigation missing P3-J token: ${token}`);
}

const packageJson = JSON.parse(fs.readFileSync('package.json', 'utf8'));
if (!packageJson.scripts?.['verify:p3j-simulator-readiness']) {
  throw new Error('package.json missing verify:p3j-simulator-readiness script');
}

const docs = fs.readFileSync('docs/P3_J_DISPATCH_SIMULATOR_MIGRATION_READINESS.md', 'utf8');
for (const token of ['ROUTING_ELIGIBILITY_ENGINE_MODE=ENFORCE', 'Capability records that still look like task aliases', 'Supply Profiles without Runtime Binding']) {
  if (!docs.includes(token)) throw new Error(`P3-J doc missing token: ${token}`);
}

console.log('OK P3-J dispatch simulator and migration readiness center scaffolding is present.');

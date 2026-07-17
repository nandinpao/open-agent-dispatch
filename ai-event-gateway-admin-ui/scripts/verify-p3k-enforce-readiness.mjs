import fs from 'node:fs';

const requiredFiles = [
  'lib/fixtures/p3kEnforceFixture.ts',
  '../ai-event-gateway-core/control-plane-app/src/test/resources/fixtures/p3k-enforce-ci-fixture.json',
  'lib/eligibility/operatorRepairGuide.ts',
  'components/dispatch-simulator/DispatchSimulatorConsole.tsx',
  'components/migration-readiness/MigrationReadinessCenter.tsx',
  'docs/P3_K_ENFORCE_CI_GATE_OPERATOR_REPAIR.md',
  '../docs/CURRENT_DISPATCH_DOMAIN_MODEL.md'
];

for (const file of requiredFiles) {
  if (!fs.existsSync(file)) throw new Error(`Missing required file: ${file}`);
}

const fixtureTs = fs.readFileSync('lib/fixtures/p3kEnforceFixture.ts', 'utf8');
for (const token of [
  'p3kEnforceFixtureEvaluation',
  'P3K_ENFORCE_FIXTURE',
  'agent-p3k-ready',
  'P3H_ACTIVE_RUNTIME_BINDING_REQUIRED',
  'P3H_REQUIRED_CAPABILITY_MISSING',
  'P3H_REQUIRED_RUNTIME_FEATURE_NOT_TRUSTED',
  'P3H_QUALITY_RULE_FAILED'
]) {
  if (!fixtureTs.includes(token)) throw new Error(`P3-K TS fixture missing token: ${token}`);
}

const fixtureJson = JSON.parse(fs.readFileSync('../ai-event-gateway-core/control-plane-app/src/test/resources/fixtures/p3k-enforce-ci-fixture.json', 'utf8'));
const expectedCodes = fixtureJson.expected?.blockedCodes ?? [];
for (const code of [
  'P3H_ACTIVE_RUNTIME_BINDING_REQUIRED',
  'P3H_REQUIRED_CAPABILITY_MISSING',
  'P3H_REQUIRED_RUNTIME_FEATURE_NOT_TRUSTED',
  'P3H_QUALITY_RULE_FAILED'
]) {
  if (!expectedCodes.includes(code)) throw new Error(`P3-K JSON fixture missing expected blocking code: ${code}`);
}
if (!fixtureJson.expected?.failClosed) throw new Error('P3-K JSON fixture must assert failClosed=true');

const simulator = fs.readFileSync('components/dispatch-simulator/DispatchSimulatorConsole.tsx', 'utf8');
for (const token of ['Load P3-K ENFORCE Fixture', 'p3kEnforceFixtureEvaluation', 'blockingCodes', 'repairGuideForBlockingCode']) {
  if (!simulator.includes(token)) throw new Error(`DispatchSimulatorConsole missing P3-K token: ${token}`);
}

const readiness = fs.readFileSync('components/migration-readiness/MigrationReadinessCenter.tsx', 'utf8');
for (const token of ['Export Readiness JSON', 'Copy Repair Checklist', 'Operator repair workflow', 'buildReadinessExport', 'operatorRepairWorkflow']) {
  if (!readiness.includes(token)) throw new Error(`MigrationReadinessCenter missing P3-K token: ${token}`);
}

const repairGuide = fs.readFileSync('lib/eligibility/operatorRepairGuide.ts', 'utf8');
for (const token of ['p3kOperatorRepairGuides', '/settings/runtime-resources', '/settings/capabilities', '/settings/runtime-features', '/settings/quality-metrics']) {
  if (!repairGuide.includes(token)) throw new Error(`operatorRepairGuide missing token: ${token}`);
}

const docs = fs.readFileSync('docs/P3_K_ENFORCE_CI_GATE_OPERATOR_REPAIR.md', 'utf8');
for (const token of ['LEGACY_ONLY', 'SHADOW', 'WARN', 'ENFORCE', 'npm run verify:p3k-enforce-gate', 'ROUTING_ELIGIBILITY_ENGINE_MODE=ENFORCE']) {
  if (!docs.includes(token)) throw new Error(`P3-K doc missing token: ${token}`);
}

const packageJson = JSON.parse(fs.readFileSync('package.json', 'utf8'));
if (!packageJson.scripts?.['verify:p3k-enforce-gate']) {
  throw new Error('package.json missing verify:p3k-enforce-gate script');
}

console.log('OK P3-K ENFORCE fixture, readiness export, repair workflow, and runbook are present.');

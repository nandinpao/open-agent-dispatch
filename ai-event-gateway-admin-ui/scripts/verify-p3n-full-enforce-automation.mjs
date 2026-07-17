import fs from 'node:fs';

const requiredFiles = [
  'lib/fixtures/p3nFullEnforceAutomationFixture.ts',
  '../ai-event-gateway-core/control-plane-app/src/test/resources/fixtures/p3n-enforce-runtime-seed.json',
  'scripts/p3n-enforce-fixture-bootstrap.mjs',
  'scripts/p3n-full-enforce-acceptance.mjs',
  'docs/P3_N_RUNTIME_FIXTURE_SEEDING_FULL_ENFORCE_ACCEPTANCE.md',
  '../scripts/acceptance/p3n-full-enforce-acceptance.sh',
  '../scripts/verify/verify-p3n-full-enforce-automation.py',
  'components/release-cutover/EnforceReleaseCutoverPanel.tsx',
  'package.json',
  '../Makefile',
  '../docs/CURRENT_DISPATCH_DOMAIN_MODEL.md',
];

for (const file of requiredFiles) {
  if (!fs.existsSync(file)) throw new Error(`Missing required file: ${file}`);
}

const fixture = fs.readFileSync('lib/fixtures/p3nFullEnforceAutomationFixture.ts', 'utf8');
for (const token of [
  'P3N_FULL_ENFORCE_ACCEPTANCE_AUTOMATION',
  'P3N_ACCEPTANCE_OUTPUT_DIR',
  'p3n-seed-report.json',
  'p3n-enforce-runtime-acceptance.json',
  'p3n-readiness-report.json',
  'p3n-teardown-report.json',
  'p3n-rollback-rehearsal.json',
  'P3H_ACTIVE_RUNTIME_BINDING_REQUIRED',
  'P3H_REQUIRED_CAPABILITY_MISSING',
  'P3H_REQUIRED_RUNTIME_FEATURE_NOT_TRUSTED',
  'P3H_QUALITY_RULE_FAILED',
]) {
  if (!fixture.includes(token)) throw new Error(`P3-N fixture missing token: ${token}`);
}

const seed = JSON.parse(fs.readFileSync('../ai-event-gateway-core/control-plane-app/src/test/resources/fixtures/p3n-enforce-runtime-seed.json', 'utf8'));
for (const token of ['taskDefinition', 'capabilities', 'runtimeResources', 'runtimeBindings', 'supplyProfiles', 'dispatchPolicy', 'qualitySnapshots', 'expected']) {
  if (!(token in seed)) throw new Error(`P3-N seed missing key: ${token}`);
}
for (const code of seed.expected.blockedCodes) {
  if (!fixture.includes(code)) throw new Error(`P3-N fixture missing expected seed blocked code: ${code}`);
}

const bootstrap = fs.readFileSync('scripts/p3n-enforce-fixture-bootstrap.mjs', 'utf8');
for (const token of ['P3N_FIXTURE_ACTION', 'P3N_FIXTURE_MODE', 'P3N_STRICT_BOOTSTRAP', 'seedBodyVariants', 'teardownCalls', '/admin/runtime-resources/', '/admin/supply-profiles/']) {
  if (!bootstrap.includes(token)) throw new Error(`P3-N bootstrap missing token: ${token}`);
}

const oneCommand = fs.readFileSync('scripts/p3n-full-enforce-acceptance.mjs', 'utf8');
for (const token of ['p3n-enforce-fixture-bootstrap.mjs', 'p3m-enforce-runtime-acceptance.mjs', 'p3n-readiness-report.json', 'p3n-rollback-rehearsal.json', 'P3N_CHECK_ROUTING_DECISION', 'ENFORCE', 'WARN']) {
  if (!oneCommand.includes(token)) throw new Error(`P3-N one-command script missing token: ${token}`);
}

const panel = fs.readFileSync('components/release-cutover/EnforceReleaseCutoverPanel.tsx', 'utf8');
for (const token of ['P3-N one-command automation', 'npm run acceptance:p3n-full-enforce', 'seed → acceptance → readiness → rollback → teardown', 'P3N_RUNTIME_ACCEPTANCE_MODE=live']) {
  if (!panel.includes(token)) throw new Error(`Release cutover panel missing P3-N token: ${token}`);
}

const docs = fs.readFileSync('docs/P3_N_RUNTIME_FIXTURE_SEEDING_FULL_ENFORCE_ACCEPTANCE.md', 'utf8');
for (const token of ['P3-N', 'seed → acceptance → export → teardown', 'P3N_CLEANUP', 'Rollback rehearsal', 'acceptance:p3n-full-enforce']) {
  if (!docs.includes(token)) throw new Error(`P3-N doc missing token: ${token}`);
}

const pkg = JSON.parse(fs.readFileSync('package.json', 'utf8'));
if (!pkg.scripts?.['verify:p3n-full-enforce']) throw new Error('package.json missing verify:p3n-full-enforce script');
if (!pkg.scripts?.['acceptance:p3n-full-enforce']) throw new Error('package.json missing acceptance:p3n-full-enforce script');
if (!pkg.scripts?.ci?.includes('verify:p3n-full-enforce')) throw new Error('ci script must include verify:p3n-full-enforce after P3-N.');

const makefile = fs.readFileSync('../Makefile', 'utf8');
for (const token of ['verify-p3n-full-enforce-acceptance', 'P3-N full ENFORCE acceptance automation']) {
  if (!makefile.includes(token)) throw new Error(`Makefile missing token: ${token}`);
}

console.log('OK P3-N runtime fixture seeding, full ENFORCE acceptance automation, readiness export, teardown, and rollback rehearsal wiring are present.');

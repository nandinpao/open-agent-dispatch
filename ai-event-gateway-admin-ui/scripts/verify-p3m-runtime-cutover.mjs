import fs from 'node:fs';

const requiredFiles = [
  'lib/fixtures/p3mRuntimeAcceptanceFixture.ts',
  'components/release-cutover/EnforceReleaseCutoverPanel.tsx',
  'app/settings/release-cutover/page.tsx',
  'scripts/p3m-enforce-runtime-acceptance.mjs',
  'docs/P3_M_ENFORCE_RUNTIME_ACCEPTANCE_RELEASE_CUTOVER.md',
  '../scripts/acceptance/p3m-enforce-runtime-acceptance.sh',
  '../docs/CURRENT_DISPATCH_DOMAIN_MODEL.md',
  'package.json',
];

for (const file of requiredFiles) {
  if (!fs.existsSync(file)) throw new Error(`Missing required file: ${file}`);
}

const fixture = fs.readFileSync('lib/fixtures/p3mRuntimeAcceptanceFixture.ts', 'utf8');
for (const token of [
  'P3M_ENFORCE_RUNTIME_ACCEPTANCE',
  'p3m-enforce-runtime-acceptance.json',
  'P3M_CORE_BASE_URL',
  'P3M_ACCEPTANCE_OUTPUT',
  'P3H_ACTIVE_RUNTIME_BINDING_REQUIRED',
  'P3H_REQUIRED_CAPABILITY_MISSING',
  'P3H_REQUIRED_RUNTIME_FEATURE_NOT_TRUSTED',
  'P3H_QUALITY_RULE_FAILED',
  'LEGACY_ONLY',
  'SHADOW',
  'WARN',
  'ENFORCE',
]) {
  if (!fixture.includes(token)) throw new Error(`P3-M fixture missing token: ${token}`);
}

const panel = fs.readFileSync('components/release-cutover/EnforceReleaseCutoverPanel.tsx', 'utf8');
for (const token of [
  'RELEASE_CUTOVER_RUNBOOK',
  'Runtime acceptance command',
  'Rollback runbook',
  'Copy cutover checklist',
  'ROUTING_ELIGIBILITY_ENGINE_MODE=ENFORCE',
  'P3M_RUNTIME_ACCEPTANCE_MODE=live',
]) {
  if (!panel.includes(token)) throw new Error(`Release cutover panel missing token: ${token}`);
}

const page = fs.readFileSync('app/settings/release-cutover/page.tsx', 'utf8');
for (const token of ['ENFORCE Release Cutover', 'Migration Readiness & Repair', 'Dispatch Simulator']) {
  if (!page.includes(token)) throw new Error(`Release cutover page missing token: ${token}`);
}

const nav = fs.readFileSync('lib/navigation/adminInformationArchitecture.ts', 'utf8');
for (const token of ['/settings/release-cutover', 'ENFORCE Release Cutover', 'P3-M']) {
  if (!nav.includes(token)) throw new Error(`Navigation missing P3-M token: ${token}`);
}

const acceptance = fs.readFileSync('scripts/p3m-enforce-runtime-acceptance.mjs', 'utf8');
for (const token of [
  'P3M_RUNTIME_ACCEPTANCE_MODE',
  'eligible-agents-v2',
  'routing-decision-json-optional',
  'P3M_CHECK_ROUTING_DECISION',
  'blocked-codes-present',
  'p3m-enforce-runtime-acceptance.json',
]) {
  if (!acceptance.includes(token)) throw new Error(`Runtime acceptance script missing token: ${token}`);
}

const shell = fs.readFileSync('../scripts/acceptance/p3m-enforce-runtime-acceptance.sh', 'utf8');
for (const token of ['P3M_RUNTIME_ACCEPTANCE_MODE', 'P3M_CORE_BASE_URL', 'npm run acceptance:p3m-enforce-runtime']) {
  if (!shell.includes(token)) throw new Error(`Root acceptance wrapper missing token: ${token}`);
}

const docs = fs.readFileSync('docs/P3_M_ENFORCE_RUNTIME_ACCEPTANCE_RELEASE_CUTOVER.md', 'utf8');
for (const token of [
  'P3-M',
  'runtime acceptance',
  'Release cutover runbook',
  'Rollback runbook',
  'ROUTING_ELIGIBILITY_ENGINE_MODE=ENFORCE',
  'npm run acceptance:p3m-enforce-runtime',
]) {
  if (!docs.includes(token)) throw new Error(`P3-M doc missing token: ${token}`);
}

const pkg = JSON.parse(fs.readFileSync('package.json', 'utf8'));
if (!pkg.scripts?.['verify:p3m-runtime-cutover']) throw new Error('package.json missing verify:p3m-runtime-cutover script');
if (!pkg.scripts?.['acceptance:p3m-enforce-runtime']) throw new Error('package.json missing acceptance:p3m-enforce-runtime script');
if (!pkg.scripts?.ci?.includes('verify:p3m-runtime-cutover')) throw new Error('ci script must include verify:p3m-runtime-cutover after P3-M.');

console.log('OK P3-M runtime acceptance, release cutover, rollback runbook, and CI gate wiring are present.');

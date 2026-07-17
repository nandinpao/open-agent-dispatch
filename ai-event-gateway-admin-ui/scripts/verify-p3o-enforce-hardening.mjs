import fs from 'node:fs';

const requiredFiles = [
  'lib/fixtures/p3oEnforceDefaultHardeningFixture.ts',
  'scripts/p3o-release-artifact-archive.mjs',
  'scripts/p3o-release-artifact-gate.mjs',
  'scripts/p3o-rollback-live-rehearsal.mjs',
  'components/release-cutover/EnforceReleaseCutoverPanel.tsx',
  'docs/P3_O_ENFORCE_DEFAULT_HARDENING_LEGACY_RUNTIME_REMOVAL.md',
  '../ai-event-gateway-core/control-plane-app/src/main/resources/application-prod.yml',
  '../ai-event-gateway-core/control-plane-app/src/main/resources/application-enforce.yml',
  '../ai-event-gateway-core/task-orchestration/src/main/java/com/opensocket/aievent/core/routing/RoutingProperties.java',
  '../ai-event-gateway-core/task-orchestration/src/main/java/com/opensocket/aievent/core/routing/RoutingDecisionService.java',
  '../ai-event-gateway-core/database-platform/src/main/resources/db/migration/V74__p3o_enforce_hardening_legacy_readonly.sql',
  '../scripts/verify/verify-p3o-enforce-hardening.py',
  '../Makefile',
  '../docs/CURRENT_DISPATCH_DOMAIN_MODEL.md',
];
for (const file of requiredFiles) {
  if (!fs.existsSync(file)) throw new Error(`Missing required file: ${file}`);
}

const fixture = fs.readFileSync('lib/fixtures/p3oEnforceDefaultHardeningFixture.ts', 'utf8');
for (const token of [
  'P3O_ENFORCE_DEFAULT_HARDENING',
  'prodDefault',
  'WARN',
  'application-enforce.yml',
  'release:verify:p3o-enforce-artifact',
  'p3o-release-artifact-manifest.json',
  'p3o_legacy_readonly_report',
  'eligibilityV2ScoreBreakdown',
]) {
  if (!fixture.includes(token)) throw new Error(`P3-O fixture missing token: ${token}`);
}

const prod = fs.readFileSync('../ai-event-gateway-core/control-plane-app/src/main/resources/application-prod.yml', 'utf8');
if (!prod.includes('ROUTING_ELIGIBILITY_ENGINE_MODE:WARN')) throw new Error('application-prod.yml must default routing eligibility engine mode to WARN after P3-O.');
const enforce = fs.readFileSync('../ai-event-gateway-core/control-plane-app/src/main/resources/application-enforce.yml', 'utf8');
for (const token of ['ROUTING_ELIGIBILITY_ENGINE_MODE:ENFORCE', 'require-v2-score-breakdown-in-enforce', 'legacy-profile-eligibility-disabled-in-enforce']) {
  if (!enforce.includes(token)) throw new Error(`application-enforce.yml missing token: ${token}`);
}

const props = fs.readFileSync('../ai-event-gateway-core/task-orchestration/src/main/java/com/opensocket/aievent/core/routing/RoutingProperties.java', 'utf8');
for (const token of ['requireV2ScoreBreakdownInEnforce', 'legacyProfileEligibilityDisabledInEnforce']) {
  if (!props.includes(token)) throw new Error(`RoutingProperties missing P3-O token: ${token}`);
}

const routing = fs.readFileSync('../ai-event-gateway-core/task-orchestration/src/main/java/com/opensocket/aievent/core/routing/RoutingDecisionService.java', 'utf8');
for (const token of [
  'legacyProfileEligibilityDisabledFor',
  'hasRequiredV2ScoreBreakdown',
  'userFacingV2ScoreBreakdownRequiredError',
  'eligibilityV2ScoreBreakdown',
  'backend Assignment Profile eligibility service not available',
]) {
  if (!routing.includes(token)) throw new Error(`RoutingDecisionService missing P3-O token: ${token}`);
}
if (!routing.includes('if (!legacyProfileEligibilityDisabledFor(eligibilityMode) && isProfileNotConfigured(requirements))')) {
  throw new Error('RoutingDecisionService must skip legacy profile-not-configured gate in ENFORCE when legacy fallback is disabled.');
}

const migration = fs.readFileSync('../ai-event-gateway-core/database-platform/src/main/resources/db/migration/V74__p3o_enforce_hardening_legacy_readonly.sql', 'utf8');
for (const token of ['p3o_legacy_readonly_report', 'CAPABILITY_TASK_COUPLED_READONLY', 'ASSIGNMENT_PROFILE_TASK_COUPLED_READONLY', 'SKILL_REGISTRY_POLICY_READONLY']) {
  if (!migration.includes(token)) throw new Error(`P3-O migration missing token: ${token}`);
}

const panel = fs.readFileSync('components/release-cutover/EnforceReleaseCutoverPanel.tsx', 'utf8');
for (const token of ['P3-O OPERATOR_CUTOVER_DASHBOARD', 'Production default', 'Mandatory live gate', 'artifact archive and release gate']) {
  if (!panel.includes(token)) throw new Error(`Release cutover panel missing P3-O token: ${token}`);
}

const archive = fs.readFileSync('scripts/p3o-release-artifact-archive.mjs', 'utf8');
for (const token of ['P3O_RELEASE_ARTIFACT_ARCHIVE_DIR', 'p3o-release-artifact-manifest.json', 'p3n-full-enforce-summary.json']) {
  if (!archive.includes(token)) throw new Error(`P3-O archive script missing token: ${token}`);
}
const gate = fs.readFileSync('scripts/p3o-release-artifact-gate.mjs', 'utf8');
for (const token of ['P3O_REQUIRE_LIVE_ACCEPTANCE', 'P3O_ALLOW_FIXTURE_ARTIFACT', 'Live acceptance artifact required']) {
  if (!gate.includes(token)) throw new Error(`P3-O release gate script missing token: ${token}`);
}

const pkg = JSON.parse(fs.readFileSync('package.json', 'utf8'));
for (const script of ['verify:p3o-hardening', 'archive:p3o-release-artifacts', 'release:verify:p3o-enforce-artifact', 'rehearsal:p3o-rollback-live']) {
  if (!pkg.scripts?.[script]) throw new Error(`package.json missing script: ${script}`);
}
if (!pkg.scripts?.ci?.includes('verify:p3o-hardening')) throw new Error('ci script must include verify:p3o-hardening after P3-O.');

const makefile = fs.readFileSync('../Makefile', 'utf8');
for (const token of ['verify-p3o-enforce-hardening', 'P3-O ENFORCE default hardening']) {
  if (!makefile.includes(token)) throw new Error(`Makefile missing P3-O token: ${token}`);
}

console.log('OK P3-O ENFORCE default hardening, live artifact gate, legacy fallback removal, cutover dashboard, and artifact archive wiring are present.');

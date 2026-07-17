import fs from 'node:fs';

const requiredFiles = [
  'docs/P3_L_LEGACY_FEATURE_CLEANUP_CONTROLLED_REMOVAL.md',
  '../docs/CURRENT_DISPATCH_DOMAIN_MODEL.md',
  '../ai-event-gateway-core/database-platform/src/main/resources/db/migration/V73__p3l_legacy_feature_cleanup_controlled_removal.sql',
  '../ai-event-gateway-core/agent-control/src/main/java/com/opensocket/aievent/core/agent/assignment/AgentAssignmentService.java',
  '../ai-event-gateway-core/control-plane-app/src/main/java/com/opensocket/aievent/core/api/AgentSkillRegistryController.java',
  'app/settings/dispatch-contract-builder/page.tsx',
  'app/dispatch-policies/page.tsx',
  'app/supply-profiles/page.tsx',
  'scripts/route-smoke.mjs',
  'package.json'
];

for (const file of requiredFiles) {
  if (!fs.existsSync(file)) throw new Error(`Missing required file: ${file}`);
}

const nav = fs.readFileSync('lib/navigation/adminInformationArchitecture.ts', 'utf8');
if (nav.includes("href: '/settings/dispatch-contract-builder'")) {
  throw new Error('Legacy Contract Builder must not appear in Advanced Management navigation after P3-L.');
}
for (const token of ['P3-L', 'legacy feature cleanup', 'task-like capability']) {
  if (!nav.includes(token)) throw new Error(`Navigation missing P3-L cleanup token: ${token}`);
}

const routeSmoke = fs.readFileSync('scripts/route-smoke.mjs', 'utf8');
for (const path of ["'/skills'", "'/assignment-profiles'", "'/settings/dispatch-contract-builder'"]) {
  if (routeSmoke.includes(path)) throw new Error(`Default route smoke should not include legacy path ${path}`);
}

const contractBuilder = fs.readFileSync('app/settings/dispatch-contract-builder/page.tsx', 'utf8');
for (const token of ['LEGACY_TOOL_HIDDEN', 'Deprecated Legacy Contract Builder', 'Open Dispatch Simulator']) {
  if (!contractBuilder.includes(token)) throw new Error(`Deprecated contract builder page missing token: ${token}`);
}

const policiesPage = fs.readFileSync('app/dispatch-policies/page.tsx', 'utf8');
for (const token of ['READONLY_LEGACY_POLICY_REGISTRY', 'DispatchPolicyV2Console', 'Migration Readiness & Repair']) {
  if (!policiesPage.includes(token)) throw new Error(`Dispatch Policies page missing P3-L token: ${token}`);
}
if (policiesPage.includes('<SkillRegistryConsole')) {
  throw new Error('Dispatch Policies page must not render SkillRegistryConsole in the default P3-L workflow.');
}

const supplyProfilesPage = fs.readFileSync('app/supply-profiles/page.tsx', 'utf8');
if (supplyProfilesPage.includes('AssignmentProfilesConsole')) {
  throw new Error('Supply Profiles page must not import/render legacy AssignmentProfilesConsole in P3-L.');
}
for (const token of ['LEGACY_ASSIGNMENT_PROFILE_REMOVED_FROM_WORKFLOW', 'SupplyProfilesConsole']) {
  if (!supplyProfilesPage.includes(token)) throw new Error(`Supply Profiles page missing token: ${token}`);
}

const assignmentService = fs.readFileSync('../ai-event-gateway-core/agent-control/src/main/java/com/opensocket/aievent/core/agent/assignment/AgentAssignmentService.java', 'utf8');
for (const token of [
  'LEGACY_TASK_COUPLED_CAPABILITY_CREATION_BLOCKED',
  'LEGACY_TASK_COUPLED_CAPABILITY_UPDATE_BLOCKED',
  'LEGACY_ASSIGNMENT_PROFILE_CREATION_BLOCKED'
]) {
  if (!assignmentService.includes(token)) throw new Error(`AgentAssignmentService missing guard: ${token}`);
}

const skillController = fs.readFileSync('../ai-event-gateway-core/control-plane-app/src/main/java/com/opensocket/aievent/core/api/AgentSkillRegistryController.java', 'utf8');
for (const token of [
  'requireLegacySkillWriteOverride',
  'p3lLegacySkillWriteOverride',
  'LEGACY_SKILL_REGISTRY_WRITE_BLOCKED',
  'LEGACY_SKILL_REGISTRY_DELETE_BLOCKED',
  'LEGACY_SKILL_VERSION_PUBLISH_BLOCKED'
]) {
  if (!skillController.includes(token)) throw new Error(`AgentSkillRegistryController missing legacy write block token: ${token}`);
}

const migration = fs.readFileSync('../ai-event-gateway-core/database-platform/src/main/resources/db/migration/V73__p3l_legacy_feature_cleanup_controlled_removal.sql', 'utf8');
for (const token of [
  'p3l_legacy_feature_cleanup_report',
  'READONLY_MIGRATION_VISIBILITY',
  'CAPABILITY_TASK_ALIAS',
  'ASSIGNMENT_PROFILE_TASK_ALIAS',
  'SKILL_REGISTRY_POLICY'
]) {
  if (!migration.includes(token)) throw new Error(`P3-L migration missing token: ${token}`);
}

const pkg = JSON.parse(fs.readFileSync('package.json', 'utf8'));
if (!pkg.scripts?.['verify:p3l-legacy-cleanup']) throw new Error('package.json missing verify:p3l-legacy-cleanup script');
if (!pkg.scripts?.ci?.includes('verify:p3k-enforce-gate')) throw new Error('ci script must include verify:p3k-enforce-gate after P3-L.');
if (!pkg.scripts?.ci?.includes('verify:p3l-legacy-cleanup')) throw new Error('ci script must include verify:p3l-legacy-cleanup after P3-L.');

const docs = fs.readFileSync('docs/P3_L_LEGACY_FEATURE_CLEANUP_CONTROLLED_REMOVAL.md', 'utf8');
for (const token of ['P3-L', 'LEGACY_ONLY → SHADOW → WARN → ENFORCE', 'Legacy Contract Builder', 'task-like Capability', 'Supply Profile v2']) {
  if (!docs.includes(token)) throw new Error(`P3-L doc missing token: ${token}`);
}

console.log('OK P3-L legacy feature cleanup, controlled removal, write guards, and CI gate integration are present.');

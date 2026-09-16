#!/usr/bin/env node
import fs from 'node:fs';
import path from 'node:path';

const root = process.cwd();
const repoRoot = path.resolve(root, '..');
function read(file) {
  const abs = path.resolve(root, file);
  if (!fs.existsSync(abs)) throw new Error(`Missing ${file}`);
  return fs.readFileSync(abs, 'utf8');
}
function readRepo(file) {
  const abs = path.resolve(repoRoot, file);
  if (!fs.existsSync(abs)) throw new Error(`Missing ${file}`);
  return fs.readFileSync(abs, 'utf8');
}
function assertIncludes(text, needle, file) {
  if (!text.includes(needle)) throw new Error(`${file} must include ${needle}`);
}
function assertNotIncludes(text, needle, file) {
  if (text.includes(needle)) throw new Error(`${file} must not include ${needle}`);
}

const migrationFile = '../ai-event-gateway-core/database-platform/src/main/resources/db/migration/V97__r4_flow_owned_capabilities_skills.sql';
const migration = read(migrationFile);
for (const token of [
  'skill_name',
  'skill_kind',
  'openclaw_skill',
  'description',
  'legacy_status',
  'dispatch_r4_flow_skill_readiness_report',
  'idx_flow_required_skills_stage_kind',
  'idx_flow_required_skills_openclaw',
]) assertIncludes(migration, token, migrationFile);

const skillViewFile = '../ai-event-gateway-core/data-model/src/main/java/com/opensocket/aievent/core/dispatch/flow/DispatchFlowRequiredSkillView.java';
const skillView = read(skillViewFile);
for (const token of [
  'private String skillName;',
  'private String skillKind;',
  'private Boolean openClawSkill',
  'private String description;',
  'private String legacyStatus;',
  'getOpenClawSkill()',
]) assertIncludes(skillView, token, skillViewFile);

const controllerFile = '../ai-event-gateway-core/control-plane-app/src/main/java/com/opensocket/aievent/core/api/DispatchFlowController.java';
const controller = read(controllerFile);
for (const token of [
  'R4_PREVIEW_ONLY',
  'R4_SKILL_MODEL_PREVIEW',
  'OPENCLAW_ANALYSIS_SKILL',
  'OPENCLAW_A2A_SKILL',
  'RESULT_SKILL',
  'ISSUE_SKILL',
  'FLOW_OWNED_SKILL',
  'standaloneDispatchCapabilities',
]) assertIncludes(controller, token, controllerFile);

const typesFile = 'lib/types/core.ts';
const types = read(typesFile);
for (const token of [
  'skillName?: string;',
  'skillKind?: string;',
  'openClawSkill?: boolean;',
  'description?: string;',
  'legacyStatus?: string;',
]) assertIncludes(types, token, typesFile);

// Stage 4 keeps R4 persistence compatibility, but the beginner workflow owns
// required capabilities inside the complete Dispatch Flow aggregate.
const flowConsoleFile = 'components/dispatch-contract-builder/DispatchContractBuilderConsole.tsx';
const flowConsole = read(flowConsoleFile);
for (const token of [
  '特殊能力（選填）',
  'capabilities.map',
  'requiredSkills:',
  'capabilityRequirementMode:',
  'requestedSkill: undefined',
  'coreAdminApi.createDispatchFlow',
  'coreAdminApi.updateDispatchFlow',
]) assertIncludes(flowConsole, token, flowConsoleFile);
assertNotIncludes(flowConsole, 'coreAdminApi.upsertDispatchFlowSkill', flowConsoleFile);

const capabilityPageFile = 'app/settings/capabilities/page.tsx';
const capabilityPage = read(capabilityPageFile);
for (const token of [
  'Legacy Capability / Skill Diagnostics',
  'R4 Compatibility Notice',
  'Dispatch Capabilities are Flow-owned now',
  '/dispatch-flows',
  '<CapabilityCatalogConsole readOnly />',
]) assertIncludes(capabilityPage, token, capabilityPageFile);

const navigationFile = 'lib/navigation/adminInformationArchitecture.ts';
const navigation = read(navigationFile);
assertNotIncludes(navigation, "href: '/settings/capabilities'", navigationFile);
assertNotIncludes(navigation, "href: '/dispatch-capabilities'", navigationFile);

const redirectFile = 'app/dispatch-capabilities/page.tsx';
const redirect = read(redirectFile);
assertIncludes(redirect, "redirect(buildDispatchFlowsHref(resolvedSearchParams))", redirectFile);

for (const file of [
  '../docs/R4_FLOW_OWNED_CAPABILITIES_SKILLS/README.md',
  '../docs/R4_FLOW_OWNED_CAPABILITIES_SKILLS/r4_change_log.md',
  '../docs/R4_FLOW_OWNED_CAPABILITIES_SKILLS/r4_to_r5_handoff.md',
  'docs/R4_FLOW_OWNED_CAPABILITIES_SKILLS.md',
]) {
  const content = read(file);
  assertIncludes(content, 'R4', file);
}

const modelDoc = readRepo('docs/CURRENT_DISPATCH_DOMAIN_MODEL.md');
for (const token of ['### Capability', 'optional reusable technical ability', 'optional required Capabilities']) assertIncludes(modelDoc, token, 'docs/CURRENT_DISPATCH_DOMAIN_MODEL.md');

const packageJson = JSON.parse(read('package.json'));
if (!packageJson.scripts['verify:r4-flow-owned-capabilities-skills']) throw new Error('package.json missing verify:r4-flow-owned-capabilities-skills');

const makefile = readRepo('Makefile');
assertIncludes(makefile, 'verify-r4-flow-owned-capabilities-skills', 'Makefile');

console.log('OK R4 persistence compatibility remains, while Stage 4 manages optional capabilities only inside the complete Dispatch Flow aggregate and keeps the standalone catalog diagnostics-only.');

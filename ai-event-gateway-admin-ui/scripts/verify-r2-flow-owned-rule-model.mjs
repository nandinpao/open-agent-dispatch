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

const migrationFile = '../ai-event-gateway-core/database-platform/src/main/resources/db/migration/V95__r2_flow_owned_dispatch_rule_model.sql';
const migration = read(migrationFile);
for (const token of [
  'create table if not exists dispatch_flows',
  'alter table if exists dispatch_policies add column if not exists flow_id',
  'rule_scope', 'event_stage',
  'alter table if exists dispatch_policies add column if not exists source_system varchar(128);',
  'idx_dispatch_policies_flow_event_lookup', 'requested_skill', 'handoff_mode',
  'create table if not exists flow_required_skills',
  'alter table if exists tasks add column if not exists matched_flow_id',
  'alter table if exists task_assignments add column if not exists matched_rule_id',
  'dispatch_rule_legacy_status_report',
]) assertIncludes(migration, token, migrationFile);

const controllerFile = '../ai-event-gateway-core/control-plane-app/src/main/java/com/opensocket/aievent/core/api/DispatchFlowController.java';
const controller = read(controllerFile);
for (const token of ['@RequestMapping("/admin/dispatch-flows")', '@GetMapping', '@GetMapping("/{flowId}/rules")', '@GetMapping("/{flowId}/skills")', 'R2_PREVIEW_ONLY', 'A2A_DISPATCH', 'EXTERNAL_INTAKE']) assertIncludes(controller, token, controllerFile);

for (const file of [
  '../ai-event-gateway-core/data-model/src/main/java/com/opensocket/aievent/core/dispatch/flow/DispatchEventStage.java',
  '../ai-event-gateway-core/data-model/src/main/java/com/opensocket/aievent/core/dispatch/flow/DispatchRuleScope.java',
  '../ai-event-gateway-core/data-model/src/main/java/com/opensocket/aievent/core/dispatch/flow/DispatchFlowView.java',
  '../ai-event-gateway-core/data-model/src/main/java/com/opensocket/aievent/core/dispatch/flow/DispatchFlowRuleView.java',
  '../ai-event-gateway-core/data-model/src/main/java/com/opensocket/aievent/core/dispatch/flow/DispatchFlowRequiredSkillView.java',
]) assertIncludes(read(file), 'com.opensocket.aievent.core.dispatch.flow', file);

const endpointsFile = 'lib/api/endpoints.ts';
const endpoints = read(endpointsFile);
for (const token of ["dispatchFlows: '/admin/dispatch-flows'", 'dispatchFlowRules', 'dispatchFlowSkills', 'dispatchFlowRulePreview']) assertIncludes(endpoints, token, endpointsFile);
const apiFile = 'lib/api/coreAdminApi.ts';
const api = read(apiFile);
for (const token of ['getDispatchFlows', 'getDispatchFlowRules', 'getDispatchFlowSkills', 'previewDispatchFlowRule', 'createDispatchFlow', 'updateDispatchFlow']) assertIncludes(api, token, apiFile);

const uiFile = 'components/dispatch-contract-builder/DispatchContractBuilderConsole.tsx';
const ui = read(uiFile);
for (const token of [
  'coreAdminApi.getDispatchFlows',
  'coreAdminApi.createDispatchFlow',
  'coreAdminApi.updateDispatchFlow',
  "eventStage: 'EXTERNAL'",
  'rules: [beginnerRule, ...otherRules]',
  "candidatePoolMode: 'EXPLICIT_FLOW_AGENTS'",
]) assertIncludes(ui, token, uiFile);
assertNotIncludes(ui, 'FlowOwnedRuleSkeletonPanel', uiFile);
assertNotIncludes(ui, 'R2_SKELETON', uiFile);

for (const file of ['../docs/R2_FLOW_OWNED_RULE_MODEL/README.md', '../docs/R2_FLOW_OWNED_RULE_MODEL/r2_change_log.md', '../docs/R2_FLOW_OWNED_RULE_MODEL/r2_to_r3_handoff.md', 'docs/R2_FLOW_OWNED_RULE_MODEL.md']) assertIncludes(read(file), 'R2', file);
const packageJson = JSON.parse(read('package.json'));
if (!packageJson.scripts['verify:r2-flow-owned-rule-model']) throw new Error('package.json missing verify:r2-flow-owned-rule-model');
assertIncludes(readRepo('Makefile'), 'verify-r2-flow-owned-rule-model', 'Makefile');
console.log('OK R2 schema/API compatibility remains and Stage 4 edits Flow-owned rules only through the complete Dispatch Flow aggregate.');

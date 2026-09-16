#!/usr/bin/env node
import fs from 'node:fs';
import path from 'node:path';

const root = process.cwd();
const repoRoot = path.resolve(root, '..');

function read(relativePath) {
  return fs.readFileSync(path.join(repoRoot, relativePath), 'utf8');
}

function assertIncludes(file, content, needle) {
  if (!content.includes(needle)) throw new Error(`${file} is missing required content: ${needle}`);
}

function assertNotIncludes(file, content, needle) {
  if (content.includes(needle)) throw new Error(`${file} still contains forbidden content: ${needle}`);
}

const resultPath = 'ai-event-gateway-core/data-model/src/main/java/com/opensocket/aievent/core/agent/readiness/DispatchReadinessEvaluationResult.java';
const result = read(resultPath);
for (const needle of [
  'private List<String> rawTaskRequirements',
  'private List<String> effectiveDispatchCapabilities',
  'private List<String> legacyTaskAliases',
]) assertIncludes(resultPath, result, needle);

const readinessPath = 'ai-event-gateway-core/agent-control/src/main/java/com/opensocket/aievent/core/agent/readiness/DispatchReadinessEvaluationService.java';
const readiness = read(readinessPath);
for (const needle of [
  'effectiveDispatchCapabilities(rawTaskRequirements, contract)',
  'legacyTaskAliases(rawTaskRequirements, effectiveCapabilities)',
  'governanceCapabilityCheck(agentId, profile, effectiveCapabilities)',
  'runtimeCapabilityCheck(agentId, runtimeItems, agent.orElse(null), effectiveCapabilities)',
  'EFFECTIVE_CAPABILITY_CONTRACT',
  'Governance, runtime and eligibility checks use effective dispatch capabilities, not legacy task aliases.',
]) assertIncludes(readinessPath, readiness, needle);
assertNotIncludes(readinessPath, readiness, 'Agent Governance 尚未核准：');
assertNotIncludes(readinessPath, readiness, 'Skill Registry 找不到或未啟用這些能力');

const contractResultPath = 'ai-event-gateway-core/data-model/src/main/java/com/opensocket/aievent/core/agent/skill/TaskDispatchContractResolveResult.java';
const contractResult = read(contractResultPath);
assertIncludes(contractResultPath, contractResult, 'matchedSkillCodes == null || matchedSkillCodes.isEmpty() ? requiredCapabilities : matchedSkillCodes');

const routingPath = 'ai-event-gateway-core/task-orchestration/src/main/java/com/opensocket/aievent/core/routing/RoutingDecisionService.java';
const routing = read(routingPath);
assertIncludes(routingPath, routing, 'effectiveRoutingCapabilities(task, raw)');
assertIncludes(routingPath, routing, 'resolved.getMatchedSkillCodes()');

const agentDetailPath = 'ai-event-gateway-admin-ui/components/agents/AgentDetailProductView.tsx';
const agentDetail = read(agentDetailPath);
assertIncludes(agentDetailPath, agentDetail, 'Runtime capability mismatch');
assertIncludes(agentDetailPath, agentDetail, 'Core Approval');
assertIncludes(agentDetailPath, agentDetail, 'Runtime Reported');
assertIncludes(agentDetailPath, agentDetail, 'Dispatch Usable');
assertNotIncludes(agentDetailPath, agentDetail, 'Profile capabilities vs runtime-reported capabilities');
assertNotIncludes(agentDetailPath, agentDetail, 'Core profile capabilities');

const editDialogPath = 'ai-event-gateway-admin-ui/components/agents/AgentProfileEditDialog.tsx';
const editDialog = read(editDialogPath);
assertIncludes(editDialogPath, editDialog, 'Capabilities are managed from Agent Detail');
assertNotIncludes(editDialogPath, editDialog, 'CapabilityCardSelector');
assertNotIncludes(editDialogPath, editDialog, 'Governed Capability Cards');
assertNotIncludes(editDialogPath, editDialog, 'capabilities: selectedCapabilities');

const selectorPath = 'ai-event-gateway-admin-ui/components/agents/CapabilityCardSelector.tsx';
const selector = read(selectorPath);
assertNotIncludes(selectorPath, selector, 'Governed Capability Cards');
assertIncludes(selectorPath, selector, 'Capability Catalog');

const typesPath = 'ai-event-gateway-admin-ui/lib/types/core.ts';
const types = read(typesPath);
for (const needle of ['rawTaskRequirements?: string[]', 'effectiveDispatchCapabilities?: string[]', 'legacyTaskAliases?: string[]']) {
  assertIncludes(typesPath, types, needle);
}

const testPath = 'ai-event-gateway-core/agent-control/src/test/java/com/opensocket/aievent/core/agent/readiness/DispatchReadinessEvaluationServiceTest.java';
const test = read(testPath);
assertIncludes(testPath, test, 'shouldUseResolvedEffectiveCapabilitiesInsteadOfLegacyTaskAlias');
assertIncludes(testPath, test, 'result.getEffectiveDispatchCapabilities()).containsExactly("MES_ALARM_TRIAGE")');

console.log('[verify-stage16-effective-capability-contract] OK');

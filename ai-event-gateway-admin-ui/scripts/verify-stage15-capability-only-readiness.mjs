#!/usr/bin/env node
import fs from 'node:fs';
import path from 'node:path';

const root = process.cwd();
const repoRoot = path.resolve(root, '..');

function read(relativePath) {
  return fs.readFileSync(path.join(repoRoot, relativePath), 'utf8');
}

function assertIncludes(file, content, needle) {
  if (!content.includes(needle)) {
    throw new Error(`${file} is missing required content: ${needle}`);
  }
}

function assertNotIncludes(file, content, needle) {
  if (content.includes(needle)) {
    throw new Error(`${file} still contains legacy wording: ${needle}`);
  }
}

const servicePath = 'ai-event-gateway-core/agent-control/src/main/java/com/opensocket/aievent/core/agent/setup/AgentSetupService.java';
const service = read(servicePath);
assertIncludes(servicePath, service, 'RUNTIME_CAPABILITIES_REPORTED');
assertIncludes(servicePath, service, 'Capability assigned, but not reported by runtime');
assertIncludes(servicePath, service, 'OPENSOCKET_AGENT_CAPABILITIES');
assertIncludes(servicePath, service, 'profileCapabilities');
assertIncludes(servicePath, service, 'runtimeReportedCapabilities');
assertIncludes(servicePath, service, 'missingRuntimeCapabilities');

const responsePath = 'ai-event-gateway-core/data-model/src/main/java/com/opensocket/aievent/core/agent/setup/AgentSetupReadinessResponse.java';
const response = read(responsePath);
assertIncludes(responsePath, response, 'private List<String> profileCapabilities');
assertIncludes(responsePath, response, 'private List<String> runtimeReportedCapabilities');
assertIncludes(responsePath, response, 'private List<String> missingRuntimeCapabilities');
assertIncludes(responsePath, response, 'private List<String> extraRuntimeCapabilities');

const startCommandPath = 'ai-event-gateway-core/data-model/src/main/java/com/opensocket/aievent/core/agent/setup/AgentSetupStartCommand.java';
const startCommand = read(startCommandPath);
assertIncludes(startCommandPath, startCommand, 'private List<String> expectedCapabilities');
assertIncludes(startCommandPath, startCommand, 'private String capabilityEnvironmentVariable');

const eligibilityPath = 'ai-event-gateway-core/agent-control/src/main/java/com/opensocket/aievent/core/agent/eligibility/DispatchEligibilityService.java';
const eligibility = read(eligibilityPath);
for (const legacy of [
  'No approved Assignment Profile is available for dispatch',
  'No backend Assignment Profile has been assigned to this Agent',
  'Agent has no ACTIVE Runtime Binding. Runtime observation alone is not a dispatch authority in P3-C.',
]) {
  assertNotIncludes(eligibilityPath, eligibility, legacy);
}
assertIncludes(eligibilityPath, eligibility, 'No active Agent Service Scope is available for dispatch');
assertIncludes(eligibilityPath, eligibility, 'Runtime online status alone is not enough for dispatch authority');

const agentDetailPath = 'ai-event-gateway-admin-ui/components/agents/AgentDetailProductView.tsx';
const agentDetail = read(agentDetailPath);
assertIncludes(agentDetailPath, agentDetail, 'Runtime capability mismatch');
assertIncludes(agentDetailPath, agentDetail, 'OPENSOCKET_AGENT_CAPABILITIES');

const typesPath = 'ai-event-gateway-admin-ui/lib/types/core.ts';
const types = read(typesPath);
assertIncludes(typesPath, types, 'profileCapabilities?: string[]');
assertIncludes(typesPath, types, 'runtimeReportedCapabilities?: string[]');
assertIncludes(typesPath, types, 'missingRuntimeCapabilities?: string[]');
assertIncludes(typesPath, types, 'expectedCapabilities?: string[]');
assertIncludes(typesPath, types, 'capabilityEnvironmentVariable?: string');

const readinessPath = 'ai-event-gateway-core/agent-control/src/main/java/com/opensocket/aievent/core/agent/readiness/DispatchReadinessEvaluationService.java';
const readiness = read(readinessPath);
assertIncludes(readinessPath, readiness, 'Capability assigned, but not reported by runtime');
assertIncludes(readinessPath, readiness, 'OPENSOCKET_AGENT_CAPABILITIES');
assertNotIncludes(readinessPath, readiness, 'Runtime 沒有回報');

const smokePath = 'scripts/acceptance/agent-setup-backend-contract-smoke.mjs';
const smoke = read(smokePath);
assertIncludes(smokePath, smoke, 'RUNTIME_CAPABILITIES_REPORTED');
assertIncludes(smokePath, smoke, 'OPENSOCKET_AGENT_CAPABILITIES');
assertIncludes(smokePath, smoke, 'expectedCapabilities');

console.log('[verify-stage15-capability-only-readiness] OK');

#!/usr/bin/env node
import fs from 'node:fs';
import path from 'node:path';

const root = process.cwd();
const repoRoot = path.resolve(root, '..');
const checks = [
  ['Core DTO', path.join(repoRoot, 'ai-event-gateway-core/data-model/src/main/java/com/opensocket/aievent/core/agent/governance/AgentLatestAuthFailureResponse.java'), ['hasFailure', 'securityEventId', 'denyReason', 'troubleshooting']],
  ['Core service contract', path.join(repoRoot, 'ai-event-gateway-core/agent-control/src/main/java/com/opensocket/aievent/core/agent/governance/AgentGovernanceService.java'), ['latestAuthFailure', 'CORE_AGENT_SECURITY_EVENTS', 'CREDENTIAL_INVALID', 'authorizationDecision', 'denyReason']],
  ['Core admin endpoint', path.join(repoRoot, 'ai-event-gateway-core/control-plane-app/src/main/java/com/opensocket/aievent/core/api/AgentGovernanceController.java'), ['/admin/agents/{agentId}/latest-auth-failure', 'AgentLatestAuthFailureResponse']],
  ['Admin endpoint map', path.join(root, 'lib/api/endpoints.ts'), ['agentLatestAuthFailure', 'latest-auth-failure']],
  ['Admin API client', path.join(root, 'lib/api/coreAdminApi.ts'), ['getAgentLatestAuthFailure', 'CoreAgentLatestAuthFailureResponse']],
  ['Agent detail hook', path.join(root, 'hooks/useAgentDetail.ts'), ['latestAuthFailure', 'getAgentLatestAuthFailure', 'coreLatestAuthFailure']],
  ['Agent detail UI', path.join(root, 'components/agents/AgentDetailProductView.tsx'), ['Runtime Auth Failure', 'Open Security Event', 'latestAuthFailure']],
  ['Acceptance smoke', path.join(repoRoot, 'scripts/acceptance/agent-setup-backend-contract-smoke.mjs'), ['latest-auth-failure', 'validateLatestAuthFailure', 'CREDENTIAL_INVALID']],
  ['Postman collection', path.join(repoRoot, 'docs/postman/OpenDispatch_First_Agent_Setup.postman_collection.json'), ['Read Latest Auth Failure After Invalid Token', 'latest-auth-failure', 'CREDENTIAL_INVALID']],
];

const failures = [];
for (const [label, file, needles] of checks) {
  if (!fs.existsSync(file)) {
    failures.push(`${label}: missing ${file}`);
    continue;
  }
  const text = fs.readFileSync(file, 'utf8');
  for (const needle of needles) {
    if (!text.includes(needle)) failures.push(`${label}: missing ${needle}`);
  }
}

if (failures.length) {
  console.error('[stage13-auth-failure-observability] FAILED');
  for (const failure of failures) console.error(`- ${failure}`);
  process.exit(1);
}
console.log('[stage13-auth-failure-observability] OK');

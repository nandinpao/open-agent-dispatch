#!/usr/bin/env node
import fs from 'node:fs';
import path from 'node:path';

const root = process.cwd();
const repoRoot = path.resolve(root, '..');
const checks = [
  ['Repair action DTO', path.join(repoRoot, 'ai-event-gateway-core/data-model/src/main/java/com/opensocket/aievent/core/agent/governance/AgentConnectionRepairAction.java'), ['actionCode', 'requiresCredentialToken', 'highRisk']],
  ['Repair actions response DTO', path.join(repoRoot, 'ai-event-gateway-core/data-model/src/main/java/com/opensocket/aievent/core/agent/governance/AgentConnectionRepairActionsResponse.java'), ['actions', 'denyReason', 'sourceOfTruth']],
  ['Repair action command DTO', path.join(repoRoot, 'ai-event-gateway-core/data-model/src/main/java/com/opensocket/aievent/core/agent/governance/AgentConnectionRepairActionCommand.java'), ['credentialToken', 'enableAfterRepair', 'revokeExisting']],
  ['Repair action result DTO', path.join(repoRoot, 'ai-event-gateway-core/data-model/src/main/java/com/opensocket/aievent/core/agent/governance/AgentConnectionRepairActionResult.java'), ['latestAuthFailure', 'nextActions', 'profile']],
  ['Core governance service', path.join(repoRoot, 'ai-event-gateway-core/agent-control/src/main/java/com/opensocket/aievent/core/agent/governance/AgentGovernanceService.java'), ['connectionRepairActions', 'executeConnectionRepairAction', 'ROTATE_CREDENTIAL', 'ENABLE_AGENT', 'RESTORE_AGENT_WITH_CREDENTIAL']],
  ['Core controller', path.join(repoRoot, 'ai-event-gateway-core/control-plane-app/src/main/java/com/opensocket/aievent/core/api/AgentGovernanceController.java'), ['/admin/agents/{agentId}/connection-repair-actions', 'executeConnectionRepairAction']],
  ['Admin endpoint map', path.join(root, 'lib/api/endpoints.ts'), ['agentConnectionRepairActions', 'agentConnectionRepairActionExecute']],
  ['Admin API client', path.join(root, 'lib/api/coreAdminApi.ts'), ['getAgentConnectionRepairActions', 'executeAgentConnectionRepairAction']],
  ['Agent detail hook', path.join(root, 'hooks/useAgentDetail.ts'), ['connectionRepairActions', 'getAgentConnectionRepairActions', 'coreConnectionRepairActions']],
  ['Agent detail UI', path.join(root, 'components/agents/AgentDetailProductView.tsx'), ['Repair Actions', 'RepairActionDialog', 'executeAgentConnectionRepairAction']],
  ['Acceptance smoke', path.join(repoRoot, 'scripts/acceptance/agent-setup-backend-contract-smoke.mjs'), ['connection-repair-actions', 'validateConnectionRepairActions', 'ROTATE_CREDENTIAL']],
  ['Postman collection', path.join(repoRoot, 'docs/postman/OpenDispatch_First_Agent_Setup.postman_collection.json'), ['Read Connection Repair Actions', 'Run Rotate Credential Repair Action']],
];

const failures = [];
for (const [label, file, tokens] of checks) {
  if (!fs.existsSync(file)) {
    failures.push(`${label}: missing file ${file}`);
    continue;
  }
  const content = fs.readFileSync(file, 'utf8');
  for (const token of tokens) {
    if (!content.includes(token)) failures.push(`${label}: missing token ${token}`);
  }
}

if (failures.length) {
  console.error('[verify-stage14-connection-repair-actions] FAILED');
  for (const failure of failures) console.error(` - ${failure}`);
  process.exit(1);
}
console.log('[verify-stage14-connection-repair-actions] OK');

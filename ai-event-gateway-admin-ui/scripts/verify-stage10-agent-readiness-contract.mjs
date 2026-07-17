#!/usr/bin/env node
import fs from 'node:fs';
import path from 'node:path';
const root = process.cwd();
const checks = [
  { file: path.join(root, '../ai-event-gateway-core/control-plane-app/src/main/java/com/opensocket/aievent/core/api/AgentSetupController.java'), patterns: ['@GetMapping("/admin/agents/{agentId}/setup-readiness")', 'getSetupReadiness(@PathVariable String agentId)'] },
  { file: path.join(root, '../ai-event-gateway-core/agent-control/src/main/java/com/opensocket/aievent/core/agent/setup/AgentSetupService.java'), patterns: ['AgentSetupReadinessResponse getSetupReadiness', 'GET /admin/agents/{agentId}/setup-readiness', 'sourceOfTruth", "CORE_BACKEND_READINESS'] },
  { file: path.join(root, '../ai-event-gateway-core/data-model/src/main/java/com/opensocket/aievent/core/agent/setup/AgentSetupReadinessResponse.java'), patterns: ['private boolean ready;', 'private List<String> blockingReasons', 'private List<AgentSetupReadinessCheck> checks'] },
  { file: path.join(root, 'lib/api/endpoints.ts'), patterns: ['agentSetupReadiness', '/setup-readiness'] },
  { file: path.join(root, 'lib/api/coreAdminApi.ts'), patterns: ['getAgentSetupReadiness', 'coreAdminEndpoints.agentSetupReadiness'] },
  { file: path.join(root, 'hooks/useAgentDetail.ts'), patterns: ['setupReadiness?: CoreAgentSetupReadinessResponse', 'coreAdminApi.getAgentSetupReadiness(agentId)', 'coreSetupReadiness'] },
  { file: path.join(root, 'components/agents/AgentDetailProductView.tsx'), patterns: ['data.setupReadiness?.checks', 'Readiness Source', 'Core Backend'] },
  { file: path.join(root, 'tests/agent-setup-backend-contract.test.ts'), patterns: ['getAgentSetupReadiness', '/core-api/admin/agents/redmine-agent-001/setup-readiness'] },
];
let failed = false;
for (const check of checks) {
  const content = fs.existsSync(check.file) ? fs.readFileSync(check.file, 'utf8') : '';
  if (!content) { console.error(`[stage10] Missing file: ${path.relative(root, check.file)}`); failed = true; continue; }
  for (const pattern of check.patterns) {
    if (!content.includes(pattern)) { console.error(`[stage10] Missing pattern in ${path.relative(root, check.file)}: ${pattern}`); failed = true; }
  }
}
if (failed) process.exit(1);
console.log('[stage10] Agent setup readiness backend contract verification passed.');

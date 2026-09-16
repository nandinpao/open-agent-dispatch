#!/usr/bin/env node
import fs from 'node:fs';
import path from 'node:path';
const root = process.cwd();
const checks = [
  {
    file: path.join(root, '../ai-event-gateway-core/agent-control/src/test/java/com/opensocket/aievent/core/agent/setup/AgentSetupServiceTest.java'),
    patterns: [
      'shouldReportRuntimeConnectedBlockingReasonBeforeHeartbeat',
      'shouldTransitionReadinessToReadyAfterRuntimeHeartbeatSnapshot',
      'shouldReturnIncompleteWhenRuntimeDisconnectsAfterBeingReady',
      'AgentDirectoryService agentDirectoryService',
    ],
  },
  {
    file: path.join(root, '../ai-event-gateway-core/control-plane-app/src/test/java/com/opensocket/aievent/core/api/AgentSetupControllerMockMvcTest.java'),
    patterns: ['shouldExposeReadyTransitionAfterRuntimeHeartbeat', 'RUNTIME_CONNECTED', 'READY'],
  },
  {
    file: path.join(root, '../ai-event-gateway-core/control-plane-app/src/test/java/com/opensocket/aievent/core/CoreInternalSecurityClassifierTest.java'),
    patterns: ['/connected', '/heartbeat', '/disconnected', 'CoreInternalSecurityRole.GATEWAY'],
  },
  {
    file: path.join(root, '../scripts/acceptance/agent-setup-backend-contract-smoke.mjs'),
    patterns: [
      'Stage 9/10/11',
      'sendRuntimeTransition',
      'expectRuntimeReady',
      'expectRuntimePending',
      '/internal/gateway-nodes/${encodeURIComponent(gatewayNodeId)}/agents/${encodeURIComponent(agentId)}/connected',
      '/internal/gateway-nodes/${encodeURIComponent(gatewayNodeId)}/agents/${encodeURIComponent(agentId)}/heartbeat',
      '/internal/gateway-nodes/${encodeURIComponent(gatewayNodeId)}/agents/${encodeURIComponent(agentId)}/disconnected',
      '--skip-runtime-transition',
    ],
  },
  {
    file: path.join(root, '../docs/postman/OpenDispatch_First_Agent_Setup.postman_collection.json'),
    patterns: [
      'Register Gateway Node',
      'Agent Connected',
      'Agent Heartbeat',
      'Read Setup Readiness After Heartbeat',
      'Read Setup Readiness After Disconnect',
    ],
  },
  {
    file: path.join(root, 'tests/agent-setup-backend-contract.test.ts'),
    patterns: ['Stage 11 runtime heartbeat readiness transition', 'sendRuntimeTransition', 'expectRuntimeReady'],
  },
  {
    file: path.join(root, 'components/agents/AgentDetailProductView.tsx'),
    patterns: ['Refresh Connection', 'RUNTIME_CONNECTED changed to ready'],
  },
];
let failed = false;
for (const check of checks) {
  const content = fs.existsSync(check.file) ? fs.readFileSync(check.file, 'utf8') : '';
  if (!content) {
    console.error(`[stage11] Missing file: ${path.relative(root, check.file)}`);
    failed = true;
    continue;
  }
  for (const pattern of check.patterns) {
    if (!content.includes(pattern)) {
      console.error(`[stage11] Missing pattern in ${path.relative(root, check.file)}: ${pattern}`);
      failed = true;
    }
  }
}
if (failed) process.exit(1);
console.log('[stage11] Runtime heartbeat readiness transition verification passed.');

#!/usr/bin/env node
import fs from 'node:fs';
import path from 'node:path';
const root = process.cwd();
const checks = [
  {
    file: path.join(root, '../ai-event-gateway-core/data-model/src/main/java/com/opensocket/aievent/core/agent/setup/AgentSetupStartCommand.java'),
    patterns: ['dockerCommand', 'localCommand', 'remoteCommand', 'healthCheckCommand', 'verifyConnectionCommand', 'troubleshooting', 'startupSteps'],
  },
  {
    file: path.join(root, '../ai-event-gateway-core/data-model/src/main/java/com/opensocket/aievent/core/agent/setup/AgentSetupTroubleshootingStep.java'),
    patterns: ['severity', 'command', 'metadata', 'public static AgentSetupTroubleshootingStep warn'],
  },
  {
    file: path.join(root, '../ai-event-gateway-core/data-model/src/main/java/com/opensocket/aievent/core/agent/setup/AgentSetupReadinessResponse.java'),
    patterns: ['AgentSetupStartCommand startCommand', 'AgentSetupTroubleshootingStep', 'troubleshooting'],
  },
  {
    file: path.join(root, '../ai-event-gateway-core/agent-control/src/main/java/com/opensocket/aievent/core/agent/setup/AgentSetupService.java'),
    patterns: ['buildStartCommandForReadiness', 'TOKEN_MISMATCH', 'GATEWAY_UNREACHABLE', 'AGENT_ID_MISMATCH', 'setHealthCheckCommand', 'setVerifyConnectionCommand'],
  },
  {
    file: path.join(root, 'components/agents/AgentOnboardingPanel.tsx'),
    patterns: ['commandVariants', 'Startup checklist', 'Connection troubleshooting', 'verifyConnectionCommand'],
  },
  {
    file: path.join(root, 'components/agents/AgentDetailProductView.tsx'),
    patterns: ['Start Command & Diagnostics', 'Connection troubleshooting', 'backendStartCommand', 'Gateway health check', 'Verify authorization'],
  },
  {
    file: path.join(root, '../scripts/acceptance/agent-setup-backend-contract-smoke.mjs'),
    patterns: ['Stage 9/10/11/12', 'authorizeConnection', 'CREDENTIAL_INVALID', 'sendMismatchedRuntimeSignal', 'dockerCommand', 'healthCheckCommand', 'troubleshooting'],
  },
  {
    file: path.join(root, '../docs/postman/OpenDispatch_First_Agent_Setup.postman_collection.json'),
    patterns: ['Invalid Token Authorization Failure', 'Mismatched Agent Connected Failure', 'Read Setup Readiness After Failure Cases'],
  },
  {
    file: path.join(root, 'tests/agent-setup-backend-contract.test.ts'),
    patterns: ['startCommandPayload', 'TOKEN_MISMATCH', 'healthCheckCommand', 'authorizeConnection', 'sendMismatchedRuntimeSignal'],
  },
];
let failed = false;
for (const check of checks) {
  const content = fs.existsSync(check.file) ? fs.readFileSync(check.file, 'utf8') : '';
  if (!content) {
    console.error(`[stage12] Missing file: ${path.relative(root, check.file)}`);
    failed = true;
    continue;
  }
  for (const pattern of check.patterns) {
    if (!content.includes(pattern)) {
      console.error(`[stage12] Missing pattern in ${path.relative(root, check.file)}: ${pattern}`);
      failed = true;
    }
  }
}
if (failed) process.exit(1);
console.log('[stage12] Runtime start command and connection troubleshooting verification passed.');

#!/usr/bin/env node
/**
 * Stage 9/10/11/12/13/14 first-Agent setup, runtime readiness, auth failure, and repair action smoke flow.
 *
 * Runs against an already-started Core stack. The script verifies the empty-database
 * onboarding path and the backend-owned readiness transition:
 *
 *   POST /admin/agents/setup
 *   GET  /admin/agents/{agentId}/setup-readiness          -> usually INCOMPLETE, RUNTIME_CONNECTED and/or ADMIN_MANAGED_CAPABILITIES_ACTIVE pending
 *   POST /internal/gateway-nodes/{gatewayNodeId}/agents/{agentId}/connected
 *   POST /internal/gateway-nodes/{gatewayNodeId}/agents/{agentId}/heartbeat
 *   GET  /admin/agents/{agentId}/setup-readiness          -> READY
 *   POST /internal/gateway-nodes/{gatewayNodeId}/agents/{agentId}/disconnected
 *   GET  /admin/agents/{agentId}/setup-readiness          -> INCOMPLETE again
 *
 * Use --skip-runtime-transition when the stack does not expose Core internal gateway endpoints.
 */

const coreOrigin = origin(process.env.CORE_URL || process.env.CORE_BASE_URL || 'http://127.0.0.1:18080');
const tenantId = process.env.AGENT_SETUP_TENANT_ID || 'tenant-a';
const agentId = process.env.AGENT_SETUP_AGENT_ID || `agent-setup-smoke-${Date.now()}`;
const agentName = process.env.AGENT_SETUP_AGENT_NAME || 'Stage 15 Setup Smoke Agent';
const ownerTeam = process.env.AGENT_SETUP_OWNER_TEAM || 'stage11-acceptance';
const gatewayNodeId = process.env.AGENT_SETUP_GATEWAY_NODE_ID || 'gateway-node-stage11';
const gatewayUrl = process.env.AGENT_SETUP_GATEWAY_URL || process.env.NETTY_URL || 'http://127.0.0.1:18081';
const credentialToken = process.env.AGENT_SETUP_TOKEN || `stage11-token-${Date.now()}`;
const operatorId = process.env.AGENT_SETUP_OPERATOR_ID || 'stage11-acceptance';
const dryRun = process.argv.includes('--dry-run') || flag('AGENT_SETUP_DRY_RUN');
const skipRuntimeTransition = process.argv.includes('--skip-runtime-transition') || flag('AGENT_SETUP_SKIP_RUNTIME_TRANSITION');
const verifyDisconnect = !process.argv.includes('--skip-disconnect-transition') && !flag('AGENT_SETUP_SKIP_DISCONNECT_TRANSITION');

function origin(value) {
  return String(value || '').replace(/\/+$/, '');
}

function flag(name) {
  const value = process.env[name];
  return value !== undefined && ['1', 'true', 'yes', 'on'].includes(value.toLowerCase());
}

function join(originValue, requestPath) {
  return `${originValue}${requestPath.startsWith('/') ? requestPath : `/${requestPath}`}`;
}

function isRecord(value) {
  return value !== null && typeof value === 'object' && !Array.isArray(value);
}

function isStandardEnvelope(value) {
  return isRecord(value)
    && typeof value.code === 'string'
    && typeof value.message === 'string'
    && Object.prototype.hasOwnProperty.call(value, 'data')
    && typeof value.timestamp === 'string';
}

function unwrapEnvelope(name, body) {
  if (!isStandardEnvelope(body)) {
    throw new Error(`${name}: expected standard API envelope, got ${JSON.stringify(body).slice(0, 400)}`);
  }
  if (body.code !== 'OK') {
    const error = new Error(`${name}: expected envelope code=OK but got code=${body.code} message=${body.message}`);
    error.code = body.code;
    error.envelope = body;
    throw error;
  }
  return body.data;
}

async function request(name, method, requestPath, body) {
  const url = join(coreOrigin, requestPath);
  const headers = { Accept: 'application/json' };
  if (body !== undefined) headers['Content-Type'] = 'application/json';
  const response = await fetch(url, {
    method,
    headers,
    body: body === undefined ? undefined : JSON.stringify(body),
    cache: 'no-store',
  });
  const text = await response.text();
  let parsed = null;
  if (text) {
    try {
      parsed = JSON.parse(text);
    } catch {
      parsed = { raw: text };
    }
  }
  if (response.status !== 200) {
    throw new Error(`${name}: expected HTTP 200 envelope but got HTTP ${response.status}; body=${text.slice(0, 500)}`);
  }
  return unwrapEnvelope(name, parsed);
}

function readinessCodes(data) {
  return Array.isArray(data?.checks) ? data.checks.map((check) => check?.code).filter(Boolean) : [];
}

function runtimeCheck(data) {
  return Array.isArray(data?.checks) ? data.checks.find((check) => check?.code === 'RUNTIME_CONNECTED') : null;
}

function runtimeCapabilitiesCheck(data) {
  return Array.isArray(data?.checks) ? data.checks.find((check) => check?.code === 'ADMIN_MANAGED_CAPABILITIES_ACTIVE') : null;
}

function validateSetupReadiness(data) {
  if (!isRecord(data)) throw new Error(`readiness response data is not an object: ${JSON.stringify(data)}`);
  if (data.agentId !== agentId) throw new Error(`expected readiness agentId=${agentId} but got ${data.agentId}`);
  if (typeof data.ready !== 'boolean') throw new Error('readiness response must include ready boolean');
  if (typeof data.status !== 'string') throw new Error('readiness response must include status string');
  if (!Array.isArray(data.blockingReasons)) throw new Error('readiness response must include blockingReasons array');
  if (!Array.isArray(data.checks) || data.checks.length === 0) throw new Error('readiness response must include checks');
  if (!isRecord(data.startCommand) || typeof data.startCommand.command !== 'string') throw new Error('readiness response must include redacted startCommand');
  if (!Array.isArray(data.profileCapabilities)) throw new Error('readiness response must include profileCapabilities array');
  if (!Array.isArray(data.runtimeReportedCapabilities)) throw new Error('readiness response must include runtimeReportedCapabilities array');
  if (!Array.isArray(data.missingRuntimeCapabilities)) throw new Error('readiness response must include missingRuntimeCapabilities array');
  if (data.startCommand.capabilityEnvironmentVariable !== 'ADMIN_UI_MANAGED_CAPABILITIES') throw new Error('startCommand must mark capabilities as ADMIN_UI_MANAGED_CAPABILITIES');
  if (!Array.isArray(data.troubleshooting) || data.troubleshooting.length === 0) throw new Error('readiness response must include troubleshooting');
  const codes = readinessCodes(data);
  for (const expected of ['AGENT_APPROVED', 'CREDENTIAL_ACTIVE', 'CAPABILITIES_ASSIGNED', 'RUNTIME_BINDING_ACTIVE', 'SERVICE_SCOPE_ACTIVE', 'DISPATCH_RULE_ACTIVE', 'RUNTIME_CONNECTED', 'ADMIN_MANAGED_CAPABILITIES_ACTIVE']) {
    if (!codes.includes(expected)) throw new Error(`setup-readiness checks missing ${expected}; got ${codes.join(',')}`);
  }
}

function expectRuntimePending(data, label) {
  validateSetupReadiness(data);
  const check = runtimeCheck(data);
  if (!check) throw new Error(`${label}: RUNTIME_CONNECTED check missing`);
  if (check.ready === true || data.ready === true) {
    throw new Error(`${label}: expected runtime readiness to be pending, got ready=${data.ready} status=${data.status}`);
  }
  if (!data.blockingReasons.includes('RUNTIME_CONNECTED')) {
    throw new Error(`${label}: expected blockingReasons to include RUNTIME_CONNECTED, got ${data.blockingReasons.join(',')}`);
  }
}

function expectRuntimeReady(data, label) {
  validateSetupReadiness(data);
  const check = runtimeCheck(data);
  const capabilityCheck = runtimeCapabilitiesCheck(data);
  if (!check) throw new Error(`${label}: RUNTIME_CONNECTED check missing`);
  if (!capabilityCheck) throw new Error(`${label}: ADMIN_MANAGED_CAPABILITIES_ACTIVE check missing`);
  if (data.ready !== true || data.status !== 'READY' || check.ready !== true || capabilityCheck.ready !== true) {
    throw new Error(`${label}: expected backend readiness READY after runtime heartbeat and capability report, got ready=${data.ready} status=${data.status} runtimeCheck=${JSON.stringify(check)} capabilityCheck=${JSON.stringify(capabilityCheck)}`);
  }
  if (data.blockingReasons.includes('RUNTIME_CONNECTED') || data.blockingReasons.includes('ADMIN_MANAGED_CAPABILITIES_ACTIVE')) {
    throw new Error(`${label}: runtime/capability checks remained blocking after runtime heartbeat`);
  }
}


function validateLatestAuthFailure(data, expectedReason) {
  if (!isRecord(data)) throw new Error(`latest auth failure response data is not an object: ${JSON.stringify(data)}`);
  if (data.agentId !== agentId) throw new Error(`expected auth failure agentId=${agentId} but got ${data.agentId}`);
  if (data.hasFailure !== true) throw new Error(`expected latest auth failure hasFailure=true, got ${JSON.stringify(data).slice(0, 400)}`);
  if (data.denyReason !== expectedReason) throw new Error(`expected denyReason=${expectedReason} but got ${data.denyReason}`);
  if (typeof data.securityEventId !== 'string' || data.securityEventId.length === 0) throw new Error('latest auth failure must include securityEventId');
  if (typeof data.securityEventLink !== 'string' || !data.securityEventLink.includes('/security-events')) throw new Error('latest auth failure must include securityEventLink');
  if (!Array.isArray(data.troubleshooting) || data.troubleshooting.length === 0) throw new Error('latest auth failure must include troubleshooting steps');
  const codes = data.troubleshooting.map((step) => step?.code).filter(Boolean);
  if (!codes.includes(expectedReason)) throw new Error(`latest auth failure troubleshooting missing ${expectedReason}; got ${codes.join(',')}`);
}

function validateConnectionRepairActions(data, expectedActionCode) {
  if (!isRecord(data)) throw new Error(`connection repair actions response data is not an object: ${JSON.stringify(data)}`);
  if (data.agentId !== agentId) throw new Error(`expected repair actions agentId=${agentId} but got ${data.agentId}`);
  if (!Array.isArray(data.actions) || data.actions.length === 0) throw new Error('connection repair actions response must include actions');
  const action = data.actions.find((candidate) => candidate?.actionCode === expectedActionCode);
  if (!action) throw new Error(`connection repair actions missing ${expectedActionCode}; got ${data.actions.map((candidate) => candidate?.actionCode).join(',')}`);
  if (action.actionType !== 'EXECUTE') throw new Error(`${expectedActionCode} must be executable`);
  if (typeof action.endpoint !== 'string' || !action.endpoint.includes('/connection-repair-actions/')) throw new Error(`${expectedActionCode} must include executable endpoint`);
  return action;
}

function validateRepairActionResult(data, expectedActionCode) {
  if (!isRecord(data)) throw new Error(`repair action result data is not an object: ${JSON.stringify(data)}`);
  if (data.agentId !== agentId) throw new Error(`expected repair result agentId=${agentId} but got ${data.agentId}`);
  if (data.actionCode !== expectedActionCode) throw new Error(`expected repair actionCode=${expectedActionCode} but got ${data.actionCode}`);
  if (data.status !== 'COMPLETED') throw new Error(`expected repair action status COMPLETED but got ${data.status}`);
  if (!isRecord(data.profile)) throw new Error('repair action result must include updated profile');
  if (!Array.isArray(data.nextActions)) throw new Error('repair action result must include nextActions');
}

function validateSetupResponse(data) {
  if (!isRecord(data)) throw new Error(`setup response data is not an object: ${JSON.stringify(data)}`);
  if (data.agentId !== agentId) throw new Error(`expected agentId=${agentId} but got ${data.agentId}`);
  if (!['READY', 'INCOMPLETE', 'PENDING_REVIEW'].includes(String(data.setupStatus))) {
    throw new Error(`unexpected setupStatus=${data.setupStatus}`);
  }
  if (!Array.isArray(data.readinessChecks) || data.readinessChecks.length === 0) {
    throw new Error('setup response must include readinessChecks');
  }
  const codes = data.readinessChecks.map((check) => check?.code).filter(Boolean);
  for (const expected of ['AGENT_APPROVED', 'CAPABILITIES_ASSIGNED', 'RUNTIME_BINDING_ACTIVE', 'SERVICE_SCOPE_ACTIVE', 'DISPATCH_RULE_ACTIVE', 'RUNTIME_CONNECTED', 'ADMIN_MANAGED_CAPABILITIES_ACTIVE']) {
    if (!codes.includes(expected)) throw new Error(`readinessChecks missing ${expected}; got ${codes.join(',')}`);
  }
  if (!isRecord(data.startCommand) || typeof data.startCommand.command !== 'string' || !data.startCommand.command.includes(agentId)) {
    throw new Error('setup response must include a startCommand.command containing the agentId');
  }
  for (const field of ['dockerCommand', 'localCommand', 'healthCheckCommand', 'verifyConnectionCommand']) {
    if (typeof data.startCommand[field] !== 'string' || data.startCommand[field].length === 0) {
      throw new Error(`setup response startCommand must include ${field}`);
    }
  }
  if (!Array.isArray(data.startCommand.expectedCapabilities) || data.startCommand.expectedCapabilities.length === 0) {
    throw new Error('setup response startCommand must include expectedCapabilities');
  }
  if (data.startCommand.capabilityEnvironmentVariable !== 'ADMIN_UI_MANAGED_CAPABILITIES') {
    throw new Error('setup response startCommand must mark capabilityEnvironmentVariable as ADMIN_UI_MANAGED_CAPABILITIES');
  }
  if (!Array.isArray(data.startCommand.startupSteps) || data.startCommand.startupSteps.length === 0) {
    throw new Error('setup response startCommand must include startupSteps');
  }
  if (!Array.isArray(data.startCommand.troubleshooting) || data.startCommand.troubleshooting.length === 0) {
    throw new Error('setup response startCommand must include troubleshooting steps');
  }
}

async function authorizeConnection(label, token, targetAgentId = agentId) {
  return request(label, 'POST', '/internal/agents/authorize-connection', {
    gatewayNodeId,
    agentId: targetAgentId,
    claimedAgentId: targetAgentId,
    agentSessionId: `session-${targetAgentId}`,
    credentialToken: token,
    transport: 'acceptance-smoke',
    capabilities: ['ISSUE_CREATE'],
    metadata: { smoke: 'stage15-capability-only-readiness-cleanup' },
  });
}

async function sendMismatchedRuntimeSignal() {
  const wrongAgentId = `${agentId}-wrong`;
  await request('mismatched agent connected must not satisfy target readiness', 'POST', `/internal/gateway-nodes/${encodeURIComponent(gatewayNodeId)}/agents/${encodeURIComponent(wrongAgentId)}/connected`, {
    agentId: wrongAgentId,
    ownerGatewayNodeId: gatewayNodeId,
    agentSessionId: `session-${wrongAgentId}`,
    status: 'IDLE',
    capabilities: ['ISSUE_CREATE'],
    maxConcurrentTasks: 1,
    currentTaskCount: 0,
    healthScore: 100,
    availableSlots: 1,
  });
}

async function sendRuntimeTransition() {
  await request('register gateway node for runtime transition', 'POST', '/internal/gateway-nodes/register', {
    gatewayNodeId,
    nodeName: 'Stage 15 Runtime Gateway',
    status: 'ONLINE',
    siteId: 'local',
    region: 'local',
    zone: 'local-a',
    metadata: { smoke: 'stage15-capability-only-readiness-cleanup' },
  });
  await request('gateway heartbeat for runtime transition', 'POST', `/internal/gateway-nodes/${encodeURIComponent(gatewayNodeId)}/heartbeat`, {
    status: 'ONLINE',
    leaseTtlSeconds: 120,
  });
  await request('agent connected runtime transition', 'POST', `/internal/gateway-nodes/${encodeURIComponent(gatewayNodeId)}/agents/${encodeURIComponent(agentId)}/connected`, {
    agentId,
    agentType: 'ISSUE_TRACKING',
    ownerGatewayNodeId: gatewayNodeId,
    agentSessionId: `session-${agentId}`,
    siteId: 'local',
    region: 'local',
    zone: 'local-a',
    status: 'IDLE',
    capabilities: ['ISSUE_CREATE', 'ISSUE_UPDATE', 'CALLBACK_HANDLE'],
    maxConcurrentTasks: 1,
    currentTaskCount: 0,
    healthScore: 100,
    availableSlots: 1,
    runtimeLoad: {
      activeTasks: 0,
      maxConcurrentTasks: 1,
      availableSlots: 1,
      capacityUtilization: 0,
      outboxPending: 0,
      outboxInFlight: 0,
      recoveryPendingAssignments: 0,
      draining: false,
    },
    pluginName: 'stage11-smoke-agent',
    pluginVersion: 'local',
    capabilityRevision: 'stage11-smoke',
    capabilityProfile: { supportedCapabilities: ['ISSUE_CREATE', 'ISSUE_UPDATE', 'CALLBACK_HANDLE'], runtimeCapabilities: ['ISSUE_CREATE', 'ISSUE_UPDATE', 'CALLBACK_HANDLE'], supportedTaskTypes: ['ISSUE_CREATE', 'ISSUE_UPDATE'] },
  });
  await request('agent heartbeat runtime transition', 'POST', `/internal/gateway-nodes/${encodeURIComponent(gatewayNodeId)}/agents/${encodeURIComponent(agentId)}/heartbeat`, {
    status: 'IDLE',
    currentTaskCount: 0,
    healthScore: 100,
    agentSessionId: `session-${agentId}`,
    runtimeLoad: {
      activeTasks: 0,
      maxConcurrentTasks: 1,
      availableSlots: 1,
      capacityUtilization: 0,
      draining: false,
    },
    capabilityRevision: 'stage11-smoke-heartbeat',
    plugin: { name: 'stage11-smoke-agent', version: 'local' },
    capabilityProfile: { supportedCapabilities: ['ISSUE_CREATE', 'ISSUE_UPDATE', 'CALLBACK_HANDLE'], runtimeCapabilities: ['ISSUE_CREATE', 'ISSUE_UPDATE', 'CALLBACK_HANDLE'], supportedTaskTypes: ['ISSUE_CREATE', 'ISSUE_UPDATE'] },
  });
}

async function sendRuntimeDisconnect() {
  await request('agent disconnected runtime transition', 'POST', `/internal/gateway-nodes/${encodeURIComponent(gatewayNodeId)}/agents/${encodeURIComponent(agentId)}/disconnected`, {
    agentSessionId: `session-${agentId}`,
    reason: 'Stage 15 smoke verifies readiness returns to incomplete when runtime disconnects.',
  });
}

async function main() {
  const body = {
    tenantId,
    agentId,
    agentName,
    ownerTeam,
    description: 'Created by Stage 15 first-Agent setup and runtime readiness transition smoke flow.',
    purpose: 'ISSUE_TRACKING',
    runtimeType: 'Docker',
    gatewayUrl,
    credentialToken,
    autoApprove: true,
    createDefaultCapabilities: true,
    createRuntimeBinding: true,
    createSupplyProfile: true,
    createDefaultDispatchRule: true,
    capacityLimit: 1,
    operatorId,
    metadata: {
      smoke: 'agent-setup-runtime-readiness-transition',
      generatedBy: 'scripts/acceptance/agent-setup-backend-contract-smoke.mjs',
    },
  };

  console.log(`[agent-setup-smoke] Core=${coreOrigin}`);
  console.log(`[agent-setup-smoke] Agent=${agentId}`);
  console.log(`[agent-setup-smoke] Gateway node=${gatewayNodeId}`);
  if (dryRun) {
    console.log('[agent-setup-smoke] Dry run request body:');
    console.log(JSON.stringify(body, null, 2));
    console.log(`[agent-setup-smoke] Runtime transition endpoints will ${skipRuntimeTransition ? 'not ' : ''}be called.`);
    return;
  }

  const setup = await request('first-Agent setup backend contract', 'POST', '/admin/agents/setup', body);
  validateSetupResponse(setup);
  console.log(`[agent-setup-smoke] setupStatus=${setup.setupStatus}`);
  console.log(`[agent-setup-smoke] readiness=${setup.readinessChecks.map((check) => `${check.code}:${check.status}`).join(', ')}`);
  console.log('[agent-setup-smoke] start command:');
  console.log(setup.startCommand.command);

  const beforeHeartbeat = await request('backend-owned setup readiness before runtime heartbeat', 'GET', `/admin/agents/${encodeURIComponent(agentId)}/setup-readiness`);
  expectRuntimePending(beforeHeartbeat, 'before heartbeat');
  console.log(`[agent-setup-smoke] before heartbeat: status=${beforeHeartbeat.status} ready=${beforeHeartbeat.ready} blocking=${beforeHeartbeat.blockingReasons.join(',')}`);

  const invalidAuth = await authorizeConnection('invalid token should be denied', `${credentialToken}-wrong`);
  if (invalidAuth.decision !== 'DENY' || invalidAuth.reason !== 'CREDENTIAL_INVALID') {
    throw new Error(`expected invalid token authorization DENY/CREDENTIAL_INVALID, got ${JSON.stringify(invalidAuth)}`);
  }
  const latestAuthFailure = await request('latest auth failure after invalid token', 'GET', `/admin/agents/${encodeURIComponent(agentId)}/latest-auth-failure`);
  validateLatestAuthFailure(latestAuthFailure, 'CREDENTIAL_INVALID');
  console.log(`[agent-setup-smoke] latest auth failure: reason=${latestAuthFailure.denyReason} event=${latestAuthFailure.securityEventId}`);

  const repairActions = await request('connection repair actions after invalid token', 'GET', `/admin/agents/${encodeURIComponent(agentId)}/connection-repair-actions`);
  validateConnectionRepairActions(repairActions, 'ROTATE_CREDENTIAL');

  const rotatedToken = `${credentialToken}-rotated`;
  const repairResult = await request('rotate credential repair action', 'POST', `/admin/agents/${encodeURIComponent(agentId)}/connection-repair-actions/ROTATE_CREDENTIAL`, {
    operatorId,
    reason: 'Stage 14 smoke test rotates the credential after invalid token failure',
    credentialToken: rotatedToken,
    revokeExisting: true,
    enableAfterRepair: true,
  });
  validateRepairActionResult(repairResult, 'ROTATE_CREDENTIAL');

  const rotatedAuthorization = await authorizeConnection('authorization succeeds with rotated token', rotatedToken);
  if (rotatedAuthorization.allowed !== true) {
    throw new Error(`expected rotated token authorization to be allowed, got ${JSON.stringify(rotatedAuthorization).slice(0, 400)}`);
  }

  const afterInvalidAuth = await request('readiness after invalid token remains pending', 'GET', `/admin/agents/${encodeURIComponent(agentId)}/setup-readiness`);
  expectRuntimePending(afterInvalidAuth, 'after invalid token authorization');

  if (!skipRuntimeTransition) {
    await sendMismatchedRuntimeSignal();
    const afterMismatchedAgent = await request('readiness after mismatched runtime remains pending', 'GET', `/admin/agents/${encodeURIComponent(agentId)}/setup-readiness`);
    expectRuntimePending(afterMismatchedAgent, 'after mismatched runtime');

    await sendRuntimeTransition();
    const afterHeartbeat = await request('backend-owned setup readiness after runtime heartbeat', 'GET', `/admin/agents/${encodeURIComponent(agentId)}/setup-readiness`);
    expectRuntimeReady(afterHeartbeat, 'after heartbeat');
    console.log(`[agent-setup-smoke] after heartbeat: status=${afterHeartbeat.status} ready=${afterHeartbeat.ready}`);

    if (verifyDisconnect) {
      await sendRuntimeDisconnect();
      const afterDisconnect = await request('backend-owned setup readiness after runtime disconnect', 'GET', `/admin/agents/${encodeURIComponent(agentId)}/setup-readiness`);
      expectRuntimePending(afterDisconnect, 'after disconnect');
      console.log(`[agent-setup-smoke] after disconnect: status=${afterDisconnect.status} ready=${afterDisconnect.ready} blocking=${afterDisconnect.blockingReasons.join(',')}`);

      await sendRuntimeTransition();
      const afterReconnect = await request('backend-owned setup readiness after runtime reconnect', 'GET', `/admin/agents/${encodeURIComponent(agentId)}/setup-readiness`);
      expectRuntimeReady(afterReconnect, 'after reconnect');
      console.log(`[agent-setup-smoke] after reconnect: status=${afterReconnect.status} ready=${afterReconnect.ready}`);
    }
  } else {
    console.log('[agent-setup-smoke] Runtime transition skipped by flag. Initial RUNTIME_CONNECTED pending state was verified.');
  }

  const profile = await request('read created Agent profile', 'GET', `/admin/agents/${encodeURIComponent(agentId)}`);
  if (!isRecord(profile) || profile.agentId !== agentId) {
    throw new Error(`created Agent profile cannot be read back: ${JSON.stringify(profile).slice(0, 400)}`);
  }
  console.log(`[agent-setup-smoke] profile approvalStatus=${profile.approvalStatus} enabled=${profile.enabled}`);
  console.log('OK Stage 14 first-Agent setup runtime readiness, auth failure, and connection repair smoke flow completed.');
}

main().catch((error) => {
  console.error(`[agent-setup-smoke] FAILED: ${error instanceof Error ? error.stack || error.message : String(error)}`);
  process.exit(1);
});

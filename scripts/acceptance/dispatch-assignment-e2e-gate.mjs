#!/usr/bin/env node
/**
 * Stage 19 dispatch assignment E2E gate.
 *
 * Verifies the full operator path that repeatedly blocked agent-cluster-node-001-002:
 *
 *   1. Ensure the target Agent setup/readiness contract exists.
 *   2. Ensure the target Agent runtime is connected and reports MES capabilities.
 *   3. Verify dispatch-readiness resolves raw INCIDENT_ANALYSIS to effective MES capabilities.
 *   4. Create a real incident task through /api/events/intake with raw INCIDENT_ANALYSIS + MES context.
 *   5. Assert assignment selects the target Agent and a dispatch request is created/eligible.
 *   6. Optionally execute approved dispatch and/or submit a RESULT callback when the stack supports it.
 *
 * This script is intentionally runtime-facing. Use --dry-run for request previews.
 */

const coreOrigin = origin(process.env.CORE_URL || process.env.CORE_BASE_URL || 'http://127.0.0.1:18080');
const tenantId = process.env.STAGE19_TENANT_ID || process.env.AGENT_SETUP_TENANT_ID || 'tenant-a';
const agentId = process.env.STAGE19_AGENT_ID || process.env.AGENT_SETUP_AGENT_ID || 'agent-cluster-node-001-002';
const agentName = process.env.STAGE19_AGENT_NAME || 'Stage 19 MES Assignment Agent';
const ownerTeam = process.env.STAGE19_OWNER_TEAM || 'stage19-e2e';
const gatewayNodeId = process.env.STAGE19_GATEWAY_NODE_ID || process.env.AGENT_SETUP_GATEWAY_NODE_ID || 'gateway-node-001';
const gatewayUrl = process.env.STAGE19_GATEWAY_URL || process.env.NETTY_URL || 'http://127.0.0.1:18081';
const credentialToken = process.env.STAGE19_AGENT_TOKEN || process.env.AGENT_SETUP_TOKEN || `stage19-token-${Date.now()}`;
const operatorId = process.env.STAGE19_OPERATOR_ID || 'stage19-e2e';
const rawTaskRequirement = normalizeList(process.env.STAGE19_RAW_TASK_REQUIREMENTS || 'INCIDENT_ANALYSIS');
const effectiveCapabilities = normalizeList(process.env.STAGE19_EFFECTIVE_CAPABILITIES || 'MES_ALARM_TRIAGE,MES_LOT_TRACE,MES_YIELD_ANOMALY_ANALYSIS');
const defaultTaskTypes = normalizeList(process.env.STAGE19_DEFAULT_TASK_TYPES || 'INCIDENT_RESPONSE,MES_ALARM_TRIAGE,MES_LOT_TRACE,MES_YIELD_ANOMALY_ANALYSIS');
const dispatchRequiredCapabilities = normalizeList(process.env.STAGE19_EVENT_REQUIRED_CAPABILITIES || ['INCIDENT_ANALYSIS', 'DOMAIN:MES', 'PROVIDER:MES', 'OPERATION:ANALYZE'].join(','));
const setupAgent = !hasArg('--skip-setup') && !flag('STAGE19_SKIP_SETUP');
const skipRuntimeTransition = hasArg('--skip-runtime') || flag('STAGE19_SKIP_RUNTIME');
const skipDispatchExecute = hasArg('--skip-execute') || flag('STAGE19_SKIP_DISPATCH_EXECUTE');
const skipCallback = hasArg('--skip-callback') || flag('STAGE19_SKIP_CALLBACK');
const dryRun = hasArg('--dry-run') || flag('STAGE19_DRY_RUN');

function hasArg(name) {
  return process.argv.includes(name);
}

function origin(value) {
  return String(value || '').replace(/\/+$/, '');
}

function flag(name) {
  const value = process.env[name];
  return value !== undefined && ['1', 'true', 'yes', 'on'].includes(String(value).toLowerCase());
}

function join(originValue, requestPath) {
  return `${originValue}${requestPath.startsWith('/') ? requestPath : `/${requestPath}`}`;
}

function isRecord(value) {
  return value !== null && typeof value === 'object' && !Array.isArray(value);
}

function normalize(value) {
  return String(value || '').trim().replace(/[.\-\s]+/g, '_').toUpperCase();
}

function normalizeList(value) {
  const raw = Array.isArray(value) ? value : String(value || '').split(',');
  return [...new Set(raw.map((item) => normalize(item)).filter(Boolean))];
}

function isStandardEnvelope(value) {
  return isRecord(value)
    && typeof value.code === 'string'
    && Object.prototype.hasOwnProperty.call(value, 'data')
    && typeof value.timestamp === 'string';
}

function unwrap(name, body, { allowError = false } = {}) {
  if (isStandardEnvelope(body)) {
    if (body.code !== 'OK' && !allowError) {
      const error = new Error(`${name}: expected envelope code=OK but got ${body.code} ${body.message}`);
      error.envelope = body;
      throw error;
    }
    return body.data;
  }
  return body;
}

async function request(name, method, requestPath, body, options = {}) {
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
    try { parsed = JSON.parse(text); } catch { parsed = { raw: text }; }
  }
  if (response.status !== 200 && !options.allowHttpError) {
    throw new Error(`${name}: expected HTTP 200 but got HTTP ${response.status}; body=${text.slice(0, 600)}`);
  }
  return unwrap(name, parsed, options);
}

function readinessCodes(readiness) {
  return Array.isArray(readiness?.checks) ? readiness.checks.map((check) => check?.code).filter(Boolean) : [];
}

function requireArrayContains(label, values, expectedValues) {
  const actual = normalizeList(values || []);
  const missing = expectedValues.filter((expected) => !actual.includes(normalize(expected)));
  if (missing.length > 0) {
    throw new Error(`${label}: missing ${missing.join(', ')}; actual=${actual.join(', ')}`);
  }
}

function validateReady(readiness) {
  if (!isRecord(readiness)) throw new Error(`setup-readiness must return object, got ${JSON.stringify(readiness)}`);
  if (readiness.agentId !== agentId) throw new Error(`expected readiness agentId=${agentId}, got ${readiness.agentId}`);
  if (readiness.ready !== true || readiness.status !== 'READY') {
    throw new Error(`expected ${agentId} setup-readiness READY, got ready=${readiness.ready} status=${readiness.status} blocking=${(readiness.blockingReasons || []).join(',')}`);
  }
  for (const expected of ['RUNTIME_BINDING_ACTIVE', 'SERVICE_SCOPE_ACTIVE', 'DISPATCH_RULE_ACTIVE', 'RUNTIME_CONNECTED', 'ADMIN_MANAGED_CAPABILITIES_ACTIVE']) {
    if (!readinessCodes(readiness).includes(expected)) throw new Error(`readiness checks missing ${expected}`);
  }
  requireArrayContains('runtimeReportedCapabilities', readiness.runtimeReportedCapabilities, effectiveCapabilities);
  requireArrayContains('profileCapabilities', readiness.profileCapabilities, effectiveCapabilities);
}

async function ensureSetup() {
  if (!setupAgent) return;
  const setupBody = {
    tenantId,
    agentId,
    agentName,
    ownerTeam,
    description: 'Created or refreshed by Stage 19 dispatch assignment E2E gate.',
    purpose: 'MES_OPERATIONS_ANALYZER',
    runtimeType: 'Docker',
    gatewayUrl,
    credentialToken,
    autoApprove: true,
    createDefaultCapabilities: true,
    createRuntimeBinding: true,
    createSupplyProfile: true,
    createDefaultDispatchRule: true,
    defaultCapabilities: effectiveCapabilities,
    defaultTaskTypes,
    capacityLimit: 2,
    operatorId,
    metadata: { stage: '19', gate: 'dispatch-assignment-e2e' },
  };
  await request('setup or refresh target Agent', 'POST', '/admin/agents/setup', setupBody);
}

async function sendRuntimeSignal() {
  if (skipRuntimeTransition) return;
  await request('register gateway node', 'POST', '/internal/gateway-nodes/register', {
    gatewayNodeId,
    nodeName: 'Stage 19 E2E Gateway',
    status: 'ONLINE',
    siteId: 'MES_SITE_A',
    region: 'TW',
    zone: 'FAB_A',
    metadata: { stage: '19' },
  });
  await request('gateway heartbeat', 'POST', `/internal/gateway-nodes/${encodeURIComponent(gatewayNodeId)}/heartbeat`, {
    status: 'ONLINE',
    leaseTtlSeconds: 120,
  });
  const runtimePayload = {
    agentId,
    agentType: 'MES_OPERATIONS_ANALYZER',
    ownerGatewayNodeId: gatewayNodeId,
    agentSessionId: `session-${agentId}`,
    siteId: 'MES_SITE_A',
    region: 'TW',
    zone: 'FAB_A',
    status: 'IDLE',
    capabilities: effectiveCapabilities,
    maxConcurrentTasks: 2,
    currentTaskCount: 0,
    healthScore: 100,
    availableSlots: 2,
    runtimeLoad: {
      activeTasks: 0,
      maxConcurrentTasks: 2,
      availableSlots: 2,
      capacityUtilization: 0,
      outboxPending: 0,
      outboxInFlight: 0,
      recoveryPendingAssignments: 0,
      draining: false,
    },
    pluginName: 'stage19-mes-agent',
    pluginVersion: 'local',
    capabilityRevision: 'stage19-mes',
    capabilityProfile: {
      supportedCapabilities: effectiveCapabilities,
      runtimeCapabilities: effectiveCapabilities,
      supportedTaskTypes: defaultTaskTypes,
    },
  };
  await request('target Agent connected with MES capabilities', 'POST', `/internal/gateway-nodes/${encodeURIComponent(gatewayNodeId)}/agents/${encodeURIComponent(agentId)}/connected`, runtimePayload);
  await request('target Agent heartbeat with MES capabilities', 'POST', `/internal/gateway-nodes/${encodeURIComponent(gatewayNodeId)}/agents/${encodeURIComponent(agentId)}/heartbeat`, {
    ...runtimePayload,
    capabilityRevision: 'stage19-mes-heartbeat',
    plugin: { name: 'stage19-mes-agent', version: 'local' },
  });
}

async function validateReadinessContract() {
  const readiness = await request('target Agent setup-readiness', 'GET', `/admin/agents/${encodeURIComponent(agentId)}/setup-readiness`);
  validateReady(readiness);
  return readiness;
}

async function validateDispatchReadinessEvaluation() {
  const body = {
    agentId,
    taskId: `stage19-preview-${Date.now()}`,
    taskType: 'INCIDENT_RESPONSE',
    domain: 'MES',
    provider: 'MES',
    operation: 'ANALYZE',
    requiredCapabilities: dispatchRequiredCapabilities,
    siteCode: 'MES_SITE_A',
    plantId: 'FAB_A',
    objectType: 'MES_LOT',
    eventType: 'MES_LOT_TRACE',
  };
  const result = await request('dispatch-readiness effective capability preview', 'POST', '/admin/dispatch-readiness/evaluate', body);
  if (!isRecord(result)) throw new Error('dispatch-readiness evaluate must return object');
  requireArrayContains('rawTaskRequirements', result.rawTaskRequirements, rawTaskRequirement);
  requireArrayContains('effectiveDispatchCapabilities', result.effectiveDispatchCapabilities, effectiveCapabilities);
  const legacyAliases = normalizeList(result.legacyTaskAliases || []);
  for (const raw of rawTaskRequirement) {
    if (!legacyAliases.includes(raw)) throw new Error(`expected legacyTaskAliases to include ${raw}; got ${legacyAliases.join(', ')}`);
  }
  if (result.ready !== true) {
    throw new Error(`dispatch-readiness evaluation must be ready before assignment; summary=${result.summary}; missing=${(result.missingRequirements || []).join(',')}`);
  }
  return result;
}

async function createMesIncidentTask() {
  const objectId = `LOT-${Date.now()}`;
  const response = await request('event intake creates MES incident task and assignment', 'POST', '/api/events/intake', {
    tenantId,
    sourceSystem: 'MES',
    siteId: 'MES_SITE_A',
    plantId: 'FAB_A',
    objectType: 'MES_LOT',
    objectId,
    eventType: 'MES_LOT_TRACE',
    errorCode: 'MES_LOT_TRACE_REQUIRED',
    severity: 'CRITICAL',
    message: `Stage 19 E2E dispatch assignment test for ${agentId}`,
    attributes: {
      requiredCapabilities: dispatchRequiredCapabilities,
      routingPolicy: 'LOCAL_FIRST',
      domain: 'MES',
      provider: 'MES',
      operation: 'ANALYZE',
      stage: '19',
      targetAgentId: agentId,
    },
  });
  if (!response.taskCreated || !response.taskId) throw new Error(`event intake did not create task: ${JSON.stringify(response).slice(0, 800)}`);
  if (!response.assignmentCreated || !response.assignmentId) throw new Error(`event intake did not create assignment: ${JSON.stringify(response).slice(0, 800)}`);
  if (response.selectedAgentId !== agentId) throw new Error(`expected selectedAgentId=${agentId}, got ${response.selectedAgentId}; response=${JSON.stringify(response).slice(0, 800)}`);
  if (!response.dispatchRequestCreated || !response.dispatchRequestId) throw new Error(`dispatch request was not created: ${JSON.stringify(response).slice(0, 800)}`);
  if (response.dispatchEligibilityStatus !== 'ELIGIBLE') throw new Error(`expected dispatchEligibilityStatus=ELIGIBLE, got ${response.dispatchEligibilityStatus}; reason=${response.dispatchReason}`);
  return response;
}

async function validateAssignmentAndRouting(intake) {
  const assignment = await request('read task assignment', 'GET', `/api/assignments/${encodeURIComponent(intake.assignmentId)}`);
  if (assignment.agentId !== agentId) throw new Error(`assignment target mismatch: expected ${agentId}, got ${assignment.agentId}`);
  const task = await request('read created task', 'GET', `/api/tasks/${encodeURIComponent(intake.taskId)}`);
  requireArrayContains('task.requiredCapabilities', task.requiredCapabilities, rawTaskRequirement);
  const routing = await request('read routing decision', 'GET', `/admin/tasks/${encodeURIComponent(intake.taskId)}/routing-decisions`);
  const records = Array.isArray(routing) ? routing : Array.isArray(routing?.items) ? routing.items : [];
  const selected = records.find((record) => record?.decisionId === intake.routingDecisionId) || records[0];
  if (!selected) throw new Error(`routing decision not found for task ${intake.taskId}`);
  if (selected.selectedAgentId !== agentId) throw new Error(`routing selectedAgentId mismatch: expected ${agentId}, got ${selected.selectedAgentId}`);
  const selectedCandidate = Array.isArray(selected.candidates) ? selected.candidates.find((candidate) => candidate?.agentId === agentId) : null;
  if (selectedCandidate?.scoreBreakdown) {
    requireArrayContains('routing.scoreBreakdown.matchedCapabilities', selectedCandidate.scoreBreakdown.matchedCapabilities || [], effectiveCapabilities);
    const missing = normalizeList(selectedCandidate.scoreBreakdown.missingCapabilities || []);
    for (const raw of rawTaskRequirement) {
      if (missing.includes(raw)) throw new Error(`legacy raw requirement ${raw} must not remain missing in routing scoreBreakdown`);
    }
  }
  return { assignment, task, routing: selected };
}

async function validateDispatchRequest(intake) {
  let dispatch = await request('read dispatch request', 'GET', `/api/dispatch-requests/${encodeURIComponent(intake.dispatchRequestId)}`);
  if (dispatch.agentId !== agentId) throw new Error(`dispatch target mismatch: expected ${agentId}, got ${dispatch.agentId}`);
  if (dispatch.eligibilityStatus !== 'ELIGIBLE') throw new Error(`expected dispatch eligibility ELIGIBLE, got ${dispatch.eligibilityStatus}; reason=${dispatch.reason}`);
  if (dispatch.status === 'PENDING_REVIEW') {
    dispatch = await request('approve dispatch request', 'POST', `/api/dispatch-requests/${encodeURIComponent(dispatch.dispatchRequestId)}/approve`, {
      reason: 'Stage 19 E2E gate approves dispatch request for execution.',
    });
  }
  if (!skipDispatchExecute) {
    const execution = await request('execute dispatch request', 'POST', `/api/dispatch-requests/${encodeURIComponent(dispatch.dispatchRequestId)}/execute`);
    if (execution && execution.executed === false && !String(execution.message || '').includes('Netty')) {
      throw new Error(`dispatch execute did not execute and did not report a runtime delivery reason: ${JSON.stringify(execution).slice(0, 800)}`);
    }
  }
  return dispatch;
}

async function maybeSubmitResultCallback(intake, dispatch) {
  if (skipCallback) return null;
  if (!dispatch?.dispatchToken) {
    console.log('[stage19-dispatch-e2e] callback skipped: dispatchToken unavailable in dispatch request response.');
    return null;
  }
  const result = await request('submit successful task result callback', 'POST', `/internal/control-plane/tasks/${encodeURIComponent(intake.taskId)}/result`, {
    callbackId: `stage19-result-${intake.taskId}`,
    dispatchRequestId: dispatch.dispatchRequestId,
    assignmentId: dispatch.assignmentId,
    taskId: intake.taskId,
    agentId,
    ownerGatewayNodeId: dispatch.ownerGatewayNodeId || gatewayNodeId,
    agentSessionId: dispatch.agentSessionId || `session-${agentId}`,
    attemptNo: Math.max(1, Number(dispatch.attemptCount || 0) + 1),
    dispatchToken: dispatch.dispatchToken,
    resultStatus: 'SUCCESS',
    message: 'Stage 19 E2E gate completed successfully.',
    payload: {
      issueLink: `https://redmine.local/issues/stage19-${Date.now()}`,
      effectiveCapabilities,
      rawTaskRequirement,
    },
  }, { allowHttpError: true, allowError: true });
  return result;
}

async function main() {
  console.log(`[stage19-dispatch-e2e] Core=${coreOrigin}`);
  console.log(`[stage19-dispatch-e2e] Target Agent=${agentId}`);
  console.log(`[stage19-dispatch-e2e] Raw requirement=${rawTaskRequirement.join(',')}`);
  console.log(`[stage19-dispatch-e2e] Effective capabilities=${effectiveCapabilities.join(',')}`);
  if (dryRun) {
    console.log('[stage19-dispatch-e2e] Dry run only. No requests were sent.');
    return;
  }
  await ensureSetup();
  await sendRuntimeSignal();
  const readiness = await validateReadinessContract();
  console.log(`[stage19-dispatch-e2e] readiness=${readiness.status} runtime=${(readiness.runtimeReportedCapabilities || []).join(',')}`);
  const preview = await validateDispatchReadinessEvaluation();
  console.log(`[stage19-dispatch-e2e] readiness preview effective=${(preview.effectiveDispatchCapabilities || []).join(',')}`);
  const intake = await createMesIncidentTask();
  console.log(`[stage19-dispatch-e2e] task=${intake.taskId} assignment=${intake.assignmentId} dispatch=${intake.dispatchRequestId}`);
  await validateAssignmentAndRouting(intake);
  const dispatch = await validateDispatchRequest(intake);
  console.log(`[stage19-dispatch-e2e] dispatch status=${dispatch.status} eligibility=${dispatch.eligibilityStatus}`);
  const callback = await maybeSubmitResultCallback(intake, dispatch);
  if (callback) {
    console.log(`[stage19-dispatch-e2e] callback result=${callback.accepted ?? callback.status ?? 'submitted'}`);
  }
  console.log('OK Stage 19 dispatch assignment E2E gate completed.');
}

main().catch((error) => {
  console.error(`[stage19-dispatch-e2e] FAILED: ${error instanceof Error ? error.stack || error.message : String(error)}`);
  process.exit(1);
});

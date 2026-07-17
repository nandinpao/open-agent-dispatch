#!/usr/bin/env node
/*
 * Stage 7 Fix2: bootstrap local/SIT simulated cluster agents through the
 * authoritative Agent identity/credential/runtime path only.
 *
 * Admin mutations use the Core Human Admin Session + CSRF by default.
 * Internal Agent authorization preflight uses the Gateway machine token.
 * Legacy Assignment Profile, Qualification, Service Scope and capability
 * presets are intentionally ignored and never written by this script.
 */

const crypto = require('node:crypto');
const fs = require('node:fs');
const path = require('node:path');

const PROJECT_ROOT = path.resolve(__dirname, '..', '..');

function readEnvFile(filePath) {
  if (!filePath || !fs.existsSync(filePath)) return {};
  const rawValues = {};
  for (const rawLine of fs.readFileSync(filePath, 'utf8').split(/\r?\n/)) {
    const line = rawLine.trim();
    if (!line || line.startsWith('#')) continue;
    const match = line.match(/^(?:export\s+)?([A-Za-z_][A-Za-z0-9_]*)=(.*)$/);
    if (!match) continue;
    let value = match[2].trim();
    if ((value.startsWith('"') && value.endsWith('"')) || (value.startsWith("'") && value.endsWith("'"))) {
      value = value.slice(1, -1);
    }
    rawValues[match[1]] = value;
  }

  const resolved = {};
  const resolving = new Set();
  const resolveValue = (name) => {
    if (Object.prototype.hasOwnProperty.call(resolved, name)) return resolved[name];
    if (resolving.has(name)) throw new Error(`Circular environment reference in ${filePath}: ${name}`);
    resolving.add(name);
    const raw = String(rawValues[name] ?? '');
    const value = raw.replace(/\$\{([A-Za-z_][A-Za-z0-9_]*)\}/g, (_match, referencedName) => {
      if (process.env[referencedName] !== undefined) return String(process.env[referencedName]);
      return resolveValue(referencedName);
    });
    resolving.delete(name);
    resolved[name] = value;
    return value;
  };

  for (const name of Object.keys(rawValues)) resolveValue(name);
  return resolved;
}

function resolveEnvFile() {
  const explicit = process.env.CORE_BOOTSTRAP_ENV_FILE || process.env.ENV_FILE;
  if (explicit) return path.isAbsolute(explicit) ? explicit : path.resolve(PROJECT_ROOT, explicit);
  const local = path.resolve(PROJECT_ROOT, 'deploy/env/.env.local');
  if (fs.existsSync(local)) return local;
  return path.resolve(PROJECT_ROOT, 'deploy/env/.env.local.example');
}

const configuredEnvFile = resolveEnvFile();
const fileEnvironment = readEnvFile(configuredEnvFile);

function env(name, fallback = '') {
  const value = process.env[name] ?? fileEnvironment[name];
  return value === undefined || String(value).trim() === '' ? fallback : String(value).trim();
}

function intEnv(name, fallback) {
  const parsed = Number.parseInt(env(name, String(fallback)), 10);
  return Number.isFinite(parsed) ? parsed : fallback;
}

function boolEnv(name, fallback) {
  const raw = env(name, fallback ? 'true' : 'false').toLowerCase();
  return raw === '1' || raw === 'true' || raw === 'yes' || raw === 'y' || raw === 'on';
}

function nodeId(index) {
  return `gateway-node-${String(index).padStart(3, '0')}`;
}

function agentId(nodeIndex, agentIndex) {
  return `agent-cluster-node-${String(nodeIndex).padStart(3, '0')}-${String(agentIndex).padStart(3, '0')}`;
}

function normalizeCode(value) {
  return String(value || '').trim().replace(/[\s.-]+/g, '_').toUpperCase();
}

const coreBaseUrl = env('CORE_BASE_URL', env('CORE_API_BASE_URL', 'http://127.0.0.1:18080')).replace(/\/+$/, '');
const adminAuthMode = env('CORE_BOOTSTRAP_ADMIN_AUTH_MODE', 'SESSION').toUpperCase();
const adminUsername = env('CORE_BOOTSTRAP_ADMIN_USERNAME', env('CORE_ADMIN_BOOTSTRAP_USERNAME', 'admin'));
const adminPassword = env('CORE_BOOTSTRAP_ADMIN_PASSWORD', env('CORE_ADMIN_BOOTSTRAP_PASSWORD', ''));
const tokenHeaderName = env('CORE_INTERNAL_TOKEN_HEADER', 'X-Cluster-Token');
const operatorToken = env('CORE_OPERATOR_TOKEN', env('CORE_OPERATOR_INTERNAL_TOKEN', env('CLUSTER_INTERNAL_TOKEN', '')));
const gatewayToken = env('CORE_GATEWAY_INTERNAL_TOKEN', env('CLUSTER_INTERNAL_TOKEN', ''));
const operatorId = env('CORE_BOOTSTRAP_OPERATOR_ID', 'local-cluster-agent-bootstrap');
const requestedCredentialToken = env('AGENT_ONBOARDING_TOKEN', 'local-agent-onboarding-token-change-me');
const envFileCredentialToken = String(fileEnvironment.AGENT_ONBOARDING_TOKEN || '').trim();
const deprecatedLocalCredentialToken = requestedCredentialToken === 'local-dev-agent-token-change-me';
const credentialToken = deprecatedLocalCredentialToken && envFileCredentialToken
  ? envFileCredentialToken
  : requestedCredentialToken;
const nodeCount = intEnv('GATEWAY_CLUSTER_NODE_COUNT', 3);
const agentsPerNode = intEnv('AGENTS_PER_NODE', 1);
const tenantId = env('AGENT_TENANT_ID', 'tenant-a');
const agentType = env('AGENT_TYPE', 'OPENCLAW');
const capacityLimit = intEnv('AGENT_MAX_CONCURRENT_TASKS', 3);
const dryRun = boolEnv('CORE_BOOTSTRAP_DRY_RUN', false);
const resetExisting = boolEnv('CORE_BOOTSTRAP_RESET_EXISTING', true);
const resetBlocked = boolEnv('CORE_BOOTSTRAP_RESET_BLOCKED', false);
const failFast = boolEnv('CORE_BOOTSTRAP_FAIL_FAST', true);
const forceIssueCredential = boolEnv('CORE_BOOTSTRAP_FORCE_ISSUE_CREDENTIAL', true);
const verifyAuthorization = boolEnv('CORE_BOOTSTRAP_VERIFY_AUTHORIZATION', true);

const runId = `bootstrap-${Date.now()}`;
const cookieJar = new Map();
let csrfHeaderName = null;
let csrfToken = null;

function splitSetCookieHeader(value) {
  if (!value) return [];
  return value.split(/,(?=\s*[^;,=\s]+=[^;,]*)/g).map((item) => item.trim()).filter(Boolean);
}

function rememberResponseCookies(headers) {
  const values = typeof headers.getSetCookie === 'function'
    ? headers.getSetCookie()
    : splitSetCookieHeader(headers.get('set-cookie'));
  for (const value of values) {
    const first = String(value).split(';', 1)[0];
    const separator = first.indexOf('=');
    if (separator <= 0) continue;
    const name = first.slice(0, separator).trim();
    const cookieValue = first.slice(separator + 1).trim();
    if (/max-age=0|expires=thu, 01 jan 1970/i.test(String(value))) cookieJar.delete(name);
    else cookieJar.set(name, cookieValue);
  }
}

function cookieHeader() {
  return [...cookieJar.entries()].map(([name, value]) => `${name}=${value}`).join('; ');
}

function isUnsafeMethod(method) {
  return !['GET', 'HEAD', 'OPTIONS', 'TRACE'].includes(String(method || 'GET').toUpperCase());
}

function adminHeaders(method, body, diagnosticHeaders = {}) {
  const out = { Accept: 'application/json', ...diagnosticHeaders };
  if (body !== undefined) out['Content-Type'] = 'application/json';
  if (adminAuthMode === 'MACHINE_TOKEN') {
    if (operatorToken) out[tokenHeaderName] = operatorToken;
    return out;
  }
  const cookies = cookieHeader();
  if (cookies) out.Cookie = cookies;
  if (isUnsafeMethod(method) && csrfHeaderName && csrfToken) out[csrfHeaderName] = csrfToken;
  return out;
}

function internalHeaders(body, diagnosticHeaders = {}) {
  const out = { Accept: 'application/json', ...diagnosticHeaders };
  if (body !== undefined) out['Content-Type'] = 'application/json';
  if (gatewayToken) out[tokenHeaderName] = gatewayToken;
  return out;
}

function isStandardEnvelope(value) {
  // Stage 8-F0d: Core error envelopes in some older controllers can return
  // HTTP 200 with {code,message,data:null} and no timestamp. Treat any code/data
  // envelope as semantic success/failure so bootstrap never ignores BAD_REQUEST.
  return value !== null
    && typeof value === 'object'
    && typeof value.code === 'string'
    && (Object.prototype.hasOwnProperty.call(value, 'data') || typeof value.message === 'string');
}

async function request(method, requestPath, body, options = {}) {
  const kind = options.kind || (requestPath.startsWith('/internal/') ? 'internal' : 'admin');
  const requestCorrelationId = options.correlationId || `${runId}-${method.toLowerCase()}-${requestPath.replace(/[^a-zA-Z0-9]+/g, '-').replace(/^-|-$/g, '').slice(0, 64)}`;
  const requestId = crypto.randomUUID();
  const diagnosticHeaders = {
    'X-Request-Id': requestId,
    'X-Correlation-Id': requestCorrelationId,
    'X-Bootstrap-Run-Id': runId,
  };
  const response = await fetch(`${coreBaseUrl}${requestPath}`, {
    method,
    headers: kind === 'internal' ? internalHeaders(body, diagnosticHeaders) : adminHeaders(method, body, diagnosticHeaders),
    body: body === undefined ? undefined : JSON.stringify(body),
    cache: 'no-store',
  });
  rememberResponseCookies(response.headers);
  const responseRequestId = response.headers.get('x-request-id') || requestId;
  const responseCorrelationId = response.headers.get('x-correlation-id') || requestCorrelationId;
  const text = await response.text();
  let payload = null;
  if (text) {
    try { payload = JSON.parse(text); } catch { payload = { raw: text }; }
  }
  if (!response.ok) {
    let hint = '';
    if (response.status === 401 && kind === 'admin') {
      hint = adminAuthMode === 'MACHINE_TOKEN'
        ? ' Check CORE_BOOTSTRAP_ADMIN_AUTH_MODE=MACHINE_TOKEN and CORE_OPERATOR_TOKEN.'
        : ` Check CORE_ADMIN_BOOTSTRAP_USERNAME/CORE_ADMIN_BOOTSTRAP_PASSWORD from ${configuredEnvFile}.`;
    } else if ((response.status === 401 || response.status === 403) && kind === 'internal') {
      hint = ' Check CORE_GATEWAY_INTERNAL_TOKEN/CLUSTER_INTERNAL_TOKEN; /internal/agents/authorize-connection requires the GATEWAY role.';
    }
    const diagnosticHint = ` Core log lookup: correlationId=${responseCorrelationId} requestId=${responseRequestId} bootstrapRunId=${runId}.`;
    const error = new Error(`${method} ${requestPath} failed: HTTP ${response.status} ${text}${diagnosticHint}${hint}`);
    error.code = 'TRANSPORT_ERROR';
    error.payload = payload;
    error.responseText = text;
    error.requestId = responseRequestId;
    error.correlationId = responseCorrelationId;
    error.bootstrapRunId = runId;
    throw error;
  }
  if (isStandardEnvelope(payload)) {
    if (payload.code !== 'OK') {
      const diagnosticHint = ` Core log lookup: correlationId=${responseCorrelationId} requestId=${responseRequestId} bootstrapRunId=${runId}.`;
      const error = new Error(`${method} ${requestPath} failed: code=${payload.code} ${payload.message}${diagnosticHint}`);
      error.code = payload.code;
      error.payload = payload;
      error.responseText = text;
      error.requestId = responseRequestId;
      error.correlationId = responseCorrelationId;
      error.bootstrapRunId = runId;
      throw error;
    }
    return { payload: payload.data };
  }
  return { payload };
}

async function refreshCsrf() {
  const response = await request('GET', '/api/auth/csrf', undefined, { kind: 'admin' });
  const payload = response.payload || {};
  if (!payload.headerName || !payload.token) {
    throw new Error(`Core Admin CSRF response is incomplete: ${JSON.stringify(payload)}`);
  }
  csrfHeaderName = String(payload.headerName);
  csrfToken = String(payload.token);
}

async function ensureAdminAuthentication() {
  if (dryRun) return;
  if (adminAuthMode === 'MACHINE_TOKEN') {
    if (!operatorToken) throw new Error('CORE_OPERATOR_TOKEN is required when CORE_BOOTSTRAP_ADMIN_AUTH_MODE=MACHINE_TOKEN.');
    console.log('[bootstrap] admin authentication=machine-token');
    return;
  }
  if (adminAuthMode !== 'SESSION') {
    throw new Error(`Unsupported CORE_BOOTSTRAP_ADMIN_AUTH_MODE=${adminAuthMode}; expected SESSION or MACHINE_TOKEN.`);
  }
  if (!adminUsername || !adminPassword) {
    throw new Error(`Core Admin session credentials are missing. Configure CORE_ADMIN_BOOTSTRAP_USERNAME and CORE_ADMIN_BOOTSTRAP_PASSWORD in ${configuredEnvFile}.`);
  }
  await refreshCsrf();
  await request('POST', '/api/auth/login', { username: adminUsername, password: adminPassword }, { kind: 'admin' });
  await refreshCsrf();
  const current = await request('GET', '/api/auth/me', undefined, { kind: 'admin' });
  if (String(current.payload?.username || '') !== adminUsername) {
    throw new Error(`Core Admin session verification failed: ${JSON.stringify(current.payload)}`);
  }
  console.log(`[bootstrap] admin authentication=session username=${adminUsername} envFile=${configuredEnvFile}`);
}

function looksLikeProfileNotFound(error) {
  if (!error) return false;
  if (error.code === 'NOT_FOUND' || error.code === 'CORE_AGENT_NOT_FOUND') return true;
  const message = String(error.payload?.message || error.payload?.error || error.responseText || error.message || '');
  return message.includes('Agent profile not found');
}

async function getProfileOrMissing(agent) {
  try {
    return await request('GET', `/admin/agents/${encodeURIComponent(agent)}`);
  } catch (error) {
    if (looksLikeProfileNotFound(error)) return { payload: null };
    throw error;
  }
}

function approvalBody(agent, reason) {
  return {
    agentId: agent,
    approvedBy: operatorId,
    operatorId,
    tenantId,
    agentName: agent,
    agentType,
    ownerTeam: 'local-simulator',
    description: 'Local Netty TCP cluster simulator Agent bootstrap.',
    comment: reason,
    reason,
    enabled: true,
    riskStatus: 'NORMAL',
    credentialType: 'TOKEN',
    credentialToken,
    revokeExisting: true,
  };
}

function credentialHashPrefix() {
  return crypto.createHash('sha256').update(credentialToken).digest('hex').slice(0, 12);
}

function credentialIssueBody(agent, reason) {
  return { agentId: agent, operatorId, reason, credentialType: 'TOKEN', credentialToken, revokeExisting: true };
}

async function issueCredential(agent, reason) {
  if (!forceIssueCredential) return null;
  return (await request('POST', `/admin/agents/${encodeURIComponent(agent)}/credentials/issue`, credentialIssueBody(agent, reason))).payload;
}

async function verifyAgentAuthorization(agent, node) {
  if (!verifyAuthorization) return null;
  const response = await request('POST', '/internal/agents/authorize-connection', {
    agentId: agent,
    agentType,
    connectionType: 'TCP',
    gatewayNodeId: node,
    agentSessionId: `${runId}-${agent}`,
    connectionId: `${runId}-${agent}`,
    remoteAddress: 'local-bootstrap-preflight',
    claimedCapabilities: [],
    metadata: {
      source: 'core-bootstrap-cluster-agents.js',
      gatewayNodeId: node,
      localSimulator: true,
      bootstrapRunId: runId,
      credentialHashPrefix: credentialHashPrefix(),
      capabilityAuthority: 'DISPATCH_FLOW_OPTIONAL_CAPABILITY',
    },
    credentialToken,
  }, { kind: 'internal' });
  const payload = response.payload || {};
  const allowed = payload.allowed === true || payload.decision === 'ALLOW';
  if (!allowed) {
    const reason = payload.reason || payload.detail || payload.message || payload.decision || 'UNKNOWN';
    throw new Error(`Core authorization preflight denied for ${agent}: ${reason} ${payload.message || ''}`.trim());
  }
  return payload;
}

function runtimeCodeForAgent(agent, node) {
  return normalizeCode(`${agent}-${node || 'gateway-node-unknown'}-runtime`);
}

function runtimeResourceIdForAgent(agent, node) {
  return `runtime-${runtimeCodeForAgent(agent, node).toLowerCase().replace(/_/g, '-')}`;
}

function runtimeResourceBody(agent, node) {
  const runtimeCode = runtimeCodeForAgent(agent, node);
  return {
    tenantId,
    runtimeId: runtimeResourceIdForAgent(agent, node),
    runtimeCode,
    runtimeName: `${agent} runtime on ${node}`,
    runtimeType: 'SIMULATED_AGENT_RUNTIME',
    connectorType: 'GATEWAY_RUNTIME',
    executionHost: node,
    environment: 'local',
    trustStatus: 'TRUSTED',
    status: 'ACTIVE',
    capacityLimit,
    metadata: {
      source: 'core-bootstrap-cluster-agents.js',
      localSimulator: true,
      bootstrapRunId: runId,
      gatewayNodeId: node,
      dispatchAuthority: 'ACTIVE_RUNTIME_BINDING',
    },
  };
}

function runtimeBindingBody(agent, node, reason) {
  const runtimeCode = runtimeCodeForAgent(agent, node);
  return {
    tenantId,
    agentId: agent,
    runtimeId: runtimeResourceIdForAgent(agent, node),
    runtimeCode,
    bindingStatus: 'ACTIVE',
    verifiedBy: operatorId,
    approvedBy: operatorId,
    capacityLimit,
    dataScope: 'STANDARD',
    riskLimit: 'MIDDLE',
    metadata: {
      source: 'core-bootstrap-cluster-agents.js',
      localSimulator: true,
      bootstrapRunId: runId,
      gatewayNodeId: node,
      reason,
      dispatchAuthority: 'ACTIVE_RUNTIME_BINDING',
    },
  };
}

function bindingStatus(binding) {
  return String(binding?.bindingStatus || binding?.status || '').toUpperCase();
}

async function ensureRuntimeBinding(agent, node, reason) {
  if (dryRun) return { status: 'DRY_RUN_ACTIVE', detail: `runtimeBinding=DRY_RUN_ACTIVE node=${node}` };
  const runtimeId = runtimeResourceIdForAgent(agent, node);
  await request('PUT', `/admin/runtime-resources/${encodeURIComponent(runtimeId)}?tenantId=${encodeURIComponent(tenantId)}`, runtimeResourceBody(agent, node));
  let bindings = [];
  try {
    const listed = await request('GET', `/admin/agents/${encodeURIComponent(agent)}/runtime-bindings`);
    bindings = Array.isArray(listed.payload) ? listed.payload : [];
  } catch {
    bindings = [];
  }
  let binding = bindings.find((item) => item?.runtimeId === runtimeId || String(item?.runtimeCode || '').toUpperCase() === runtimeCodeForAgent(agent, node));
  if (!binding) {
    binding = (await request('POST', `/admin/agents/${encodeURIComponent(agent)}/runtime-bindings`, runtimeBindingBody(agent, node, reason))).payload;
  }
  if (bindingStatus(binding) !== 'ACTIVE') {
    binding = (await request('POST', `/admin/agents/${encodeURIComponent(agent)}/runtime-bindings/${encodeURIComponent(binding.bindingId)}/activate`, runtimeBindingBody(agent, node, reason))).payload || binding;
  }
  return { status: bindingStatus(binding) || 'ACTIVE', binding, detail: `runtimeBinding=${bindingStatus(binding) || 'ACTIVE'} runtime=${runtimeId}` };
}

function enrollmentBody(agent, node) {
  return {
    claimedAgentId: agent,
    tenantId,
    agentName: agent,
    agentType,
    submittedMetadata: {
      source: 'core-bootstrap-cluster-agents.js',
      gatewayNodeId: node,
      localSimulator: true,
      bootstrapRunId: runId,
    },
    evidence: { generatedBy: 'ai-event-gateway-netty/scripts/core-bootstrap-cluster-agents.js' },
  };
}

function profileStatus(profile) {
  return String(profile?.approvalStatus || profile?.status || '').toUpperCase();
}

function profileRiskStatus(profile) {
  return String(profile?.riskStatus || 'NORMAL').toUpperCase();
}

function profileCredentialStatus(profile) {
  return String(profile?.credential?.status || profile?.credentialStatus || '').toUpperCase();
}

function profileSyncNeeded(profile) {
  return profileStatus(profile) !== 'APPROVED'
    || profile.enabled === false
    || profileRiskStatus(profile) !== 'NORMAL'
    || String(profile?.tenantId || '') !== tenantId
    || String(profile?.agentType || '') !== agentType;
}

function profileUpdateBody(agent, reason) {
  return {
    tenantId,
    agentName: agent,
    agentType,
    ownerTeam: 'local-simulator',
    description: 'Local Netty TCP cluster simulator Agent bootstrap.',
    approvalStatus: 'APPROVED',
    enabled: true,
    riskStatus: 'NORMAL',
    operatorId,
    reason,
  };
}

function isGovernanceBlockedProfile(profile) {
  if (!profile) return false;
  const status = profileStatus(profile);
  const risk = profileRiskStatus(profile);
  const credentialStatus = profileCredentialStatus(profile);
  return profile.enabled === false
    || ['REJECTED', 'REVOKED', 'SUSPENDED', 'DISABLED'].includes(status)
    || ['REVOKED', 'REJECTED', 'DISABLED'].includes(risk)
    || credentialStatus === 'REVOKED';
}

function shouldApproveExistingProfile(profile) {
  if (!profile) return false;
  if (isGovernanceBlockedProfile(profile)) return resetBlocked;
  return resetExisting && profileSyncNeeded(profile);
}

async function verifyTenantScopedAgentProfile(agent) {
  if (dryRun) return { status: 'DRY_RUN_PROFILE_READY' };
  const profileResponse = await getProfileOrMissing(agent);
  const profile = profileResponse.payload;
  if (!profile) {
    throw new Error(`Agent profile bootstrap verification failed: ${agent} was not created in tenant ${tenantId}.`);
  }
  const status = profileStatus(profile);
  const risk = profileRiskStatus(profile);
  if (String(profile.tenantId || '') !== tenantId) {
    throw new Error(`Agent profile bootstrap verification failed: ${agent} tenant=${profile.tenantId || 'UNKNOWN'} expected=${tenantId}.`);
  }
  if (status !== 'APPROVED' || profile.enabled === false || risk !== 'NORMAL') {
    throw new Error(`Agent profile bootstrap verification failed: ${agent} status=${status || 'UNKNOWN'} enabled=${profile.enabled !== false} risk=${risk || 'UNKNOWN'}.`);
  }
  return { status, risk, enabled: profile.enabled !== false };
}

async function bootstrapAgent(agent, node) {
  const profileResponse = await getProfileOrMissing(agent);
  if (profileResponse.payload) {
    const profile = profileResponse.payload;
    const status = profileStatus(profile);
    const risk = profileRiskStatus(profile);
    const credentialStatus = profileCredentialStatus(profile);
    const blocked = isGovernanceBlockedProfile(profile);
    if (blocked && !resetBlocked) {
      return { agent, status: 'SKIPPED_BLOCKED', detail: `${status || 'UNKNOWN'} enabled=${profile.enabled} risk=${risk} credential=${credentialStatus || 'UNKNOWN'}` };
    }

    const approveExisting = shouldApproveExistingProfile(profile);
    if (!approveExisting && !forceIssueCredential && !verifyAuthorization) {
      return { agent, status: 'UNCHANGED', detail: `${status || 'EXISTS'} enabled=${profile.enabled !== false} risk=${risk} credential=${credentialStatus || 'UNKNOWN'}` };
    }
    if (dryRun) {
      return { agent, status: approveExisting ? 'DRY_RUN_APPROVE_EXISTING' : 'DRY_RUN_SYNC_CREDENTIAL', detail: `${status || 'EXISTS'} node=${node}` };
    }

    const reason = blocked
      ? `Local simulator bootstrap explicitly restored blocked ${agent} because CORE_BOOTSTRAP_RESET_BLOCKED=true.`
      : `Local simulator bootstrap synchronized identity, credential and runtime for ${agent}.`;
    let syncedProfile = profile;
    let approvalStatus = status || 'APPROVED';
    if (approveExisting && (blocked || status !== 'APPROVED' || risk !== 'NORMAL' || profile.enabled === false)) {
      syncedProfile = (await request('POST', `/admin/agents/${encodeURIComponent(agent)}/approve`, approvalBody(agent, reason))).payload || syncedProfile;
      approvalStatus = syncedProfile?.approvalStatus || approvalStatus;
    }
    if (approveExisting || profileSyncNeeded(syncedProfile)) {
      syncedProfile = (await request('PUT', `/admin/agents/${encodeURIComponent(agent)}`, profileUpdateBody(agent, reason))).payload || syncedProfile;
      approvalStatus = syncedProfile?.approvalStatus || approvalStatus;
    }
    const credential = await issueCredential(agent, `Local simulator bootstrap credential synchronization for ${agent}; token=${credentialHashPrefix()}.`);
    const authorization = await verifyAgentAuthorization(agent, node);
    const runtimeBinding = await ensureRuntimeBinding(agent, node, `Local simulator bootstrap activated runtime binding for ${agent} on ${node}.`);
    const verifiedProfile = await verifyTenantScopedAgentProfile(agent);
    return {
      agent,
      status: approveExisting ? 'SYNCED_PROFILE' : 'SYNCED_EXISTING',
      detail: `${approvalStatus} profile=${verifiedProfile.status || 'VERIFIED'} credential=${credential?.credential?.credentialStatus || credential?.credentialStatus || credentialStatus || 'SYNCED'} auth=${authorization?.decision || 'SKIPPED'} ${runtimeBinding.detail} legacyBootstrap=disabled`,
    };
  }

  if (dryRun) return { agent, status: 'DRY_RUN_CREATE_AND_APPROVE', detail: node };
  const enrollment = await request('POST', '/admin/agent-enrollments', enrollmentBody(agent, node));
  const enrollmentId = enrollment.payload?.enrollmentId;
  if (!enrollmentId) throw new Error(`Core did not return enrollmentId for ${agent}: ${JSON.stringify(enrollment.payload)}`);
  const reason = `Local simulator bootstrap approved ${agent} for ${node}.`;
  const approved = await request('POST', `/admin/agent-enrollments/${encodeURIComponent(enrollmentId)}/approve`, approvalBody(agent, reason));
  const credential = await issueCredential(agent, `Local simulator bootstrap credential synchronization for ${agent}; token=${credentialHashPrefix()}.`);
  const authorization = await verifyAgentAuthorization(agent, node);
  const runtimeBinding = await ensureRuntimeBinding(agent, node, `Local simulator bootstrap activated runtime binding for ${agent} on ${node}.`);
  const verifiedProfile = await verifyTenantScopedAgentProfile(agent);
  return {
    agent,
    status: 'CREATED_AND_APPROVED',
    detail: `${approved.payload?.approvalStatus || verifiedProfile.status || 'APPROVED'} profile=${verifiedProfile.status || 'VERIFIED'} credential=${credential?.credentialStatus || credential?.credential?.credentialStatus || 'SYNCED'} auth=${authorization?.decision || 'SKIPPED'} ${runtimeBinding.detail} legacyBootstrap=disabled`,
  };
}

function legacyEnvironmentNames() {
  return [
    'CORE_BOOTSTRAP_ASSIGNMENT_PROFILE',
    'CORE_BOOTSTRAP_AUTO_APPROVE_QUALIFICATION',
    'CORE_BOOTSTRAP_QUALIFICATION_EVIDENCE',
    'CORE_BOOTSTRAP_RUN_CERTIFICATION',
    'CORE_BOOTSTRAP_LEGACY_CAPABILITIES_ENABLED',
    'AGENT_CLUSTER_CAPABILITY_PRESETS',
    'AGENT_CAPABILITY_PRESET',
    'AGENT_SCOPE_SYSTEM_CODE',
    'AGENT_SCOPE_TASK_TYPE',
    'AGENT_SCOPE_SITE_CODE',
  ].filter((name) => process.env[name] !== undefined && String(process.env[name]).trim() !== '');
}

async function main() {
  if (!credentialToken) throw new Error('AGENT_ONBOARDING_TOKEN is required because Core credential issuance requires a token.');
  const ignoredLegacy = legacyEnvironmentNames();
  console.log(`[bootstrap] core=${coreBaseUrl} tenant=${tenantId} nodes=${nodeCount} agentsPerNode=${agentsPerNode} adminAuth=${adminAuthMode} resetExisting=${resetExisting} resetBlocked=${resetBlocked} forceIssueCredential=${forceIssueCredential} verifyAuthorization=${verifyAuthorization} dryRun=${dryRun} legacyBootstrap=disabled`);
  if (ignoredLegacy.length > 0) {
    console.warn(`[bootstrap] ignored legacy variables: ${ignoredLegacy.join(', ')}. Assignment Profile, Qualification, Service Scope and capability presets are not bootstrap authority.`);
  }
  if (deprecatedLocalCredentialToken && credentialToken !== requestedCredentialToken) {
    console.warn(`[bootstrap] deprecated AGENT_ONBOARDING_TOKEN=${requestedCredentialToken} was ignored; using the canonical token from ${configuredEnvFile}.`);
  }
  if (adminAuthMode === 'SESSION' && process.env.CORE_OPERATOR_TOKEN) {
    console.warn('[bootstrap] CORE_OPERATOR_TOKEN is ignored in SESSION mode; /admin/** uses the authenticated Admin session and CSRF.');
  }
  if (resetBlocked) {
    console.warn('[bootstrap] WARNING: CORE_BOOTSTRAP_RESET_BLOCKED=true restores rejected/disabled/revoked simulator Agents. Do not use this mode for governance-blocking tests.');
  }
  await ensureAdminAuthentication();

  const results = [];
  for (let nodeIndex = 1; nodeIndex <= nodeCount; nodeIndex += 1) {
    const node = nodeId(nodeIndex);
    for (let agentIndex = 1; agentIndex <= agentsPerNode; agentIndex += 1) {
      const agent = agentId(nodeIndex, agentIndex);
      try {
        const result = await bootstrapAgent(agent, node);
        results.push(result);
        console.log(`[bootstrap] ${result.status.padEnd(22)} ${agent} ${result.detail || ''}`);
      } catch (error) {
        const result = { agent, status: 'FAILED', detail: error.message };
        results.push(result);
        console.error(`[bootstrap] FAILED                ${agent} ${error.message}`);
        if (failFast) throw error;
      }
    }
  }
  const failed = results.filter((item) => item.status === 'FAILED').length;
  console.log(`[bootstrap] completed total=${results.length} failed=${failed}`);
  if (failed > 0) process.exitCode = 1;
}

main().catch((error) => {
  console.error(`[bootstrap] fatal: ${error.message}`);
  process.exit(1);
});

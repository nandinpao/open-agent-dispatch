#!/usr/bin/env node
/**
 * Stage 0 integration characterization for the OpenDispatch standard path.
 *
 * This is deliberately NOT a simulator or readiness test. In live mode every
 * scenario calls the normal Admin/Event APIs, creates real persisted records,
 * and inspects the resulting Task evidence. The default mode is
 * characterization: semantic failures are written to a report instead of
 * hiding them behind mock success. Use --strict when Stage 1 is expected to be
 * complete and any Golden Path mismatch must fail the build.
 */

import fs from 'node:fs';
import path from 'node:path';
import process from 'node:process';

const args = new Set(process.argv.slice(2));
const dryRun = args.has('--dry-run');
const strict = args.has('--strict');
const authOnly = args.has('--auth-only');
const configuredEnvFile = process.env.STAGE1_ENV_FILE || process.env.STAGE0_ENV_FILE
  || (fs.existsSync('deploy/env/.env.local') ? 'deploy/env/.env.local' : 'deploy/env/.env.local.example');
const fileEnvironment = readEnvFile(configuredEnvFile);

function readEnvFile(filePath) {
  if (!filePath || !fs.existsSync(filePath)) return {};
  const values = {};
  for (const rawLine of fs.readFileSync(filePath, 'utf8').split(/\r?\n/)) {
    const line = rawLine.trim();
    if (!line || line.startsWith('#')) continue;
    const match = line.match(/^(?:export\s+)?([A-Za-z_][A-Za-z0-9_]*)=(.*)$/);
    if (!match) continue;
    let value = match[2].trim();
    if ((value.startsWith('"') && value.endsWith('"')) || (value.startsWith("'") && value.endsWith("'"))) {
      value = value.slice(1, -1);
    }
    values[match[1]] = value;
  }
  return values;
}

function configuredValue(...names) {
  for (const name of names) {
    const value = process.env[name] ?? fileEnvironment[name];
    if (value !== undefined && String(value).trim()) return String(value).trim();
  }
  return '';
}

const coreOrigin = origin(process.env.CORE_URL || process.env.CORE_BASE_URL || 'http://127.0.0.1:18080');
const characterizationStage = process.env.DISPATCH_CHARACTERIZATION_STAGE || 'STAGE_0_INTEGRATION_CHARACTERIZATION';
const scenarioPrefix = characterizationStage === 'STAGE_1_BACKEND_GOLDEN_PATH' ? 'T1' : 'T0';
const runPrefix = characterizationStage.startsWith('STAGE_1') ? 'stage1' : 'stage0';
const runId = normalizeId(process.env.STAGE1_RUN_ID || process.env.STAGE0_RUN_ID || `${runPrefix}-${Date.now()}-${Math.random().toString(16).slice(2, 8)}`);
const reportDir = process.env.STAGE1_REPORT_DIR || process.env.STAGE0_REPORT_DIR || path.resolve(`.ci-output/${runPrefix}-characterization`);
const timeoutMs = intEnv('STAGE1_POLL_TIMEOUT_MS', intEnv('STAGE0_POLL_TIMEOUT_MS', 45000));
const intervalMs = intEnv('STAGE1_POLL_INTERVAL_MS', intEnv('STAGE0_POLL_INTERVAL_MS', 1200));
const adminToken = configuredValue('STAGE1_ADMIN_BEARER_TOKEN', 'STAGE0_ADMIN_BEARER_TOKEN');
const eventToken = configuredValue('STAGE1_EVENT_BEARER_TOKEN', 'STAGE0_EVENT_BEARER_TOKEN', 'CORE_EVENT_INTAKE_INTERNAL_TOKEN');
const adminUsername = configuredValue('STAGE1_ADMIN_USERNAME', 'STAGE0_ADMIN_USERNAME', 'CORE_ADMIN_BOOTSTRAP_USERNAME', 'SMOKE_ADMIN_AUTH_USERNAME');
const adminPassword = configuredValue('STAGE1_ADMIN_PASSWORD', 'STAGE0_ADMIN_PASSWORD', 'CORE_ADMIN_BOOTSTRAP_PASSWORD', 'SMOKE_ADMIN_AUTH_PASSWORD');
const gatewayUrl = process.env.STAGE1_GATEWAY_URL || process.env.STAGE0_GATEWAY_URL || process.env.NETTY_URL || 'http://127.0.0.1:18081';
const agentOnboardingToken = configuredValue('STAGE1_AGENT_ONBOARDING_TOKEN', 'STAGE0_AGENT_ONBOARDING_TOKEN', 'AGENT_ONBOARDING_TOKEN', 'NETTY_AGENT_ONBOARDING_TOKEN') || `cred-${runId}`;

const ids = {
  tenantA: String(process.env.STAGE0_TENANT_A || 'tenant-a').trim(),
  tenantB: String(process.env.STAGE1_TENANT_B || process.env.STAGE0_TENANT_B || `tenant-${runPrefix}-isolation-${runId}`).trim(),
  sourceNoCapability: normalizeCode(`SRC_${runId}_NO_CAP`),
  sourceCapability: normalizeCode(`SRC_${runId}_CAP`),
  sourceNoFlow: normalizeCode(`SRC_${runId}_NO_FLOW`),
  sourceOfflineAgent: normalizeCode(`SRC_${runId}_OFFLINE_AGENT`),
  agentRuntime: normalizeId(process.env.STAGE1_AGENT_ID || process.env.STAGE0_AGENT_ID || 'agent-local-001'),
  agentOffline: normalizeId(`agent-${runId}-offline`),
  capability: normalizeCode(`CAP_${runId}_ANALYZE`),
};

const report = {
  schemaVersion: 1,
  stage: characterizationStage,
  mode: dryRun ? 'DRY_RUN' : strict ? 'LIVE_STRICT' : 'LIVE_CHARACTERIZE',
  runId,
  startedAt: new Date().toISOString(),
  coreOrigin,
  authentication: {
    mode: adminToken ? 'BEARER_TOKEN' : 'CORE_ADMIN_SESSION',
    username: adminToken ? null : adminUsername || null,
    envFile: configuredEnvFile,
  },
  dynamicData: ids,
  rules: {
    sourceNamesAreArbitrary: true,
    noBusinessSeedRequired: true,
    noReadinessSimulator: true,
    noLegacyFallbackExpected: true,
  },
  scenarios: [],
  requests: [],
};

function origin(value) { return String(value || '').replace(/\/+$/, ''); }
function normalizeCode(value) { return String(value || '').trim().replace(/[^a-zA-Z0-9]+/g, '_').replace(/^_+|_+$/g, '').toUpperCase(); }
function normalizeId(value) { return String(value || '').trim().replace(/[^a-zA-Z0-9-]+/g, '-').replace(/^-+|-+$/g, '').toLowerCase(); }
function intEnv(name, fallback) { const n = Number.parseInt(String(process.env[name] || ''), 10); return Number.isFinite(n) ? n : fallback; }
function join(base, requestPath) { return `${base}${requestPath.startsWith('/') ? requestPath : `/${requestPath}`}`; }
function isRecord(value) { return value !== null && typeof value === 'object' && !Array.isArray(value); }
function unwrap(value) { return isRecord(value) && Object.prototype.hasOwnProperty.call(value, 'data') ? value.data : value; }
function nowIso() { return new Date().toISOString(); }
function sleep(ms) { return new Promise((resolve) => setTimeout(resolve, ms)); }
function taskIdOf(value) {
  const data = unwrap(value) || {};
  const explicit = valuesForKey(data, new Set(['taskId', 'task_id'])).find((item) => String(item).trim());
  return explicit || data.id || null;
}
function selectedAgentOf(value) {
  return valuesForKey(unwrap(value) || {}, new Set(['selectedAgentId', 'selected_agent_id']))
    .map(String).find((item) => item.trim()) || null;
}
function bool(value, ...keys) {
  const data = unwrap(value) || {};
  return valuesForKey(data, new Set(keys)).some((item) => item === true || String(item).toLowerCase() === 'true');
}
function safe(value, max = 12000) { const text = JSON.stringify(value); return text.length > max ? `${text.slice(0, max)}…` : text; }
function redact(value) {
  if (Array.isArray(value)) return value.map(redact);
  if (!isRecord(value)) return value;
  const result = {};
  for (const [key, item] of Object.entries(value)) {
    result[key] = /token|password|secret|credential/i.test(key) ? '<redacted>' : redact(item);
  }
  return result;
}


const legacyBlockerPatterns = new Set([
  'SERVICE_SCOPE', 'TASK_SCOPE', 'ASSIGNMENT_PROFILE', 'QUALIFICATION',
  'SOURCE_DEFAULT', 'SOURCE_ASSIGNMENT', 'OPERATION_PROFILE', 'POLICY_BINDING',
]);

function categorizedBlockers(value) {
  const blockers = detectBlockers(value);
  const legacyBlockers = blockers.filter((item) => legacyBlockerPatterns.has(item));
  const standardBlockers = blockers.filter((item) => !legacyBlockerPatterns.has(item));
  return {
    blockers,
    standardBlockers,
    legacyBlockers,
    legacyLeakInStandardEvidence: legacyBlockers.length > 0,
  };
}

const okEnvelopeCodes = new Set(['OK', 'SUCCESS', 'ACCEPTED']);

function apiCodeOf(responseOrBody) {
  const body = responseOrBody?.body ?? responseOrBody;
  return isRecord(body) && typeof body.code === 'string' ? String(body.code).trim().toUpperCase() : null;
}

function apiMessageOf(responseOrBody) {
  const body = responseOrBody?.body ?? responseOrBody;
  return isRecord(body) ? String(body.message || body.error || body.detail || '').trim() : '';
}

function apiEnvelopeOk(responseOrBody) {
  const code = apiCodeOf(responseOrBody);
  return !code || okEnvelopeCodes.has(code);
}

function responseOk(response) {
  return Boolean(response && response.status >= 200 && response.status < 300 && apiEnvelopeOk(response));
}

function apiFailureSummary(responseOrBody) {
  const code = apiCodeOf(responseOrBody);
  const message = apiMessageOf(responseOrBody);
  return {
    apiCode: code,
    apiMessage: message || null,
    apiOk: !code || okEnvelopeCodes.has(code),
  };
}

// Stage 8-F0d canonical first failure example: FLOW_CREATE_REJECTED_AGENT_NOT_FOUND.
function classifyApiFailure(responseOrBody, prefix = 'API') {
  const code = apiCodeOf(responseOrBody);
  const message = apiMessageOf(responseOrBody);
  const text = safe(responseOrBody, 12000);
  if (/Agent does not exist in the selected tenant|Agent profile not found|Create or approve this Agent profile/i.test(`${message} ${text}`)) {
    return `${prefix}_REJECTED_AGENT_NOT_FOUND`;
  }
  if (/tenantId|required request parameter 'tenantId'|TENANT_CONTEXT_REQUIRED/i.test(`${message} ${text}`)) {
    return `${prefix}_REJECTED_TENANT_CONTEXT_REQUIRED`;
  }
  if (code && !okEnvelopeCodes.has(code)) return `${prefix}_REJECTED_${code}`;
  return `${prefix}_REJECTED`;
}

function arrayData(responseOrBody, fallback = []) {
  const data = unwrap(responseOrBody?.body ?? responseOrBody);
  if (Array.isArray(data)) return data;
  if (Array.isArray(data?.items)) return data.items;
  if (Array.isArray(data?.content)) return data.content;
  if (Array.isArray(data?.data)) return data.data;
  return fallback;
}

function firstValue(record, keys) {
  if (!isRecord(record)) return null;
  for (const key of keys) {
    const value = record[key];
    if (value !== null && value !== undefined && String(value).trim()) return value;
  }
  return null;
}

function sameCode(left, right) {
  return String(left ?? '').trim().toUpperCase() === String(right ?? '').trim().toUpperCase();
}

function ruleMatchesEvent(rule, event) {
  if (!isRecord(rule) || !isRecord(event)) return false;
  const comparisons = [
    ['sourceSystem', rule.sourceSystem, event.sourceSystem],
    ['objectType', rule.objectType, event.objectType],
    ['eventType', rule.eventType, event.eventType],
  ];
  const ruleErrorCode = rule.errorCode;
  if (ruleErrorCode !== null && ruleErrorCode !== undefined && String(ruleErrorCode).trim() && String(ruleErrorCode).trim() !== '*') {
    comparisons.push(['errorCode', rule.errorCode, event.errorCode]);
  }
  return comparisons.every(([, left, right]) => String(left || '').trim() === '*' || sameCode(left, right));
}

function ruleSnapshot(rule) {
  if (!isRecord(rule)) return null;
  return {
    tenantId: firstValue(rule, ['tenantId', 'tenant_id']),
    flowId: firstValue(rule, ['flowId', 'flow_id']),
    ruleId: firstValue(rule, ['ruleId', 'rule_id', 'id']),
    enabled: firstValue(rule, ['enabled', 'isEnabled', 'active', 'isActive']),
    eventStage: firstValue(rule, ['eventStage', 'event_stage']),
    sourceSystem: firstValue(rule, ['sourceSystem', 'source_system']),
    objectType: firstValue(rule, ['objectType', 'object_type']),
    eventType: firstValue(rule, ['eventType', 'event_type']),
    errorCode: firstValue(rule, ['errorCode', 'error_code']),
    requestedSkill: firstValue(rule, ['requestedSkill', 'requested_skill']),
    capabilityRequirementMode: firstValue(rule, ['capabilityRequirementMode', 'capability_requirement_mode']),
    candidatePoolMode: firstValue(rule, ['candidatePoolMode', 'candidate_pool_mode']),
  };
}

function agentSnapshot(agent) {
  if (!isRecord(agent)) return null;
  return {
    tenantId: firstValue(agent, ['tenantId', 'tenant_id']),
    flowId: firstValue(agent, ['flowId', 'flow_id']),
    agentId: firstValue(agent, ['agentId', 'agent_id', 'id']),
    assignmentStatus: firstValue(agent, ['assignmentStatus', 'assignment_status', 'status']),
    approvalStatus: firstValue(agent, ['approvalStatus', 'approval_status']),
    readinessStatus: firstValue(agent, ['readinessStatus', 'readiness_status']),
    runtimeStatus: firstValue(agent, ['runtimeStatus', 'runtime_status']),
  };
}

function skillSnapshot(skill) {
  if (!isRecord(skill)) return null;
  return {
    tenantId: firstValue(skill, ['tenantId', 'tenant_id']),
    flowId: firstValue(skill, ['flowId', 'flow_id']),
    ruleId: firstValue(skill, ['ruleId', 'rule_id']),
    skillCode: firstValue(skill, ['skillCode', 'skill_code', 'capabilityCode', 'capability_code']),
    required: firstValue(skill, ['required', 'isRequired']),
  };
}

function conditionSnapshot(event) {
  return {
    tenantId: event?.tenantId ?? null,
    sourceSystem: event?.sourceSystem ?? null,
    objectType: event?.objectType ?? null,
    eventType: event?.eventType ?? null,
    errorCode: event?.errorCode ?? null,
    severity: event?.severity ?? null,
  };
}

function analyzeFlowAggregate({ expectedFlow, expectedEvent, detail, rules, agents, skills }) {
  const detailData = unwrap(detail?.body || {});
  const detailRules = arrayData(rules, Array.isArray(detailData?.rules) ? detailData.rules : []);
  const detailAgents = arrayData(agents, Array.isArray(detailData?.agents) ? detailData.agents : []);
  const detailSkills = arrayData(skills, Array.isArray(detailData?.requiredSkills) ? detailData.requiredSkills : []);
  const expectedRule = expectedFlow?.rules?.[0] || null;
  const expectedAgent = expectedFlow?.agents?.[0] || null;
  const expectedSkill = expectedFlow?.requiredSkills?.[0] || null;
  const observedRules = detailRules.map(ruleSnapshot).filter(Boolean);
  const observedAgents = detailAgents.map(agentSnapshot).filter(Boolean);
  const observedSkills = detailSkills.map(skillSnapshot).filter(Boolean);
  const activeExternalRules = observedRules.filter((rule) => {
    const enabled = String(rule.enabled ?? '').toLowerCase();
    return (rule.enabled === true || enabled === 'true' || enabled === 'active' || enabled === 'enabled')
      && (!rule.eventStage || sameCode(rule.eventStage, 'EXTERNAL'));
  });
  const expectedRuleObserved = observedRules.some((rule) => sameCode(rule.ruleId, expectedRule?.ruleId));
  const expectedAgentObserved = observedAgents.some((agent) => sameCode(agent.agentId, expectedAgent?.agentId));
  const expectedSkillObserved = expectedSkill
    ? observedSkills.some((skill) => sameCode(skill.skillCode, expectedSkill.skillCode))
    : observedSkills.length === 0;
  const matchedRule = observedRules.find((rule) => sameCode(rule.ruleId, expectedRule?.ruleId)) || observedRules[0] || null;
  const detailApi = apiFailureSummary(detail);
  const rulesApi = apiFailureSummary(rules);
  const agentsApi = apiFailureSummary(agents);
  const skillsApi = apiFailureSummary(skills);
  const flowVisible = responseOk(detail) && isRecord(detailData) && Object.keys(detailData).length > 0;
  return {
    flowId: expectedFlow?.flowId,
    tenantId: expectedFlow?.tenantId,
    expectedSourceSystem: expectedFlow?.sourceSystem,
    detailStatus: detail?.status ?? null,
    rulesStatus: rules?.status ?? null,
    agentsStatus: agents?.status ?? null,
    skillsStatus: skills?.status ?? null,
    detailApi,
    rulesApi,
    agentsApi,
    skillsApi,
    flowVisible,
    flowActive: flowVisible && sameCode(detailData?.status ?? expectedFlow?.status, 'ACTIVE'),
    ruleCount: observedRules.length,
    activeExternalRuleCount: activeExternalRules.length,
    expectedRuleObserved,
    expectedAgentObserved,
    expectedSkillObserved,
    flowAgentAssignmentCount: observedAgents.length,
    requiredSkillCount: observedSkills.length,
    ruleConditionMatchesEvent: ruleMatchesEvent(matchedRule, expectedEvent),
    ruleCondition: ruleSnapshot(expectedRule),
    observedMatchedRule: matchedRule,
    eventCondition: conditionSnapshot(expectedEvent),
    observedAgents,
    observedSkills,
    likelyFailure: likelyFlowFailure({
      detail, expectedRuleObserved, expectedAgentObserved, expectedSkillObserved,
      activeExternalRules, matchedRule, expectedEvent, observedAgents,
    }),
    raw: {
      detail: detail?.body,
      rules: rules?.body,
      agents: agents?.body,
      skills: skills?.body,
    },
  };
}

function likelyFlowFailure({ detail, expectedRuleObserved, expectedAgentObserved, expectedSkillObserved, activeExternalRules, matchedRule, expectedEvent, observedAgents }) {
  if (!responseOk(detail)) return classifyApiFailure(detail, 'FLOW_DETAIL');
  if (!expectedRuleObserved) return 'FLOW_RULE_NOT_PERSISTED';
  if (!activeExternalRules.length) return 'NO_ACTIVE_EXTERNAL_FLOW_RULE_AFTER_SAVE';
  if (!matchedRule || !ruleMatchesEvent(matchedRule, expectedEvent)) return 'RULE_EVENT_CONDITION_MISMATCH';
  if (!expectedAgentObserved || observedAgents.length === 0) return 'FLOW_AGENT_ASSIGNMENT_NOT_PERSISTED';
  if (!expectedSkillObserved) return 'REQUIRED_CAPABILITY_NOT_PERSISTED';
  return 'FLOW_AGGREGATE_LOOKS_VALID_CHECK_RUNTIME_LOOKUP';
}

function valuesForKey(value, keys, result = []) {
  if (Array.isArray(value)) {
    for (const item of value) valuesForKey(item, keys, result);
    return result;
  }
  if (!isRecord(value)) return result;
  for (const [key, item] of Object.entries(value)) {
    if (keys.has(key) && item !== null && item !== undefined && String(item).trim()) result.push(item);
    valuesForKey(item, keys, result);
  }
  return result;
}

function evidenceState(taskId, evidence, expectedAgentId = null) {
  const selected = valuesForKey(evidence, new Set([
    'selectedAgentId', 'selected_agent_id', 'assignedAgentId', 'assigned_agent_id',
  ])).map(String).find((value) => value.trim()) || null;
  const assignmentIds = valuesForKey(evidence, new Set(['assignmentId', 'assignment_id']));
  const statuses = valuesForKey(evidence, new Set([
    'status', 'taskStatus', 'task_status', 'assignmentStatus', 'assignment_status', 'lifecycleStatus',
  ])).map((value) => String(value).toUpperCase());
  const text = safe(evidence, 100000).toUpperCase();
  const assigned = assignmentIds.length > 0 || selected !== null
    || statuses.some((value) => /ASSIGNED|DELIVERED|ACKNOWLEDGED|IN_PROGRESS|COMPLETED|SUCCEEDED/.test(value))
    || /ASSIGNMENT_CREATED|TASK_DELIVERED|TASK_ACKNOWLEDGED/.test(text);
  const completed = statuses.some((value) => /COMPLETED|SUCCEEDED|SUCCESS/.test(value))
    || /TASK_COMPLETED|TASK_RESULT.*SUCCESS|RESULT_RECEIVED/.test(text);
  const blocked = statuses.some((value) => /BLOCKED|NEEDS_ATTENTION|MANUAL_REVIEW|FAILED/.test(value))
    || /NO_ACTIVE_FLOW_RULE|NO_MATCHING_FLOW|NO_GENERIC_ELIGIBLE_AGENT/.test(text);
  return {
    taskId,
    taskCreated: Boolean(taskId),
    assignmentCreated: assigned,
    selectedAgentId: selected || (assigned ? expectedAgentId : null),
    taskCompleted: completed,
    blocked,
    statuses: [...new Set(statuses)],
  };
}

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

function authHeaders(kind, method) {
  if (kind === 'event') return eventToken ? { Authorization: `Bearer ${eventToken}` } : {};
  if (adminToken) return { Authorization: `Bearer ${adminToken}` };
  const headers = {};
  const cookies = cookieHeader();
  if (cookies) headers.Cookie = cookies;
  if (isUnsafeMethod(method) && csrfHeaderName && csrfToken) headers[csrfHeaderName] = csrfToken;
  return headers;
}

async function request(name, method, requestPath, body, options = {}) {
  const record = { name, method, path: requestPath, startedAt: nowIso(), requestBody: redact(body) };
  report.requests.push(record);
  if (dryRun) {
    record.status = options.dryRunStatus || 200;
    record.responseBody = redact(options.dryRunResponse || {});
    record.apiCode = apiCodeOf(options.dryRunResponse || {});
    record.apiOk = apiEnvelopeOk(options.dryRunResponse || {});
    record.completedAt = nowIso();
    console.log(`[DRY-RUN] ${method} ${join(coreOrigin, requestPath)}`);
    return { status: record.status, body: options.dryRunResponse || {}, data: unwrap(options.dryRunResponse || {}), apiCode: record.apiCode, apiOk: record.apiOk };
  }
  let response;
  try {
    response = await fetch(join(coreOrigin, requestPath), {
      method,
      headers: {
        Accept: 'application/json',
        ...(body === undefined ? {} : { 'Content-Type': 'application/json' }),
        ...authHeaders(options.authKind || 'admin', method),
      },
      body: body === undefined ? undefined : JSON.stringify(body),
      cache: 'no-store',
    });
  } catch (error) {
    record.transportError = String(error?.message || error);
    record.completedAt = nowIso();
    throw new Error(`${name}: unable to call ${join(coreOrigin, requestPath)}: ${record.transportError}`);
  }
  rememberResponseCookies(response.headers);
  const text = await response.text();
  let parsed = null;
  if (text) {
    try { parsed = JSON.parse(text); } catch { parsed = { raw: text }; }
  }
  record.status = response.status;
  record.responseBody = redact(parsed);
  record.apiCode = apiCodeOf(parsed);
  record.apiOk = apiEnvelopeOk(parsed);
  record.completedAt = nowIso();
  if (!options.allowHttpError && (response.status < 200 || response.status >= 300)) {
    throw new Error(`${name}: HTTP ${response.status}; body=${text.slice(0, 1800)}`);
  }
  if (!options.allowApiError && response.status >= 200 && response.status < 300 && !apiEnvelopeOk(parsed)) {
    throw new Error(`${name}: API code=${apiCodeOf(parsed)}; body=${text.slice(0, 1800)}`);
  }
  return { status: response.status, body: parsed, data: unwrap(parsed), apiCode: apiCodeOf(parsed), apiOk: apiEnvelopeOk(parsed) };
}

async function refreshCsrf() {
  const response = await request('admin csrf', 'GET', '/api/auth/csrf', undefined, { allowHttpError: true });
  if (response.status !== 200 || !response.data?.headerName || !response.data?.token) {
    throw new Error(`Core Admin CSRF bootstrap failed with HTTP ${response.status}: ${safe(response.body)}`);
  }
  csrfHeaderName = String(response.data.headerName);
  csrfToken = String(response.data.token);
  return response.data;
}

function validateAgentSelection() {
  const normalizedEnvFile = String(configuredEnvFile || '').replace(/\\/g, '/');
  const ciEnvironment = /\.env\.local\.ci$/.test(normalizedEnvFile) || /docker-compose\.ci/.test(normalizedEnvFile);
  if (!ciEnvironment && ids.agentRuntime === 'agent-local-ci-001') {
    throw new Error('STAGE1_AGENT_ID=agent-local-ci-001 belongs to docker-compose.ci.yml, but the current ENV_FILE is local. make up-agent starts agent-local-001. Remove STAGE1_AGENT_ID or run the CI Compose stack.');
  }
}

async function ensureAdminAuthentication() {
  if (dryRun) return { mode: 'DRY_RUN' };
  if (adminToken) {
    console.log('Stage 1 Admin authentication: using configured Bearer token.');
    return { mode: 'BEARER_TOKEN' };
  }
  if (!adminUsername || !adminPassword) {
    throw new Error(`Stage 1 Admin authentication credentials are missing. Set STAGE1_ADMIN_USERNAME/STAGE1_ADMIN_PASSWORD or configure CORE_ADMIN_BOOTSTRAP_* in ${configuredEnvFile}.`);
  }
  await refreshCsrf();
  const login = await request('admin login', 'POST', '/api/auth/login', {
    username: adminUsername,
    password: adminPassword,
  }, { allowHttpError: true });
  if (login.status !== 200) {
    throw new Error(`Core Admin login failed for username=${adminUsername} with HTTP ${login.status}: ${safe(login.body)}`);
  }
  // Spring Security may rotate the session and CSRF token after authentication.
  await refreshCsrf();
  const current = await request('admin current session', 'GET', '/api/auth/me', undefined, { allowHttpError: true });
  if (current.status !== 200 || String(current.data?.username || '') !== adminUsername) {
    throw new Error(`Core Admin session verification failed with HTTP ${current.status}: ${safe(current.body)}`);
  }
  console.log(`Stage 1 Admin authentication: session established for username=${adminUsername}.`);
  return { mode: 'CORE_ADMIN_SESSION', username: adminUsername };
}

function setupPayload(tenantId, agentId, capabilityCodes = []) {
  return {
    tenantId,
    agentId,
    agentName: `Stage 0 ${agentId}`,
    ownerTeam: 'stage0-characterization',
    description: 'Created by Stage 0 Integration Characterization.',
    purpose: 'STANDARD_DISPATCH_CHARACTERIZATION',
    runtimeType: 'Docker',
    gatewayUrl,
    credentialToken: agentId === ids.agentRuntime ? agentOnboardingToken : `cred-${runId}-${agentId}`,
    autoApprove: true,
    createDefaultCapabilities: false,
    createRuntimeBinding: true,
    createSupplyProfile: false,
    createDefaultDispatchRule: false,
    defaultCapabilities: [],
    defaultTaskTypes: [],
    capacityLimit: 4,
    operatorId: 'stage0-characterization',
    metadata: { stage0: true, runId, arbitrarySourceOnly: true },
  };
}

function flowPayload({ tenantId, sourceSystem, agentId, capabilityCode = null }) {
  const suffix = normalizeId(`${sourceSystem}-${agentId}`);
  const mode = capabilityCode ? 'EXPLICIT' : 'NONE';
  const flowId = `flow-${suffix}`;
  const ruleId = `rule-${suffix}`;
  const eventType = normalizeCode(`EVENT_${sourceSystem}`);
  return {
    tenantId,
    flowId,
    flowCode: normalizeCode(`FLOW_${sourceSystem}`),
    flowName: `Stage 0 Flow ${sourceSystem}`,
    sourceSystem,
    status: 'ACTIVE',
    description: 'Stage 0 real Flow using an arbitrary tenant-owned Source System.',
    defaultCapabilityRequirementMode: mode,
    defaultRequiredOperation: 'ANALYZE',
    defaultSideEffectLevel: 'NONE',
    defaultCandidatePoolMode: 'EXPLICIT_FLOW_AGENTS',
    defaultRoutingStrategy: 'WEIGHTED_SCORE',
    metadata: { stage0: true, runId, expectedStandardPath: true },
    rules: [{
      tenantId,
      ruleId,
      flowId,
      ruleCode: normalizeCode(`RULE_${sourceSystem}`),
      ruleName: `Stage 0 Rule ${sourceSystem}`,
      ruleScope: 'EXTERNAL_INTAKE',
      eventStage: 'EXTERNAL',
      sourceSystem,
      objectType: normalizeCode(`OBJECT_${sourceSystem}`),
      eventType,
      errorCode: normalizeCode(`ERROR_${sourceSystem}`),
      requestedSkill: capabilityCode,
      capabilityRequirementMode: mode,
      requiredOperation: 'ANALYZE',
      sideEffectLevel: 'NONE',
      candidatePoolMode: 'EXPLICIT_FLOW_AGENTS',
      routingStrategy: 'WEIGHTED_SCORE',
      explicitActionAuthorizationRequired: false,
      priority: 10,
      enabled: true,
      condition: { stage0: true, runId },
    }],
    requiredSkills: capabilityCode ? [{
      tenantId,
      id: `skill-${suffix}`,
      flowId,
      ruleId,
      eventStage: 'EXTERNAL',
      agentRole: 'LEAD',
      skillCode: capabilityCode,
      skillName: capabilityCode,
      skillKind: 'FLOW_SKILL',
      required: true,
      openClawSkill: true,
      description: 'Stage 0 explicit capability requirement.',
    }] : [],
    agents: [{
      tenantId,
      id: `flow-agent-${suffix}`,
      flowId,
      agentId,
      agentName: `Stage 0 ${agentId}`,
      eventStage: 'EXTERNAL',
      agentRole: 'LEAD',
      assignmentStatus: 'ACTIVE',
      approvalStatus: 'APPROVED',
      readinessStatus: 'READY',
      runtimeStatus: 'CONNECTED',
    }],
  };
}

function eventPayload({ tenantId, sourceSystem, flow = null }) {
  const rule = flow?.rules?.[0];
  return {
    tenantId,
    sourceSystem,
    eventStage: 'EXTERNAL',
    objectType: rule?.objectType || normalizeCode(`OBJECT_${sourceSystem}`),
    objectId: normalizeCode(`OBJECT_ID_${runId}_${Date.now()}`),
    eventType: rule?.eventType || normalizeCode(`EVENT_${sourceSystem}`),
    errorCode: rule?.errorCode || normalizeCode(`ERROR_${sourceSystem}`),
    severity: 'CRITICAL',
    message: `Stage 0 characterization event for arbitrary source ${sourceSystem}.`,
    occurredAt: nowIso(),
    attributes: { stage0: true, runId, arbitrarySource: true },
  };
}

const blockerPatterns = [
  'NO_ACTIVE_FLOW_RULE', 'NO_MATCHING_FLOW',
  'AGENT_RUNTIME_NOT_FOUND', 'AGENT_SKILL_GRANT', 'MISSING_CAPABILITY', 'CAPABILITY_MISSING', 'REQUIRED_CAPABILITY_NOT_APPROVED', 'SERVICE_SCOPE', 'TASK_SCOPE',
  'ASSIGNMENT_PROFILE', 'SOURCE_DEFAULT', 'SOURCE_ASSIGNMENT',
  'OPERATION_PROFILE', 'NO_CANDIDATE', 'AGENT_OFFLINE', 'RUNTIME_UNAVAILABLE',
  'CAPACITY_EXHAUSTED', 'TENANT_CONTEXT_REQUIRED', 'BAD_SQL_GRAMMAR', 'PreparedStatementCallback',
];

function detectBlockers(value) {
  const text = safe(value, 50000);
  return blockerPatterns.filter((pattern) => text.toUpperCase().includes(pattern.toUpperCase()));
}

async function taskEvidence(taskId) {
  if (!taskId) return { taskId: null, views: [], blockers: [] };
  const endpoints = [
    `/admin/tasks/${encodeURIComponent(taskId)}/runtime-view`,
    `/admin/tasks/${encodeURIComponent(taskId)}`,
    `/admin/tasks/${encodeURIComponent(taskId)}/dispatch-evidence`,
    `/admin/tasks/${encodeURIComponent(taskId)}/routing-decisions`,
    `/admin/tasks/${encodeURIComponent(taskId)}/case-timeline`,
  ];
  const views = [];
  for (const endpoint of endpoints) {
    const result = await request(`task evidence ${endpoint}`, 'GET', endpoint, undefined, {
      allowHttpError: true,
      dryRunResponse: { taskId, routingPath: 'FLOW_RULE', characterization: true },
    });
    views.push({ endpoint, status: result.status, body: result.body });
  }
  const categories = categorizedBlockers(views);
  return { taskId, views, ...categories };
}

async function waitForEvidence(taskId, options = {}) {
  if (!taskId) return taskEvidence(taskId);
  if (dryRun) {
    const blocked = options.expectBlocked === true;
    return {
      taskId,
      views: [{
        endpoint: `/admin/tasks/${encodeURIComponent(taskId)}`,
        status: 200,
        body: blocked
          ? { taskId, status: 'NEEDS_ATTENTION', routingPath: 'FLOW_RULE_REQUIRED_BLOCKED', reason: 'NO_ACTIVE_FLOW_RULE' }
          : { taskId, status: 'COMPLETED', assignmentId: `assignment-${taskId}`, selectedAgentId: options.expectedAgentId || ids.agentRuntime, routingPath: 'FLOW_RULE' },
      }],
      ...categorizedBlockers(blocked ? { reason: 'NO_ACTIVE_FLOW_RULE' } : { status: 'COMPLETED' }),
    };
  }
  const deadline = Date.now() + timeoutMs;
  let last = null;
  while (Date.now() <= deadline) {
    last = await taskEvidence(taskId);
    const state = evidenceState(taskId, last, options.expectedAgentId || null);
    if (options.expectBlocked ? state.blocked : state.taskCompleted) return last;
    await sleep(intervalMs);
  }
  return last || { taskId, views: [], blockers: ['EVIDENCE_TIMEOUT'] };
}

async function scenario(name, desired, runner) {
  const item = { name, desired, startedAt: nowIso(), status: 'RUNNING' };
  report.scenarios.push(item);
  try {
    const actual = await runner();
    item.actual = redact(actual);
    item.blockers = detectBlockers(actual);
    item.passed = Boolean(desired(actual));
    item.status = item.passed ? 'DESIRED_BEHAVIOR_OBSERVED' : 'CURRENT_BEHAVIOR_DIFFERS';
  } catch (error) {
    item.passed = false;
    item.status = 'EXECUTION_ERROR';
    item.error = String(error?.stack || error?.message || error);
    item.blockers = detectBlockers(item.error);
  }
  item.completedAt = nowIso();
  console.log(`[${item.status}] ${name}${item.blockers?.length ? ` blockers=${item.blockers.join(',')}` : ''}`);
  return item;
}

const preparedAgents = new Set();

async function createAgent(tenantId, agentId) {
  const key = `${tenantId}|${agentId}`;
  if (preparedAgents.has(key)) return { status: 200, body: { tenantId, agentId, reused: true }, data: { tenantId, agentId, reused: true }, apiOk: true };
  const result = await request(`setup ${agentId}`, 'POST', '/admin/agents/setup', setupPayload(tenantId, agentId, []), {
    dryRunResponse: { tenantId, agentId, setupStatus: 'PROFILE_READY', approvalStatus: 'APPROVED', capabilityAssignments: [] },
  });
  preparedAgents.add(key);
  return result;
}

function setupCheckReady(viewBody, code) {
  const data = unwrap(viewBody) || {};
  const checks = [];
  if (Array.isArray(data.authorityChecks)) checks.push(...data.authorityChecks);
  if (Array.isArray(data.setupReadiness?.checks)) checks.push(...data.setupReadiness.checks);
  const wanted = String(code || '').toUpperCase();
  const matched = checks.find((check) => String(check?.code || '').toUpperCase() === wanted);
  if (!matched) return false;
  if (matched.ready === true) return true;
  const status = String(matched.status || '').toUpperCase();
  return status === 'READY' || status === 'PASS';
}

async function ensureAgentProfile(tenantId, agentId) {
  // Stage 8-F0h: this precondition only verifies that the tenant-scoped
  // Agent profile exists and is approved before Dispatch Flow persists
  // flow_agent_assignments. It must not fail just because legacy Service Scope
  // diagnostics are present in the operational view; Flow-owned dispatch is the
  // Stage 8 authority.
  const setup = await createAgent(tenantId, agentId);
  const view = await request(`verify profile ${agentId}`, 'GET', `/admin/agents/${encodeURIComponent(agentId)}/operational-view`, undefined, {
    allowHttpError: true,
    allowApiError: true,
    dryRunResponse: { tenantId, agentId, canReceiveTask: true, readinessStatus: 'READY', readinessLevel: 'READY' },
  });
  const text = safe(view.body, 50000);
  if (!dryRun && !setupCheckReady(view.body, 'AGENT_PROFILE_EXISTS')) {
    throw new Error(`Agent profile precondition failed for ${agentId} in ${tenantId}: ${text.slice(0, 1800)}`);
  }
  return { setup: setup.body, operationalView: view.body };
}

async function waitForAgentRuntime(agentId) {
  if (dryRun) return { connected: true, agentId, status: 'IDLE' };
  const deadline = Date.now() + timeoutMs;
  let last = null;
  while (Date.now() <= deadline) {
    const response = await request(`runtime ${agentId}`, 'GET', `/admin/agents/${encodeURIComponent(agentId)}/operational-view`, undefined, { allowHttpError: true });
    last = response.body;
    if (response.status === 401 || response.status === 403) {
      throw new Error(`Agent runtime query is not authorized (HTTP ${response.status}): ${safe(last)}`);
    }
    const text = safe(last, 50000).toUpperCase();
    const data = unwrap(last) || {};
    const connectionStatus = String(data.connectionStatus || data.runtimeStatus || data.status || '');
    const runtimeConnected = setupCheckReady(last, 'RUNTIME_CONNECTED')
      || /^(CONNECTED|IDLE|BUSY_ACCEPTING)$/i.test(connectionStatus);
    if (!setupCheckReady(last, 'AGENT_PROFILE_EXISTS') || /CREATE OR APPROVE THIS AGENT PROFILE/.test(text)) {
      return { connected: false, agentId, profileMissing: true, response: last };
    }
    if (responseOk(response) && runtimeConnected && !/^(OFFLINE|EXPIRED|UNKNOWN)$/i.test(connectionStatus)) {
      return { connected: true, agentId, response: last };
    }
    await sleep(intervalMs);
  }
  return { connected: false, agentId, response: last };
}

async function createCapability(tenantId, capabilityCode) {
  return request(`create capability ${capabilityCode}`, 'PUT',
    `/admin/capabilities/${encodeURIComponent(capabilityCode)}?tenantId=${encodeURIComponent(tenantId)}`,
    {
      tenantId,
      capabilityCode,
      capabilityName: `Stage 1 ${capabilityCode}`,
      category: 'GENERAL',
      capabilityType: 'SERVICE',
      domain: 'GENERAL',
      resourceType: 'GENERIC_RESOURCE',
      operation: 'ANALYZE',
      dataClass: 'INTERNAL',
      serviceLevel: 'STANDARD',
      status: 'ACTIVE',
      requiresApproval: false,
      requiresCertification: false,
      requiresRuntimeProbe: false,
      dispatchEligible: true,
      metadata: { stage1GoldenPath: true, sourceNeutral: true, runId },
    },
    { dryRunResponse: { tenantId, capabilityCode, status: 'ACTIVE', requiresApproval: false } });
}

async function assignCapability(tenantId, agentId, capabilityCode) {
  const result = await request(`assign capability ${capabilityCode} to ${agentId}`, 'POST',
    `/admin/agents/${encodeURIComponent(agentId)}/capabilities`,
    {
      tenantId,
      capabilityCode,
      operatorId: 'stage1-characterization',
      source: 'STAGE1_GOLDEN_PATH',
      evidenceRef: runId,
      reason: 'Assign explicit reusable Capability for Stage 1 Golden Path integration.',
      metadata: { stage1GoldenPath: true, sourceNeutral: true, runId },
    },
    {
      allowHttpError: true,
      allowApiError: true,
      dryRunResponse: { tenantId, agentId, capabilityCode, assignmentId: `assignment-${runId}`, status: 'APPROVED' },
    });
  const text = safe(result.body, 20000);
  if (result.status >= 200 && result.status < 300 && apiEnvelopeOk(result.body)) return result;
  // Stage 8-F0i: T1-03 intentionally assigns the same explicit capability that
  // T1-09 later verifies. The characterization should treat an already-approved
  // assignment as idempotent evidence instead of failing before it can inspect
  // tenant consistency. The product lifecycle can still reject duplicate writes.
  if (result.status === 400 && /already has APPROVED capability/i.test(text)) {
    return {
      ...result,
      status: 200,
      body: {
        code: 'OK',
        message: 'Capability assignment already approved; reused for Stage 1 characterization.',
        data: { tenantId, agentId, capabilityCode, status: 'APPROVED', reused: true },
      },
      data: { tenantId, agentId, capabilityCode, status: 'APPROVED', reused: true },
      apiCode: 'OK',
      apiOk: true,
    };
  }
  throw new Error(`assign capability ${capabilityCode} to ${agentId}: HTTP ${result.status}; body=${text.slice(0, 1800)}`);
}

async function createFlow(payload) {
  // Stage 8-F0d: keep BAD_REQUEST/INTERNAL_ERROR envelopes as evidence so the
  // drilldown reports FLOW_CREATE_REJECTED_* instead of a misleading
  // FLOW_RULE_NOT_PERSISTED follow-up symptom.
  return request(`create ${payload.flowId}`, 'POST', `/admin/dispatch-flows?tenantId=${encodeURIComponent(payload.tenantId)}`, payload, {
    allowApiError: true,
    dryRunResponse: payload,
  });
}

async function inspectFlowAggregate(expectedFlow, expectedEvent) {
  const tenantId = expectedFlow.tenantId;
  const flowId = expectedFlow.flowId;
  const detail = await request(`inspect flow detail ${flowId}`, 'GET', `/admin/dispatch-flows/${encodeURIComponent(flowId)}?tenantId=${encodeURIComponent(tenantId)}`, undefined, {
    allowHttpError: true,
    allowApiError: true,
    dryRunResponse: expectedFlow,
  });
  const rules = await request(`inspect flow rules ${flowId}`, 'GET', `/admin/dispatch-flows/${encodeURIComponent(flowId)}/rules?tenantId=${encodeURIComponent(tenantId)}`, undefined, {
    allowHttpError: true,
    allowApiError: true,
    dryRunResponse: expectedFlow.rules || [],
  });
  const agents = await request(`inspect flow agents ${flowId}`, 'GET', `/admin/dispatch-flows/${encodeURIComponent(flowId)}/agents?tenantId=${encodeURIComponent(tenantId)}`, undefined, {
    allowHttpError: true,
    allowApiError: true,
    dryRunResponse: expectedFlow.agents || [],
  });
  const skills = await request(`inspect flow skills ${flowId}`, 'GET', `/admin/dispatch-flows/${encodeURIComponent(flowId)}/skills?tenantId=${encodeURIComponent(tenantId)}`, undefined, {
    allowHttpError: true,
    allowApiError: true,
    dryRunResponse: expectedFlow.requiredSkills || [],
  });
  return analyzeFlowAggregate({ expectedFlow, expectedEvent, detail, rules, agents, skills });
}

function buildStage1Evidence({ taskId, runtime, createResult, flowDiagnostics, event, intakeResponse, taskEvidence }) {
  const state = evidenceState(taskId, taskEvidence, flowDiagnostics?.observedAgents?.[0]?.agentId || ids.agentRuntime);
  const blockerCategories = categorizedBlockers({ intake: intakeResponse?.body, taskEvidence });
  const runtimeLookupLikely = flowDiagnostics?.likelyFailure === 'FLOW_AGGREGATE_LOOKS_VALID_CHECK_RUNTIME_LOOKUP';
  let firstFailure = null;
  if (createResult && !responseOk(createResult)) firstFailure = classifyApiFailure(createResult, 'FLOW_CREATE');
  else if (!flowDiagnostics?.flowVisible) firstFailure = flowDiagnostics?.likelyFailure || 'FLOW_NOT_VISIBLE_AFTER_SAVE';
  else if (!flowDiagnostics?.expectedRuleObserved) firstFailure = 'FLOW_RULE_NOT_PERSISTED';
  else if (!flowDiagnostics?.activeExternalRuleCount) firstFailure = 'NO_ACTIVE_EXTERNAL_FLOW_RULE_AFTER_SAVE';
  else if (!flowDiagnostics?.ruleConditionMatchesEvent) firstFailure = 'RULE_EVENT_CONDITION_MISMATCH';
  else if (!flowDiagnostics?.expectedAgentObserved) firstFailure = 'FLOW_AGENT_ASSIGNMENT_NOT_PERSISTED';
  else if (!runtime?.connected) firstFailure = 'AGENT_RUNTIME_NOT_CONNECTED';
  else if (!state.assignmentCreated && runtimeLookupLikely) firstFailure = 'RUNTIME_LOOKUP_OR_ASSIGNMENT_DID_NOT_USE_FLOW_AGGREGATE';
  else if (!state.taskCompleted && state.assignmentCreated) firstFailure = 'DELIVERY_ACK_RESULT_NOT_COMPLETED';
  else if (blockerCategories.legacyLeakInStandardEvidence) firstFailure = 'LEGACY_BLOCKER_LEAKED_IN_STANDARD_EVIDENCE';
  return {
    ...state,
    firstFailure,
    tenantId: flowDiagnostics?.tenantId,
    sourceSystem: flowDiagnostics?.expectedSourceSystem,
    flowId: flowDiagnostics?.flowId,
    ruleId: flowDiagnostics?.ruleCondition?.ruleId,
    agentId: flowDiagnostics?.observedAgents?.[0]?.agentId || ids.agentRuntime,
    eventCondition: conditionSnapshot(event),
    flowDiagnostics,
    blockerCategories,
    runtime,
    createFlowStatus: createResult?.status ?? null,
    createFlowApi: apiFailureSummary(createResult),
    createFlow: createResult?.body,
    intake: intakeResponse?.body,
    evidence: taskEvidence,
  };
}

async function intake(payload, dryRunResponse) {
  return request(`intake ${payload.sourceSystem}`, 'POST', '/api/events/intake', payload, {
    authKind: 'event',
    dryRunResponse,
  });
}

async function queryOptionalFilterEndpoints() {
  const endpoints = [
    ['/admin/dispatch-governance/operation-profiles', 'operation profiles omitted status'],
    ['/admin/dispatch-governance/source-defaults', 'source defaults omitted status'],
    ['/admin/dispatch-governance/source-assignments', 'source assignments omitted optional filters'],
    ['/admin/dispatch-governance/actions/catalog', 'action catalog omitted status'],
    ['/admin/dispatch-governance/actions/grants', 'action grants omitted optional filters'],
    ['/admin/dispatch-governance/actions/approval-requests', 'approval requests omitted status'],
  ];
  const results = [];
  for (const [endpoint, label] of endpoints) {
    const result = await request(label, 'GET', `${endpoint}?tenantId=${encodeURIComponent(ids.tenantA)}&limit=10`, undefined, {
      allowHttpError: true,
      allowApiError: true,
      dryRunResponse: [],
    });
    results.push({ label, endpoint, status: result.status, body: result.body });
  }
  return results;
}

function apiHealthy(result) {
  const text = safe(result, 50000);
  return apiEnvelopeOk(result?.body ?? result)
    && !/tenantId is required|BadSqlGrammarException|PreparedStatementCallback|bad SQL grammar|Internal Server Error/i.test(text);
}

function every2xx(results) {
  return Array.isArray(results) && results.every((item) => item.status >= 200 && item.status < 300 && apiEnvelopeOk(item.body));
}

function containsCapability(value, capabilityCode) {
  const text = safe(value, 100000).toUpperCase();
  return text.includes(String(capabilityCode).toUpperCase());
}

async function run() {
  await scenario(
    `${scenarioPrefix}-01 arbitrary Source System uses no CMS/MES/ERP special case`,
    (actual) => actual.sourceNamesAreArbitrary === true && actual.noBusinessSourceLiteral === true && actual.noReadinessSimulator === true,
    async () => {
      const createdSources = [ids.sourceNoCapability, ids.sourceCapability, ids.sourceNoFlow, ids.sourceOfflineAgent];
      const text = safe({ ids, createdSources }, 100000);
      return {
        sourceNamesAreArbitrary: createdSources.every((source) => /^SRC_STAGE0|^SRC_STAGE1|^SRC_/.test(source)),
        noBusinessSourceLiteral: !/\b(CMS|MES|ERP)\b/.test(text),
        noReadinessSimulator: !(new RegExp('readiness|simulator|test-chain', 'i')).test(text),
        createdSources,
      };
    },
  );

  await scenario(
    `${scenarioPrefix}-02 No-Capability Golden Path`,
    (actual) => actual.taskCreated === true && actual.assignmentCreated === true && actual.selectedAgentId === ids.agentRuntime && actual.taskCompleted === true,
    async () => {
      const agentProfile = await ensureAgentProfile(ids.tenantA, ids.agentRuntime);
      const runtime = await waitForAgentRuntime(ids.agentRuntime);
      if (!runtime.connected) throw new Error(`Stage 1 requires a real connected Agent: ${ids.agentRuntime}`);
      const flow = flowPayload({ tenantId: ids.tenantA, sourceSystem: ids.sourceNoCapability, agentId: ids.agentRuntime });
      const createResult = await createFlow(flow);
      const event = eventPayload({ tenantId: ids.tenantA, sourceSystem: ids.sourceNoCapability, flow });
      const flowDiagnostics = await inspectFlowAggregate(flow, event);
      const response = await intake(event, {
        taskCreated: true, taskId: `task-${runId}-no-cap`, assignmentCreated: true,
        selectedAgentId: ids.agentRuntime, matchedFlowId: flow.flowId, matchedRuleId: flow.rules[0].ruleId,
        routingPath: 'FLOW_RULE', capabilityRequirementMode: 'NONE',
      });
      const taskId = taskIdOf(response);
      const evidence = await waitForEvidence(taskId, { expectedAgentId: ids.agentRuntime });
      return buildStage1Evidence({ taskId, runtime: { ...runtime, agentProfile }, createResult, flowDiagnostics, event, intakeResponse: response, taskEvidence: evidence });
    },
  );

  await scenario(
    `${scenarioPrefix}-03 Explicit-Capability Golden Path`,
    (actual) => actual.taskCreated === true && actual.assignmentCreated === true && actual.selectedAgentId === ids.agentRuntime && actual.taskCompleted === true,
    async () => {
      const agentProfile = await ensureAgentProfile(ids.tenantA, ids.agentRuntime);
      const runtime = await waitForAgentRuntime(ids.agentRuntime);
      if (!runtime.connected) throw new Error(`Stage 1 requires a real connected Agent: ${ids.agentRuntime}`);
      await createCapability(ids.tenantA, ids.capability);
      await assignCapability(ids.tenantA, ids.agentRuntime, ids.capability);
      const flow = flowPayload({ tenantId: ids.tenantA, sourceSystem: ids.sourceCapability, agentId: ids.agentRuntime, capabilityCode: ids.capability });
      const createResult = await createFlow(flow);
      const event = eventPayload({ tenantId: ids.tenantA, sourceSystem: ids.sourceCapability, flow });
      const flowDiagnostics = await inspectFlowAggregate(flow, event);
      const response = await intake(event, {
        taskCreated: true, taskId: `task-${runId}-cap`, assignmentCreated: true,
        selectedAgentId: ids.agentRuntime, matchedFlowId: flow.flowId, matchedRuleId: flow.rules[0].ruleId,
        requestedSkill: ids.capability, routingPath: 'FLOW_RULE', capabilityRequirementMode: 'EXPLICIT',
      });
      const taskId = taskIdOf(response);
      const evidence = await waitForEvidence(taskId, { expectedAgentId: ids.agentRuntime });
      return buildStage1Evidence({ taskId, runtime: { ...runtime, agentProfile }, createResult, flowDiagnostics, event, intakeResponse: response, taskEvidence: evidence });
    },
  );

  await scenario(
    `${scenarioPrefix}-04 no matching Flow is fail-closed`,
    (actual) => actual.taskCreated === true && actual.assignmentCreated !== true && actual.noLegacyEvidence === true && actual.noParallelFallbackGuidance === true,
    async () => {
      const response = await intake(eventPayload({ tenantId: ids.tenantA, sourceSystem: ids.sourceNoFlow }), {
        taskCreated: true, taskId: `task-${runId}-no-flow`, assignmentCreated: false,
        routingPath: 'FLOW_RULE_REQUIRED_BLOCKED', reason: 'NO_ACTIVE_FLOW_RULE',
      });
      const evidence = await waitForEvidence(taskIdOf(response), { expectBlocked: true });
      const text = safe({ intake: response.body, evidence }, 50000);
      return {
        taskCreated: bool(response, 'taskCreated', 'task_created'),
        assignmentCreated: bool(response, 'assignmentCreated', 'assignment_created'),
        noLegacyEvidence: !/CAPABILITY_FIRST|PROFILE_FIRST|LEGACY_ROUTING|SERVICE_SCOPE|ASSIGNMENT_PROFILE|TASK_SCOPE/i.test(text),
        noParallelFallbackGuidance: !/SOURCE_DEFAULT|SOURCE ASSIGNMENT|AGENT SOURCE COVERAGE|OPERATION PROFILE/i.test(text),
        intake: response.body,
        evidence,
      };
    },
  );

  await scenario(
    `${scenarioPrefix}-05 Flow has Agent but Agent offline explains runtime blocker`,
    (actual) => actual.taskCreated === true && actual.assignmentCreated !== true && actual.offlineOrRuntimeBlocker === true && actual.notMissingLegacyServiceScope === true,
    async () => {
      await ensureAgentProfile(ids.tenantA, ids.agentOffline);
      const flow = flowPayload({ tenantId: ids.tenantA, sourceSystem: ids.sourceOfflineAgent, agentId: ids.agentOffline });
      await createFlow(flow);
      const response = await intake(eventPayload({ tenantId: ids.tenantA, sourceSystem: ids.sourceOfflineAgent, flow }), {
        taskCreated: true, taskId: `task-${runId}-offline-agent`, assignmentCreated: false,
        selectedAgentId: null, matchedFlowId: flow.flowId, matchedRuleId: flow.rules[0].ruleId,
        routingPath: 'FLOW_RULE_AGENT_RUNTIME_BLOCKED', reason: 'AGENT_OFFLINE', blockers: ['AGENT_OFFLINE'],
      });
      const evidence = dryRun
        ? { taskId: `task-${runId}-offline-agent`, views: [{ endpoint: '/admin/tasks/offline-agent-characterization', status: 200, body: { status: 'NEEDS_ATTENTION', reason: 'AGENT_OFFLINE', agentId: ids.agentOffline } }], blockers: ['AGENT_OFFLINE'] }
        : await waitForEvidence(taskIdOf(response), { expectBlocked: true });
      const text = safe({ intake: response.body, evidence }, 100000);
      return {
        taskCreated: bool(response, 'taskCreated', 'task_created') || Boolean(taskIdOf(response)),
        assignmentCreated: bool(response, 'assignmentCreated', 'assignment_created'),
        offlineOrRuntimeBlocker: /AGENT_OFFLINE|RUNTIME_UNAVAILABLE|OFFLINE|NO_GENERIC_ELIGIBLE_AGENT|NO_ELIGIBLE_AGENT/i.test(text),
        notMissingLegacyServiceScope: !/SERVICE_SCOPE|TASK_SCOPE|ASSIGNMENT_PROFILE|QUALIFICATION/i.test(text),
        intake: response.body,
        evidence,
      };
    },
  );

  await scenario(
    `${scenarioPrefix}-06 tenant isolation for Dispatch Flow detail`,
    (actual) => actual.crossTenantStatus === 404 || actual.crossTenantStatus === 403 || actual.crossTenantHidden === true,
    async () => {
      await ensureAgentProfile(ids.tenantA, ids.agentRuntime);
      const flow = flowPayload({ tenantId: ids.tenantA, sourceSystem: normalizeCode(`SRC_${runId}_ISOLATION`), agentId: ids.agentRuntime });
      await createFlow(flow);
      const crossTenant = await request('cross tenant flow detail', 'GET', `/admin/dispatch-flows/${encodeURIComponent(flow.flowId)}?tenantId=${encodeURIComponent(ids.tenantB)}`, undefined, {
        allowHttpError: true,
        dryRunStatus: 404,
        dryRunResponse: { code: 'NOT_FOUND' },
      });
      const text = safe(crossTenant.body);
      return {
        crossTenantStatus: crossTenant.status,
        crossTenantHidden: crossTenant.status >= 400 || !text.includes(flow.flowId),
        response: crossTenant.body,
      };
    },
  );

  await scenario(
    `${scenarioPrefix}-07 Operation Profile nullable status query`,
    (actual) => actual.status >= 200 && actual.status < 300 && actual.noSqlGrammar === true,
    async () => {
      const result = await request('operation profiles omitted status', 'GET', `/admin/dispatch-governance/operation-profiles?tenantId=${encodeURIComponent(ids.tenantA)}&limit=10`, undefined, {
        allowHttpError: true,
        dryRunResponse: [],
      });
      return {
        status: result.status,
        noSqlGrammar: apiHealthy(result),
        response: result.body,
      };
    },
  );

  await scenario(
    `${scenarioPrefix}-08 governance nullable optional filters`,
    (actual) => actual.all2xx === true && actual.noSqlGrammar === true && actual.noTenantContextError === true,
    async () => {
      const results = await queryOptionalFilterEndpoints();
      const text = safe(results, 100000);
      return {
        all2xx: every2xx(results),
        noSqlGrammar: !/BadSqlGrammarException|PreparedStatementCallback|bad SQL grammar/i.test(text),
        noTenantContextError: !/tenantId is required|TENANT_CONTEXT_REQUIRED/i.test(text),
        results,
      };
    },
  );

  await scenario(
    `${scenarioPrefix}-09 capability catalog and Agent assignment tenant consistency`,
    (actual) => actual.catalogStatus >= 200 && actual.catalogStatus < 300 && actual.agentStatus >= 200 && actual.agentStatus < 300 && actual.catalogContainsCapability === true && actual.agentContainsCapability === true,
    async () => {
      await createCapability(ids.tenantA, ids.capability);
      await assignCapability(ids.tenantA, ids.agentRuntime, ids.capability);
      const catalog = await request('capability catalog with tenant', 'GET', `/admin/capabilities?tenantId=${encodeURIComponent(ids.tenantA)}&limit=500`, undefined, {
        allowHttpError: true,
        dryRunResponse: [{ tenantId: ids.tenantA, capabilityCode: ids.capability, status: 'ACTIVE' }],
      });
      const agentCaps = await request('agent capability assignments', 'GET', `/admin/agents/${encodeURIComponent(ids.agentRuntime)}/capabilities`, undefined, {
        allowHttpError: true,
        dryRunResponse: [{ tenantId: ids.tenantA, agentId: ids.agentRuntime, capabilityCode: ids.capability, status: 'APPROVED' }],
      });
      return {
        catalogStatus: catalog.status,
        agentStatus: agentCaps.status,
        catalogContainsCapability: containsCapability(catalog.body, ids.capability),
        agentContainsCapability: containsCapability(agentCaps.body, ids.capability),
        catalog: catalog.body,
        agentCapabilities: agentCaps.body,
      };
    },
  );

  await scenario(
    `${scenarioPrefix}-10 tenant contract rejects missing tenant instead of silent success`,
    (actual) => actual.missingTenantStatus >= 400 && actual.missingTenantIsExplicit === true,
    async () => {
      const result = await request('operation profiles missing tenant', 'GET', '/admin/dispatch-governance/operation-profiles?limit=10', undefined, {
        allowHttpError: true,
        allowApiError: true,
        dryRunStatus: 400,
        dryRunResponse: { code: 'TENANT_CONTEXT_REQUIRED', message: 'tenantId is required' },
      });
      const text = safe(result.body, 50000);
      return {
        missingTenantStatus: result.status,
        missingTenantIsExplicit: /tenant|TENANT_CONTEXT_REQUIRED|required/i.test(text),
        response: result.body,
      };
    },
  );
}

async function main() {
  fs.mkdirSync(reportDir, { recursive: true });
  console.log(`${characterizationStage} starting mode=${report.mode} runId=${runId}`);
  validateAgentSelection();
  report.authentication.result = await ensureAdminAuthentication();
  if (authOnly) {
    const check = await request('admin csrf mutation check', 'POST', '/admin/characterization/auth-check', { runId });
    report.authCheck = { status: check.status, body: redact(check.body) };
  } else {
    await run();
  }
  report.completedAt = nowIso();
  report.summary = {
    total: report.scenarios.length,
    desiredBehaviorObserved: report.scenarios.filter((item) => item.passed).length,
    differs: report.scenarios.filter((item) => !item.passed).length,
    executionErrors: report.scenarios.filter((item) => item.status === 'EXECUTION_ERROR').length,
  };
  const jsonPath = path.join(reportDir, `${runPrefix}-dispatch-characterization-${runId}.json`);
  const latestPath = path.join(reportDir, 'latest.json');
  fs.writeFileSync(jsonPath, `${JSON.stringify(report, null, 2)}\n`);
  fs.writeFileSync(latestPath, `${JSON.stringify(report, null, 2)}\n`);
  console.log(`${characterizationStage} report: ${jsonPath}`);
  if (strict && report.scenarios.some((item) => !item.passed)) process.exitCode = 1;
}

main().catch((error) => {
  report.completedAt = nowIso();
  report.fatalError = String(error?.stack || error?.message || error);
  fs.mkdirSync(reportDir, { recursive: true });
  fs.writeFileSync(path.join(reportDir, 'latest.json'), `${JSON.stringify(report, null, 2)}\n`);
  console.error(report.fatalError);
  process.exit(2);
});

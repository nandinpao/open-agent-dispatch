#!/usr/bin/env node
/*
 * Lightweight TCP Agent simulator for ai-event-gateway-netty local/cluster tests.
 * Uses newline-delimited JSON over TCP to avoid extra WebSocket npm dependencies.
 *
 * Phase 3G-P1 turns the simulator into a small task worker, not just a connection
 * probe.  By default it ACKs TASK_DISPATCH, emits progress, sends RESULT, and
 * returns to IDLE.  Set AGENT_WORKER_MODE=observe to keep the old connect-only
 * behavior when you only want runtime registration/heartbeat tests.
 */
const net = require('node:net');
const crypto = require('node:crypto');
const os = require('node:os');
const fs = require('node:fs');
const path = require('node:path');

function env(name, fallback) {
  const value = process.env[name];
  return value === undefined || value === '' ? fallback : value;
}

function now() {
  return new Date().toISOString();
}

function messageId(prefix) {
  return `${prefix}-${crypto.randomUUID()}`;
}

function markerValue(value) {
  if (value === undefined || value === null) return '-';
  if (typeof value === 'object') return JSON.stringify(value);
  return String(value).replace(/\s+/g, '_');
}

function logMarker(marker, fields = {}) {
  const pairs = Object.entries(fields)
    .map(([key, value]) => `${key}=${markerValue(value)}`)
    .join(' ');
  console.log(`[${now()}] ${marker}${pairs ? ' ' + pairs : ''}`);
}

function intEnv(name, fallback) {
  const raw = env(name, String(fallback));
  const parsed = Number.parseInt(raw, 10);
  return Number.isFinite(parsed) ? parsed : fallback;
}

function boolEnv(name, fallback) {
  const raw = env(name, fallback ? 'true' : 'false').toLowerCase();
  return raw === '1' || raw === 'true' || raw === 'yes' || raw === 'y' || raw === 'on';
}

function csvEnv(name, fallback) {
  return env(name, fallback)
    .split(',')
    .map((item) => item.trim())
    .filter(Boolean);
}

function normalizeCapability(value) {
  return String(value || '')
    .trim()
    .replace(/[\s.-]+/g, '_')
    .toUpperCase();
}

function capabilityVariants(values) {
  const seen = new Set();
  const out = [];
  for (const value of values || []) {
    const text = String(value || '').trim();
    const normalized = normalizeCapability(text);
    for (const candidate of [normalized, text]) {
      if (candidate && !seen.has(candidate)) {
        seen.add(candidate);
        out.push(candidate);
      }
    }
  }
  return out;
}

// P3-W: simulator capability values are optional runtime observations only.
// Dispatch eligibility is based on Admin UI/Core-approved capabilities, service scope,
// runtime connection, runtime features and capacity.
const DEFAULT_CAPABILITIES = [];
const RUNTIME_FEATURES = ['TASK_ACK', 'TASK_PROGRESS', 'TASK_RESULT', 'CALLBACK_REPLAY'];

const mode = process.argv[2] || env('AGENT_CLIENT_MODE', 'run');
const host = env('GATEWAY_TCP_HOST', env('GATEWAY_HOST', '127.0.0.1'));
const port = intEnv('GATEWAY_TCP_PORT', 19090);
const agentId = env('AGENT_ID', `agent-local-${process.pid}`);
const target = env('GATEWAY_NODE_ID', 'gateway-node-001');
const token = env('AGENT_ONBOARDING_TOKEN', '');
const heartbeatIntervalMs = intEnv('AGENT_HEARTBEAT_INTERVAL_MS', 5000);
const agentType = env('AGENT_TYPE', 'OPENCLAW');
const legacyCapabilitiesEnabled = boolEnv('AGENT_LEGACY_CAPABILITIES_ENABLED', false);
const configuredCapabilities = csvEnv('OPENSOCKET_AGENT_CAPABILITIES', env('AGENT_CAPABILITIES', DEFAULT_CAPABILITIES.join(',')));
const capabilities = capabilityVariants(configuredCapabilities);
const maxConcurrentTasks = Math.max(1, intEnv('AGENT_MAX_CONCURRENT_TASKS', 3));
const pluginName = env('AGENT_PLUGIN_NAME', 'netty-tcp-agent-simulator');
const pluginVersion = env('AGENT_PLUGIN_VERSION', 'local');
const runSeconds = intEnv('AGENT_RUN_SECONDS', 0);
const oneShotType = process.argv[3] || env('AGENT_ONE_SHOT_TYPE', 'agent-heartbeat');
const taskId = process.argv[4] || env('TASK_ID', `task-${Date.now()}`);
const workerMode = env('AGENT_WORKER_MODE', boolEnv('AGENT_WORKER_ENABLED', true) ? 'process-result' : 'observe').toLowerCase();
const workerProcessingMs = intEnv('AGENT_WORKER_PROCESSING_MS', intEnv('AGENT_WORKER_RESULT_DELAY_MS', intEnv('AGENT_RESULT_DELAY_MS', 8000)));
const workerResultDelayMs = workerProcessingMs;
const workerProgressDelayMs = intEnv('AGENT_WORKER_PROGRESS_DELAY_MS', Math.max(500, Math.floor(workerProcessingMs / 4)));
const workerAckDelayMs = intEnv('AGENT_WORKER_ACK_DELAY_MS', 200);
const workerIdleDelayMs = intEnv('AGENT_WORKER_IDLE_DELAY_MS', 800);
const callbackReplayEnabled = boolEnv('AGENT_CALLBACK_REPLAY_ENABLED', true);
const callbackReplayOnConnect = boolEnv('AGENT_CALLBACK_REPLAY_ON_CONNECT', true);
const callbackReplayIntervalMs = intEnv('AGENT_CALLBACK_REPLAY_INTERVAL_MS', intEnv('AGENT_TERMINAL_CALLBACK_REPLAY_INTERVAL_MS', 15000));
const callbackReplayMaxAttempts = Math.max(0, intEnv('AGENT_CALLBACK_REPLAY_MAX_ATTEMPTS', intEnv('AGENT_TERMINAL_CALLBACK_REPLAY_MAX_ATTEMPTS', 0)));
const pendingCallbackStorePath = env('AGENT_PENDING_CALLBACK_STORE',
  path.join(os.tmpdir(), `opendispatch-agent-pending-callbacks-${agentId}.json`));

let socket;
let heartbeatTimer;
let callbackReplayTimer;
let shutdownTimer;
let buffer = '';
let registered = false;
let registerMessageId = null;
let postRegisterStarted = false;
const activeTasks = new Map();
const queuedTasks = [];
let pendingCallbackIndex = new Map();

function envelope(messageType, payload, eventType) {
  return {
    messageId: messageId('msg'),
    messageType,
    eventType: eventType || undefined,
    source: agentId,
    target,
    timestamp: now(),
    payload,
  };
}

function writeEnvelope(data) {
  const line = JSON.stringify(data);
  socket.write(line + '\n');
  console.log(`[${now()}] -> ${data.messageType} ${data.eventType || ''} ${data.messageId}`);
  if (['TASK_ACK', 'TASK_PROGRESS', 'TASK_RESULT', 'TASK_ERROR'].includes(String(data.messageType || '').toUpperCase())) {
    logMarker('agent_callback_frame_sent', {
      callbackType: data.messageType,
      taskId: data.payload?.taskId,
      dispatchRequestId: data.payload?.dispatchRequestId,
      assignmentId: data.payload?.assignmentId,
      callbackId: data.payload?.callbackId,
      idempotencyKey: data.payload?.idempotencyKey,
      messageId: data.messageId,
    });
  }
  return data;
}

function ensureParentDir(filePath) {
  fs.mkdirSync(path.dirname(filePath), { recursive: true });
}

function loadPendingCallbacks() {
  try {
    if (!fs.existsSync(pendingCallbackStorePath)) return [];
    const raw = fs.readFileSync(pendingCallbackStorePath, 'utf8');
    const parsed = JSON.parse(raw || '[]');
    return Array.isArray(parsed) ? parsed.filter((item) => item && typeof item === 'object') : [];
  } catch (error) {
    console.error(`[${now()}] failed to load pending callback store ${pendingCallbackStorePath}: ${error.message}`);
    return [];
  }
}

function savePendingCallbacks(entries) {
  try {
    ensureParentDir(pendingCallbackStorePath);
    fs.writeFileSync(pendingCallbackStorePath, JSON.stringify(entries, null, 2));
  } catch (error) {
    console.error(`[${now()}] failed to save pending callback store ${pendingCallbackStorePath}: ${error.message}`);
  }
}

function pendingKeyFromPayload(payload) {
  return firstNonBlank(payload?.idempotencyKey, payload?.callbackId, `${payload?.dispatchRequestId || 'dispatch'}:${payload?.taskId || 'task'}:${payload?.eventType || payload?.callbackType || 'callback'}`);
}

function rebuildPendingCallbackIndex() {
  pendingCallbackIndex = new Map();
  for (const item of loadPendingCallbacks()) {
    if (item.messageId) pendingCallbackIndex.set(item.messageId, item.key || pendingKeyFromPayload(item.envelope?.payload || {}));
  }
}

function rememberPendingCallback(callbackEnvelope, reason = 'created') {
  if (!callbackReplayEnabled) return callbackEnvelope;
  const payload = callbackEnvelope.payload || {};
  const key = pendingKeyFromPayload(payload);
  const current = loadPendingCallbacks().filter((item) => item.key !== key);
  const entry = {
    key,
    messageId: callbackEnvelope.messageId,
    callbackType: callbackEnvelope.messageType,
    taskId: payload.taskId,
    dispatchRequestId: payload.dispatchRequestId,
    idempotencyKey: payload.idempotencyKey,
    callbackId: payload.callbackId,
    attempts: 0,
    createdAt: now(),
    updatedAt: now(),
    reason,
    envelope: callbackEnvelope,
  };
  current.push(entry);
  savePendingCallbacks(current);
  pendingCallbackIndex.set(callbackEnvelope.messageId, key);
  console.log(`[${now()}] pending callback saved type=${entry.callbackType} taskId=${entry.taskId} key=${key} store=${pendingCallbackStorePath}`);
  logMarker('agent_pending_callback_saved', { callbackType: entry.callbackType, taskId: entry.taskId, dispatchRequestId: entry.dispatchRequestId, callbackId: entry.callbackId, key, store: pendingCallbackStorePath });
  return callbackEnvelope;
}

function isTerminalCallbackType(value) {
  const type = String(value || '').toUpperCase();
  return type === 'TASK_RESULT' || type === 'TASK_ERROR' || type === 'RESULT' || type === 'ERROR';
}

function explicitCoreAcceptedStatus(value) {
  const status = String(value || '').toUpperCase();
  return status === 'CALLBACK_CORE_ACCEPTED'
    || status === 'CORE_CALLBACK_ACCEPTED'
    || status === 'CALLBACK_ACCEPTED'
    || status === 'CORE_ACCEPTED'
    || status === 'CALLBACK_INBOX_ACCEPTED';
}

function provisionalRelayStatus(value) {
  const status = String(value || '').toUpperCase();
  return status === 'RELAY_QUEUED'
    || status === 'RELAY_SUBMITTED'
    || status === 'FORWARD_QUEUED'
    || status === 'ACCEPTED';
}

function removePendingCallback(messageId, key, status, message) {
  const next = loadPendingCallbacks().filter((item) => item.key !== key);
  savePendingCallbacks(next);
  pendingCallbackIndex.delete(messageId);
  console.log(`[${now()}] pending callback core-accepted and removed messageId=${messageId} key=${key} status=${status || '-'}`);
  logMarker('agent_pending_callback_core_accepted', { messageId, key, status, message, store: pendingCallbackStorePath });
  logMarker('agent_pending_callback_accepted', { messageId, key, status, message, store: pendingCallbackStorePath });
}

function retainPendingCallback(messageId, key, entry, status, message, reason) {
  const entries = loadPendingCallbacks();
  const next = entries.map((item) => {
    if (item.key !== key) return item;
    return {
      ...item,
      lastGatewayAckStatus: status || null,
      lastGatewayAckMessage: message || null,
      lastGatewayAckAt: now(),
      retainedReason: reason,
      updatedAt: now(),
    };
  });
  savePendingCallbacks(next);
  console.log(`[${now()}] pending callback retained messageId=${messageId} key=${key} type=${entry?.callbackType || '-'} status=${status || '-'} reason=${reason}`);
  logMarker('agent_pending_callback_retained', {
    messageId,
    key,
    callbackType: entry?.callbackType,
    taskId: entry?.taskId,
    dispatchRequestId: entry?.dispatchRequestId,
    status,
    reason,
    store: pendingCallbackStorePath,
  });
}

function handlePendingCallbackGatewayAck(messageId, messageType, status, message) {
  if (!messageId || !callbackReplayEnabled) return false;
  rebuildPendingCallbackIndex();
  const key = pendingCallbackIndex.get(messageId);
  if (!key) return false;
  const entry = loadPendingCallbacks().find((item) => item.key === key);
  const callbackType = entry?.callbackType || messageType;
  if (explicitCoreAcceptedStatus(status)) {
    removePendingCallback(messageId, key, status, message);
    return true;
  }
  if (isTerminalCallbackType(callbackType)) {
    const reason = provisionalRelayStatus(status)
      ? 'terminal_callback_waiting_for_core_acceptance'
      : 'terminal_callback_gateway_not_core_accepted';
    retainPendingCallback(messageId, key, entry, status, message, reason);
    return true;
  }
  // Non-terminal callbacks are best-effort telemetry in this simulator. They are
  // safe to remove once Netty has accepted or queued the relay, but terminal
  // callbacks must remain durable until Core acceptance is explicit.
  removePendingCallback(messageId, key, status || 'NON_TERMINAL_RELAY_ACCEPTED', message);
  return true;
}

function withCallbackIdentity(context, callbackType, sequenceNoOverride = null) {
  const dispatchRequestId = context.dispatchRequestId || context.assignmentId || context.taskId;
  const taskId = context.taskId;
  const sequenceNo = sequenceNoOverride || (callbackType === 'TASK_ACK' ? 1 : callbackType === 'TASK_PROGRESS' ? 2 : 99);
  const idempotencyKey = `${agentId}:${dispatchRequestId}:${taskId}:${callbackType}:${sequenceNo}`;
  return {
    callbackId: `cb-${crypto.createHash('sha256').update(idempotencyKey).digest('hex').slice(0, 24)}`,
    callbackType,
    sequenceNo,
    idempotencyKey,
    replay: false,
  };
}

function replayPendingCallbacks(reason = 'connect') {
  if (!callbackReplayEnabled) return;
  if (!socket || socket.destroyed || !registered) return;
  const entries = loadPendingCallbacks();
  if (entries.length === 0) return;
  const replayable = [];
  const retained = [];
  for (const entry of entries) {
    const attempts = Number(entry.attempts || 0);
    if (callbackReplayMaxAttempts > 0 && attempts >= callbackReplayMaxAttempts) {
      retained.push({
        ...entry,
        updatedAt: now(),
        retainedReason: 'max_replay_attempts_reached',
      });
      logMarker('agent_pending_callback_replay_max_attempts_reached', {
        key: entry.key,
        callbackType: entry.callbackType,
        taskId: entry.taskId,
        dispatchRequestId: entry.dispatchRequestId,
        attempts,
        maxAttempts: callbackReplayMaxAttempts,
      });
      continue;
    }
    replayable.push(entry);
  }
  if (replayable.length === 0) {
    if (retained.length > 0) savePendingCallbacks(retained);
    return;
  }
  console.log(`[${now()}] replaying ${replayable.length} pending callback(s) from ${pendingCallbackStorePath}; reason=${reason}`);
  logMarker('agent_pending_callback_replay_started', { count: replayable.length, reason, store: pendingCallbackStorePath });
  const nextEntries = [...retained];
  for (const entry of replayable) {
    const original = entry.envelope;
    if (!original || !original.payload || !original.messageType) continue;
    const replayEnvelope = {
      ...original,
      messageId: messageId('replay'),
      timestamp: now(),
      payload: {
        ...original.payload,
        replay: true,
        replayDetected: true,
        replayAttempt: Number(entry.attempts || 0) + 1,
        replayedAt: now(),
        replayReason: reason,
        originalMessageId: original.messageId,
      },
    };
    const replayed = writeEnvelope(replayEnvelope);
    nextEntries.push({
      ...entry,
      messageId: replayed.messageId,
      attempts: Number(entry.attempts || 0) + 1,
      updatedAt: now(),
      lastReplayAt: now(),
      lastReplayReason: reason,
      envelope: replayed,
    });
    pendingCallbackIndex.set(replayed.messageId, entry.key);
    logMarker('agent_pending_callback_replayed', {
      key: entry.key,
      callbackType: entry.callbackType,
      taskId: entry.taskId,
      dispatchRequestId: entry.dispatchRequestId,
      messageId: replayed.messageId,
      attempt: Number(entry.attempts || 0) + 1,
      reason,
    });
  }
  savePendingCallbacks(nextEntries);
}

function capabilityRevision() {
  const capabilityToken = capabilities.length > 0 ? capabilities.join('-') : 'none';
  return `sim-capability-${capabilityToken}-${RUNTIME_FEATURES.join('-')}-${maxConcurrentTasks}`;
}

function runtimeLoad(status) {
  const activeTaskCount = activeTasks.size;
  return {
    activeTasks: activeTaskCount,
    maxConcurrentTasks,
    availableSlots: Math.max(0, maxConcurrentTasks - activeTaskCount),
    capacityUtilization: maxConcurrentTasks <= 0 ? 0 : activeTaskCount / maxConcurrentTasks,
    queuedTasks: queuedTasks.length,
    outboxPending: loadPendingCallbacks().length,
    outboxInFlight: 0,
    recoveryPendingAssignments: 0,
    draining: false,
  };
}

function capabilityProfile() {
  return {
    revision: capabilityRevision(),
    supportedCapabilities: capabilities,
    supportedTaskTypes: capabilities,
    runtimeCapabilities: capabilities,
    runtimeFeatures: RUNTIME_FEATURES,
    maxConcurrentTasks,
    workerMode,
  };
}

function register() {
  const metadata = {
    host: env('AGENT_HOSTNAME', os.hostname()),
    node: target,
    pid: String(process.pid),
    maxConcurrentTasks: String(maxConcurrentTasks),
    workerMode,
    workerEnabled: String(workerMode !== 'observe'),
    workerProcessingMs: String(workerProcessingMs),
    runtimeFeatures: RUNTIME_FEATURES.join(','),
    legacyCapabilitiesEnabled: String(legacyCapabilitiesEnabled),
    callbackReplayEnabled: String(callbackReplayEnabled),
    callbackReplayIntervalMs: String(callbackReplayIntervalMs),
    callbackReplayMaxAttempts: String(callbackReplayMaxAttempts),
    callbackStorePath: pendingCallbackStorePath,
    simulator: 'netty-tcp-agent-client.js',
  };
  if (token) {
    metadata.onboardingToken = token;
    metadata.agentToken = token;
  }
  const registerEnvelope = writeEnvelope(envelope('AGENT_REGISTER', {
    agentId,
    agentType,
    connectionType: 'TCP',
    capabilities,
    runtimeFeatures: RUNTIME_FEATURES,
    capabilityProfile: capabilityProfile(),
    metadata,
    onboardingToken: token || undefined,
  }, 'ai.agent.registered'));
  registerMessageId = registerEnvelope.messageId;
}

function currentTaskIdValue() {
  return Array.from(activeTasks.keys())[0] || null;
}

function agentStatus() {
  return activeTasks.size > 0 ? 'BUSY' : 'IDLE';
}

function heartbeat(status = agentStatus(), currentTaskId = currentTaskIdValue()) {
  const heartbeatAt = now();
  writeEnvelope(envelope('AGENT_HEARTBEAT', {
    agentId,
    status,
    currentTaskId,
    activeTaskIds: Array.from(activeTasks.keys()),
    queuedTaskIds: queuedTasks.map((item) => item.taskId),
    heartbeatAt,
    sentAt: heartbeatAt,
    runtimeLoad: runtimeLoad(status),
    capabilityRevision: capabilityRevision(),
    capabilityProfile: capabilityProfile(),
    plugin: {
      name: pluginName,
      version: pluginVersion,
    },
  }, 'ai.agent.heartbeat'));
}

function taskRequested() {
  writeEnvelope(envelope('TASK_SUBMIT', {
    taskId,
    requestedBy: agentId,
    priority: Number.parseInt(env('TASK_PRIORITY', '1'), 10),
    taskType: env('TASK_TYPE', 'demo.task'),
    payload: {
      demo: true,
      source: 'cluster-send-event.sh',
      createdAt: now(),
    },
  }, 'ai.task.requested'));
}

function pickPayload(incoming) {
  if (!incoming || typeof incoming !== 'object') return {};
  return incoming.payload && typeof incoming.payload === 'object' ? incoming.payload : {};
}

function firstNonBlank(...values) {
  for (const value of values) {
    if (value !== undefined && value !== null && String(value).trim() !== '') {
      return String(value).trim();
    }
  }
  return null;
}

function positiveInt(...values) {
  for (const value of values) {
    const parsed = Number.parseInt(String(value ?? ''), 10);
    if (Number.isFinite(parsed) && parsed > 0) return parsed;
  }
  return 1;
}

function isTaskAssignment(incoming) {
  const messageType = String(incoming?.messageType || '').toUpperCase();
  const eventType = String(incoming?.eventType || incoming?.event || '').toLowerCase();
  return ['TASK_DISPATCH', 'TASK_ASSIGN', 'TASK_ASSIGNED', 'TASK_COMMAND', 'COMMAND'].includes(messageType)
    || ['agent.task.assigned', 'ai.task.assigned', 'ai.task.dispatch', 'task.assign', 'task.dispatch'].includes(eventType);
}

function extractTaskContext(incoming) {
  const payload = pickPayload(incoming);
  const routing = payload.routing && typeof payload.routing === 'object' ? payload.routing : {};
  const gateway = payload.gateway && typeof payload.gateway === 'object' ? payload.gateway : {};
  const input = payload.input && typeof payload.input === 'object' ? payload.input : (payload.payload && typeof payload.payload === 'object' ? payload.payload : payload);
  const incomingTaskId = firstNonBlank(payload.taskId, payload.id, incoming?.messageId, incoming?.id, taskId);
  return {
    taskId: incomingTaskId,
    agentId,
    dispatchRequestId: firstNonBlank(payload.dispatchRequestId, payload.dispatchId, payload.requestId, payload.assignmentId, incoming?.id, incoming?.messageId),
    assignmentId: firstNonBlank(payload.assignmentId, payload.dispatchRequestId, payload.requestId, incoming?.id, incoming?.messageId),
    attemptNo: positiveInt(payload.attemptNo, payload.attempt),
    dispatchToken: firstNonBlank(payload.dispatchToken, payload.fencingToken, payload.assignmentFencingToken, `local-sim-token-${incomingTaskId}`),
    fencingToken: firstNonBlank(payload.fencingToken, payload.assignmentFencingToken, payload.dispatchToken),
    ownerGatewayNodeId: firstNonBlank(payload.ownerGatewayNodeId, routing.ownerNodeId, gateway.gatewayNodeId, target),
    agentSessionId: firstNonBlank(payload.agentSessionId, gateway.sessionId, routing.sessionLeaseId),
    taskType: firstNonBlank(payload.taskType, payload.type, 'demo.command'),
    externalExecutionKey: firstNonBlank(payload.externalExecutionKey, input?.externalExecutionKey),
    input,
    commandId: firstNonBlank(incoming?.messageId, incoming?.id),
  };
}

function ackPayload(context) {
  return {
    taskId: context.taskId,
    agentId,
    dispatchRequestId: context.dispatchRequestId,
    assignmentId: context.assignmentId,
    attemptNo: context.attemptNo,
    dispatchToken: context.dispatchToken,
    fencingToken: context.fencingToken || undefined,
    ownerGatewayNodeId: context.ownerGatewayNodeId,
    agentSessionId: context.agentSessionId || undefined,
    externalExecutionKey: context.externalExecutionKey || undefined,
    accepted: true,
    message: 'Task command accepted by simulated TCP worker agent',
    occurredAt: now(),
  };
}

function taskAck(context) {
  logMarker('agent_task_ack_callback_started', { taskId: context.taskId, dispatchRequestId: context.dispatchRequestId, assignmentId: context.assignmentId, attemptNo: context.attemptNo });
  writeEnvelope(envelope('TASK_ACK', {
    ...ackPayload(context),
    ...withCallbackIdentity(context, 'TASK_ACK'),
  }, 'ai.task.ack'));
}

function taskProgress(context, percent) {
  logMarker('agent_task_progress_callback_started', { taskId: context.taskId, dispatchRequestId: context.dispatchRequestId, assignmentId: context.assignmentId, attemptNo: context.attemptNo, progressPercent: percent });
  writeEnvelope(envelope('TASK_PROGRESS', {
    ...ackPayload(context),
    ...withCallbackIdentity(context, 'TASK_PROGRESS', Math.max(2, percent)),
    progressPercent: percent,
    progress: percent,
    message: `Simulated worker progress ${percent}%`,
    reportedAt: now(),
    occurredAt: now(),
  }, 'ai.task.progress'));
}

function resultSummary(context) {
  const type = normalizeCapability(context.taskType || '');
  if (type.includes('MES') || type.includes('ALARM')) {
    return '模擬 Agent 已完成 MES 設備告警分析，建議檢查溫度趨勢、感測器讀值、冷卻系統與近期相似告警。';
  }
  if (type.includes('ERP') || type.includes('PURCHASE')) {
    return '模擬 Agent 已完成 ERP 採購單審核，建議檢查供應商、金額級距、核准流程與歷史異常。';
  }
  if (type.includes('HR') || type.includes('PAYROLL')) {
    return '模擬 Agent 已完成 HR 薪資/出勤異常分析，建議比對出勤、請假、加班與薪資規則。';
  }
  return '模擬 Agent 已完成一般事件分析，建議檢查事件來源、最近相似事件與後續人工處理紀錄。';
}

function taskResult(context) {
  logMarker('agent_task_result_callback_started', { taskId: context.taskId, dispatchRequestId: context.dispatchRequestId, assignmentId: context.assignmentId, attemptNo: context.attemptNo, workerMode });
  const callbackEnvelope = envelope('TASK_RESULT', {
    ...ackPayload(context),
    ...withCallbackIdentity(context, 'TASK_RESULT'),
    status: 'SUCCEEDED',
    resultStatus: 'SUCCESS',
    message: 'Simulated worker completed task',
    result: {
      simulated: true,
      workerMode,
      commandId: context.commandId || null,
      taskType: context.taskType,
      summary: resultSummary(context),
      analysis: '此結果由本機 TCP worker simulator 產生，用於驗證 Core → Netty → Agent → callback relay → Core task completion。正式 Agent 應回傳實際診斷內容、證據與建議。',
      findings: [
        '已接收 Netty delivery API 送出的 TASK_DISPATCH / task.assign。',
        '已保留 dispatch context，callback 可回寫 Core。',
        '已模擬 ACK、PROGRESS 與 RESULT callback。',
      ],
      recommendations: [
        '若 Task 長時間停在等待 callback，請確認 AGENT_WORKER_MODE 是否為 process-result；若要停在處理中請使用 work-only。',
        '若 Core 拒絕 callback，請檢查 dispatchRequestId、assignmentId、attemptNo、dispatchToken。',
        '若 Issue Adapter 已啟用，請確認 Agent Result 是否寫入 issue history。',
      ],
      evidence: [
        { type: 'dispatchCommand', commandId: context.commandId || null },
        { type: 'simulator', agentId, workerMode },
      ],
      input: context.input,
    },
    completedAt: now(),
    occurredAt: now(),
  }, 'ai.task.result');
  rememberPendingCallback(callbackEnvelope, 'terminal-result');
  writeEnvelope(callbackEnvelope);
}

function taskError(context) {
  logMarker('agent_task_error_callback_started', { taskId: context.taskId, dispatchRequestId: context.dispatchRequestId, assignmentId: context.assignmentId, attemptNo: context.attemptNo, workerMode });
  const callbackEnvelope = envelope('TASK_ERROR', {
    ...ackPayload(context),
    ...withCallbackIdentity(context, 'TASK_ERROR'),
    resultStatus: 'FAILED',
    errorCode: 'SIMULATED_WORKER_ERROR',
    errorMessage: 'Simulated worker error requested by AGENT_WORKER_MODE=error',
    message: 'Simulated worker failed task',
    occurredAt: now(),
  }, 'ai.task.error');
  rememberPendingCallback(callbackEnvelope, 'terminal-error');
  writeEnvelope(callbackEnvelope);
}

function taskAlreadyKnown(taskIdValue) {
  return activeTasks.has(taskIdValue) || queuedTasks.some((item) => item.taskId === taskIdValue);
}

function dispatchNextQueuedTask() {
  while (queuedTasks.length > 0 && activeTasks.size < maxConcurrentTasks) {
    const next = queuedTasks.shift();
    console.log(`[${now()}] dequeued task taskId=${next.taskId}; active=${activeTasks.size}/${maxConcurrentTasks}; queued=${queuedTasks.length}`);
    startTaskProcessing(next);
  }
}

function finishTask(context) {
  activeTasks.delete(context.taskId);
  console.log(`[${now()}] worker finished simulated processing taskId=${context.taskId}; active=${activeTasks.size}/${maxConcurrentTasks}; queued=${queuedTasks.length}`);
  logMarker('agent_task_worker_completed', { taskId: context.taskId, dispatchRequestId: context.dispatchRequestId, assignmentId: context.assignmentId, active: activeTasks.size, queued: queuedTasks.length });
  heartbeat(agentStatus(), currentTaskIdValue());
  dispatchNextQueuedTask();
}

function startTaskProcessing(context) {
  activeTasks.set(context.taskId, context);
  console.log(`[${now()}] worker started task taskId=${context.taskId} mode=${workerMode} processingMs=${workerProcessingMs} active=${activeTasks.size}/${maxConcurrentTasks}`);
  logMarker('agent_task_worker_started', { taskId: context.taskId, dispatchRequestId: context.dispatchRequestId, assignmentId: context.assignmentId, workerMode, processingMs: workerProcessingMs, active: activeTasks.size, maxConcurrentTasks });
  heartbeat('BUSY', context.taskId);

  if (workerMode === 'observe' || workerMode === 'idle') {
    console.log(`[${now()}] worker is observing only; no ACK/PROGRESS/RESULT will be sent. Set AGENT_WORKER_MODE=process-result to execute tasks.`);
    logMarker('agent_task_worker_observe_only', { taskId: context.taskId, workerMode });
    setTimeout(() => finishTask(context), workerIdleDelayMs);
    return;
  }

  setTimeout(() => taskAck(context), workerAckDelayMs);
  if (workerMode === 'ack-only') {
    setTimeout(() => finishTask(context), workerIdleDelayMs);
    return;
  }

  const progressSteps = [25, 50, 75];
  progressSteps.forEach((percent, index) => {
    setTimeout(() => taskProgress(context, percent), workerProgressDelayMs * (index + 1));
  });

  if (workerMode === 'work-only' || workerMode === 'ack-progress') {
    console.log(`[${now()}] workerMode=${workerMode}; terminal RESULT/ERROR will not be sent. Use this mode to keep the task in PROCESSING for manual callback tests.`);
    logMarker('agent_task_terminal_callback_suppressed', { taskId: context.taskId, dispatchRequestId: context.dispatchRequestId, workerMode });
    return;
  }

  const terminalDelay = Math.max(workerAckDelayMs + 250, workerResultDelayMs);
  if (workerMode === 'error') {
    setTimeout(() => taskError(context), terminalDelay);
  } else {
    setTimeout(() => taskResult(context), terminalDelay);
  }
  setTimeout(() => finishTask(context), terminalDelay + 250);
}

function handleTaskAssignment(incoming) {
  const context = extractTaskContext(incoming);
  console.log(`[${now()}] worker received task taskId=${context.taskId} mode=${workerMode} dispatchRequestId=${context.dispatchRequestId}`);
  logMarker('agent_task_dispatch_received', { taskId: context.taskId, dispatchRequestId: context.dispatchRequestId, assignmentId: context.assignmentId, attemptNo: context.attemptNo, agentId, workerMode, commandId: context.commandId });

  if (taskAlreadyKnown(context.taskId)) {
    console.log(`[${now()}] duplicate task command ignored taskId=${context.taskId}; active=${activeTasks.size}; queued=${queuedTasks.length}`);
    logMarker('agent_task_dispatch_duplicate_ignored', { taskId: context.taskId, dispatchRequestId: context.dispatchRequestId, active: activeTasks.size, queued: queuedTasks.length });
    heartbeat(agentStatus(), currentTaskIdValue());
    return;
  }

  if (workerMode === 'observe' || workerMode === 'idle') {
    startTaskProcessing(context);
    return;
  }

  if (activeTasks.size >= maxConcurrentTasks) {
    queuedTasks.push(context);
    console.log(`[${now()}] worker queued task taskId=${context.taskId}; active=${activeTasks.size}/${maxConcurrentTasks}; queued=${queuedTasks.length}`);
    logMarker('agent_task_worker_queued', { taskId: context.taskId, dispatchRequestId: context.dispatchRequestId, active: activeTasks.size, maxConcurrentTasks, queued: queuedTasks.length });
    heartbeat('BUSY', currentTaskIdValue());
    return;
  }

  startTaskProcessing(context);
}

function handleLine(line) {
  if (!line.trim()) return;
  console.log(`[${now()}] <- ${line}`);
  let incoming;
  try {
    incoming = JSON.parse(line);
  } catch {
    return;
  }
  if (!registered && incoming.messageType === 'ERROR') {
    const code = incoming?.payload?.code || 'REGISTER_FAILED';
    const detail = incoming?.payload?.detail || incoming?.payload?.message || line;
    console.error(`[${now()}] AGENT_REGISTER rejected for ${agentId}: ${code} ${detail}`);
    process.exitCode = 1;
    if (socket) socket.end();
    return;
  }
  if (incoming.messageType === 'GATEWAY_ACK') {
    const ackedMessageId = incoming?.payload?.messageId;
    const ackedMessageType = incoming?.payload?.messageType;
    const status = incoming?.payload?.status;
    logMarker('agent_gateway_ack_received', { messageId: ackedMessageId, messageType: ackedMessageType, status, message: incoming?.payload?.message });
    if (registered && ackedMessageId && handlePendingCallbackGatewayAck(ackedMessageId, ackedMessageType, status, incoming?.payload?.message)) {
      return;
    }
  }
  if (!registered && incoming.messageType === 'GATEWAY_ACK') {
    const ackedMessageId = incoming?.payload?.messageId;
    const ackedMessageType = incoming?.payload?.messageType;
    const status = incoming?.payload?.status;
    if (ackedMessageId === registerMessageId || ackedMessageType === 'AGENT_REGISTER') {
      if (status && String(status).startsWith('REJECTED')) {
        console.error(`[${now()}] AGENT_REGISTER rejected for ${agentId}: ${status} ${incoming?.payload?.message || ''}`);
        process.exitCode = 1;
        if (socket) socket.end();
        return;
      }
      registered = true;
      console.log(`[${now()}] AGENT_REGISTER accepted for ${agentId}; workerMode=${workerMode}; starting post-register traffic`);
      logMarker('agent_register_accepted', { agentId, workerMode, maxConcurrentTasks, callbackReplayEnabled, store: pendingCallbackStorePath });
      startPostRegisterTraffic();
      return;
    }
  }
  if (isTaskAssignment(incoming)) {
    handleTaskAssignment(incoming);
  }
}

function startPostRegisterTraffic() {
  if (postRegisterStarted) return;
  postRegisterStarted = true;
  if (callbackReplayOnConnect) replayPendingCallbacks('connect');
  if (callbackReplayEnabled && callbackReplayIntervalMs > 0) {
    callbackReplayTimer = setInterval(() => replayPendingCallbacks('interval'), callbackReplayIntervalMs);
  }
  if (mode === 'send') {
    if (oneShotType === 'task-requested') {
      setTimeout(taskRequested, 100);
    } else if (oneShotType === 'agent-heartbeat') {
      setTimeout(() => heartbeat('IDLE', null), 100);
    } else {
      console.error(`Unsupported one-shot type: ${oneShotType}`);
      process.exitCode = 2;
    }
    setTimeout(() => socket.end(), intEnv('AGENT_ONE_SHOT_EXIT_DELAY_MS', 800));
    return;
  }
  heartbeatTimer = setInterval(() => heartbeat(agentStatus(), currentTaskIdValue()), heartbeatIntervalMs);
  heartbeat(agentStatus(), currentTaskIdValue());
  if (runSeconds > 0) {
    shutdownTimer = setTimeout(() => {
      console.log(`[${now()}] runSeconds reached; closing ${agentId}`);
      socket.end();
    }, runSeconds * 1000);
  }
}

function connect() {
  socket = net.createConnection({ host, port }, () => {
    console.log(`[${now()}] connected to ${host}:${port} as ${agentId}; runtimeFeatures=${RUNTIME_FEATURES.join(',')}; capabilities=${capabilities.join(',') || 'NONE'}; legacyCapabilitiesFlag=${legacyCapabilitiesEnabled}; workerMode=${workerMode}; processingMs=${workerProcessingMs}; maxConcurrentTasks=${maxConcurrentTasks}; callbackReplay=${callbackReplayEnabled}; replayIntervalMs=${callbackReplayIntervalMs}; replayMaxAttempts=${callbackReplayMaxAttempts}; store=${pendingCallbackStorePath}`);
    logMarker('agent_runtime_connected', { agentId, host, port, target, workerMode, processingMs: workerProcessingMs, maxConcurrentTasks, callbackReplayEnabled, callbackReplayIntervalMs, callbackReplayMaxAttempts, store: pendingCallbackStorePath });
    register();
  });

  socket.setEncoding('utf8');
  socket.on('data', (chunk) => {
    buffer += chunk;
    let idx;
    while ((idx = buffer.indexOf('\n')) >= 0) {
      const line = buffer.slice(0, idx);
      buffer = buffer.slice(idx + 1);
      handleLine(line);
    }
  });
  socket.on('error', (error) => {
    console.error(`[${now()}] socket error: ${error.message}`);
    logMarker('agent_runtime_socket_error', { agentId, error: error.message });
    process.exitCode = 1;
  });
  socket.on('close', () => {
    if (heartbeatTimer) clearInterval(heartbeatTimer);
    if (callbackReplayTimer) clearInterval(callbackReplayTimer);
    if (shutdownTimer) clearTimeout(shutdownTimer);
    console.log(`[${now()}] disconnected ${agentId}; registered=${registered}`);
    logMarker('agent_runtime_disconnected', { agentId, registered });
  });
}

process.on('SIGTERM', () => socket ? socket.end() : process.exit(0));
process.on('SIGINT', () => socket ? socket.end() : process.exit(0));
connect();

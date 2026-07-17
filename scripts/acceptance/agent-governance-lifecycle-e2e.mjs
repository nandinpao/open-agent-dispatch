#!/usr/bin/env node
/**
 * P26 Agent Governance runtime lifecycle E2E.
 *
 * Against an already-started local/CI stack, this script creates/approves a
 * unique Core Agent profile, starts a real Netty TCP simulator, verifies the
 * Agent appears in Netty runtime, then disables/revokes the Core Agent profile,
 * verifies Core-triggered runtime disconnect removes the live Netty session,
 * and confirms revoked agents cannot reconnect with the same credential.
 */
import { spawn } from 'node:child_process';
import { once } from 'node:events';
import { existsSync } from 'node:fs';
import path from 'node:path';
import { fileURLToPath } from 'node:url';

const __dirname = path.dirname(fileURLToPath(import.meta.url));
const rootDir = path.resolve(__dirname, '../..');
const timeoutMs = Number(process.env.P26_AGENT_GOVERNANCE_TIMEOUT_MS || process.env.SMOKE_TIMEOUT_MS || 120000);
const pollMs = Number(process.env.P26_AGENT_GOVERNANCE_POLL_MS || 2000);
const coreOrigin = origin(process.env.CORE_URL || process.env.CORE_BASE_URL || 'http://127.0.0.1:18080');
const nettyOrigin = origin(process.env.NETTY_URL || process.env.NETTY_ADMIN_BASE_URL || 'http://127.0.0.1:18081');
const gatewayHost = process.env.GATEWAY_TCP_HOST || process.env.NETTY_TCP_HOST || '127.0.0.1';
const gatewayPort = process.env.GATEWAY_TCP_PORT || process.env.NETTY_TCP_PORT || '19090';
const gatewayNodeId = process.env.P26_GATEWAY_NODE_ID || process.env.I6_GATEWAY_NODE_ID || 'gateway-node-001';
const token = process.env.AGENT_ONBOARDING_TOKEN || 'local-ci-agent-onboarding-token-change-me';
const operatorId = process.env.P26_OPERATOR_ID || 'p26-runtime-lifecycle-e2e';
const tenantId = process.env.P26_AGENT_TENANT_ID || process.env.I6_AGENT_TENANT_ID || 'tenant-a';
const agentId = process.env.P26_AGENT_ID || `agent-p26-governance-${Date.now()}`;
const dryRun = flag('P26_DRY_RUN') || process.argv.includes('--dry-run');
const clientScript = process.env.P26_NETTY_AGENT_CLIENT || path.join(rootDir, 'ai-event-gateway-netty/scripts/netty-tcp-agent-client.js');

function origin(value) {
  return String(value || '').replace(/\/+$/, '');
}

function flag(name) {
  const value = process.env[name];
  return value !== undefined && ['1', 'true', 'yes', 'on'].includes(value.toLowerCase());
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
  if (!isStandardEnvelope(body)) return body;
  if (body.code !== 'OK') {
    const error = new Error(`${name}: expected envelope code=OK but got code=${body.code} message=${body.message}`);
    error.code = body.code;
    error.envelope = body;
    throw error;
  }
  return body.data;
}

function join(originValue, requestPath) {
  return `${originValue}${requestPath.startsWith('/') ? requestPath : `/${requestPath}`}`;
}

async function request(name, method, url, body) {
  const headers = { Accept: 'application/json' };
  if (body !== undefined) headers['Content-Type'] = 'application/json';
  const response = await fetch(url, {
    method,
    headers,
    body: body === undefined ? undefined : JSON.stringify(body),
    cache: 'no-store'
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
    throw new Error(`${name}: expected HTTP 200 but got HTTP ${response.status}; body=${text.slice(0, 400)}`);
  }
  return unwrapEnvelope(name, parsed);
}

async function optionalRequest(name, method, url, body) {
  try {
    return await request(name, method, url, body);
  } catch (error) {
    if (['CORE_AGENT_NOT_FOUND', 'NOT_FOUND'].includes(error.code)) return null;
    const message = String(error.message || '');
    if (message.includes('Agent profile not found') || message.includes('not found')) return null;
    throw error;
  }
}

function approvalBody(reason) {
  return {
    agentId,
    approvedBy: operatorId,
    tenantId,
    agentName: agentId,
    agentType: 'OPENCLAW',
    ownerTeam: 'p26-runtime-e2e',
    description: 'P26 runtime governance lifecycle E2E agent',
    comment: reason,
    capabilities: ['GENERAL_AGENT', 'incident-analysis', 'task-execution'],
    scopes: [{ tenantId, systemCode: '*', taskType: '*', enabled: true }],
    credentialType: 'TOKEN',
    credentialToken: token
  };
}

async function ensureApprovedProfile() {
  const existing = await optionalRequest('get existing Core Agent profile', 'GET', join(coreOrigin, `/admin/agents/${encodeURIComponent(agentId)}`));
  if (existing) {
    await request('approve existing Core Agent profile', 'POST', join(coreOrigin, `/admin/agents/${encodeURIComponent(agentId)}/approve`), approvalBody('P26 restored existing profile before lifecycle E2E.'));
    await request('enable existing Core Agent profile', 'POST', join(coreOrigin, `/admin/agents/${encodeURIComponent(agentId)}/enable`), { operatorId, reason: 'P26 lifecycle E2E setup' });
  } else {
    const enrollment = await request('create P26 Agent enrollment', 'POST', join(coreOrigin, '/admin/agent-enrollments'), {
      claimedAgentId: agentId,
      tenantId,
      agentName: agentId,
      agentType: 'OPENCLAW',
      submittedMetadata: { source: 'P26 runtime governance lifecycle E2E', gatewayNodeId },
      evidence: { generatedBy: 'scripts/acceptance/agent-governance-lifecycle-e2e.mjs' }
    });
    const enrollmentId = enrollment?.enrollmentId;
    if (!enrollmentId) throw new Error(`Core did not return enrollmentId: ${JSON.stringify(enrollment)}`);
    await request('approve P26 Agent enrollment', 'POST', join(coreOrigin, `/admin/agent-enrollments/${encodeURIComponent(enrollmentId)}/approve`), approvalBody('P26 approved Agent profile for lifecycle E2E.'));
  }
  await request('issue P26 Agent credential', 'POST', join(coreOrigin, `/admin/agents/${encodeURIComponent(agentId)}/credentials/issue`), {
    operatorId,
    reason: 'P26 lifecycle E2E credential sync',
    credentialType: 'TOKEN',
    credentialToken: token,
    revokeExisting: true
  });
}

function startAgent() {
  if (!existsSync(clientScript)) throw new Error(`Netty TCP Agent client missing: ${clientScript}`);
  const child = spawn(process.execPath, [clientScript, 'run'], {
    stdio: ['ignore', 'pipe', 'pipe'],
    env: {
      ...process.env,
      AGENT_ID: agentId,
      GATEWAY_NODE_ID: gatewayNodeId,
      GATEWAY_TCP_HOST: gatewayHost,
      GATEWAY_TCP_PORT: String(gatewayPort),
      AGENT_ONBOARDING_TOKEN: token,
      AGENT_HEARTBEAT_INTERVAL_MS: '1000',
      AGENT_RUN_SECONDS: '120',
      AGENT_CAPABILITIES: 'GENERAL_AGENT,incident-analysis,task-execution'
    }
  });
  child.stdout.on('data', (chunk) => process.stdout.write(`[p26-agent:${agentId}] ${chunk}`));
  child.stderr.on('data', (chunk) => process.stderr.write(`[p26-agent:${agentId}] ${chunk}`));
  return child;
}

async function waitForExit(child, timeout = 15000) {
  const exit = once(child, 'exit').then(([code, signal]) => ({ code, signal }));
  const timer = new Promise((resolve) => setTimeout(() => resolve({ timeout: true }), timeout));
  return Promise.race([exit, timer]);
}

async function stopAgent(child) {
  if (!child || child.killed) return;
  child.kill('SIGTERM');
  const result = await waitForExit(child, 3000);
  if (result.timeout && !child.killed) child.kill('SIGKILL');
}

async function expectRegistrationRejected(reason) {
  const rejected = startAgent();
  const result = await waitForExit(rejected, 15000);
  if (result.timeout) {
    if (!rejected.killed) rejected.kill('SIGTERM');
    throw new Error(`${reason}: revoked/non-connectable Agent stayed connected; registration should have been rejected.`);
  }
  if (result.code === 0) {
    throw new Error(`${reason}: expected rejected Agent client to exit with non-zero status, got code=0 signal=${result.signal || ''}`);
  }
  console.log(`[p26-governance] ${reason} rejected reconnect verified exitCode=${result.code} signal=${result.signal || ''}`);
}

function agentsFromRuntime(data) {
  if (Array.isArray(data)) return data;
  if (Array.isArray(data?.agents)) return data.agents;
  if (Array.isArray(data?.data?.agents)) return data.data.agents;
  return [];
}

async function findRuntimeAgent() {
  const data = await request('Netty runtime agents', 'GET', join(nettyOrigin, '/api/admin/runtime/agents'));
  return agentsFromRuntime(data).find((agent) => String(agent.agentId) === agentId) || null;
}

async function waitFor(description, fn, predicate = Boolean) {
  const deadline = Date.now() + timeoutMs;
  let lastError = null;
  while (Date.now() < deadline) {
    try {
      const value = await fn();
      if (predicate(value)) return value;
    } catch (error) {
      lastError = error;
    }
    await new Promise((resolve) => setTimeout(resolve, pollMs));
  }
  throw new Error(`Timed out waiting for ${description}${lastError ? `; lastError=${lastError.message}` : ''}`);
}

async function main() {
  console.log(`[p26-governance] core=${coreOrigin} netty=${nettyOrigin} tcp=${gatewayHost}:${gatewayPort} agent=${agentId}`);
  if (dryRun) {
    if (!existsSync(clientScript)) throw new Error(`Netty TCP Agent client missing: ${clientScript}`);
    console.log('[p26-governance] dry-run passed');
    return;
  }

  await ensureApprovedProfile();
  const child = startAgent();
  try {
    const runtimeAgent = await waitFor('Agent to appear as connected in Netty runtime', findRuntimeAgent, (agent) => agent && agent.transportStatus === 'CONNECTED');
    console.log(`[p26-governance] connected agent verified transport=${runtimeAgent.transportStatus} freshness=${runtimeAgent.freshnessStatus}`);

    const disabled = await request('disable Core Agent profile and force runtime disconnect', 'POST', join(coreOrigin, `/admin/agents/${encodeURIComponent(agentId)}/disable`), {
      operatorId,
      reason: 'P26 lifecycle E2E disable should disconnect live runtime',
      gatewayNodeId
    });
    if (disabled?.enabled !== false) throw new Error(`Expected disabled profile enabled=false, got ${JSON.stringify(disabled).slice(0, 300)}`);

    await waitFor('Agent to disappear or become disconnected after Core governance disable', findRuntimeAgent, (agent) => !agent || agent.transportStatus !== 'CONNECTED');
    console.log('[p26-governance] disable -> runtime disconnect verified');
    await stopAgent(child);

    const revoked = await request('revoke Core Agent profile and keep runtime blocked', 'POST', join(coreOrigin, `/admin/agents/${encodeURIComponent(agentId)}/revoke`), {
      operatorId,
      reason: 'P28 lifecycle E2E revoke should keep Agent non-connectable and reject reconnect',
      gatewayNodeId
    });
    const approvalStatus = String(revoked?.approvalStatus || revoked?.status || '').toUpperCase();
    if (!approvalStatus.includes('REVOK')) throw new Error(`Expected revoked profile status, got ${JSON.stringify(revoked).slice(0, 300)}`);

    await expectRegistrationRejected('revoke -> authorization denial');
  } finally {
    await stopAgent(child);
  }
  console.log('[p26-governance] Agent governance runtime lifecycle E2E passed.');
}

main().catch((error) => {
  console.error(`[p26-governance] FAILED: ${error.message}`);
  process.exit(1);
});

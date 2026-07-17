#!/usr/bin/env node
/**
 * P2-R runtime smoke for the Dispatch Contract Builder path.
 *
 * This script intentionally avoids browser automation so it can run in the
 * existing local CI/CD toolchain. It verifies that the Builder route renders as
 * HTML and that the canonical CMS_CONTENT_REVIEW eligibility API returns the
 * seeded eligible agent.
 */
const timeoutMs = Number(process.env.SMOKE_TIMEOUT_MS || 8000);
const publicHost = process.env.OPENDISPATCH_PUBLIC_HOST || process.env.CI_PUBLIC_HOST || '';
const publicScheme = process.env.OPENDISPATCH_PUBLIC_SCHEME || 'http';
const adminOrigin = normalizeOrigin(process.env.ADMIN_UI_ORIGIN || (publicHost ? `${publicScheme}://${publicHost}:${process.env.ADMIN_UI_HTTP_PORT || 3000}` : 'http://localhost:3000'));
const coreOrigin = normalizeOrigin(process.env.CORE_BACKEND_ORIGIN || process.env.CORE_BASE_URL || (publicHost ? `${publicScheme}://${publicHost}:${process.env.CORE_HTTP_PORT || 18080}` : 'http://localhost:18080'));
const token = process.env.ADMIN_TOKEN || process.env.ACCESS_TOKEN || '';
const taskId = process.env.P2P_CMS_TASK_ID || 'p2p-cms-content-review-task';
const agentId = process.env.P2P_CMS_AGENT_ID || 'p2p-cms-review-agent-001';

function normalizeOrigin(value) {
  return String(value || '').replace(/\/+$/, '');
}

function joinUrl(origin, path) {
  return `${origin}${path.startsWith('/') ? path : `/${path}`}`;
}

async function fetchWithTimeout(url, options = {}) {
  const controller = new AbortController();
  const timer = setTimeout(() => controller.abort(), timeoutMs);
  try {
    return await fetch(url, { ...options, signal: controller.signal });
  } finally {
    clearTimeout(timer);
  }
}

async function verifyBuilderRoute() {
  const url = joinUrl(adminOrigin, '/settings/dispatch-contract-builder');
  const headers = token ? { Authorization: `Bearer ${token}` } : undefined;
  const started = Date.now();
  const response = await fetchWithTimeout(url, { headers });
  const body = await response.text();
  const latency = Date.now() - started;
  const contentType = response.headers.get('content-type') || '';
  const looksLikeHtml = contentType.includes('text/html') || /^\s*<!doctype html/i.test(body) || /<html[\s>]/i.test(body);
  if (!response.ok || !looksLikeHtml) {
    throw new Error(`Dispatch Contract Builder route failed: HTTP ${response.status} content-type=${contentType || '(empty)'} ${url}`);
  }
  console.log(`OK   Dispatch Contract Builder route -> HTTP ${response.status} text/html (${latency}ms) ${url}`);
}

function unwrapEnvelope(payload) {
  if (payload && typeof payload === 'object' && typeof payload.code === 'string' && Object.prototype.hasOwnProperty.call(payload, 'data')) {
    if (payload.code !== 'OK') {
      throw new Error(`Eligibility API returned envelope code=${payload.code} message=${payload.message || ''}`.trim());
    }
    return payload.data;
  }
  return payload;
}

async function verifyEligibilityApi() {
  const url = joinUrl(coreOrigin, `/admin/tasks/${encodeURIComponent(taskId)}/eligible-agents?limit=50`);
  const headers = token ? { Authorization: `Bearer ${token}` } : undefined;
  const started = Date.now();
  const response = await fetchWithTimeout(url, { headers });
  const latency = Date.now() - started;
  const text = await response.text();
  if (!response.ok) {
    throw new Error(`Eligibility API failed: HTTP ${response.status} ${url} ${text.slice(0, 240)}`);
  }
  const payload = unwrapEnvelope(JSON.parse(text));
  const eligible = payload?.eligibleAgents || [];
  const blocked = payload?.blockedAgents || [];
  const match = eligible.find((candidate) => candidate.agentId === agentId && candidate.eligible !== false);
  if (!match) {
    const blockedMatch = blocked.find((candidate) => candidate.agentId === agentId);
    if (blockedMatch) {
      const blocking = (blockedMatch.checks || []).filter((check) => check.blocking);
      throw new Error(`${agentId} is blocked for ${taskId}. Blocking checks=${JSON.stringify(blocking)}`);
    }
    throw new Error(`${agentId} was not found in eligibleAgents for ${taskId}. Eligible=${eligible.map((candidate) => candidate.agentId).join(', ') || '(none)'}`);
  }
  console.log(`OK   CMS_CONTENT_REVIEW eligibility -> ${agentId} eligible score=${match.score ?? 'n/a'} (${latency}ms) ${url}`);
}

try {
  await verifyBuilderRoute();
  await verifyEligibilityApi();
  console.log('\nP2-R Dispatch Contract Builder runtime smoke completed.');
} catch (error) {
  console.error(`FAIL ${error instanceof Error ? error.message : String(error)}`);
  process.exit(1);
}

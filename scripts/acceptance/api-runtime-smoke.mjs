#!/usr/bin/env node
/**
 * Runtime smoke acceptance for high-risk OpenDispatch API surfaces.
 *
 * This complements the strict P21 envelope acceptance by probing the Core and
 * Netty read-only endpoints that historically break during Core/Netty/Admin UI
 * integration work: Agent Governance, Task Callback metadata, Core Admin Task
 * Facade, Admin Runtime, and Admin UI proxy reachability.
 */
const timeoutMs = Number(process.env.API_RUNTIME_SMOKE_TIMEOUT_MS || process.env.SMOKE_TIMEOUT_MS || 7000);
const publicHost = process.env.OPENDISPATCH_PUBLIC_HOST || process.env.CI_PUBLIC_HOST || '';
const publicScheme = process.env.OPENDISPATCH_PUBLIC_SCHEME || 'http';
const defaultCoreOrigin = publicHost ? `${publicScheme}://${publicHost}:${process.env.CORE_HTTP_PORT || 18080}` : 'http://127.0.0.1:18080';
const defaultNettyOrigin = publicHost ? `${publicScheme}://${publicHost}:${process.env.NETTY_ADMIN_HTTP_PORT || 18081}` : 'http://127.0.0.1:18081';
const defaultAdminOrigin = publicHost ? `${publicScheme}://${publicHost}:${process.env.ADMIN_UI_HTTP_PORT || 3000}` : 'http://127.0.0.1:3000';
const coreOrigin = normalizeOrigin(process.env.CORE_URL || process.env.CORE_BACKEND_ORIGIN || defaultCoreOrigin);
const nettyOrigin = normalizeOrigin(process.env.NETTY_URL || process.env.NETTY_BACKEND_ORIGIN || defaultNettyOrigin);
const adminOrigin = normalizeOrigin(process.env.ADMIN_UI_URL || process.env.ADMIN_UI_ORIGIN || defaultAdminOrigin);
const token = process.env.ADMIN_TOKEN || process.env.ACCESS_TOKEN || '';
const skipAdminProxy = envFlag('SKIP_ADMIN_UI_SMOKE') || envFlag('API_RUNTIME_SMOKE_SKIP_ADMIN_PROXY');

const AUTH_OR_POLICY_CODES = new Set([
  'UNAUTHORIZED',
  'FORBIDDEN',
  'AUTH_TOKEN_EXPIRED',
  'ADMIN_AUTH_REQUIRED',
  'ADMIN_TOKEN_EXPIRED'
]);

function normalizeOrigin(value) {
  return value.replace(/\/+$/, '');
}

function envFlag(name) {
  const value = process.env[name];
  return value !== undefined && ['1', 'true', 'yes', 'on'].includes(value.toLowerCase());
}

function joinUrl(origin, path) {
  return `${origin}${path.startsWith('/') ? path : `/${path}`}`;
}

function isRecord(value) {
  return typeof value === 'object' && value !== null && !Array.isArray(value);
}

function isStandardEnvelope(value) {
  return isRecord(value)
    && typeof value.code === 'string'
    && typeof value.message === 'string'
    && Object.prototype.hasOwnProperty.call(value, 'data')
    && typeof value.timestamp === 'string';
}

async function readBody(response) {
  const contentType = response.headers.get('content-type') || '';
  if (contentType.includes('application/json')) {
    try {
      return await response.json();
    } catch (error) {
      throw new Error(`Invalid JSON response: ${error instanceof Error ? error.message : String(error)}`);
    }
  }
  const text = await response.text().catch(() => '');
  throw new Error(`Expected JSON but got content-type=${contentType || '<empty>'} body=${text.slice(0, 200)}`);
}

async function fetchJson(url, method = 'GET') {
  const controller = new AbortController();
  const timer = setTimeout(() => controller.abort(), timeoutMs);
  const headers = token ? { Authorization: `Bearer ${token}` } : undefined;
  try {
    const response = await fetch(url, { method, headers, signal: controller.signal, cache: 'no-store' });
    const body = await readBody(response);
    return { response, body };
  } finally {
    clearTimeout(timer);
  }
}

function assertEnvelope(name, response, body, url) {
  if (response.status !== 200) {
    throw new Error(`${name}: expected HTTP 200 but got HTTP ${response.status} ${url}`);
  }
  if (!isStandardEnvelope(body)) {
    throw new Error(`${name}: response is not a standard API envelope. Body=${JSON.stringify(body).slice(0, 400)}`);
  }
  return body;
}

function assertDataShape(name, envelope, predicate, expectation) {
  if (envelope.code !== 'OK') {
    if (AUTH_OR_POLICY_CODES.has(envelope.code)) {
      return 'auth-required';
    }
    throw new Error(`${name}: expected code=OK but got code=${envelope.code}. message=${envelope.message}`);
  }
  if (predicate && !predicate(envelope.data)) {
    throw new Error(`${name}: response data does not match expected shape: ${expectation}. data=${JSON.stringify(envelope.data).slice(0, 400)}`);
  }
  return 'ok';
}

async function probe({ name, url, method = 'GET', predicate, expectation }) {
  const started = Date.now();
  const { response, body } = await fetchJson(url, method);
  const envelope = assertEnvelope(name, response, body, url);
  const status = assertDataShape(name, envelope, predicate, expectation);
  const auth = status === 'auth-required' ? ' auth-required' : '';
  console.log(`OK   ${name} -> HTTP ${response.status} code=${envelope.code}${auth} (${Date.now() - started}ms) ${url}`);
}

const checks = [
  {
    name: 'Core Agent Governance list contract',
    url: joinUrl(coreOrigin, process.env.API_RUNTIME_SMOKE_AGENT_GOVERNANCE_PATH || '/admin/agents?limit=1'),
    predicate: (data) => Array.isArray(data),
    expectation: 'data is an array of agent profiles'
  },
  {
    name: 'Core Task Callback metadata contract',
    url: joinUrl(coreOrigin, process.env.API_RUNTIME_SMOKE_TASK_CALLBACK_METADATA_PATH || '/internal/control-plane/tasks/callbacks/metadata'),
    predicate: (data) => isRecord(data) && Object.prototype.hasOwnProperty.call(data, 'idempotencyEnabled'),
    expectation: 'data.idempotencyEnabled exists'
  },
  {
    name: 'Core Admin Task failure queue contract',
    url: joinUrl(coreOrigin, process.env.API_RUNTIME_SMOKE_TASK_FAILURE_QUEUE_PATH || '/admin/tasks/failure-queue?limit=1'),
    predicate: (data) => isRecord(data) || Array.isArray(data),
    expectation: 'data is a failure queue DTO or list'
  },
  {
    name: 'Netty Admin Runtime snapshot contract',
    url: joinUrl(nettyOrigin, process.env.API_RUNTIME_SMOKE_ADMIN_RUNTIME_PATH || '/api/admin/runtime/snapshot'),
    predicate: (data) => isRecord(data) && Object.prototype.hasOwnProperty.call(data, 'connections'),
    expectation: 'data.connections exists'
  }
];

if (!skipAdminProxy) {
  checks.push(
    {
      name: 'Admin UI proxy Core Agent Governance contract',
      url: joinUrl(adminOrigin, process.env.API_RUNTIME_SMOKE_ADMIN_PROXY_CORE_GOVERNANCE_PATH || '/core-api/admin/agents?limit=1'),
      predicate: (data) => Array.isArray(data),
      expectation: 'data is an array of agent profiles'
    },
    {
      name: 'Admin UI proxy Netty Runtime snapshot contract',
      url: joinUrl(adminOrigin, process.env.API_RUNTIME_SMOKE_ADMIN_PROXY_RUNTIME_PATH || '/netty-api/api/admin/runtime/snapshot'),
      predicate: (data) => isRecord(data) && Object.prototype.hasOwnProperty.call(data, 'connections'),
      expectation: 'data.connections exists'
    }
  );
} else {
  console.log('SKIP Admin UI proxy runtime smoke checks by SKIP_ADMIN_UI_SMOKE/API_RUNTIME_SMOKE_SKIP_ADMIN_PROXY.');
}

for (const check of checks) {
  await probe(check);
}

console.log('\nOpenDispatch high-risk API runtime smoke acceptance passed.');

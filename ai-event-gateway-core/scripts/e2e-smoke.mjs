#!/usr/bin/env node
const timeoutMs = Number(process.env.SMOKE_TIMEOUT_MS || 5000);
const adminOrigin = normalizeOrigin(process.env.ADMIN_UI_ORIGIN || 'http://localhost:3000');
const coreOrigin = normalizeOrigin(process.env.CORE_BACKEND_ORIGIN || 'http://localhost:18080');
const nettyOrigin = normalizeOrigin(process.env.NETTY_BACKEND_ORIGIN || 'http://localhost:18081');
const token = process.env.ADMIN_TOKEN || process.env.ACCESS_TOKEN || '';

function normalizeOrigin(value) {
  return value.replace(/\/+$/, '');
}

function joinUrl(origin, path) {
  return `${origin}${path.startsWith('/') ? path : `/${path}`}`;
}

const AUTH_REQUIRED_CODES = new Set(['UNAUTHORIZED', 'FORBIDDEN', 'AUTH_TOKEN_EXPIRED', 'ADMIN_AUTH_REQUIRED', 'ADMIN_TOKEN_EXPIRED']);

function isStandardEnvelope(value) {
  return value !== null
    && typeof value === 'object'
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
    } catch {
      return undefined;
    }
  }
  try {
    return await response.text();
  } catch {
    return undefined;
  }
}

async function probe(name, url, { required = true } = {}) {
  const controller = new AbortController();
  const timer = setTimeout(() => controller.abort(), timeoutMs);
  const headers = token ? { Authorization: `Bearer ${token}` } : undefined;
  const started = Date.now();
  try {
    const response = await fetch(url, { headers, signal: controller.signal });
    const body = await readBody(response);
    const latency = Date.now() - started;
    const envelope = isStandardEnvelope(body) ? body : undefined;
    const code = envelope?.code;
    const authRequired = AUTH_REQUIRED_CODES.has(code || '');
    const ok = response.ok && (!envelope || code === 'OK' || authRequired);
    const statusLabel = code ? `HTTP ${response.status} code=${code}` : `HTTP ${response.status}`;
    if (ok || authRequired) {
      console.log(`OK   ${name} -> ${statusLabel}${authRequired ? ' auth-required' : ''} (${latency}ms) ${url}`);
      return true;
    }
    const message = `${required ? 'FAIL' : 'WARN'} ${name} -> ${statusLabel} (${latency}ms) ${url}`;
    console[required ? 'error' : 'warn'](message);
    return !required;
  } catch (error) {
    const message = `${required ? 'FAIL' : 'WARN'} ${name} -> ${error instanceof Error ? error.message : String(error)} ${url}`;
    console[required ? 'error' : 'warn'](message);
    return !required;
  } finally {
    clearTimeout(timer);
  }
}

const probes = [
  ['Admin UI health', joinUrl(adminOrigin, '/api/health')],
  ['Admin proxy Core dashboard snapshot', joinUrl(adminOrigin, '/core-api/admin/dashboard/snapshot')],
  ['Admin proxy Netty runtime snapshot', joinUrl(adminOrigin, '/netty-api/api/admin/runtime/snapshot')],
  ['Admin proxy legacy Netty health alias', joinUrl(adminOrigin, '/gateway-api/api/admin/health'), { required: false }],
  ['Core direct dashboard snapshot', joinUrl(coreOrigin, '/admin/dashboard/snapshot')],
  ['Netty direct health', joinUrl(nettyOrigin, '/api/admin/health')],
  ['Netty direct runtime snapshot', joinUrl(nettyOrigin, '/api/admin/runtime/snapshot')],
  ['Netty runtime stream HTTP reachability', joinUrl(nettyOrigin, '/api/admin/runtime/stream'), { required: false }]
];

let success = true;
for (const [name, url, options] of probes) {
  const passed = await probe(name, url, options ?? {});
  success = success && passed;
}

if (!success) {
  console.error('\nE2E smoke test failed. Set ADMIN_TOKEN when endpoints require authentication.');
  process.exit(1);
}

console.log('\nE2E smoke test completed. Standard auth-required codes are treated as reachable when no ADMIN_TOKEN is supplied.');

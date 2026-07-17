#!/usr/bin/env node
/**
 * P21 runtime acceptance test for the OpenDispatch API envelope contract.
 *
 * This script is intentionally transport-level: it talks to an already-started
 * Core / Netty / Admin UI stack and verifies that business/admin JSON APIs use
 * { code, message, data, timestamp } envelopes for both success and failure.
 * Success responses must remain HTTP 200. Error envelopes must keep the standard
 * JSON shape while using the transport status mapped by the backend, such as 404
 * for not-found errors. This remains the code/message/data/timestamp envelope contract.
 */
const timeoutMs = Number(process.env.P21_API_ACCEPTANCE_TIMEOUT_MS || process.env.SMOKE_TIMEOUT_MS || 7000);
const publicHost = process.env.OPENDISPATCH_PUBLIC_HOST || process.env.CI_PUBLIC_HOST || '';
const publicScheme = process.env.OPENDISPATCH_PUBLIC_SCHEME || 'http';
const defaultCoreOrigin = publicHost ? `${publicScheme}://${publicHost}:${process.env.CORE_HTTP_PORT || 18080}` : 'http://127.0.0.1:18080';
const defaultNettyOrigin = publicHost ? `${publicScheme}://${publicHost}:${process.env.NETTY_ADMIN_HTTP_PORT || 18081}` : 'http://127.0.0.1:18081';
const defaultAdminOrigin = publicHost ? `${publicScheme}://${publicHost}:${process.env.ADMIN_UI_HTTP_PORT || 3000}` : 'http://127.0.0.1:3000';
const coreOrigin = normalizeOrigin(process.env.CORE_URL || process.env.CORE_BACKEND_ORIGIN || defaultCoreOrigin);
const nettyOrigin = normalizeOrigin(process.env.NETTY_URL || process.env.NETTY_BACKEND_ORIGIN || defaultNettyOrigin);
const adminOrigin = normalizeOrigin(process.env.ADMIN_UI_URL || process.env.ADMIN_UI_ORIGIN || defaultAdminOrigin);
const token = process.env.ADMIN_TOKEN || process.env.ACCESS_TOKEN || '';
const skipAdminProxy = envFlag('P21_SKIP_ADMIN_PROXY') || envFlag('SKIP_ADMIN_UI_SMOKE');
const runAdminProxyBackendError = envFlag('P21_RUN_ADMIN_PROXY_BACKEND_ERROR');

const DEFAULTS = {
  coreSuccessPath: '/api/core/status',
  coreErrorPath: '/api/agents/__p21-runtime-acceptance-missing-agent__/runtime/load',
  nettySuccessPath: '/api/agents/summary',
  nettyErrorPath: '/api/agents/__p21-runtime-acceptance-missing-agent__',
  adminCoreProxySuccessPath: '/core-api/api/core/status',
  adminNettyProxySuccessPath: '/netty-api/api/agents/summary',
  adminCoreProxyApplicationErrorPath: '/core-api/__p21-runtime-acceptance-missing-route__',
  adminCoreProxyBackendErrorPath: '/core-api/api/core/status'
};

const EXPECTED = {
  coreSuccessCode: 'OK',
  coreSuccessHttpStatuses: parseExpectedStatuses('P21_CORE_SUCCESS_HTTP_STATUSES', '200'),
  coreErrorCode: process.env.P21_CORE_ERROR_CODE || 'CORE_AGENT_NOT_FOUND',
  coreErrorHttpStatuses: parseExpectedStatuses('P21_CORE_ERROR_HTTP_STATUSES', '404'),
  nettySuccessCode: 'OK',
  nettySuccessHttpStatuses: parseExpectedStatuses('P21_NETTY_SUCCESS_HTTP_STATUSES', '200'),
  nettyErrorCode: process.env.P21_NETTY_ERROR_CODE || 'GATEWAY_AGENT_NOT_FOUND',
  nettyErrorHttpStatuses: parseExpectedStatuses('P21_NETTY_ERROR_HTTP_STATUSES', '404'),
  adminProxyCoreSuccessCode: 'OK',
  adminProxyCoreSuccessHttpStatuses: parseExpectedStatuses('P21_ADMIN_CORE_PROXY_SUCCESS_HTTP_STATUSES', '200'),
  adminProxyNettySuccessCode: 'OK',
  adminProxyNettySuccessHttpStatuses: parseExpectedStatuses('P21_ADMIN_NETTY_PROXY_SUCCESS_HTTP_STATUSES', '200'),
  adminProxyApplicationErrorCodes: new Set((process.env.P21_ADMIN_PROXY_APPLICATION_ERROR_CODES || 'INTERNAL_ERROR,NOT_FOUND')
    .split(',')
    .map((value) => value.trim())
    .filter(Boolean)),
  adminProxyApplicationErrorHttpStatuses: parseExpectedStatuses('P21_ADMIN_PROXY_APPLICATION_ERROR_HTTP_STATUSES', '404,500'),
  adminProxyBackendErrorCodes: new Set((process.env.P21_ADMIN_PROXY_BACKEND_ERROR_CODES || 'ADMIN_PROXY_CORE_ERROR,ADMIN_PROXY_CORE_UNAVAILABLE')
    .split(',')
    .map((value) => value.trim())
    .filter(Boolean)),
  adminProxyBackendErrorHttpStatuses: parseExpectedStatuses('P21_ADMIN_PROXY_BACKEND_ERROR_HTTP_STATUSES', '502,503')
};

function normalizeOrigin(value) {
  return value.replace(/\/+$/, '');
}


function parseExpectedStatuses(envName, fallback) {
  return new Set((process.env[envName] || fallback)
    .split(',')
    .map((value) => Number(value.trim()))
    .filter((value) => Number.isInteger(value) && value >= 100 && value <= 599));
}

function envFlag(name) {
  const value = process.env[name];
  return value !== undefined && ['1', 'true', 'yes', 'on'].includes(value.toLowerCase());
}

function configuredPath(name, fallback) {
  return process.env[name] || fallback;
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
      throw new Error(`Invalid JSON body: ${error instanceof Error ? error.message : String(error)}`);
    }
  }
  const text = await response.text().catch(() => '');
  throw new Error(`Expected JSON response but got content-type=${contentType || '<empty>'} body=${text.slice(0, 200)}`);
}

async function fetchJson(url) {
  const controller = new AbortController();
  const timer = setTimeout(() => controller.abort(), timeoutMs);
  try {
    const headers = token ? { Authorization: `Bearer ${token}` } : undefined;
    const response = await fetch(url, { headers, signal: controller.signal, cache: 'no-store' });
    const body = await readBody(response);
    return { response, body };
  } finally {
    clearTimeout(timer);
  }
}

function assertEnvelope(name, response, body, expectedStatuses) {
  const allowedStatuses = expectedStatuses || new Set([200]);
  if (!allowedStatuses.has(response.status)) {
    throw new Error(`${name}: expected HTTP status in [${Array.from(allowedStatuses).join(', ')}] but got HTTP ${response.status}`);
  }
  if (!isStandardEnvelope(body)) {
    throw new Error(`${name}: response is not a standard envelope. Body=${JSON.stringify(body).slice(0, 400)}`);
  }
  return body;
}

async function expectCode(name, url, expectedCode, expectedStatuses = new Set([200])) {
  const started = Date.now();
  const { response, body } = await fetchJson(url);
  const envelope = assertEnvelope(name, response, body, expectedStatuses);
  if (envelope.code !== expectedCode) {
    throw new Error(`${name}: expected code=${expectedCode} but got code=${envelope.code}. message=${envelope.message}`);
  }
  console.log(`OK   ${name} -> HTTP ${response.status} code=${envelope.code} (${Date.now() - started}ms) ${url}`);
  return envelope;
}

async function expectCodeIn(name, url, expectedCodes, expectedStatuses = new Set([200])) {
  const started = Date.now();
  const { response, body } = await fetchJson(url);
  const envelope = assertEnvelope(name, response, body, expectedStatuses);
  if (!expectedCodes.has(envelope.code)) {
    throw new Error(`${name}: expected code in [${Array.from(expectedCodes).join(', ')}] but got code=${envelope.code}. message=${envelope.message}`);
  }
  console.log(`OK   ${name} -> HTTP ${response.status} code=${envelope.code} (${Date.now() - started}ms) ${url}`);
  return envelope;
}

async function main() {
  const checks = [
    () => expectCode('Core success API envelope', joinUrl(coreOrigin, configuredPath('P21_CORE_SUCCESS_PATH', DEFAULTS.coreSuccessPath)), EXPECTED.coreSuccessCode, EXPECTED.coreSuccessHttpStatuses),
    () => expectCode('Core error API envelope', joinUrl(coreOrigin, configuredPath('P21_CORE_ERROR_PATH', DEFAULTS.coreErrorPath)), EXPECTED.coreErrorCode, EXPECTED.coreErrorHttpStatuses),
    () => expectCode('Netty success API envelope', joinUrl(nettyOrigin, configuredPath('P21_NETTY_SUCCESS_PATH', DEFAULTS.nettySuccessPath)), EXPECTED.nettySuccessCode, EXPECTED.nettySuccessHttpStatuses),
    () => expectCode('Netty error API envelope', joinUrl(nettyOrigin, configuredPath('P21_NETTY_ERROR_PATH', DEFAULTS.nettyErrorPath)), EXPECTED.nettyErrorCode, EXPECTED.nettyErrorHttpStatuses)
  ];

  if (!skipAdminProxy) {
    checks.push(
      () => expectCode('Admin UI proxy Core envelope pass-through', joinUrl(adminOrigin, configuredPath('P21_ADMIN_CORE_PROXY_SUCCESS_PATH', DEFAULTS.adminCoreProxySuccessPath)), EXPECTED.adminProxyCoreSuccessCode, EXPECTED.adminProxyCoreSuccessHttpStatuses),
      () => expectCode('Admin UI proxy Netty envelope pass-through', joinUrl(adminOrigin, configuredPath('P21_ADMIN_NETTY_PROXY_SUCCESS_PATH', DEFAULTS.adminNettyProxySuccessPath)), EXPECTED.adminProxyNettySuccessCode, EXPECTED.adminProxyNettySuccessHttpStatuses),
      () => expectCodeIn('Admin UI proxy Core application error pass-through', joinUrl(adminOrigin, configuredPath('P21_ADMIN_CORE_PROXY_APPLICATION_ERROR_PATH', DEFAULTS.adminCoreProxyApplicationErrorPath)), EXPECTED.adminProxyApplicationErrorCodes, EXPECTED.adminProxyApplicationErrorHttpStatuses)
    );

    if (runAdminProxyBackendError) {
      checks.push(
        () => expectCodeIn('Admin UI proxy backend transport error envelope', joinUrl(adminOrigin, configuredPath('P21_ADMIN_CORE_PROXY_BACKEND_ERROR_PATH', DEFAULTS.adminCoreProxyBackendErrorPath)), EXPECTED.adminProxyBackendErrorCodes, EXPECTED.adminProxyBackendErrorHttpStatuses)
      );
    } else {
      console.log('SKIP Admin UI proxy backend transport error check by default. Set P21_RUN_ADMIN_PROXY_BACKEND_ERROR=true with an unreachable backend origin to enable it.');
    }
  } else {
    console.log('SKIP Admin UI proxy envelope checks by P21_SKIP_ADMIN_PROXY/SKIP_ADMIN_UI_SMOKE.');
  }

  for (const check of checks) {
    await check();
  }

  console.log('\nP21 API envelope runtime acceptance passed.');
}

main().catch((error) => {
  console.error(`FAIL ${error instanceof Error ? error.message : String(error)}`);
  process.exit(1);
});

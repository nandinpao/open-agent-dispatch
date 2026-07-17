#!/usr/bin/env node
import http from 'node:http';
import fs from 'node:fs';
import os from 'node:os';
import { spawn } from 'node:child_process';
import assert from 'node:assert/strict';
import path from 'node:path';
import process from 'node:process';

const observed = {
  loginCsrf: false,
  sessionCookie: false,
  adminMutationCsrf: false,
  gatewayToken: false,
  legacyMutation: false,
  legacyFields: false,
  canonicalCredentialToken: false,
};

function envelope(data) {
  return JSON.stringify({ code: 'OK', message: 'Success', data, timestamp: new Date().toISOString() });
}

function send(response, status, body, cookies = []) {
  response.statusCode = status;
  response.setHeader('Content-Type', 'application/json');
  for (const cookie of cookies) response.appendHeader('Set-Cookie', cookie);
  response.end(typeof body === 'string' ? body : JSON.stringify(body));
}

let csrfSequence = 0;
const server = http.createServer(async (request, response) => {
  const url = new URL(request.url, 'http://127.0.0.1');
  const chunks = [];
  for await (const chunk of request) chunks.push(chunk);
  const rawBody = Buffer.concat(chunks).toString('utf8');
  const body = rawBody ? JSON.parse(rawBody) : null;
  const cookie = request.headers.cookie || '';
  const csrf = request.headers['x-xsrf-token'] || '';

  if (/qualifications|assignment-profiles|supply-profiles|agent-skills/.test(url.pathname)) observed.legacyMutation = true;
  if (body && (Object.hasOwn(body, 'capabilities') || Object.hasOwn(body, 'scopes') || Object.hasOwn(body, 'profileCode'))) observed.legacyFields = true;

  if (request.method === 'GET' && url.pathname === '/api/auth/csrf') {
    csrfSequence += 1;
    const token = csrfSequence === 1 ? 'csrf-before-login' : 'csrf-after-login';
    return send(response, 200, envelope({ headerName: 'X-XSRF-TOKEN', parameterName: '_csrf', token }), [
      `XSRF-TOKEN=${token}; Path=/; SameSite=Lax`,
    ]);
  }
  if (request.method === 'POST' && url.pathname === '/api/auth/login') {
    observed.loginCsrf = cookie.includes('XSRF-TOKEN=csrf-before-login') && csrf === 'csrf-before-login';
    return send(response, 200, envelope({ username: 'admin' }), [
      'OPENDISPATCH_ADMIN_SESSION=session-1; Path=/; HttpOnly; SameSite=Lax',
    ]);
  }
  if (request.method === 'GET' && url.pathname === '/api/auth/me') {
    observed.sessionCookie = cookie.includes('OPENDISPATCH_ADMIN_SESSION=session-1');
    return send(response, 200, envelope({ username: 'admin' }));
  }
  if (request.method === 'GET' && url.pathname === '/admin/agents/agent-cluster-node-001-001') {
    observed.sessionCookie ||= cookie.includes('OPENDISPATCH_ADMIN_SESSION=session-1');
    return send(response, 200, envelope({
      agentId: 'agent-cluster-node-001-001', tenantId: 'tenant-a', agentType: 'OPENCLAW',
      approvalStatus: 'APPROVED', enabled: true, riskStatus: 'NORMAL', credentialStatus: 'ACTIVE',
    }));
  }
  if (url.pathname.startsWith('/admin/')) {
    observed.adminMutationCsrf ||= request.method === 'GET'
      ? cookie.includes('OPENDISPATCH_ADMIN_SESSION=session-1')
      : cookie.includes('OPENDISPATCH_ADMIN_SESSION=session-1') && csrf === 'csrf-after-login';
    if (request.method === 'GET' && url.pathname.endsWith('/runtime-bindings')) return send(response, 200, envelope([]));
    if (request.method === 'POST' && url.pathname.endsWith('/credentials/issue')) {
      observed.canonicalCredentialToken = body?.credentialToken === 'agent-onboarding-token';
      return send(response, 200, envelope({ credentialStatus: 'ACTIVE' }));
    }
    if (request.method === 'PUT' && url.pathname.startsWith('/admin/runtime-resources/')) return send(response, 200, envelope(body));
    if (request.method === 'POST' && url.pathname.endsWith('/runtime-bindings')) return send(response, 200, envelope({ ...body, bindingId: 'binding-1', bindingStatus: 'ACTIVE' }));
    return send(response, 200, envelope(body || {}));
  }
  if (request.method === 'POST' && url.pathname === '/internal/agents/authorize-connection') {
    observed.gatewayToken = request.headers['x-cluster-token'] === 'gateway-token';
    return send(response, 200, envelope({ allowed: true, decision: 'ALLOW' }));
  }
  return send(response, 404, { code: 'NOT_FOUND', message: 'not found', data: null, timestamp: new Date().toISOString() });
});

await new Promise((resolve) => server.listen(0, '127.0.0.1', resolve));
const address = server.address();
const root = process.cwd();
const script = path.join(root, 'ai-event-gateway-netty/scripts/core-bootstrap-cluster-agents.js');

const tempDir = fs.mkdtempSync(path.join(os.tmpdir(), 'opendispatch-stage7-fix2-'));
const envFile = path.join(tempDir, '.env.stage7-fix2');
fs.writeFileSync(envFile, [
  'CORE_ADMIN_BOOTSTRAP_USERNAME=admin',
  'CORE_ADMIN_BOOTSTRAP_PASSWORD=password',
  'CLUSTER_INTERNAL_TOKEN=gateway-token',
  'CORE_GATEWAY_INTERNAL_TOKEN=${CLUSTER_INTERNAL_TOKEN}',
  'AGENT_ONBOARDING_TOKEN=agent-onboarding-token',
  '',
].join('\n'));

const childEnvironment = {
  ...process.env,
  CORE_BASE_URL: `http://127.0.0.1:${address.port}`,
  CORE_BOOTSTRAP_ENV_FILE: envFile,
  CORE_BOOTSTRAP_ADMIN_AUTH_MODE: 'SESSION',
  CORE_OPERATOR_TOKEN: 'operator-token-must-not-authorize-gateway',
  AGENT_ONBOARDING_TOKEN: 'local-dev-agent-token-change-me',
  AGENT_TENANT_ID: 'tenant-a',
  GATEWAY_CLUSTER_NODE_COUNT: '1',
  AGENTS_PER_NODE: '1',
  CORE_BOOTSTRAP_RESET_EXISTING: 'false',
  CORE_BOOTSTRAP_FORCE_ISSUE_CREDENTIAL: 'true',
  CORE_BOOTSTRAP_VERIFY_AUTHORIZATION: 'true',
  CORE_BOOTSTRAP_ASSIGNMENT_PROFILE: 'MUST_BE_IGNORED',
  CORE_BOOTSTRAP_AUTO_APPROVE_QUALIFICATION: 'true',
  CORE_BOOTSTRAP_LEGACY_CAPABILITIES_ENABLED: 'true',
};
for (const name of [
  'CORE_BOOTSTRAP_ADMIN_USERNAME', 'CORE_BOOTSTRAP_ADMIN_PASSWORD',
  'CORE_ADMIN_BOOTSTRAP_USERNAME', 'CORE_ADMIN_BOOTSTRAP_PASSWORD',
  'CORE_GATEWAY_INTERNAL_TOKEN', 'CLUSTER_INTERNAL_TOKEN',
]) delete childEnvironment[name];

const child = spawn(process.execPath, [script], {
  cwd: root,
  env: childEnvironment,
  stdio: ['ignore', 'pipe', 'pipe'],
});
let stdout = '';
let stderr = '';
child.stdout.on('data', (chunk) => { stdout += chunk; });
child.stderr.on('data', (chunk) => { stderr += chunk; });
const exitCode = await new Promise((resolve) => child.on('close', resolve));
server.close();
fs.rmSync(tempDir, { recursive: true, force: true });

assert.equal(exitCode, 0, `bootstrap failed\nstdout=${stdout}\nstderr=${stderr}`);
assert.equal(observed.loginCsrf, true, 'login must carry pre-auth CSRF cookie/header');
assert.equal(observed.sessionCookie, true, 'admin requests must carry the authenticated session cookie');
assert.equal(observed.adminMutationCsrf, true, 'admin mutations must carry refreshed CSRF');
assert.equal(observed.gatewayToken, true, 'internal authorization must use the Gateway machine token');
assert.equal(observed.canonicalCredentialToken, true, 'deprecated local onboarding token must resolve to the canonical env-file token');
assert.equal(observed.legacyMutation, false, 'bootstrap must not call Legacy Profile/Qualification/Skill APIs');
assert.equal(observed.legacyFields, false, 'bootstrap must not send capabilities/scopes/profileCode fields');
assert.match(stdout, /legacyBootstrap=disabled/);
assert.match(stderr, /CORE_OPERATOR_TOKEN is ignored in SESSION mode/);
assert.match(stderr, /deprecated AGENT_ONBOARDING_TOKEN/);
console.log('Stage 7 Fix2 bootstrap authentication self-test: PASS');

#!/usr/bin/env node
/**
 * TDD self-test for Stage 1 Core Admin session authentication.
 *
 * Proves that the characterization runner performs the real Core sequence:
 * CSRF bootstrap -> login -> session cookie -> refreshed CSRF -> authenticated
 * mutation. No OpenDispatch production endpoint is mocked by the runner itself;
 * this local HTTP server only verifies the test harness contract.
 */
import http from 'node:http';
import fs from 'node:fs';
import os from 'node:os';
import path from 'node:path';
import process from 'node:process';
import { spawn } from 'node:child_process';

const root = path.resolve(path.dirname(new URL(import.meta.url).pathname), '../..');
const reportDir = fs.mkdtempSync(path.join(os.tmpdir(), 'opendispatch-stage1-auth-'));
const observed = {
  csrfBootstrap: false,
  loginCookie: false,
  loginCsrf: false,
  sessionCookie: false,
  refreshedCsrf: false,
  mutationCookie: false,
  mutationCsrf: false,
};
let csrfCalls = 0;

function json(response, status, body, cookies = []) {
  response.writeHead(status, {
    'content-type': 'application/json',
    ...(cookies.length ? { 'set-cookie': cookies } : {}),
  });
  response.end(JSON.stringify(body));
}

function readBody(request) {
  return new Promise((resolve) => {
    let text = '';
    request.setEncoding('utf8');
    request.on('data', (chunk) => { text += chunk; });
    request.on('end', () => resolve(text));
  });
}

const server = http.createServer(async (request, response) => {
  const url = new URL(request.url, 'http://127.0.0.1');
  const cookie = request.headers.cookie || '';
  if (request.method === 'GET' && url.pathname === '/api/auth/csrf') {
    csrfCalls += 1;
    if (csrfCalls === 1) observed.csrfBootstrap = true;
    else if (/OPENDISPATCH_ADMIN_SESSION=session-1/.test(cookie)) observed.sessionCookie = true;
    const token = csrfCalls === 1 ? 'csrf-before-login' : 'csrf-after-login';
    return json(response, 200, { headerName: 'X-XSRF-TOKEN', parameterName: '_csrf', token }, [
      `XSRF-TOKEN=${token}; Path=/; SameSite=Lax`,
    ]);
  }
  if (request.method === 'POST' && url.pathname === '/api/auth/login') {
    const body = JSON.parse(await readBody(request) || '{}');
    observed.loginCookie = /XSRF-TOKEN=csrf-before-login/.test(cookie);
    observed.loginCsrf = request.headers['x-xsrf-token'] === 'csrf-before-login';
    if (body.username !== 'stage1-admin' || body.password !== 'stage1-password'
        || !observed.loginCookie || !observed.loginCsrf) {
      return json(response, 403, { code: 'LOGIN_CONTRACT_FAILED' });
    }
    return json(response, 200, { username: body.username, selectedTenantId: 'tenant-a' }, [
      'OPENDISPATCH_ADMIN_SESSION=session-1; Path=/; HttpOnly; SameSite=Lax',
    ]);
  }
  if (request.method === 'GET' && url.pathname === '/api/auth/me') {
    if (!/OPENDISPATCH_ADMIN_SESSION=session-1/.test(cookie)) {
      return json(response, 401, { code: 'UNAUTHORIZED' });
    }
    return json(response, 200, { username: 'stage1-admin', selectedTenantId: 'tenant-a' });
  }
  if (request.method === 'POST' && url.pathname === '/admin/characterization/auth-check') {
    await readBody(request);
    observed.mutationCookie = /OPENDISPATCH_ADMIN_SESSION=session-1/.test(cookie);
    observed.refreshedCsrf = /XSRF-TOKEN=csrf-after-login/.test(cookie);
    observed.mutationCsrf = request.headers['x-xsrf-token'] === 'csrf-after-login';
    if (!observed.mutationCookie || !observed.refreshedCsrf || !observed.mutationCsrf) {
      return json(response, 403, { code: 'CSRF_OR_SESSION_MISSING' });
    }
    return json(response, 200, { authenticated: true });
  }
  return json(response, 404, { code: 'NOT_FOUND', path: url.pathname });
});

await new Promise((resolve) => server.listen(0, '127.0.0.1', resolve));
const address = server.address();
const child = spawn(process.execPath, [
  path.join(root, 'scripts/acceptance/stage0-dispatch-characterization.mjs'),
  '--auth-only',
  '--strict',
], {
  cwd: root,
  env: {
    ...process.env,
    CORE_URL: `http://127.0.0.1:${address.port}`,
    DISPATCH_CHARACTERIZATION_STAGE: 'STAGE_1_BACKEND_GOLDEN_PATH',
    STAGE1_REPORT_DIR: reportDir,
    STAGE1_ADMIN_USERNAME: 'stage1-admin',
    STAGE1_ADMIN_PASSWORD: 'stage1-password',
    STAGE1_ADMIN_BEARER_TOKEN: '',
    STAGE1_ENV_FILE: path.join(reportDir, 'does-not-exist.env'),
  },
  stdio: ['ignore', 'pipe', 'pipe'],
});
let stdout = '';
let stderr = '';
child.stdout.on('data', (chunk) => { stdout += chunk; });
child.stderr.on('data', (chunk) => { stderr += chunk; });
const exitCode = await new Promise((resolve) => child.on('close', resolve));
await new Promise((resolve) => server.close(resolve));

const wrongAgentChild = spawn(process.execPath, [
  path.join(root, 'scripts/acceptance/stage0-dispatch-characterization.mjs'),
  '--auth-only',
  '--strict',
], {
  cwd: root,
  env: {
    ...process.env,
    CORE_URL: 'http://127.0.0.1:1',
    DISPATCH_CHARACTERIZATION_STAGE: 'STAGE_1_BACKEND_GOLDEN_PATH',
    STAGE1_REPORT_DIR: reportDir,
    STAGE1_AGENT_ID: 'agent-local-ci-001',
    STAGE1_ENV_FILE: 'deploy/env/.env.local.example',
  },
  stdio: ['ignore', 'pipe', 'pipe'],
});
let wrongAgentOutput = '';
wrongAgentChild.stdout.on('data', (chunk) => { wrongAgentOutput += chunk; });
wrongAgentChild.stderr.on('data', (chunk) => { wrongAgentOutput += chunk; });
const wrongAgentExitCode = await new Promise((resolve) => wrongAgentChild.on('close', resolve));
const wrongAgentGuardPassed = wrongAgentExitCode !== 0
  && wrongAgentOutput.includes('agent-local-ci-001 belongs to docker-compose.ci.yml');

const missing = Object.entries(observed).filter(([, value]) => !value).map(([key]) => key);
if (exitCode !== 0 || missing.length || !wrongAgentGuardPassed) {
  console.error('Stage 1 characterization auth self-test failed.');
  console.error(`exitCode=${exitCode} missing=${missing.join(',') || 'none'} wrongAgentGuardPassed=${wrongAgentGuardPassed}`);
  if (stdout) console.error(`stdout:\n${stdout}`);
  if (stderr) console.error(`stderr:\n${stderr}`);
  process.exit(1);
}
console.log('Stage 1 characterization auth self-test passed: session/CSRF handling and local-vs-CI Agent selection guard are correct.');
fs.rmSync(reportDir, { recursive: true, force: true });

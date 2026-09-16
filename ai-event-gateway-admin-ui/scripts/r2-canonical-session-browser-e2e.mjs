#!/usr/bin/env node
import crypto from 'node:crypto';
import process from 'node:process';

const baseUrl = (process.env.R2_SESSION_BASE_URL || process.env.ADMIN_UI_BASE_URL || 'http://127.0.0.1:3000').replace(/\/+$/, '');
const username = process.env.R2_SESSION_USERNAME || process.env.CORE_ADMIN_BOOTSTRAP_USERNAME;
const password = process.env.R2_SESSION_PASSWORD || process.env.CORE_ADMIN_BOOTSTRAP_PASSWORD;
const mfaCode = process.env.R2_SESSION_MFA_CODE || '';
const expectedCookie = process.env.R2_SESSION_COOKIE || 'OPENDISPATCH_SESSION';

if (!username || !password) {
  console.error('R2_SESSION_USERNAME and R2_SESSION_PASSWORD (or CORE_ADMIN_BOOTSTRAP_* equivalents) are required.');
  process.exit(2);
}

class BrowserClient {
  constructor() {
    this.cookies = new Map();
    this.csrf = null;
  }

  cookieHeader() {
    return [...this.cookies.entries()].map(([key, value]) => `${key}=${value}`).join('; ');
  }

  storeCookies(response) {
    const values = response.headers.getSetCookie?.() ?? (response.headers.get('set-cookie') ? [response.headers.get('set-cookie')] : []);
    for (const value of values) {
      if (!value) continue;
      const first = value.split(';', 1)[0];
      const separator = first.indexOf('=');
      if (separator < 1) continue;
      const key = first.slice(0, separator);
      const cookieValue = first.slice(separator + 1);
      if (cookieValue) this.cookies.set(key, cookieValue);
      else this.cookies.delete(key);
    }
  }

  async fetch(path, init = {}) {
    const headers = new Headers(init.headers);
    headers.set('accept', 'application/json');
    if (this.cookies.size) headers.set('cookie', this.cookieHeader());
    const response = await fetch(`${baseUrl}${path}`, { ...init, headers, redirect: 'manual' });
    this.storeCookies(response);
    const contentType = response.headers.get('content-type') || '';
    const body = contentType.includes('application/json') ? await response.json() : await response.text();
    return { response, body };
  }

  async expect(path, status, init = {}) {
    const result = await this.fetch(path, init);
    if (result.response.status !== status) {
      throw new Error(`${init.method || 'GET'} ${path} expected ${status}, got ${result.response.status}: ${JSON.stringify(result.body)}`);
    }
    return result.body;
  }

  async csrfToken() {
    this.csrf = await this.expect('/api/session/csrf', 200);
    return this.csrf;
  }

  async mutation(path, body) {
    if (!this.csrf) await this.csrfToken();
    return this.fetch(path, {
      method: 'POST',
      headers: {
        'content-type': 'application/json',
        [this.csrf.headerName || 'X-XSRF-TOKEN']: this.csrf.token,
        'Idempotency-Key': crypto.randomUUID()
      },
      body: body === undefined ? undefined : JSON.stringify(body)
    });
  }
}

const browser = new BrowserClient();
await browser.csrfToken();

const missingCsrf = await new BrowserClient().fetch('/api/session/login', {
  method: 'POST',
  headers: { 'content-type': 'application/json' },
  body: JSON.stringify({ username, password })
});
if (missingCsrf.response.status !== 403) {
  throw new Error(`Canonical login without CSRF expected 403, got ${missingCsrf.response.status}.`);
}

let loginResult = await browser.mutation('/api/session/login', { username, password });
if (!loginResult.response.ok) {
  throw new Error(`Canonical login failed (${loginResult.response.status}): ${JSON.stringify(loginResult.body)}`);
}
let login = loginResult.body;


if (login.state === 'MFA_REQUIRED') {
  if (!mfaCode) throw new Error('MFA is required. Set R2_SESSION_MFA_CODE for this smoke run.');
  const mfaResult = await browser.mutation('/api/session/mfa/verify', {
    challengeId: login.challengeId,
    code: mfaCode,
    recoveryCode: false
  });
  if (!mfaResult.response.ok) throw new Error(`MFA verification failed: ${JSON.stringify(mfaResult.body)}`);
  login = mfaResult.body;
}

if (login.state !== 'AUTHENTICATED' && login.state !== 'PASSWORD_CHANGE_REQUIRED') {
  throw new Error(`Unexpected canonical login state: ${login.state}`);
}
if (!browser.cookies.has(expectedCookie)) {
  throw new Error(`Canonical login did not set ${expectedCookie}. Cookies: ${[...browser.cookies.keys()].join(', ')}`);
}
for (const disallowed of ['JSESSIONID', 'LEGACY_ADMIN_SESSION', 'IAM_SESSION']) {
  if (disallowed !== expectedCookie && browser.cookies.has(disallowed)) {
    throw new Error(`Canonical login unexpectedly set legacy browser cookie ${disallowed}.`);
  }
}

const session = await browser.expect('/api/session', 200);
if (session.authenticationType !== 'CANONICAL_SESSION') {
  throw new Error(`Expected CANONICAL_SESSION, got ${session.authenticationType}.`);
}
if (!session.userId || !session.username || !session.selectedTenantId) {
  throw new Error('Canonical session projection is missing user or Tenant identity.');
}
if (!Array.isArray(session.authenticationMethods) || session.authenticationMethods.length === 0) {
  throw new Error('Canonical session did not retain credential-provider evidence for audit.');
}

// Human sessions are bound to the backend-resolved home workspace. Tenant switching is not part of the canonical sign-in UI.


const logout = await browser.mutation('/api/session/logout');
if (logout.response.status !== 204) {
  throw new Error(`Canonical logout expected 204, got ${logout.response.status}: ${JSON.stringify(logout.body)}`);
}
const afterLogout = await browser.fetch('/api/session');
if (afterLogout.response.status !== 401) {
  throw new Error(`Canonical session expected 401 after logout, got ${afterLogout.response.status}.`);
}

console.log('R2 canonical browser-session E2E passed: one endpoint family, one cookie, CSRF, backend-resolved Tenant context, audit method evidence and logout.');

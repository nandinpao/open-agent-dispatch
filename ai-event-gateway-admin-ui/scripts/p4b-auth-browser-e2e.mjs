#!/usr/bin/env node
import process from 'node:process';

const baseUrl = (process.env.ADMIN_UI_BASE_URL || 'http://127.0.0.1:3000').replace(/\/+$/, '');
const username = process.env.P4B_AUTH_USERNAME || process.env.CORE_ADMIN_BOOTSTRAP_USERNAME;
const password = process.env.P4B_AUTH_PASSWORD || process.env.CORE_ADMIN_BOOTSTRAP_PASSWORD;
if (!username || !password) {
  console.error('P4B_AUTH_USERNAME and P4B_AUTH_PASSWORD are required.');
  process.exit(2);
}

const cookies = new Map();
function storeCookies(response) {
  const values = response.headers.getSetCookie?.() ?? (response.headers.get('set-cookie') ? [response.headers.get('set-cookie')] : []);
  for (const value of values) {
    const first = value.split(';', 1)[0];
    const separator = first.indexOf('=');
    if (separator > 0) cookies.set(first.slice(0, separator), first.slice(separator + 1));
  }
}
function cookieHeader() {
  return [...cookies.entries()].map(([key, value]) => `${key}=${value}`).join('; ');
}
async function request(path, init = {}) {
  const headers = new Headers(init.headers);
  if (cookies.size) headers.set('cookie', cookieHeader());
  const response = await fetch(`${baseUrl}${path}`, { ...init, headers, redirect: 'manual' });
  storeCookies(response);
  const body = (response.headers.get('content-type') || '').includes('application/json') ? await response.json() : await response.text();
  if (!response.ok) throw new Error(`${init.method || 'GET'} ${path} -> ${response.status}: ${JSON.stringify(body)}`);
  return body;
}

const csrf = await request('/api/auth/csrf');
const csrfHeader = csrf.headerName || 'X-XSRF-TOKEN';
const headers = { 'content-type': 'application/json', [csrfHeader]: csrf.token };
const session = await request('/api/auth/login', { method: 'POST', headers, body: JSON.stringify({ username, password }) });
if (!session.userId || !session.selectedTenantId) throw new Error('Login response is missing authenticated user or selected tenant.');
const me = await request('/api/auth/me');
const tenants = await request('/api/auth/tenants');
if (me.userId !== session.userId) throw new Error('Session identity changed after login.');
if (!Array.isArray(tenants.tenants) || tenants.tenants.length === 0) throw new Error('No authorized tenants returned.');
const alternate = tenants.tenants.find((item) => item.tenantId !== me.selectedTenantId);
if (alternate) {
  await request('/api/auth/select-tenant', { method: 'POST', headers, body: JSON.stringify({ tenantId: alternate.tenantId }) });
  const changed = await request('/api/auth/me');
  if (changed.selectedTenantId !== alternate.tenantId) throw new Error('Tenant selection was not persisted in the session.');
}
await request('/api/auth/logout', { method: 'POST', headers });
const afterLogout = await fetch(`${baseUrl}/api/auth/me`, { headers: cookies.size ? { cookie: cookieHeader() } : {} });
if (afterLogout.status !== 401) throw new Error(`Expected 401 after logout, got ${afterLogout.status}`);
console.log('P4-B browser authentication E2E passed.');

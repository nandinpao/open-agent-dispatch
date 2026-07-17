#!/usr/bin/env node
const baseUrl = (process.env.P4C_BASE_URL ?? process.env.ADMIN_UI_BASE_URL ?? 'http://localhost:3000').replace(/\/+$/, '');
const expiryWaitMs = Number(process.env.P4C_SESSION_EXPIRY_WAIT_MS ?? '6500');

const actors = {
  ADMIN: { username: process.env.P4C_ADMIN_USERNAME ?? 'admin-e2e', password: process.env.P4C_ADMIN_PASSWORD ?? 'admin-e2e-password' },
  OPERATOR: { username: process.env.P4C_OPERATOR_USERNAME ?? 'operator-e2e', password: process.env.P4C_OPERATOR_PASSWORD ?? 'operator-e2e-password' },
  VIEWER: { username: process.env.P4C_VIEWER_USERNAME ?? 'viewer-e2e', password: process.env.P4C_VIEWER_PASSWORD ?? 'viewer-e2e-password' }
};

class BrowserClient {
  constructor(label) { this.label = label; this.cookies = new Map(); this.csrf = null; }
  cookieHeader() { return [...this.cookies].map(([key,value]) => `${key}=${value}`).join('; '); }
  storeCookies(response) {
    const values = response.headers.getSetCookie?.() ?? (response.headers.get('set-cookie') ? [response.headers.get('set-cookie')] : []);
    for (const value of values) {
      if (!value) continue;
      const first = value.split(';',1)[0]; const separator = first.indexOf('=');
      if (separator < 1) continue;
      const key = first.slice(0,separator); const cookieValue = first.slice(separator+1);
      if (cookieValue) this.cookies.set(key,cookieValue); else this.cookies.delete(key);
    }
  }
  async fetch(path, init={}) {
    const headers = new Headers(init.headers);
    if (this.cookies.size) headers.set('cookie', this.cookieHeader());
    const response = await fetch(`${baseUrl}${path}`, { ...init, headers, redirect: 'manual' });
    this.storeCookies(response);
    const contentType=response.headers.get('content-type') ?? '';
    const body=contentType.includes('application/json') ? await response.json() : await response.text();
    return { response, body };
  }
  async expect(path, status, init={}) {
    const result=await this.fetch(path,init);
    if (result.response.status !== status) throw new Error(`${this.label}: ${init.method ?? 'GET'} ${path} expected ${status}, got ${result.response.status}: ${JSON.stringify(result.body)}`);
    return result.body;
  }
  async csrfToken() {
    const value=await this.expect('/api/auth/csrf',200);
    this.csrf=value; return value;
  }
  mutationInit(body) {
    if (!this.csrf) throw new Error(`${this.label}: CSRF token has not been loaded.`);
    return { method:'POST', headers:{'content-type':'application/json',[this.csrf.headerName]:this.csrf.token}, body:body===undefined?undefined:JSON.stringify(body) };
  }
  async login(actor) {
    await this.csrfToken();
    const session=await this.expect('/api/auth/login',200,this.mutationInit(actor));
    return session;
  }
}

async function assertMissingCsrfRejected() {
  const client=new BrowserClient('missing CSRF');
  const result=await client.fetch('/api/auth/login',{method:'POST',headers:{'content-type':'application/json'},body:JSON.stringify(actors.ADMIN)});
  if (result.response.status !== 403) throw new Error(`missing CSRF login expected 403, got ${result.response.status}`);
}

async function loginRole(role) {
  const client=new BrowserClient(role);
  const session=await client.login(actors[role]);
  if (!session.roles?.includes(role)) throw new Error(`${role}: login response does not contain expected role.`);
  return { client, session };
}

await assertMissingCsrfRejected();
const adminOne=await loginRole('ADMIN');
const operator=await loginRole('OPERATOR');
const viewer=await loginRole('VIEWER');

// VIEWER can read but cannot mutate; OPERATOR and ADMIN cross the authorization boundary.
await viewer.client.expect('/core-api/admin/agents',200);
await viewer.client.expect('/core-api/admin/agent-enrollments',403,viewer.client.mutationInit({}));
for (const actor of [operator, adminOne]) {
  const result=await actor.client.fetch('/core-api/admin/agent-enrollments',actor.client.mutationInit({}));
  if (result.response.status === 403 || result.response.status === 401) throw new Error(`${actor.session.roles[0]} should cross the OPERATOR mutation RBAC boundary.`);
}
await viewer.client.expect('/api/auth/security-audit?limit=10',403);
await operator.client.expect('/api/auth/security-audit?limit=10',403);
await adminOne.client.expect('/api/auth/security-audit?limit=10',200);

// cross-tenant rejection and header spoof protection.
const originalTenant=viewer.session.selectedTenantId;
await viewer.client.expect('/api/auth/select-tenant',400,viewer.client.mutationInit({tenantId:'tenant-not-authorized'}));
const spoofed=await viewer.client.expect('/api/auth/me',200,{headers:{'X-Tenant-Id':'tenant-not-authorized'}});
if (spoofed.selectedTenantId !== originalTenant) throw new Error('cross-tenant header spoof changed the authoritative session tenant.');
const authorizedTenant=viewer.session.allowedTenantIds?.find((tenant) => tenant !== originalTenant);
if (authorizedTenant) {
  const selected=await viewer.client.expect('/api/auth/select-tenant',200,viewer.client.mutationInit({tenantId:authorizedTenant}));
  if (selected.selectedTenantId !== authorizedTenant) throw new Error('Authorized tenant switch was not persisted.');
}

// Session list and ADMIN force revoke use browser-safe references, never raw HttpOnly cookie values.
const sessionsBeforeSecondLogin=await adminOne.client.expect('/api/auth/sessions',200);
const knownSessionReferences=new Set(sessionsBeforeSecondLogin.sessions.map((session) => session.sessionReference));
const adminTwo=await loginRole('ADMIN');
const sessions=await adminOne.client.expect('/api/auth/sessions',200);
const otherSession=sessions.sessions.find((session) =>
  session.username === actors.ADMIN.username && !session.current && !knownSessionReferences.has(session.sessionReference));
if (!otherSession) throw new Error('force revoke E2E could not identify the newly created second ADMIN session.');
for (const cookieValue of adminOne.client.cookies.values()) {
  if (otherSession.sessionReference.includes(cookieValue)) throw new Error('Session API exposed the raw HttpOnly session identifier.');
}
await adminOne.client.expect(`/api/auth/sessions/${encodeURIComponent(otherSession.sessionReference)}/revoke`,200,adminOne.client.mutationInit());
await adminTwo.client.expect('/api/auth/me',401);

// session expiration: run the smoke stack with CORE_ADMIN_SESSION_TIMEOUT=5s.
const expiring=await loginRole('VIEWER');
await new Promise((resolve) => setTimeout(resolve, expiryWaitMs));
await expiring.client.expect('/api/auth/me',401);

await adminOne.client.expect('/api/auth/logout',200,adminOne.client.mutationInit());
await adminOne.client.expect('/api/auth/me',401);
console.log('P4-C Browser E2E passed: missing CSRF, VIEWER/OPERATOR/ADMIN RBAC, cross-tenant rejection, force revoke, session expiration, and /api/auth/logout.');

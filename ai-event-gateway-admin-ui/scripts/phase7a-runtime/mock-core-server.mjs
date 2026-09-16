#!/usr/bin/env node
import http from 'node:http';

const port = Number(process.env.PHASE7A_MOCK_CORE_PORT ?? 18080);
const tenantId = 'phase7a-tenant';
let hydrationMode = 'normal';
const metrics = { bootstrapRequests: 0, hydrationRequests: 0, hydrationContexts: 0, hydrationDurationsMs: [], maxBatchContexts: 0 };

function json(response, status, body, headers = {}) {
  response.writeHead(status, { 'content-type': 'application/json; charset=utf-8', 'cache-control': 'no-store', ...headers });
  response.end(JSON.stringify(body));
}
function readBody(request) {
  return new Promise((resolve, reject) => {
    const chunks = [];
    request.on('data', (chunk) => chunks.push(chunk));
    request.on('end', () => { try { resolve(chunks.length ? JSON.parse(Buffer.concat(chunks).toString('utf8')) : undefined); } catch (error) { reject(error); } });
    request.on('error', reject);
  });
}
function capability(uiActionId, displayMode = 'ENABLED', reasonCategory = null) {
  return {
    uiActionId,
    displayMode,
    reasonCategory,
    stepUpRequired: displayMode === 'STEP_UP_REQUIRED',
    approvalRequired: displayMode === 'APPROVAL_REQUIRED',
    visibilityCeiling: displayMode === 'ENABLED' ? 'STANDARD' : 'NONE',
    requestAccessAllowed: displayMode === 'REQUEST_ACCESS',
    relatedHelpId: `ui.help.${uiActionId}`,
  };
}
function outcomeFor(resourceId) {
  if (resourceId.includes('step-up')) return 'STEP_UP_SHELL';
  if (resourceId.includes('request-access')) return 'REQUEST_ACCESS_SHELL';
  if (resourceId.includes('resource-changed')) return 'SAFE_RESOURCE_CHANGED_SHELL';
  if (resourceId.includes('hidden') || resourceId.includes('cross-tenant')) return 'ANTI_ENUMERATION_NOT_FOUND_SHELL';
  return 'PAGE';
}
function bootstrap(resourceId) {
  const outcome = outcomeFor(resourceId);
  const expiresAt = new Date(Date.now() + 60_000).toISOString();
  return {
    contractVersion: '1.0', routeContext: 'task.detail', canonicalPath: `/tasks/${encodeURIComponent(resourceId)}`,
    tenantId, principalEpoch: 7, catalogRevision: 11, policyVersion: 19, outcome,
    layoutCapabilities: [], pageCapabilities: outcome === 'PAGE' ? [capability('task.detail.view')] : [],
    resourceSummaryRef: outcome === 'PAGE' ? `sha256:${Buffer.from(resourceId).toString('hex').slice(0, 32)}` : '',
    resourceVersion: 3, expiresAt, hydrationNonce: `nonce-${resourceId}`,
  };
}
function envelope(context, durationMs) {
  const stale = hydrationMode === 'stale';
  const expiresAt = new Date(Date.now() + 60_000).toISOString();
  const refreshAfter = new Date(Date.now() + 30_000).toISOString();
  const capabilities = context.uiActionIds.map((id) => {
    if (id === 'task.cancel.execute') return capability(id, 'DISABLE_WITH_REASON', 'NOT_ALLOWED');
    return capability(id);
  });
  return {
    contractVersion: '1.0', contextId: context.contextId, tenantId,
    principalEpoch: stale ? 8 : 7, catalogRevision: 11, policyVersion: 19,
    resourceRefHash: `sha256:${Buffer.from(context.resourceId).toString('hex').slice(0, 32)}`,
    resourceVersion: stale ? Number(context.resourceVersion ?? 3) + 1 : Number(context.resourceVersion ?? 3),
    enforcementMode: 'FORMAL', capabilities, expiresAt, refreshAfter, viewStateToken: '', durationMs,
  };
}

const server = http.createServer(async (request, response) => {
  const url = new URL(request.url ?? '/', `http://${request.headers.host ?? `127.0.0.1:${port}`}`);
  if (url.pathname === '/__certification__/health') return json(response, 200, { status: 'UP' });
  if (url.pathname === '/__certification__/metrics') return json(response, 200, { ...metrics, hydrationMode });
  if (url.pathname === '/__certification__/mode' && request.method === 'POST') {
    hydrationMode = url.searchParams.get('hydration') || 'normal';
    return json(response, 200, { hydrationMode });
  }
  if (url.pathname === '/api/session') {
    return json(response, 200, { selectedTenantId: tenantId, userId: 'phase7a-viewer', expiresAt: new Date(Date.now() + 600_000).toISOString() });
  }
  if (url.pathname === '/api/session/csrf') {
    return json(response, 200, { headerName: 'X-XSRF-TOKEN', parameterName: '_csrf', token: 'phase7a-csrf-token' }, { 'set-cookie': 'XSRF-TOKEN=phase7a-csrf-token; Path=/; SameSite=Lax' });
  }
  if (url.pathname.startsWith('/api/ui/bootstrap/')) {
    metrics.bootstrapRequests += 1;
    const resourceId = url.searchParams.get('resourceId') ?? 'missing';
    return json(response, 200, bootstrap(resourceId), { vary: 'Cookie', 'x-content-type-options': 'nosniff' });
  }
  if ((url.pathname === '/api/ui/capabilities:batch' || url.pathname === '/api/ui/list-capabilities:batch') && request.method === 'POST') {
    if (url.searchParams.get('forceStatus')) {
      const status = Number(url.searchParams.get('forceStatus'));
      return json(response, status, { code: `UI_RUNTIME_FORCED_${status}`, message: `Forced ${status} for runtime certification.` });
    }
    const started = performance.now();
    const body = await readBody(request);
    const contexts = Array.isArray(body?.contexts) ? body.contexts : [];
    metrics.hydrationRequests += 1;
    metrics.hydrationContexts += contexts.length;
    metrics.maxBatchContexts = Math.max(metrics.maxBatchContexts, contexts.length);
    await new Promise((resolve) => setTimeout(resolve, 600));
    const durationMs = Math.round(performance.now() - started);
    metrics.hydrationDurationsMs.push(durationMs);
    return json(response, 200, { contractVersion: '1.0', contexts: contexts.map((context) => envelope(context, durationMs)) });
  }
  json(response, 404, { code: 'NOT_FOUND', message: 'Mock endpoint not found.' });
});
server.listen(port, '127.0.0.1', () => console.log(`[phase7a-mock-core] listening on http://127.0.0.1:${port}`));
for (const signal of ['SIGTERM', 'SIGINT']) process.on(signal, () => server.close(() => process.exit(0)));

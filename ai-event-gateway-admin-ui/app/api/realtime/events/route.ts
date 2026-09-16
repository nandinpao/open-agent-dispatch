import { NextRequest } from 'next/server';
import { fetchBackend } from '@/lib/server/backendOrigins';
import { ROOT_ADMIN_TENANT_COOKIE_KEY } from '@/lib/auth/workspaceTenantContext';

export const runtime = 'nodejs';
export const dynamic = 'force-dynamic';

const encoder = new TextEncoder();
type JsonRecord = Record<string, unknown>;
function machineToken(): string | undefined {
  return [process.env.NETTY_MACHINE_ADMIN_TOKEN, process.env.NETTY_BACKEND_ADMIN_TOKEN]
    .find((value) => value?.trim())?.trim();
}
function record(value: unknown): JsonRecord | null { return value && typeof value === 'object' && !Array.isArray(value) ? value as JsonRecord : null; }
function stringValue(value: unknown): string | undefined { return typeof value === 'string' && value.trim() ? value.trim() : undefined; }
function unwrapEvents(value: unknown): unknown[] {
  if (Array.isArray(value)) return value;
  const root = record(value);
  if (root && Array.isArray(root.data)) return root.data;
  return [];
}
function eventId(value: unknown): string {
  const item = record(value); if (!item) return JSON.stringify(value);
  return String(item.eventId ?? item.id ?? `${item.eventType ?? 'event'}:${item.timestamp ?? JSON.stringify(item)}`);
}
function findString(value: unknown, keys: Set<string>, depth = 0): string | undefined {
  if (depth > 3) return undefined;
  const item = record(value); if (!item) return undefined;
  for (const [key, child] of Object.entries(item)) {
    if (keys.has(key)) { const found = stringValue(child); if (found) return found; }
  }
  for (const child of Object.values(item)) { const found = findString(child, keys, depth + 1); if (found) return found; }
  return undefined;
}
function taskId(value: unknown): string | undefined { return findString(value, new Set(['taskId','task_id'])); }
function agentId(value: unknown): string | undefined { return findString(value, new Set(['agentId','agent_id'])); }
function selectedTenantId(value: unknown): string | undefined {
  const root = record(value); if (!root) return undefined;
  return stringValue(root.selectedTenantId) ?? stringValue(record(root.data)?.selectedTenantId);
}
function sanitizedBusinessEvent(value: unknown, resolvedTaskId?: string, resolvedAgentId?: string): JsonRecord {
  const item = record(value) ?? {};
  return {
    eventType: stringValue(item.eventType) ?? 'runtime',
    timestamp: stringValue(item.timestamp) ?? new Date().toISOString(),
    nodeId: stringValue(item.nodeId),
    taskId: resolvedTaskId,
    agentId: resolvedAgentId,
    traceId: stringValue(item.traceId),
    status: stringValue(item.status),
    message: stringValue(item.message),
  };
}
async function coreAllowed(cookie: string, path: string, tenantId?: string): Promise<boolean> {
  try {
    const headers: Record<string, string> = { Accept:'application/json' };
    if (cookie) headers.cookie = cookie;
    if (tenantId) headers['X-Tenant-Id'] = tenantId;
    const { response } = await fetchBackend('core', path, { headers });
    return response.ok;
  } catch { return false; }
}
async function currentSession(cookie: string, rootAdministrationTenantId?: string): Promise<{ ok:boolean; tenantId?:string }> {
  try {
    const { response } = await fetchBackend('core','/api/session',{ headers: cookie ? {cookie,Accept:'application/json'} : {Accept:'application/json'} });
    if (!response.ok) return {ok:false};
    return {ok:true, tenantId:selectedTenantId(await response.json()) ?? stringValue(rootAdministrationTenantId)};
  } catch { return {ok:false}; }
}
async function authorizedEvent(cookie: string, event: unknown, tenantId?: string): Promise<{ allowed:boolean; event?:unknown }> {
  const task = taskId(event); const agent = agentId(event);
  if (task) return { allowed: await coreAllowed(cookie, `/api/tasks/${encodeURIComponent(task)}`, tenantId), event: sanitizedBusinessEvent(event, task, agent) };
  if (agent) return { allowed: await coreAllowed(cookie, `/admin/agents/${encodeURIComponent(agent)}`, tenantId), event: sanitizedBusinessEvent(event, undefined, agent) };
  if (!tenantId) return {allowed:false};
  return { allowed: await coreAllowed(cookie, `/admin/tenants/${encodeURIComponent(tenantId)}/dashboard/snapshot?limit=1`, tenantId), event };
}

export async function GET(request: NextRequest): Promise<Response> {
  const cookie = request.headers.get('cookie') ?? '';
  const rootAdministrationTenantId = request.cookies.get(ROOT_ADMIN_TENANT_COOKIE_KEY)?.value?.trim();
  const firstSession = await currentSession(cookie, rootAdministrationTenantId);
  if (!firstSession.ok) return Response.json({ code:'UNAUTHORIZED', message:'Core Admin session is required.' }, {status:401});
  const token = machineToken();
  if (!token) return Response.json({ code:'MACHINE_CREDENTIAL_UNAVAILABLE', message:'Netty machine credential is not configured.' }, {status:503});

  const stream = new ReadableStream<Uint8Array>({
    start(controller) {
      let closed = false; let polling = false; const timerRef: { current?: ReturnType<typeof setInterval> } = {};
      const seen = new Set<string>();
      const send = (text:string) => { if (!closed) controller.enqueue(encoder.encode(text)); };
      const close = () => { if (closed) return; closed=true; if (timerRef.current) clearInterval(timerRef.current); try { controller.close(); } catch {} };
      const poll = async () => {
        if (polling || closed) return; polling=true;
        try {
          const session = await currentSession(cookie, rootAdministrationTenantId);
          if (!session.ok) { close(); return; }
          const { response } = await fetchBackend('netty','/api/admin/events?limit=100',{headers:{Authorization:`Bearer ${token}`,Accept:'application/json'}});
          if (!response.ok) { send(`event: error\ndata: ${JSON.stringify({status:response.status,message:'Runtime event relay failed.'})}\n\n`); return; }
          const events = unwrapEvents(await response.json());
          for (const event of [...events].reverse()) {
            const id = eventId(event); if (seen.has(id)) continue;
            const decision = await authorizedEvent(cookie,event,session.tenantId);
            seen.add(id); if (seen.size>1000) seen.delete(seen.values().next().value as string);
            if (!decision.allowed) continue;
            send(`id: ${id.replace(/[\r\n]/g,'')}\nevent: runtime\ndata: ${JSON.stringify(decision.event)}\n\n`);
          }
          send(`: heartbeat ${Date.now()}\n\n`);
        } catch { send(`event: error\ndata: ${JSON.stringify({message:'Runtime event relay failed.'})}\n\n`); }
        finally { polling=false; }
      };
      send(': connected\n\n'); void poll();
      timerRef.current=setInterval(()=>void poll(),2000);
      request.signal.addEventListener('abort',close,{once:true});
    }
  });
  return new Response(stream,{headers:{'Content-Type':'text/event-stream; charset=utf-8','Cache-Control':'no-cache, no-transform',Connection:'keep-alive','X-Accel-Buffering':'no'}});
}

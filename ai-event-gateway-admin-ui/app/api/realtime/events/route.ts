import { NextRequest } from 'next/server';
import { fetchBackend } from '@/lib/server/backendOrigins';

export const runtime = 'nodejs';
export const dynamic = 'force-dynamic';

const encoder = new TextEncoder();
function machineToken(): string | undefined {
  return [process.env.NETTY_MACHINE_ADMIN_TOKEN, process.env.NETTY_BACKEND_ADMIN_TOKEN]
    .find((value) => value?.trim())?.trim();
}
function unwrapEvents(value: unknown): unknown[] {
  if (Array.isArray(value)) return value;
  if (value && typeof value === 'object' && 'data' in value && Array.isArray((value as { data?: unknown }).data)) {
    return (value as { data: unknown[] }).data;
  }
  return [];
}
function eventId(value: unknown): string {
  if (!value || typeof value !== 'object') return JSON.stringify(value);
  const record = value as Record<string, unknown>;
  return String(record.eventId ?? record.id ?? `${record.eventType ?? 'event'}:${record.timestamp ?? JSON.stringify(record)}`);
}

export async function GET(request: NextRequest): Promise<Response> {
  const cookie = request.headers.get('cookie') ?? '';
  let session: Response;
  try {
    ({ response: session } = await fetchBackend('core', '/api/auth/me', { headers: cookie ? { cookie } : {} }));
  } catch {
    return Response.json({ code: 'CORE_AUTH_UNAVAILABLE', message: 'Admin UI cannot reach Core authentication.' }, { status: 503 });
  }
  if (!session.ok) return Response.json({ code: 'UNAUTHORIZED', message: 'Core Admin session is required.' }, { status: 401 });
  const token = machineToken();
  if (!token) return Response.json({ code: 'MACHINE_CREDENTIAL_UNAVAILABLE', message: 'Netty machine credential is not configured.' }, { status: 503 });

  const stream = new ReadableStream<Uint8Array>({
    start(controller) {
      let closed = false;
      const seen = new Set<string>();
      const send = (text: string) => { if (!closed) controller.enqueue(encoder.encode(text)); };
      const close = () => { if (closed) return; closed = true; clearInterval(timer); try { controller.close(); } catch {} };
      const poll = async () => {
        try {
          const { response } = await fetchBackend('netty', '/api/admin/events?limit=100', {
            headers: { Authorization: `Bearer ${token}`, Accept: 'application/json' }
          });
          if (!response.ok) {
            send(`event: error\ndata: ${JSON.stringify({ status: response.status, message: 'Netty runtime event relay failed.' })}\n\n`);
            return;
          }
          const events = unwrapEvents(await response.json());
          for (const event of [...events].reverse()) {
            const id = eventId(event);
            if (seen.has(id)) continue;
            seen.add(id);
            if (seen.size > 1000) seen.delete(seen.values().next().value as string);
            send(`id: ${id.replace(/[\r\n]/g, '')}\nevent: runtime\ndata: ${JSON.stringify(event)}\n\n`);
          }
          send(`: heartbeat ${Date.now()}\n\n`);
        } catch (error) {
          send(`event: error\ndata: ${JSON.stringify({ message: error instanceof Error ? error.message : 'Runtime event relay failed.' })}\n\n`);
        }
      };
      send(': connected\n\n');
      void poll();
      const timer = setInterval(() => void poll(), 2000);
      request.signal.addEventListener('abort', close, { once: true });
    }
  });
  return new Response(stream, {
    headers: {
      'Content-Type': 'text/event-stream; charset=utf-8',
      'Cache-Control': 'no-cache, no-transform',
      Connection: 'keep-alive',
      'X-Accel-Buffering': 'no'
    }
  });
}
